package dev.starcore.starcore.module.treasury;

import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.treasury.BudgetService.BudgetBill;
import dev.starcore.starcore.module.treasury.BudgetService.BudgetCategory;
import dev.starcore.starcore.module.treasury.BudgetService.BudgetEntry;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 预算服务接口
 * 管理国家预算和 Bill.BUDGET 类型议案的执行
 */
public interface BudgetService {

    // ==================== 预算类别 ====================

    enum BudgetCategory {
        /** 军事支出 */
        MILITARY("military", "军事", "军队、武器、防务"),
        /** 基础设施建设 */
        INFRASTRUCTURE("infrastructure", "基础设施", "道路、建筑、公共设施"),
        /** 科技研发 */
        RESEARCH("research", "科技研发", "技术研究、创新"),
        /** 外交活动 */
        DIPLOMACY("diplomacy", "外交", "外交活动、国际关系"),
        /** 社会福利 */
        WELFARE("welfare", "社会福利", "公民福利、援助"),
        /** 行政运作 */
        ADMINISTRATION("administration", "行政", "政府运作、管理费用"),
        /** 紧急储备 */
        EMERGENCY_RESERVE("emergency-reserve", "紧急储备", "应急资金"),
        /** 债务偿还 */
        DEBT_REPAYMENT("debt-repayment", "债务偿还", "偿还贷款和债务"),
        /** 贸易投资 */
        TRADE_INVESTMENT("trade-investment", "贸易投资", "商业活动、投资"),
        /** 其他支出 */
        OTHER("other", "其他", "其他支出");

        private final String configKey;
        private final String displayName;
        private final String description;

        BudgetCategory(String configKey, String displayName, String description) {
            this.configKey = configKey;
            this.displayName = displayName;
            this.description = description;
        }

        public String getConfigKey() {
            return configKey;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getDescription() {
            return description;
        }
    }

    // ==================== 预算条目 ====================

    /**
     * 预算条目
     */
    record BudgetEntry(
        UUID entryId,
        BudgetCategory category,
        String description,
        BigDecimal amount,
        Instant createdAt
    ) {}

    /**
     * 预算账单（对应 Bill.BUDGET 类型）
     */
    record BudgetBill(
        UUID billId,                // 对应议会 Bill ID
        NationId nationId,
        int year,
        int month,
        List<BudgetEntry> entries,
        BigDecimal totalAmount,
        BudgetStatus status,
        Instant approvedAt,
        String approvedBy
    ) {
        public enum BudgetStatus {
            DRAFT,      // 草稿
            PROPOSED,   // 已提出
            APPROVED,   // 已批准
            REJECTED,   // 已否决
            EXECUTED,   // 已执行
            CANCELLED   // 已取消
        }
    }

    // ==================== 预算操作 ====================

    /**
     * 创建预算草案
     */
    BudgetBill createBudgetDraft(NationId nationId, int year, int month);

    /**
     * 添加预算条目
     */
    void addBudgetEntry(UUID billId, BudgetCategory category, String description, BigDecimal amount);

    /**
     * 提交预算（供议会投票）
     */
    BudgetBill submitBudget(UUID billId);

    /**
     * 执行预算（议会通过后）
     */
    boolean executeBudget(UUID billId);

    /**
     * 取消预算
     */
    boolean cancelBudget(UUID billId);

    // ==================== 查询 ====================

    /**
     * 获取预算
     */
    Optional<BudgetBill> getBudget(UUID billId);

    /**
     * 获取国家当前预算
     */
    Optional<BudgetBill> getCurrentBudget(NationId nationId);

    /**
     * 获取国家预算历史
     */
    List<BudgetBill> getBudgetHistory(NationId nationId, int limit);

    /**
     * 获取预算执行报告
     */
    BudgetExecutionReport getBudgetExecutionReport(UUID billId);

    // ==================== 预算执行报告 ====================

    /**
     * 预算执行报告
     */
    record BudgetExecutionReport(
        UUID billId,
        NationId nationId,
        Instant executedAt,
        BigDecimal totalAllocated,
        BigDecimal totalSpent,
        BigDecimal remaining,
        List<CategorySpending> categorySpendings
    ) {
        public record CategorySpending(
            BudgetCategory category,
            BigDecimal allocated,
            BigDecimal spent
        ) {}
    }

    // ==================== 预算统计 ====================

    /**
     * 获取国家月度支出
     */
    BigDecimal getMonthlySpending(NationId nationId, int year, int month);

    /**
     * 获取国家年度支出
     */
    BigDecimal getYearlySpending(NationId nationId, int year);

    /**
     * 获取国家某类别总支出
     */
    BigDecimal getCategorySpending(NationId nationId, BudgetCategory category);

    /**
     * 检查预算是否超支
     */
    boolean isOverBudget(NationId nationId, BudgetCategory category, BigDecimal amount);

    // ==================== 预算配置 ====================

    record BudgetConfig(
        BigDecimal maxSingleBudget,        // 单个预算最大金额
        BigDecimal maxMonthlyBudget,       // 月度预算上限
        BigDecimal emergencyReservePercent, // 紧急储备比例
        boolean requireApproval,           // 是否需要批准
        int budgetCycleDays                // 预算周期（天）
    ) {
        public static BudgetConfig defaults() {
            return new BudgetConfig(
                new BigDecimal("50000.00"),  // 单个预算上限 5万
                new BigDecimal("100000.00"), // 月度上限 10万
                new BigDecimal("10.00"),     // 10% 紧急储备
                true,                       // 需要批准
                30                          // 30天周期
            );
        }
    }

    BudgetConfig getBudgetConfig();
    void setBudgetConfig(BudgetConfig config);
}
