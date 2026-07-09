package dev.starcore.starcore.war;

/**
 * 情报类型
 */
public enum IntelligenceType {
    /**
     * 军事情报 - 军队数量、位置、装备
     */
    MILITARY("军事情报", 50, 24),

    /**
     * 战略情报 - 战争计划、战略意图
     */
    STRATEGIC("战略情报", 100, 72),

    /**
     * 战术情报 - 战场情况、据点防御
     */
    TACTICAL("战术情报", 30, 12),

    /**
     * 政治情报 - 内政状况、民心士气
     */
    POLITICAL("政治情报", 40, 48),

    /**
     * 经济情报 - 财政状况、资源储备
     */
    ECONOMIC("经济情报", 60, 96),

    /**
     * 技术情报 - 科技水平、武器装备
     */
    TECHNICAL("技术情报", 80, 168);

    private final String displayName;
    private final int baseValue;        // 基础价值
    private final int validityHours;    // 有效期（小时）

    IntelligenceType(String displayName, int baseValue, int validityHours) {
        this.displayName = displayName;
        this.baseValue = baseValue;
        this.validityHours = validityHours;
    }

    public String displayName() {
        return displayName;
    }

    public int baseValue() {
        return baseValue;
    }

    public int validityHours() {
        return validityHours;
    }
}
