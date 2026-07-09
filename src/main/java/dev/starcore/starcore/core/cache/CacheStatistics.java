package dev.starcore.starcore.core.cache;

/**
 * Statistics for a cache instance.
 *
 * @param hitCount number of cache hits
 * @param missCount number of cache misses
 * @param loadSuccessCount number of successful load operations
 * @param loadFailureCount number of failed load operations
 * @param totalLoadNanos total time spent loading values
 * @param evictionCount number of evicted entries
 */
public record CacheStatistics(
    long hitCount,
    long missCount,
    long loadSuccessCount,
    long loadFailureCount,
    long totalLoadNanos,
    long evictionCount
) {
    /**
     * Returns the hit rate as a value between 0.0 and 1.0.
     *
     * @return hit rate
     */
    public double hitRate() {
        long total = hitCount + missCount;
        return total == 0 ? 0.0 : (double) hitCount / total;
    }

    /**
     * Returns the average load duration in nanoseconds.
     *
     * @return average load duration
     */
    public long averageLoadNanos() {
        long total = loadSuccessCount + loadFailureCount;
        return total == 0 ? 0 : totalLoadNanos / total;
    }

    /**
     * Returns an empty statistics instance.
     *
     * @return zero-valued statistics
     */
    public static CacheStatistics empty() {
        return new CacheStatistics(0, 0, 0, 0, 0, 0);
    }
}
