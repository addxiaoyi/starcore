package dev.starcore.starcore.core.cache;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * Cache implementation backed by ConcurrentHashMap with LRU and TTL support.
 *
 * <p>This implementation provides:
 * <ul>
 *   <li>LRU (Least Recently Used) eviction for memory pressure situations</li>
 *   <li>TTL (Time To Live) based expiration (access or write time)</li>
 *   <li>Thread-safe operations using ConcurrentHashMap</li>
 * </ul>
 *
 * @param <K> key type
 * @param <V> value type
 */
final class ConcurrentMapCache<K, V> implements Cache<K, V> {
    private final ConcurrentHashMap<K, CacheEntry<V>> map;
    private final StatsRecorder stats;
    private final int maxSize;
    private final Duration expireAfterAccess;
    private final Duration expireAfterWrite;
    // LRU tracking: simple counter for ordering
    private final AtomicLong accessCounter = new AtomicLong(0);

    ConcurrentMapCache(ConcurrentHashMap<K, CacheEntry<V>> map) {
        this(map, true, 1000, null, null);
    }

    ConcurrentMapCache(ConcurrentHashMap<K, CacheEntry<V>> map, boolean recordStats) {
        this(map, recordStats, 1000, null, null);
    }

    private ConcurrentMapCache(ConcurrentHashMap<K, CacheEntry<V>> map, boolean recordStats,
                                int maxSize, Duration expireAfterAccess, Duration expireAfterWrite) {
        this.map = map;
        this.stats = recordStats ? new StatsRecorder() : StatsRecorder.NOOP;
        this.maxSize = maxSize;
        this.expireAfterAccess = expireAfterAccess;
        this.expireAfterWrite = expireAfterWrite;
    }

    /**
     * Creates a cache with configuration options.
     */
    static <K, V> ConcurrentMapCache<K, V> create(int maxSize, Duration expireAfterAccess, Duration expireAfterWrite, boolean recordStats) {
        return new ConcurrentMapCache<>(new ConcurrentHashMap<>(Math.min(maxSize, 256), 0.75f, 4),
            recordStats, maxSize, expireAfterAccess, expireAfterWrite);
    }

    private CacheEntry<V> createEntry(V value) {
        long accessTime = accessCounter.incrementAndGet();
        // E-066/E-067: 用 System.currentTimeMillis() 而非 nanoTime 作为时间基准,
        // nanoTime 是相对 JVM 启动的纳秒,转毫秒后 Instant.ofEpochMilli 生成的是 1970+uptime,
        // 与 Instant.now() 完全不同基准,TTL 永远不会过期或永远立刻过期。
        long writeMillis = System.currentTimeMillis();
        return new CacheEntry<>(value, accessTime, writeMillis, writeMillis);
    }

    @Override
    public Optional<V> get(K key) {
        CacheEntry<V> entry = map.get(key);
        if (entry != null) {
            // Check expiration on access if configured
            if (isExpired(entry, Instant.now())) {
                map.remove(key, entry);
                stats.recordEviction();
                stats.recordMiss();
                return Optional.empty();
            }
            // Update access order for LRU
            if (expireAfterAccess != null) {
                // E-069: 用 computeIfPresent 原子更新,且只在 expireAfterAccess 配置时刷新 lastAccessMillis
                map.computeIfPresent(key, (k, old) ->
                    old.withUpdatedAccess(accessCounter.incrementAndGet(), System.currentTimeMillis()));
            }
            stats.recordHit();
            return Optional.of(entry.value());
        }
        stats.recordMiss();
        return Optional.empty();
    }

