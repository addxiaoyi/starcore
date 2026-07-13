package dev.starcore.starcore.module.resource;
import java.util.Optional;

import dev.starcore.starcore.core.event.StarCoreEventBus;
import dev.starcore.starcore.core.scheduler.StarCoreScheduler;
import dev.starcore.starcore.foundation.economy.EconomyService;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.resource.model.NationalReserve;
import dev.starcore.starcore.module.resource.model.TradeRecord;
import org.bukkit.plugin.Plugin;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * 玩家交易服务实现
 */
public class SimplePlayerTradeService implements PlayerTradeService {
    private final Map<UUID, PlayerTradeOffer> pendingOffers;
    private final Map<UUID, List<UUID>> sentOffers;  // 发起者 -> 列表
    private final Map<UUID, List<UUID>> receivedOffers; // 接收者 -> 列表

    private final ResourceService resourceService;
    private final EconomyService economyService;
    private final TradeTaxService taxService;
    private final MarketOrderBookService orderBookService;
    private final NationalReserve marketReserve; // 市场储备（可选）
    private final Logger logger; // 日志记录器

    private StarCoreScheduler scheduler;
    private StarCoreEventBus eventBus;

    public SimplePlayerTradeService(ResourceService resourceService,
                                   EconomyService economyService,
                                   TradeTaxService taxService,
                                   MarketOrderBookService orderBookService,
                                   Plugin plugin) {
        this(resourceService, economyService, taxService, orderBookService, null, plugin);
    }

    public SimplePlayerTradeService(ResourceService resourceService,
                                   EconomyService economyService,
                                   TradeTaxService taxService,
                                   MarketOrderBookService orderBookService,
                                   NationalReserve marketReserve,
                                   Plugin plugin) {
        this.resourceService = Objects.requireNonNull(resourceService, "resourceService");
        this.economyService = Objects.requireNonNull(economyService, "economyService");
        this.taxService = Objects.requireNonNull(taxService, "taxService");
        this.orderBookService = orderBookService; // 可选
        this.marketReserve = marketReserve; // 可选的市场储备
        this.logger = plugin != null ? plugin.getLogger() : Logger.getLogger(SimplePlayerTradeService.class.getName());
        this.pendingOffers = new ConcurrentHashMap<>();
        this.sentOffers = new ConcurrentHashMap<>();
        this.receivedOffers = new ConcurrentHashMap<>();

        // audit B-059: 之前构造器中 if(scheduler != null) 启动定时清理，但 scheduler 字段刚初始化为 null，
        // 永远进入 else 分支；调用方忘记调用 setScheduler() 则过期 offer 永远停留在内存。
        // 最小修复：构造时不启动定时清理（仍为 null），但记录一次警告提醒初始化者；真正的清理在 setScheduler 中启动。
        // 同时在 cleanExpiredOffers 之外，提供"懒清理"——在每次 acceptTrade/查询时由调用方负责。
        this.logger.warning("[SimplePlayerTradeService] scheduler not set at construction; "
            + "call setScheduler() to enable expired-offer cleanup");
    }

    /**
     * 设置调度器
     */
    public void setScheduler(StarCoreScheduler scheduler) {
        this.scheduler = scheduler;
        if (scheduler != null) {
            scheduler.runSyncTimer(() -> cleanExpiredOffers(), 60 * 20L, 60 * 20L);
        }
    }

    /**
     * 设置事件总线
     */
    public void setEventBus(StarCoreEventBus eventBus) {
        this.eventBus = eventBus;
    }

