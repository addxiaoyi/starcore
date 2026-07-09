package dev.starcore.starcore.module.treasury;

import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.module.ModuleLayer;
import dev.starcore.starcore.core.module.ModuleMetadata;
import dev.starcore.starcore.core.module.StarCoreModule;
import dev.starcore.starcore.foundation.animation.GuiAnimationManager;
import dev.starcore.starcore.foundation.animation.SoundFeedbackManager;
import dev.starcore.starcore.foundation.economy.InternalEconomyService;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.economy.EconomyTrendService;
import dev.starcore.starcore.module.economy.gui.EconomyTrendGui;
import dev.starcore.starcore.module.economy.gui.EconomyTrendGuiListener;
import dev.starcore.starcore.module.treasury.gui.TreasuryMenu;
import dev.starcore.starcore.module.treasury.gui.TreasuryMenuListener;
import dev.starcore.starcore.module.event.EventService;
import dev.starcore.starcore.module.event.NationEventRecord;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.policy.PolicyService;
import dev.starcore.starcore.module.policy.model.PolicyEffectScope;
import dev.starcore.starcore.module.treasury.BankruptcyService;
import dev.starcore.starcore.module.treasury.BankruptcyService.Restriction;
import dev.starcore.starcore.module.treasury.BudgetService.BudgetBill;
import dev.starcore.starcore.module.treasury.BudgetService.BudgetCategory;
import dev.starcore.starcore.module.treasury.LoanService.NationDebt;
import dev.starcore.starcore.module.treasury.TaxationService.TaxContext;
import dev.starcore.starcore.module.treasury.TaxationService.TaxType;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TreasuryModule implements StarCoreModule, TreasuryService, TreasuryRewardService {
    private static final int SCALE = 2;

    private static final ModuleMetadata METADATA = new ModuleMetadata(
        "treasury",
        "金库核心",
        ModuleLayer.MODULE,
        List.of("nation", "government", "resolution"),
        List.of(TreasuryService.class, TreasuryRewardService.class),
        "Owns national finance, tax, budgets, and public debt abstractions."
    );

    private final ConcurrentMap<NationId, BigDecimal> balances = new ConcurrentHashMap<>();
    private TreasuryStateStorage stateStorage;
    private BukkitTask dailyIncomeTask;
    private BukkitTask taxTask;
    private StarCoreContext currentContext;
    private PolicyService policyService;

    // 子服务引用（集成模式）
    private TaxationService taxationService;
    private LoanService loanService;
    private BankruptcyService bankruptcyService;
    private BudgetService budgetService;
    private EventService eventService;

    // GUI 相关
    private TreasuryMenu treasuryMenu;
    private TreasuryMenuListener treasuryMenuListener;

    // 经济趋势分析 GUI
    private EconomyTrendService economyTrendService;
    private EconomyTrendGui economyTrendGui;
    private EconomyTrendGuiListener economyTrendGuiListener;

    // 用于从事件上下文解析金额的正则表达式
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("amount=([\\d.]+)");

    @Override
    public ModuleMetadata metadata() {
        return METADATA;
    }

    @Override
    public void enable(StarCoreContext context) {
        this.currentContext = context;
        context.persistenceService().ensureNamespace(metadata().id());
        this.stateStorage = new DatabaseAwareTreasuryStateStorage(
            metadata().id(),
            context.databaseService(),
            context.persistenceService(),
            context.plugin().getLogger()
        );
        // 获取 PolicyService 以应用国策效果
        this.policyService = context.serviceRegistry().find(PolicyService.class).orElse(null);

        // 初始化子服务（如果已注册）
        this.taxationService = context.serviceRegistry().find(TaxationService.class).orElse(null);
        this.loanService = context.serviceRegistry().find(LoanService.class).orElse(null);
        this.bankruptcyService = context.serviceRegistry().find(BankruptcyService.class).orElse(null);
        this.budgetService = context.serviceRegistry().find(BudgetService.class).orElse(null);
        this.eventService = context.serviceRegistry().find(EventService.class).orElse(null);

        // 获取共享服务
        NationService nationService = context.serviceRegistry().find(NationService.class).orElse(null);
        GuiAnimationManager animationManager = context.serviceRegistry().find(GuiAnimationManager.class).orElse(null);
        SoundFeedbackManager soundManager = context.serviceRegistry().find(SoundFeedbackManager.class).orElse(null);

        // 初始化经济趋势分析 GUI（先创建，因为 TreasuryMenuListener 需要它）
        this.economyTrendService = new EconomyTrendService(this.eventService, this);
        this.economyTrendGui = new EconomyTrendGui(economyTrendService, this, nationService, animationManager, soundManager);
        this.economyTrendGuiListener = new EconomyTrendGuiListener(economyTrendService, this, nationService, economyTrendGui, soundManager);
        context.plugin().getServer().getPluginManager().registerEvents(economyTrendGuiListener, context.plugin());

        // 初始化国库 GUI 和监听器
        this.treasuryMenu = new TreasuryMenu(this, this, nationService, animationManager, soundManager);
        this.treasuryMenuListener = new TreasuryMenuListener(this, treasuryMenu, nationService, soundManager, economyTrendGui);
        context.plugin().getServer().getPluginManager().registerEvents(treasuryMenuListener, context.plugin());

        loadState();
        startDailyIncomeTask(context);
        startTaxTask(context);
    }

    @Override
    public void disable(StarCoreContext context) {
        stopDailyIncomeTask();
        stopTaxTask();
        flushState();
        this.currentContext = null;
    }

    @Override
    public BigDecimal balance(NationId nationId) {
        return balances.getOrDefault(nationId, BigDecimal.ZERO).setScale(SCALE, RoundingMode.DOWN);
    }

    @Override
    public void deposit(NationId nationId, BigDecimal amount) {
        requirePositive(amount);
        // audit B-050: 之前 deposit 无上限校验，极端金额（如 10^15）直接 merge 进 balances。
        // 虽然 BigDecimal 支持任意大值，但持久化为 String 后部分数据库会异常；且玩家间财政数额超出
        // 合理范围通常意味着 bug/作弊。新增 maxTreasuryBalance 软上限警告。
        BigDecimal normalized = normalize(amount);
        BigDecimal maxBalance = new BigDecimal("1000000000000000.00"); // 1e15，远超合理游戏场景额度
        BigDecimal current = balances.getOrDefault(nationId, BigDecimal.ZERO);
        BigDecimal after = current.add(normalized);
        if (after.compareTo(maxBalance) > 0) {
            currentContext.plugin().getLogger().warning(
                "[Treasury] deposit by " + nationId + " would push balance to " + after.toPlainString()
                    + " (exceeds soft cap " + maxBalance.toPlainString() + "); clamping to cap and logging");
            // 软上限：截断至 max 以防 DB 解析异常，但保留告警便于 admin 排查
            normalized = maxBalance.subtract(current);
            if (normalized.signum() <= 0) {
                return; // 已达上限，不再累加
            }
        }
        balances.merge(nationId, normalized, BigDecimal::add);
        // audit B-051/B-053: deposit 影响国库余额，属于关键交易。之前用 saveState()=saveAsync，
        // 异步保存可能延迟或失败，玩家已入账但状态没落盘 → 崩溃后回档。改为同步保存 + 失败告警。
        saveStateSync();
    }

    @Override
    public boolean withdraw(NationId nationId, BigDecimal amount) {
        requirePositive(amount);
        BigDecimal normalized = normalize(amount);
        boolean[] withdrawn = new boolean[] { false };
        balances.compute(nationId, (ignored, current) -> {
            BigDecimal balance = current == null ? BigDecimal.ZERO : current;
            if (balance.compareTo(normalized) < 0) {
                return balance;
            }
            withdrawn[0] = true;
            return balance.subtract(normalized);
        });
        if (withdrawn[0]) {
            // audit B-051/B-053: 关键交易（扣款）后必须同步落盘，避免崩溃后玩家钱已扣但国库回档。
            saveStateSync();
        }
        return withdrawn[0];
    }

    @Override
    public String summary() {
        BigDecimal total = balances.values().stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(SCALE, RoundingMode.DOWN);
        return balances.size() + " nation treasury account(s), total balance " + total.toPlainString();
    }

    @Override
    public TreasuryRewardResult reward(NationId nationId, BigDecimal amount, String actor, String reason) {
        // audit B-052: 之前先 deposit(amount) 再 recordRewardEvent，若 deposit 成功但事件记录失败，
        // 国库有钱无来源，无法审计。改为先记录事件（先 capture 入账前余额作 balance 字段，
        // 记录入账后的金额由 deposit 后再回填），再 deposit。
        // 这里用更简洁的顺序：先记录"待入账"事件 + 进入 deposit；事件失败不影响 deposit 但记录失败日志。
        // 真正事务化需要 DB 事务支持；最小修复：deposit 改为同步保存后用 try/catch 包住事件记录失败不撤销入账。
        BigDecimal balanceBefore = balance(nationId);
        // audit B-052: 先尝试记录事件（用预估的入账后金额作为 balance 字段），再实际 deposit。
        // 这样即使 deposit 之后服务器崩溃也已经留痕；若 deposit 失败则事件早记一条"幽灵入账"，
        // 接受这种边界，因为 deposit 抛异常的概率远低于事件记录失败的概率。
        BigDecimal normalized = normalize(amount);
        BigDecimal projectedBalance = balanceBefore.add(normalized);
        boolean eventRecorded;
        try {
            eventRecorded = recordRewardEvent(nationId, amount, projectedBalance, actor, reason);
        } catch (RuntimeException e) {
            currentContext.plugin().getLogger().warning(
                "[Treasury] reward recordRewardEvent failed before deposit: " + e.getMessage());
            eventRecorded = false;
        }
        deposit(nationId, amount);
        BigDecimal balanceAfter = balance(nationId);
        return new TreasuryRewardResult(nationId, normalized, balanceAfter, eventRecorded);
    }

    private boolean recordRewardEvent(NationId nationId, BigDecimal amount, BigDecimal balance, String actor, String reason) {
        EventService eventService = currentContext == null ? null : currentContext.serviceRegistry().find(EventService.class).orElse(null);
        if (eventService == null) {
            return false;
        }
        String displayActor = actor == null || actor.isBlank() ? "STARCORE" : actor.trim();
        String displayReason = reason == null || reason.isBlank() ? rewardFallbackReason() : reason.trim();
        eventService.record(
            nationId,
            "treasury.reward",
            rewardMessage(displayActor, amount, displayReason),
            rewardContext(displayActor, amount, balance, reason)
        );
        return true;
    }

    private String rewardMessage(String actor, BigDecimal amount, String reason) {
        MessageService messages = currentContext == null ? null : currentContext.serviceRegistry().find(MessageService.class).orElse(null);
        if (messages == null) {
            return actor + " rewarded nation treasury " + normalize(amount).toPlainString() + ", reason: " + reason;
        }
        return messages.format("command.event.message.treasury-reward", actor, normalize(amount).toPlainString(), reason);
    }

    private String rewardFallbackReason() {
        MessageService messages = currentContext == null ? null : currentContext.serviceRegistry().find(MessageService.class).orElse(null);
        return messages == null ? "unspecified" : messages.format("command.event.reason-unspecified");
    }

    private String rewardContext(String actor, BigDecimal amount, BigDecimal balance, String reason) {
        StringBuilder builder = new StringBuilder()
            .append("actor=").append(sanitizeContextValue(actor))
            .append(";amount=").append(normalize(amount).toPlainString())
            .append(";balance=").append(balance.toPlainString());
        if (reason != null && !reason.isBlank()) {
            builder.append(";reason=").append(sanitizeContextValue(reason));
        }
        return builder.toString();
    }

    private String sanitizeContextValue(String value) {
        return value == null ? "" : value.replace(';', ',').replace('\n', ' ').replace('\r', ' ').trim();
    }

    private void saveState() {
        if (stateStorage == null) {
            return;
        }
        stateStorage.saveAsync(TreasuryStateCodec.toProperties(balances));
    }

    /**
     * audit B-051/B-053: 同步保存国库状态。关键交易（deposit/withdraw/reward）必须同步落盘，
     * 避免异步保存失败或延迟导致玩家钱已扣但国库余额未落盘 → 崩溃后回档。
     * 失败时记录严重告警，便于 admin 排查（不能静默丢失玩家扣款结果）。
     */
    private void saveStateSync() {
        if (stateStorage == null) {
            return;
        }
        try {
            stateStorage.save(TreasuryStateCodec.toProperties(balances));
        } catch (RuntimeException e) {
            // audit B-053: 之前 saveAsync 失败无任何处理；改为同步保存并在失败时记录告警。
            // 玩家已扣款但状态未落盘属于严重数据一致性问题，需要 admin 介入。
            if (currentContext != null) {
                currentContext.plugin().getLogger().log(Level.SEVERE,
                    "[Treasury] Synchronous state save FAILED after critical treasury transaction. "
                        + "Player balance may be inconsistent after restart: " + e.getMessage(), e);
            }
        }
    }

    private void flushState() {
        if (stateStorage == null) {
            return;
        }
        stateStorage.save(TreasuryStateCodec.toProperties(balances));
    }

    private void loadState() {
        balances.clear();
        balances.putAll(TreasuryStateCodec.fromProperties(stateStorage == null ? new java.util.Properties() : stateStorage.load()));
    }

    private void startDailyIncomeTask(StarCoreContext context) {
        stopDailyIncomeTask();
        if (!context.configuration().nationDailyIncomeAutoEnabled()) {
            return;
        }
        Duration interval = context.configuration().nationDailyIncomeAutoInterval();
        long intervalTicks = Math.max(20L, Math.min(172_800L, interval.toSeconds() * 20L));
        this.dailyIncomeTask = Bukkit.getScheduler().runTaskTimer(
            context.plugin(),
            () -> settleDailyIncomeAutomatically(context),
            intervalTicks,
            intervalTicks
        );
        context.plugin().getLogger().info("STARCORE nation daily income auto settlement enabled, intervalTicks=" + intervalTicks);
    }

    private void stopDailyIncomeTask() {
        if (dailyIncomeTask != null) {
            dailyIncomeTask.cancel();
            dailyIncomeTask = null;
        }
    }

    private void startTaxTask(StarCoreContext context) {
        stopTaxTask();
        if (!context.configuration().nationTaxEnabled() || !context.configuration().nationTaxAutoEnabled()) {
            return;
        }
        Duration interval = context.configuration().nationTaxAutoInterval();
        long intervalTicks = Math.max(20L, Math.min(172_800L, interval.toSeconds() * 20L));
        this.taxTask = Bukkit.getScheduler().runTaskTimer(
            context.plugin(),
            () -> settleTaxesAutomatically(context),
            intervalTicks,
            intervalTicks
        );
        context.plugin().getLogger().info("STARCORE nation player tax auto settlement enabled, intervalTicks=" + intervalTicks);
    }

    private void stopTaxTask() {
        if (taxTask != null) {
            taxTask.cancel();
            taxTask = null;
        }
    }

    private void settleDailyIncomeAutomatically(StarCoreContext context) {
        NationService nationService = context.serviceRegistry().find(NationService.class).orElse(null);
        EventService eventService = context.serviceRegistry().find(EventService.class).orElse(null);
        if (nationService == null || eventService == null) {
            context.plugin().getLogger().warning("STARCORE daily income auto settlement skipped because nation/event service is unavailable.");
            return;
        }
        BigDecimal total = BigDecimal.ZERO;
        int settled = 0;
        for (Nation nation : nationService.nations()) {
            try {
                BigDecimal amount = dailyIncomeAmount(context, nation, nationService);
                if (amount.signum() <= 0) {
                    continue;
                }
                deposit(nation.id(), amount);
                BigDecimal balance = balance(nation.id());
                int members = nation.members().size();
                int claims = Math.max(0, nationService.claimCount(nation.id()));
                eventService.record(
                    nation.id(),
                    "treasury.income",
                    incomeMessage(context, amount, members, claims),
                    incomeContext(amount, balance, members, claims)
                );
                total = total.add(amount);
                settled++;
            } catch (RuntimeException exception) {
                context.plugin().getLogger().log(Level.WARNING, "Failed to settle STARCORE daily income for nation " + nation.name(), exception);
            }
        }
        if (settled > 0) {
            context.plugin().getLogger().info("STARCORE auto-settled daily income for " + settled + " nation(s), total " + total.setScale(SCALE, RoundingMode.DOWN).toPlainString());
        }
    }

    private BigDecimal dailyIncomeAmount(StarCoreContext context, Nation nation, NationService nationService) {
        BigDecimal base = context.configuration().nationDailyIncomeBaseAmount();
        BigDecimal memberIncome = context.configuration().nationDailyIncomePerMember().multiply(BigDecimal.valueOf(nation.members().size()));
        BigDecimal claimIncome = context.configuration().nationDailyIncomePerClaim().multiply(BigDecimal.valueOf(Math.max(0, nationService.claimCount(nation.id()))));
        BigDecimal income = base.add(memberIncome).add(claimIncome).setScale(SCALE, RoundingMode.DOWN);
        // 应用国策加成（交易收入加成）
        return applyPolicyBonus(nation.id(), income);
    }

    /**
     * 应用国策加成到收入
     * @param nationId 国家ID
     * @param baseIncome 基础收入
     * @return 应用加成后的收入
     */
    private BigDecimal applyPolicyBonus(NationId nationId, BigDecimal baseIncome) {
        if (policyService == null) {
            return baseIncome;
        }
        // 获取交易收入加成
        double modifier = policyService.activePolicyModifier(nationId, "trade_income_bonus", PolicyEffectScope.GLOBAL);
        if (modifier == 0) {
            return baseIncome;
        }
        BigDecimal multiplier = BigDecimal.ONE.add(BigDecimal.valueOf(modifier));
        return baseIncome.multiply(multiplier).setScale(SCALE, RoundingMode.DOWN);
    }

    private String incomeMessage(StarCoreContext context, BigDecimal amount, int members, int claims) {
        MessageService messages = context.serviceRegistry().find(MessageService.class).orElse(null);
        if (messages == null) {
            return "STARCORE auto settled nation daily income " + amount.toPlainString();
        }
        return messages.format("command.event.message.treasury-income", "STARCORE", amount.toPlainString(), members, claims, messages.format("command.event.reason-auto"));
    }

    private String incomeContext(BigDecimal amount, BigDecimal balance, int members, int claims) {
        return "actor=STARCORE;amount=" + amount.toPlainString()
            + ";balance=" + balance.toPlainString()
            + ";members=" + members
            + ";claims=" + claims
            + ";reason=auto";
    }

    private void settleTaxesAutomatically(StarCoreContext context) {
        NationService nationService = context.serviceRegistry().find(NationService.class).orElse(null);
        InternalEconomyService economyService = context.economyService();
        if (nationService == null || economyService == null) {
            context.plugin().getLogger().warning("STARCORE player tax auto settlement skipped because nation/economy service is unavailable.");
            return;
        }
        EventService eventService = context.serviceRegistry().find(EventService.class).orElse(null);
        BigDecimal total = BigDecimal.ZERO;
        int settled = 0;
        int taxedMembers = 0;
        int skippedMembers = 0;
        for (Nation nation : nationService.nations()) {
            try {
                TaxSettlement settlement = settleTaxForNation(context, nation, economyService, eventService);
                if (settlement.amount().signum() <= 0) {
                    continue;
                }
                total = total.add(settlement.amount());
                taxedMembers += settlement.taxedMembers();
                skippedMembers += settlement.skippedMembers();
                settled++;
            } catch (RuntimeException exception) {
                context.plugin().getLogger().log(Level.WARNING, "Failed to settle STARCORE player tax for nation " + nation.name(), exception);
            }
        }
        if (settled > 0) {
            context.plugin().getLogger().info("STARCORE auto-settled player tax for " + settled + " nation(s), taxedMembers=" + taxedMembers + ", skippedMembers=" + skippedMembers + ", total=" + total.setScale(SCALE, RoundingMode.DOWN).toPlainString());
        }
    }

    private TaxSettlement settleTaxForNation(StarCoreContext context, Nation nation, InternalEconomyService economyService, EventService eventService) {
        BigDecimal total = BigDecimal.ZERO;
        int taxedMembers = 0;
        int skippedMembers = 0;
        for (var member : nation.members()) {
            BigDecimal memberTax = taxAmountFor(context, economyService.balance(member.playerId()));
            if (memberTax.signum() <= 0) {
                skippedMembers++;
                continue;
            }
            if (!economyService.withdraw(member.playerId(), memberTax)) {
                skippedMembers++;
                continue;
            }
            total = total.add(memberTax);
            taxedMembers++;
        }
        total = total.setScale(SCALE, RoundingMode.DOWN);
        BigDecimal balance = balance(nation.id());
        if (total.signum() <= 0) {
            return new TaxSettlement(total, taxedMembers, skippedMembers, balance);
        }
        deposit(nation.id(), total);
        balance = balance(nation.id());
        if (eventService != null) {
            eventService.record(
                nation.id(),
                "treasury.tax",
                taxMessage(context, total, taxedMembers, skippedMembers),
                taxContext(context, total, balance, taxedMembers, skippedMembers)
            );
        }
        return new TaxSettlement(total, taxedMembers, skippedMembers, balance);
    }

    private BigDecimal taxAmountFor(StarCoreContext context, BigDecimal balance) {
        BigDecimal safeBalance = balance == null ? BigDecimal.ZERO : normalize(balance);
        BigDecimal protectedBalance = context.configuration().nationTaxMinimumBalance();
        BigDecimal taxableBalance = safeBalance.subtract(protectedBalance).max(BigDecimal.ZERO).setScale(SCALE, RoundingMode.DOWN);
        if (taxableBalance.signum() <= 0) {
            return BigDecimal.ZERO.setScale(SCALE, RoundingMode.DOWN);
        }
        BigDecimal percentageTax = taxableBalance
            .multiply(context.configuration().nationTaxBalancePercent())
            .divide(new BigDecimal("100.00"), SCALE, RoundingMode.DOWN);
        BigDecimal requested = context.configuration().nationTaxFixedAmount().add(percentageTax).setScale(SCALE, RoundingMode.DOWN);
        if (requested.signum() <= 0) {
            return BigDecimal.ZERO.setScale(SCALE, RoundingMode.DOWN);
        }
        if (requested.compareTo(taxableBalance) <= 0) {
            return requested;
        }
        return context.configuration().nationTaxSkipInsufficientMembers() ? BigDecimal.ZERO.setScale(SCALE, RoundingMode.DOWN) : taxableBalance;
    }

    private String taxMessage(StarCoreContext context, BigDecimal amount, int taxedMembers, int skippedMembers) {
        MessageService messages = context.serviceRegistry().find(MessageService.class).orElse(null);
        if (messages == null) {
            return "STARCORE auto settled player tax " + amount.toPlainString();
        }
        return messages.format("command.event.message.treasury-tax", "STARCORE", amount.toPlainString(), taxedMembers, skippedMembers, messages.format("command.event.reason-auto"));
    }

    private String taxContext(StarCoreContext context, BigDecimal amount, BigDecimal balance, int taxedMembers, int skippedMembers) {
        return "actor=STARCORE;amount=" + amount.toPlainString()
            + ";balance=" + balance.toPlainString()
            + ";taxedMembers=" + taxedMembers
            + ";skippedMembers=" + skippedMembers
            + ";fixed=" + context.configuration().nationTaxFixedAmount().toPlainString()
            + ";percent=" + context.configuration().nationTaxBalancePercent().toPlainString()
            + ";minimumBalance=" + context.configuration().nationTaxMinimumBalance().toPlainString()
            + ";reason=auto";
    }

    private record TaxSettlement(BigDecimal amount, int taxedMembers, int skippedMembers, BigDecimal balance) {
    }

    // ==================== 税收操作 ====================

    @Override
    public Map<TaxType, TaxationService.TaxConfig> getTaxConfigs(NationId nationId) {
        if (taxationService == null) {
            return new EnumMap<>(TaxType.class);
        }
        return taxationService.getTaxConfigs(nationId);
    }

    @Override
    public TaxationService.TaxConfig getTaxConfig(NationId nationId, TaxType type) {
        if (taxationService == null) {
            return null;
        }
        return taxationService.getTaxConfig(nationId, type);
    }

    @Override
    public void setTaxConfig(NationId nationId, TaxType type, TaxationService.TaxConfig config) {
        if (taxationService != null) {
            taxationService.setTaxConfig(nationId, type, config);
        }
    }

    @Override
    public BigDecimal collectTax(NationId nationId, TaxType type, TaxContext context) {
        if (taxationService == null) {
            return BigDecimal.ZERO;
        }
        return taxationService.collectTax(nationId, type, context);
    }

    @Override
    public Map<TaxType, BigDecimal> collectAllTaxes(NationId nationId, TaxContext context) {
        if (taxationService == null) {
            return new EnumMap<>(TaxType.class);
        }
        return taxationService.collectAllTaxes(nationId, context);
    }

    @Override
    public List<TaxationService.TaxRevenue> getTaxHistory(NationId nationId, TaxType type, int limit) {
        if (taxationService == null) {
            return List.of();
        }
        return taxationService.getTaxHistory(nationId, type, limit);
    }

    // ==================== 贷款/国债操作 ====================

    @Override
    public NationDebt applyForLoan(NationId nationId, NationId creditorId, BigDecimal principal, BigDecimal annualInterestRate, int totalInstallments) {
        // 贷款服务通过 BankruptcyServiceImpl 内部引用获取
        BankruptcyServiceImpl bankruptcyServiceImpl = getBankruptcyServiceImpl();
        LoanService effectiveLoanService = this.loanService;

        // 如果外部 loanService 不可用，尝试从破产服务获取
        if (effectiveLoanService == null && bankruptcyServiceImpl != null) {
            effectiveLoanService = bankruptcyServiceImpl.getLoanService();
        }

        if (effectiveLoanService == null) {
            throw new IllegalStateException("Loan service not available");
        }
        return effectiveLoanService.applyForLoan(nationId, creditorId, principal, annualInterestRate, totalInstallments);
    }

    private BankruptcyServiceImpl getBankruptcyServiceImpl() {
        if (currentContext == null) {
            return null;
        }
        var registry = currentContext.serviceRegistry();
        // 通过模块管理器查找 BankruptcyServiceImpl
        return registry.find(BankruptcyServiceImpl.class).orElse(null);
    }

    @Override
    public boolean repayDebt(UUID debtId, BigDecimal amount) {
        if (loanService == null) {
            return false;
        }
        return loanService.repayDebt(debtId, amount);
    }

    @Override
    public boolean payInstallment(UUID debtId) {
        if (loanService == null) {
            return false;
        }
        return loanService.payInstallment(debtId);
    }

    @Override
    public boolean forgiveDebt(UUID debtId) {
        if (loanService == null) {
            return false;
        }
        return loanService.forgiveDebt(debtId);
    }

    @Override
    public Optional<NationDebt> getDebt(UUID debtId) {
        if (loanService == null) {
            return Optional.empty();
        }
        return loanService.getDebt(debtId);
    }

    @Override
    public List<NationDebt> getDebtsByNation(NationId nationId) {
        if (loanService == null) {
            return List.of();
        }
        return loanService.getDebtsByNation(nationId);
    }

    @Override
    public List<NationDebt> getActiveDebts(NationId nationId) {
        if (loanService == null) {
            return List.of();
        }
        return loanService.getActiveDebts(nationId);
    }

    @Override
    public BigDecimal getTotalDebt(NationId nationId) {
        if (loanService == null) {
            return BigDecimal.ZERO;
        }
        return loanService.getTotalDebt(nationId);
    }

    @Override
    public boolean canBorrow(NationId nationId, BigDecimal amount) {
        if (loanService == null) {
            return false;
        }
        return loanService.canBorrow(nationId, amount);
    }

    @Override
    public BigDecimal getMaxBorrowableAmount(NationId nationId) {
        if (loanService == null) {
            return BigDecimal.ZERO;
        }
        return loanService.getMaxBorrowableAmount(nationId);
    }

    @Override
    public int getDebtHealthScore(NationId nationId) {
        if (loanService == null) {
            return 100;
        }
        return loanService.getDebtHealthScore(nationId);
    }

    // ==================== 破产操作 ====================

    @Override
    public void enterBankruptcy(NationId nationId, String reason) {
        if (bankruptcyService == null) {
            currentContext.plugin().getLogger().warning("Bankruptcy service not available, cannot enter bankruptcy for nation: " + nationId);
            return;
        }
        // 兼容旧调用：使用 EXCESSIVE_DEBT 作为默认值
        bankruptcyService.enterBankruptcy(nationId,
            dev.starcore.starcore.module.treasury.BankruptcyService.BankruptcyReason.EXCESSIVE_DEBT,
            reason);
    }

    @Override
    public void enterBankruptcy(NationId nationId, BankruptcyService.BankruptcyReason reason, String description) {
        if (bankruptcyService == null) {
            currentContext.plugin().getLogger().warning("Bankruptcy service not available, cannot enter bankruptcy for nation: " + nationId);
            return;
        }
        bankruptcyService.enterBankruptcy(nationId, reason, description);
    }

    @Override
    public boolean exitBankruptcy(NationId nationId) {
        if (bankruptcyService == null) {
            return false;
        }
        return bankruptcyService.exitBankruptcy(nationId);
    }

    @Override
    public boolean isBankrupt(NationId nationId) {
        if (bankruptcyService == null) {
            return false;
        }
        return bankruptcyService.isBankrupt(nationId);
    }

    @Override
    public Optional<BankruptcyService.BankruptcyRecord> getBankruptcyRecord(NationId nationId) {
        if (bankruptcyService == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(bankruptcyService.getBankruptcyRecord(nationId));
    }

    @Override
    public long getBankruptcyDays(NationId nationId) {
        if (bankruptcyService == null) {
            return 0;
        }
        return bankruptcyService.getBankruptcyDays(nationId);
    }

    @Override
    public boolean isRestricted(NationId nationId, BankruptcyService.Restriction restriction) {
        if (bankruptcyService == null) {
            return false;
        }
        return bankruptcyService.isRestricted(nationId, restriction);
    }

    @Override
    public List<Restriction> getActiveRestrictions(NationId nationId) {
        if (bankruptcyService == null) {
            return List.of();
        }
        return List.copyOf(bankruptcyService.getActiveRestrictions(nationId));
    }

    @Override
    public boolean earlyExitBankruptcy(NationId nationId) {
        if (bankruptcyService == null) {
            return false;
        }
        return bankruptcyService.earlyExitBankruptcy(nationId);
    }

    @Override
    public List<NationId> getAllBankruptNations() {
        if (bankruptcyService == null) {
            return List.of();
        }
        return bankruptcyService.getAllBankruptNations();
    }

    // ==================== 预算操作 ====================

    @Override
    public BudgetBill createBudgetDraft(NationId nationId, int year, int month) {
        BudgetService effectiveBudgetService = this.budgetService;
        // 如果外部 budgetService 不可用，尝试创建简化实现
        if (effectiveBudgetService == null) {
            throw new IllegalStateException("Budget service not available, cannot create budget draft");
        }
        return effectiveBudgetService.createBudgetDraft(nationId, year, month);
    }

    @Override
    public void addBudgetEntry(UUID billId, BudgetCategory category, String description, BigDecimal amount) {
        if (budgetService != null) {
            budgetService.addBudgetEntry(billId, category, description, amount);
        }
    }

    @Override
    public BudgetBill submitBudget(UUID billId) {
        if (budgetService == null) {
            throw new IllegalStateException("Budget service not available, cannot submit budget");
        }
        return budgetService.submitBudget(billId);
    }

    @Override
    public boolean executeBudget(UUID billId) {
        if (budgetService == null) {
            return false;
        }
        return budgetService.executeBudget(billId);
    }

    @Override
    public boolean cancelBudget(UUID billId) {
        if (budgetService == null) {
            return false;
        }
        return budgetService.cancelBudget(billId);
    }

    @Override
    public Optional<BudgetBill> getCurrentBudget(NationId nationId) {
        if (budgetService == null) {
            return Optional.empty();
        }
        return budgetService.getCurrentBudget(nationId);
    }

    @Override
    public List<BudgetBill> getBudgetHistory(NationId nationId, int limit) {
        if (budgetService == null) {
            return List.of();
        }
        return budgetService.getBudgetHistory(nationId, limit);
    }

    @Override
    public BudgetService.BudgetExecutionReport getBudgetExecutionReport(UUID billId) {
        if (budgetService == null) {
            return null;
        }
        return budgetService.getBudgetExecutionReport(billId);
    }

    @Override
    public BigDecimal getMonthlySpending(NationId nationId, int year, int month) {
        if (budgetService == null) {
            return BigDecimal.ZERO;
        }
        return budgetService.getMonthlySpending(nationId, year, month);
    }

    @Override
    public BigDecimal getYearlySpending(NationId nationId, int year) {
        if (budgetService == null) {
            return BigDecimal.ZERO;
        }
        return budgetService.getYearlySpending(nationId, year);
    }

    // ==================== 综合状态查询 ====================

    @Override
    public FinanceHealthReport getFinanceHealthReport(NationId nationId) {
        BigDecimal currentBalance = balance(nationId);
        BigDecimal totalDebt = getTotalDebt(nationId);
        int debtHealth = getDebtHealthScore(nationId);
        boolean bankrupt = isBankrupt(nationId);
        List<Restriction> restrictions = getActiveRestrictions(nationId);

        // 获取启用的税种
        Map<TaxType, TaxationService.TaxConfig> taxConfigs = getTaxConfigs(nationId);
        List<TaxType> enabledTaxes = taxConfigs.entrySet().stream()
            .filter(e -> e.getValue() != null && e.getValue().enabled())
            .map(Map.Entry::getKey)
            .toList();

        // 计算月度收支
        YearMonth now = YearMonth.now();
        BigDecimal monthlyIncome = dailyIncomeAmount(currentContext,
            nationId, now.getYear(), now.getMonthValue());
        BigDecimal monthlyExpense = getMonthlySpending(nationId, now.getYear(), now.getMonthValue());

        // 获取贷款配置中的最大债务限制
        BigDecimal maxDebtLimit = loanService != null
            ? loanService.getLoanConfig().maxDebtLimit()
            : new BigDecimal("100000.00");

        return new FinanceHealthReport(
            nationId,
            currentBalance,
            totalDebt,
            maxDebtLimit,
            debtHealth,
            bankrupt,
            restrictions,
            enabledTaxes,
            monthlyIncome,
            monthlyExpense,
            Instant.now()
        );
    }

    private BigDecimal dailyIncomeAmount(StarCoreContext context, NationId nationId, int year, int month) {
        if (context == null) {
            return BigDecimal.ZERO;
        }
        NationService nationService = context.serviceRegistry().find(NationService.class).orElse(null);
        if (nationService == null) {
            return BigDecimal.ZERO;
        }
        var nationOpt = nationService.nationById(nationId);
        if (nationOpt.isEmpty()) {
            return BigDecimal.ZERO;
        }
        Nation nation = nationOpt.get();
        BigDecimal base = context.configuration().nationDailyIncomeBaseAmount();
        BigDecimal memberIncome = context.configuration().nationDailyIncomePerMember()
            .multiply(BigDecimal.valueOf(nation.members().size()));
        BigDecimal claimIncome = context.configuration().nationDailyIncomePerClaim()
            .multiply(BigDecimal.valueOf(Math.max(0, nationService.claimCount(nationId))));
        return applyPolicyBonus(nationId, base.add(memberIncome).add(claimIncome).setScale(SCALE, RoundingMode.DOWN));
    }

    @Override
    public BigDecimal getTodayIncome(NationId nationId) {
        if (eventService == null) {
            return BigDecimal.ZERO;
        }
        return calculateTodayAmount(nationId, true);
    }

    @Override
    public BigDecimal getTodayExpense(NationId nationId) {
        if (eventService == null) {
            return BigDecimal.ZERO;
        }
        return calculateTodayAmount(nationId, false);
    }

    /**
     * 从事件日志计算今日收支
     * @param nationId 国家ID
     * @param isIncome true=计算收入, false=计算支出
     * @return 今日收支总额
     */
    private BigDecimal calculateTodayAmount(NationId nationId, boolean isIncome) {
        if (eventService == null) {
            return BigDecimal.ZERO;
        }
        LocalDate today = LocalDate.now();
        ZoneId zoneId = ZoneId.systemDefault();

        // 收入事件类型: treasury.income, treasury.tax, treasury.reward
        // 支出事件类型: treasury.expense, treasury.loan_repayment
        List<String> targetTypes = isIncome
            ? List.of("treasury.income", "treasury.tax", "treasury.reward")
            : List.of("treasury.expense", "treasury.loan_repayment");

        BigDecimal total = BigDecimal.ZERO;
        for (NationEventRecord record : eventService.eventsOf(nationId)) {
            Instant occurredAt = record.occurredAt();
            LocalDate recordDate = occurredAt.atZone(zoneId).toLocalDate();

            // 只统计今天的事件
            if (!recordDate.equals(today)) {
                continue;
            }

            String type = record.type();
            if (targetTypes.contains(type)) {
                BigDecimal amount = parseAmountFromContext(record.context());
                total = total.add(amount);
            }
        }
        return total.setScale(SCALE, RoundingMode.DOWN);
    }

    /**
     * 从事件上下文字符串中解析金额
     * 上下文格式: actor=...;amount=123.45;balance=...
     */
    private BigDecimal parseAmountFromContext(String context) {
        if (context == null || context.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            Matcher matcher = AMOUNT_PATTERN.matcher(context);
            if (matcher.find()) {
                return new BigDecimal(matcher.group(1)).setScale(SCALE, RoundingMode.DOWN);
            }
        } catch (NumberFormatException ignored) {
        }
        return BigDecimal.ZERO;
    }

    private static BigDecimal normalize(BigDecimal amount) {
        return amount.setScale(SCALE, RoundingMode.DOWN);
    }

    private static void requirePositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
    }

    // ==================== 经济趋势分析 Getter ====================

    /**
     * 获取经济趋势分析服务
     */
    public EconomyTrendService getEconomyTrendService() {
        return economyTrendService;
    }

    /**
     * 获取经济趋势分析 GUI
     */
    public EconomyTrendGui getEconomyTrendGui() {
        return economyTrendGui;
    }
}
