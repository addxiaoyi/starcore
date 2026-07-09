package dev.starcore.starcore.module.policy.model;

/**
 * 国策效果范围枚举 v2
 * 扩展支持更多维度的效果应用范围
 */
public enum PolicyEffectScope {
    // ==================== 基础范围 ====================

    /** 全局 - 影响所有领域 */
    GLOBAL("全局", "影响所有领域"),

    /** 领土 - 只影响领土相关 */
    TERRITORY("领土", "只影响领土相关"),

    /** 国家 - 只影响国家层面 */
    NATION("国家", "只影响国家层面"),

    /** 玩家 - 只影响玩家个人 */
    PLAYER("玩家", "只影响玩家个人"),

    // ==================== 经济维度 ====================

    /** 经济 - 影响经济系统 */
    ECONOMY("经济", "影响经济系统"),

    /** 税收 - 影响税收 */
    TAXATION("税收", "影响税收"),

    /** 贸易 - 影响贸易 */
    TRADE("贸易", "影响贸易"),

    /** 生产 - 影响生产 */
    PRODUCTION("生产", "影响生产"),

    // ==================== 军事维度 ====================

    /** 军事 - 影响军事系统 */
    MILITARY("军事", "影响军事系统"),

    /** 防御 - 影响防御能力 */
    DEFENSE("防御", "影响防御能力"),

    /** 进攻 - 影响进攻能力 */
    OFFENSE("进攻", "影响进攻能力"),

    /** 情报 - 影响情报活动 */
    INTELLIGENCE("情报", "影响情报活动"),

    // ==================== 外交维度 ====================

    /** 外交 - 影响外交关系 */
    DIPLOMACY("外交", "影响外交关系"),

    /** 声誉 - 影响国际声誉 */
    REPUTATION("声誉", "影响国际声誉"),

    /** 联盟 - 影响联盟 */
    ALLIANCE("联盟", "影响联盟"),

    // ==================== 稳定维度 ====================

    /** 稳定 - 影响稳定性 */
    STABILITY("稳定", "影响稳定性"),

    /** 幸福 - 影响国民幸福度 */
    HAPPINESS("幸福", "影响国民幸福度"),

    /** 支持 - 影响政府支持率 */
    APPROVAL("支持", "影响政府支持率"),

    /** 腐败 - 影响腐败程度 */
    CORRUPTION("腐败", "影响腐败程度"),

    // ==================== 科技维度 ====================

    /** 科技 - 影响科技发展 */
    TECHNOLOGY("科技", "影响科技发展"),

    /** 研究 - 影响研究速度 */
    RESEARCH("研究", "影响研究速度");

    private final String displayName;
    private final String description;

    PolicyEffectScope(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    /**
     * 获取效果范围分组
     */
    public ScopeGroup group() {
        return switch (this) {
            case GLOBAL, TERRITORY, NATION, PLAYER -> ScopeGroup.BASIC;
            case ECONOMY, TAXATION, TRADE, PRODUCTION -> ScopeGroup.ECONOMY;
            case MILITARY, DEFENSE, OFFENSE, INTELLIGENCE -> ScopeGroup.MILITARY;
            case DIPLOMACY, REPUTATION, ALLIANCE -> ScopeGroup.DIPLOMACY;
            case STABILITY, HAPPINESS, APPROVAL, CORRUPTION -> ScopeGroup.STABILITY;
            case TECHNOLOGY, RESEARCH -> ScopeGroup.TECHNOLOGY;
        };
    }

    /**
     * 范围分组
     */
    public enum ScopeGroup {
        BASIC("基础", "基础影响范围"),
        ECONOMY("经济", "经济维度"),
        MILITARY("军事", "军事维度"),
        DIPLOMACY("外交", "外交维度"),
        STABILITY("稳定", "稳定维度"),
        TECHNOLOGY("科技", "科技维度");

        private final String name;
        private final String description;

        ScopeGroup(String name, String description) {
            this.name = name;
            this.description = description;
        }

        public String getName() { return name; }
        public String description() { return description; }
    }
}
