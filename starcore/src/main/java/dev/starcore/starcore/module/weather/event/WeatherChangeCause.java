package dev.starcore.starcore.module.weather.event;

/**
 * 天气变化原因枚举
 */
public enum WeatherChangeCause {
    /** 自然变化 */
    NATURAL("自然变化"),
    /** 国家控制 */
    NATION_CONTROL("国家控制"),
    /** 政策效果 */
    POLICY_EFFECT("政策效果"),
    /** 科技效果 */
    TECHNOLOGY_EFFECT("科技效果"),
    /** 随机事件 */
    RANDOM_EVENT("随机事件"),
    /** 系统重置 */
    SYSTEM_RESET("系统重置");

    private final String displayName;

    WeatherChangeCause(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
