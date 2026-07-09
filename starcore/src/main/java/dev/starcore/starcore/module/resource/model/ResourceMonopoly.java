package dev.starcore.starcore.module.resource.model;

import dev.starcore.starcore.module.nation.model.NationId;

import java.time.Instant;
import java.util.Objects;

/**
 * 资源垄断记录
 * 记录某个国家对某种资源的垄断状态
 */
public final class ResourceMonopoly {
    private final NationId nationId;
    private final String resourceId;
    private double marketShare;
    private final Instant startTime;
    private final MonopolyLevel level;
    private double dailyRevenue;

    public ResourceMonopoly(NationId nationId, String resourceId, double marketShare,
                            Instant startTime, MonopolyLevel level) {
        this.nationId = Objects.requireNonNull(nationId, "nationId");
        this.resourceId = Objects.requireNonNull(resourceId, "resourceId");
        this.marketShare = Math.max(0.0, Math.min(1.0, marketShare));
        this.startTime = Objects.requireNonNull(startTime, "startTime");
        this.level = Objects.requireNonNull(level, "level");
        this.dailyRevenue = 0.0;
    }

    /**
     * 获取垄断国家ID
     */
    public NationId nationId() {
        return nationId;
    }

    /**
     * 获取资源ID
     */
    public String resourceId() {
        return resourceId;
    }

    /**
     * 获取市场份额（0.0-1.0）
     */
    public double marketShare() {
        return marketShare;
    }

    /**
     * 设置市场份额
     */
    public void setMarketShare(double marketShare) {
        this.marketShare = Math.max(0.0, Math.min(1.0, marketShare));
    }

    /**
     * 获取垄断开始时间
     */
    public Instant startTime() {
        return startTime;
    }

    /**
     * 获取垄断等级
     */
    public MonopolyLevel level() {
        return level;
    }

    /**
     * 获取每日收益
     */
    public double dailyRevenue() {
        return dailyRevenue;
    }

    /**
     * 设置每日收益
     */
    public void setDailyRevenue(double dailyRevenue) {
        this.dailyRevenue = Math.max(0.0, dailyRevenue);
    }

    /**
     * 计算垄断收益倍数
     */
    public double revenueMultiplier() {
        return level.revenueMultiplier();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ResourceMonopoly that = (ResourceMonopoly) o;
        return nationId.equals(that.nationId) && resourceId.equals(that.resourceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nationId, resourceId);
    }

    /**
     * 垄断等级枚举
     */
    public enum MonopolyLevel {
        /**
         * 主导地位 - 市场份额 50%-70%
         */
        DOMINANT("主导地位", 1.5),

        /**
         * 垄断 - 市场份额 70%-90%
         */
        MONOPOLY("垄断", 2.0),

        /**
         * 完全垄断 - 市场份额 90%+
         */
        ABSOLUTE("完全垄断", 3.0);

        private final String displayName;
        private final double revenueMultiplier;

        MonopolyLevel(String displayName, double revenueMultiplier) {
            this.displayName = displayName;
            this.revenueMultiplier = revenueMultiplier;
        }

        public String displayName() {
            return displayName;
        }

        public double revenueMultiplier() {
            return revenueMultiplier;
        }

        /**
         * 根据市场份额获取垄断等级
         */
        public static MonopolyLevel fromMarketShare(double marketShare) {
            if (marketShare >= 0.9) {
                return ABSOLUTE;
            } else if (marketShare >= 0.7) {
                return MONOPOLY;
            } else if (marketShare >= 0.5) {
                return DOMINANT;
            }
            return null;
        }
    }
}
