package dev.starcore.starcore.core.metrics;

import dev.starcore.starcore.core.service.StarCoreService;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Collects and aggregates performance metrics for STARCORE operations.
 *
 * <p>This service tracks:
 * <ul>
 *   <li>Event processing durations</li>
 *   <li>Database query performance</li>
 *   <li>Cache hit/miss ratios</li>
 *   <li>Async task execution times</li>
 *   <li>Memory usage statistics</li>
 * </ul>
 *
 * <p>All operations are thread-safe and use lock-free data structures
 * to minimize performance impact on the measured code paths.
 */
public final class PerformanceMetricsService implements StarCoreService {
    private final Map<String, OperationMetrics> operationMetrics = new ConcurrentHashMap<>();
    private final Map<String, CacheMetrics> cacheMetrics = new ConcurrentHashMap<>();
    private final AtomicLong startTimeMillis = new AtomicLong(System.currentTimeMillis());
    private final MemoryMXBean memoryMXBean;
    private volatile boolean enabled = true;
    private volatile boolean memoryTrackingEnabled = true;

    // Quick access metrics (avoid map lookups for hot paths)
    private final CacheMetrics dbHitMetrics = new CacheMetrics();
    private final CacheMetrics dbMissMetrics = new CacheMetrics();
    private final OperationMetrics asyncTaskMetrics = new OperationMetrics();
    private final OperationMetrics eventDispatchMetrics = new OperationMetrics();

    public PerformanceMetricsService() {
        this.memoryMXBean = ManagementFactory.getMemoryMXBean();
    }

    /**
     * Records the duration of an operation (event, command, etc.).
     *
     * @param operationName the operation identifier
     * @param durationNanos the duration in nanoseconds
     */
    public void recordOperation(String operationName, long durationNanos) {
        if (!enabled) return;
        operationMetrics.computeIfAbsent(operationName, k -> new OperationMetrics())
            .record(durationNanos);
    }

    /**
     * Records a database query execution.
     *
     * @param queryType the query type (e.g., "nation.load", "territory.save")
     * @param durationMillis the duration in milliseconds
     */
    public void recordDatabaseQuery(String queryType, long durationMillis) {
        if (!enabled) return;
        recordOperation("db." + queryType, durationMillis * 1_000_000L);
    }

    /**
     * Records a cache hit.
     *
     * @param cacheName the cache identifier
     */
    public void recordCacheHit(String cacheName) {
        if (!enabled) return;
        cacheMetrics.computeIfAbsent(cacheName, k -> new CacheMetrics())
            .recordHit();
    }

    /**
     * Records a cache miss.
     *
     * @param cacheName the cache identifier
     */
    public void recordCacheMiss(String cacheName) {
        if (!enabled) return;
        cacheMetrics.computeIfAbsent(cacheName, k -> new CacheMetrics())
            .recordMiss();
    }

    /**
     * Returns a snapshot of all collected metrics.
     *
     * @return immutable metrics snapshot
     */
    public PerformanceSnapshot snapshot() {
        Duration uptime = Duration.ofMillis(System.currentTimeMillis() - startTimeMillis.get());
        Map<String, OperationStats> operations = new ConcurrentHashMap<>();
        operationMetrics.forEach((name, metrics) -> operations.put(name, metrics.toStats()));

        Map<String, CacheStats> caches = new ConcurrentHashMap<>();
        cacheMetrics.forEach((name, metrics) -> caches.put(name, metrics.toStats()));

        return new PerformanceSnapshot(Instant.now(), uptime, operations, caches);
    }

    /**
     * Resets all collected metrics.
     */
    public void reset() {
        operationMetrics.clear();
        cacheMetrics.clear();
        startTimeMillis.set(System.currentTimeMillis());
    }

    /**
     * Enables or disables metric collection.
     *
     * <p>When disabled, all record methods become no-ops.
     * This can be used to reduce overhead in production if needed.
     *
     * @param enabled true to enable, false to disable
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Returns whether metric collection is currently enabled.
     *
     * @return true if enabled
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Creates a timer for measuring operation duration.
     *
     * <p>Example usage:
     * <pre>{@code
     * try (Timer timer = metricsService.startTimer("nation.create")) {
     *     // Operation code
     * }
     * }</pre>
     *
     * @param operationName the operation identifier
     * @return a timer that automatically records duration when closed
     */
    public Timer startTimer(String operationName) {
        return new Timer(this, operationName);
    }

    /**
     * Enables or disables memory tracking.
     *
     * @param enabled true to enable memory tracking
     */
    public void setMemoryTrackingEnabled(boolean enabled) {
        this.memoryTrackingEnabled = enabled;
    }

