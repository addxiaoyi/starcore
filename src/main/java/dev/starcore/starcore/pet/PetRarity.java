package dev.starcore.starcore.pet;

/**
 * 宠物稀有度枚举
 */
public enum PetRarity {
    COMMON("普通", "f", 1.0, 0),
    UNCOMMON("优秀", "a", 1.2, 100),
    RARE("稀有", "6", 1.5, 500),
    EPIC("史诗", "5", 2.0, 2500),
    LEGENDARY("传说", "6", 3.0, 10000),
    MYTHIC("神话", "c", 5.0, 50000);

    private final String displayName;
    private final String colorCode;
    private final double attributeMultiplier;
    private final int shopPrice;

    PetRarity(String displayName, String colorCode, double attributeMultiplier, int shopPrice) {
        this.displayName = displayName;
        this.colorCode = colorCode;
        this.attributeMultiplier = attributeMultiplier;
        this.shopPrice = shopPrice;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getColorCode() {
        return colorCode;
    }

    public double getAttributeMultiplier() {
        return attributeMultiplier;
    }

    public int getShopPrice() {
        return shopPrice;
    }

    /**
     * 获取带颜色的显示名称
     */
    public String getColoredName() {
        return "§" + colorCode + displayName + "§r";
    }

    /**
     * 从字符串获取稀有度
     */
    public static PetRarity fromString(String name) {
        try {
            return valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return COMMON;
        }
    }

    /**
     * 获取下一个稀有度
     */
    public PetRarity getNextRarity() {
        return switch (this) {
            case COMMON -> UNCOMMON;
            case UNCOMMON -> RARE;
            case RARE -> EPIC;
            case EPIC -> LEGENDARY;
            case LEGENDARY -> MYTHIC;
            case MYTHIC -> MYTHIC;
        };
    }

    /**
     * 获取升级所需经验
     */
    public int getUpgradeExp() {
        return switch (this) {
            case COMMON -> 0;
            case UNCOMMON -> 500;
            case RARE -> 2000;
            case EPIC -> 8000;
            case LEGENDARY -> 30000;
            case MYTHIC -> 100000;
        };
    }
}
