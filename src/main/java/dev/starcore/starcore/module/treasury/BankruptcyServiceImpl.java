package dev.starcore.starcore.module.treasury;
import java.util.Optional;

import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.database.DatabaseService;
import dev.starcore.starcore.core.module.ModuleLayer;
import dev.starcore.starcore.core.module.ModuleMetadata;
import dev.starcore.starcore.core.module.StarCoreModule;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.event.EventService;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationMember;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.resolution.ResolutionService;
import dev.starcore.starcore.module.resolution.model.Resolution;
import dev.starcore.starcore.module.resolution.model.ResolutionKind;
import dev.starcore.starcore.module.treasury.BankruptcyService.*;
import dev.starcore.starcore.module.treasury.LoanService.DebtStatus;
import dev.starcore.starcore.module.treasury.LoanService.NationDebt;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * 破产服务实现
 * 管理国家破产状态和功能限制
 */
public final class BankruptcyServiceImpl implements StarCoreModule, BankruptcyService {
    private static final int SCALE = 2;

    private static final ModuleMetadata METADATA = new ModuleMetadata(
        "bankruptcy",
        "破产管理系统",
        ModuleLayer.MODULE,
        List.of("nation", "treasury", "loan"),
        List.of(BankruptcyService.class),
        "Provides bankruptcy management and functional restrictions."
    );

    // 破产记录: nationId -> BankruptcyRecord
    private final ConcurrentHashMap<NationId, BankruptcyRecord> bankruptcies = new ConcurrentHashMap<>();

    // audit B-041: 之前 isBankrupt() 仅依据 restrictions 是否非空推断，
    // 但 getInitialRestrictions 在 autoRestrictOnBankruptcy=false 时返回 Set.of()，
    // 表示"破产但无限制"，isBankrupt 会误返回 false 让玩家绕过破产保护。
    // 新增显式破产状态字段，按 state 判定而非 restrictions。
    private enum BankruptcyState { ACTIVE, EXITED }
    private final ConcurrentHashMap<NationId, BankruptcyState> bankruptcyStates = new ConcurrentHashMap<>();

    // 破产配置
    private volatile BankruptcyConfig config = BankruptcyConfig.defaults();

    // 默认限制集合
    private static final Set<Restriction> DEFAULT_RESTRICTIONS = Set.of(
        Restriction.CLAIM_NEW_LAND,
        Restriction.DECLARE_WAR,
        Restriction.CREATE_ALLIANCE,
        Restriction.INITIATE_TRADE,
        Restriction.BORROW_MONEY
    );

    // 高压限制（逾期债务过多时）
    private static final Set<Restriction> HIGH_RESTRICTIONS = Set.of(
        Restriction.UPGRADE_BUILDINGS,
        Restriction.USE_ADVANCED_TECH,
        Restriction.RECRUIT_TROOPS,
        Restriction.HOST_EVENTS
    );

    private BukkitTask bankruptcyCheckTask;
    private StarCoreContext currentContext;

    @Override
    public ModuleMetadata metadata() {
        return METADATA;
    }

    @Override
    public void enable(StarCoreContext context) {
        this.currentContext = context;
        loadRecords(context);
        startBankruptcyCheckTask(context);
    }

    @Override
    public void disable(StarCoreContext context) {
        stopBankruptcyCheckTask();
        flushRecords();
        this.currentContext = null;
    }

    // ==================== 破产操作 ====================

    /**
     * @deprecated Use {@link #enterBankruptcy(NationId, BankruptcyReason, String)} instead
     */
    @Deprecated
    @Override
    public void enterBankruptcy(NationId nationId, String description) {
        // 兼容旧调用：自动推断原因
        Objects.requireNonNull(nationId, "nationId");
        BankruptcyReason reason = determineBankruptcyReason(description);
        enterBankruptcyImpl(nationId, reason, description);
    }

