package dev.starcore.starcore.module.army.weather.model;

import dev.starcore.starcore.module.weather.model.WeatherType;

/**
 * 天气战术类型
 */
public enum WeatherTacticType {
    /** 伏击 - 利用恶劣天气发动突袭 */
    AMBUSH("ambush", "伏击战术", "利用恶劣天气掩护发动突然袭击"),

    /** 防御 - 恶劣天气下加强防守 */
    DEFENSIVE("defensive", "防御战术", "在恶劣天气下加强阵地防御"),

    /** 消耗 - 利用天气消耗敌军 */
    ATTRITION("attrition", "消耗战术", "利用恶劣天气持续消耗敌军实力"),

    /** 撤退 - 天气不利时安全撤退 */
    RETREAT("retreat", "撤退战术", "在不利天气下有序撤退保存实力"),

    /** 追击 - 趁恶劣天气追击溃败敌军 */
    PURSUIT("pursuit", "追击战术", "趁敌军撤退时在恶劣天气下追击"),

    /** 增援 - 良好天气下快速增援 */
    REINFORCE("reinforce", "增援战术", "在良好天气下快速调集增援部队");

    private final String id;
    private final String displayName;
    private final String description;

    WeatherTacticType(String id, String displayName, String description) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    /**
     * 获取战术适用的天气类型
     * @return 适用的天气类型列表
     */
    public WeatherType[] getEffectiveWeather() {
        return switch (this) {
            case AMBUSH -> new WeatherType[]{WeatherType.THUNDER, WeatherType.STORM, WeatherType.RAIN};
            case DEFENSIVE -> new WeatherType[]{WeatherType.SNOW, WeatherType.STORM, WeatherType.THUNDER};
            case ATTRITION -> new WeatherType[]{WeatherType.THUNDER, WeatherType.STORM, WeatherType.SNOW};
            case RETREAT -> new WeatherType[]{WeatherType.THUNDER, WeatherType.STORM};
            case PURSUIT -> new WeatherType[]{WeatherType.RAIN, WeatherType.THUNDER, WeatherType.STORM};
            case REINFORCE -> new WeatherType[]{WeatherType.CLEAR};
        };
    }

    /**
     * 获取战术的基础成功率
     */
    public double getBaseSuccessRate() {
        return switch (this) {
            case AMBUSH -> 0.6;
            case DEFENSIVE -> 0.85;
            case ATTRITION -> 0.7;
            case RETREAT -> 0.9;
            case PURSUIT -> 0.75;
            case REINFORCE -> 0.95;
        };
    }

    /**
     * 获取战术的冷却时间（秒）
     */
    public int getCooldownSeconds() {
        return switch (this) {
            case AMBUSH -> 300;
            case DEFENSIVE -> 60;
            case ATTRITION -> 180;
            case RETREAT -> 120;
            case PURSUIT -> 240;
            case REINFORCE -> 180;
        };
    }

    public static WeatherTacticType fromId(String id) {
        for (WeatherTacticType type : values()) {
            if (type.id.equalsIgnoreCase(id)) {
                return type;
            }
        }
        return null;
    }
}
