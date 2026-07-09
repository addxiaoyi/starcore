package dev.starcore.starcore.module.resource.model;

import dev.starcore.starcore.module.nation.model.NationId;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 交易订单
 * 代表市场中的一个买单或卖单
 */
public final class TradeOrder {
    private final UUID orderId;
    private final OrderType type;
    private final OrderSource source;
    private final UUID playerId;           // 玩家ID（如果是玩家订单）
    private final NationId nationId;         // 国家ID（如果是国家订单）
    private final String resourceId;
    private final double pricePerUnit;
    private long totalAmount;               // 订单总数量
    private long remainingAmount;           // 剩余未成交数量
    private final Instant createdAt;
    private Instant expiryTime;
    private OrderStatus status;
    private boolean isMarketOrder;          // 是否是市价单
    private double fillPrice;               // 成交均价
    private long filledAmount;              // 已成交数量

    public TradeOrder(UUID orderId, OrderType type, OrderSource source,
                      UUID playerId, NationId nationId, String resourceId,
                      double pricePerUnit, long amount, Instant expiryTime,
                      boolean isMarketOrder) {
        this.orderId = Objects.requireNonNull(orderId, "orderId");
        this.type = Objects.requireNonNull(type, "type");
        this.source = Objects.requireNonNull(source, "source");
        this.playerId = playerId;
        this.nationId = nationId;
        this.resourceId = Objects.requireNonNull(resourceId, "resourceId");
        this.pricePerUnit = Math.max(0, pricePerUnit);
        this.totalAmount = Math.max(1, amount);
        this.remainingAmount = this.totalAmount;
        this.createdAt = Instant.now();
        this.expiryTime = expiryTime;
        this.status = OrderStatus.PENDING;
        this.isMarketOrder = isMarketOrder;
        this.fillPrice = 0;
        this.filledAmount = 0;
    }

    /**
     * 获取订单ID
     */
    public UUID orderId() {
        return orderId;
    }

    /**
     * 获取订单类型
     */
    public OrderType type() {
        return type;
    }

    /**
     * 获取订单来源
     */
    public OrderSource source() {
        return source;
    }

    /**
     * 获取玩家ID
     */
    public Optional<UUID> playerId() {
        return Optional.ofNullable(playerId);
    }

    /**
     * 获取国家ID
     */
    public Optional<NationId> nationId() {
        return Optional.ofNullable(nationId);
    }

    /**
     * 获取资源ID
     */
    public String resourceId() {
        return resourceId;
    }

    /**
     * 获取单价
     */
    public double pricePerUnit() {
        return pricePerUnit;
    }

    /**
     * 获取订单总数量
     */
    public long totalAmount() {
        return totalAmount;
    }

    /**
     * 获取剩余数量
     */
    public long remainingAmount() {
        return remainingAmount;
    }

    /**
     * 获取创建时间
     */
    public Instant createdAt() {
        return createdAt;
    }

    /**
     * 获取过期时间
     */
    public Instant expiryTime() {
        return expiryTime;
    }

    /**
     * 获取订单状态
     */
    public OrderStatus status() {
        return status;
    }

    /**
     * 是否是市价单
     */
    public boolean isMarketOrder() {
        return isMarketOrder;
    }

    /**
     * 获取成交均价
     */
    public double fillPrice() {
        return fillPrice;
    }

    /**
     * 获取已成交数量
     */
    public long filledAmount() {
        return filledAmount;
    }

    /**
     * 订单类型
     */
    public enum OrderType {
        BUY("买单"),
        SELL("卖单");

        private final String displayName;

        OrderType(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }
    }

    /**
     * 订单来源
     */
    public enum OrderSource {
        PLAYER("玩家"),
        NATION("国家"),
        MARKET("做市商"),
        EVENT("事件");

        private final String displayName;

        OrderSource(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }
    }

    /**
     * 订单状态
     */
    public enum OrderStatus {
        PENDING("待成交"),
        PARTIAL("部分成交"),
        FILLED("完全成交"),
        CANCELLED("已取消"),
        EXPIRED("已过期");

        private final String displayName;

        OrderStatus(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }
    }

    /**
     * 设置过期时间
     */
    public void setExpiryTime(Instant expiryTime) {
        this.expiryTime = expiryTime;
    }

    /**
     * 取消订单
     */
    public boolean cancel() {
        if (status == OrderStatus.PENDING || status == OrderStatus.PARTIAL) {
            this.status = OrderStatus.CANCELLED;
            return true;
        }
        return false;
    }

    /**
     * 检查是否过期
     */
    public boolean isExpired() {
        return isExpired(Instant.now());
    }

    /**
     * 检查是否过期（指定时间）
     */
    public boolean isExpired(Instant now) {
        return expiryTime != null && now.isAfter(expiryTime);
    }

    /**
     * 部分成交
     */
    public long fill(long amount) {
        if (amount <= 0 || remainingAmount <= 0) {
            return 0;
        }

        long actualFill = Math.min(amount, remainingAmount);
        remainingAmount -= actualFill;
        filledAmount += actualFill;

        // 更新成交均价
        if (fillPrice == 0) {
            fillPrice = pricePerUnit;
        }

        // 更新状态
        if (remainingAmount == 0) {
            status = OrderStatus.FILLED;
        } else {
            status = OrderStatus.PARTIAL;
        }

        return actualFill;
    }

    /**
     * 计算订单价值
     */
    public double totalValue() {
        return totalAmount * pricePerUnit;
    }

    /**
     * 计算剩余价值
     */
    public double remainingValue() {
        return remainingAmount * pricePerUnit;
    }

    /**
     * 检查是否是买单（买入需要花费货币）
     */
    public boolean isBuyOrder() {
        return type == OrderType.BUY;
    }

    /**
     * 检查是否是卖单（卖出获得货币）
     */
    public boolean isSellOrder() {
        return type == OrderType.SELL;
    }

    /**
     * 获取订单完成百分比
     */
    public double fillPercentage() {
        if (totalAmount == 0) return 100.0;
        return (double) filledAmount / totalAmount * 100.0;
    }

    /**
     * 转为订单快照
     */
    public OrderSnapshot toSnapshot() {
        return new OrderSnapshot(
                orderId,
                type,
                source,
                playerId,
                nationId,
                resourceId,
                pricePerUnit,
                totalAmount,
                remainingAmount,
                createdAt,
                expiryTime,
                status,
                isMarketOrder,
                fillPrice,
                filledAmount
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TradeOrder that = (TradeOrder) o;
        return orderId.equals(that.orderId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(orderId);
    }

    /**
     * 订单快照（不可变）
     */
    public record OrderSnapshot(
            UUID orderId,
            OrderType type,
            OrderSource source,
            UUID playerId,
            NationId nationId,
            String resourceId,
            double pricePerUnit,
            long totalAmount,
            long remainingAmount,
            Instant createdAt,
            Instant expiryTime,
            OrderStatus status,
            boolean isMarketOrder,
            double fillPrice,
            long filledAmount
    ) {}
}
