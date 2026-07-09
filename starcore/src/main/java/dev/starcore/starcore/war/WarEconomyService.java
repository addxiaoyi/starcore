package dev.starcore.starcore.war;
import java.util.Optional;

import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.treasury.TreasuryService;
import org.bukkit.plugin.Plugin;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * 战争经济服务
 * 管理军工生产、战争债券、资源征用等战争经济活动
 */
public final class WarEconomyService {
    private final Plugin plugin;
    private final TreasuryService treasuryService;
    private final Logger logger;
    private final WarEconomyConfig config;

    // 战争债券
    private final ConcurrentHashMap<UUID, WarBond> warBonds = new ConcurrentHashMap<>();
    // 国家的债券索引
    private final ConcurrentHashMap<NationId, Set<UUID>> nationBonds = new ConcurrentHashMap<>();
    // 军工生产订单
    private final ConcurrentHashMap<UUID, MilitaryProductionOrder> productionOrders = new ConcurrentHashMap<>();

    public WarEconomyService(
        Plugin plugin,
        TreasuryService treasuryService,
        WarEconomyConfig config
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.treasuryService = Objects.requireNonNull(treasuryService, "treasuryService");
        this.config = Objects.requireNonNull(config, "config");
        this.logger = plugin.getLogger();

        startPeriodicTasks();
    }

    /**
     * 发行战争债券
     */
    public WarBond issueWarBond(
        UUID warId,
        NationId nationId,
        BigDecimal faceValue,
        double interestRate,
        int termMonths
    ) {
        Objects.requireNonNull(warId, "warId");
        Objects.requireNonNull(nationId, "nationId");
        Objects.requireNonNull(faceValue, "faceValue");

        if (faceValue.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Face value must be positive");
        }

        if (interestRate <= 0 || interestRate > 1) {
            throw new IllegalArgumentException("Interest rate must be between 0 and 1");
        }

        if (termMonths <= 0) {
            throw new IllegalArgumentException("Term must be positive");
        }

        WarBond bond = new WarBond(
            UUID.randomUUID(),
            warId,
            nationId,
            faceValue,
            interestRate,
            termMonths,
            Instant.now()
        );

        warBonds.put(bond.id(), bond);
        nationBonds.computeIfAbsent(nationId, k -> ConcurrentHashMap.newKeySet())
            .add(bond.id());

        logger.info(String.format("War bond issued: Nation=%s, Value=%s, Rate=%.2f%%, Term=%d months",
            nationId, faceValue, interestRate * 100, termMonths));

        return bond;
    }

    /**
     * 购买战争债券
     */
    public void purchaseWarBond(UUID bondId, UUID buyerId, String buyerName, BigDecimal amount) {
        WarBond bond = warBonds.get(bondId);
        if (bond == null) {
            throw new IllegalArgumentException("War bond not found");
        }

        if (bond.status() != WarBond.BondStatus.ACTIVE) {
            throw new IllegalStateException("Bond is not active");
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }

        // 记录购买
        bond.purchase(buyerId, buyerName, amount);

        // 资金进入国库
        treasuryService.deposit(bond.nationId(), amount);

        logger.info(String.format("War bond purchased: Buyer=%s, Amount=%s, Bond=%s",
            buyerName, amount, bondId));
    }

    /**
     * 赎回债券
     */
    public BigDecimal redeemWarBond(UUID bondId, UUID holderId) {
        WarBond bond = warBonds.get(bondId);
        if (bond == null) {
            throw new IllegalArgumentException("War bond not found");
        }

        if (!bond.isMature(Instant.now())) {
            throw new IllegalStateException("Bond is not yet mature");
        }

        // 计算赎回金额（本金 + 利息）
        BigDecimal holderAmount = bond.holderAmount(holderId);
        if (holderAmount.compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalArgumentException("Holder has no bonds");
        }

        BigDecimal redemptionAmount = bond.calculateRedemptionValue(holderAmount);

        // 从国库支付
        if (!treasuryService.withdraw(bond.nationId(), redemptionAmount)) {
            throw new IllegalStateException("Insufficient funds in treasury");
        }

        // 移除持有记录
        bond.redeem(holderId);

        logger.info(String.format("War bond redeemed: Holder=%s, Amount=%s, Value=%s",
            holderId, holderAmount, redemptionAmount));

        return redemptionAmount;
    }

    /**
     * 获取战争债券
     */
    public Optional<WarBond> getWarBond(UUID bondId) {
        return Optional.ofNullable(warBonds.get(bondId));
    }

    /**
     * 获取国家的所有债券
     */
    public List<WarBond> getWarBondsOfNation(NationId nationId) {
        Set<UUID> bondIds = nationBonds.getOrDefault(nationId, Collections.emptySet());
        return bondIds.stream()
            .map(warBonds::get)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    /**
     * 创建军工生产订单
     */
    public MilitaryProductionOrder createProductionOrder(
        NationId nationId,
        String itemType,
        int quantity,
        BigDecimal costPerUnit
    ) {
        Objects.requireNonNull(nationId, "nationId");
        Objects.requireNonNull(itemType, "itemType");

        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }

        if (costPerUnit.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Cost must be positive");
        }

        // 计算总成本和生产时间
        BigDecimal totalCost = costPerUnit.multiply(BigDecimal.valueOf(quantity));
        int productionHours = calculateProductionTime(quantity);

        // 检查国库
        if (treasuryService.balance(nationId).compareTo(totalCost) < 0) {
            throw new IllegalStateException("Insufficient funds");
        }

        // 扣除费用
        treasuryService.withdraw(nationId, totalCost);

        MilitaryProductionOrder order = new MilitaryProductionOrder(
            UUID.randomUUID(),
            nationId,
            itemType,
            quantity,
            totalCost,
            Instant.now(),
            Instant.now().plusSeconds(productionHours * 3600L)
        );

        productionOrders.put(order.id(), order);

        logger.info(String.format("Military production order: Nation=%s, Item=%s, Qty=%d, Cost=%s",
            nationId, itemType, quantity, totalCost));

        return order;
    }

