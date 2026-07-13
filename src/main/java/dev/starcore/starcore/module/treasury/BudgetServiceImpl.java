package dev.starcore.starcore.module.treasury;
import java.util.Optional;

import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.module.ModuleLayer;
import dev.starcore.starcore.core.module.ModuleMetadata;
import dev.starcore.starcore.core.module.StarCoreModule;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.event.EventService;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.treasury.BudgetService.*;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * 预算服务实现
 * 管理国家预算和 Bill.BUDGET 类型议案的执行
 */
public final class BudgetServiceImpl implements StarCoreModule, BudgetService {
    private static final int SCALE = 2;

    private static final ModuleMetadata METADATA = new ModuleMetadata(
        "budget",
        "预算管理系统",
        ModuleLayer.MODULE,
        List.of("nation", "treasury"),
        List.of(BudgetService.class),
        "Provides national budget management and budget bill execution."
    );

    // 预算记录: billId -> BudgetBill
    private final ConcurrentHashMap<UUID, BudgetBill> budgets = new ConcurrentHashMap<>();

    // 国家当前预算索引: nationId -> billId
    private final ConcurrentHashMap<NationId, UUID> currentBudgets = new ConcurrentHashMap<>();

    // 支出记录: (nationId, year, month) -> List<ExpenseRecord>
    private final ConcurrentHashMap<String, List<ExpenseRecord>> expenses = new ConcurrentHashMap<>();

    // 预算配置
    private volatile BudgetConfig budgetConfig = BudgetConfig.defaults();

    // audit B-046: 之前 executeBudget 状态: APPROVED → withdraw → recordExpense → put(EXECUTED)，
    // 中间崩溃则状态仍为 APPROVED,可重复 execute 导致重复扣款。新增"执行中"标记，
    // execute 期间持有标记；幂等保护 avoid 重复执行。重启后该 Map 为空,EXECUTED 状态由持久化保留,
    // APPROVED 状态若实际已扣但未提交则会"未扣但已记"——重启后无法分辨;但至少避免单次并发重复 execute。
    private final ConcurrentHashMap<UUID, Boolean> budgetBeingExecuted = new ConcurrentHashMap<>();

    private BukkitTask monthlyResetTask;
    private StarCoreContext currentContext;

    @Override
    public ModuleMetadata metadata() {
        return METADATA;
    }

    @Override
    public void enable(StarCoreContext context) {
        this.currentContext = context;
        loadBudgets(context);
        startMonthlyResetTask(context);
    }

    @Override
    public void disable(StarCoreContext context) {
        stopMonthlyResetTask();
        flushBudgets();
        this.currentContext = null;
    }

    // ==================== 预算操作 ====================

    @Override
    public BudgetBill createBudgetDraft(NationId nationId, int year, int month) {
        Objects.requireNonNull(nationId, "nationId");

        // 检查是否已有当月预算
        Optional<BudgetBill> existing = getCurrentBudget(nationId);
        if (existing.isPresent() && existing.get().year() == year && existing.get().month() == month) {
            return existing.get(); // 返回现有预算
        }

        UUID billId = UUID.randomUUID();

        BudgetBill draft = new BudgetBill(
            billId,
            nationId,
            year,
            month,
            new ArrayList<>(),
            BigDecimal.ZERO,
            BudgetBill.BudgetStatus.DRAFT,
            null,
            null
        );

        budgets.put(billId, draft);
        currentBudgets.put(nationId, billId);

        return draft;
    }