    @Override
    public void enterBankruptcy(NationId nationId, BankruptcyReason reason, String description) {
        Objects.requireNonNull(nationId, "nationId");
        Objects.requireNonNull(reason, "reason must not be null, use EXCESSIVE_DEBT as default");
        enterBankruptcyImpl(nationId, reason, description);
    }

    /**
     * 内部实现：进入破产状态
     */
    private void enterBankruptcyImpl(NationId nationId, BankruptcyReason reason, String description) {
        if (isBankrupt(nationId)) {
            // 已破产，更新描述
            BankruptcyRecord current = getBankruptcyRecord(nationId);
            if (current != null) {
                recordBankruptcyEvent(nationId, "extend",
                    "Bankruptcy extended: " + description);
            }
            return;
        }

        // 计算预计结束时间
        Instant estimatedEnd = Instant.now().plus(Duration.ofDays(config.maxBankruptcyDays()));

        // 获取当前限制
        Set<String> restrictions = getInitialRestrictions(nationId);

        BankruptcyRecord record = new BankruptcyRecord(
            nationId,
            reason,
            Instant.now(),
            estimatedEnd,
            description,
            restrictions,
            Set.of()
        );

        bankruptcies.put(nationId, record);
        // audit B-041: 显式标记破产状态为 ACTIVE，无论 restrictions 是否为空（修 autoRestrictOnBankruptcy=false 时
        // restrictions=Set.of() 导致 isBankrupt 误返回 false 的问题）。
        bankruptcyStates.put(nationId, BankruptcyState.ACTIVE);

        // audit B-040: 之前仅在 disable() 时 flushRecords()，期间崩溃破产状态丢失，
        // 而 LoanService 已基于记录触发破产限制；丢失后限制解除。改为变后立即持久化。
        saveRecords();

        // 记录事件
        recordBankruptcyEvent(nationId, "enter",
            "Nation entered bankruptcy: " + description);
        recordBankruptcyEvent(nationId, "enter-restrictions",
            "Restrictions applied: " + String.join(", ", restrictions));

        // 通知国家成员
        notifyNationBankruptcy(nationId, record);
    }

    @Override
    public boolean exitBankruptcy(NationId nationId) {
        if (!isBankrupt(nationId)) {
            return false;
        }

        // 检查是否满足退出条件
        if (!canExitBankruptcy(nationId)) {
            return false;
        }

        BankruptcyRecord record = getBankruptcyRecord(nationId);
        Set<String> liftedRestrictions = new HashSet<>(record.restrictions());

        BankruptcyRecord exitedRecord = new BankruptcyRecord(
            nationId,
            record.reason(),
            record.startedAt(),
            Instant.now(),
            "Normal exit after bankruptcy period",
            Set.of(),  // 所有限制解除
            liftedRestrictions
        );

        bankruptcies.put(nationId, exitedRecord);
        // audit B-041: 标记 EXITED，避免 restrictions=Set.of() 后又被 isBankrupt 通过限制集合推断回 ACTIVE。
        bankruptcyStates.put(nationId, BankruptcyState.EXITED);
        // audit B-040: 退出后立即持久化，避免重启后回到 ACTIVE 状态。
        saveRecords();

        // 记录事件
        recordBankruptcyEvent(nationId, "exit",
            "Nation exited bankruptcy after " + record.daysSinceBankruptcy() + " days");

        // 通知国家成员
        notifyNationRecovery(nationId);

        return true;
    }

    @Override
    public boolean isBankrupt(NationId nationId) {
        // audit B-041: 之前 bankruptcies != null && !restrictions.isEmpty() 推断是否破产，
        // 但 enterBankruptcy 可能存空限制集（autoRestrictOnBankruptcy=false 时），导致破产状态实质未生效。
        // 改为基于显式 bankruptcyStates 判定。
        BankruptcyState state = bankruptcyStates.get(nationId);
        if (state == null) {
            // 兼容旧版本：记录存在但 state 还未设置（升级路径或从持久化加载但 states 未加载）。
            // 保守：只要有破产记录存在且未显式 EXITED 就视为 ACTIVE。liftedRestrictions 非空说明已走过 exit 流程。
            BankruptcyRecord record = bankruptcies.get(nationId);
            if (record == null) {
                return false;
            }
            // liftedRestrictions 非空 → 已走 exitBankruptcy 流程 → 视为 EXITED；否则保守视为 ACTIVE。
            // 长期计划：持久化结构增加 state 字段并加载到 bankruptcyStates。
            return record.liftedRestrictions().isEmpty();
        }
        return state == BankruptcyState.ACTIVE;
    }

