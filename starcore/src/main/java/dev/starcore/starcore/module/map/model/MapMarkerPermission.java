package dev.starcore.starcore.module.map.model;

/**
 * 地图标记权限枚举
 * 定义不同类型玩家对地图标记的操作权限
 */
public enum MapMarkerPermission {
    // 查看权限
    VIEW_PUBLIC("查看公共标记", "starcore.map.view.public"),
    VIEW_NATION("查看国家标记", "starcore.map.view.nation"),
    VIEW_FRIENDLY("查看友方标记", "starcore.map.view.friendly"),
    VIEW_ALL("查看所有标记", "starcore.map.view.all"),

    // 创建权限
    CREATE_WAYPOINT("创建路径点", "starcore.map.create.waypoint"),
    CREATE_HOME("创建家标记", "starcore.map.create.home"),
    CREATE_SHOP("创建商店标记", "starcore.map.create.shop"),
    CREATE_FARM("创建农场标记", "starcore.map.create.farm"),
    CREATE_CUSTOM("创建自定义标记", "starcore.map.create.custom"),

    // 编辑权限
    EDIT_OWN("编辑自己的标记", "starcore.map.edit.own"),
    EDIT_NATION("编辑国家标记", "starcore.map.edit.nation"),
    EDIT_ALL("编辑所有标记", "starcore.map.edit.all"),

    // 删除权限
    DELETE_OWN("删除自己的标记", "starcore.map.delete.own"),
    DELETE_NATION("删除国家标记", "starcore.map.delete.nation"),
    DELETE_ALL("删除所有标记", "starcore.map.delete.all"),

    // 特殊权限
    PIN_NATION("钉住国家标记", "starcore.map.pin.nation"),
    PIN_GLOBAL("钉住全局标记", "starcore.map.pin.global"),
    VIEW_DYNAMIC("查看动态标记", "starcore.map.view.dynamic"),
    CREATE_BATTLE("创建战场标记", "starcore.map.create.battle"),
    CREATE_EVENT("创建事件标记", "starcore.map.create.event");

    private final String displayName;
    private final String permissionNode;

    MapMarkerPermission(String displayName, String permissionNode) {
        this.displayName = displayName;
        this.permissionNode = permissionNode;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPermissionNode() {
        return permissionNode;
    }

    /**
     * 获取查看权限的最低级别
     */
    public static MapMarkerPermission getMinViewPermission() {
        return VIEW_PUBLIC;
    }

    /**
     * 获取管理员权限
     */
    public static MapMarkerPermission getAdminPermission() {
        return VIEW_ALL;
    }
}