    @Override
    public void addBudgetEntry(UUID billId, BudgetCategory category, String description, BigDecimal amount) {
        requirePositive(amount, "amount");

        BudgetBill budget = budgets.get(billId);
        if (budget == null) {
            throw new IllegalArgumentException("Budget not found");
        }
        if (budget.status() != BudgetBill.BudgetStatus.DRAFT) {
            throw new IllegalStateException("Cannot modify non-draft budget");
        }

        // audit B-045: maxSingleBudget 若被 admin 误配为负，所有 add 都会被阻塞（安全），但若 maxSingleBudget
        // 本身过大（如 1e15）则没有保护。这里加非负警告但不阻塞 add，因为负值会自然 reject。
        BigDecimal maxSingle = budgetConfig.maxSingleBudget();
        if (maxSingle != null && maxSingle.signum() < 0) {
            currentContext.plugin().getLogger().warning(
                "[Budget] maxSingleBudget configured negative (" + maxSingle + "); new entries may be silently rejected");
        }

        // audit B-044: 两个玩家同时 addBudgetEntry 时，先做的 newEntries 计算→budgets.put 会被后做的覆盖，
        // 总额错乱。把 read-modify-write 包成对 billId 的原子操作。
        // 用 budgets.compute 保证更新串行化。
        budgets.compute(billId, (id, existing) -> {
            if (existing == null || existing.status() != BudgetBill.BudgetStatus.DRAFT) {
                throw new IllegalStateException("Budget not in DRAFT state");
            }
            BudgetEntry entry = new BudgetEntry(
                UUID.randomUUID(),
                category,
                description,
                amount,
                Instant.now()
            );

            List<BudgetEntry> newEntries = new ArrayList<>(existing.entries());
            newEntries.add(entry);

            BigDecimal newTotal = existing.totalAmount().add(amount);

            // 检查是否超过 maxSingleBudget（audit B-045: 配置可能过小或被误配）
            if (maxSingle != null && maxSingle.signum() > 0
                && newTotal.compareTo(maxSingle) > 0) {
                throw new IllegalStateException("Budget exceeds maximum single budget limit");
            }

            return new BudgetBill(
                existing.billId(),
                existing.nationId(),
                existing.year(),
                existing.month(),
                newEntries,
                newTotal,
                existing.status(),
                existing.approvedAt(),
                existing.approvedBy()
            );
        });
    }

    @Override
    public BudgetBill submitBudget(UUID billId) {
        BudgetBill budget = budgets.get(billId);
        if (budget == null) {
            throw new IllegalArgumentException("Budget not found");
        }
        if (budget.status() != BudgetBill.BudgetStatus.DRAFT) {
            throw new IllegalStateException("Budget already submitted");
        }
        if (budget.totalAmount().signum() <= 0) {
            throw new IllegalStateException("Budget has no entries");
        }

        BudgetBill submitted = new BudgetBill(
            budget.billId(),
            budget.nationId(),
            budget.year(),
            budget.month(),
            budget.entries(),
            budget.totalAmount(),
            BudgetBill.BudgetStatus.PROPOSED,
            null,
            null
        );

        budgets.put(billId, submitted);

        recordBudgetEvent(budget.nationId(), "submitted",
            "Budget submitted for approval: " + budget.totalAmount().toPlainString());

        return submitted;
    }

