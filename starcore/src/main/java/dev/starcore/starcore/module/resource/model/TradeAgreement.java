package dev.starcore.starcore.module.resource.model;

import dev.starcore.starcore.module.nation.model.NationId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 贸易协定
 * 国家之间的资源贸易协议
 */
public final class TradeAgreement {
    private final UUID agreementId;
    private final NationId exporterNationId;
    private final NationId importerNationId;
    private final String resourceId;
    private final long amount;
    private final double pricePerUnit;
    private final Instant startTime;
    private final Instant expiryTime;
    private final TradeAgreementStatus status;
    private boolean autoRenew;

    public TradeAgreement(UUID agreementId, NationId exporterNationId, NationId importerNationId,
                          String resourceId, long amount, double pricePerUnit,
                          Instant startTime, Instant expiryTime) {
        this.agreementId = Objects.requireNonNull(agreementId, "agreementId");
        this.exporterNationId = Objects.requireNonNull(exporterNationId, "exporterNationId");
        this.importerNationId = Objects.requireNonNull(importerNationId, "importerNationId");
        this.resourceId = Objects.requireNonNull(resourceId, "resourceId");
        this.amount = Math.max(1, amount);
        this.pricePerUnit = Math.max(0.0, pricePerUnit);
        this.startTime = Objects.requireNonNull(startTime, "startTime");
        this.expiryTime = Objects.requireNonNull(expiryTime, "expiryTime");
        this.status = TradeAgreementStatus.ACTIVE;
        this.autoRenew = false;
    }

    /**
     * 获取协定ID
     */
    public UUID agreementId() {
        return agreementId;
    }

    /**
     * 获取出口国ID
     */
    public NationId exporterNationId() {
        return exporterNationId;
    }

    /**
     * 获取进口国ID
     */
    public NationId importerNationId() {
        return importerNationId;
    }

    /**
     * 获取资源ID
     */
    public String resourceId() {
        return resourceId;
    }

    /**
     * 获取交易数量
     */
    public long amount() {
        return amount;
    }

    /**
     * 获取单价
     */
    public double pricePerUnit() {
        return pricePerUnit;
    }

    /**
     * 获取协定开始时间
     */
    public Instant startTime() {
        return startTime;
    }

    /**
     * 获取协定到期时间
     */
    public Instant expiryTime() {
        return expiryTime;
    }

    /**
     * 获取协定状态
     */
    public TradeAgreementStatus status() {
        return status;
    }

    /**
     * 是否自动续约
     */
    public boolean isAutoRenew() {
        return autoRenew;
    }

    /**
     * 设置自动续约
     */
    public void setAutoRenew(boolean autoRenew) {
        this.autoRenew = autoRenew;
    }

    /**
     * 计算总价值
     */
    public double totalValue() {
        return amount * pricePerUnit;
    }

    /**
     * 检查协定是否过期
     */
    public boolean isExpired() {
        return Instant.now().isAfter(expiryTime);
    }

    /**
     * 检查协定是否有效
     */
    public boolean isActive() {
        return status == TradeAgreementStatus.ACTIVE && !isExpired();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TradeAgreement that = (TradeAgreement) o;
        return agreementId.equals(that.agreementId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(agreementId);
    }

    /**
     * 贸易协定状态
     */
    public enum TradeAgreementStatus {
        ACTIVE("生效中"),
        SUSPENDED("已暂停"),
        CANCELLED("已取消"),
        EXPIRED("已过期");

        private final String displayName;

        TradeAgreementStatus(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }
    }
}
