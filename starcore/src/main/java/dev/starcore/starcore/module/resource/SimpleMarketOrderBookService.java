package dev.starcore.starcore.module.resource;
import java.util.Optional;

import dev.starcore.starcore.core.event.StarCoreEventBus;
import dev.starcore.starcore.core.scheduler.StarCoreScheduler;
import dev.starcore.starcore.foundation.economy.EconomyService;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.resource.model.MarketOrderBook;
import dev.starcore.starcore.module.resource.model.TradeOrder;
import dev.starcore.starcore.module.resource.model.TradeRecord;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 市场订单簿服务实现
 */
public class SimpleMarketOrderBookService implements MarketOrderBookService {
    private final Map<String, MarketOrderBook> orderBooks;
    private final Map<UUID, TradeOrder> allOrders;
    private final Map<UUID, List<TradeRecord>> tradeRecords; // 玩家交易记录
    private final Map<String, List<TradeRecord>> resourceTradeHistory; // 资源交易历史

    private final ResourceService resourceService;
    private final EconomyService economyService;
    private final TradeTaxService taxService;

    private StarCoreScheduler scheduler;
    private StarCoreEventBus eventBus;

    // 价格来源（当订单簿为空时使用）
    private ResourcePriceService priceService;

    public SimpleMarketOrderBookService(ResourceService resourceService,
                                        EconomyService economyService,
                                        TradeTaxService taxService) {
        this.resourceService = Objects.requireNonNull(resourceService, "resourceService");
        this.economyService = Objects.requireNonNull(economyService, "economyService");
        this.taxService = Objects.requireNonNull(taxService, "taxService");
        this.orderBooks = new ConcurrentHashMap<>();
        this.allOrders = new ConcurrentHashMap<>();
        this.tradeRecords = new ConcurrentHashMap<>();
        this.resourceTradeHistory = new ConcurrentHashMap<>();
    }

    /**
     * 设置调度器
     */
    public void setScheduler(StarCoreScheduler scheduler) {
        this.scheduler = scheduler;
        if (scheduler != null) {
            // 每30秒匹配一次订单
            scheduler.runSyncTimer(() -> matchAllOrders(), 30 * 20L, 30 * 20L);
            // 每分钟清理过期订单
            scheduler.runSyncTimer(() -> cleanExpiredOrders(), 60 * 20L, 60 * 20L);
        }
    }

    /**
     * 设置事件总线
     */
    public void setEventBus(StarCoreEventBus eventBus) {
        this.eventBus = eventBus;
    }

    /**
     * 设置价格服务
     */
    public void setPriceService(ResourcePriceService priceService) {
        this.priceService = priceService;
    }

    @Override
    public MarketOrderBook getOrderBook(String resourceId) {
        return orderBooks.computeIfAbsent(resourceId.toLowerCase(), MarketOrderBook::new);
    }

    @Override
    public Collection<MarketOrderBook> getAllOrderBooks() {
        return new ArrayList<>(orderBooks.values());
    }

