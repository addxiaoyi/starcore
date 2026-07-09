package dev.starcore.starcore.territory;

/**
 * 领土权限类型枚举
 * 定义所有可能的领土权限类型
 */
public enum TerritoryPermission {

    /**
     * 建造权限 - 放置方块
     */
    BUILD("建造", "放置方块", "starcore.territory.build"),

    /**
     * 破坏权限 - 破坏方块
     */
    BREAK("破坏", "破坏方块", "starcore.territory.break"),

    /**
     * 交互权限 - 使用门、按钮、拉杆等
     */
    INTERACT("交互", "使用门、按钮、拉杆等", "starcore.territory.interact"),

    /**
     * 容器权限 - 打开箱子、熔炉等
     */
    CONTAINER("容器", "打开箱子、熔炉、潜影盒等", "starcore.territory.container"),

    /**
     * PvP权限 - 玩家对战
     */
    PVP("PvP", "攻击其他玩家", "starcore.territory.pvp"),

    /**
     * 传送权限 - 传送到领土
     */
    TELEPORT("传送", "传送到领土出生点", "starcore.territory.teleport"),

    /**
     * 生成怪物权限 - 怪物生成
     */
    SPAWN_MOBS("怪物生成", "允许怪物生成", "starcore.territory.spawn_mobs"),

    /**
     * 爆炸权限 - 爆炸破坏
     */
    EXPLOSION("爆炸", "允许爆炸破坏方块", "starcore.territory.explosion"),

    /**
     * 使用物品权限 - 使用打火石、桶等
     */
    ITEM_USE("使用物品", "使用打火石、桶、骨粉等", "starcore.territory.item_use"),

    /**
     * 动物伤害权限 - 伤害动物
     */
    HURT_ANIMALS("伤害动物", "攻击和杀死动物", "starcore.territory.hurt_animals"),

    /**
     * 红石权限 - 使用红石装置
     */
    REDSTONE("红石", "使用红石装置", "starcore.territory.redstone"),

    /**
     * 展示框权限 - 操作展示框
     */
    ITEM_FRAME("展示框", "操作展示框和盔甲架", "starcore.territory.item_frame"),

    /**
     * 耕地权限 - 耕地和践踏
     */
    FARMLAND("耕地", "耕地和践踏农田", "starcore.territory.farmland"),

    /**
     * 载具权限 - 使用矿车和船
     */
    VEHICLE("载具", "放置和使用矿车、船", "starcore.territory.vehicle");

    private final String displayName;
    private final String description;
    private final String permission;

    TerritoryPermission(String displayName, String description, String permission) {
        this.displayName = displayName;
        this.description = description;
        this.permission = permission;
    }

    /**
     * 获取显示名称
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * 获取描述
     */
    public String getDescription() {
        return description;
    }

    /**
     * 获取权限节点
     */
    public String getPermission() {
        return permission;
    }

    /**
     * 获取带颜色的显示名称
     */
    public String getColoredName() {
        return "§e" + displayName;
    }

    /**
     * 从字符串获取权限类型
     */
    public static TerritoryPermission fromString(String name) {
        try {
            return valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 获取所有权限的显示列表
     */
    public static String[] getDisplayNames() {
        TerritoryPermission[] values = values();
        String[] names = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            names[i] = values[i].displayName;
        }
        return names;
    }
}