    @Override
    public BankruptcyRecord getBankruptcyRecord(NationId nationId) {
        return bankruptcies.get(nationId);
    }

    @Override
    public long getBankruptcyDays(NationId nationId) {
        BankruptcyRecord record = getBankruptcyRecord(nationId);
        if (record == null) {
            return 0;
        }
        return record.daysSinceBankruptcy();
    }

    @Override
    public long getRemainingBankruptcyDays(NationId nationId) {
        BankruptcyRecord record = getBankruptcyRecord(nationId);
        if (record == null) {
            return 0;
        }
        long daysSince = record.daysSinceBankruptcy();
        return Math.max(0, config.maxBankruptcyDays() - daysSince);
    }

    // ==================== 功能限制 ====================

    @Override
    public boolean isRestricted(NationId nationId, Restriction restriction) {
        BankruptcyRecord record = getBankruptcyRecord(nationId);
        if (record == null) {
            return false;
        }
        return record.restrictions().contains(restriction.name());
    }

    @Override
    public Set<Restriction> getActiveRestrictions(NationId nationId) {
        BankruptcyRecord record = getBankruptcyRecord(nationId);
        if (record == null) {
            return Set.of();
        }
        return record.restrictions().stream()
            .map(name -> {
                try {
                    return Restriction.valueOf(name);
                } catch (IllegalArgumentException e) {
                    return null;
                }
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    }

    @Override
    public boolean liftRestriction(NationId nationId, Restriction restriction) {
        if (!isRestricted(nationId, restriction)) {
            return false;
        }

        BankruptcyRecord record = getBankruptcyRecord(nationId);
        Set<String> newRestrictions = new HashSet<>(record.restrictions());
        newRestrictions.remove(restriction.name());

        Set<String> newLifted = new HashSet<>(record.liftedRestrictions());
        newLifted.add(restriction.name());

        BankruptcyRecord updated = new BankruptcyRecord(
            nationId,
            record.reason(),
            record.startedAt(),
            record.estimatedEndAt(),
            record.description(),
            newRestrictions,
            newLifted
        );

        bankruptcies.put(nationId, updated);

        // audit B-040: liftRestriction 后立即持久化，避免重启后变更丢失。
        saveRecords();

        recordBankruptcyEvent(nationId, "restriction-lifted",
            "Restriction lifted: " + restriction.getDisplayName());

        return true;
    }

    @Override
    public boolean earlyExitBankruptcy(NationId nationId) {
        if (!config.allowEarlyExit()) {
            return false;
        }

        if (!isBankrupt(nationId)) {
            return false;
        }

        // 检查是否已还清债务
        LoanService loanService = currentContext == null ? null :
            currentContext.serviceRegistry().find(LoanService.class).orElse(null);

        if (loanService != null) {
            BigDecimal totalDebt = loanService.getTotalDebt(nationId);
            if (totalDebt.signum() > 0) {
                return false; // 还有债务
            }
        }

        return exitBankruptcy(nationId);
    }

    // ==================== 破产保护 ====================

    @Override
    public int getBankruptcyProtectionDays(NationId nationId) {
        if (!isBankrupt(nationId)) {
            return 0;
        }
        return config.maxBankruptcyDays();
    }

    @Override
    public boolean isUnderProtection(NationId nationId) {
        return isBankrupt(nationId);
    }

    @Override
    public List<NationId> getAllBankruptNations() {
        // audit B-041: 基于 bankruptcyStates ACTIVE 判定，而非 restrictions 非 空 推断。
        return bankruptcies.entrySet().stream()
            .filter(e -> {
                BankruptcyState s = bankruptcyStates.get(e.getKey());
                if (s != null) {
                    return s == BankruptcyState.ACTIVE;
                }
                // 兼容推测：与 isBankrupt 同逻辑
                return e.getValue().liftedRestrictions().isEmpty();
            })
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }

    // ==================== 配置 ====================

    @Override
    public BankruptcyConfig getConfig() {
        return config;
    }

    @Override
    public void setConfig(BankruptcyConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    // ==================== 辅助方法 ====================

    private BankruptcyReason determineBankruptcyReason(String description) {
        // audit B-043: 之前靠 description.toLowerCase().contains("overdue/petition/court") 判定原因，
        // 依赖调用方传字符串，跨语言/拼写易误归 EXCESSIVE_DEBT。
        // 最小修复：保持描述启发式但改为更精确的关键词匹配，无匹配时显式记 admin 警告并默认 EXCESSIVE_DEBT。
        // 注：调用方现在应使用 enterBankruptcy(nationId, reason, description) 显式指定原因，
        // 此方法仅作为向后兼容的回退（当调用旧 API 时）。
        if (description == null) {
            currentContext.plugin().getLogger().warning(
                "[Bankruptcy] determineBankruptcyReason got null description, defaulting to EXCESSIVE_DEBT");
            return BankruptcyReason.EXCESSIVE_DEBT;
        }
        String lower = description.toLowerCase();
        // 严格按当前触发源的固定描述匹配
        if (lower.contains("overdue")) {
            return BankruptcyReason.OVERDUE_DEBTS;
        }
        if (lower.contains("petition") || lower.contains("apply for bankruptcy")) {
            return BankruptcyReason.BANKRUPTCY_PETITION;
        }
        if (lower.contains("court") || lower.contains("court-order") || lower.contains("court order")) {
            return BankruptcyReason.COURT_ORDER;
        }
        currentContext.plugin().getLogger().warning(
            "[Bankruptcy] determineBankruptcyReason: no keyword matched in description \""
                + description + "\", defaulting to EXCESSIVE_DEBT");
        return BankruptcyReason.EXCESSIVE_DEBT;
    }

    private Set<String> getInitialRestrictions(NationId nationId) {
        if (!config.autoRestrictOnBankruptcy()) {
            return Set.of();
        }

        // 检查逾期债务比例
        LoanService loanService = currentContext == null ? null :
            currentContext.serviceRegistry().find(LoanService.class).orElse(null);

        Set<String> restrictions = new HashSet<>();

        // 基础限制
        for (Restriction r : DEFAULT_RESTRICTIONS) {
            restrictions.add(r.name());
        }

        // 检查是否需要高压限制
        if (loanService != null) {
            List<NationDebt> overdueDebts = loanService.getOverdueDebts(nationId);
            if (!overdueDebts.isEmpty()) {
                for (Restriction r : HIGH_RESTRICTIONS) {
                    restrictions.add(r.name());
                }
            }
        }

        return restrictions;
    }

    private boolean canExitBankruptcy(NationId nationId) {
        // 检查是否超过破产期限
        long days = getBankruptcyDays(nationId);
        if (days < config.maxBankruptcyDays()) {
            // 未满期限，需要特殊条件
            if (config.requireParliamentApproval()) {
                // 检查议会是否已批准破产退出决议
                if (!isBankruptcyExitApprovedByParliament(nationId)) {
                    return false;
                }
            }
            if (!config.allowEarlyExit()) {
                return false;
            }
        }

        // 检查是否还有逾期债务
        LoanService loanService = currentContext == null ? null :
            currentContext.serviceRegistry().find(LoanService.class).orElse(null);

        if (loanService != null) {
            List<NationDebt> overdueDebts = loanService.getOverdueDebts(nationId);
            if (!overdueDebts.isEmpty()) {
                // 还有逾期债务，需要先处理
                return false;
            }
        }

        return true;
    }

    /**
     * 检查议会是否已批准破产退出
     * 通过检查是否有通过的类型为 BANKRUPTCY_EXIT 的决议
     */
    private boolean isBankruptcyExitApprovedByParliament(NationId nationId) {
        ResolutionService resolutionService = currentContext == null ? null :
            currentContext.serviceRegistry().find(ResolutionService.class).orElse(null);

        if (resolutionService == null) {
            return false;
        }

        // 查找该国家的所有未解决决议
        NationService nationService = currentContext == null ? null :
            currentContext.serviceRegistry().find(NationService.class).orElse(null);

        if (nationService == null || resolutionService == null) {
            return false;
        }

        Optional<dev.starcore.starcore.module.nation.model.Nation> nationOpt = nationService.nationById(nationId);
        if (nationOpt.isEmpty()) {
            return false;
        }

        Collection<Resolution> openResolutions = resolutionService.openResolutions(nationOpt.get());

        // audit B-042 (resolved): 之前仅靠 summary.toLowerCase().contains("bankruptcy") 判定，
        // 任何包含单词 "bankruptcy" 的普通议案都能被误判为破产退出决议通过，绕过议会审批。
        // 最小修复：收紧 summary 匹配为明确短语（如 "exit bankruptcy"）。
        // 最终修复：使用 ResolutionKind.BANKRUPTCY_EXIT 枚举值精确匹配决议类型。
        for (Resolution resolution : openResolutions) {
            ResolutionKind kind = resolution.action().kind();
            if (kind == ResolutionKind.BANKRUPTCY_EXIT
                && resolution.state() == dev.starcore.starcore.module.resolution.model.ResolutionState.ENACTED) {
                return true;
            }
        }

        return false;
    }

    /**
     * 获取 LoanService（供 TreasuryModule 使用）
     */
    public LoanService getLoanService() {
        if (currentContext == null) {
            return null;
        }
        return currentContext.serviceRegistry().find(LoanService.class).orElse(null);
    }

    private void recordBankruptcyEvent(NationId nationId, String action, String details) {
        EventService eventService = currentContext == null ? null :
            currentContext.serviceRegistry().find(EventService.class).orElse(null);
        if (eventService == null) {
            return;
        }

        String message = "Bankruptcy " + action + ": " + details;
        String context = "action=" + action + ";" + details;

        eventService.record(nationId, "treasury.bankruptcy." + action, message, context);
    }

    private void notifyNationBankruptcy(NationId nationId, BankruptcyRecord record) {
        MessageService messages = currentContext == null ? null :
            currentContext.serviceRegistry().find(MessageService.class).orElse(null);
        NationService nationService = currentContext == null ? null :
            currentContext.serviceRegistry().find(NationService.class).orElse(null);

        if (messages == null || nationService == null) {
            return;
        }

        String title = messages.format("nation.bankruptcy.title", record.reason().getDisplayName());
        String subtitle = messages.format("nation.bankruptcy.subtitle", config.maxBankruptcyDays());

        // 向国家所有在线成员发送通知
        nationService.nationById(nationId).ifPresent(nation -> {
            for (NationMember member : nation.members()) {
                Player player = Bukkit.getPlayer(member.playerId());
                if (player != null && player.isOnline()) {
                    // 发送标题通知
                    player.sendTitle(title, subtitle, 10, 70, 20);

                    // 发送聊天消息
                    String chatMessage = messages.format("nation.bankruptcy.chat-message",
                        nation.name(),
                        record.reason().getDisplayName(),
                        record.description() != null ? record.description() : "",
                        config.maxBankruptcyDays());
                    player.sendMessage(chatMessage);
                }
            }
        });

        // 记录日志
        currentContext.plugin().getLogger().info(
            String.format("Nation %s entered bankruptcy: reason=%s, days=%d",
                nationId, record.reason(), config.maxBankruptcyDays()));
    }

    private void notifyNationRecovery(NationId nationId) {
        MessageService messages = currentContext == null ? null :
            currentContext.serviceRegistry().find(MessageService.class).orElse(null);
        NationService nationService = currentContext == null ? null :
            currentContext.serviceRegistry().find(NationService.class).orElse(null);

        if (messages == null || nationService == null) {
            return;
        }

        String title = messages.format("nation.bankruptcy-recovery.title");
        String subtitle = messages.format("nation.bankruptcy-recovery.subtitle");

        // 向国家所有在线成员发送恢复通知
        nationService.nationById(nationId).ifPresent(nation -> {
            for (NationMember member : nation.members()) {
                Player player = Bukkit.getPlayer(member.playerId());
                if (player != null && player.isOnline()) {
                    // 发送标题通知
                    player.sendTitle(title, subtitle, 10, 70, 20);

                    // 发送聊天消息
                    String chatMessage = messages.format("nation.bankruptcy-recovery.chat-message", nation.name());
                    player.sendMessage(chatMessage);
                }
            }
        });

        // 记录日志
        currentContext.plugin().getLogger().info("Nation " + nationId + " exited bankruptcy and recovered.");
    }

    // ==================== 定时检查 ====================

    private void startBankruptcyCheckTask(StarCoreContext context) {
        stopBankruptcyCheckTask();
        // 每天检查一次
        this.bankruptcyCheckTask = Bukkit.getScheduler().runTaskTimer(
            context.plugin(),
            () -> checkBankruptcies(),
            20L * 60 * 60 * 24, // 1天后开始
            20L * 60 * 60 * 24  // 每天执行
        );
    }

    private void stopBankruptcyCheckTask() {
        if (bankruptcyCheckTask != null) {
            bankruptcyCheckTask.cancel();
            bankruptcyCheckTask = null;
        }
    }

    private void checkBankruptcies() {
        for (Map.Entry<NationId, BankruptcyRecord> entry : bankruptcies.entrySet()) {
            NationId nationId = entry.getKey();
            BankruptcyRecord record = entry.getValue();

            // 检查是否超过最大破产期限
            if (record.daysSinceBankruptcy() >= config.maxBankruptcyDays()) {
                // 检查是否满足退出条件
                if (canExitBankruptcy(nationId)) {
                    exitBankruptcy(nationId);
                } else {
                    // 超过期限但无法退出，记录警告
                    currentContext.plugin().getLogger().warning(
                        String.format("Nation bankruptcy period exceeded: nation=%s, days=%d",
                            nationId, record.daysSinceBankruptcy()));
                }
            }

            // 检查债务状态变化
            checkDebtStatusChanges(nationId);
        }
    }

    private void checkDebtStatusChanges(NationId nationId) {
        LoanService loanService = currentContext == null ? null :
            currentContext.serviceRegistry().find(LoanService.class).orElse(null);
        if (loanService == null) {
            return;
        }

        BankruptcyRecord record = getBankruptcyRecord(nationId);
        if (record == null) {
            return;
        }

        List<NationDebt> allDebts = loanService.getDebtsByNation(nationId);

        // 检查是否所有债务都已处理
        boolean hasActiveDebts = allDebts.stream()
            .anyMatch(d -> d.status() == DebtStatus.ACTIVE || d.status() == DebtStatus.OVERDUE);

        if (!hasActiveDebts && isBankrupt(nationId)) {
            // 没有活跃债务，可以考虑解除限制
            // 但仍需等待破产期满或议会批准
        }
    }

    // ==================== 持久化 ====================

    private void loadRecords(StarCoreContext context) {
        DatabaseService dbService = context.databaseService();
        if (!dbService.isRunning()) {
            context.plugin().getLogger().info("Database not running, bankruptcy records will not be loaded from persistence.");
            return;
        }

        dbService.dataSource().ifPresent(dataSource -> {
            String sql = "SELECT property_key, property_value FROM starcore_bankruptcy_state";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {

                int loaded = 0;
                while (rs.next()) {
                    String key = rs.getString("property_key");
                    String value = rs.getString("property_value");

                    try {
                        BankruptcyRecord record = deserializeRecord(value);
                        if (record != null) {
                            // 解析 nationId 从 key
                            NationId nationId = new NationId(UUID.fromString(key));
                            if (nationId != null) {
                                bankruptcies.put(nationId, record);
                                loaded++;
                            }
                        }
                    } catch (Exception e) {
                        context.plugin().getLogger().log(Level.WARNING,
                            "Failed to deserialize bankruptcy record for key: " + key, e);
                    }
                }

                if (loaded > 0) {
                    context.plugin().getLogger().info("Loaded " + loaded + " bankruptcy record(s) from database.");
                }
            } catch (Exception e) {
                context.plugin().getLogger().log(Level.WARNING, "Failed to load bankruptcy records from database", e);
            }
        });
    }

    private void saveRecords() {
        if (currentContext == null) {
            return;
        }

        DatabaseService dbService = currentContext.databaseService();
        if (!dbService.isRunning()) {
            return;
        }

        dbService.dataSource().ifPresent(dataSource -> {
            try (Connection conn = dataSource.getConnection()) {
                // 检测数据库类型
                String productName = conn.getMetaData().getDatabaseProductName();
                boolean isSQLite = "SQLite".equalsIgnoreCase(productName);

                // 使用 upsert 模式保存所有记录
                String upsertSql;
                if (isSQLite) {
                    upsertSql = """
                        INSERT INTO starcore_bankruptcy_state (property_key, property_value)
                        VALUES (?, ?)
                        ON CONFLICT(property_key) DO UPDATE SET property_value = excluded.property_value
                        """;
                } else {
                    upsertSql = """
                        INSERT INTO starcore_bankruptcy_state (property_key, property_value)
                        VALUES (?, ?)
                        ON DUPLICATE KEY UPDATE property_value = VALUES(property_value)
                        """;
                }

                try (PreparedStatement stmt = conn.prepareStatement(upsertSql)) {
                    conn.setAutoCommit(false);

                    for (Map.Entry<NationId, BankruptcyRecord> entry : bankruptcies.entrySet()) {
                        String key = entry.getKey().toString();
                        String value = serializeRecord(entry.getValue());

                        stmt.setString(1, key);
                        stmt.setString(2, value);
                        stmt.addBatch();
                    }

                    stmt.executeBatch();
                    conn.commit();
                }
            } catch (Exception e) {
                currentContext.plugin().getLogger().log(Level.WARNING, "Failed to save bankruptcy records to database", e);
            }
        });
    }

    /**
     * 序列化破产记录为 JSON 字符串
     */
    private String serializeRecord(BankruptcyRecord record) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            sb.append("\"reason\":\"").append(escapeJson(record.reason().name())).append("\",");
            sb.append("\"startedAt\":").append(record.startedAt().toEpochMilli()).append(",");
            sb.append("\"estimatedEndAt\":").append(record.estimatedEndAt().toEpochMilli()).append(",");
            sb.append("\"description\":\"").append(escapeJson(record.description() != null ? record.description() : "")).append("\",");
            sb.append("\"restrictions\":[");
            sb.append(record.restrictions().stream()
                .map(r -> "\"" + escapeJson(r) + "\"")
                .collect(Collectors.joining(",")));
            sb.append("],");
            sb.append("\"liftedRestrictions\":[");
            sb.append(record.liftedRestrictions().stream()
                .map(r -> "\"" + escapeJson(r) + "\"")
                .collect(Collectors.joining(",")));
            sb.append("]");
            sb.append("}");
            return sb.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    /**
     * 从 JSON 字符串反序列化破产记录
     */
    private BankruptcyRecord deserializeRecord(String json) {
        if (json == null || json.isEmpty() || "{}".equals(json)) {
            return null;
        }

        try {
            // 简单的 JSON 解析（避免引入额外依赖）
            String reasonStr = extractJsonValue(json, "reason");
            String startedAtStr = extractJsonValue(json, "startedAt");
            String estimatedEndAtStr = extractJsonValue(json, "estimatedEndAt");
            String description = extractJsonValue(json, "description");
            String restrictionsStr = extractJsonValue(json, "restrictions");
            String liftedStr = extractJsonValue(json, "liftedRestrictions");

            BankruptcyReason reason = BankruptcyReason.valueOf(reasonStr);
            Instant startedAt = Instant.ofEpochMilli(Long.parseLong(startedAtStr));
            Instant estimatedEndAt = Instant.ofEpochMilli(Long.parseLong(estimatedEndAtStr));

            Set<String> restrictions = parseJsonStringArray(restrictionsStr);
            Set<String> liftedRestrictions = parseJsonStringArray(liftedStr);

            return new BankruptcyRecord(
                null, // nationId 从 key 获取，这里传 null
                reason,
                startedAt,
                estimatedEndAt,
                description.isEmpty() ? null : description,
                restrictions,
                liftedRestrictions
            );
        } catch (Exception e) {
            return null;
        }
    }

    private String extractJsonValue(String json, String key) {
        String pattern = "\"" + key + "\":";
        int keyIndex = json.indexOf(pattern);
        if (keyIndex == -1) {
            pattern = "\"" + key + "\"";
            keyIndex = json.indexOf(pattern, keyIndex);
            if (keyIndex == -1) return "";
            keyIndex += pattern.length();
        } else {
            keyIndex += pattern.length();
        }

        // 跳过空白
        while (keyIndex < json.length() && Character.isWhitespace(json.charAt(keyIndex))) {
            keyIndex++;
        }

        if (keyIndex >= json.length()) return "";

        char startChar = json.charAt(keyIndex);
        if (startChar == '"') {
            // 字符串值
            int endIndex = keyIndex + 1;
            while (endIndex < json.length()) {
                if (json.charAt(endIndex) == '"' && json.charAt(endIndex - 1) != '\\') {
                    break;
                }
                endIndex++;
            }
            return json.substring(keyIndex + 1, endIndex);
        } else if (startChar == '[') {
            // 数组值
            int endIndex = keyIndex + 1;
            int bracketCount = 1;
            while (endIndex < json.length() && bracketCount > 0) {
                if (json.charAt(endIndex) == '[') bracketCount++;
                else if (json.charAt(endIndex) == ']') bracketCount--;
                endIndex++;
            }
            return json.substring(keyIndex, endIndex);
        } else {
            // 数字或其他值
            int endIndex = keyIndex;
            while (endIndex < json.length() && !Character.isWhitespace(json.charAt(endIndex)) && json.charAt(endIndex) != ',' && json.charAt(endIndex) != '}') {
                endIndex++;
            }
            return json.substring(keyIndex, endIndex);
        }
    }

    private Set<String> parseJsonStringArray(String arrayStr) {
        if (arrayStr == null || arrayStr.isEmpty() || "[]".equals(arrayStr)) {
            return Set.of();
        }

        Set<String> result = new HashSet<>();
        int i = 0;
        while (i < arrayStr.length()) {
            int start = arrayStr.indexOf('"', i);
            if (start == -1) break;
            int end = start + 1;
            while (end < arrayStr.length()) {
                if (arrayStr.charAt(end) == '"' && arrayStr.charAt(end - 1) != '\\') {
                    break;
                }
                end++;
            }
            if (end > start + 1) {
                result.add(arrayStr.substring(start + 1, end));
            }
            i = end + 1;
        }
        return result;
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                 .replace("\"", "\\\"")
                 .replace("\n", "\\n")
                 .replace("\r", "\\r")
                 .replace("\t", "\\t");
    }

    private void flushRecords() {
        saveRecords();
    }
}
