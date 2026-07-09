package dev.starcore.starcore.module.treasury;
import java.util.Optional;

import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.module.ModuleLayer;
import dev.starcore.starcore.core.module.ModuleMetadata;
import dev.starcore.starcore.core.module.StarCoreModule;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.event.EventService;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.treasury.LoanService.*;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * 贷款服务实现
 * 管理国债和贷款
 */
public final class LoanServiceImpl implements StarCoreModule, LoanService {
    private static final int SCALE = 2;

    private static final ModuleMetadata METADATA = new ModuleMetadata(
        "loan",
        "国债贷款系统",
        ModuleLayer.MODULE,
        List.of("nation", "treasury"),
        List.of(LoanService.class),
        "Provides national debt and loan management."
    );

    // 债务记录: debtId -> NationDebt
    private final ConcurrentHashMap<UUID, NationDebt> debts = new ConcurrentHashMap<>();

    // 国家债务索引: nationId -> debtIds
    private final ConcurrentHashMap<NationId, Set<UUID>> nationDebts = new ConcurrentHashMap<>();

    // 债务配置
    private volatile LoanConfig loanConfig = LoanConfig.defaults();

    private BukkitTask overdueCheckTask;
    private StarCoreContext currentContext;

    @Override
    public ModuleMetadata metadata() {
        return METADATA;
    }

    @Override
    public void enable(StarCoreContext context) {
        this.currentContext = context;
        loadDebts(context);
        startOverdueCheckTask(context);
    }

    @Override
    public void disable(StarCoreContext context) {
        stopOverdueCheckTask();
        flushDebts();
        this.currentContext = null;
    }

    // ==================== 贷款操作 ====================

