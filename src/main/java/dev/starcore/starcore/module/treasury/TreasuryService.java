package dev.starcore.starcore.module.treasury;

import dev.starcore.starcore.module.nation.model.NationId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import dev.starcore.starcore.module.treasury.BankruptcyService.BankruptcyReason;

/**
 * 财政服务接口
 * 整合国库、税收、贷款、破产、预算的统一财政管理系统
 */
public interface TreasuryService {

    // ==================== 基础操作 (原 TreasuryModule) ====================

    /**
     * 获取国家国库余额
     */
    BigDecimal balance(NationId nationId);

    /**
     * 存入国库
     */
    void deposit(NationId nationId, BigDecimal amount);

    /**
     * 存入国库（带描述）
     */
    default void deposit(NationId nationId, BigDecimal amount, String description) {
        deposit(nationId, amount);
    }

    /**
     * 从国库取出（仅当余额充足时）
     * @return 是否成功
     */
    boolean withdraw(NationId nationId, BigDecimal amount);

    /**
     * 获取服务摘要
     */
    String summary();

    // ==================== 税收操作 (TaxationService) ====================

    /**
     * 获取国家所有税种配置
     */
    Map<TaxationService.TaxType, TaxationService.TaxConfig> getTaxConfigs(NationId nationId);

    /**
     * 获取特定税种配置
     */
    TaxationService.TaxConfig getTaxConfig(NationId nationId, TaxationService.TaxType type);

    /**
     * 设置税种配置
     */
    void setTaxConfig(NationId nationId, TaxationService.TaxType type, TaxationService.TaxConfig config);

    /**
     * 征收指定税种
     */
    BigDecimal collectTax(NationId nationId, TaxationService.TaxType type, TaxationService.TaxContext context);

    /**
     * 征收所有启用税种
     */
    Map<TaxationService.TaxType, BigDecimal> collectAllTaxes(NationId nationId, TaxationService.TaxContext context);

    /**
     * 获取税收历史
     */
    List<TaxationService.TaxRevenue> getTaxHistory(NationId nationId, TaxationService.TaxType type, int limit);

    // ==================== 贷款/国债操作 (LoanService) ====================

    /**
     * 申请贷款
     */
    LoanService.NationDebt applyForLoan(
        NationId nationId,
        NationId creditorId,
        BigDecimal principal,
        BigDecimal annualInterestRate,
        int totalInstallments
    );

    /**
     * 偿还债务
     */
    boolean repayDebt(UUID debtId, BigDecimal amount);

    /**
     * 偿还分期
     */
    boolean payInstallment(UUID debtId);

    /**
     * 免除债务
     */
    boolean forgiveDebt(UUID debtId);

    /**
     * 获取债务记录
     */
    Optional<LoanService.NationDebt> getDebt(UUID debtId);

    /**
     * 获取国家所有债务
     */
    List<LoanService.NationDebt> getDebtsByNation(NationId nationId);

    /**
     * 获取国家有效债务
     */
    List<LoanService.NationDebt> getActiveDebts(NationId nationId);

    /**
     * 获取国家总债务
     */
    BigDecimal getTotalDebt(NationId nationId);

    /**
     * 检查是否可以借款
     */
    boolean canBorrow(NationId nationId, BigDecimal amount);

    /**
     * 获取最大可借额度
     */
    BigDecimal getMaxBorrowableAmount(NationId nationId);

    /**
     * 获取债务健康度（0-100）
     */
    int getDebtHealthScore(NationId nationId);

    // ==================== 破产操作 (BankruptcyService) ====================

    /**
     * 进入破产状态（推荐使用带显式原因的版本）
     * @deprecated Use {@link #enterBankruptcy(NationId, BankruptcyService.BankruptcyReason, String)} instead
     */
    @Deprecated
    void enterBankruptcy(NationId nationId, String reason);

    /**
     * 进入破产状态
     */
    void enterBankruptcy(NationId nationId, BankruptcyService.BankruptcyReason reason, String description);

    /**
     * 退出破产状态
     */
    boolean exitBankruptcy(NationId nationId);

    /**
     * 检查破产状态
     */
    boolean isBankrupt(NationId nationId);

    /**
     * 获取破产记录
     */
    Optional<BankruptcyService.BankruptcyRecord> getBankruptcyRecord(NationId nationId);

    /**
     * 获取破产天数
     */
    long getBankruptcyDays(NationId nationId);

    /**
     * 检查功能是否受限
     */
    boolean isRestricted(NationId nationId, BankruptcyService.Restriction restriction);

    /**
     * 获取所有受限功能
     */
    List<BankruptcyService.Restriction> getActiveRestrictions(NationId nationId);

    /**
     * 提前解除破产
     */
    boolean earlyExitBankruptcy(NationId nationId);

    /**
     * 获取所有破产国家
     */
    List<NationId> getAllBankruptNations();

    // ==================== 预算操作 (BudgetService) ====================

    /**
     * 创建预算草案
     */
    BudgetService.BudgetBill createBudgetDraft(NationId nationId, int year, int month);

    /**
     * 添加预算条目
     */
    void addBudgetEntry(UUID billId, BudgetService.BudgetCategory category, String description, BigDecimal amount);

    /**
     * 提交预算
     */
    BudgetService.BudgetBill submitBudget(UUID billId);

    /**
     * 执行预算
     */
    boolean executeBudget(UUID billId);

    /**
     * 取消预算
     */
    boolean cancelBudget(UUID billId);

    /**
     * 获取当前预算
     */
    Optional<BudgetService.BudgetBill> getCurrentBudget(NationId nationId);

    /**
     * 获取预算历史
     */
    List<BudgetService.BudgetBill> getBudgetHistory(NationId nationId, int limit);

    /**
     * 获取预算执行报告
     */
    BudgetService.BudgetExecutionReport getBudgetExecutionReport(UUID billId);

    /**
     * 获取月度支出
     */
    BigDecimal getMonthlySpending(NationId nationId, int year, int month);

    /**
     * 获取年度支出
     */
    BigDecimal getYearlySpending(NationId nationId, int year);

    // ==================== 综合状态查询 ====================

    /**
     * 财政健康报告
     */
    record FinanceHealthReport(
        NationId nationId,
        BigDecimal balance,
        BigDecimal totalDebt,
        BigDecimal maxDebtLimit,
        int debtHealthScore,
        boolean isBankrupt,
        List<BankruptcyService.Restriction> activeRestrictions,
        List<TaxationService.TaxType> enabledTaxTypes,
        BigDecimal monthlyIncome,
        BigDecimal monthlyExpense,
        Instant generatedAt
    ) {}

    /**
     * 获取财政健康报告
     */
    FinanceHealthReport getFinanceHealthReport(NationId nationId);

    /**
     * 获取今日收入（从事件日志计算的真实数据）
     * @param nationId 国家ID
     * @return 今日总收入
     */
    BigDecimal getTodayIncome(NationId nationId);

    /**
     * 获取今日支出（从事件日志计算的真实数据）
     * @param nationId 国家ID
     * @return 今日总支出
     */
    BigDecimal getTodayExpense(NationId nationId);
}
