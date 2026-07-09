package dev.starcore.starcore.module.policy.model;

/**
 * 国策效果类型枚举 v2
 * 定义 30+ 种政策效果类型
 */
public enum PolicyEffectType {
    // ==================== 经济效果 ====================

    /** 税率调整 */
    TAX_RATE_MODIFIER("税率调整", "调整税率百分比"),

    /** 生产加成 */
    PRODUCTION_BONUS("生产加成", "增加产出"),

    /** 贸易收入调整 */
    TRADE_INCOME_MODIFIER("贸易收入调整", "调整贸易收入"),

    /** 利率 */
    INTEREST_RATE("利率", "调整利率"),

    /** 通胀控制 */
    INFLATION_CONTROL("通胀控制", "控制通货膨胀"),

    /** 经济增长 */
    ECONOMIC_GROWTH("经济增长", "促进经济增长"),

    /** 就业率 */
    EMPLOYMENT_RATE("就业率", "影响就业"),

    // ==================== 人口效果 ====================

    /** 人口增长 */
    POPULATION_GROWTH("人口增长", "影响人口增长"),

    /** 幸福度 */
    HAPPINESS_MODIFIER("幸福度", "影响国民幸福度"),

    /** 生产效率 */
    PRODUCTIVITY("生产效率", "影响生产效率"),

    /** 移民率 */
    IMMIGRATION_RATE("移民率", "影响人口迁入迁出"),

    // ==================== 军事效果 ====================

    /** 防御加成 */
    DEFENSE_BONUS("防御加成", "增加防御能力"),

    /** 攻击加成 */
    ATTACK_BONUS("攻击加成", "增加攻击能力"),

    /** 招募速度 */
    RECRUIT_SPEED("招募速度", "影响军队招募"),

    /** 单位维护费用 */
    UNIT_MAINTENANCE_COST("单位维护费用", "影响军队维护成本"),

    /** 征兵率 */
    CONSCRIPTION_RATE("征兵率", "影响可征兵数量"),

    /** 士气 */
    MORALE("士气", "影响军队士气"),

    /** 训练效率 */
    TRAINING_EFFICIENCY("训练效率", "影响训练速度"),

    /** 装备加成 */
    EQUIPMENT_BONUS("装备加成", "影响装备效果"),

    // ==================== 科技效果 ====================

    /** 研究速度 */
    RESEARCH_SPEED("研究速度", "加快科技研究"),

    /** 科技成本减免 */
    TECH_COST_REDUCTION("科技成本减免", "降低研发成本"),

    /** 创新加成 */
    INNOVATION_BONUS("创新加成", "增加创新产出"),

    // ==================== 外交效果 ====================

    /** 外交声誉 */
    DIPLOMATIC_REPUTATION("外交声誉", "影响国际声誉"),

    /** 贸易协定加成 */
    TRADE_AGREEMENT_BONUS("贸易协定加成", "增强贸易协定效果"),

    /** 联盟强度 */
    ALLIANCE_STRENGTH("联盟强度", "影响联盟关系"),

    /** 反间谍能力 */
    ESPIONAGE_RESISTANCE("反间谍", "降低被间谍活动影响"),

    /** 间谍效率 */
    ESPIONAGE_EFFICIENCY("间谍效率", "提升间谍活动效果"),

    // ==================== 稳定性效果 ====================

    /** 稳定性 */
    STABILITY("稳定性", "影响国家稳定"),

    /** 革命风险 */
    REVOLUTION_RISK("革命风险", "影响叛乱可能性"),

    /** 腐败程度 */
    CORRUPTION("腐败程度", "影响腐败水平"),

    /** 政府效率 */
    GOVERNMENT_EFFICIENCY("政府效率", "影响行政效率"),

    // ==================== 资源效果 ====================

    /** 资源产出 */
    RESOURCE_OUTPUT("资源产出", "影响资源采集"),

    /** 资源消耗 */
    RESOURCE_CONSUMPTION("资源消耗", "影响资源使用"),

    /** 战略储备 */
    STRATEGIC_RESERVES("战略储备", "增加储备容量"),

    // ==================== 文化效果 ====================