    /**
     * Gets current memory usage statistics.
     *
     * @return memory usage snapshot
     */
    public MemorySnapshot getMemorySnapshot() {
        if (!memoryTrackingEnabled) {
            return MemorySnapshot.DISABLED;
        }
        MemoryUsage heap = memoryMXBean.getHeapMemoryUsage();
        MemoryUsage nonHeap = memoryMXBean.getNonHeapMemoryUsage();
        return new MemorySnapshot(
            heap.getUsed(),
            heap.getMax(),
            heap.getCommitted(),
            nonHeap.getUsed(),
            Runtime.getRuntime().freeMemory(),
            Runtime.getRuntime().totalMemory()
        );
    }

    /**
     * Records a database cache hit (query found in cache).
     */
    public void recordDbHit() {
        if (!enabled) return;
        dbHitMetrics.recordHit();
    }

    /**
     * Records a database cache miss (query not in cache).
     */
    public void recordDbMiss() {
        if (!enabled) return;
        dbMissMetrics.recordMiss();
    }

    /**
     * Records async task execution.
     */
    public void recordAsyncTask(long durationNanos) {
        if (!enabled) return;
        asyncTaskMetrics.record(durationNanos);
    }

    /**
     * Records event dispatch duration.
     */
    public void recordEventDispatch(long durationNanos) {
        if (!enabled) return;
        eventDispatchMetrics.record(durationNanos);
    }

    /**
     * Gets quick metrics without map lookups.
     */
    public QuickMetrics getQuickMetrics() {
        return new QuickMetrics(
            dbHitMetrics.toStats(),
            dbMissMetrics.toStats(),
            asyncTaskMetrics.toStats(),
            eventDispatchMetrics.toStats()
        );
    }

    /**
     * Quick access metrics for hot paths.
     */
    public record QuickMetrics(
        CacheStats dbCache,
        CacheStats dbMisses,
        OperationStats asyncTasks,
        OperationStats eventDispatches
    ) {}

    /**
     * Memory usage snapshot.
     */
    public record MemorySnapshot(
        long heapUsed,
        long heapMax,
        long heapCommitted,
        long nonHeapUsed,
        long jvmFreeMemory,
        long jvmTotalMemory
    ) {
        public static final MemorySnapshot DISABLED = new MemorySnapshot(0, 0, 0, 0, 0, 0);

        public double heapUsagePercent() {
            return heapMax > 0 ? (double) heapUsed / heapMax * 100 : 0;
        }

        public long heapFree() {
            return heapMax > 0 ? heapMax - heapUsed : 0;
        }
    }

    private static final class OperationMetrics {
        private final LongAdder count = new LongAdder();
        private final LongAdder totalNanos = new LongAdder();
        private final AtomicLong minNanos = new AtomicLong(Long.MAX_VALUE);
        private final AtomicLong maxNanos = new AtomicLong(Long.MIN_VALUE);

        void record(long durationNanos) {
            count.increment();
            totalNanos.add(durationNanos);
            updateMin(durationNanos);
            updateMax(durationNanos);
        }

        private void updateMin(long value) {
            long current;
            while ((current = minNanos.get()) > value) {
                if (minNanos.compareAndSet(current, value)) {
                    break;
                }
            }
        }

        private void updateMax(long value) {
            long current;
            while ((current = maxNanos.get()) < value) {
                if (maxNanos.compareAndSet(current, value)) {
                    break;
                }
            }
        }

        OperationStats toStats() {
            long calls = count.sum();
            long total = totalNanos.sum();
            long min = minNanos.get() == Long.MAX_VALUE ? 0 : minNanos.get();
            long max = maxNanos.get() == Long.MIN_VALUE ? 0 : maxNanos.get();
            long avg = calls > 0 ? total / calls : 0;
            return new OperationStats(calls, Duration.ofNanos(avg), Duration.ofNanos(min), Duration.ofNanos(max));
        }
    }

    private static final class CacheMetrics {
        private final LongAdder hits = new LongAdder();
        private final LongAdder misses = new LongAdder();

        void recordHit() {
            hits.increment();
        }

        void recordMiss() {
            misses.increment();
        }

        CacheStats toStats() {
            long hitCount = hits.sum();
            long missCount = misses.sum();
            long total = hitCount + missCount;
            double hitRate = total > 0 ? (double) hitCount / total : 0.0;
            return new CacheStats(hitCount, missCount, hitRate);
        }
    }

    /**
     * Auto-closeable timer for measuring operation duration.
     */
    public static final class Timer implements AutoCloseable {
        private final PerformanceMetricsService service;
        private final String operationName;
        private final long startNanos;

        private Timer(PerformanceMetricsService service, String operationName) {
            this.service = service;
            this.operationName = operationName;
            this.startNanos = System.nanoTime();
        }

        @Override
        public void close() {
            long duration = System.nanoTime() - startNanos;
            service.recordOperation(operationName, duration);
        }
    }
}