    @Override
    public NationDebt applyForLoan(
        NationId nationId,
        NationId creditorId,
        BigDecimal principal,
        BigDecimal annualInterestRate,
        int totalInstallments
    ) {
        Objects.requireNonNull(nationId, "nationId");
        requirePositive(principal, "principal");
        requirePositive(annualInterestRate, "annualInterestRate");

        // audit B-039: maxInstallments 可能被 admin 配置过大（如百万期），每期都触发转账+持久化。
        // 加硬上限 100，防止误配导致海量分期债务。
        int hardInstallmentCap = 100;

        if (totalInstallments <= 0) {
            totalInstallments = loanConfig.defaultInstallments();
        }
        if (totalInstallments > loanConfig.maxInstallments()) {
            totalInstallments = loanConfig.maxInstallments();
        }
        if (totalInstallments > hardInstallmentCap) {
            totalInstallments = hardInstallmentCap;
            currentContext.plugin().getLogger().warning(
                "[Loan] totalInstallments capped to hard limit " + hardInstallmentCap
                    + " (configured maxInstallments=" + loanConfig.maxInstallments() + ")");
        }

        // 检查是否可以借款
        if (!canBorrow(nationId, principal)) {
            throw new IllegalStateException("Nation cannot borrow this amount");
        }

        // audit B-032: 之前Interest = principal * annualInterestRate 是把"年利率"一次性套用到全部本金、
        // 不分期也不按天数计息，与"分期/逾期"语义不符。最小修复：保持单利但按每期天数换算成期利率，
        // 利息按 "principal * periodRate * totalInstallments" 计算更贴合分期语义。
        // TODO(long-term): 真正的等额本息/等额本金公式，含按日计息与逾期罚息。
        int daysPerInstallment = Math.max(1, loanConfig.daysPerInstallment());
        // 期利率 = 年利率 * 每期天数 / 365
        BigDecimal periodRate = annualInterestRate
            .multiply(BigDecimal.valueOf(daysPerInstallment))
            .divide(BigDecimal.valueOf(365), SCALE + 4, RoundingMode.HALF_UP);
        BigDecimal interest = principal.multiply(periodRate)
            .multiply(BigDecimal.valueOf(totalInstallments))
            .setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal totalAmount = principal.add(interest);

        // audit B-033: installmentAmount = totalAmount / installments 用 CEILING 取整会让
        // sum(installmentAmount * totalInstallments) != totalAmount（最后一期可能少/多还）。
        // 修复：把整数除法余数塞入第一期，其余期使用平摊值。
        BigDecimal baseInstallment = totalAmount
            .divide(BigDecimal.valueOf(totalInstallments), SCALE, RoundingMode.DOWN);
        BigDecimal firstInstallment = baseInstallment;
        BigDecimal remainder = totalAmount.subtract(baseInstallment.multiply(BigDecimal.valueOf(totalInstallments)));
        if (remainder.signum() != 0) {
            firstInstallment = baseInstallment.add(remainder);
        }

        // 计算到期时间
        Instant dueDate = Instant.now().plus(Duration.ofDays(totalInstallmentsToDays(totalInstallments)));

        // 创建债务记录
        NationDebt debt = new NationDebt(
            UUID.randomUUID(),
            nationId,
            creditorId,
            creditorId == null ? "LOAN" : "TREATY",
            principal,
            annualInterestRate,
            totalAmount,
            totalInstallments,
            0,
            firstInstallment, // audit B-033: 首期含余数，确保 sum(installment) == totalAmount
            Instant.now(),
            dueDate,
            DebtStatus.ACTIVE
        );

        // 存储
        debts.put(debt.debtId(), debt);
        nationDebts.computeIfAbsent(nationId, id -> ConcurrentHashMap.newKeySet())
            .add(debt.debtId());

        // 如果是系统借款，直接入账
        if (creditorId == null) {
            TreasuryService treasury = currentContext.serviceRegistry().find(TreasuryService.class).orElse(null);
            if (treasury != null) {
                treasury.deposit(nationId, principal);
            }
        } else {
            // 国与国借贷，从债权国转账
            // audit B-030: 之前 treasury.withdraw(creditorId, principal) 没检查返回值，
            // 债权国国库余额不足时 withdraw 返回 false，但仍继续 deposit 到借款国，等于凭空印发国库资金。
            // 最小修复：withdraw 失败则回滚内存债务记录并抛出异常，避免转账非原子。
            TreasuryService treasury = currentContext.serviceRegistry().find(TreasuryService.class).orElse(null);
            if (treasury != null) {
                if (!treasury.withdraw(creditorId, principal)) {
                    // 回滚内存状态，让债务记录不持久化
                    debts.remove(debt.debtId());
                    nationDebts.getOrDefault(nationId, ConcurrentHashMap.newKeySet()).remove(debt.debtId());
                    currentContext.plugin().getLogger().warning(
                        "[Loan] applyForLoan rejected: creditor nation " + creditorId
                            + " insufficient treasury for principal " + principal);
                    throw new IllegalStateException("Creditor nation has insufficient treasury funds");
                }
                treasury.deposit(nationId, principal);
            }
        }
        // audit B-031: 每次贷款申请后立即持久化债务记录，避免内存状态与国库已变动不一致。
        // 之前仅 disable 时 flushDebts()，期间崩溃则债务丢失但钱已变动。
        saveDebts();

        // 记录事件
        recordLoanEvent(debt, "applied", principal, "Loan application approved");

        return debt;
    }

