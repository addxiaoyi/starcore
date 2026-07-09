package dev.starcore.starcore.module.army.fatigue.model;

/**
 * 疲劳等级枚举
 */
public enum FatigueLevel {
    FRESH("精神饱满", "身体状态极佳，所有属性正常"),
    NORMAL("略有疲惫", "轻微影响，属性略微下降"),
    TIRED("相当疲惫", "明显影响，移速和攻击速度下降"),
    SEVERELY_FATIGUED("严重疲惫", "严重影响，可能触发强制休息"),
    EXHAUSTED("精疲力竭", "严重影响，战斗能力大幅下降"),
    CRITICAL("极度危险", "属性大幅下降，可能强制晕倒");

    private final String displayName;
    private final String description;

    FatigueLevel(String displayName, String description) {
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
     * 获取疲劳等级的颜色代码
     */
    public String colorCode() {
        return switch (this) {
            case FRESH -> "a";
            case NORMAL -> "e";
            case TIRED -> "6";
            case SEVERELY_FATIGUED -> "6";
            case EXHAUSTED -> "c";
            case CRITICAL -> "4";
        };
    }

    /**
     * 获取疲劳等级的 ActionBar 颜色代码
     */
    public String actionBarColor() {
        return switch (this) {
            case FRESH -> "\\u00a7a";
            case NORMAL -> "\\u00a7e";
            case TIRED -> "\\u00a76";
            case SEVERELY_FATIGUED -> "\\u00a76";
            case EXHAUSTED -> "\\u00a7c";
            case CRITICAL -> "\\u00a74";
        };
    }
}