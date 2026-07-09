package dev.starcore.starcore.module.resource.model;

/**
 * 资源稀有度枚举
 * 决定资源的基础价格和产出效率
 */
public enum ResourceRarity {
    /**
     * 普通 - 随处可见的资源
     */
    COMMON("普通", 1.0, "#FFFFFF"),

    /**
     * 罕见 - 较少见的资源
     */
    UNCOMMON("罕见", 2.5, "#00FF00"),

    /**
     * 稀有 - 稀有的资源
     */
    RARE("稀有", 5.0, "#0080FF"),

    /**
     * 史诗 - 极为稀有的资源
     */
    EPIC("史诗", 10.0, "#A020F0"),

    /**
     * 传说 - 传奇般的稀有资源
     */
    LEGENDARY("传说", 25.0, "#FFA500");

    private final String displayName;
    private final double priceMultiplier;
    private final String colorCode;

    ResourceRarity(String displayName, double priceMultiplier, String colorCode) {
        this.displayName = displayName;
        this.priceMultiplier = priceMultiplier;
        this.colorCode = colorCode;
    }

    /**
     * 获取显示名称
     */
    public String displayName() {
        return displayName;
    }

    /**
     * 获取价格倍数
     */
    public double priceMultiplier() {
        return priceMultiplier;
    }

    /**
     * 获取颜色代码
     */
    public String colorCode() {
        return colorCode;
    }
}
