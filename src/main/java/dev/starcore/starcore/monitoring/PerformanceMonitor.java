package dev.starcore.starcore.monitoring;

import org.bukkit.Bukkit;
import org.bukkit.World;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 性能监控系统
 * 监控TPS、内存、缓存等关键指标
 */
public class PerformanceMonitor {

    private static final int TPS_SAMPLE_SIZE = 20;
    private final Deque<Long> tpsSamples = new ArrayDeque<>(TPS_SAMPLE_SIZE);
    private long lastTick = System.currentTimeMillis();

    // 缓存统计
    private final Map<String, CacheMetrics> cacheMetrics = new ConcurrentHashMap<>();

    /**
     * 更新TPS采样
     * 应该每tick调用一次
     */
    public void updateTPS() {
        long now = System.currentTimeMillis();
        long diff = now - lastTick;

        tpsSamples.addLast(diff);
        if (tpsSamples.size() > TPS_SAMPLE_SIZE) {
            tpsSamples.removeFirst();
        }

        lastTick = now;
    }

    /**
     * 获取当前TPS
     */
    public double getCurrentTPS() {
        if (tpsSamples.isEmpty()) {
            return 20.0;
        }

        long sum = 0;
        for (long sample : tpsSamples) {
            sum += sample;
        }

        double avgTickTime = (double) sum / tpsSamples.size();
        return Math.min(20.0, 1000.0 / avgTickTime);
    }

    /**
     * 获取TPS健康状态
     */
    public TPSHealth getTPSHealth() {
        double tps = getCurrentTPS();

        if (tps >= 19.5) return TPSHealth.EXCELLENT;
        if (tps >= 18.0) return TPSHealth.GOOD;
        if (tps >= 16.0) return TPSHealth.FAIR;
        if (tps >= 14.0) return TPSHealth.POOR;
        return TPSHealth.CRITICAL;
    }

    /**
     * 获取内存使用情况
     */
    public MemoryMetrics getMemoryMetrics() {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();

        long used = heapUsage.getUsed();
        long max = heapUsage.getMax();
        long committed = heapUsage.getCommitted();

        double usagePercent = (double) used / max * 100;

        return new MemoryMetrics(
            used / 1024 / 1024,      // MB
            max / 1024 / 1024,       // MB
            committed / 1024 / 1024, // MB
            usagePercent
        );
    }

    /**
     * 获取世界统计
     */
    public WorldMetrics getWorldMetrics() {
        int totalEntities = 0;
        int totalChunks = 0;
        int totalPlayers = Bukkit.getOnlinePlayers().size();

        for (World world : Bukkit.getWorlds()) {
            totalEntities += world.getEntityCount();
            totalChunks += world.getLoadedChunks().length;
        }

        return new WorldMetrics(
            Bukkit.getWorlds().size(),
            totalPlayers,
            totalEntities,
            totalChunks
        );
    }

    /**
     * 注册缓存指标
     */
    public void registerCache(String name, CacheMetrics metrics) {
        cacheMetrics.put(name, metrics);
    }

    /**
     * 获取所有缓存指标
     */
    public Map<String, CacheMetrics> getCacheMetrics() {
        return Collections.unmodifiableMap(cacheMetrics);
    }

    /**
     * 获取综合性能评分（0-100）
     */
    public double getPerformanceScore() {
        double tpsScore = (getCurrentTPS() / 20.0) * 40; // 40分

        MemoryMetrics memory = getMemoryMetrics();
        double memoryScore = (1.0 - memory.usagePercent() / 100.0) * 30; // 30分

        WorldMetrics world = getWorldMetrics();
        double entityScore = Math.max(0, 1.0 - (world.totalEntities() / 5000.0)) * 15; // 15分

        double chunkScore = Math.max(0, 1.0 - (world.totalChunks() / 10000.0)) * 15; // 15分

        return Math.min(100, tpsScore + memoryScore + entityScore + chunkScore);
    }

    /**
     * 生成性能报告
     */
    public PerformanceReport generateReport() {
        return new PerformanceReport(
            getCurrentTPS(),
            getTPSHealth(),
            getMemoryMetrics(),
            getWorldMetrics(),
            new ConcurrentHashMap<>(cacheMetrics),
            getPerformanceScore()
        );
    }

    // ==================== 数据类 ====================

    /**
     * TPS健康状态
     */
    public enum TPSHealth {
        EXCELLENT("§a§l优秀", "TPS >= 19.5"),
        GOOD("§a良好", "TPS >= 18.0"),
        FAIR("§e一般", "TPS >= 16.0"),
        POOR("§6较差", "TPS >= 14.0"),
        CRITICAL("§c§l危急", "TPS < 14.0");

        private final String displayName;
        private final String description;

        TPSHealth(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * 内存指标
     */
    public record MemoryMetrics(
        long usedMB,
        long maxMB,
        long committedMB,
        double usagePercent
    ) {
        public String getHealthStatus() {
            if (usagePercent < 60) return "§a健康";
            if (usagePercent < 75) return "§e警告";
            if (usagePercent < 90) return "§6危险";
            return "§c§l严重";
        }
    }

    /**
     * 世界指标
     */
    public record WorldMetrics(
        int worldCount,
        int playerCount,
        int totalEntities,
        int totalChunks
    ) {}

    /**
     * 缓存指标
     */
    public record CacheMetrics(
        long size,
        long hits,
        long misses,
        double hitRate
    ) {
        public String getHealthStatus() {
            if (hitRate >= 0.9) return "§a优秀";
            if (hitRate >= 0.7) return "§e良好";
            if (hitRate >= 0.5) return "§6一般";
            return "§c较差";
        }
    }

    /**
     * 性能报告
     */
    public record PerformanceReport(
        double tps,
        TPSHealth tpsHealth,
        MemoryMetrics memory,
        WorldMetrics world,
        Map<String, CacheMetrics> caches,
        double performanceScore
    ) {
        public String getOverallHealth() {
            if (performanceScore >= 90) return "§a§l优秀";
            if (performanceScore >= 75) return "§a良好";
            if (performanceScore >= 60) return "§e一般";
            if (performanceScore >= 45) return "§6较差";
            return "§c§l危急";
        }
    }
}
