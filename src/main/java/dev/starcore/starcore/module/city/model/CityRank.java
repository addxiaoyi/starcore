package dev.starcore.starcore.module.city.model;

/**
 * 城市成员等级
 */
public enum CityRank {
    /**
     * 市长 - 城市创建者，拥有全部权限
     */
    MAYOR("市长", 3),

    /**
     * 官员 - 由市长任命，拥有部分管理权限
     */
    OFFICER("官员", 2),

    /**
     * 居民 - 普通城市成员
     */
    RESIDENT("居民", 1);

    private final String displayName;
    private final int level;

    CityRank(String displayName, int level) {
        this.displayName = displayName;
        this.level = level;
    }

    public String displayName() {
        return displayName;
    }

    public int level() {
        return level;
    }

    /**
     * 检查当前等级是否有权执行某个权限等级的操作
     */
    public boolean hasPermission(CityRank required) {
        return this.level >= required.level;
    }

    /**
     * 从字符串获取等级
     */
    public static CityRank fromString(String name) {
        if (name == null) {
            return RESIDENT;
        }
        return switch (name.toLowerCase()) {
            case "mayor", "市长" -> MAYOR;
            case "officer", "官员" -> OFFICER;
            default -> RESIDENT;
        };
    }
}