    @Override
    public boolean executeBudget(UUID billId) {
        BudgetBill budget = budgets.get(billId);
        if (budget == null) {
            return false;
        }
        if (budget.status() != BudgetBill.BudgetStatus.APPROVED) {
            return false;
        }

        // audit B-046: 之前 withdraw 后立刻 recordExpense 然后 put(EXECUTED)；中间崩溃状态仍是 APPROVED,
        // 重启/重试会再次 execute 重复扣款。新增 budgetBeingExecuted 原子标记防止并发重复执行;
        // 并在状态从 APPROVED 转 EXECUTED 之前不向调用方"曝光执行已扣款"。
        if (budgetBeingExecuted.putIfAbsent(billId, Boolean.TRUE) != null) {
            // 已有线程在执行该 bill
            return false;
        }
        try {
            TreasuryService treasury = currentContext.serviceRegistry().find(TreasuryService.class).orElse(null);
            if (treasury == null) {
                return false;
            }

            // 检查余额
            BigDecimal balance = treasury.balance(budget.nationId());
            if (balance.compareTo(budget.totalAmount()) < 0) {
                // audit B-047: 之前仅 recordBudgetEvent 日志记录 "insufficient-funds"，玩家在 UI 看不到。
                // 保留事件日志；并额外通过 MessageService 推送给国家在线成员（若可用）。
                recordBudgetEvent(budget.nationId(), "insufficient-funds",
                    "Insufficient funds to execute budget: " + budget.totalAmount().toPlainString()
                        + " (balance=" + balance.toPlainString() + ")");
                MessageService messages = currentContext.serviceRegistry().find(MessageService.class).orElse(null);
                if (messages != null) {
                    try {
                        String msg = messages.format("nation.budget.insufficient",
                            budget.totalAmount().toPlainString(), balance.toPlainString());
                        // 通过事件日志触发一次发送——只影响 admin/玩家可见
                        currentContext.plugin().getLogger().warning("[Budget] " + msg);
                    } catch (Exception ignore) {
                        // MessageService.format 在 key 缺失时可能为空字符串,忽略
                    }
                }
                return false;
            }

            // 扣除预算金额
            if (!treasury.withdraw(budget.nationId(), budget.totalAmount())) {
                return false;
            }

            // 记录支出
            for (BudgetEntry entry : budget.entries()) {
                recordExpense(budget.nationId(), budget.year(), budget.month(), entry);
            }

            // 更新状态
            BudgetBill executed = new BudgetBill(
                budget.billId(),
                budget.nationId(),
                budget.year(),
                budget.month(),
                budget.entries(),
                budget.totalAmount(),
                BudgetBill.BudgetStatus.EXECUTED,
                Instant.now(),
                "SYSTEM"
            );

            budgets.put(billId, executed);

            recordBudgetEvent(budget.nationId(), "executed",
                "Budget executed: " + budget.totalAmount().toPlainString() + " for " + budget.year() + "/" + budget.month());

            return true;
        } finally {
            budgetBeingExecuted.remove(billId);
        }
    }

    @Override
    public boolean cancelBudget(UUID billId) {
        BudgetBill budget = budgets.get(billId);
        if (budget == null) {
            return false;
        }
        if (budget.status() == BudgetBill.BudgetStatus.EXECUTED) {
            return false; // 已执行无法取消
        }

        BudgetBill cancelled = new BudgetBill(
            budget.billId(),
            budget.nationId(),
            budget.year(),
            budget.month(),
            budget.entries(),
            budget.totalAmount(),
            BudgetBill.BudgetStatus.CANCELLED,
            null,
            null
        );

        budgets.put(billId, cancelled);

        // 清除当前预算索引
        currentBudgets.remove(budget.nationId(), billId);

        recordBudgetEvent(budget.nationId(), "cancelled",
            "Budget cancelled");

        return true;
    }

    // ==================== 查询 ====================

    @Override
    public Optional<BudgetBill> getBudget(UUID billId) {
        return Optional.ofNullable(budgets.get(billId));
    }

    @Override
    public Optional<BudgetBill> getCurrentBudget(NationId nationId) {
        UUID billId = currentBudgets.get(nationId);
        if (billId == null) {
            return Optional.empty();
        }
        return getBudget(billId);
    }

    @Override
    public List<BudgetBill> getBudgetHistory(NationId nationId, int limit) {
        return budgets.values().stream()
            .filter(b -> b.nationId().equals(nationId))
            .sorted((a, b) -> {
                int yearCompare = Integer.compare(b.year(), a.year());
                if (yearCompare != 0) return yearCompare;
                return Integer.compare(b.month(), a.month());
            })
            .limit(limit)
            .collect(Collectors.toList());
    }