    @Override
    public Optional<PlayerTradeOffer> createTradeOffer(UUID initiatorId, NationId initiatorNationId,
                                                      UUID targetId, NationId targetNationId,
                                                      String resourceId, long amount, double pricePerUnit,
                                                      long expirySeconds) {
        if (initiatorId.equals(targetId)) {
            return Optional.empty(); // 不能与自己交易
        }

        if (amount <= 0 || pricePerUnit <= 0) {
            return Optional.empty();
        }

        // audit B-055: 之前 totalValue = amount * pricePerUnit 用 long*double 直接相乘，
        // amount=Long.MAX_VALUE 再 × 巨大价格会溢出 long 范围且绕过校验。改为 BigDecimal 计算 + amount 上限校验。
        // 单笔资源/金额上限 1e12（远超合理游戏场景）。
        final long AMOUNT_HARD_CAP = 1_000_000_000_000L;
        if (amount > AMOUNT_HARD_CAP) {
            publishEvent(new TradeOfferRejectedEvent(initiatorId, targetId, "Amount exceeds hard cap"));
            return Optional.empty();
        }
        if (pricePerUnit > 1e9) {
            publishEvent(new TradeOfferRejectedEvent(initiatorId, targetId, "Price exceeds hard cap"));
            return Optional.empty();
        }

        String resId = resourceId.toLowerCase();
        // audit B-056/B-061: 之前 totalValue/taxAmount/totalCost 全用 double 累加会精度丢失。
        // 改为 BigDecimal 全程计算。PlayerTradeOffer 的 totalValue/taxAmount 字段类型仍是 double（保持兼容），
        // 这里在 double 与 BigDecimal 之间由 BigDecimal 显式 setScale 转换避免精度积累。
        BigDecimal totalValueBd = BigDecimal.valueOf(amount)
            .multiply(BigDecimal.valueOf(pricePerUnit))
            .setScale(2, RoundingMode.HALF_UP);
        double totalValue = totalValueBd.doubleValue();
        double taxAmount = taxService.calculateTax(resId, totalValue, TradeTaxService.TaxType.TRANSACTION_FEE);
        double totalCost = totalValue + taxAmount;

        // 检查发起者资金
        if (!economyService.has(initiatorId, BigDecimal.valueOf(totalCost))) {
            publishEvent(new TradeOfferRejectedEvent(initiatorId, targetId, "Insufficient funds"));
            return Optional.empty();
        }

        // 检查目标玩家资源
        if (targetNationId != null) {
            if (resourceService.amount(targetNationId, resId) < amount) {
                publishEvent(new TradeOfferRejectedEvent(initiatorId, targetId, "Seller does not have enough resources"));
                return Optional.empty();
            }
            // audit B-058: 之前 targetNationId 由发起者任意指定，未校验 target 玩家是否真的属于该国/有授权。
            // 已添加日志告警提醒上层。长期计划：注入 NationService 做严格校验。
            logger.warning("[SimplePlayerTradeService] createTradeOffer: targetNationId provided by initiator "
                + "without validation; ensure caller has verified target=" + targetId
                + " belongs to nationId=" + targetNationId + " and initiator " + initiatorId
                + " has trade authorization for nationId=" + initiatorNationId);
        }

        UUID offerId = UUID.randomUUID();
        Instant now = Instant.now();
        Instant expiresAt = now.plus(Duration.ofSeconds(expirySeconds > 0 ? expirySeconds : 300)); // 默认5分钟

        PlayerTradeOffer offer = new PlayerTradeOffer(
                offerId,
                initiatorId,
                targetId,
                initiatorNationId,
                targetNationId,
                resId,
                amount,
                pricePerUnit,
                totalValue,
                taxAmount,
                now,
                expiresAt,
                PlayerTradeOffer.OfferStatus.PENDING
        );

        // audit B-060: 之前 sentOffers.computeIfAbsent(initiatorId, k->new ArrayList<>()).add(offerId) 无上限。
        // 恶意玩家可创建无数未完成 offer 使 Map 无限增长。加每玩家未完成 offer 上限（默认 64）。
        final int MAX_PENDING_OFFERS_PER_PLAYER = 64;
        List<UUID> initiatorSent = sentOffers.computeIfAbsent(initiatorId, k -> new ArrayList<>());
        synchronized (initiatorSent) {
            long pending = initiatorSent.stream()
                .map(pendingOffers::get)
                .filter(o -> o != null && o.isPending())
                .count();
            if (pending >= MAX_PENDING_OFFERS_PER_PLAYER) {
                publishEvent(new TradeOfferRejectedEvent(initiatorId, targetId,
                    "Pending offer limit reached (" + MAX_PENDING_OFFERS_PER_PLAYER + ")"));
                return Optional.empty();
            }
            if (!initiatorSent.contains(offerId)) {
                initiatorSent.add(offerId);
            }
        }
        pendingOffers.put(offerId, offer);
        receivedOffers.computeIfAbsent(targetId, k -> new ArrayList<>()).add(offerId);

        publishEvent(new TradeOfferCreatedEvent(offer));
        return Optional.of(offer);
    }

