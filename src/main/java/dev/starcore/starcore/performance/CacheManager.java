package dev.starcore.starcore.performance;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;

import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * 高性能缓存管理器
 * 使用 Caffeine 实现
 */
public final class CacheManager {

    /**
     * 创建缓存构建器
     */
    public static <K, V> CacheBuilder<K, V> builder() {
        return new CacheBuilder<>();
    }

    /**
     * 缓存构建器
     */
    public static class CacheBuilder<K, V> {
        private long maximumSize = 1000;
        private long expireAfterWrite = 3600;
        private long expireAfterAccess = -1;
        private TimeUnit timeUnit = TimeUnit.SECONDS;
        private boolean recordStats = false;

        public CacheBuilder<K, V> maximumSize(long size) {
            this.maximumSize = size;
            return this;
        }

        public CacheBuilder<K, V> expireAfterWrite(long duration, TimeUnit unit) {
            this.expireAfterWrite = duration;
            this.timeUnit = unit;
            return this;
        }

        public CacheBuilder<K, V> expireAfterAccess(long duration, TimeUnit unit) {
            this.expireAfterAccess = duration;
            this.timeUnit = unit;
            return this;
        }

        public CacheBuilder<K, V> recordStats() {
            this.recordStats = true;
            return this;
        }

        public SmartCache<K, V> build() {
            Caffeine<Object, Object> builder = Caffeine.newBuilder()
                .maximumSize(maximumSize)
                .expireAfterWrite(expireAfterWrite, timeUnit);

            if (expireAfterAccess > 0) {
                builder.expireAfterAccess(expireAfterAccess, timeUnit);
            }

            if (recordStats) {
                builder.recordStats();
            }

            return new SmartCache<>(builder.build());
        }
    }

    /**
     * 智能缓存包装器
     */
    public static class SmartCache<K, V> {
        private final Cache<K, V> cache;

        private SmartCache(Cache<K, V> cache) {
            this.cache = cache;
        }

        /**
         * 获取缓存值
         */
        public V get(K key) {
            return cache.getIfPresent(key);
        }

        /**
         * 获取或计算缓存值
         */
        public V get(K key, Function<K, V> loader) {
            return cache.get(key, loader);
        }

        /**
         * 设置缓存值
         */
        public void put(K key, V value) {
            cache.put(key, value);
        }

        /**
         * 移除缓存值
         */
        public void remove(K key) {
            cache.invalidate(key);
        }

        /**
         * 清空缓存
         */
        public void clear() {
            cache.invalidateAll();
        }

        /**
         * 获取缓存大小
         */
        public long size() {
            return cache.estimatedSize();
        }

        /**
         * 获取统计信息
         */
        public CacheStats getStats() {
            return cache.stats();
        }

        /**
         * 清理过期缓存
         */
        public void cleanUp() {
            cache.cleanUp();
        }
    }
}
