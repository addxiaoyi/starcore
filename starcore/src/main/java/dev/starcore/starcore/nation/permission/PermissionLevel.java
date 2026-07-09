package dev.starcore.starcore.nation.permission;

/**
 * 权限层级
 * 定义不同职位的默认权限等级
 */
public enum PermissionLevel {
    /**
     * 创建者
     * - 国家创建者
     * - 拥有最高权限
     * - 可以解散国家、转让国家
     */
    FOUNDER("创始人", 4),

    /**
     * 领导者
     * - 国家领导层
     * - 拥有管理权限
     * - 可以管理成员、设置、外交
     */
    LEADER("领导者", 3),

    /**
     * 受信任成员
     * - 资深成员
     * - 拥有部分管理权限
     * - 可以领取领地、管理军队
     */
    TRUSTED("信任成员", 2),

    /**
     * 普通成员
     * - 基础成员
     * - 拥有基本权限
     * - 可以使用国家设施
     */
    MEMBER("成员", 1);

    private final String displayName;
    private final int level;

    PermissionLevel(String displayName, int level) {
        this.displayName = displayName;
        this.level = level;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getLevel() {
        return level;
    }

    /**
     * 检查是否高于或等于指定等级
     */
    public boolean isAtLeast(PermissionLevel other) {
        return this.level >= other.level;
    }

    /**
     * 检查是否高于指定等级
     */
    public boolean isHigherThan(PermissionLevel other) {
        return this.level > other.level;
    }
}