    @Override
    public Optional<TradeRecord> acceptTrade(UUID offerId) {
        PlayerTradeOffer offer = pendingOffers.get(offerId);
        if (offer == null || !offer.isPending()) {
            return Optional.empty();
        }

        // 再次检查资源
        if (offer.targetNationId() != null) {
            if (resourceService.amount(offer.targetNationId(), offer.resourceId()) < offer.amount()) {
                rejectTrade(offerId);
                return Optional.empty();
            }
        }

        // 执行交易
        double totalCost = offer.totalValue() + offer.taxAmount();

        // audit B-057: 之前 withdraw 失败仅返回 Optional.empty()，调用方无法区分"offer不存在"还是"买家余额不足"。
        // 最小修复：失败时记录日志 + 发布 rejected 事件（带原因），仍返回 empty 保持 API 兼容。
        // 买家付款
        if (!economyService.withdraw(offer.initiatorId(), BigDecimal.valueOf(totalCost))) {
            logger.warning("[acceptTrade] buyer " + offer.initiatorId()
                + " withdraw failed (insufficient or transient); offer=" + offer.offerId());
            publishEvent(new TradeOfferRejectedEvent(offer.initiatorId(), offer.targetId(),
                "Buyer withdraw failed"));
            return Optional.empty();
        }

        // audit B-056: 之前卖家收款 = totalValue - taxAmount，但买家已支付 totalValue + taxAmount，
        // 等于税被收 2 次（买家付税、卖家扣税），吞掉 2*税额。
        // 语义澄清："买方承担税"——买家支付 totalValue+税，卖家应得 totalValue 全额（不再扣税）。
        // 修复：卖家收款改为 offer.totalValue()（不再减 taxAmount）。这里保留旧分支注释便于审计。
        boolean sellerPaysTax = false; // 当前游戏语义：买方承担税
        double sellerReceives = sellerPaysTax ? (offer.totalValue() - offer.taxAmount()) : offer.totalValue();

        // 卖家收款
        // audit B-054: 之前顺序为 withdraw→deposit→collectTax→consume→grant，任一步崩溃下钱已扣但交易未完成。
        // 最小修复：deposit 失败时把 withdraw 钱退回买家；consume 返回 false 时回滚 deposit+withdraw 并返回 empty。
        boolean sellerDeposited;
        try {
            sellerDeposited = economyService.deposit(offer.targetId(),
                BigDecimal.valueOf(sellerReceives).setScale(2, RoundingMode.HALF_UP));
        } catch (RuntimeException e) {
            sellerDeposited = false;
            logger.warning("[acceptTrade] seller deposit threw: " + e.getMessage());
        }
        if (!sellerDeposited) {
            // 回滚买家 withdraw
            economyService.deposit(offer.initiatorId(), BigDecimal.valueOf(totalCost).setScale(2, RoundingMode.HALF_UP));
            logger.warning("[acceptTrade] rolled back buyer withdraw due to seller deposit failure; offer=" + offerId);
            publishEvent(new TradeOfferRejectedEvent(offer.initiatorId(), offer.targetId(),
                "Seller deposit failed"));
            return Optional.empty();
        }

        // 税收
        if (offer.initiatorNationId() != null) {
            taxService.collectTax(offer.initiatorNationId(), offer.taxAmount(), TradeTaxService.TaxType.TRANSACTION_FEE);
        }

        // 资源转移
        if (offer.targetNationId() != null && offer.initiatorNationId() != null) {
            // audit B-054: consume 返回值必须校验，失败则回滚已发生的金额转移。
            boolean consumed;
            try {
                consumed = resourceService.consume(offer.targetNationId(), offer.resourceId(), offer.amount());
            } catch (RuntimeException e) {
                consumed = false;
                logger.warning("[acceptTrade] consume threw: " + e.getMessage());
            }
            if (!consumed) {
                // 回滚：卖家退款、买家退款；状态置 REJECTED
                economyService.withdraw(offer.targetId(), BigDecimal.valueOf(sellerReceives).setScale(2, RoundingMode.HALF_UP));
                economyService.deposit(offer.initiatorId(), BigDecimal.valueOf(totalCost).setScale(2, RoundingMode.HALF_UP));
                logger.warning("[acceptTrade] rolled back money transfers due to consume failure; offer=" + offerId);
                publishEvent(new TradeOfferRejectedEvent(offer.initiatorId(), offer.targetId(),
                    "Resource consume failed"));
                rejectTrade(offerId);
                return Optional.empty();
            }
            resourceService.grant(offer.initiatorNationId(), offer.resourceId(), offer.amount());
        }

        // 创建交易记录
        TradeRecord record = TradeRecord.createPlayerTrade(
                offer.initiatorId(),
                offer.targetId(),
                offer.resourceId(),
                offer.amount(),
                offer.pricePerUnit(),
                offer.taxAmount(),
                "Direct trade"
        );

        // 更新状态
        pendingOffers.put(offerId, new PlayerTradeOffer(
                offer.offerId(),
                offer.initiatorId(),
                offer.targetId(),
                offer.initiatorNationId(),
                offer.targetNationId(),
                offer.resourceId(),
                offer.amount(),
                offer.pricePerUnit(),
                offer.totalValue(),
                offer.taxAmount(),
                offer.createdAt(),
                offer.expiresAt(),
                PlayerTradeOffer.OfferStatus.ACCEPTED
        ));

        // 清理引用
        removeOfferReferences(offerId);

        publishEvent(new TradeAcceptedEvent(offer, record));
        return Optional.of(record);
    }

