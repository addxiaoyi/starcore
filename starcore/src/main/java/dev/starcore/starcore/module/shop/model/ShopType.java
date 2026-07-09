package dev.starcore.starcore.module.shop.model;

/**
 * 商店类型枚举
 */
public enum ShopType {
    PLAYER("玩家商店", "玩家创建的私人商店"),
    NPC("NPC商店", "绑定到NPC的商店"),
    AUCTION("拍卖行", "公共拍卖系统"),
    BLACK_MARKET("黑市", "特殊黑市商店"),
    GUILD("公会商店", "公会专属商店"),
    NATION("国家商店", "国家公共商店"),
    SERVER("服务器商店", "管理员管理的服务器商店");

    private final String displayName;
    private final String description;

    ShopType(String displayName, String description) {
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
     * 从字符串获取商店类型
     */
    public static ShopType fromString(String name) {
        if (name == null) {
            return PLAYER;
        }
        try {
            return valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return PLAYER;
        }
    }

    /**
     * 检查是否是公共商店
     */
    public boolean isPublic() {
        return this == AUCTION || this == SERVER;
    }

    /**
     * 检查是否需要NPC
     */
    public boolean requiresNpc() {
        return this == NPC;
    }
}
