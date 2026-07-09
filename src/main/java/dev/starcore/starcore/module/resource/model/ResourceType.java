package dev.starcore.starcore.module.resource.model;

/**
 * 资源类型枚举
 * 定义游戏中不同类别的资源
 */
public enum ResourceType {
    /**
     * 矿产资源 - 从地下开采的矿物和金属
     */
    MINERAL("矿产", "从地下开采的矿物和金属资源"),

    /**
     * 农业资源 - 农作物和农产品
     */
    AGRICULTURAL("农业", "农作物和农产品"),

    /**
     * 能源资源 - 能量来源
     */
    ENERGY("能源", "各类能量资源"),

    /**
     * 奢侈品 - 高价值的稀有商品
     */
    LUXURY("奢侈品", "高价值的稀有商品"),

    /**
     * 工业原料 - 工业生产所需的基础材料
     */
    INDUSTRIAL("工业原料", "工业生产所需的基础材料"),

    /**
     * 化工产品 - 化学工业产品
     */
    CHEMICAL("化工产品", "化学工业产品"),

    /**
     * 战略物资 - 军事和战略用途的关键资源
     */
    STRATEGIC("战略物资", "军事和战略用途的关键资源");

    private final String displayName;
    private final String description;

    ResourceType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }
}
