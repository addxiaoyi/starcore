package dev.starcore.starcore.storage;

import java.math.BigDecimal;

/**
 * 仓库系统配置
 * 从配置文件加载的所有配置项
 */
public class StorageConfig {
    // 远程访问配置
    private final boolean remoteAccessEnabled;
    private final double maxRemoteDistance;
    private final BigDecimal remoteAccessCost;

    // 容量配置
    private final int personalMaxLevel;
    private final int nationMaxLevel;
    private final double upgradeCostMultiplier;

    // 日志配置
    private final boolean logsEnabled;
    private final int logRetentionDays;

    // 其他配置
    private final boolean autoSort;
    private final boolean allowSharing;

    /**
     * 构造函数
     */
    public StorageConfig(boolean remoteAccessEnabled, double maxRemoteDistance,
                         BigDecimal remoteAccessCost, int personalMaxLevel,
                         int nationMaxLevel, double upgradeCostMultiplier,
                         boolean logsEnabled, int logRetentionDays,
                         boolean autoSort, boolean allowSharing) {
        this.remoteAccessEnabled = remoteAccessEnabled;
        this.maxRemoteDistance = maxRemoteDistance;
        this.remoteAccessCost = remoteAccessCost;
        this.personalMaxLevel = personalMaxLevel;
        this.nationMaxLevel = nationMaxLevel;
        this.upgradeCostMultiplier = upgradeCostMultiplier;
        this.logsEnabled = logsEnabled;
        this.logRetentionDays = logRetentionDays;
        this.autoSort = autoSort;
        this.allowSharing = allowSharing;
    }

    // ==================== Getters ====================

    public boolean isRemoteAccessEnabled() {
        return remoteAccessEnabled;
    }

    public double getMaxRemoteDistance() {
        return maxRemoteDistance;
    }

    public boolean hasDistanceLimit() {
        return maxRemoteDistance > 0;
    }

    public BigDecimal getRemoteAccessCost() {
        return remoteAccessCost;
    }

    public int getPersonalMaxLevel() {
        return personalMaxLevel;
    }

    public int getNationMaxLevel() {
        return nationMaxLevel;
    }

    public double getUpgradeCostMultiplier() {
        return upgradeCostMultiplier;
    }

    public boolean isLogsEnabled() {
        return logsEnabled;
    }

    public int getLogRetentionDays() {
        return logRetentionDays;
    }

    public boolean isAutoSort() {
        return autoSort;
    }

    public boolean isAllowSharing() {
        return allowSharing;
    }

    // ==================== 默认配置 ====================

    /**
     * 创建默认配置
     * @return 默认配置实例
     */
    public static StorageConfig createDefault() {
        return new StorageConfig(
                true,                          // 启用远程访问
                1000.0,                        // 最大距离1000格
                BigDecimal.valueOf(100),       // 远程访问费用100
                10,                            // 个人仓库最大10级
                15,                            // 国家仓库最大15级
                1.5,                           // 升级费用倍率1.5
                true,                          // 启用日志
                30,                            // 日志保留30天
                true,                          // 启用自动整理
                true                           // 允许共享
        );
    }

    /**
     * Builder模式创建配置
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private boolean remoteAccessEnabled = true;
        private double maxRemoteDistance = 1000.0;
        private BigDecimal remoteAccessCost = BigDecimal.valueOf(100);
        private int personalMaxLevel = 10;
        private int nationMaxLevel = 15;
        private double upgradeCostMultiplier = 1.5;
        private boolean logsEnabled = true;
        private int logRetentionDays = 30;
        private boolean autoSort = true;
        private boolean allowSharing = true;

        public Builder remoteAccessEnabled(boolean enabled) {
            this.remoteAccessEnabled = enabled;
            return this;
        }

        public Builder maxRemoteDistance(double distance) {
            this.maxRemoteDistance = distance;
            return this;
        }

        public Builder remoteAccessCost(BigDecimal cost) {
            this.remoteAccessCost = cost;
            return this;
        }

        public Builder personalMaxLevel(int level) {
            this.personalMaxLevel = level;
            return this;
        }

        public Builder nationMaxLevel(int level) {
            this.nationMaxLevel = level;
            return this;
        }

        public Builder upgradeCostMultiplier(double multiplier) {
            this.upgradeCostMultiplier = multiplier;
            return this;
        }

        public Builder logsEnabled(boolean enabled) {
            this.logsEnabled = enabled;
            return this;
        }

        public Builder logRetentionDays(int days) {
            this.logRetentionDays = days;
            return this;
        }

        public Builder autoSort(boolean enabled) {
            this.autoSort = enabled;
            return this;
        }

        public Builder allowSharing(boolean enabled) {
            this.allowSharing = enabled;
            return this;
        }

        public StorageConfig build() {
            return new StorageConfig(
                    remoteAccessEnabled, maxRemoteDistance, remoteAccessCost,
                    personalMaxLevel, nationMaxLevel, upgradeCostMultiplier,
                    logsEnabled, logRetentionDays, autoSort, allowSharing
            );
        }
    }

    @Override
    public String toString() {
        return "StorageConfig{" +
                "remoteAccess=" + remoteAccessEnabled +
                ", maxDistance=" + maxRemoteDistance +
                ", remoteCost=" + remoteAccessCost +
                ", personalMaxLv=" + personalMaxLevel +
                ", nationMaxLv=" + nationMaxLevel +
                ", upgradeMult=" + upgradeCostMultiplier +
                ", logs=" + logsEnabled +
                ", retention=" + logRetentionDays +
                '}';
    }
}