    /**
     * 获取生产订单
     */
    public Optional<MilitaryProductionOrder> getProductionOrder(UUID orderId) {
        return Optional.ofNullable(productionOrders.get(orderId));
    }

    /**
     * 获取国家的生产订单
     */
    public List<MilitaryProductionOrder> getProductionOrdersOfNation(NationId nationId) {
        return productionOrders.values().stream()
            .filter(order -> order.nationId().equals(nationId))
            .collect(Collectors.toList());
    }

    /**
     * 计算生产时间（小时）
     */
    private int calculateProductionTime(int quantity) {
        // 简单线性：每100件需要1小时
        return Math.max(1, quantity / 100);
    }

    /**
     * 启动定时任务
     */
    private void startPeriodicTasks() {
        // 每小时检查一次
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            checkProductionOrders();
            checkBondMaturity();
        }, 20L * 60 * 60, 20L * 60 * 60);
    }

    /**
     * 检查生产订单
     */
    private void checkProductionOrders() {
        Instant now = Instant.now();

        for (MilitaryProductionOrder order : productionOrders.values()) {
            if (order.status() == MilitaryProductionOrder.OrderStatus.IN_PROGRESS) {
                if (now.isAfter(order.completionTime())) {
                    order.complete();
                    logger.info(String.format("Production order completed: %s", order.id()));
                }
            }
        }
    }

    /**
     * 检查债券到期
     */
    private void checkBondMaturity() {
        Instant now = Instant.now();

        for (WarBond bond : warBonds.values()) {
            if (bond.status() == WarBond.BondStatus.ACTIVE && bond.isMature(now)) {
                bond.mature();
                logger.info(String.format("War bond matured: %s", bond.id()));
            }
        }
    }

    /**
     * 军工生产订单
     */
    public static final class MilitaryProductionOrder {
        private final UUID id;
        private final NationId nationId;
        private final String itemType;
        private final int quantity;
        private final BigDecimal totalCost;
        private final Instant startTime;
        private final Instant completionTime;
        private OrderStatus status;

        public MilitaryProductionOrder(
            UUID id,
            NationId nationId,
            String itemType,
            int quantity,
            BigDecimal totalCost,
            Instant startTime,
            Instant completionTime
        ) {
            this.id = Objects.requireNonNull(id, "id");
            this.nationId = Objects.requireNonNull(nationId, "nationId");
            this.itemType = Objects.requireNonNull(itemType, "itemType");
            this.quantity = quantity;
            this.totalCost = Objects.requireNonNull(totalCost, "totalCost");
            this.startTime = Objects.requireNonNull(startTime, "startTime");
            this.completionTime = Objects.requireNonNull(completionTime, "completionTime");
            this.status = OrderStatus.IN_PROGRESS;
        }

        public UUID id() { return id; }
        public NationId nationId() { return nationId; }
        public String itemType() { return itemType; }
        public int quantity() { return quantity; }
        public BigDecimal totalCost() { return totalCost; }
        public Instant startTime() { return startTime; }
        public Instant completionTime() { return completionTime; }
        public OrderStatus status() { return status; }

        public void complete() {
            this.status = OrderStatus.COMPLETED;
        }

        public void cancel() {
            this.status = OrderStatus.CANCELLED;
        }

        public double progressPercentage(Instant now) {
            if (status == OrderStatus.COMPLETED) {
                return 100.0;
            }
            if (now.isAfter(completionTime)) {
                return 100.0;
            }

            long total = completionTime.getEpochSecond() - startTime.getEpochSecond();
            long elapsed = now.getEpochSecond() - startTime.getEpochSecond();

            return Math.min(100.0, (double) elapsed / total * 100.0);
        }

        public enum OrderStatus {
            IN_PROGRESS("生产中"),
            COMPLETED("已完成"),
            CANCELLED("已取消");

            private final String displayName;

            OrderStatus(String displayName) {
                this.displayName = displayName;
            }

            public String displayName() {
                return displayName;
            }
        }

        @Override
        public String toString() {
            return String.format("ProductionOrder{id=%s, item=%s, qty=%d, status=%s}",
                id, itemType, quantity, status);
        }
    }

    /**
     * 战争经济配置
     */
    public record WarEconomyConfig(
        BigDecimal minBondValue,
        double maxInterestRate,
        int maxBondTermMonths,
        double productionEfficiencyMultiplier
    ) {
        public static WarEconomyConfig defaults() {
            return new WarEconomyConfig(
                new BigDecimal("100"),      // 最低债券面值
                0.15,                       // 最高利率15%
                60,                         // 最长期限60个月
                1.0                         // 生产效率系数
            );
        }
    }
}