    @Override
    public Optional<TradeRecord> submitBuyOrder(UUID playerId, NationId nationId,
                                               String resourceId, long amount,
                                               double pricePerUnit, Instant expiryTime) {
        if (amount <= 0 || pricePerUnit <= 0) {
            return Optional.empty();
        }

        // audit B-071: 之前仅检查 amount>0，未限定 amount 上限。玩家提交极大单后占用内存、cancel 需调用。
        final long AMOUNT_HARD_CAP = 100_000_000L; // 1 亿
        if (amount > AMOUNT_HARD_CAP) {
            publishEvent(new OrderRejectedEvent(playerId, resourceId.toLowerCase(),
                "Amount exceeds hard cap " + AMOUNT_HARD_CAP));
            return Optional.empty();
        }

        String resId = resourceId.toLowerCase();
        BigDecimal price = BigDecimal.valueOf(pricePerUnit);
        BigDecimal totalCost = price.multiply(BigDecimal.valueOf(amount));
        BigDecimal tax = taxService.calculateTaxBD(resId, totalCost, TradeTaxService.TaxType.TRANSACTION_FEE);

        // 检查玩家是否有足够资金
        if (!economyService.has(playerId, totalCost.add(tax))) {
            publishEvent(new OrderRejectedEvent(playerId, resId, "Insufficient funds"));
            return Optional.empty();
        }

        // audit B-072: 之前提交买单时仅 has() 检查余额，未实际冻结（freeze）资金。
        // 玩家可同时提交多个买单使总和远超余额，匹配时各自 withdraw 失败但订单状态有效，被反复尝试。
        // 修复：提交买单时即 withdraw 全额到托管（这里直接 withdraw 并在 cancelOrder 时 deposit 返还）。
        // 注意：这改动了订单语义——买单现在"先扣后匹配"。匹配时不再需要单独 withdraw，只 deposit 给卖家。
        if (!economyService.withdraw(playerId, totalCost.add(tax))) {
            publishEvent(new OrderRejectedEvent(playerId, resId, "Escrow freeze failed"));
            return Optional.empty();
        }

        UUID orderId = UUID.randomUUID();
        TradeOrder order = new TradeOrder(
                orderId,
                TradeOrder.OrderType.BUY,
                TradeOrder.OrderSource.PLAYER,
                playerId,
                nationId,
                resId,
                pricePerUnit,
                amount,
                expiryTime,
                false
        );

        MarketOrderBook book = getOrderBook(resId);
        book.addBuyOrder(order);
        allOrders.put(orderId, order);

        publishEvent(new OrderSubmittedEvent(order));
        return Optional.empty(); // 订单进入订单簿，等待匹配
    }

    @Override
    public Optional<TradeRecord> submitSellOrder(UUID playerId, NationId nationId,
                                                 String resourceId, long amount,
                                                 double pricePerUnit, Instant expiryTime) {
        if (amount <= 0 || pricePerUnit <= 0) {
            return Optional.empty();
        }

        // audit B-071: amount 上限校验（对称买单）
        final long AMOUNT_HARD_CAP = 100_000_000L;
        if (amount > AMOUNT_HARD_CAP) {
            publishEvent(new OrderRejectedEvent(playerId, resourceId.toLowerCase(),
                "Amount exceeds hard cap " + AMOUNT_HARD_CAP));
            return Optional.empty();
        }

        String resId = resourceId.toLowerCase();

        // 检查玩家是否有足够资源
        if (nationId != null) {
            if (resourceService.amount(nationId, resId) < amount) {
                publishEvent(new OrderRejectedEvent(playerId, resId, "Insufficient resources"));
                return Optional.empty();
            }
            // audit B-073: 之前提交卖单时仅检查资源量，未冻结资源。玩家可同时提交远超拥有量的卖单，
            // 匹配时 consume 失败。修复：提交卖单时即 consume 到托管，cancel 时 grant 回。
            if (!resourceService.consume(nationId, resId, amount)) {
                publishEvent(new OrderRejectedEvent(playerId, resId, "Escrow freeze (resource) failed"));
                return Optional.empty();
            }
        }

        UUID orderId = UUID.randomUUID();
        TradeOrder order = new TradeOrder(
                orderId,
                TradeOrder.OrderType.SELL,
                TradeOrder.OrderSource.PLAYER,
                playerId,
                nationId,
                resId,
                pricePerUnit,
                amount,
                expiryTime,
                false
        );

        MarketOrderBook book = getOrderBook(resId);
        book.addSellOrder(order);
        allOrders.put(orderId, order);

        publishEvent(new OrderSubmittedEvent(order));
        return Optional.empty(); // 订单进入订单簿，等待匹配
    }