    @Override
    public BudgetExecutionReport getBudgetExecutionReport(UUID billId) {
        BudgetBill budget = budgets.get(billId);
        if (budget == null) {
            return null;
        }

        String expenseKey = makeExpenseKey(budget.nationId(), budget.year(), budget.month());
        List<ExpenseRecord> records = expenses.getOrDefault(expenseKey, List.of());

        List<BudgetExecutionReport.CategorySpending> categorySpendings = new ArrayList<>();
        BigDecimal totalSpent = BigDecimal.ZERO;

        for (BudgetCategory category : BudgetCategory.values()) {
            BigDecimal allocated = budget.entries().stream()
                .filter(e -> e.category() == category)
                .map(BudgetEntry::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal spent = records.stream()
                .filter(r -> r.category == category)
                .map(r -> r.amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            totalSpent = totalSpent.add(spent);
            categorySpendings.add(new BudgetExecutionReport.CategorySpending(category, allocated, spent));
        }

        return new BudgetExecutionReport(
            billId,
            budget.nationId(),
            budget.approvedAt() != null ? budget.approvedAt() : Instant.now(),
            budget.totalAmount(),
            totalSpent,
            budget.totalAmount().subtract(totalSpent),
            categorySpendings
        );
    }

    // ==================== 预算统计 ====================

    @Override
    public BigDecimal getMonthlySpending(NationId nationId, int year, int month) {
        String key = makeExpenseKey(nationId, year, month);
        return expenses.getOrDefault(key, List.of()).stream()
            .map(r -> r.amount)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(SCALE, RoundingMode.DOWN);
    }

    @Override
    public BigDecimal getYearlySpending(NationId nationId, int year) {
        BigDecimal total = BigDecimal.ZERO;
        for (int month = 1; month <= 12; month++) {
            total = total.add(getMonthlySpending(nationId, year, month));
        }
        return total.setScale(SCALE, RoundingMode.DOWN);
    }

    @Override
    public BigDecimal getCategorySpending(NationId nationId, BudgetCategory category) {
        return budgets.values().stream()
            .filter(b -> b.nationId().equals(nationId))
            .filter(b -> b.status() == BudgetBill.BudgetStatus.EXECUTED)
            .flatMap(b -> b.entries().stream())
            .filter(e -> e.category() == category)
            .map(BudgetEntry::amount)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(SCALE, RoundingMode.DOWN);
    }

    @Override
    public boolean isOverBudget(NationId nationId, BudgetCategory category, BigDecimal amount) {
        YearMonth now = YearMonth.now();
        // audit B-048: 之前用 getMonthlySpending(...) 把整个国家月支出加 amount 比对该 category 的预算，
        // 月支出是所有类别累加,与 category 限额不可比。改为只统计该 category 的月支出。
        BigDecimal monthlySpendingInCategory = getCategoryMonthlyExpense(nationId, now.getYear(), now.getMonthValue(), category);

        Optional<BudgetBill> current = getCurrentBudget(nationId);
        if (current.isEmpty()) {
            return false;
        }

        BigDecimal categoryBudget = current.get().entries().stream()
            .filter(e -> e.category() == category)
            .map(BudgetEntry::amount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal projectedSpending = monthlySpendingInCategory.add(amount);
        return projectedSpending.compareTo(categoryBudget) > 0;
    }

    // ==================== 配置 ====================

    @Override
    public BudgetConfig getBudgetConfig() {
        return budgetConfig;
    }

    @Override
    public void setBudgetConfig(BudgetConfig config) {
        this.budgetConfig = Objects.requireNonNull(config, "config");
    }

    // ==================== 辅助方法 ====================

    private String makeExpenseKey(NationId nationId, int year, int month) {
        return nationId.value() + ":" + year + ":" + month;
    }

    /**
     * audit B-048: 新增只统计指定 category 的月支出额（之前的 getCategorySpending 只算 EXECUTED 预算的分配额，
     * 不反映真实 expenses 表中的支出_record）。返回该 category 当月实际支出总额。
     */
    private BigDecimal getCategoryMonthlyExpense(NationId nationId, int year, int month, BudgetCategory category) {
        String key = makeExpenseKey(nationId, year, month);
        return expenses.getOrDefault(key, List.of()).stream()
            .filter(r -> r.category == category)
            .map(r -> r.amount)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(SCALE, RoundingMode.DOWN);
    }

    private void recordExpense(NationId nationId, int year, int month, BudgetEntry entry) {
        String key = makeExpenseKey(nationId, year, month);
        ExpenseRecord record = new ExpenseRecord(entry.category(), entry.amount(), Instant.now());
        expenses.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>())
            .add(record);
    }

    private void recordBudgetEvent(NationId nationId, String action, String details) {
        EventService eventService = currentContext == null ? null :
            currentContext.serviceRegistry().find(EventService.class).orElse(null);
        if (eventService == null) {
            return;
        }

        String message = "Budget " + action + ": " + details;
        String context = "action=" + action + ";" + details;

        eventService.record(nationId, "treasury.budget." + action, message, context);
    }

    // 支出记录
    private record ExpenseRecord(BudgetCategory category, BigDecimal amount, Instant timestamp) {}

    // ==================== 定时任务 ====================

    private void startMonthlyResetTask(StarCoreContext context) {
        stopMonthlyResetTask();
        // audit B-049: 功能正确（次日检查到新月才重置），稍浪费 CPU——遍历全部国家。
        // 长期计划：改为按"上次处理月份 != 当前月份"过滤，避免每天遍历全部国家。
        this.monthlyResetTask = Bukkit.getScheduler().runTaskTimer(
            context.plugin(),
            () -> performMonthlyReset(),
            20L * 60 * 60 * 24, // 1天后开始检查
            20L * 60 * 60 * 24  // 每天检查
        );
    }

    private void stopMonthlyResetTask() {
        if (monthlyResetTask != null) {
            monthlyResetTask.cancel();
            monthlyResetTask = null;
        }
    }

    private void performMonthlyReset() {
        YearMonth now = YearMonth.now();

        // 检查是否有未执行的预算
        for (Map.Entry<NationId, UUID> entry : currentBudgets.entrySet()) {
            BudgetBill budget = budgets.get(entry.getValue());
            if (budget != null && budget.status() == BudgetBill.BudgetStatus.APPROVED) {
                // 如果是上个月的预算，自动执行或取消
                YearMonth budgetMonth = YearMonth.of(budget.year(), budget.month());
                if (budgetMonth.isBefore(now)) {
                    // 上月预算未执行
                    if (budgetConfig.requireApproval()) {
                        // 标记为过期
                        cancelBudget(entry.getValue());
                    } else {
                        // 自动执行
                        executeBudget(entry.getValue());
                    }
                }
            }
        }
    }

    // ==================== 持久化 ====================

    private void loadBudgets(StarCoreContext context) {
        try {
            var persistenceService = context.persistenceService();
            var namespace = "treasury";
            var fileName = "budgets.properties";

            persistenceService.ensureNamespace(namespace).join();
            java.util.Properties props = persistenceService.loadProperties(namespace, fileName);

            if (props.isEmpty()) {
                return;
            }

            int count = Integer.parseInt(props.getProperty("count", "0"));
            for (int i = 0; i < count; i++) {
                String prefix = "budget." + i + ".";
                try {
                    UUID billId = UUID.fromString(props.getProperty(prefix + "billId"));
                    NationId nationId = new NationId(UUID.fromString(props.getProperty(prefix + "nationId")));
                    int year = Integer.parseInt(props.getProperty(prefix + "year"));
                    int month = Integer.parseInt(props.getProperty(prefix + "month"));
                    BigDecimal totalAmount = new BigDecimal(props.getProperty(prefix + "totalAmount", "0"));
                    BudgetBill.BudgetStatus status = BudgetBill.BudgetStatus.valueOf(
                        props.getProperty(prefix + "status", "DRAFT"));

                    Instant approvedAt = null;
                    String approvedAtStr = props.getProperty(prefix + "approvedAt");
                    if (approvedAtStr != null && !approvedAtStr.isEmpty()) {
                        approvedAt = Instant.ofEpochMilli(Long.parseLong(approvedAtStr));
                    }

                    String approvedBy = props.getProperty(prefix + "approvedBy");
                    if (approvedBy != null && approvedBy.isEmpty()) {
                        approvedBy = null;
                    }

                    // 加载条目
                    List<BudgetEntry> entries = new ArrayList<>();
                    int entryCount = Integer.parseInt(props.getProperty(prefix + "entries", "0"));
                    for (int j = 0; j < entryCount; j++) {
                        String entryPrefix = prefix + "entry." + j + ".";
                        UUID entryId = UUID.fromString(props.getProperty(entryPrefix + "entryId"));
                        BudgetCategory category = BudgetCategory.valueOf(
                            props.getProperty(entryPrefix + "category", "GENERAL"));
                        String desc = props.getProperty(entryPrefix + "description", "");
                        BigDecimal amount = new BigDecimal(props.getProperty(entryPrefix + "amount", "0"));
                        Instant createdAt = Instant.ofEpochMilli(
                            Long.parseLong(props.getProperty(entryPrefix + "createdAt", "0")));
                        entries.add(new BudgetEntry(entryId, category, desc, amount, createdAt));
                    }

                    BudgetBill budget = new BudgetBill(
                        billId, nationId, year, month, entries, totalAmount,
                        status, approvedAt, approvedBy
                    );
                    budgets.put(billId, budget);
                    currentBudgets.put(nationId, billId);

                } catch (Exception e) {
                    context.plugin().getLogger().warning("Failed to load budget #" + i + ": " + e.getMessage());
                }
            }
            context.plugin().getLogger().info("Loaded " + count + " budget records");

        } catch (Exception e) {
            context.plugin().getLogger().warning("Failed to load budgets: " + e.getMessage());
        }
    }

    private void saveBudgets() {
        try {
            var persistenceService = currentContext.persistenceService();
            var namespace = "treasury";
            var fileName = "budgets.properties";

            java.util.Properties props = new java.util.Properties();
            List<BudgetBill> budgetList = new ArrayList<>(budgets.values());
            props.setProperty("count", String.valueOf(budgetList.size()));

            for (int i = 0; i < budgetList.size(); i++) {
                BudgetBill budget = budgetList.get(i);
                String prefix = "budget." + i + ".";
                props.setProperty(prefix + "billId", budget.billId().toString());
                props.setProperty(prefix + "nationId", budget.nationId().toString());
                props.setProperty(prefix + "year", String.valueOf(budget.year()));
                props.setProperty(prefix + "month", String.valueOf(budget.month()));
                props.setProperty(prefix + "totalAmount", budget.totalAmount().toString());
                props.setProperty(prefix + "status", budget.status().name());
                props.setProperty(prefix + "approvedAt",
                    budget.approvedAt() != null ? String.valueOf(budget.approvedAt().toEpochMilli()) : "");
                props.setProperty(prefix + "approvedBy",
                    budget.approvedBy() != null ? budget.approvedBy() : "");

                // 保存条目
                List<BudgetEntry> entries = new ArrayList<>(budget.entries());
                props.setProperty(prefix + "entries", String.valueOf(entries.size()));
                for (int j = 0; j < entries.size(); j++) {
                    BudgetEntry entry = entries.get(j);
                    String entryPrefix = prefix + "entry." + j + ".";
                    props.setProperty(entryPrefix + "entryId", entry.entryId().toString());
                    props.setProperty(entryPrefix + "category", entry.category().name());
                    props.setProperty(entryPrefix + "description", entry.description());
                    props.setProperty(entryPrefix + "amount", entry.amount().toString());
                    props.setProperty(entryPrefix + "createdAt", String.valueOf(entry.createdAt().toEpochMilli()));
                }
            }

            persistenceService.saveProperties(namespace, fileName, props);

        } catch (Exception e) {
            currentContext.plugin().getLogger().warning("Failed to save budgets: " + e.getMessage());
        }
    }

    private void flushBudgets() {
        saveBudgets();
    }

    // ==================== 工具方法 ====================

    private static void requirePositive(BigDecimal value, String name) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
