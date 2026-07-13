package dev.starcore.starcore.module.treasury;

import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.module.ModuleLayer;
import dev.starcore.starcore.core.module.ModuleMetadata;
import dev.starcore.starcore.core.module.StarCoreModule;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.economy.TransactionHistoryService;
import dev.starcore.starcore.module.event.EventService;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.resource.ResourceTradeService;
import dev.starcore.starcore.module.resource.model.TradeAgreement;
import dev.starcore.starcore.module.resource.model.TradeRoute;
import dev.starcore.starcore.module.treasury.TaxationService.TaxConfig;
import dev.starcore.starcore.module.treasury.TaxationService.TaxContext;
import dev.starcore.starcore.module.treasury.TaxationService.TaxRevenue;
import dev.starcore.starcore.module.treasury.TaxationService.TaxType;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * 税收服务实现
 * 管理多种税种的配置和征收
 *
 * 计划集成（待实现）:
 * - BusinessService: 获取真实商业活动数据和交易额
 * - TransactionHistoryService: 记录玩家交易历史用于准确计算所得税
 * - PlayerActivityService: 获取玩家在线时长辅助估算收入
 *
 * 当前使用估算值计算，待上述服务实现后可替换为真实数据
 */
public final class TaxationServiceImpl implements StarCoreModule, TaxationService {
    private static final int SCALE = 2;

    private static final ModuleMetadata METADATA = new ModuleMetadata(
        "taxation",
        "税收系统",
        ModuleLayer.MODULE,
        List.of("nation", "treasury"),
        List.of(TaxationService.class),
        "Provides multiple tax types: land, business, income, and tariff."
    );

    // 国家税种配置: nationId -> (taxType -> config)
    private final ConcurrentMap<NationId, EnumMap<TaxType, TaxConfig>> nationTaxConfigs = new ConcurrentHashMap<>();

    // 税收历史: nationId -> list of tax revenues (按时间倒序)
    private final ConcurrentMap<NationId, List<TaxRevenue>> taxHistories = new ConcurrentHashMap<>();
    private static final int MAX_HISTORY_SIZE = 1000;
    /** audit B-024: 税率百分比上限 100，防止 admin 配置过大导致负扣玩家 */
    private static final BigDecimal MAX_PERCENT_RATE = new BigDecimal("100");
    /** audit B-025: 自动收税最小间隔（5 分钟）避免 1 秒级别密集收税刷屏事务和日志 */
    private static final long MIN_AUTO_TICKS = 5L * 60L * 20L;

    private final ConcurrentMap<NationId, Long> lastCollectionTime = new ConcurrentHashMap<>();

    private BukkitTask autoCollectTask;
    private StarCoreContext currentContext;

    @Override
    public ModuleMetadata metadata() {
        return METADATA;
    }

    @Override
    public void enable(StarCoreContext context) {
        this.currentContext = context;
        loadConfigs(context);
        startAutoCollectTask(context);
    }

    @Override
    public void disable(StarCoreContext context) {
        stopAutoCollectTask();
        flushConfigs();
        this.currentContext = null;
    }

    // ==================== 配置管理 ====================

    @Override
    public Map<TaxType, TaxConfig> getTaxConfigs(NationId nationId) {
        return nationTaxConfigs.computeIfAbsent(nationId, id -> {
            EnumMap<TaxType, TaxConfig> defaults = new EnumMap<>(TaxType.class);
            // 初始化默认配置
            defaults.put(TaxType.LAND_TAX, createDefaultLandTaxConfig());
            defaults.put(TaxType.BUSINESS_TAX, createDefaultBusinessTaxConfig());
            defaults.put(TaxType.INCOME_TAX, createDefaultIncomeTaxConfig());
            defaults.put(TaxType.TARIFF, createDefaultTariffConfig());
            return defaults;
        });
    }

    @Override
    public TaxConfig getTaxConfig(NationId nationId, TaxType type) {
        return getTaxConfigs(nationId).get(type);
    }