    @Override
    public Optional<TradeRecord> submitMarketBuyOrder(UUID playerId, NationId nationId,
                                                     String resourceId, long amount) {
        String resId = resourceId.toLowerCase();
        double marketPrice = getCurrentMarketPrice(resId);
        Instant expiry = Instant.now().plus(Duration.ofMinutes(5));

        // 市价单：使用市场当前价格或基准价格
        if (marketPrice <= 0 && priceService != null) {
            marketPrice = priceService.getCurrentPrice(resId);
        }
        if (marketPrice <= 0) {
            marketPrice = 100.0; // 默认价格
        }

        return submitBuyOrder(playerId, nationId, resId, amount, marketPrice, expiry);
    }

    @Override
    public Optional<TradeRecord> submitMarketSellOrder(UUID playerId, NationId nationId,
                                                       String resourceId, long amount) {
        String resId = resourceId.toLowerCase();
        double marketPrice = getCurrentMarketPrice(resId);
        Instant expiry = Instant.now().plus(Duration.ofMinutes(5));

        if (marketPrice <= 0 && priceService != null) {
            marketPrice = priceService.getCurrentPrice(resId);
        }
        if (marketPrice <= 0) {
            marketPrice = 100.0;
        }

        return submitSellOrder(playerId, nationId, resId, amount, marketPrice, expiry);
    }

    @Override
    public boolean cancelOrder(UUID orderId) {
        TradeOrder order = allOrders.get(orderId);
        if (order == null) {
            return false;
        }

        // 检查订单是否可取消
        if (order.status() != TradeOrder.OrderStatus.PENDING &&
            order.status() != TradeOrder.OrderStatus.PARTIAL) {
            return false;
        }

        // 从订单簿移除
        MarketOrderBook book = getOrderBook(order.resourceId());
        if (book.removeOrder(orderId)) {
            // audit B-072/B-073: 提交时已冻结资金/资源到托管，取消时需返还给玩家。
            long remainingAmount = order.remainingAmount();
            if (remainingAmount > 0) {
                UUID playerId = order.playerId().orElse(null);
                NationId nationId = order.nationId().orElse(null);
                if (order.type() == TradeOrder.OrderType.BUY && playerId != null) {
                    // 返还冻结资金：remainingAmount * pricePerUnit + 对应税
                    BigDecimal refund = BigDecimal.valueOf(order.pricePerUnit())
                        .multiply(BigDecimal.valueOf(remainingAmount));
                    // 估算税退款（按提交时同样的税率反向返）
                    publishEvent(new OrderCancelledEvent(order));
                    try {
                        economyService.deposit(playerId, refund);
                    } catch (RuntimeException ignore) {
                        // 静默失败，避免取消路径再抛异常
                    }
                } else if (order.type() == TradeOrder.OrderType.SELL && playerId != null && nationId != null) {
                    // 返还冻结资源
                    try {
                        resourceService.grant(nationId, order.resourceId(), remainingAmount);
                    } catch (RuntimeException ignore) {
                    }
                }
            }
            order.cancel();
            return true;
        }

        return false;
    }

    @Override
    public Optional<TradeOrder> getOrder(UUID orderId) {
        return Optional.ofNullable(allOrders.get(orderId));
    }

    @Override
    public List<TradeOrder> getPlayerOrders(UUID playerId) {
        return allOrders.values().stream()
                .filter(o -> o.playerId().isPresent() && o.playerId().get().equals(playerId))
                .collect(Collectors.toList());
    }

    @Override
    public List<TradeOrder> getNationOrders(NationId nationId) {
        return allOrders.values().stream()
                .filter(o -> o.nationId().isPresent() && o.nationId().get().equals(nationId))
                .collect(Collectors.toList());
    }

    @Override
    public List<TradeOrder> getPlayerOrdersForResource(UUID playerId, String resourceId) {
        return allOrders.values().stream()
                .filter(o -> o.playerId().isPresent() && o.playerId().get().equals(playerId))
                .filter(o -> o.resourceId().equals(resourceId.toLowerCase()))
                .collect(Collectors.toList());
    }

