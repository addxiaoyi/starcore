package dev.starcore.starcore.core.cache;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Generic cache interface for key-value storage with eviction policies.
 *
 * <p>All implementations must be thread-safe.
 *
 * @param <K> key type
 * @param <V> value type
 */
public interface Cache<K, V> {

    /**
     * Returns the value associated with the key, if present.
     *
     * @param key the cache key
     * @return the cached value, or empty if not present
     */
    Optional<V> get(K key);

    /**
     * Returns the value associated with the key, computing it if absent.
     *
     * @param key the cache key
     * @param loader function to compute the value if absent
     * @return the cached or computed value
     */
    V get(K key, Function<? super K, ? extends V> loader);

    /**
     * Associates the value with the key in the cache.
     *
     * @param key the cache key
     * @param value the value to cache
     */
    void put(K key, V value);

    /**
     * Removes the entry for the key, if present.
     *
     * @param key the cache key
     */
    void invalidate(K key);

    /**
     * Removes all entries from the cache.
     */
    void invalidateAll();

    /**
     * Returns an immutable snapshot of all cache entries.
     *
     * <p>This is an expensive operation and should be used sparingly.
     *
     * @return snapshot of cache contents
     */
    Map<K, V> snapshot();

    /**
     * Returns the approximate number of entries in the cache.
     *
     * @return entry count
     */
    long size();

    /**
     * Returns statistics for this cache, if stats are enabled.
     *
     * @return cache statistics
     */
    CacheStatistics statistics();

    /**
     * Evicts the specified number of least recently used entries.
     * Used for LRU-based eviction under memory pressure.
     *
     * @param count number of entries to evict
     */
    default void evictLRU(long count) {
        // Default implementation: subclasses should override for efficiency
    }

    /**
     * Cleans up expired entries based on access or write time.
     *
     * @param now current timestamp
     * @param expireAfterAccess expiration after last access, or null
     * @param expireAfterWrite expiration after last write, or null
     */
    default void cleanupExpired(Instant now, Duration expireAfterAccess, Duration expireAfterWrite) {
        // Default implementation: subclasses should override for efficiency
    }

    /**
     * Simple unbounded concurrent map-based cache implementation.
     * Used when no eviction policy is needed.
     */
    static <K, V> Cache<K, V> concurrent() {
        return new ConcurrentMapCache<>(new ConcurrentHashMap<>());
    }
}