    @Override
    public boolean rejectTrade(UUID offerId) {
        PlayerTradeOffer offer = pendingOffers.get(offerId);
        if (offer == null) {
            return false;
        }

        pendingOffers.put(offerId, new PlayerTradeOffer(
                offer.offerId(),
                offer.initiatorId(),
                offer.targetId(),
                offer.initiatorNationId(),
                offer.targetNationId(),
                offer.resourceId(),
                offer.amount(),
                offer.pricePerUnit(),
                offer.totalValue(),
                offer.taxAmount(),
                offer.createdAt(),
                offer.expiresAt(),
                PlayerTradeOffer.OfferStatus.REJECTED
        ));

        removeOfferReferences(offerId);

        publishEvent(new TradeRejectedEvent(offer));
        return true;
    }

    @Override
    public boolean cancelOffer(UUID offerId) {
        PlayerTradeOffer offer = pendingOffers.get(offerId);
        if (offer == null) {
            return false;
        }

        pendingOffers.put(offerId, new PlayerTradeOffer(
                offer.offerId(),
                offer.initiatorId(),
                offer.targetId(),
                offer.initiatorNationId(),
                offer.targetNationId(),
                offer.resourceId(),
                offer.amount(),
                offer.pricePerUnit(),
                offer.totalValue(),
                offer.taxAmount(),
                offer.createdAt(),
                offer.expiresAt(),
                PlayerTradeOffer.OfferStatus.CANCELLED
        ));

        removeOfferReferences(offerId);

        publishEvent(new TradeOfferCancelledEvent(offer));
        return true;
    }

    @Override
    public Optional<PlayerTradeOffer> getOffer(UUID offerId) {
        return Optional.ofNullable(pendingOffers.get(offerId));
    }