    /** 文化传播 */
    CULTURE_SPREAD("文化传播", "增加文化影响力"),

    /** 宗教影响力 */
    RELIGIOUS_INFLUENCE("宗教影响力", "影响宗教传播"),

    /** 宣传效果 */
    PROPAGANDA_EFFECTIVENESS("宣传效果", "影响宣传效力"),

    // ==================== 支持率效果 ====================

    /** 支持率 */
    APPROVAL_RATING("支持率", "影响政府支持率"),

    /** 异见者镇压 */
    DISSIDENTS_SUPPRESSION("异见者镇压", "压制反对声音"),

    /** 言论自由 */
    FREEDOM_OF_SPEECH("言论自由", "影响言论开放程度"),

    // ==================== 特殊效果 ====================

    /** 战争支持 */
    WAR_SUPPORT("战争支持", "影响战争参与意愿"),

    /** 经济封锁抵抗 */
    EMBARGO_RESISTANCE("经济封锁抵抗", "抵抗贸易制裁"),

    /** 自然灾害抵抗 */
    NATURAL_DISASTER_RESISTANCE("自然灾害抵抗", "抵抗天灾");

    private final String displayName;
    private final String description;

    PolicyEffectType(String displayName, String description) {
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
     * 获取效果分类
     */
    public EffectGroup group() {
        return switch (this) {
            case TAX_RATE_MODIFIER, PRODUCTION_BONUS, TRADE_INCOME_MODIFIER, INTEREST_RATE,
                 INFLATION_CONTROL, ECONOMIC_GROWTH, EMPLOYMENT_RATE -> EffectGroup.ECONOMIC;
            case POPULATION_GROWTH, HAPPINESS_MODIFIER, PRODUCTIVITY, IMMIGRATION_RATE -> EffectGroup.POPULATION;
            case DEFENSE_BONUS, ATTACK_BONUS, RECRUIT_SPEED, UNIT_MAINTENANCE_COST,
                 CONSCRIPTION_RATE, MORALE, TRAINING_EFFICIENCY, EQUIPMENT_BONUS -> EffectGroup.MILITARY;
            case RESEARCH_SPEED, TECH_COST_REDUCTION, INNOVATION_BONUS -> EffectGroup.TECHNOLOGY;
            case DIPLOMATIC_REPUTATION, TRADE_AGREEMENT_BONUS, ALLIANCE_STRENGTH,
                 ESPIONAGE_RESISTANCE, ESPIONAGE_EFFICIENCY -> EffectGroup.DIPLOMATIC;
            case STABILITY, REVOLUTION_RISK, CORRUPTION, GOVERNMENT_EFFICIENCY -> EffectGroup.STABILITY;
            case RESOURCE_OUTPUT, RESOURCE_CONSUMPTION, STRATEGIC_RESERVES -> EffectGroup.RESOURCE;
            case CULTURE_SPREAD, RELIGIOUS_INFLUENCE, PROPAGANDA_EFFECTIVENESS -> EffectGroup.CULTURAL;
            case APPROVAL_RATING, DISSIDENTS_SUPPRESSION, FREEDOM_OF_SPEECH -> EffectGroup.APPROVAL;
            case WAR_SUPPORT, EMBARGO_RESISTANCE, NATURAL_DISASTER_RESISTANCE -> EffectGroup.SPECIAL;
        };
    }

    /**
     * 效果分组
     */
    public enum EffectGroup {
        ECONOMIC("经济", "经济类效果"),
        POPULATION("人口", "人口类效果"),
        MILITARY("军事", "军事类效果"),
        TECHNOLOGY("科技", "科技类效果"),
        DIPLOMATIC("外交", "外交类效果"),
        STABILITY("稳定", "稳定类效果"),
        RESOURCE("资源", "资源类效果"),
        CULTURAL("文化", "文化类效果"),
        APPROVAL("支持", "支持率类效果"),
        SPECIAL("特殊", "特殊效果");

        private final String name;
        private final String description;

        EffectGroup(String name, String description) {
            this.name = name;
            this.description = description;
        }

        public String getName() { return name; }
        public String description() { return description; }
    }
}
