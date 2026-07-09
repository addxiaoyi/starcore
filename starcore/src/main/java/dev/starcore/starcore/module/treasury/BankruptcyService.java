package dev.starcore.starcore.module.treasury;

import dev.starcore.starcore.module.nation.model.NationId;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * 破产服务接口
 * 管理国家破产状态和功能限制
 */
public interface BankruptcyService {

    // ==================== 破产状态 ====================

    /**
     * 破产原因
     */
    enum BankruptcyReason {
        EXCESSIVE_DEBT("excessive-debt", "债务过高", "国家债务超过偿还能力"),
        OVERDUE_DEBTS("overdue-debts", "债务逾期", "多个债务逾期未还"),
        BANKRUPTCY_PETITION("bankruptcy-petition", "破产申请", "主动申请破产保护"),
        COURT_ORDER("court-order", "法庭裁决", "被法庭强制宣告破产");

        private final String configKey;
        private final String displayName;
        private final String description;

        BankruptcyReason(String configKey, String displayName, String description) {
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

    /**
     * 破产记录
     */
    record BankruptcyRecord(
        NationId nationId,
        BankruptcyReason reason,
        Instant startedAt,
        Instant estimatedEndAt,
        String description,
        Set<String> restrictions,
        Set<String> liftedRestrictions
    ) {
        public long daysSinceBankruptcy() {
            return java.time.Duration.between(startedAt, Instant.now()).toDays();
        }

        public boolean isRestrictionsLifted(String restriction) {
            return liftedRestrictions.contains(restriction);
        }
    }

    // ==================== 破产操作 ====================

    /**
     * 进入破产状态（推荐使用带显式原因的版本）
     * @param nationId 国家ID
     * @param description 破产描述（仅用于日志和显示）
     * @deprecated Use {@link #enterBankruptcy(NationId, BankruptcyReason, String)} instead
     */
    @Deprecated
    void enterBankruptcy(NationId nationId, String description);

    /**
     * 进入破产状态
     * @param nationId 国家ID
     * @param reason 破产原因（显式指定，避免字符串推断）
     * @param description 破产描述（用于日志和显示）
     */
    void enterBankruptcy(NationId nationId, BankruptcyReason reason, String description);

    /**
     * 退出破产状态
     * @param nationId 国家ID
     * @return 是否成功退出
     */
    boolean exitBankruptcy(NationId nationId);

    /**
     * 检查破产状态
     */
    boolean isBankrupt(NationId nationId);

    /**
     * 获取破产记录
     */
    BankruptcyRecord getBankruptcyRecord(NationId nationId);

    /**
     * 获取破产天数
     */
    long getBankruptcyDays(NationId nationId);

    /**
     * 获取破产剩余天数（预计）
     */
    long getRemainingBankruptcyDays(NationId nationId);

    // ==================== 功能限制 ====================

    /**
     * 功能限制类型
     */
    enum Restriction {
        /** 禁止领取新领土 */
        CLAIM_NEW_LAND("claim-land", "领取新领土", "禁止在破产期间扩张领土"),
        /** 禁止宣战 */
        DECLARE_WAR("declare-war", "宣战", "禁止向其他国家宣战"),
        /** 禁止创建联盟 */
        CREATE_ALLIANCE("create-alliance", "创建联盟", "禁止与其他国家建立联盟"),
        /** 禁止发起贸易 */
        INITIATE_TRADE("initiate-trade", "发起贸易", "禁止主动发起新的贸易协定"),
        /** 禁止建设升级 */
        UPGRADE_BUILDINGS("upgrade-buildings", "建设升级", "禁止进行建设和升级"),
        /** 禁止使用高级科技 */
        USE_ADVANCED_TECH("use-advanced-tech", "使用高级科技", "禁止使用高消耗科技"),
        /** 禁止大规模招募 */
        RECRUIT_TROOPS("recruit-troops", "大规模招募", "禁止超出最低编制的招募"),
        /** 禁止举办活动 */
        HOST_EVENTS("host-events", "举办活动", "禁止举办消耗性活动"),
        /** 限制官员任命 */
        LIMIT_OFFICER_APPOINTMENT("limit-officer-appointment", "官员任命", "限制官员任命数量"),
        /** 禁止借贷 */
        BORROW_MONEY("borrow-money", "借贷", "禁止申请新贷款");

        private final String configKey;
        private final String displayName;
        private final String description;

        Restriction(String configKey, String displayName, String description) {
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

    /**
     * 检查功能是否受限
     */
    boolean isRestricted(NationId nationId, Restriction restriction);

    /**
     * 获取国家所有受限功能
     */
    Set<Restriction> getActiveRestrictions(NationId nationId);

    /**
     * 解除特定限制（需要满足条件）
     */
    boolean liftRestriction(NationId nationId, Restriction restriction);

    /**
     * 提前解除破产状态（需偿还所有债务）
     */
    boolean earlyExitBankruptcy(NationId nationId);

    // ==================== 破产保护 ====================

    /**
     * 破产保护期限（天）
     */
    int getBankruptcyProtectionDays(NationId nationId);

    /**
     * 检查是否在破产保护期
     */
    boolean isUnderProtection(NationId nationId);

    /**
     * 获取所有破产国家
     */
    List<NationId> getAllBankruptNations();

    // ==================== 配置 ====================

    /**
     * 破产配置
     */
    record BankruptcyConfig(
        int maxBankruptcyDays,              // 最大破产天数
        int gracePeriodDays,                // 宽限期
        int minDebtRatioToTrigger,         // 触发破产的债务比例（百分比）
        boolean autoRestrictOnBankruptcy,   // 破产时自动限制功能
        boolean allowEarlyExit,             // 允许提前解除
        boolean requireParliamentApproval    // 解除需议会批准
    ) {
        public static BankruptcyConfig defaults() {
            return new BankruptcyConfig(
                30,     // 最多破产30天
                7,      // 7天宽限期
                50,     // 50%债务触发
                true,   // 自动限制
                true,   // 允许提前
                true    // 需议会批准
            );
        }
    }

    BankruptcyConfig getConfig();
    void setConfig(BankruptcyConfig config);
}
