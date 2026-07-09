package dev.starcore.starcore.foundation.metrics;

import org.slf4j.Logger;

import java.lang.management.ManagementFactory;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 性能监控服务
 *
 * 记录结算耗时、成功率等指标
 */
public final class MetricsCollector {

    private final Logger logger;
    private final ConcurrentMap<String, AtomicLong> counters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AtomicLong> timers = new ConcurrentHashMap<>();

    public MetricsCollector(Logger logger) {
        this.logger = logger;
    }

    /**
     * 记录成功
     */
    public void recordSuccess(String metric) {
        counters.computeIfAbsent(metric + ":success", k -> new AtomicLong()).incrementAndGet();
    }

    /**
     * 记录失败
     */
    public void recordFailure(String metric) {
        counters.computeIfAbsent(metric + ":failure", k -> new AtomicLong()).incrementAndGet();
    }

    /**
     * 记录耗时（毫秒）
     */
    public void recordDuration(String metric, long durationMs) {
        timers.computeIfAbsent(metric + ":duration", k -> new AtomicLong()).addAndGet(durationMs);
        counters.computeIfAbsent(metric + ":count", k -> new AtomicLong()).incrementAndGet();
    }

    /**
     * 获取指标报告
     */
    public MetricsReport getReport(String metric) {
        long success = counters.getOrDefault(metric + ":success", new AtomicLong()).get();
        long failure = counters.getOrDefault(metric + ":failure", new AtomicLong()).get();
        long total = success + failure;
        long duration = timers.getOrDefault(metric + ":duration", new AtomicLong()).get();
        long count = counters.getOrDefault(metric + ":count", new AtomicLong()).get();
        double avgDuration = count > 0 ? (double) duration / count : 0;

        return new MetricsReport(
            metric,
            total,
            success,
            failure,
            total > 0 ? (double) success / total : 0,
            avgDuration
        );
    }

    /**
     * 获取运行时指标
     */
    public RuntimeMetrics getRuntimeMetrics() {
        var runtime = ManagementFactory.getRuntimeMXBean();
        var mem = ManagementFactory.getMemoryMXBean();
        var gc = ManagementFactory.getGarbageCollectorMXBeans();

        long heapUsed = mem.getHeapMemoryUsage().getUsed();
        long heapMax = mem.getHeapMemoryUsage().getMax();
        long uptime = runtime.getUptime();

        return new RuntimeMetrics(
            heapUsed,
            heapMax,
            heapMax > 0 ? (double) heapUsed / heapMax : 0,
            uptime,
            gc.stream().mapToLong(g -> g.getCollectionCount()).sum()
        );
    }

    /**
     * 打印报告
     */
    public void logReport(String metric) {
        MetricsReport report = getReport(metric);
        logger.info("Metrics[{}]: total={}, success={}, failure={}, rate={:.2f}%, avgDuration={:.2f}ms",
            report.metric(),
            report.total(),
            report.success(),
            report.failure(),
            report.successRate() * 100,
            report.avgDurationMs()
        );
    }

    public record MetricsReport(
        String metric,
        long total,
        long success,
        long failure,
        double successRate,
        double avgDurationMs
    ) {}

    public record RuntimeMetrics(
        long heapUsed,
        long heapMax,
        double heapUsageRatio,
        long uptimeMs,
        long gcCount
    ) {}
}
