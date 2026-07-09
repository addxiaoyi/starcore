package dev.starcore.starcore.module.split;

import org.bukkit.plugin.Plugin;

import java.util.logging.Logger;

/**
 * 国家分裂配置
 */
public class SplitConfig {
    private final Plugin plugin;
    private final Logger logger;

    // 基本设置
    private boolean enabled = true;
    private boolean cooldownEnabled = true;
    private long cooldownMillis = 7 * 24 * 60 * 60 * 1000L; // 7天

    // 领土限制
    private int minChunksToSplit = 4;           // 最少需要多少区块才能分裂
    private int minChunksRemaining = 4;         // 分裂后原国家最少保留的区块数

    // 费用设置
    private boolean costEnabled = true;
    private double baseCost = 500.0;            // 基础分裂费用
    private double perChunkCost = 100.0;        // 每个分离区块的额外费用

    // 请求设置
    private int requestExpirationMinutes = 1440; // 请求过期时间（分钟），默认24小时
    private int maxPendingRequestsPerNation = 1; // 每个国家最多待处理请求数

    // 权限设置
    private boolean leaderOnly = true;          // 是否只有领导人可以发起分裂

    public SplitConfig(Plugin plugin) {
        this(plugin, plugin.getLogger());
    }

    public SplitConfig(Plugin plugin, Logger logger) {
        this.plugin = plugin;
        this.logger = logger;
        loadConfig();
    }

    /**
     * 重新加载配置
     */
    public void reload() {
        loadConfig();
    }

    /**
     * 从配置文件加载设置
     */
    private void loadConfig() {
        // 加载 split 配置节
        enabled = plugin.getConfig().getBoolean("split.enabled", true);
        cooldownEnabled = plugin.getConfig().getBoolean("split.cooldown.enabled", true);
        cooldownMillis = plugin.getConfig().getLong("split.cooldown.hours", 168) * 60 * 60 * 1000L; // 默认7天

        minChunksToSplit = plugin.getConfig().getInt("split.limits.min-chunks-to-split", 4);
        minChunksRemaining = plugin.getConfig().getInt("split.limits.min-chunks-remaining", 4);

        costEnabled = plugin.getConfig().getBoolean("split.cost.enabled", true);
        baseCost = plugin.getConfig().getDouble("split.cost.base", 500.0);
        perChunkCost = plugin.getConfig().getDouble("split.cost.per-chunk", 100.0);

        requestExpirationMinutes = plugin.getConfig().getInt("split.request.expiration-minutes", 1440);
        maxPendingRequestsPerNation = plugin.getConfig().getInt("split.request.max-per-nation", 1);

        leaderOnly = plugin.getConfig().getBoolean("split.permission.leader-only", true);

        logger.info("SplitConfig loaded: enabled=" + enabled + ", minChunks=" + minChunksToSplit);
    }

    // ==================== Getters ====================

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isCooldownEnabled() {
        return cooldownEnabled;
    }

    public long getCooldownMillis() {
        return cooldownMillis;
    }

    public int getMinChunksToSplit() {
        return minChunksToSplit;
    }

    public int getMinChunksRemaining() {
        return minChunksRemaining;
    }

    public boolean isCostEnabled() {
        return costEnabled;
    }

    public double getBaseCost() {
        return baseCost;
    }

    public double getPerChunkCost() {
        return perChunkCost;
    }

    public int getRequestExpirationMinutes() {
        return requestExpirationMinutes;
    }

    public int getMaxPendingRequestsPerNation() {
        return maxPendingRequestsPerNation;
    }

    public boolean isLeaderOnly() {
        return leaderOnly;
    }

    /**
     * 获取分裂冷却时间（小时）
     */
    public long getCooldownHours() {
        return cooldownMillis / (60 * 60 * 1000);
    }

    /**
     * 计算分裂总费用
     * @param chunkCount 分离的区块数量
     * @return 总费用
     */
    public double calculateTotalCost(int chunkCount) {
        if (!costEnabled) {
            return 0;
        }
        return baseCost + (perChunkCost * chunkCount);
    }

    // ==================== 配置验证 ====================

    /**
     * 验证配置完整性
     */
    public boolean validate() {
        if (minChunksToSplit <= 0) {
            logger.warning("Invalid split.limits.min-chunks-to-split: " + minChunksToSplit);
            return false;
        }
        if (minChunksRemaining <= 0) {
            logger.warning("Invalid split.limits.min-chunks-remaining: " + minChunksRemaining);
            return false;
        }
        if (minChunksRemaining >= minChunksToSplit * 2) {
            logger.warning("min-chunks-remaining should be less than min-chunks-to-split * 2");
        }
        return true;
    }
}