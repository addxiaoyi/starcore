package dev.starcore.starcore.module.resource;

import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.resource.model.MarketOrderBook;
import dev.starcore.starcore.module.resource.model.TradeOrder;
import dev.starcore.starcore.module.resource.model.TradeRecord;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 市场订单簿服务
 * 管理市场订单簿，处理订单匹配和交易执行
 */
public interface MarketOrderBookService {
    /**
     * 获取资源订单簿
     */
    MarketOrderBook getOrderBook(String resourceId);

    /**
     * 获取所有订单簿
     */
    Collection<MarketOrderBook> getAllOrderBooks();

    /**
     * 提交买单
     */
    Optional<TradeRecord> submitBuyOrder(UUID playerId, NationId nationId,
                                         String resourceId, long amount,
                                         double pricePerUnit, Instant expiryTime);

    /**
     * 提交卖单
     */
    Optional<TradeRecord> submitSellOrder(UUID playerId, NationId nationId,
                                           String resourceId, long amount,
                                           double pricePerUnit, Instant expiryTime);

    /**
     * 提交市价买单
     */
    Optional<TradeRecord> submitMarketBuyOrder(UUID playerId, NationId nationId,
                                                String resourceId, long amount);

    /**
     * 提交市价卖单
     */
    Optional<TradeRecord> submitMarketSellOrder(UUID playerId, NationId nationId,
                                                 String resourceId, long amount);

    /**
     * 取消订单
     */
    boolean cancelOrder(UUID orderId);

    /**
     * 获取订单
     */
    Optional<TradeOrder> getOrder(UUID orderId);

    /**
     * 获取玩家的所有订单
     */
    List<TradeOrder> getPlayerOrders(UUID playerId);

    /**
     * 获取国家的所有订单
     */
    List<TradeOrder> getNationOrders(NationId nationId);

    /**
     * 获取玩家在特定资源上的订单
     */
    List<TradeOrder> getPlayerOrdersForResource(UUID playerId, String resourceId);

    /**
     * 匹配订单（手动触发）
     */
    int matchOrders(String resourceId);

    /**
     * 匹配所有订单簿的订单
     */
    void matchAllOrders();

    /**
     * 清理过期订单
     */
    int cleanExpiredOrders();

    /**
     * 获取订单簿快照
     */
    MarketOrderBook.OrderBookSnapshot getOrderBookSnapshot(String resourceId);

    /**
     * 获取市场深度
     */
    long getMarketDepth(String resourceId, double priceRange);

    /**
     * 获取最佳买价
     */
    Optional<Double> getBestBid(String resourceId);

    /**
     * 获取最佳卖价
     */
    Optional<Double> getBestAsk(String resourceId);

    /**
     * 获取中间价
     */
    Optional<Double> getMidPrice(String resourceId);

    /**
     * 获取买卖价差
     */
    double getSpread(String resourceId);

    /**
     * 更新价格波动率
     */
    void updateVolatility(String resourceId, double volatility);

    /**
     * 获取资源当前价格（基于订单簿）
     */
    double getCurrentMarketPrice(String resourceId);
}