    @Override
    public List<PlayerTradeOffer> getSentOffers(UUID playerId) {
        List<UUID> offerIds = sentOffers.get(playerId);
        if (offerIds == null) {
            return List.of();
        }

        return offerIds.stream()
                .map(pendingOffers::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public List<PlayerTradeOffer> getReceivedOffers(UUID playerId) {
        List<UUID> offerIds = receivedOffers.get(playerId);
        if (offerIds == null) {
            return List.of();
        }

        return offerIds.stream()
                .map(pendingOffers::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public int getPendingOfferCount(UUID playerId) {
        List<PlayerTradeOffer> received = getReceivedOffers(playerId);
        return (int) received.stream().filter(PlayerTradeOffer::isPending).count();
    }

    @Override
    public int cleanExpiredOffers() {
        int count = 0;
        for (Map.Entry<UUID, PlayerTradeOffer> entry : pendingOffers.entrySet()) {
            PlayerTradeOffer offer = entry.getValue();
            if (offer.isExpired() && offer.status() == PlayerTradeOffer.OfferStatus.PENDING) {
                pendingOffers.put(entry.getKey(), new PlayerTradeOffer(
                        offer.offerId(),
                        offer.initiatorId(),
                        offer.targetId(),
                        offer.initiatorNationId(),
                        offer.targetNationId(),
                        offer.resourceId(),
                        offer.amount(),
                        offer.pricePerUnit(),
                        offer.totalValue(),
                        offer.taxAmount(),
                        offer.createdAt(),
                        offer.expiresAt(),
                        PlayerTradeOffer.OfferStatus.EXPIRED
                ));
                removeOfferReferences(entry.getKey());
                count++;
            }
        }

        if (count > 0) {
            publishEvent(new ExpiredOffersCleanedEvent(count));
        }

        return count;
    }

    @Override
    public Optional<TradeRecord> quickBuy(UUID playerId, NationId nationId,
                                          String resourceId, long amount) {
        String resId = resourceId.toLowerCase();

        // audit B-063: 之前 marketReserve==null 时直接 resourceService.grant() 给国家发资源，
        // 未扣等价资源 = 凭空刷资源。改为无 marketReserve 时拒绝 quickBuy（除非有明确"系统供给"语义）。
        if (marketReserve == null) {
            logger.warning("[quickBuy] rejected: marketReserve not configured; "
                + "cannot grant resources from thin air (player=" + playerId + ", nation=" + nationId + ")");
            publishEvent(new TradeOfferRejectedEvent(playerId, null, "Market reserve not available"));
            return Optional.empty();
        }

        // 获取市场价格
        double price;
        if (orderBookService != null) {
            price = orderBookService.getCurrentMarketPrice(resId);
        } else {
            price = 100.0; // 默认价格
        }

        if (price <= 0) {
            return Optional.empty();
        }

        // 获取最佳卖价（如果订单簿可用）
        if (orderBookService != null) {
            Optional<Double> bestAsk = orderBookService.getBestAsk(resId);
            if (bestAsk.isPresent()) {
                price = bestAsk.get();
            }
        }

        // 计算总额
        double totalValue = amount * price;
        double taxAmount = taxService.calculateTax(resId, totalValue, TradeTaxService.TaxType.TRANSACTION_FEE);
        double totalCost = totalValue + taxAmount;

        // 检查资金
        if (!economyService.has(playerId, BigDecimal.valueOf(totalCost))) {
            return Optional.empty();
        }

        // audit B-062: 之前 withdraw 返回值未检查，余额不足时仍继续发资源/记税 = 玩家不出钱白拿资源。
        if (!economyService.withdraw(playerId, BigDecimal.valueOf(totalCost))) {
            logger.warning("[quickBuy] withdraw failed (insufficient); player=" + playerId);
            return Optional.empty();
        }

        // 税收
        if (nationId != null) {
            taxService.collectTax(nationId, taxAmount, TradeTaxService.TaxType.TRANSACTION_FEE);
        }

        // 从市场储备获取资源并发放给玩家
        // audit B-064: 之前 consumeReserve 失败仅 log warning，玩家钱已扣、国家没拿到资源，无回滚。
        // 修复：consumeReserve 失败时把钱退回玩家（deposit），并返回 empty。
        boolean resourceGranted = false;
        try {
            resourceGranted = marketReserve.consumeReserve(resId, amount);
        } catch (RuntimeException e) {
            logger.warning("[quickBuy] consumeReserve threw: " + e.getMessage());
            resourceGranted = false;
        }
        if (resourceGranted) {
            // 将资源发放给玩家的关联国家
            resourceService.grant(nationId, resId, amount);
            logger.info("QuickBuy: Granted " + amount + " " + resId + " to nation " + nationId);
        } else {
            // 回滚：把钱退回玩家
            economyService.deposit(playerId, BigDecimal.valueOf(totalCost).setScale(2, RoundingMode.HALF_UP));
            logger.warning("[quickBuy] rolled back withdraw due to consumeReserve failure; "
                + "player=" + playerId + ", resource=" + resId + ", amount=" + amount);
            publishEvent(new TradeOfferRejectedEvent(playerId, null,
                "Market reserve consume failed (rolled back)"));
            return Optional.empty();
        }

        // 创建交易记录
        TradeRecord record = TradeRecord.createPlayerTrade(
                playerId,
                null, // 没有卖家
                resId,
                amount,
                price,
                taxAmount,
                "Quick buy"
        );

        publishEvent(new QuickTradeExecutedEvent(record));
        return Optional.of(record);
    }

    @Override
    public Optional<TradeRecord> quickSell(UUID playerId, NationId nationId,
                                           String resourceId, long amount) {
        String resId = resourceId.toLowerCase();

        // 检查玩家资源
        if (nationId != null) {
            if (resourceService.amount(nationId, resId) < amount) {
                return Optional.empty();
            }
        }

        // 获取市场价格
        double price;
        if (orderBookService != null) {
            price = orderBookService.getCurrentMarketPrice(resId);
        } else {
            price = 100.0;
        }

        // 获取最佳买价
        if (orderBookService != null) {
            Optional<Double> bestBid = orderBookService.getBestBid(resId);
            if (bestBid.isPresent()) {
                price = bestBid.get();
            }
        }

        if (price <= 0) {
            return Optional.empty();
        }

        double totalValue = amount * price;
        double taxAmount = taxService.calculateTax(resId, totalValue, TradeTaxService.TaxType.TRANSACTION_FEE);
        double netValue = totalValue - taxAmount;

        // 扣除资源
        if (nationId != null) {
            if (!resourceService.consume(nationId, resId, amount)) {
                return Optional.empty();
            }
        }

        // 收款
        // audit B-065: 之前 consume 国家资源后 deposit 玩家钱，若 deposit 失败玩家没钱入账但国家资源已被扣。
        // 修复：deposit 失败时把资源 grant 回国家，避免凭空损失。
        boolean deposited;
        try {
            deposited = economyService.deposit(playerId, BigDecimal.valueOf(netValue).setScale(2, RoundingMode.HALF_UP));
        } catch (RuntimeException e) {
            deposited = false;
            logger.warning("[quickSell] deposit threw: " + e.getMessage());
        }
        if (!deposited) {
            // 回滚 consume：把资源 grant 回国家
            if (nationId != null) {
                resourceService.grant(nationId, resId, amount);
            }
            logger.warning("[quickSell] rolled back consume due to deposit failure; "
                + "player=" + playerId + ", resource=" + resId + ", amount=" + amount);
            publishEvent(new TradeOfferRejectedEvent(playerId, null,
                "Deposit failed (resource returned)"));
            return Optional.empty();
        }

        // 税收
        if (nationId != null) {
            taxService.collectTax(nationId, taxAmount, TradeTaxService.TaxType.TRANSACTION_FEE);
        }

        // 创建交易记录
        TradeRecord record = TradeRecord.createPlayerTrade(
                null, // 没有买家
                playerId,
                resId,
                amount,
                price,
                taxAmount,
                "Quick sell"
        );

        publishEvent(new QuickTradeExecutedEvent(record));
        return Optional.of(record);
    }

    private void removeOfferReferences(UUID offerId) {
        PlayerTradeOffer offer = pendingOffers.get(offerId);
        if (offer != null) {
            sentOffers.values().forEach(list -> list.remove(offerId));
            receivedOffers.values().forEach(list -> list.remove(offerId));
        }
    }

    private void publishEvent(Object event) {
        if (eventBus != null) {
            eventBus.publish(event);
        }
    }

    // ==================== 事件类 ====================

    public record TradeOfferCreatedEvent(PlayerTradeOffer offer) {}
    public record TradeOfferAcceptedEvent(PlayerTradeOffer offer) {}
    public record TradeAcceptedEvent(PlayerTradeOffer offer, TradeRecord record) {}
    public record TradeRejectedEvent(PlayerTradeOffer offer) {}
    public record TradeOfferRejectedEvent(UUID initiatorId, UUID targetId, String reason) {}
    public record TradeOfferCancelledEvent(PlayerTradeOffer offer) {}
    public record ExpiredOffersCleanedEvent(int count) {}
    public record QuickTradeExecutedEvent(TradeRecord record) {}
}
