package dev.starcore.starcore.territory;

/**
 * 权限级别枚举
 * 定义不同的访问级别
 */
public enum PermissionLevel {

    /**
     * 无权限 - 禁止所有操作
     */
    NONE("§c无权限", "禁止所有操作", 0),

    /**
     * 访客 - 只能行走和观看
     */
    VISITOR("§7访客", "只能行走和观看", 1),

    /**
     * 信任 - 可以交互但不能建造
     */
    TRUSTED("§a信任", "可以使用门、按钮等", 2),

    /**
     * 成员 - 可以建造和破坏
     */
    MEMBER("§e成员", "可以建造和破坏", 3),

    /**
     * 管理员 - 可以管理权限
     */
    ADMIN("§6管理员", "可以管理权限和成员", 4),

    /**
     * 所有者 - 完全控制
     */
    OWNER("§c所有者", "完全控制权", 5);

    private final String displayName;
    private final String description;
    private final int level;

    PermissionLevel(String displayName, String description, int level) {
        this.displayName = displayName;
        this.description = description;
        this.level = level;
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
     * 获取数值级别
     */
    public int getLevel() {
        return level;
    }

    /**
     * 检查是否高于或等于指定级别
     */
    public boolean isAtLeast(PermissionLevel other) {
        return this.level >= other.level;
    }

    /**
     * 检查是否低于指定级别
     */
    public boolean isBelow(PermissionLevel other) {
        return this.level < other.level;
    }

    /**
     * 从字符串获取权限级别
     */
    public static PermissionLevel fromString(String name) {
        try {
            return valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return NONE;
        }
    }

    /**
     * 从数值获取权限级别
     */
    public static PermissionLevel fromLevel(int level) {
        for (PermissionLevel pl : values()) {
            if (pl.level == level) {
                return pl;
            }
        }
        return NONE;
    }

    /**
     * 获取所有级别的显示列表
     */
    public static String[] getDisplayNames() {
        PermissionLevel[] values = values();
        String[] names = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            names[i] = values[i].displayName;
        }
        return names;
    }
}
