package dev.starcore.starcore.module.army.weather.model;

import dev.starcore.starcore.module.weather.model.WeatherType;

/**
 * 天气战术加成
 * 定义国家在特定天气下获得的战术加成
 */
public final class WeatherTacticsBoost {

    private final WeatherType weather;
    private final double attackMultiplier;
    private final double defenseMultiplier;
    private final double movementMultiplier;
    private final double moraleBonus;
    private final String tacticsName;
    private final String description;

    public WeatherTacticsBoost(
        WeatherType weather,
        double attackMultiplier,
        double defenseMultiplier,
        double movementMultiplier,
        double moraleBonus,
        String tacticsName,
        String description
    ) {
        this.weather = weather;
        this.attackMultiplier = clamp(attackMultiplier, 0.1, 3.0);
        this.defenseMultiplier = clamp(defenseMultiplier, 0.1, 3.0);
        this.movementMultiplier = clamp(movementMultiplier, 0.1, 3.0);
        this.moraleBonus = clamp(moraleBonus, 0.0, 2.0);
        this.tacticsName = tacticsName;
        this.description = description;
    }

    // ==================== Getters ====================

    public WeatherType weather() {
        return weather;
    }

    public double attackMultiplier() {
        return attackMultiplier;
    }

    public double defenseMultiplier() {
        return defenseMultiplier;
    }

    public double movementMultiplier() {
        return movementMultiplier;
    }

    public double moraleBonus() {
        return moraleBonus;
    }

    public String tacticsName() {
        return tacticsName;
    }

    public String description() {
        return description;
    }

    // ==================== 计算方法 ====================

    /**
     * 计算综合战斗加成
     */
    public double combatBonus() {
        return (attackMultiplier + defenseMultiplier) / 2.0;
    }

    /**
     * 获取战术强度等级 (0-5)
     */
    public int getTacticsTier() {
        double avg = (attackMultiplier + defenseMultiplier + movementMultiplier) / 3.0;
        if (avg >= 2.5) return 5;
        if (avg >= 2.0) return 4;
        if (avg >= 1.5) return 3;
        if (avg >= 1.0) return 2;
        if (avg >= 0.5) return 1;
        return 0;
    }

    /**
     * 获取显示格式的加成信息
     */
    public String getFormattedBonus() {
        return String.format(
            "%s [%s]: ATK %.0f%% | DEF %.0f%% | MOV %.0f%% | 士气 %.0f%%",
            weather.getDisplayName(),
            tacticsName,
            attackMultiplier * 100,
            defenseMultiplier * 100,
            movementMultiplier * 100,
            moraleBonus * 100
        );
    }

    // ==================== 静态工厂方法 ====================

    /**
     * 创建默认加成
     */
    public static WeatherTacticsBoost defaultBoost() {
        return new WeatherTacticsBoost(
            dev.starcore.starcore.module.weather.model.WeatherType.CLEAR,
            1.0, 1.0, 1.0, 1.0,
            "默认战术",
            "无特殊加成"
        );
    }

    /**
     * 创建晴天加成
     */
    public static WeatherTacticsBoost clearBoost() {
        return new WeatherTacticsBoost(
            dev.starcore.starcore.module.weather.model.WeatherType.CLEAR,
            1.0, 1.0, 1.0, 1.0,
            "晴天作战",
            "所有兵种正常作战，弓箭手获得额外远程优势"
        );
    }

    /**
     * 创建雨天加成
     */
    public static WeatherTacticsBoost rainBoost() {
        return new WeatherTacticsBoost(
            dev.starcore.starcore.module.weather.model.WeatherType.RAIN,
            0.8, 1.2, 0.7, 1.0,
            "雨天战术",
            "弓箭手和骑兵能力下降，步兵获得防守优势"
        );
    }

    /**
     * 创建雷暴加成
     */
    public static WeatherTacticsBoost thunderBoost() {
        return new WeatherTacticsBoost(
            dev.starcore.starcore.module.weather.model.WeatherType.THUNDER,
            0.6, 1.5, 0.5, 0.9,
            "雷暴战术",
            "骑兵最危险，步兵和守军获得地形优势"
        );
    }

    /**
     * 创建雪天加成
     */
    public static WeatherTacticsBoost snowBoost() {
        return new WeatherTacticsBoost(
            dev.starcore.starcore.module.weather.model.WeatherType.SNOW,
            0.5, 1.3, 0.3, 0.8,
            "雪地作战",
            "骑兵完全无法移动，步兵勉强维持"
        );
    }

    /**
     * 创建暴风雨加成
     */
    public static WeatherTacticsBoost stormBoost() {
        return new WeatherTacticsBoost(
            dev.starcore.starcore.module.weather.model.WeatherType.STORM,
            0.4, 1.8, 0.2, 0.7,
            "风暴突击",
            "所有兵种受限，守军获得最大优势"
        );
    }

    // ==================== 辅助方法 ====================

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    public String toString() {
        return getFormattedBonus();
    }
}