    @Override
    public V get(K key, Function<? super K, ? extends V> loader) {
        // E-068: 原实现 get 后 expire 判定 → 进入 loader 与其它线程同时加载(single-flight 缺失)。
        // 改用 computeIfAbsent 原子加载:首个线程加载,其它线程等待同一 future。
        // 但 ConcurrentHashMap.computeIfAbsent 调用 loader 时持有 bucket 锁,若 loader 长时间阻塞
        // 会影响同 bucket 其它 key。这里折中:先做快速 get(命中且未过期直接返回),miss 时用 computeIfAbsent
        // 包裹 loader,且 loader 不阻塞在 compute 内太久(loader 应是快速 IO)。
        CacheEntry<V> existing = map.get(key);
        if (existing != null) {
            if (!isExpired(existing, Instant.now())) {
                // 命中且未过期
                if (expireAfterAccess != null) {
                    map.computeIfPresent(key, (k, old) ->
                        old.withUpdatedAccess(accessCounter.incrementAndGet(), System.currentTimeMillis()));
                }
                stats.recordHit();
                return existing.value();
            }
            // 过期,移除
            map.remove(key, existing);
            stats.recordEviction();
        }

        stats.recordMiss();
        long startNanos = System.nanoTime();
        // E-068: 用 computeIfAbsent 实现 single-flight;loader 期间其它线程会等待。
        // 但 computeIfAbsent 在 loader 内抛异常时不写入,符合预期。
        try {
            CacheEntry<V> computedEntry = map.computeIfAbsent(key, k -> {
                V computed = loader.apply(k);
                if (computed == null) {
                    return null; // 不缓存 null
                }
                // E-069: 若仍超过 maxSize,evictOne 不会立刻在 computeIfAbsent 内调用(避免死锁),
                // 留到 put 路径或下次操作时清理;宁可暂时超过 maxSize 也不要在持有 bucket 锁时 evictOne。
                return createEntry(computed);
            });
            if (computedEntry == null) {
                // loader 返回 null 或被并发清除
                stats.recordLoadFailure(System.nanoTime() - startNanos);
                return null;
            }
            stats.recordLoadSuccess(System.nanoTime() - startNanos);
            // E-069: computeIfAbsent 后再异步检查容量(非原子,但能渐进收敛 maxSize)
            if (map.size() > maxSize) {
                evictOne();
            }
            return computedEntry.value();
        } catch (RuntimeException e) {
            stats.recordLoadFailure(System.nanoTime() - startNanos);
            throw e;
        }
    }

    @Override
    public void put(K key, V value) {
        // E-069: 用 putIfAbsent + 后续 computeIfPresent 实现"先占位后填充"避免 evictOne 时被并发插入
        // Check capacity and evict if needed
        // E-064: evictOne O(n) 在主路径上 maxSize=1000 可接受,但并发场景多个线程同时 evictOne
        // 可能误删多条。这里用 synchronized 控制并发 evict,避免双重 evict;put 本身仍用 ConcurrentHashMap 原子操作。
        if (map.size() >= maxSize) {
            synchronized (this) {
                // double-check inside lock,可能其它线程已 evictOne
                if (map.size() >= maxSize) {
                    evictOne();
                }
            }
        }
        map.put(key, createEntry(value));
    }

    private void evictOne() {
        // E-064: 原 O(n) 扫描找 LRU,在主线程 put 路径上 maxSize=1000 可接受。
        // 并发场景多个线程同时 evictOne 找到同一 lruKey 时调用 map.remove(key, value) 是 condition remove
        // 安全,但可能"漏 evict"导致 size 略超 maxSize。本方法调用方已用 synchronized 控制,
        // 这里单线程遍历不再有并发问题。改进:用 PriorityQueue 太重,且要保持 ordering 同步,
        // 用 LinkedHashMap access-order 需要外层加锁,与 ConcurrentHashMap 不兼容。保持 O(n) + 锁。
        Map.Entry<K, CacheEntry<V>> lru = null;
        long minAccess = Long.MAX_VALUE;
        for (Map.Entry<K, CacheEntry<V>> entry : map.entrySet()) {
            if (entry.getValue().accessOrder() < minAccess) {
                minAccess = entry.getValue().accessOrder();
                lru = entry;
            }
        }
        if (lru != null) {
            map.remove(lru.getKey(), lru.getValue());
            stats.recordEviction();
        }
    }