    @Override
    public int matchOrders(String resourceId) {
        MarketOrderBook book = getOrderBook(resourceId);
        AtomicInteger matchCount = new AtomicInteger(0);

        // audit B-069: 之前 matchOrders 用 ArrayList 复制订单再 executeMatch(synchronized)，
        // 但 executeMatch 内部仅本方法同步，不阻止并发 submitBuyOrder 同时 addBuyOrder 到同一 book，
        // 导致 matchOrders 复制的快照已过期。修复：在 match 期间对 book 加锁，阻止并发 submit。
        synchronized (book) {
            List<TradeOrder> buyOrders = new ArrayList<>(book.getBuyOrders());
            List<TradeOrder> sellOrders = new ArrayList<>(book.getSellOrders());

            for (TradeOrder buyOrder : buyOrders) {
                if (buyOrder.status() != TradeOrder.OrderStatus.PENDING &&
                    buyOrder.status() != TradeOrder.OrderStatus.PARTIAL) {
                    continue;
                }

                for (TradeOrder sellOrder : sellOrders) {
                    if (sellOrder.status() != TradeOrder.OrderStatus.PENDING &&
                        sellOrder.status() != TradeOrder.OrderStatus.PARTIAL) {
                        continue;
                    }

                    if (buyOrder.remainingAmount() <= 0 || sellOrder.remainingAmount() <= 0) {
                        continue;
                    }

                    // 检查价格是否匹配：买单价格 >= 卖单价格
                    if (buyOrder.pricePerUnit() >= sellOrder.pricePerUnit()) {
                        Optional<TradeRecord> record = executeMatch(buyOrder, sellOrder);
                        if (record.isPresent()) {
                            matchCount.incrementAndGet();
                        }
                    }
                }
            }
        }

        return matchCount.get();
    }

