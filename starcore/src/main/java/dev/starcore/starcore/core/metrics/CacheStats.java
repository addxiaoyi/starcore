package dev.starcore.starcore.core.metrics;

/**
 * Statistics for a cache.
 *
 * @param hits number of cache hits
 * @param misses number of cache misses
 * @param hitRate hit rate as a value between 0.0 and 1.0
 */
public record CacheStats(
    long hits,
    long misses,
    double hitRate
) {
}
