package dev.starcore.starcore.module.territoryrent.model;

/**
 * 权限等级枚举
 */
public enum PermissionLevel {
    /**
     * 无权限
     */
    NONE(0, "无权限", "无法使用租借的土地"),

    /**
     * 使用权限（行走、交互）
     */
    USE(1, "使用", "可以在土地上行走和交互"),

    /**
     * 建设权限（可以放置方块）
     */
    BUILD(2, "建设", "可以在土地上放置和破坏方块"),

    /**
     * 管理权限（可以转租）
     */
    MANAGE(3, "管理", "可以设置权限和转租");

    private final int level;
    private final String displayName;
    private final String description;

    PermissionLevel(int level, String displayName, String description) {
        this.level = level;
        this.displayName = displayName;
        this.description = description;
    }

    public int level() {
        return level;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    /**
     * 检查是否拥有至少指定级别的权限
     */
    public boolean hasAtLeast(PermissionLevel other) {
        return this.level >= other.level;
    }

    /**
     * 从整数获取权限等级
     */
    public static PermissionLevel fromLevel(int level) {
        for (PermissionLevel pl : values()) {
            if (pl.level == level) {
                return pl;
            }
        }
        return level >= 3 ? MANAGE : (level >= 2 ? BUILD : (level >= 1 ? USE : NONE));
    }
}