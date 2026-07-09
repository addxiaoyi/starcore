package dev.starcore.starcore.zone;

/**
 * 经济区类型枚举
 */
public enum ZoneType {
    // 商业区 - 高税收加成
    COMMERCIAL("商业区", 50.0, 0.05, 0.02, 5),

    // 工业区 - 高产出加成
    INDUSTRIAL("工业区", 80.0, 0.02, 0.05, 5),

    // 农业区 - 中等加成，资源丰富
    AGRICULTURAL("农业区", 60.0, 0.03, 0.04, 5),

    // 科研区 - 科技加成
    RESEARCH("科研区", 100.0, 0.02, 0.03, 3),

    // 旅游区 - 稳定收入
    TOURISM("旅游区", 40.0, 0.06, 0.01, 5),

    // 军事区 - 防御加成
    MILITARY("军事区", 120.0, 0.01, 0.02, 3),

    // 金融区 - 综合高加成
    FINANCIAL("金融区", 150.0, 0.07, 0.04, 4),

    // 自由贸易区 - 贸易加成
    FREE_TRADE("自由贸易区", 30.0, 0.08, 0.03, 5);

    private final String displayName;
    private final double buildCost;
    private final double taxBonusPerLevel;      // 每级税收加成
    private final double productionBonusPerLevel; // 每级产出加成
    private final int maxLevel;

    ZoneType(String displayName, double buildCost, double taxBonusPerLevel,
             double productionBonusPerLevel, int maxLevel) {
        this.displayName = displayName;
        this.buildCost = buildCost;
        this.taxBonusPerLevel = taxBonusPerLevel;
        this.productionBonusPerLevel = productionBonusPerLevel;
        this.maxLevel = maxLevel;
    }

    public String getDisplayName() { return displayName; }
    public double getBuildCost() { return buildCost; }
    public double getTaxBonusPerLevel() { return taxBonusPerLevel; }
    public double getProductionBonusPerLevel() { return productionBonusPerLevel; }
    public int getMaxLevel() { return maxLevel; }

    /**
     * 获取图标材质名称
     */
    public String getIcon() {
        return switch (this) {
            case COMMERCIAL -> "CHEST";
            case INDUSTRIAL -> "FURNACE";
            case AGRICULTURAL -> "WHEAT";
            case RESEARCH -> "BOOK";
            case TOURISM -> "MAP";
            case MILITARY -> "IRON_SWORD";
            case FINANCIAL -> "GOLD_INGOT";
            case FREE_TRADE -> "EMERALD";
        };
    }
}
