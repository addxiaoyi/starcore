package dev.starcore.starcore.module.weather.model;

/**
 * 国家天气控制权限等级枚举
 */
public enum NationWeatherPermission {
    /** 无权限 */
    NONE(0),
    /** 基础控制 - 只能设置晴天和小雨 */
    CONTROL_BASIC(1),
    /** 高级控制 - 可以设置所有天气除了暴风雨 */
    CONTROL_ADVANCED(2),
    /** 完全控制 - 可以设置所有天气类型 */
    CONTROL_FULL(3);

    private final int level;

    NationWeatherPermission(int level) {
        this.level = level;
    }

    public int level() {
        return level;
    }

    /**
     * 检查是否拥有指定权限等级
     */
    public boolean hasPermission(NationWeatherPermission required) {
        return this.level >= required.level;
    }

    /**
     * 获取权限描述
     */
    public String getDescription() {
        return switch (this) {
            case NONE -> "无权限";
            case CONTROL_BASIC -> "基础控制 (晴天/小雨)";
            case CONTROL_ADVANCED -> "高级控制 (除暴风雨外)";
            case CONTROL_FULL -> "完全控制 (所有天气)";
        };
    }
}
