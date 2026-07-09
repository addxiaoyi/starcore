package dev.starcore.starcore.module.map.model;

/**
 * 地图标记分类枚举
 * 用于对不同类型的地图标记进行分组和过滤
 */
public enum MapMarkerCategory {
    // 基础分类
    PLAYER("玩家", "player", 1),
    NATION("国家", "nation", 2),
    TERRITORY("领土", "territory", 3),
    CITY("城市", "city", 4),
    RESOURCE("资源", "resource", 5),

    // 玩家自定义标记
    CUSTOM_PLAYER("玩家标记", "custom-player", 10),
    CUSTOM_WAYPOINT("路径点", "waypoint", 11),
    CUSTOM_HOME("家", "home", 12),
    CUSTOM_SHOP("商店", "shop", 13),
    CUSTOM_FARM("农场", "farm", 14),
    CUSTOM_SPAWN("出生点", "spawn", 15),
    CUSTOM_BATTLE("战场", "battle", 16),
    CUSTOM_EVENT("事件", "event", 17),
    CUSTOM_PUBLIC("公共标记", "public", 18),

    // 国家标记
    NATION_CAPITAL("首都", "capital", 20),
    NATION_BATTLEFIELD("战场", "battlefield", 21),
    NATION_PORT("港口", "port", 22),
    NATION_FORTRESS("要塞", "fortress", 23),
    NATION_TRADE_HUB("贸易枢纽", "trade-hub", 24),

    // 动态标记（随时间/事件变化）
    DYNAMIC_WAR("战争区域", "war-zone", 30),
    DYNAMIC_PVP("PVP区域", "pvp-zone", 31),
    DYNAMIC_SAFE("安全区域", "safe-zone", 32),
    DYNAMIC_TRADING("交易区", "trading-zone", 33),
    DYNAMIC_DUNGEON("副本入口", "dungeon", 34),
    DYNAMIC_BOSS("BOSS位置", "boss", 35),

    // 特殊标记
    SPECIAL_PORTAL("传送门", "portal", 40),
    SPECIAL_MONUMENT("纪念碑", "monument", 41),
    SPECIAL_TREASURE("宝箱", "treasure", 42),
    SPECIAL_QUEST("任务点", "quest", 43);

    private final String displayName;
    private final String iconKey;
    private final int priority;

    MapMarkerCategory(String displayName, String iconKey, int priority) {
        this.displayName = displayName;
        this.iconKey = iconKey;
        this.priority = priority;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getIconKey() {
        return iconKey;
    }

    public int getPriority() {
        return priority;
    }

    /**
     * 检查是否为自定义标记分类
     */
    public boolean isCustomMarker() {
        return this == CUSTOM_PLAYER || this == CUSTOM_WAYPOINT ||
               this == CUSTOM_HOME || this == CUSTOM_SHOP ||
               this == CUSTOM_FARM || this == CUSTOM_SPAWN ||
               this == CUSTOM_BATTLE || this == CUSTOM_EVENT;
    }

    /**
     * 检查是否为动态标记分类
     */
    public boolean isDynamicMarker() {
        return this == DYNAMIC_WAR || this == DYNAMIC_PVP ||
               this == DYNAMIC_SAFE || this == DYNAMIC_TRADING ||
               this == DYNAMIC_DUNGEON || this == DYNAMIC_BOSS;
    }

    /**
     * 检查是否为玩家可创建的标记
     */
    public boolean isPlayerCreatable() {
        return isCustomMarker();
    }

    /**
     * 检查是否为管理员专用标记
     */
    public boolean isAdminOnly() {
        return this == NATION_CAPITAL || this == SPECIAL_MONUMENT ||
               this == SPECIAL_TREASURE;
    }

    /**
     * 根据iconKey查找分类
     */
    public static MapMarkerCategory fromIconKey(String iconKey) {
        for (MapMarkerCategory category : values()) {
            if (category.iconKey.equals(iconKey)) {
                return category;
            }
        }
        return PLAYER;
    }
}