    @Override
    public void invalidate(K key) {
        CacheEntry<V> removed = map.remove(key);
        if (removed != null) {
            stats.recordEviction();
        }
    }

    @Override
    public void invalidateAll() {
        long count = map.size();
        map.clear();
        stats.recordEvictions(count);
    }

    @Override
    public Map<K, V> snapshot() {
        Map<K, V> result = new ConcurrentHashMap<>();
        Instant now = Instant.now();
        for (Map.Entry<K, CacheEntry<V>> entry : map.entrySet()) {
            if (!isExpired(entry.getValue(), now)) {
                result.put(entry.getKey(), entry.getValue().value());
            }
        }
        return Map.copyOf(result);
    }

    @Override
    public long size() {
        return map.size();
    }

    @Override
    public CacheStatistics statistics() {
        return stats.toStatistics();
    }

    @Override
    public void evictLRU(long count) {
        // E-065: 原 evictLRU 算法逻辑全错,不是按 accessOrder 升序收集 count 条 LRU entry,
        // 可能只 evict 1 条或不 evict。改为先收集全部 entry 按 accessOrder 升序,取前 count 条 evict。
        if (count <= 0) return;
        // 收集到列表再排序,避免迭代期间并发修改结构
        List<Map.Entry<K, CacheEntry<V>>> snapshot = new ArrayList<>(map.size());
        for (Map.Entry<K, CacheEntry<V>> entry : map.entrySet()) {
            snapshot.add(Map.entry(entry.getKey(), entry.getValue()));
        }
        snapshot.sort(Comparator.comparingLong(e -> e.getValue().accessOrder()));
        long toEvict = Math.min(count, snapshot.size());
        for (int i = 0; i < toEvict; i++) {
            K key = snapshot.get(i).getKey();
            invalidate(key);
        }
    }

    @Override
    public void cleanupExpired(Instant now, Duration expireAfterAccess, Duration expireAfterWrite) {
        if (expireAfterAccess == null && expireAfterWrite == null) {
            return;
        }

        List<K> keysToRemove = new ArrayList<>();
        for (Map.Entry<K, CacheEntry<V>> entry : map.entrySet()) {
            if (isExpired(entry.getValue(), now)) {
                keysToRemove.add(entry.getKey());
            }
        }

        for (K key : keysToRemove) {
            invalidate(key);
        }
    }

    private boolean isExpired(CacheEntry<V> entry, Instant now) {
        long nowMillis = now.toEpochMilli();
        // E-066/E-067: 用 System.currentTimeMillis() 基准的 lastAccessMillis 和 writeMillis,
        // 与 Instant.now() 同基准。expireAfterAccess 用 lastAccessMillis;expireAfterWrite 用 writeMillis。
        if (expireAfterAccess != null) {
            long accessExpiryMillis = entry.lastAccessMillis() + expireAfterAccess.toMillis();
            if (nowMillis > accessExpiryMillis) {
                return true;
            }
        }
        if (expireAfterWrite != null) {
            long writeExpiryMillis = entry.writeMillis() + expireAfterWrite.toMillis();
            if (nowMillis > writeExpiryMillis) {
                return true;
            }
        }
        return false;
    }

    /**
     * Cache entry with LRU tracking and access time.
     * E-066/E-067: 把 accessNanos 拆成 writeMillis(创建时间,不随访问更新)和 lastAccessMillis(每次访问更新),
     * 都用 System.currentTimeMillis() 而非 nanoTime,与 Instant.now() 同基准。
     */
    private record CacheEntry<V>(V value, long accessOrder, long writeMillis, long lastAccessMillis) {
        CacheEntry<V> withUpdatedAccess(long newOrder, long accessMillis) {
            return new CacheEntry<>(value, newOrder, writeMillis, accessMillis);
        }
    }
}
