package dev.starcore.starcore.core.cache;

import dev.starcore.starcore.core.service.StarCoreService;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Central manager for all STARCORE caches.
 *
 * <p>This service provides:
 * <ul>
 *   <li>Named cache creation and retrieval</li>
 *   <li>Global cache invalidation</li>
 *   <li>Aggregate statistics across all caches</li>
 *   <li>Memory pressure-based eviction support</li>
 *   <li>Configurable cache implementations</li>
 * </ul>
 *
 * <p>Usage example:
 * <pre>{@code
 * CacheManager manager = context.serviceRegistry().require(CacheManager.class);
 * Cache<UUID, Nation> nationCache = manager.getOrCreate("nations",
 *     CacheConfig.builder()
 *         .maxSize(5000)
 *         .expireAfterAccess(Duration.ofMinutes(10))
 *         .build());
 *
 * Nation nation = nationCache.get(uuid).orElseGet(() -> loadNationFromDatabase(uuid));
 * }</pre>
 */
public final class CacheManager implements StarCoreService {
    private final Map<String, CacheHolder<?, ?>> caches = new ConcurrentHashMap<>();
    private final MemoryPressureListener memoryPressureListener;
    private final AtomicLong memoryEvictionCount = new AtomicLong(0);
    private final ScheduledExecutorService cleanupScheduler;
    private final AtomicReference<ScheduledFuture<?>> cleanupTask = new AtomicReference<>();

    // Memory pressure thresholds
    private static final double HEAP_WARNING_THRESHOLD = 0.70; // 70%
    private static final double HEAP_CRITICAL_THRESHOLD = 0.85; // 85%
    private static final double HEAP_EMERGENCY_THRESHOLD = 0.90; // 90%
    // Default cleanup interval: 5 minutes
    private static final long CLEANUP_INTERVAL_SECONDS = 300;