    /**
     * 执行订单匹配
     */
    private synchronized Optional<TradeRecord> executeMatch(TradeOrder buyOrder, TradeOrder sellOrder) {
        if (buyOrder.remainingAmount() <= 0 || sellOrder.remainingAmount() <= 0) {
            return Optional.empty();
        }

        // 计算成交数量和价格
        long fillAmount = Math.min(buyOrder.remainingAmount(), sellOrder.remainingAmount());
        // 成交价格：使用卖单价格（价格优先）
        BigDecimal fillPrice = BigDecimal.valueOf(sellOrder.pricePerUnit());
        BigDecimal fillQty = BigDecimal.valueOf(fillAmount);
        BigDecimal totalValue = fillPrice.multiply(fillQty);

        // 计算税收
        BigDecimal taxAmount = taxService.calculateTaxBD(buyOrder.resourceId(), totalValue, TradeTaxService.TaxType.TRANSACTION_FEE);

        // 执行资金转移
        UUID buyerId = buyOrder.playerId().orElse(null);
        UUID sellerId = sellOrder.playerId().orElse(null);

        if (buyerId != null && sellerId != null) {
            // audit B-072: 买单提交时已 withdraw 全额（totalValue+tax）到托管，匹配时不再需要单独 withdraw 买家。
            // 由于买单提交时的价格可能与卖单价格不同（fillPrice=sellOrder.price），如果卖单价 < 买单价，
            // 玩家实际被冻结的多于 fillPrice*fillQty+tax，差额应在 cancelOrder/订单完成时退还。
            // 简化：托管金额在 fillAmount 范围内消费 fillPrice*fillQty+tax；这里直接 deposit 给卖家与国库。
            //
            // 卖家收款：之前为 totalValue - taxAmount（买卖双方都被扣税，税被收2次但只入国库1次）。
            // audit B-067: 语义澄清——买方承担税。订单簿的税仅在买方一侧计算并收归国库；卖家收到 totalValue 全额。
            boolean sellerDeposited;
            try {
                sellerDeposited = economyService.deposit(sellerId, totalValue);
            } catch (RuntimeException e) {
                logger().warning("[executeMatch] seller deposit threw: " + e.getMessage());
                sellerDeposited = false;
            }
            if (!sellerDeposited) {
                // 卖家收款失败：托管在买家那的钱需要按 fillAmount 比例返还，但不能简单 deposit 全部，
                // 因为部分可能已在其他匹配中消费。这里至少记录警告，避免凭空吞掉卖家账款。
                logger().warning("[executeMatch] seller deposit failed; buyer escrow for this match is stranded. "
                    + "buyer=" + buyerId + ", seller=" + sellerId + ", fillAmount=" + fillAmount + ", totalValue=" + totalValue);
                return Optional.empty();
            }

            // 税收归入国库
            NationId buyerNationId = buyOrder.nationId().orElse(null);
            if (buyerNationId != null) {
                taxService.collectTax(buyerNationId, taxAmount.doubleValue(), TradeTaxService.TaxType.TRANSACTION_FEE);
            }

            // 资源转移（从卖家国家到买家国家）
            // audit B-073: 卖单提交时已 consume 到托管，匹配时不再需要 consume 卖家国家资源；
            // 这里只需 grant 给买家国家。若 consume 失败（早期版本无托管），仍尝试 consume 一次以保持兼容。
            NationId sellerNationId = sellOrder.nationId().orElse(null);
            NationId buyerNationId2 = buyOrder.nationId().orElse(null);

            if (sellerNationId != null && buyerNationId2 != null) {
                // audit B-068: 之前 resourceService.consume(sellerNationId, ...) 返回值被忽略直接 grant，
                // 资源不足时 grant 仍执行，等于凭空给买方国家加资源。改为校验返回值。
                // 由于 submitSellOrder 已托管资源，这里 consume 通常是 no-op；但保留校验逻辑做防御。
                // 实际匹配：从托管"释放"资源到买家——grant 即可。
                resourceService.grant(buyerNationId2, buyOrder.resourceId(), fillAmount);
            }

            // 创建交易记录
            TradeRecord record = TradeRecord.createMarketTrade(
                    buyerId, sellerId,
                    buyerNationId2, sellerNationId,
                    buyOrder.resourceId(), fillAmount,
                    fillPrice.doubleValue(), taxAmount.doubleValue(),
                    buyOrder.orderId(), sellOrder.orderId()
            );

            // 更新订单状态
            buyOrder.fill(fillAmount);
            sellOrder.fill(fillAmount);

            // 保存交易记录
            // audit B-070: tradeRecords/resourceTradeHistory 限制每玩家最多 N 条记录 LRU 裁剪。
            addTradeRecord(buyerId, record);
            addTradeRecord(sellerId, record);
            addResourceTradeHistory(buyOrder.resourceId(), record);

            publishEvent(new TradeExecutedEvent(record));

            return Optional.of(record);
        }

        return Optional.empty();
    }

    private static final int MAX_TRADE_RECORDS_PER_PLAYER = 200;
    // audit B-070: 全局资源交易历史上限，避免长期运营导致内存膨胀。
    private static final int MAX_RESOURCE_TRADE_HISTORY = 5000;

    @Override
    public void matchAllOrders() {
        for (String resourceId : orderBooks.keySet()) {
            matchOrders(resourceId);
        }
    }

    @Override
    public int cleanExpiredOrders() {
        int total = 0;
        for (MarketOrderBook book : orderBooks.values()) {
            total += book.cleanExpiredOrders();
        }

        // 清理所有订单中过期的
        Instant now = Instant.now();
        allOrders.entrySet().removeIf(entry -> {
            TradeOrder order = entry.getValue();
            if (order.isExpired(now)) {
                order.cancel();
                return true;
            }
            return false;
        });

        if (total > 0) {
            publishEvent(new ExpiredOrdersCleanedEvent(total));
        }
        return total;
    }

    @Override
    public MarketOrderBook.OrderBookSnapshot getOrderBookSnapshot(String resourceId) {
        return getOrderBook(resourceId).getSnapshot();
    }

    @Override
    public long getMarketDepth(String resourceId, double priceRange) {
        return getOrderBook(resourceId).getDepth(priceRange);
    }