    @Override
    public boolean repayDebt(UUID debtId, BigDecimal amount) {
        NationDebt debt = debts.get(debtId);
        if (debt == null || debt.status() != DebtStatus.ACTIVE) {
            return false;
        }

        requirePositive(amount, "amount");

        // 检查是否足够还款
        BigDecimal repayAmount = amount.min(debt.remainingAmount());
        if (repayAmount.signum() <= 0) {
            return false;
        }

        // 从借款国国库扣除
        TreasuryService treasury = currentContext.serviceRegistry().find(TreasuryService.class).orElse(null);
        if (treasury == null) {
            return false;
        }

        if (!treasury.withdraw(debt.nationId(), repayAmount)) {
            return false; // 余额不足
        }

        // 转给债权国
        // audit B-034: 顺序为 withdraw→deposit→debts.put，中间崩溃会出现：扣钱已扣但债权国未到账、
        // 记录未更新（下次仍按原额扣款 → 重复扣）。最小修复：deposit 失败/异常时立即把钱退回借款国国库，
        // 并保留债务记录原状（即回滚 withdraw），避免凭空销毁金钱。
        // TODO(long-term): 数据库事务 / 显式"还款中"中间状态。
        if (debt.creditorId() != null) {
            try {
                treasury.deposit(debt.creditorId(), repayAmount);
            } catch (RuntimeException e) {
                // deposit 抛异常也要回滚
                treasury.deposit(debt.nationId(), repayAmount);
                currentContext.plugin().getLogger().warning(
                    "[Loan] repayDebt deposit threw, rolled back withdraw: " + e.getMessage());
                throw e;
            }
        }

        // 更新债务记录
        BigDecimal newRemaining = debt.remainingAmount().subtract(repayAmount);
        int newPaidInstallments = calculatePaidInstallments(debt, repayAmount);

        NationDebt updatedDebt;
        if (newRemaining.signum() <= 0) {
            // 还清了
            updatedDebt = new NationDebt(
                debt.debtId(),
                debt.nationId(),
                debt.creditorId(),
                debt.debtType(),
                debt.principal(),
                debt.interestRate(),
                BigDecimal.ZERO,
                debt.totalInstallments(),
                debt.totalInstallments(),
                debt.installmentAmount(),
                debt.createdAt(),
                debt.dueDate(),
                DebtStatus.PAID
            );
        } else {
            updatedDebt = new NationDebt(
                debt.debtId(),
                debt.nationId(),
                debt.creditorId(),
                debt.debtType(),
                debt.principal(),
                debt.interestRate(),
                newRemaining,
                debt.totalInstallments(),
                newPaidInstallments,
                debt.installmentAmount(),
                debt.createdAt(),
                debt.dueDate(),
                DebtStatus.ACTIVE
            );
        }

        debts.put(debtId, updatedDebt);
        // audit B-031: 还款后立即持久化债务记录，避免内存与国库不一致。
        saveDebts();

        // 记录事件
        recordLoanEvent(updatedDebt, "repaid", repayAmount,
            "Repaid " + repayAmount.toPlainString() + ", remaining: " + newRemaining.toPlainString());

        return true;
    }

    @Override
    public boolean payInstallment(UUID debtId) {
        NationDebt debt = debts.get(debtId);
        if (debt == null || debt.status() != DebtStatus.ACTIVE) {
            return false;
        }

        return repayDebt(debtId, debt.installmentAmount());
    }

    @Override
    public boolean forgiveDebt(UUID debtId) {
        NationDebt debt = debts.get(debtId);
        if (debt == null || debt.status() == DebtStatus.PAID || debt.status() == DebtStatus.FORGIVEN) {
            return false;
        }

        // audit B-037: forgiveDebt 把债务标记为 FORGIVEN 但债权国未收到任何还款、债务人也无需再偿。
        // 财务含义：债权国承受"坏账核销"（已借出的本金无法收回，资金损失留存在债权国一侧）。
        // 不调用 treasury 转账——豁免语义本就是放弃债权、坏账核销，不涉及任何账户扣减。
        // 最小修复：在审计日志中显式记录"坏账核销金额 = remainingAmount"，让债权国 admin 能追溯，
        // 并立即持久化债务状态避免重启后状态丢失。
        BigDecimal forgivenAmount = debt.remainingAmount();

        NationDebt forgivenDebt = new NationDebt(
            debt.debtId(),
            debt.nationId(),
            debt.creditorId(),
            debt.debtType(),
            debt.principal(),
            debt.interestRate(),
            debt.remainingAmount(),
            debt.totalInstallments(),
            debt.paidInstallments(),
            debt.installmentAmount(),
            debt.createdAt(),
            debt.dueDate(),
            DebtStatus.FORGIVEN
        );

        debts.put(debtId, forgivenDebt);
        // audit B-031: 豁免后立即持久化，避免内存状态丢失。
        saveDebts();

        // 记录事件
        recordLoanEvent(forgivenDebt, "forgiven", forgivenAmount,
            "Debt forgiven by creditor (bad-debt write-off, creditor loss=" + forgivenAmount.toPlainString() + ")");

        // 警告债权国一侧 admin：这是一笔坏账核销
        if (currentContext != null) {
            currentContext.plugin().getLogger().warning(
                "[Loan] Debt forgiven: creditor=" + debt.creditorId() + " takes bad-debt write-off of "
                    + forgivenAmount.toPlainString() + " (debtId=" + debt.debtId() + ")");
        }
        return true;
    }

