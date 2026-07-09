package dev.starcore.starcore.module.resource.model;
import java.util.Optional;

import dev.starcore.starcore.module.nation.model.NationId;

import java.time.Instant;
import java.util.*;

/**
 * 市场订单簿
 * 管理特定资源的市场订单
 */
public final class MarketOrderBook {
    private final String resourceId;
    private final List<TradeOrder> buyOrders;   // 买单列表（按价格降序）
    private final List<TradeOrder> sellOrders; // 卖单列表（按价格升序）
    private final Instant createdAt;
    private Instant lastUpdatedAt;
    private double volatility; // 市场波动率
    private long totalBuyVolume;  // 总买入量
    private long totalSellVolume; // 总卖出量
    private double totalBuyValue; // 总买入金额
    private double totalSellValue;// 总卖出金额

    public MarketOrderBook(String resourceId) {
        this.resourceId = Objects.requireNonNull(resourceId, "resourceId");
        this.buyOrders = new ArrayList<>();
        this.sellOrders = new ArrayList<>();
        this.createdAt = Instant.now();
        this.lastUpdatedAt = Instant.now();
        this.volatility = 0.0;
        this.totalBuyVolume = 0;
        this.totalSellVolume = 0;
        this.totalBuyValue = 0;
        this.totalSellValue = 0;
    }

    /**
     * 获取资源ID
     */
    public String resourceId() {
        return resourceId;
    }

    /**
     * 获取所有买单（已排序）
     */
    public List<TradeOrder> getBuyOrders() {
        return Collections.unmodifiableList(buyOrders);
    }

    /**
     * 获取所有卖单（已排序）
     */
    public List<TradeOrder> getSellOrders() {
        return Collections.unmodifiableList(sellOrders);
    }

    /**
     * 获取最佳买价（最高买价）
     */
    public Optional<Double> getBestBidPrice() {
        return buyOrders.isEmpty() ? Optional.empty() : Optional.of(buyOrders.get(0).pricePerUnit());
    }

    /**
     * 获取最佳卖价（最低卖价）
     */
    public Optional<Double> getBestAskPrice() {
        return sellOrders.isEmpty() ? Optional.empty() : Optional.of(sellOrders.get(0).pricePerUnit());
    }

    /**
     * 获取买卖价差
     */
    public double getSpread() {
        Optional<Double> bestBid = getBestBidPrice();
        Optional<Double> bestAsk = getBestAskPrice();
        if (bestBid.isPresent() && bestAsk.isPresent()) {
            return bestAsk.get() - bestBid.get();
        }
        return 0.0;
    }

    /**
     * 获取中间价
     */
    public Optional<Double> getMidPrice() {
        Optional<Double> bestBid = getBestBidPrice();
        Optional<Double> bestAsk = getBestAskPrice();
        if (bestBid.isPresent() && bestAsk.isPresent()) {
            return Optional.of((bestBid.get() + bestAsk.get()) / 2.0);
        }
        return Optional.empty();
    }

    /**
     * 添加买单
     */
    public void addBuyOrder(TradeOrder order) {
        if (order.type() != TradeOrder.OrderType.BUY) {
            throw new IllegalArgumentException("Order must be a BUY order");
        }
        buyOrders.add(order);
        sortBuyOrders();
        totalBuyVolume += order.remainingAmount();
        totalBuyValue += order.remainingAmount() * order.pricePerUnit();
        lastUpdatedAt = Instant.now();
    }

    /**
     * 添加卖单
     */
    public void addSellOrder(TradeOrder order) {
        if (order.type() != TradeOrder.OrderType.SELL) {
            throw new IllegalArgumentException("Order must be a SELL order");
        }
        sellOrders.add(order);
        sortSellOrders();
        totalSellVolume += order.remainingAmount();
        totalSellValue += order.remainingAmount() * order.pricePerUnit();
        lastUpdatedAt = Instant.now();
    }

    /**
     * 移除订单
     */
    public boolean removeOrder(UUID orderId) {
        boolean removed = buyOrders.removeIf(o -> o.orderId().equals(orderId));
        if (!removed) {
            removed = sellOrders.removeIf(o -> o.orderId().equals(orderId));
        }
        if (removed) {
            recalculateTotals();
            lastUpdatedAt = Instant.now();
        }
        return removed;
    }

    /**
     * 获取订单
     */
    public Optional<TradeOrder> getOrder(UUID orderId) {
        return buyOrders.stream()
                .filter(o -> o.orderId().equals(orderId))
                .findFirst()
                .or(() -> sellOrders.stream()
                        .filter(o -> o.orderId().equals(orderId))
                        .findFirst());
    }