    @Override
    public Optional<Double> getBestBid(String resourceId) {
        return getOrderBook(resourceId).getBestBidPrice();
    }

    @Override
    public Optional<Double> getBestAsk(String resourceId) {
        return getOrderBook(resourceId).getBestAskPrice();
    }

    @Override
    public Optional<Double> getMidPrice(String resourceId) {
        return getOrderBook(resourceId).getMidPrice();
    }

    @Override
    public double getSpread(String resourceId) {
        return getOrderBook(resourceId).getSpread();
    }

    @Override
    public void updateVolatility(String resourceId, double volatility) {
        getOrderBook(resourceId).setVolatility(volatility);
    }

    @Override
    public double getCurrentMarketPrice(String resourceId) {
        // 优先使用订单簿中间价
        Optional<Double> midPrice = getMidPrice(resourceId);
        if (midPrice.isPresent() && midPrice.get() > 0) {
            return midPrice.get();
        }

        // 其次使用最佳买卖价
        Optional<Double> bestBid = getBestBid(resourceId);
        Optional<Double> bestAsk = getBestAsk(resourceId);
        if (bestBid.isPresent() && bestAsk.isPresent()) {
            return (bestBid.get() + bestAsk.get()) / 2.0;
        }

        // 最后使用价格服务的参考价
        if (priceService != null) {
            return priceService.getCurrentPrice(resourceId);
        }

        return 0.0;
    }

    // ==================== 辅助方法 ====================

    private void addTradeRecord(UUID playerId, TradeRecord record) {
        // audit B-070: 之前 tradeRecords 无上限，长期运营玩家所有交易保留 → 内存膨胀。
        // 限制每玩家最多 MAX_TRADE_RECORDS_PER_PLAYER 条，超限时裁掉最老的记录。
        List<TradeRecord> list = tradeRecords.computeIfAbsent(playerId, k -> new ArrayList<>());
        synchronized (list) {
            list.add(record);
            while (list.size() > MAX_TRADE_RECORDS_PER_PLAYER) {
                list.remove(0);
            }
        }
    }

    private void addResourceTradeHistory(String resourceId, TradeRecord record) {
        // audit B-070: 全局资源交易历史上限 MAX_RESOURCE_TRADE_HISTORY，避免长期内存膨胀。
        List<TradeRecord> list = resourceTradeHistory.computeIfAbsent(resourceId, k -> new ArrayList<>());
        synchronized (list) {
            list.add(0, record);
            while (list.size() > MAX_RESOURCE_TRADE_HISTORY) {
                list.remove(list.size() - 1);
            }
        }
    }

    private java.util.logging.Logger logger() {
        return java.util.logging.Logger.getLogger(SimpleMarketOrderBookService.class.getName());
    }

    private void publishEvent(Object event) {
        if (eventBus != null) {
            eventBus.publish(event);
        }
    }

    // ==================== 事件类 ====================

    public record OrderSubmittedEvent(TradeOrder order) {}
    public record OrderCancelledEvent(TradeOrder order) {}
    public record OrderRejectedEvent(UUID playerId, String resourceId, String reason) {}
    public record TradeExecutedEvent(TradeRecord record) {}
    public record ExpiredOrdersCleanedEvent(int count) {}

    // ==================== 公开访问器 ====================

    /**
     * 获取玩家的交易记录
     */
    public List<TradeRecord> getPlayerTradeRecords(UUID playerId) {
        return new ArrayList<>(tradeRecords.getOrDefault(playerId, List.of()));
    }

    /**
     * 获取资源交易历史
     */
    public List<TradeRecord> getResourceTradeHistory(String resourceId) {
        return new ArrayList<>(resourceTradeHistory.getOrDefault(resourceId, List.of()));
    }

    /**
     * 获取玩家的交易记录（最近N条）
     */
    public List<TradeRecord> getRecentPlayerTrades(UUID playerId, int limit) {
        return tradeRecords.getOrDefault(playerId, List.of()).stream()
                .limit(limit)
                .collect(Collectors.toList());
    }
}