    /**
     * Creates a CacheManager with optional memory pressure listener.
     *
     * @param memoryPressureListener optional listener for memory pressure events
     */
    public CacheManager(MemoryPressureListener memoryPressureListener) {
        this.memoryPressureListener = memoryPressureListener;
        this.cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "starcore-cache-cleanup");
            t.setDaemon(true);
            return t;
        });
        startCleanupTask();
    }

    /**
     * Creates a CacheManager without memory pressure listener.
     */
    public CacheManager() {
        this(null);
    }

    /**
     * Starts the periodic cleanup task for expired entries.
     */
    private void startCleanupTask() {
        ScheduledFuture<?> existing = cleanupTask.get();
        if (existing != null) {
            existing.cancel(false);
        }
        ScheduledFuture<?> future = cleanupScheduler.scheduleAtFixedRate(
            this::cleanupExpiredEntries,
            CLEANUP_INTERVAL_SECONDS,
            CLEANUP_INTERVAL_SECONDS,
            TimeUnit.SECONDS
        );
        cleanupTask.set(future);
    }

    /**
     * Cleanup expired entries from all caches.
     */
    private void cleanupExpiredEntries() {
        Instant now = Instant.now();
        for (CacheHolder<?, ?> holder : caches.values()) {
            Cache<?, ?> cache = holder.cache();
            Duration accessExpiry = holder.config().expireAfterAccess();
            Duration writeExpiry = holder.config().expireAfterWrite();

            if (accessExpiry != null || writeExpiry != null) {
                // Evict based on expiration
                cache.cleanupExpired(now, accessExpiry, writeExpiry);
            }
        }
        // Check memory pressure after cleanup
        checkMemoryPressure();
    }

    /**
     * Shuts down the cache manager and its scheduled tasks.
     */
    public void shutdown() {
        ScheduledFuture<?> task = cleanupTask.get();
        if (task != null) {
            task.cancel(false);
        }
        cleanupScheduler.shutdown();
        invalidateAllCaches();
    }

    /**
     * Interface for memory pressure event notifications.
     */
    @FunctionalInterface
    public interface MemoryPressureListener {
        void onMemoryPressure(MemoryPressureLevel level, double heapUsagePercent);
    }

    /**
     * Memory pressure severity levels.
     */
    public enum MemoryPressureLevel {
        NORMAL,
        WARNING,
        CRITICAL,
        EMERGENCY
    }

    /**
     * Returns the cache with the given name, creating it if necessary.
     *
     * <p>If the cache already exists, the provided config is ignored
     * and the existing cache is returned.
     *
     * @param name unique cache identifier
     * @param config cache configuration
     * @param <K> key type
     * @param <V> value type
     * @return the cache instance
     */
    @SuppressWarnings("unchecked")
    public <K, V> Cache<K, V> getOrCreate(String name, CacheConfig config) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(config, "config");

        CacheHolder<?, ?> holder = caches.computeIfAbsent(name, k -> createHolder(config));
        return (Cache<K, V>) holder.cache();
    }

    /**
     * Invalidates all entries in the cache with the given name.
     *
     * @param name the cache name
     */
    public void invalidateAll(String name) {
        CacheHolder<?, ?> holder = caches.get(name);
        if (holder != null) {
            holder.cache().invalidateAll();
        }
    }

    /**
     * Invalidates all entries in all managed caches.
     */
    public void invalidateAllCaches() {
        caches.values().forEach(holder -> holder.cache().invalidateAll());
    }

    /**
     * Evicts entries from all caches based on memory pressure.
     * Called automatically when memory threshold is exceeded.
     * Uses LRU (Least Recently Used) ordering for intelligent eviction.
     *
     * @param level the memory pressure level
     * @return number of entries evicted
     */
    public long evictBasedOnMemoryPressure(MemoryPressureLevel level) {
        long evictedTotal = 0;
        long targetEvictionPercent;

        switch (level) {
            case WARNING -> targetEvictionPercent = 10;  // Evict 10% of each cache
            case CRITICAL -> targetEvictionPercent = 25;  // Evict 25% of each cache
            case EMERGENCY -> targetEvictionPercent = 50; // Evict 50% of each cache
            default -> targetEvictionPercent = 0;
        }

        // First pass: identify caches that exceed their max size
        List<CacheHolder<?, ?>> oversizedCaches = new ArrayList<>();
        for (CacheHolder<?, ?> holder : caches.values()) {
            Cache<?, ?> cache = holder.cache();
            if (cache.size() > holder.config().maxSize()) {
                oversizedCaches.add(holder);
            }
        }

        // Evict from oversized caches first (most critical)
        for (CacheHolder<?, ?> holder : oversizedCaches) {
            Cache<?, ?> cache = holder.cache();
            int maxSize = holder.config().maxSize();
            long toEvict = cache.size() - maxSize;
            if (toEvict > 0) {
                cache.evictLRU(toEvict);
                evictedTotal += toEvict;
            }
        }

        // Then apply memory pressure eviction to remaining caches
        if (evictedTotal < targetEvictionPercent * caches.size() / 10) {
            for (CacheHolder<?, ?> holder : caches.values()) {
                if (!oversizedCaches.contains(holder)) {
                    Cache<?, ?> cache = holder.cache();
                    long size = cache.size();
                    long toEvict = size * targetEvictionPercent / 100;
                    if (toEvict > 0) {
                        cache.evictLRU(toEvict);
                        evictedTotal += toEvict;
                    }
                }
            }
        }

        memoryEvictionCount.addAndGet(evictedTotal);
        return evictedTotal;
    }

    /**
     * Checks current memory pressure and triggers eviction if needed.
     *
     * @return the current memory pressure level
     */
    public MemoryPressureLevel checkMemoryPressure() {
        MemoryUsage heapUsage = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        double usedPercent = (double) heapUsage.getUsed() / heapUsage.getMax();

        MemoryPressureLevel level;
        if (usedPercent >= HEAP_EMERGENCY_THRESHOLD) {
            level = MemoryPressureLevel.EMERGENCY;
        } else if (usedPercent >= HEAP_CRITICAL_THRESHOLD) {
            level = MemoryPressureLevel.CRITICAL;
        } else if (usedPercent >= HEAP_WARNING_THRESHOLD) {
            level = MemoryPressureLevel.WARNING;
        } else {
            level = MemoryPressureLevel.NORMAL;
        }

        if (level != MemoryPressureLevel.NORMAL && memoryPressureListener != null) {
            memoryPressureListener.onMemoryPressure(level, usedPercent * 100);
            if (level.ordinal() >= MemoryPressureLevel.CRITICAL.ordinal()) {
                evictBasedOnMemoryPressure(level);
            }
        }

        return level;
    }

    /**
     * Gets the total number of entries evicted due to memory pressure.
     */
    public long getMemoryEvictionCount() {
        return memoryEvictionCount.get();
    }

    /**
     * Returns statistics for the cache with the given name.
     *
     * @param name the cache name
     * @return cache statistics, or empty if cache doesn't exist
     */
    public CacheStatistics statistics(String name) {
        CacheHolder<?, ?> holder = caches.get(name);
        return holder != null ? holder.cache().statistics() : CacheStatistics.empty();
    }

    /**
     * Returns information about all managed caches.
     *
     * @return collection of cache info
     */
    public Collection<CacheInfo> allCaches() {
        Collection<CacheInfo> result = new ArrayList<>();
        caches.forEach((name, holder) -> {
            Cache<?, ?> cache = holder.cache();
            result.add(new CacheInfo(
                name,
                cache.size(),
                holder.config(),
                cache.statistics()
            ));
        });
        return Collections.unmodifiableCollection(result);
    }

    /**
     * Removes the cache with the given name from management.
     *
     * <p>The cache is invalidated before removal.
     *
     * @param name the cache name
     */
    public void remove(String name) {
        CacheHolder<?, ?> holder = caches.remove(name);
        if (holder != null) {
            holder.cache().invalidateAll();
        }
    }

    /**
     * Gets the current heap memory usage percentage.
     */
    public double getHeapUsagePercent() {
        MemoryUsage heapUsage = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        return (double) heapUsage.getUsed() / heapUsage.getMax();
    }

    /**
     * Gets heap memory statistics.
     */
    public HeapStats getHeapStats() {
        MemoryUsage heapUsage = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        return new HeapStats(
            heapUsage.getUsed(),
            heapUsage.getMax(),
            heapUsage.getCommitted(),
            (double) heapUsage.getUsed() / heapUsage.getMax() * 100
        );
    }

    private <K, V> CacheHolder<K, V> createHolder(CacheConfig config) {
        // Use LRU-capable ConcurrentHashMap-based cache with TTL support
        ConcurrentHashMap<K, CacheEntry<V>> map = new ConcurrentHashMap<>(
            Math.min(config.maxSize(), 256),
            0.75f,
            Runtime.getRuntime().availableProcessors()
        );
        SimpleCache<K, V> cache = new SimpleCache<>(map, config.maxSize(),
            config.expireAfterAccess(), config.expireAfterWrite(), config.recordStats());
        return new CacheHolder<>(cache, config);
    }

    private record CacheHolder<K, V>(Cache<K, V> cache, CacheConfig config) {
    }

    /**
     * Heap memory statistics.
     */
    public record HeapStats(
        long used,
        long max,
        long committed,
        double usagePercent
    ) {}

    /**
     * Simple cache implementation backed by ConcurrentHashMap.
     * Used when no eviction policy is needed.
     */
    private static class SimpleCache<K, V> implements Cache<K, V> {
        private final ConcurrentHashMap<K, CacheEntry<V>> map;
        private final StatsRecorder stats;
        private final int maxSize;
        private final Duration expireAfterAccess;
        private final Duration expireAfterWrite;
        private final AtomicLong accessCounter = new AtomicLong(0);

        SimpleCache(ConcurrentHashMap<K, CacheEntry<V>> map, int maxSize,
                    Duration expireAfterAccess, Duration expireAfterWrite, boolean recordStats) {
            this.map = map;
            this.maxSize = maxSize;
            this.expireAfterAccess = expireAfterAccess;
            this.expireAfterWrite = expireAfterWrite;
            this.stats = recordStats ? new StatsRecorder() : StatsRecorder.NOOP;
        }

        private CacheEntry<V> createEntry(V value) {
            long accessTime = accessCounter.incrementAndGet();
            return new CacheEntry<>(value, accessTime, System.nanoTime());
        }

        @Override
        public Optional<V> get(K key) {
            CacheEntry<V> entry = map.get(key);
            Instant now = Instant.now();
            if (entry != null) {
                if (isExpired(entry, now)) {
                    map.remove(key, entry);
                    stats.recordEviction();
                    stats.recordMiss();
                    return Optional.empty();
                }
                // Update access order for LRU
                if (expireAfterAccess != null) {
                    map.compute(key, (k, old) -> {
                        if (old != null) {
                            return old.withUpdatedAccess(accessCounter.incrementAndGet());
                        }
                        return null;
                    });
                }
                stats.recordHit();
                return Optional.of(entry.value());
            }
            stats.recordMiss();
            return Optional.empty();
        }

        @Override
        public V get(K key, java.util.function.Function<? super K, ? extends V> loader) {
            CacheEntry<V> entry = map.get(key);
            Instant now = Instant.now();
            if (entry != null) {
                if (isExpired(entry, now)) {
                    map.remove(key, entry);
                    stats.recordEviction();
                } else {
                    // Update access order
                    if (expireAfterAccess != null) {
                        map.compute(key, (k, old) -> {
                            if (old != null) {
                                return old.withUpdatedAccess(accessCounter.incrementAndGet());
                            }
                            return null;
                        });
                    }
                    stats.recordHit();
                    return entry.value();
                }
            }

            stats.recordMiss();
            long startNanos = System.nanoTime();
            try {
                V computed = loader.apply(key);
                if (computed != null) {
                    put(key, computed);
                    stats.recordLoadSuccess(System.nanoTime() - startNanos);
                    return computed;
                }
                stats.recordLoadFailure(System.nanoTime() - startNanos);
                return null;
            } catch (RuntimeException e) {
                stats.recordLoadFailure(System.nanoTime() - startNanos);
                throw e;
            }
        }

        @Override
        public void put(K key, V value) {
            // Check capacity and evict if needed
            if (maxSize > 0 && map.size() >= maxSize) {
                evictOne();
            }
            map.put(key, createEntry(value));
        }

        private void evictOne() {
            // Find and evict the LRU entry
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
            if (count <= 0 || map.isEmpty()) return;

            List<K> keysToEvict = new ArrayList<>();
            long minAccess = Long.MAX_VALUE;
            K lruKey = null;

            // Collect LRU entries
            for (Map.Entry<K, CacheEntry<V>> entry : map.entrySet()) {
                long access = entry.getValue().accessOrder();
                if (access < minAccess) {
                    if (lruKey != null && keysToEvict.size() < count - 1) {
                        keysToEvict.add(lruKey);
                    }
                    minAccess = access;
                    lruKey = entry.getKey();
                } else if (keysToEvict.size() < count - 1) {
                    keysToEvict.add(entry.getKey());
                }
            }

            if (lruKey != null) {
                keysToEvict.add(lruKey);
            }

            for (K key : keysToEvict) {
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
            if (expireAfterAccess != null) {
                Instant accessExpiry = Instant.ofEpochMilli(entry.accessNanos() / 1_000_000)
                    .plus(expireAfterAccess);
                if (now.isAfter(accessExpiry)) {
                    return true;
                }
            }
            if (expireAfterWrite != null) {
                Instant writeExpiry = Instant.ofEpochMilli(entry.accessNanos() / 1_000_000)
                    .plus(expireAfterWrite);
                if (now.isAfter(writeExpiry)) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Cache entry with LRU tracking.
     */
    private record CacheEntry<V>(V value, long accessOrder, long accessNanos) {
        CacheEntry<V> withUpdatedAccess(long newOrder) {
            return new CacheEntry<>(value, newOrder, System.nanoTime());
        }
    }

    /**
     * Snapshot information about a managed cache.
     *
     * @param name the cache name
     * @param size current number of entries
     * @param config the cache configuration
     * @param statistics current statistics
     */
    public record CacheInfo(
        String name,
        long size,
        CacheConfig config,
        CacheStatistics statistics
    ) {
    }
}
