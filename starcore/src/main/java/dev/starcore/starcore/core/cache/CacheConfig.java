package dev.starcore.starcore.core.cache;

import java.time.Duration;
import java.util.Objects;

/**
 * Configuration for a managed cache.
 *
 * <p>Provides control over cache size, eviction policy, and reference types.
 */
public final class CacheConfig {
    private final int maxSize;
    private final Duration expireAfterAccess;
    private final Duration expireAfterWrite;
    private final boolean weakKeys;
    private final boolean softValues;
    private final boolean recordStats;

    private CacheConfig(Builder builder) {
        this.maxSize = builder.maxSize;
        this.expireAfterAccess = builder.expireAfterAccess;
        this.expireAfterWrite = builder.expireAfterWrite;
        this.weakKeys = builder.weakKeys;
        this.softValues = builder.softValues;
        this.recordStats = builder.recordStats;
    }

    public int maxSize() {
        return maxSize;
    }

    public Duration expireAfterAccess() {
        return expireAfterAccess;
    }

    public Duration expireAfterWrite() {
        return expireAfterWrite;
    }

    public boolean weakKeys() {
        return weakKeys;
    }

    public boolean softValues() {
        return softValues;
    }

    public boolean recordStats() {
        return recordStats;
    }

    /**
     * Returns a new builder with default settings.
     *
     * @return builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns a config with default settings (max 1000 entries, no expiration).
     *
     * @return default config
     */
    public static CacheConfig defaults() {
        return builder().build();
    }

    public static final class Builder {
        private int maxSize = 1000;
        private Duration expireAfterAccess = null;
        private Duration expireAfterWrite = null;
        private boolean weakKeys = false;
        private boolean softValues = false;
        private boolean recordStats = true;

        private Builder() {
        }

        /**
         * Sets the maximum number of entries the cache may contain.
         *
         * @param maxSize maximum size (must be positive)
         * @return this builder
         */
        public Builder maxSize(int maxSize) {
            if (maxSize <= 0) {
                throw new IllegalArgumentException("maxSize must be positive");
            }
            this.maxSize = maxSize;
            return this;
        }

        /**
         * Sets the duration after which an entry should be evicted
         * following the last access (read or write).
         *
         * @param duration access-based expiration duration
         * @return this builder
         */
        public Builder expireAfterAccess(Duration duration) {
            this.expireAfterAccess = Objects.requireNonNull(duration, "duration");
            return this;
        }

        /**
         * Sets the duration after which an entry should be evicted
         * following its creation or last update.
         *
         * @param duration write-based expiration duration
         * @return this builder
         */
        public Builder expireAfterWrite(Duration duration) {
            this.expireAfterWrite = Objects.requireNonNull(duration, "duration");
            return this;
        }

        /**
         * Enables weak keys, allowing entries to be garbage collected
         * when keys are no longer strongly referenced elsewhere.
         *
         * @return this builder
         */
        public Builder weakKeys() {
            this.weakKeys = true;
            return this;
        }

        /**
         * Enables soft values, allowing entries to be evicted by the GC
         * when memory is low.
         *
         * @return this builder
         */
        public Builder softValues() {
            this.softValues = true;
            return this;
        }

        /**
         * Disables statistics recording for this cache.
         *
         * <p>By default, stats are enabled. Disable them for
         * performance-critical caches with millions of operations.
         *
         * @return this builder
         */
        public Builder disableStats() {
            this.recordStats = false;
            return this;
        }

        /**
         * Builds the cache config.
         *
         * @return immutable cache config
         */
        public CacheConfig build() {
            return new CacheConfig(this);
        }
    }
}
