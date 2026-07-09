package dev.starcore.starcore.foundation.optimization;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 性能分析器 - SSS级性能监控
 * 实时监控插件性能，自动检测性能瓶颈
 */
public final class PerformanceProfiler {
    private static final MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
    private static final ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();

    private final ConcurrentHashMap<String, OperationMetrics> metrics = new ConcurrentHashMap<>();
    private final AtomicLong totalOperations = new AtomicLong(0);
    private static final int MAX_METRICS_KEYS = 1000; // 限制最多保留的操作名称数

    /**
     * 记录操作执行时间
     */
    public <T> T profile(String operationName, ProfiledOperation<T> operation) throws Exception {
        Instant start = Instant.now();
        long startMemory = getUsedMemory();
        long startCpuTime = getCurrentThreadCpuTime();

        try {
            T result = operation.execute();

            // 记录成功指标
            recordSuccess(operationName, start, startMemory, startCpuTime);
            enforceMaxKeys(); // 防止无限增长
            return result;

        } catch (Exception e) {
            // 记录失败指标
            recordFailure(operationName, start, e);
            enforceMaxKeys();
            throw e; // E-127 fix: 原样抛出，不包装 RuntimeException
        }
    }

    /**
     * 限制最大指标数量，防止内存泄漏
     */
    private void enforceMaxKeys() {
        if (metrics.size() > MAX_METRICS_KEYS) {
            cleanup();
        }
    }

    /**
     * 记录成功操作
     */
    private void recordSuccess(
        String operationName,
        Instant start,
        long startMemory,
        long startCpuTime
    ) {
        Duration duration = Duration.between(start, Instant.now());
        long memoryUsed = getUsedMemory() - startMemory;
        long cpuTime = getCurrentThreadCpuTime() - startCpuTime;

        OperationMetrics operationMetrics = metrics.computeIfAbsent(
            operationName,
            k -> new OperationMetrics(operationName)
        );

        operationMetrics.recordSuccess(duration, memoryUsed, cpuTime);
        totalOperations.incrementAndGet();
    }

    /**
     * 记录失败操作
     */
    private void recordFailure(String operationName, Instant start, Exception error) {
        Duration duration = Duration.between(start, Instant.now());

        OperationMetrics operationMetrics = metrics.computeIfAbsent(
            operationName,
            k -> new OperationMetrics(operationName)
        );

        operationMetrics.recordFailure(duration, error);
        totalOperations.incrementAndGet();
    }

    /**
     * 获取操作统计
     */
    public OperationMetrics getMetrics(String operationName) {
        return metrics.get(operationName);
    }

    /**
     * 获取所有操作统计
     */
    public java.util.List<OperationMetrics> getAllMetrics() {
        return new java.util.ArrayList<>(metrics.values());
    }

    /**
     * 获取性能报告
     */
    public PerformanceReport getReport() {
        return new PerformanceReport(
            totalOperations.get(),
            new java.util.ArrayList<>(metrics.values()),
            getSystemMetrics()
        );
    }

    /**
     * 清理过期数据
     */
    public void cleanup() {
        metrics.entrySet().removeIf(entry -> entry.getValue().isStale());
    }

    private long getUsedMemory() {
        return memoryMXBean.getHeapMemoryUsage().getUsed();
    }

    private long getCurrentThreadCpuTime() {
        long cpuTime = threadMXBean.getCurrentThreadCpuTime();
        // JVM 不支持 CPU 时间监控时返回 -1，此时返回 0 避免 long 溢出导致大正数
        return cpuTime > 0 ? cpuTime : 0L;
    }

    private SystemMetrics getSystemMetrics() {
        return new SystemMetrics(
            memoryMXBean.getHeapMemoryUsage().getUsed(),
            memoryMXBean.getHeapMemoryUsage().getMax(),
            threadMXBean.getThreadCount(),
            threadMXBean.getPeakThreadCount()
        );
    }

    /**
     * 待分析的操作
     */
    @FunctionalInterface
    public interface ProfiledOperation<T> {
        T execute() throws Exception;
    }

    /**
     * 操作指标
     */
    public static class OperationMetrics {
        private final String operationName;
        private final AtomicLong successCount = new AtomicLong(0);
        private final AtomicLong failureCount = new AtomicLong(0);
        private final AtomicLong totalDurationNanos = new AtomicLong(0);
        private final AtomicLong totalMemoryUsed = new AtomicLong(0);
        private final AtomicLong totalCpuTime = new AtomicLong(0);
        private volatile long minDurationNanos = Long.MAX_VALUE;
        private volatile long maxDurationNanos = 0;
        private volatile Instant lastAccess = Instant.now();

        public OperationMetrics(String operationName) {
            this.operationName = operationName;
        }

        void recordSuccess(Duration duration, long memoryUsed, long cpuTime) {
            long nanos = duration.toNanos();

            successCount.incrementAndGet();
            totalDurationNanos.addAndGet(nanos);
            totalMemoryUsed.addAndGet(memoryUsed);
            totalCpuTime.addAndGet(cpuTime);

            updateMinMax(nanos);
            lastAccess = Instant.now();
        }

        void recordFailure(Duration duration, Exception error) {
            failureCount.incrementAndGet();
            lastAccess = Instant.now();
        }

        private synchronized void updateMinMax(long nanos) {
            if (nanos < minDurationNanos) {
                minDurationNanos = nanos;
            }
            if (nanos > maxDurationNanos) {
                maxDurationNanos = nanos;
            }
        }

        boolean isStale() {
            return Duration.between(lastAccess, Instant.now()).toHours() > 24;
        }

        public long getSuccessCount() {
            return successCount.get();
        }

        public long getFailureCount() {
            return failureCount.get();
        }

        public long getAverageDurationNanos() {
            long count = successCount.get();
            return count > 0 ? totalDurationNanos.get() / count : 0;
        }

        public Duration getAverageDuration() {
            return Duration.ofNanos(getAverageDurationNanos());
        }

        public String format() {
            return String.format(
                "%s: %d success, %d failures, avg %.2fms, min %.2fms, max %.2fms",
                operationName,
                successCount.get(),
                failureCount.get(),
                getAverageDurationNanos() / 1_000_000.0,
                minDurationNanos / 1_000_000.0,
                maxDurationNanos / 1_000_000.0
            );
        }
    }

    /**
     * 性能报告
     */
    public record PerformanceReport(
        long totalOperations,
        java.util.List<OperationMetrics> operations,
        SystemMetrics systemMetrics
    ) {
        public String format() {
            StringBuilder sb = new StringBuilder();
            sb.append("=== Performance Report ===\n");
            sb.append(String.format("Total Operations: %d\n", totalOperations));
            sb.append(systemMetrics.format()).append("\n");
            sb.append("\nOperation Metrics:\n");
            operations.forEach(op -> sb.append(op.format()).append("\n"));
            return sb.toString();
        }
    }

    /**
     * 系统指标
     */
    public record SystemMetrics(
        long usedMemory,
        long maxMemory,
        int threadCount,
        int peakThreadCount
    ) {
        public String format() {
            return String.format(
                "Memory: %.2fMB / %.2fMB, Threads: %d (peak: %d)",
                usedMemory / 1024.0 / 1024.0,
                maxMemory / 1024.0 / 1024.0,
                threadCount,
                peakThreadCount
            );
        }
    }
}
