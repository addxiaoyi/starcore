package dev.starcore.starcore.module.shop.model;

/**
 * 商店拥有者类型
 */
public enum ShopOwnerType {
    PLAYER("玩家", "玩家个人商店"),
    NATION("国家", "国家商店"),
    GUILD("公会", "公会商店"),
    SERVER("服务器", "服务器商店");

    private final String displayName;
    private final String description;

    ShopOwnerType(String displayName, String description) {
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
     * 从字符串获取拥有者类型
     */
    public static ShopOwnerType fromString(String name) {
        if (name == null) {
            return PLAYER;
        }
        try {
            return valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return PLAYER;
        }
    }
}
