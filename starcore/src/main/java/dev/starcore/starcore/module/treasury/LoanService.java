package dev.starcore.starcore.module.treasury;

import dev.starcore.starcore.module.nation.model.NationId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 贷款服务接口
 * 管理国债和贷款
 */
public interface LoanService {

    // ==================== 债务记录 ====================

    /**
     * 债务状态
     */
    enum DebtStatus {
        ACTIVE,       // 正常
        OVERDUE,      // 逾期
        PAID,         // 已还清
        FORGIVEN      // 已免除
    }

    /**
     * 债务记录
     */
    record NationDebt(
        UUID debtId,
        NationId nationId,           // 借款国
        NationId creditorId,         // 债权国（可为null表示系统借款）
        String debtType,             // 债务类型：LOAN, REPARATION, TREATY
        BigDecimal principal,         // 本金
        BigDecimal interestRate,     // 利率（年利率，如 0.05 表示 5%）
        BigDecimal remainingAmount,   // 剩余金额
        int totalInstallments,       // 总分期数
        int paidInstallments,        // 已付分期数
        BigDecimal installmentAmount, // 每期金额
        Instant createdAt,           // 创建时间
        Instant dueDate,             // 到期时间
        DebtStatus status            // 状态
    ) {
        public BigDecimal totalAmount() {
            return principal.add(calculateTotalInterest());
        }

        public BigDecimal calculateTotalInterest() {
            return principal.multiply(interestRate).setScale(2, java.math.RoundingMode.DOWN);
        }

        public BigDecimal calculateRemainingInterest() {
            int remainingInstallments = totalInstallments - paidInstallments;
            if (remainingInstallments <= 0) {
                return BigDecimal.ZERO;
            }
            return calculateTotalInterest()
                .multiply(BigDecimal.valueOf(remainingInstallments))
                .divide(BigDecimal.valueOf(totalInstallments), 2, java.math.RoundingMode.DOWN);
        }

        public double progressPercentage() {
            if (totalInstallments <= 0) {
                return 0.0;
            }
            return (double) paidInstallments / totalInstallments * 100.0;
        }

        public boolean isOverdue() {
            return status == DebtStatus.ACTIVE && Instant.now().isAfter(dueDate);
        }
    }

    // ==================== 贷款操作 ====================

    /**
     * 申请贷款
     * @param nationId 借款国
     * @param creditorId 债权国（null表示向系统借款）
     * @param principal 本金
     * @param annualInterestRate 年利率
     * @param totalInstallments 总分期数
     * @return 创建的债务记录
     */
    NationDebt applyForLoan(
        NationId nationId,
        NationId creditorId,
        BigDecimal principal,
        BigDecimal annualInterestRate,
        int totalInstallments
    );

    /**
     * 偿还贷款
     * @param debtId 债务ID
     * @param amount 偿还金额
     * @return 是否成功
     */
    boolean repayDebt(UUID debtId, BigDecimal amount);

    /**
     * 偿还分期
     * @param debtId 债务ID
     * @return 是否成功
     */
    boolean payInstallment(UUID debtId);

    /**
     * 免除债务
     * @param debtId 债务ID
     * @return 是否成功
     */
    boolean forgiveDebt(UUID debtId);

    /**
     * 获取债务记录
     */
    Optional<NationDebt> getDebt(UUID debtId);

    /**
     * 获取国家的所有债务
     */
    List<NationDebt> getDebtsByNation(NationId nationId);

    /**
     * 获取国家的有效债务
     */
    List<NationDebt> getActiveDebts(NationId nationId);

    /**
     * 获取国家的逾期债务
     */
    List<NationDebt> getOverdueDebts(NationId nationId);

    // ==================== 债务统计 ====================

    /**
     * 获取国家总债务
     */
    BigDecimal getTotalDebt(NationId nationId);

    /**
     * 获取国家总债务（含利息）
     */
    BigDecimal getTotalDebtWithInterest(NationId nationId);

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

    // ==================== 债务配置 ====================

    /**
     * 债务配置
     */
    record LoanConfig(
        BigDecimal maxDebtLimit,          // 最大债务上限
        BigDecimal systemInterestRate,     // 系统借款利率
        int maxInstallments,               // 最大分期数
        int defaultInstallments,           // 默认分期数
        int gracePeriodDays,              // 宽限期（天）
        int daysPerInstallment             // 每期天数
    ) {
        public static LoanConfig defaults() {
            return new LoanConfig(
                new BigDecimal("100000.00"),  // 10万
                new BigDecimal("0.05"),      // 5%
                24,                          // 最多24期
                12,                          // 默认12期
                7,                           // 7天宽限期
                30                           // 每期30天
            );
        }
    }

    /**
     * 获取债务配置
     */
    LoanConfig getLoanConfig();

    /**
     * 设置债务配置
     */
    void setLoanConfig(LoanConfig config);
}
