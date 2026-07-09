package dev.starcore.starcore.module.resource.model;

import dev.starcore.starcore.module.nation.model.NationId;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 交易记录
 * 记录已完成的交易
 */
public final class TradeRecord {
    private final UUID recordId;
    private final TradeOrder.OrderType orderType;
    private final TradeOrder.OrderSource orderSource;
    private final UUID sellerPlayerId;
    private final UUID buyerPlayerId;
    private final NationId sellerNationId;
    private final NationId buyerNationId;
    private final String resourceId;
    private final long amount;
    private final double pricePerUnit;
    private final double totalValue;
    private final double taxAmount;
    private final double netValue;
    private final Instant executedAt;
    private final UUID buyOrderId;
    private final UUID sellOrderId;
    private final TradeRecordStatus status;
    private final String notes;

    public TradeRecord(UUID recordId, TradeOrder.OrderType orderType, TradeOrder.OrderSource orderSource,
                       UUID sellerPlayerId, UUID buyerPlayerId,
                       NationId sellerNationId, NationId buyerNationId,
                       String resourceId, long amount, double pricePerUnit,
                       double taxAmount, Instant executedAt,
                       UUID buyOrderId, UUID sellOrderId,
                       String notes) {
        this.recordId = Objects.requireNonNull(recordId, "recordId");
        this.orderType = Objects.requireNonNull(orderType, "orderType");
        this.orderSource = Objects.requireNonNull(orderSource, "orderSource");
        this.sellerPlayerId = sellerPlayerId;
        this.buyerPlayerId = buyerPlayerId;
        this.sellerNationId = sellerNationId;
        this.buyerNationId = buyerNationId;
        this.resourceId = Objects.requireNonNull(resourceId, "resourceId");
        this.amount = Math.max(1, amount);
        this.pricePerUnit = Math.max(0, pricePerUnit);
        this.totalValue = this.amount * this.pricePerUnit;
        this.taxAmount = Math.max(0, taxAmount);
        this.netValue = this.totalValue - this.taxAmount;
        this.executedAt = Objects.requireNonNull(executedAt, "executedAt");
        this.buyOrderId = buyOrderId;
        this.sellOrderId = sellOrderId;
        this.status = TradeRecordStatus.COMPLETED;
        this.notes = notes;
    }

    /**
     * 工厂方法：创建玩家间交易记录
     */
    public static TradeRecord createPlayerTrade(UUID buyerPlayerId, UUID sellerPlayerId,
                                                String resourceId, long amount,
                                                double pricePerUnit, double taxAmount,
                                                String notes) {
        return new TradeRecord(
                UUID.randomUUID(),
                TradeOrder.OrderType.BUY,
                TradeOrder.OrderSource.PLAYER,
                sellerPlayerId,
                buyerPlayerId,
                null,
                null,
                resourceId,
                amount,
                pricePerUnit,
                taxAmount,
                Instant.now(),
                null,
                null,
                notes
        );
    }

    /**
     * 工厂方法：创建国家间交易记录
     */
    public static TradeRecord createNationTrade(NationId buyerNationId, NationId sellerNationId,
                                                String resourceId, long amount,
                                                double pricePerUnit, double taxAmount,
                                                UUID buyOrderId, UUID sellOrderId,
                                                String notes) {
        return new TradeRecord(
                UUID.randomUUID(),
                TradeOrder.OrderType.BUY,
                TradeOrder.OrderSource.NATION,
                null,
                null,
                sellerNationId,
                buyerNationId,
                resourceId,
                amount,
                pricePerUnit,
                taxAmount,
                Instant.now(),
                buyOrderId,
                sellOrderId,
                notes
        );
    }

    /**
     * 工厂方法：创建市场撮合交易记录
     */
    public static TradeRecord createMarketTrade(UUID buyerPlayerId, UUID sellerPlayerId,
                                                 NationId buyerNationId, NationId sellerNationId,
                                                 String resourceId, long amount,
                                                 double pricePerUnit, double taxAmount,
                                                 UUID buyOrderId, UUID sellOrderId) {
        return new TradeRecord(
                UUID.randomUUID(),
                TradeOrder.OrderType.BUY,
                TradeOrder.OrderSource.MARKET,
                sellerPlayerId,
                buyerPlayerId,
                sellerNationId,
                buyerNationId,
                resourceId,
                amount,
                pricePerUnit,
                taxAmount,
                Instant.now(),
                buyOrderId,
                sellOrderId,
                "Market match"
        );
    }

    /**
     * 获取记录ID
     */
    public UUID recordId() {
        return recordId;
    }

    /**
     * 获取订单类型
     */
    public TradeOrder.OrderType orderType() {
        return orderType;
    }

    /**
     * 获取订单来源
     */
    public TradeOrder.OrderSource orderSource() {
        return orderSource;
    }

    /**
     * 获取卖家玩家ID
     */
    public Optional<UUID> sellerPlayerId() {
        return Optional.ofNullable(sellerPlayerId);
    }

    /**
     * 获取买家玩家ID
     */
    public Optional<UUID> buyerPlayerId() {
        return Optional.ofNullable(buyerPlayerId);
    }

    /**
     * 获取卖家国家ID
     */
    public Optional<NationId> sellerNationId() {
        return Optional.ofNullable(sellerNationId);
    }

    /**
     * 获取买家国家ID
     */
    public Optional<NationId> buyerNationId() {
        return Optional.ofNullable(buyerNationId);
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
     * 获取总价值
     */
    public double totalValue() {
        return totalValue;
    }

    /**
     * 获取税收金额
     */
    public double taxAmount() {
        return taxAmount;
    }

    /**
     * 获取净价值
     */
    public double netValue() {
        return netValue;
    }

    /**
     * 获取执行时间
     */
    public Instant executedAt() {
        return executedAt;
    }

    /**
     * 获取买单ID
     */
    public Optional<UUID> buyOrderId() {
        return Optional.ofNullable(buyOrderId);
    }

    /**
     * 获取卖单ID
     */
    public Optional<UUID> sellOrderId() {
        return Optional.ofNullable(sellOrderId);
    }

    /**
     * 获取状态
     */
    public TradeRecordStatus status() {
        return status;
    }

    /**
     * 获取备注
     */
    public Optional<String> notes() {
        return Optional.ofNullable(notes);
    }

    /**
     * 交易记录状态
     */
    public enum TradeRecordStatus {
        COMPLETED("已完成"),
        REFUNDED("已退款"),
        DISPUTED("争议中"),
        CANCELLED("已取消");

        private final String displayName;

        TradeRecordStatus(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TradeRecord that = (TradeRecord) o;
        return recordId.equals(that.recordId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(recordId);
    }
}
