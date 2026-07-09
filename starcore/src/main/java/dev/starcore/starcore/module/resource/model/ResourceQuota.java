package dev.starcore.starcore.module.resource.model;

import dev.starcore.starcore.module.nation.model.NationId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 资源配额
 * 限制国家可以购买/出口的资源数量
 */
public final class ResourceQuota {
    private final UUID quotaId;
    private final NationId nationId;
    private final String resourceId;
    private final long maxAmount;
    private long currentAmount;
    private final Instant startTime;
    private final Instant resetTime;
    private final QuotaType type;

    public ResourceQuota(UUID quotaId, NationId nationId, String resourceId,
                         long maxAmount, Instant startTime, Instant resetTime, QuotaType type) {
        this.quotaId = Objects.requireNonNull(quotaId, "quotaId");
        this.nationId = Objects.requireNonNull(nationId, "nationId");
        this.resourceId = Objects.requireNonNull(resourceId, "resourceId");
        this.maxAmount = Math.max(0, maxAmount);
        this.currentAmount = 0;
        this.startTime = Objects.requireNonNull(startTime, "startTime");
        this.resetTime = Objects.requireNonNull(resetTime, "resetTime");
        this.type = Objects.requireNonNull(type, "type");
    }

    /**
     * 获取配额ID
     */
    public UUID quotaId() {
        return quotaId;
    }

    /**
     * 获取国家ID
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
     * 获取最大配额
     */
    public long maxAmount() {
        return maxAmount;
    }

    /**
     * 获取当前已使用配额
     */
    public long currentAmount() {
        return currentAmount;
    }

    /**
     * 获取开始时间
     */
    public Instant startTime() {
        return startTime;
    }

    /**
     * 获取重置时间
     */
    public Instant resetTime() {
        return resetTime;
    }

    /**
     * 获取配额类型
     */
    public QuotaType type() {
        return type;
    }

    /**
     * 获取剩余配额
     */
    public long remainingAmount() {
        return Math.max(0, maxAmount - currentAmount);
    }

    /**
     * 使用配额
     * @return 是否成功使用
     */
    public boolean use(long amount) {
        if (amount <= 0 || currentAmount + amount > maxAmount) {
            return false;
        }
        currentAmount += amount;
        return true;
    }

    /**
     * 检查是否可以使用指定数量的配额
     */
    public boolean canUse(long amount) {
        return amount > 0 && currentAmount + amount <= maxAmount;
    }

    /**
     * 重置配额
     */
    public void reset() {
        currentAmount = 0;
    }

    /**
     * 检查配额是否需要重置
     */
    public boolean needsReset() {
        return Instant.now().isAfter(resetTime);
    }

    /**
     * 获取使用百分比
     */
    public double usagePercentage() {
        if (maxAmount == 0) return 0.0;
        return (currentAmount * 100.0) / maxAmount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ResourceQuota that = (ResourceQuota) o;
        return quotaId.equals(that.quotaId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(quotaId);
    }

    /**
     * 配额类型
     */
    public enum QuotaType {
        /**
         * 进口配额
         */
        IMPORT("进口配额"),

        /**
         * 出口配额
         */
        EXPORT("出口配额"),

        /**
         * 生产配额
         */
        PRODUCTION("生产配额");

        private final String displayName;

        QuotaType(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }
    }
}
