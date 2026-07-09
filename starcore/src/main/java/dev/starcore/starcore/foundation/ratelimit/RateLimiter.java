package dev.starcore.starcore.foundation.ratelimit;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 速率限制器 - SSS级性能保护
 * 防止玩家滥用命令和API，保护服务器性能
 */
public final class RateLimiter {
    private final ConcurrentHashMap<UUID, RateLimitBucket> buckets = new ConcurrentHashMap<>();
    private final int maxRequests;
    private final Duration window;

    public RateLimiter(int maxRequests, Duration window) {
        this.maxRequests = maxRequests;
        this.window = window;
    }

    /**
     * 检查是否允许请求
     */
    public boolean allowRequest(UUID playerId) {
        RateLimitBucket bucket = buckets.computeIfAbsent(
            playerId,
            k -> new RateLimitBucket(maxRequests, window)
        );

        return bucket.tryConsume();
    }

    /**
     * 获取剩余配额
     */
    public int getRemainingQuota(UUID playerId) {
        RateLimitBucket bucket = buckets.get(playerId);
        return bucket != null ? bucket.getRemaining() : maxRequests;
    }

    /**
     * 获取重置时间
     */
    public Duration getResetTime(UUID playerId) {
        RateLimitBucket bucket = buckets.get(playerId);
        return bucket != null ? bucket.getResetTime() : Duration.ZERO;
    }

    /**
     * 清理过期的桶
     */
    public void cleanup() {
        buckets.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    /**
     * 速率限制桶（令牌桶算法）
     */
    private static class RateLimitBucket {
        private final int capacity;
        private final Duration refillInterval;
        private int tokens;
        private Instant lastRefill;

        RateLimitBucket(int capacity, Duration refillInterval) {
            this.capacity = capacity;
            this.refillInterval = refillInterval;
            this.tokens = capacity;
            this.lastRefill = Instant.now();
        }

        synchronized boolean tryConsume() {
            refill();

            if (tokens > 0) {
                tokens--;
                return true;
            }

            return false;
        }

        synchronized int getRemaining() {
            refill();
            return tokens;
        }

        synchronized Duration getResetTime() {
            if (tokens >= capacity) {
                return Duration.ZERO;
            }

            Duration elapsed = Duration.between(lastRefill, Instant.now());
            return refillInterval.minus(elapsed);
        }

        boolean isExpired() {
            Duration elapsed = Duration.between(lastRefill, Instant.now());
            return elapsed.compareTo(refillInterval.multipliedBy(2)) > 0;
        }

        private void refill() {
            Instant now = Instant.now();
            Duration elapsed = Duration.between(lastRefill, now);

            if (elapsed.compareTo(refillInterval) >= 0) {
                tokens = capacity;
                lastRefill = now;
            }
        }
    }

    /**
     * 常用的速率限制器预设
     */
    public static class Presets {
        /**
         * 命令速率限制：每秒5个命令
         */
        public static RateLimiter commandLimit() {
            return new RateLimiter(5, Duration.ofSeconds(1));
        }

        /**
         * GUI操作限制：每秒10次点击
         */
        public static RateLimiter guiClickLimit() {
            return new RateLimiter(10, Duration.ofSeconds(1));
        }

        /**
         * 聊天限制：每分钟30条消息
         */
        public static RateLimiter chatLimit() {
            return new RateLimiter(30, Duration.ofMinutes(1));
        }

        /**
         * API调用限制：每分钟100次
         */
        public static RateLimiter apiLimit() {
            return new RateLimiter(100, Duration.ofMinutes(1));
        }
    }
}
