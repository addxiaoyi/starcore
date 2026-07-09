package dev.starcore.starcore.module.shop.model;

/**
 * 商店类别
 */
public enum ShopCategory {
    GENERAL("杂货", "普通物品"),
    WEAPONS("武器", "武器和盔甲"),
    TOOLS("工具", "工具和器械"),
    BLOCKS("建筑", "建筑方块"),
    FOOD("食物", "食品和农产品"),
    POTIONS("药水", "药水和小麦"),
    ENCHANTMENTS("附魔", "附魔书和青金石"),
    SPAWNERS("刷怪笼", "生物刷怪笼"),
    RARE("稀有", "稀有物品"),
    MAGIC("魔法", "魔法物品"),
    RESOURCE("资源", "原材料和矿石"),
    CUSTOM("自定义", "自定义类别");

    private final String displayName;
    private final String description;

    ShopCategory(String displayName, String description) {
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
     * 从字符串获取类别
     */
    public static ShopCategory fromString(String name) {
        if (name == null) {
            return GENERAL;
        }
        try {
            return valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return GENERAL;
        }
    }
}
