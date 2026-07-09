package dev.starcore.starcore.module.policy.model;

/**
 * 国策分类枚举 v2
 * 参考真实世界政治体系，扩展为 22 个分类
 */
public enum PolicyCategory {
    // ==================== 内政类 ====================

    /** 行政 - 政府运作效率、官僚体系 */
    ADMINISTRATION("行政", "政府行政效率"),

    /** 教育 - 国民教育水平 */
    EDUCATION("教育", "教育投入与质量"),

    /** 医疗 - 公共卫生体系 */
    HEALTHCARE("医疗", "医疗保障体系"),

    /** 住房 - 住房政策 */
    HOUSING("住房", "住房与地产政策"),

    /** 社会福利 - 社会保障体系 */
    SOCIAL_WELFARE("社会福利", "社会保障与福利"),

    /** 劳工 - 就业与劳动关系 */
    LABOR("劳工", "劳工与就业政策"),

    // ==================== 经济类 ====================

    /** 财政 - 政府预算与支出 */
    FISCAL("财政", "财政政策"),

    /** 货币 - 货币政策与汇率 */
    MONETARY("货币", "货币政策"),

    /** 贸易 - 对外贸易政策 */
    TRADE("贸易", "贸易政策"),

    /** 产业 - 产业发展政策 */
    INDUSTRY("产业", "产业政策"),

    /** 税收 - 税制设计 */
    TAXATION("税收", "税收制度"),

    // ==================== 军事/国防类 ====================

    /** 国防 - 军事战略与部署 */
    DEFENSE("国防", "国防与军事"),

    /** 情报 - 情报与反间谍 */
    INTELLIGENCE("情报", "情报与安全"),

    /** 征兵 - 兵役制度 */
    RECRUITMENT("征兵", "兵役与动员"),

    /** 军备 - 武器装备发展 */
    ARMS("军备", "军备与装备"),

    // ==================== 外交类 ====================

    /** 外交 - 外交总体方针 */
    FOREIGN_POLICY("外交", "外交政策"),

    /** 移民 - 人口流动政策 */
    IMMIGRATION("移民", "移民政策"),

    /** 文化交流 - 文化对外交流 */
    CULTURAL_EXCHANGE("文化交流", "文化外交"),

    // ==================== 资源/环境类 ====================

    /** 资源管理 - 自然资源开发 */
    RESOURCE_MANAGEMENT("资源管理", "资源政策"),

    /** 环保 - 环境保护政策 */
    ENVIRONMENTAL("环保", "环境政策"),

    // ==================== 宗教/文化类 ====================

    /** 宗教 - 宗教政策 */
    RELIGION("宗教", "宗教政策"),

    /** 文化 - 文化发展政策 */
    CULTURE("文化", "文化政策"),

    /** 宣传 - 信息传播控制 */
    PROPAGANDA("宣传", "宣传与媒体"),

    /** 兼容旧版粗粒度经济分类 */
    ECONOMY("经济", "经济政策"),

    /** 兼容旧版粗粒度社会分类 */
    SOCIAL("社会", "社会政策"),

    /** 兼容旧版粗粒度军事分类 */
    MILITARY("军事", "军事政策"),

    /** 兼容旧版粗粒度政治分类 */
    POLITICAL("政治", "政治政策");

    private final String displayName;
    private final String description;

    PolicyCategory(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    /**
     * 获取显示名称
     */
    public String displayName() {
        return displayName;
    }

    /**
     * 获取描述
     */
    public String description() {
        return description;
    }

    /**
     * 获取分类分组
     */
    public PolicyCategoryGroup group() {
        return switch (this) {
            case ADMINISTRATION, EDUCATION, HEALTHCARE, HOUSING, SOCIAL_WELFARE, LABOR -> PolicyCategoryGroup.INTERNAL;
            case FISCAL, MONETARY, TRADE, INDUSTRY, TAXATION, ECONOMY -> PolicyCategoryGroup.ECONOMY;
            case DEFENSE, INTELLIGENCE, RECRUITMENT, ARMS, MILITARY -> PolicyCategoryGroup.MILITARY;
            case FOREIGN_POLICY, IMMIGRATION, CULTURAL_EXCHANGE -> PolicyCategoryGroup.DIPLOMACY;
            case RESOURCE_MANAGEMENT, ENVIRONMENTAL -> PolicyCategoryGroup.RESOURCE;
            case RELIGION, CULTURE, PROPAGANDA -> PolicyCategoryGroup.CULTURAL;
            case SOCIAL, POLITICAL -> PolicyCategoryGroup.INTERNAL;
        };
    }

    /**
     * 政策分类分组
     */
    public enum PolicyCategoryGroup {
        INTERNAL("内政", "政府内部治理"),
        ECONOMY("经济", "财政与经济"),
        MILITARY("军事", "国防与军事"),
        DIPLOMACY("外交", "对外关系"),
        RESOURCE("资源", "资源与环境"),
        CULTURAL("文化", "宗教与文化");

        private final String name;
        private final String description;

        PolicyCategoryGroup(String name, String description) {
            this.name = name;
            this.description = description;
        }

        public String getName() { return name; }
        public String description() { return description; }
    }
}
