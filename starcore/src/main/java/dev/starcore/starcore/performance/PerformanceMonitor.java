package dev.starcore.starcore.performance;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 性能监控器
 * 实时监控插件性能
 */
public final class PerformanceMonitor {
    // 性能指标
    private final ConcurrentHashMap<String, PerformanceMetric> metrics = new ConcurrentHashMap<>();

    // 全局统计
    private final AtomicLong totalOperations = new AtomicLong(0);
    private final AtomicLong totalTime = new AtomicLong(0);

    /**
     * 记录操作
     */
    public void recordOperation(String operation, long durationMs) {
        totalOperations.incrementAndGet();
        totalTime.addAndGet(durationMs);

        PerformanceMetric metric = metrics.computeIfAbsent(
            operation,
            k -> new PerformanceMetric(operation)
        );

        metric.record(durationMs);
    }

    /**
     * 开始计时
     */
    public Timer startTimer(String operation) {
        return new Timer(operation, this);
    }

    /**
     * 获取指标
     */
    public PerformanceMetric getMetric(String operation) {
        return metrics.get(operation);
    }

    /**
     * 获取所有指标
     */
    public ConcurrentHashMap<String, PerformanceMetric> getAllMetrics() {
        return new ConcurrentHashMap<>(metrics);
    }

    /**
     * 获取全局统计
     */
    public GlobalStats getGlobalStats() {
        long total = totalOperations.get();
        long time = totalTime.get();
        double avgTime = total > 0 ? (double) time / total : 0;

        return new GlobalStats(total, time, avgTime);
    }

    /**
     * 重置统计
     */
    public void reset() {
        metrics.clear();
        totalOperations.set(0);
        totalTime.set(0);
    }

    /**
     * 性能指标
     */
    public static class PerformanceMetric {
        private final String name;
        private final AtomicLong count = new AtomicLong(0);
        private final AtomicLong totalTime = new AtomicLong(0);
        private final AtomicLong minTime = new AtomicLong(Long.MAX_VALUE);
        private final AtomicLong maxTime = new AtomicLong(0);

        public PerformanceMetric(String name) {
            this.name = name;
        }

        public void record(long durationMs) {
            count.incrementAndGet();
            totalTime.addAndGet(durationMs);

            // 更新最小值
            long currentMin;
            do {
                currentMin = minTime.get();
            } while (durationMs < currentMin && !minTime.compareAndSet(currentMin, durationMs));

            // 更新最大值
            long currentMax;
            do {
                currentMax = maxTime.get();
            } while (durationMs > currentMax && !maxTime.compareAndSet(currentMax, durationMs));
        }

        public String getName() { return name; }
        public long getCount() { return count.get(); }
        public long getTotalTime() { return totalTime.get(); }
        public long getMinTime() {
            long min = minTime.get();
            return min == Long.MAX_VALUE ? 0 : min;
        }
        public long getMaxTime() { return maxTime.get(); }
        public double getAvgTime() {
            long c = count.get();
            return c > 0 ? (double) totalTime.get() / c : 0;
        }
    }

    /**
     * 计时器
     */
    public static class Timer implements AutoCloseable {
        private final String operation;
        private final PerformanceMonitor monitor;
        private final long startTime;

        public Timer(String operation, PerformanceMonitor monitor) {
            this.operation = operation;
            this.monitor = monitor;
            this.startTime = System.currentTimeMillis();
        }

        @Override
        public void close() {
            long duration = System.currentTimeMillis() - startTime;
            monitor.recordOperation(operation, duration);
        }
    }

    /**
     * 全局统计
     */
    public record GlobalStats(
        long totalOperations,
        long totalTime,
        double avgTime
    ) {}
}