    // ==================== 查询操作 ====================

    @Override
    public Optional<NationDebt> getDebt(UUID debtId) {
        return Optional.ofNullable(debts.get(debtId));
    }

    @Override
    public List<NationDebt> getDebtsByNation(NationId nationId) {
        Set<UUID> debtIds = nationDebts.get(nationId);
        if (debtIds == null) {
            return List.of();
        }

        return debtIds.stream()
            .map(debts::get)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    @Override
    public List<NationDebt> getActiveDebts(NationId nationId) {
        return getDebtsByNation(nationId).stream()
            .filter(d -> d.status() == DebtStatus.ACTIVE)
            .collect(Collectors.toList());
    }

    @Override
    public List<NationDebt> getOverdueDebts(NationId nationId) {
        return getDebtsByNation(nationId).stream()
            .filter(NationDebt::isOverdue)
            .collect(Collectors.toList());
    }

    // ==================== 债务统计 ====================

    @Override
    public BigDecimal getTotalDebt(NationId nationId) {
        return getActiveDebts(nationId).stream()
            .map(NationDebt::remainingAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(SCALE, RoundingMode.DOWN);
    }

    @Override
    public BigDecimal getTotalDebtWithInterest(NationId nationId) {
        return getActiveDebts(nationId).stream()
            .map(d -> d.remainingAmount().add(d.calculateRemainingInterest()))
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(SCALE, RoundingMode.DOWN);
    }

    @Override
    public boolean canBorrow(NationId nationId, BigDecimal amount) {
        if (amount.signum() <= 0) {
            return false;
        }

        BigDecimal totalDebt = getTotalDebt(nationId);
        BigDecimal maxLimit = loanConfig.maxDebtLimit();

        return totalDebt.add(amount).compareTo(maxLimit) <= 0;
    }

    @Override
    public BigDecimal getMaxBorrowableAmount(NationId nationId) {
        // audit B-035: maxDebtLimit 理论上可能被 admin 误配为负数；已有 .max(ZERO) 兜底，
        // 但若 maxDebtLimit 为负则该项始终返回 0（合理：负上限意味着不允许借款）。
        // 增加显式日志，便于 admin 排查误配。
        BigDecimal maxLimit = loanConfig.maxDebtLimit();
        if (maxLimit.signum() < 0) {
            currentContext.plugin().getLogger().warning(
                "[Loan] maxDebtLimit configured negative (" + maxLimit + "), getMaxBorrowableAmount returns 0");
            return BigDecimal.ZERO.setScale(SCALE, RoundingMode.DOWN);
        }
        BigDecimal totalDebt = getTotalDebt(nationId);
        return maxLimit.subtract(totalDebt).max(BigDecimal.ZERO).setScale(SCALE, RoundingMode.DOWN);
    }

    @Override
    public int getDebtHealthScore(NationId nationId) {
        BigDecimal maxLimit = loanConfig.maxDebtLimit();
        // audit B-036: 之前 debtRatio = totalDebt / maxLimit 在 maxLimit==0 时会 NaN。
        // 上方判断 maxLimit.signum() <= 0 时直接 return 100 已覆盖除零，无需额外处理；
        // 但显式注释说明此处保证 maxLimit > 0，避免未来误删 else 分支。
        if (maxLimit.signum() <= 0) {
            return 100;
        }

        BigDecimal totalDebt = getTotalDebt(nationId);
        BigDecimal treasuryBalance = getTreasuryBalance(nationId);

        // 债务占上限比例（50分）— 这里 maxLimit.doubleValue() 严格 > 0，无除零风险
        double debtRatio = totalDebt.doubleValue() / maxLimit.doubleValue();
        int debtScore = Math.max(0, (int) ((1.0 - debtRatio) * 50));

        // 储备金覆盖债务能力（50分）
        double coverageRatio = treasuryBalance.doubleValue() / Math.max(0.01, totalDebt.doubleValue());
        int coverageScore = Math.min(50, (int) (coverageRatio * 50));

        return Math.min(100, debtScore + coverageScore);
    }

    // ==================== 配置操作 ====================

    @Override
    public LoanConfig getLoanConfig() {
        return loanConfig;
    }

    @Override
    public void setLoanConfig(LoanConfig config) {
        this.loanConfig = Objects.requireNonNull(config, "config");
    }

    // ==================== 辅助方法 ====================

    private BigDecimal getTreasuryBalance(NationId nationId) {
        TreasuryService treasury = currentContext.serviceRegistry().find(TreasuryService.class).orElse(null);
        if (treasury == null) {
            return BigDecimal.ZERO;
        }
        return treasury.balance(nationId);
    }

    private int calculatePaidInstallments(NationDebt debt, BigDecimal amount) {
        BigDecimal totalPaid = debt.installmentAmount()
            .multiply(BigDecimal.valueOf(debt.paidInstallments()))
            .add(amount);
        return Math.min(debt.totalInstallments(),
            totalPaid.divideToIntegralValue(debt.installmentAmount()).intValue());
    }

    /**
     * 将期数转换为天数
     *
     * [FIXED] 从 LoanConfig 获取每期天数配置，不再硬编码
     *
     * @param installments 期数
     * @return 总天数
     */
    private int totalInstallmentsToDays(int installments) {
        // 从配置中获取每期天数
        int daysPerInstallment = loanConfig.daysPerInstallment();
        if (daysPerInstallment <= 0) {
            // 配置无效时使用默认值（每期30天）
            daysPerInstallment = 30;
            currentContext.plugin().getLogger().warning(
                "Invalid daysPerInstallment in loan config, using default 30 days");
        }
        return installments * daysPerInstallment;
    }

    private void recordLoanEvent(NationDebt debt, String action, BigDecimal amount, String details) {
        EventService eventService = currentContext == null ? null :
            currentContext.serviceRegistry().find(EventService.class).orElse(null);
        if (eventService == null) {
            return;
        }

        String message = String.format("Debt %s: %s %s (remaining: %s, progress: %.1f%%)",
            action, debt.debtType(), amount.toPlainString(),
            debt.remainingAmount().toPlainString(), debt.progressPercentage());

        String context = String.format("debtId=%s;action=%s;amount=%s;remaining=%s;progress=%.1f;%s",
            debt.debtId(), action, amount.toPlainString(),
            debt.remainingAmount().toPlainString(), debt.progressPercentage(), details);

        eventService.record(debt.nationId(), "treasury.debt." + action, message, context);
    }

    // ==================== 逾期检查 ====================

    private void startOverdueCheckTask(StarCoreContext context) {
        stopOverdueCheckTask();
        // 每小时检查一次
        this.overdueCheckTask = Bukkit.getScheduler().runTaskTimer(
            context.plugin(),
            () -> checkOverdueDebts(),
            20L * 60 * 60, // 1小时后开始
            20L * 60 * 60  // 每小时执行
        );
    }

    private void stopOverdueCheckTask() {
        if (overdueCheckTask != null) {
            overdueCheckTask.cancel();
            overdueCheckTask = null;
        }
    }

    private void checkOverdueDebts() {
        Instant now = Instant.now();
        Duration gracePeriod = Duration.ofDays(loanConfig.gracePeriodDays());

        for (NationDebt debt : debts.values()) {
            if (debt.status() != DebtStatus.ACTIVE) {
                continue;
            }

            if (now.isAfter(debt.dueDate().plus(gracePeriod))) {
                // 逾期超过宽限期
                NationDebt overdueDebt = new NationDebt(
                    debt.debtId(),
                    debt.nationId(),
                    debt.creditorId(),
                    debt.debtType(),
                    debt.principal(),
                    debt.interestRate(),
                    debt.remainingAmount(),
                    debt.totalInstallments(),
                    debt.paidInstallments(),
                    debt.installmentAmount(),
                    debt.createdAt(),
                    debt.dueDate(),
                    DebtStatus.OVERDUE
                );
                debts.put(debt.debtId(), overdueDebt);

                currentContext.plugin().getLogger().warning(
                    String.format("Debt overdue: nation=%s, debtId=%s, amount=%s",
                        debt.nationId(), debt.debtId(), debt.remainingAmount().toPlainString()));

                // 触发破产检查
                checkBankruptcy(debt.nationId());
            }
        }
    }

    private void checkBankruptcy(NationId nationId) {
        // 逾期债务超过一定数量或比例，触发破产
        List<NationDebt> activeDebts = getActiveDebts(nationId);
        List<NationDebt> overdueDebts = getOverdueDebts(nationId);

        if (activeDebts.isEmpty() && overdueDebts.isEmpty()) {
            return;
        }

        BigDecimal overdueAmount = overdueDebts.stream()
            .map(NationDebt::remainingAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalDebt = getTotalDebtWithInterest(nationId);

        // 如果逾期债务超过总债务的50%，触发破产
        if (totalDebt.signum() > 0) {
            // audit B-038: 之前用 overdueAmount.doubleValue() / totalDebt.doubleValue() > 0.5，
            // BigDecimal 大额转 double 会精度丢失。改为用 BigDecimal 比值（overdueAmount * 2 > totalDebt），
            // 等价于 overdueRatio > 0.5 但精确无精度损失。
            boolean bankrupt = overdueAmount.multiply(BigDecimal.valueOf(2)).compareTo(totalDebt) > 0;
            if (bankrupt) {
                triggerBankruptcy(nationId, overdueDebts);
            }
        }
    }

    private void triggerBankruptcy(NationId nationId, List<NationDebt> overdueDebts) {
        BankruptcyService bankruptcyService = currentContext == null ? null :
            currentContext.serviceRegistry().find(BankruptcyService.class).orElse(null);
        if (bankruptcyService != null) {
            bankruptcyService.enterBankruptcy(nationId,
                "Excessive overdue debt: " + overdueDebts.size() + " debts overdue");
        }

        currentContext.plugin().getLogger().warning(
            String.format("Nation entered bankruptcy: nation=%s, overdueDebts=%d",
                nationId, overdueDebts.size()));
    }

    // ==================== 持久化 ====================

    private void loadDebts(StarCoreContext context) {
        try {
            var persistenceService = context.persistenceService();
            var namespace = "treasury";
            var fileName = "loans.properties";

            persistenceService.ensureNamespace(namespace).join();
            java.util.Properties props = persistenceService.loadProperties(namespace, fileName);

            if (props.isEmpty()) {
                return;
            }

            int count = Integer.parseInt(props.getProperty("count", "0"));
            for (int i = 0; i < count; i++) {
                String prefix = "debt." + i + ".";
                try {
                    UUID debtId = UUID.fromString(props.getProperty(prefix + "debtId"));
                    NationId nationId = new NationId(UUID.fromString(props.getProperty(prefix + "nationId")));
                    NationId creditorId = null;
                    String creditorIdStr = props.getProperty(prefix + "creditorId");
                    if (creditorIdStr != null && !creditorIdStr.isEmpty()) {
                        creditorId = new NationId(UUID.fromString(creditorIdStr));
                    }
                    String loanType = props.getProperty(prefix + "loanType", "LOAN");
                    BigDecimal principal = new BigDecimal(props.getProperty(prefix + "principal"));
                    BigDecimal annualInterestRate = new BigDecimal(props.getProperty(prefix + "annualInterestRate"));
                    BigDecimal totalAmount = new BigDecimal(props.getProperty(prefix + "totalAmount"));
                    int totalInstallments = Integer.parseInt(props.getProperty(prefix + "totalInstallments"));
                    int paidInstallments = Integer.parseInt(props.getProperty(prefix + "paidInstallments"));
                    BigDecimal installmentAmount = new BigDecimal(props.getProperty(prefix + "installmentAmount"));
                    Instant startDate = Instant.ofEpochMilli(Long.parseLong(props.getProperty(prefix + "startDate")));
                    Instant dueDate = Instant.ofEpochMilli(Long.parseLong(props.getProperty(prefix + "dueDate")));
                    DebtStatus status = DebtStatus.valueOf(props.getProperty(prefix + "status", "ACTIVE"));

                    NationDebt debt = new NationDebt(
                        debtId, nationId, creditorId, loanType, principal, annualInterestRate,
                        totalAmount, totalInstallments, paidInstallments, installmentAmount,
                        startDate, dueDate, status
                    );
                    debts.put(debtId, debt);
                    nationDebts.computeIfAbsent(nationId, id -> ConcurrentHashMap.newKeySet())
                        .add(debtId);

                } catch (Exception e) {
                    context.plugin().getLogger().warning("Failed to load debt #" + i + ": " + e.getMessage());
                }
            }
            context.plugin().getLogger().info("Loaded " + count + " loan records");

        } catch (Exception e) {
            context.plugin().getLogger().warning("Failed to load debts: " + e.getMessage());
        }
    }

    private void saveDebts() {
        try {
            var persistenceService = currentContext.persistenceService();
            var namespace = "treasury";
            var fileName = "loans.properties";

            java.util.Properties props = new java.util.Properties();
            List<NationDebt> debtList = new ArrayList<>(debts.values());
            props.setProperty("count", String.valueOf(debtList.size()));

            for (int i = 0; i < debtList.size(); i++) {
                NationDebt debt = debtList.get(i);
                String prefix = "debt." + i + ".";
                props.setProperty(prefix + "debtId", debt.debtId().toString());
                props.setProperty(prefix + "nationId", debt.nationId().toString());
                props.setProperty(prefix + "creditorId", debt.creditorId() != null ? debt.creditorId().toString() : "");
                props.setProperty(prefix + "loanType", debt.debtType());
                props.setProperty(prefix + "principal", debt.principal().toString());
                props.setProperty(prefix + "annualInterestRate", debt.interestRate().toString());
                props.setProperty(prefix + "totalAmount", debt.totalAmount().toString());
                props.setProperty(prefix + "totalInstallments", String.valueOf(debt.totalInstallments()));
                props.setProperty(prefix + "paidInstallments", String.valueOf(debt.paidInstallments()));
                props.setProperty(prefix + "installmentAmount", debt.installmentAmount().toString());
                props.setProperty(prefix + "startDate", String.valueOf(debt.createdAt().toEpochMilli()));
                props.setProperty(prefix + "dueDate", String.valueOf(debt.dueDate().toEpochMilli()));
                props.setProperty(prefix + "status", debt.status().name());
            }

            persistenceService.saveProperties(namespace, fileName, props);

        } catch (Exception e) {
            currentContext.plugin().getLogger().warning("Failed to save debts: " + e.getMessage());
        }
    }

    private void flushDebts() {
        saveDebts();
    }

    // ==================== 工具方法 ====================

    private static void requirePositive(BigDecimal value, String name) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