    /**
     * 获取买单数量
     */
    public int getBuyOrderCount() {
        return buyOrders.size();
    }

    /**
     * 获取卖单数量
     */
    public int getSellOrderCount() {
        return sellOrders.size();
    }

    /**
     * 获取总买入量
     */
    public long getTotalBuyVolume() {
        return totalBuyVolume;
    }

    /**
     * 获取总卖出量
     */
    public long getTotalSellVolume() {
        return totalSellVolume;
    }

    /**
     * 获取市场波动率
     */
    public double getVolatility() {
        return volatility;
    }

    /**
     * 设置市场波动率
     */
    public void setVolatility(double volatility) {
        this.volatility = Math.max(0, Math.min(100, volatility));
    }

    /**
     * 计算深度（特定价格范围内的订单量）
     */
    public long getDepth(double priceRange) {
        double midPrice = getMidPrice().orElse(0.0);
        if (midPrice == 0.0) return 0;

        long buyDepth = buyOrders.stream()
                .filter(o -> o.pricePerUnit() >= midPrice * (1 - priceRange))
                .mapToLong(TradeOrder::remainingAmount)
                .sum();

        long sellDepth = sellOrders.stream()
                .filter(o -> o.pricePerUnit() <= midPrice * (1 + priceRange))
                .mapToLong(TradeOrder::remainingAmount)
                .sum();

        return buyDepth + sellDepth;
    }

    /**
     * 获取订单簿快照
     */
    public OrderBookSnapshot getSnapshot() {
        return new OrderBookSnapshot(
                resourceId,
                getBestBidPrice().orElse(0.0),
                getBestAskPrice().orElse(0.0),
                getSpread(),
                getMidPrice().orElse(0.0),
                buyOrders.size(),
                sellOrders.size(),
                totalBuyVolume,
                totalSellVolume,
                volatility,
                Instant.now()
        );
    }

    /**
     * 重新计算总计
     */
    private void recalculateTotals() {
        totalBuyVolume = buyOrders.stream().mapToLong(TradeOrder::remainingAmount).sum();
        totalSellVolume = sellOrders.stream().mapToLong(TradeOrder::remainingAmount).sum();
        totalBuyValue = buyOrders.stream()
                .mapToDouble(o -> o.remainingAmount() * o.pricePerUnit()).sum();
        totalSellValue = sellOrders.stream()
                .mapToDouble(o -> o.remainingAmount() * o.pricePerUnit()).sum();
    }

    /**
     * 排序买单（价格高的优先）
     */
    private void sortBuyOrders() {
        buyOrders.sort((a, b) -> {
            int priceCompare = Double.compare(b.pricePerUnit(), a.pricePerUnit()); // 降序
            if (priceCompare != 0) return priceCompare;
            return a.createdAt().compareTo(b.createdAt()); // 同价按时间
        });
    }

    /**
     * 排序卖单（价格低的优先）
     */
    private void sortSellOrders() {
        sellOrders.sort((a, b) -> {
            int priceCompare = Double.compare(a.pricePerUnit(), b.pricePerUnit()); // 升序
            if (priceCompare != 0) return priceCompare;
            return a.createdAt().compareTo(b.createdAt()); // 同价按时间
        });
    }

    /**
     * 获取最后更新时间
     */
    public Instant lastUpdatedAt() {
        return lastUpdatedAt;
    }

    /**
     * 获取创建时间
     */
    public Instant createdAt() {
        return createdAt;
    }

    /**
     * 清理过期订单
     */
    public int cleanExpiredOrders() {
        Instant now = Instant.now();
        int before = buyOrders.size() + sellOrders.size();

        buyOrders.removeIf(o -> o.isExpired(now));
        sellOrders.removeIf(o -> o.isExpired(now));

        recalculateTotals();
        lastUpdatedAt = Instant.now();

        return before - (buyOrders.size() + sellOrders.size());
    }

    /**
     * 订单簿快照
     */
    public record OrderBookSnapshot(
            String resourceId,
            double bestBid,
            double bestAsk,
            double spread,
            double midPrice,
            int buyOrderCount,
            int sellOrderCount,
            long totalBuyVolume,
            long totalSellVolume,
            double volatility,
            Instant timestamp
    ) {}

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MarketOrderBook that = (MarketOrderBook) o;
        return resourceId.equals(that.resourceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(resourceId);
    }
}
