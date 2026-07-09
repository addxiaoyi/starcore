package dev.starcore.starcore.module.weather.model;

/**
 * 天气类型枚举
 */
public enum WeatherType {
    /** 晴朗 */
    CLEAR("clear", "晴朗", "☀️"),
    /** 小雨 */
    RAIN("rain", "小雨", "🌧️"),
    /** 雷暴 */
    THUNDER("thunder", "雷暴", "⛈️"),
    /** 降雪 */
    SNOW("snow", "降雪", "❄️"),
    /** 暴风雨 */
    STORM("storm", "暴风雨", "🌪️");

    private final String id;
    private final String displayName;
    private final String icon;

    WeatherType(String id, String displayName, String icon) {
        this.id = id;
        this.displayName = displayName;
        this.icon = icon;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getIcon() {
        return icon;
    }

    public static WeatherType fromId(String id) {
        for (WeatherType type : values()) {
            if (type.id.equalsIgnoreCase(id)) {
                return type;
            }
        }
        return CLEAR;
    }

    public static WeatherType fromName(String name) {
        try {
            return valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return fromId(name);
        }
    }
}