    @Override
    public void setTaxConfig(NationId nationId, TaxType type, TaxConfig config) {
        // audit B-027: 校验 percentRate 0..100，避免 admin 误设 >100 的税率导致贸易额负扣玩家
        Objects.requireNonNull(nationId, "nationId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(config, "config");
        if (config.percentRate().compareTo(MAX_PERCENT_RATE) > 0) {
            throw new IllegalArgumentException(
                "percentRate " + config.percentRate() + " 超出 0..100 上限，配置被拒绝");
        }

        nationTaxConfigs.computeIfAbsent(nationId, id -> new EnumMap<>(TaxType.class))
            .put(type, config);
        saveConfigs();
    }

    // ==================== 默认配置 ====================

    private TaxConfig createDefaultLandTaxConfig() {
        return new TaxConfig(
            TaxType.LAND_TAX,
            false,                          // 默认禁用
            new BigDecimal("10.00"),        // 每块地 10 元
            BigDecimal.ZERO,                // 无百分比
            BigDecimal.ZERO,                // 无最低余额
            1440,                          // 每天
            false                           // 不自动征收
        );
    }

    private TaxConfig createDefaultBusinessTaxConfig() {
        return new TaxConfig(
            TaxType.BUSINESS_TAX,
            false,                          // 默认禁用
            BigDecimal.ZERO,                // 无固定金额
            new BigDecimal("5.00"),         // 营业额的 5%
            BigDecimal.ZERO,                // 无最低余额
            1440,                          // 每天
            false                           // 不自动征收
        );
    }

    private TaxConfig createDefaultIncomeTaxConfig() {
        return new TaxConfig(
            TaxType.INCOME_TAX,
            false,                          // 默认禁用
            BigDecimal.ZERO,                // 无固定金额
            new BigDecimal("10.00"),        // 收入的 10%
            new BigDecimal("100.00"),       // 100 元以下免征
            1440,                          // 每天
            false                           // 不自动征收
        );
    }

    private TaxConfig createDefaultTariffConfig() {
        return new TaxConfig(
            TaxType.TARIFF,
            false,                          // 默认禁用
            BigDecimal.ZERO,                // 无固定金额
            new BigDecimal("3.00"),         // 贸易额的 3%
            BigDecimal.ZERO,                // 无最低余额
            1440,                          // 每天
            false                           // 不自动征收
        );
    }

    // ==================== 税收征收 ====================

    @Override
    public BigDecimal collectTax(NationId nationId, TaxType type, TaxContext context) {
        TaxConfig config = getTaxConfig(nationId, type);
        if (config == null || !config.enabled()) {
            return BigDecimal.ZERO;
        }

        BigDecimal amount = calculateTaxAmount(type, config, context);
        if (amount.signum() <= 0) {
            return BigDecimal.ZERO;
        }

        // 从玩家账户扣除（所得税需要逐个玩家扣税）
        if (type == TaxType.INCOME_TAX) {
            amount = collectIncomeTaxFromPlayers(nationId, config, context);
        }

        // audit B-022/B-029: 防止凭空印钞——非所得税类（LAND_TAX/BUSINESS_TAX/TARIFF）当前实现
        // 直接 treasury.deposit 国库而不扣任何玩家，是无中生有。最小修复：仅当存在明确扣款源时入账；
        // 对这些税种目前没有扣款源，故 deposit 前要求 caller 显式传入已扣款证据（amount <= 已确认从玩家或国库扣除）。
        // 由于当前没有扣款渠道，改为：仅记录事件并 admin 警告，不再直接 deposit 想象出来的金额到国库。
        TreasuryService treasury = currentContext.serviceRegistry().find(TreasuryService.class).orElse(null);
        if (treasury != null) {
            if (type == TaxType.INCOME_TAX) {
                // 只有这一类有真实扣款源（玩家）
                treasury.deposit(nationId, amount);
            } else {
                // 其他税种目前没有扣款源，凭空 deposit 会造成国库凭空印钞；已跳过并记录警告。
                // 长期计划：为各税种建立明确扣款源后再启用 deposit。
                currentContext.plugin().getLogger().warning("[Tax] 税种 " + type + " 当前未配置扣款源，已跳过 treasury.deposit 避免凭空印发国库资金."
                    + " amount=" + amount + " nationId=" + nationId);
                // 金额不算 reel income，归零返回
                amount = BigDecimal.ZERO;
            }
        }

        // 记录历史
        recordTaxRevenue(nationId, type, amount, context);

        // 发送事件
        recordTaxEvent(nationId, type, amount);

        return amount;
    }

    @Override
    public Map<TaxType, BigDecimal> collectAllTaxes(NationId nationId, TaxContext context) {
        Map<TaxType, BigDecimal> results = new EnumMap<>(TaxType.class);

        for (TaxType type : TaxType.values()) {
            BigDecimal amount = collectTax(nationId, type, context);
            results.put(type, amount);
        }

        return results;
    }

    private BigDecimal calculateTaxAmount(TaxType type, TaxConfig config, TaxContext context) {
        BigDecimal amount = BigDecimal.ZERO;

        switch (type) {
            case LAND_TAX -> {
                // 土地税 = 固定金额 * 领土数
                // audit B-023: 校验 claimCount 非负，避免负领土数导致负税收（最终可能从国库扣款）
                long claimCount = Math.max(0, context.claimCount());
                if (config.fixedAmount().signum() > 0) {
                    amount = config.fixedAmount().multiply(BigDecimal.valueOf(claimCount));
                }
            }
            case BUSINESS_TAX -> {
                // 商业税 = 交易额 * 百分比
                // audit B-024: 校验 percentRate 在 0..100 之间，避免 admin 误设过高的税率导致贸易额*10倍的负扣玩家
                BigDecimal pct = config.percentRate();
                if (pct.signum() > 0 && pct.compareTo(MAX_PERCENT_RATE) <= 0
                        && context.totalTradeValue().signum() > 0) {
                    amount = context.totalTradeValue()
                        .multiply(pct)
                        .divide(new BigDecimal("100"), SCALE, RoundingMode.DOWN);
                } else if (pct.compareTo(MAX_PERCENT_RATE) > 0) {
                    currentContext.plugin().getLogger().warning(
                        "[Tax] BUSINESS_TAX percentRate " + pct + " 超出 0..100 上限，本次按 0 计税；请检查配置。");
                }
            }
            case INCOME_TAX -> {
                // 所得税在 collectIncomeTaxFromPlayers 中计算
            }
            case TARIFF -> {
                // 关税 = 贸易额 * 百分比
                BigDecimal pct = config.percentRate();
                if (pct.signum() > 0 && pct.compareTo(MAX_PERCENT_RATE) <= 0
                        && context.totalTradeValue().signum() > 0) {
                    amount = context.totalTradeValue()
                        .multiply(pct)
                        .divide(new BigDecimal("100"), SCALE, RoundingMode.DOWN);
                } else if (pct.compareTo(MAX_PERCENT_RATE) > 0) {
                    currentContext.plugin().getLogger().warning(
                        "[Tax] TARIFF percentRate " + pct + " 超出 0..100 上限，本次按 0 计税；请检查配置。");
                }
            }
        }

        return amount.setScale(SCALE, RoundingMode.DOWN);
    }

    private BigDecimal collectIncomeTaxFromPlayers(NationId nationId, TaxConfig config, TaxContext context) {
        BigDecimal totalCollected = BigDecimal.ZERO;
        List<PlayerIncome> incomes = context.playerIncomes();

        if (incomes == null || incomes.isEmpty()) {
            return BigDecimal.ZERO;
        }

        for (PlayerIncome playerIncome : incomes) {
            BigDecimal taxableIncome = playerIncome.income();
            if (taxableIncome.compareTo(config.minimumBalance()) < 0) {
                continue; // 低于最低标准，跳过
            }

            BigDecimal taxAmount;
            if (config.fixedAmount().signum() > 0) {
                // 固定金额
                taxAmount = config.fixedAmount();
            } else if (config.percentRate().signum() > 0 && config.percentRate().compareTo(MAX_PERCENT_RATE) <= 0) {
                // 百分比（audit B-024：校验 percentRate 在 0..100 之间）
                taxAmount = taxableIncome
                    .multiply(config.percentRate())
                    .divide(new BigDecimal("100"), SCALE, RoundingMode.DOWN);
            } else if (config.percentRate().compareTo(MAX_PERCENT_RATE) > 0) {
                currentContext.plugin().getLogger().warning(
                    "[Tax] INCOME_TAX percentRate " + config.percentRate() + " 超出 0..100 上限，跳过该玩家本次扣税；请检查配置。");
                continue;
            } else {
                continue;
            }

            // 从玩家账户扣除
            if (withdrawFromPlayer(playerIncome.playerId(), taxAmount)) {
                totalCollected = totalCollected.add(taxAmount);
            }
        }

        return totalCollected.setScale(SCALE, RoundingMode.DOWN);
    }

    private boolean withdrawFromPlayer(UUID playerId, BigDecimal amount) {
        // 获取经济服务
        var economyService = currentContext.economyService();
        return economyService.withdraw(playerId, amount);
    }

    // ==================== 历史记录 ====================

    @Override
    public List<TaxRevenue> getTaxHistory(NationId nationId, TaxType type, int limit) {
        List<TaxRevenue> history = taxHistories.getOrDefault(nationId, List.of());
        return history.stream()
            .filter(r -> type == null || r.type() == type)
            .limit(limit)
            .toList();
    }

    private void recordTaxRevenue(NationId nationId, TaxType type, BigDecimal amount, TaxContext context) {
        TaxRevenue revenue = new TaxRevenue(nationId, type, amount, Instant.now(), context);
        // audit B-026: add+裁剪放入单一 synchronized 块，保证 size 控制原子；add 仍线程安全但与裁剪一致
        List<TaxRevenue> history = taxHistories.computeIfAbsent(nationId, id -> new CopyOnWriteArrayList<>());
        synchronized (history) {
            history.add(0, revenue);
            while (history.size() > MAX_HISTORY_SIZE) {
                history.remove(history.size() - 1);
            }
        }
    }

    private void recordTaxEvent(NationId nationId, TaxType type, BigDecimal amount) {
        EventService eventService = currentContext == null ? null :
            currentContext.serviceRegistry().find(EventService.class).orElse(null);
        if (eventService == null) {
            return;
        }

        String message = taxEventMessage(type, amount);
        String context = "type=" + type.name() + ";amount=" + amount.toPlainString();

        eventService.record(nationId, "treasury.tax." + type.getConfigKey(), message, context);
    }

    private String taxEventMessage(TaxType type, BigDecimal amount) {
        MessageService messages = currentContext == null ? null :
            currentContext.serviceRegistry().find(MessageService.class).orElse(null);
        if (messages == null) {
            return "Collected " + type.getDisplayName() + " " + amount.toPlainString();
        }
        return messages.format("command.event.message.treasury-tax-type",
            "STARCORE", type.getDisplayName(), amount.toPlainString());
    }

    // ==================== 自动征收 ====================

    private void startAutoCollectTask(StarCoreContext context) {
        stopAutoCollectTask();
        if (!context.configuration().nationTaxEnabled() || !context.configuration().nationTaxAutoEnabled()) {
            return;
        }
        Duration interval = context.configuration().nationTaxAutoInterval();
        // audit B-025: 下限提升到 5 分钟（6000 tick），避免每秒级密集收税产生大量并发事务和日志
        long intervalTicks = Math.max(MIN_AUTO_TICKS, Math.min(172_800L, interval.toSeconds() * 20L));

        this.autoCollectTask = Bukkit.getScheduler().runTaskTimer(
            context.plugin(),
            () -> performAutoCollection(context),
            intervalTicks,
            intervalTicks
        );
        context.plugin().getLogger().info("STARCORE taxation auto collection enabled, intervalTicks=" + intervalTicks);
    }

    private void stopAutoCollectTask() {
        if (autoCollectTask != null) {
            autoCollectTask.cancel();
            autoCollectTask = null;
        }
    }

    private void performAutoCollection(StarCoreContext context) {
        var nationService = currentContext.serviceRegistry().find(
            dev.starcore.starcore.module.nation.NationService.class).orElse(null);
        if (nationService == null) {
            return;
        }

        for (var nation : nationService.nations()) {
            try {
                TaxContext taxContext = buildTaxContext(nation.id(), nationService);
                collectAllTaxes(nation.id(), taxContext);
            } catch (Exception e) {
                context.plugin().getLogger().log(Level.WARNING,
                    "Failed to auto-collect taxes for nation " + nation.name(), e);
            }
        }
    }

    private TaxContext buildTaxContext(NationId nationId, dev.starcore.starcore.module.nation.NationService nationService) {
        int claimCount = Math.max(0, nationService.claimCount(nationId));

        var nationOpt = nationService.nationById(nationId);
        int memberCount = nationOpt.map(n -> n.members().size()).orElse(0);

        // ============================================================
        // 从 ResourceTradeService 获取商业活动数据
        // ============================================================
        ResourceTradeService tradeService = currentContext.serviceRegistry()
            .find(ResourceTradeService.class).orElse(null);

        int businessCount = 0;
        BigDecimal totalTradeValue = BigDecimal.ZERO;

        if (tradeService != null) {
            // 获取贸易路线（代表商业活跃度）
            Collection<TradeRoute> tradeRoutes = tradeService.getTradeRoutes(nationId);
            businessCount = tradeRoutes.size();

            // 计算总贸易额: 基于活跃贸易协定的交易量 * 平均价格
            Collection<TradeAgreement> agreements = tradeService.getTradeAgreements(nationId);
            for (TradeAgreement agreement : agreements) {
                if (agreement.isActive()) {
                    // 贸易额 = 数量 * 单价
                    BigDecimal agreementValue = BigDecimal.valueOf(agreement.amount())
                        .multiply(BigDecimal.valueOf(agreement.pricePerUnit()));
                    totalTradeValue = totalTradeValue.add(agreementValue);
                }
            }
        }

        // 收集玩家收入数据
        List<PlayerIncome> playerIncomes = new ArrayList<>();
        if (nationOpt.isPresent()) {
            var economyService = currentContext.economyService();
            for (var member : nationOpt.get().members()) {
                BigDecimal balance = economyService.balance(member.playerId());

                // ============================================================
                // [FIXED] 玩家真实收入估算 - 使用交易历史服务（如果可用）
                // ============================================================
                BigDecimal estimatedIncome = estimatePlayerIncome(member.playerId(), balance);

                playerIncomes.add(new PlayerIncome(member.playerId(), estimatedIncome, balance));
            }
        }

        return new TaxContext(claimCount, memberCount, businessCount, totalTradeValue, playerIncomes);
    }

    /**
     * 估算玩家收入
     *
     * 优先使用交易历史服务的真实数据，否则返回 0（不再使用不准确的余额估算）
     *
     * @param playerId 玩家 UUID
     * @param balance 当前余额（仅用于备用）
     * @return 估算的30天收入
     */
    private BigDecimal estimatePlayerIncome(UUID playerId, BigDecimal balance) {
        // 尝试从 TransactionHistoryService 获取真实收入数据
        var transactionService = currentContext.serviceRegistry()
            .find(dev.starcore.starcore.module.economy.TransactionHistoryService.class)
            .orElse(null);

        if (transactionService != null) {
            try {
                // 获取最近30天的真实收入
                BigDecimal realIncome = transactionService.getIncome(playerId, Duration.ofDays(30));
                if (realIncome != null && realIncome.signum() > 0) {
                    return realIncome.setScale(SCALE, RoundingMode.DOWN);
                }
            } catch (Exception e) {
                // 服务异常，回退到旧方法
                currentContext.plugin().getLogger().warning(
                    "Failed to get real income for " + playerId + ": " + e.getMessage());
            }
        } else {
            // audit B-028: 当无 TransactionHistoryService 时所得税必为 0，但 TaxConfig.enabled=true 会让玩家误以为被收税。
            // 至少在 admin 级别警告一次，避免财政黑盒。
            currentContext.plugin().getLogger().warning(
                "[Tax] TransactionHistoryService 未启用，所得税将无法对玩家真实收入征收，"
                + "若 INCOME_TAX enabled=true 请启用 TransactionHistoryService 模块。");
        }

        // [DEPRECATED] 备用方案：使用余额估算
        // 注意：这种方法不准确，已标记为废弃，仅在没有交易历史时使用
        // 问题：新玩家余额低但活跃度高会被低估，存款多的玩家可能被高估
        currentContext.plugin().getLogger().fine(
            "Using deprecated balance estimation for player " + playerId + " - consider enabling TransactionHistoryService");

        return BigDecimal.ZERO;  // 不再使用不准确的余额估算，返回0表示无数据
    }

    // ==================== 持久化 ====================

    private void loadConfigs(StarCoreContext context) {
        try {
            var persistenceService = context.persistenceService();
            var namespace = "treasury";
            var fileName = "tax-configs.properties";

            persistenceService.ensureNamespace(namespace).join();
            java.util.Properties props = persistenceService.loadProperties(namespace, fileName);

            if (props.isEmpty()) {
                return;
            }

            int count = Integer.parseInt(props.getProperty("count", "0"));
            for (int i = 0; i < count; i++) {
                String prefix = "nation." + i + ".";
                try {
                    NationId nationId = new NationId(UUID.fromString(props.getProperty(prefix + "nationId")));

                    EnumMap<TaxType, TaxConfig> configs = new EnumMap<>(TaxType.class);

                    // 加载每个税种配置
                    for (TaxType type : TaxType.values()) {
                        String typePrefix = prefix + type.name() + ".";
                        boolean enabled = Boolean.parseBoolean(props.getProperty(typePrefix + "enabled", "false"));
                        BigDecimal fixedAmount = new BigDecimal(
                            props.getProperty(typePrefix + "fixedAmount", "0"));
                        BigDecimal percentRate = new BigDecimal(
                            props.getProperty(typePrefix + "percentRate", "0"));
                        BigDecimal minimumBalance = new BigDecimal(
                            props.getProperty(typePrefix + "minimumBalance", "0"));
                        int interval = Integer.parseInt(
                            props.getProperty(typePrefix + "interval", "1440"));
                        boolean autoCollect = Boolean.parseBoolean(
                            props.getProperty(typePrefix + "autoCollect", "false"));

                        configs.put(type, new TaxConfig(type, enabled, fixedAmount,
                            percentRate, minimumBalance, interval, autoCollect));
                    }

                    nationTaxConfigs.put(nationId, configs);

                } catch (Exception e) {
                    context.plugin().getLogger().warning("Failed to load tax config #" + i + ": " + e.getMessage());
                }
            }
            context.plugin().getLogger().info("Loaded tax configs for " + count + " nations");

        } catch (Exception e) {
            context.plugin().getLogger().warning("Failed to load tax configs: " + e.getMessage());
        }
    }

    private void saveConfigs() {
        try {
            var persistenceService = currentContext.persistenceService();
            var namespace = "treasury";
            var fileName = "tax-configs.properties";

            java.util.Properties props = new java.util.Properties();
            List<NationId> nationIdList = new ArrayList<>(nationTaxConfigs.keySet());
            props.setProperty("count", String.valueOf(nationIdList.size()));

            for (int i = 0; i < nationIdList.size(); i++) {
                NationId nationId = nationIdList.get(i);
                EnumMap<TaxType, TaxConfig> configs = nationTaxConfigs.get(nationId);
                String prefix = "nation." + i + ".";
                props.setProperty(prefix + "nationId", nationId.toString());

                if (configs != null) {
                    for (Map.Entry<TaxType, TaxConfig> entry : configs.entrySet()) {
                        TaxType type = entry.getKey();
                        TaxConfig config = entry.getValue();
                        String typePrefix = prefix + type.name() + ".";
                        props.setProperty(typePrefix + "enabled", String.valueOf(config.enabled()));
                        props.setProperty(typePrefix + "fixedAmount", config.fixedAmount().toString());
                        props.setProperty(typePrefix + "percentRate", config.percentRate().toString());
                        props.setProperty(typePrefix + "minimumBalance", config.minimumBalance().toString());
                        props.setProperty(typePrefix + "interval", String.valueOf(config.collectionIntervalMinutes()));
                        props.setProperty(typePrefix + "autoCollect", String.valueOf(config.autoCollect()));
                    }
                }
            }

            persistenceService.saveProperties(namespace, fileName, props);

        } catch (Exception e) {
            // audit B-028: 配置写入失败会导致下次 reload 丢失变更，已升级为 severe 并向上抛出
            // 长期计划：引入原子写入机制（WAL 或临时文件+rename）
            currentContext.plugin().getLogger().severe(
                "[Tax] saveConfigs 失败：当前内存配置已变更但未落盘，下次 reload 将丢失。原因: " + e.getMessage());
            throw new RuntimeException("saveConfigs failed: " + e.getMessage(), e);
        }
    }

    private void flushConfigs() {
        saveConfigs();
    }
}
