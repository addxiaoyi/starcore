package dev.starcore.starcore.performance;

import java.lang.ref.SoftReference;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存优化管理器
 * 减少内存使用和GC压力
 */
public final class MemoryOptimizer {
    private static final java.util.logging.Logger LOGGER = java.util.logging.Logger.getLogger("StarCore.MemoryOptimizer");

    // 使用软引用的缓存（内存不足时自动清理）
    private final Map<Object, SoftReference<Object>> softCache = new ConcurrentHashMap<>();

    // 定期清理任务
    private final AsyncTaskManager asyncManager;

    // 内存阈值（百分比）
    private final double memoryThreshold = 0.75; // 75%

    public MemoryOptimizer(AsyncTaskManager asyncManager) {
        this.asyncManager = asyncManager;
        startMemoryMonitor();
    }

    /**
     * 启动内存监控
     */
    private void startMemoryMonitor() {
        asyncManager.runRepeating(() -> {
            double usage = getMemoryUsagePercent();

            if (usage > memoryThreshold) {
                // 内存使用超过阈值，执行清理
                performCleanup();
            }
        }, 30, 30, java.util.concurrent.TimeUnit.SECONDS);
    }

    /**
     * 获取内存使用率
     */
    public double getMemoryUsagePercent() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;

        return (double) usedMemory / maxMemory;
    }

    /**
     * 执行清理
     */
    public void performCleanup() {
        // 1. 清理软引用缓存
        cleanSoftCache();

        // 2. 建议GC（不强制）
        System.gc();

        // 3. 记录清理信息
        long freed = getFreedMemory();
        LOGGER.info("[MemoryOptimizer] 清理完成，释放 " + (freed / 1024 / 1024) + " MB");
    }

    /**
     * 清理软引用缓存
     */
    private void cleanSoftCache() {
        softCache.entrySet().removeIf(entry -> entry.getValue().get() == null);
    }

    /**
     * 获取释放的内存
     */
    private long getFreedMemory() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.freeMemory();
    }

    /**
     * 使用软引用缓存
     */
    public <K, V> void putSoft(K key, V value) {
        softCache.put(key, new SoftReference<>(value));
    }

    /**
     * 获取软引用缓存
     */
    @SuppressWarnings("unchecked")
    public <K, V> V getSoft(K key) {
        SoftReference<Object> ref = softCache.get(key);
        if (ref == null) return null;

        Object value = ref.get();
        if (value == null) {
            // 已被GC回收
            softCache.remove(key);
            return null;
        }

        return (V) value;
    }

    /**
     * 获取内存统计
     */
    public MemoryStats getStats() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;

        return new MemoryStats(
            maxMemory / 1024 / 1024,      // MB
            totalMemory / 1024 / 1024,    // MB
            usedMemory / 1024 / 1024,     // MB
            freeMemory / 1024 / 1024,     // MB
            getMemoryUsagePercent() * 100
        );
    }

    /**
     * 内存统计
     */
    public record MemoryStats(
        long maxMemoryMB,
        long totalMemoryMB,
        long usedMemoryMB,
        long freeMemoryMB,
        double usagePercent
    ) {}
}
