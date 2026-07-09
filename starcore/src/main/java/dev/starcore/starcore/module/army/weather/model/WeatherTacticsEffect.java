package dev.starcore.starcore.module.army.weather.model;

import dev.starcore.starcore.module.weather.model.WeatherType;

/**
 * 天气战术效果
 * 记录军队在特定天气下获得的战术效果
 */
public record WeatherTacticsEffect(
    WeatherType weather,
    double attackModifier,
    double defenseModifier,
    double movementModifier,
    double moraleModifier,
    String source
) {

    // ==================== 构造方法 ====================

    public WeatherTacticsEffect {
        attackModifier = clamp(attackModifier, 0.0, 3.0);
        defenseModifier = clamp(defenseModifier, 0.0, 3.0);
        movementModifier = clamp(movementModifier, 0.0, 3.0);
        moraleModifier = clamp(moraleModifier, 0.0, 2.0);
    }

    // ==================== 计算方法 ====================

    /**
     * 获取综合战斗力修正
     */
    public double combatModifier() {
        return (attackModifier + defenseModifier) / 2.0;
    }

    /**
     * 获取移动效率
     */
    public double movementEfficiency() {
        return movementModifier;
    }

    /**
     * 获取士气效率
     */
    public double moraleEfficiency() {
        return moraleModifier;
    }

    /**
     * 获取整体效率（综合所有因素）
     */
    public double overallEfficiency() {
        return combatModifier() * movementModifier * moraleModifier;
    }

    /**
     * 检查是否获得正向加成
     */
    public boolean hasPositiveBonus() {
        return overallEfficiency() > 1.0;
    }

    /**
     * 检查是否受到惩罚
     */
    public boolean hasPenalty() {
        return overallEfficiency() < 1.0;
    }

    /**
     * 获取效率差异（相对于正常值）
     */
    public double efficiencyDelta() {
        return overallEfficiency() - 1.0;
    }

    /**
     * 获取天气描述
     */
    public String getWeatherDescription() {
        return switch (weather) {
            case CLEAR -> "晴朗天气";
            case RAIN -> "雨天";
            case THUNDER -> "雷暴";
            case SNOW -> "雪天";
            case STORM -> "暴风雨";
        };
    }

    /**
     * 获取战术评级
     */
    public String getTacticsRating() {
        double efficiency = overallEfficiency();
        if (efficiency >= 1.5) return "SS";
        if (efficiency >= 1.3) return "S";
        if (efficiency >= 1.1) return "A";
        if (efficiency >= 0.9) return "B";
        if (efficiency >= 0.7) return "C";
        if (efficiency >= 0.5) return "D";
        return "F";
    }

    // ==================== 格式化方法 ====================

    /**
     * 获取详细报告
     */
    public String getDetailedReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 天气战术效果报告 ===\n");
        sb.append("天气: ").append(getWeatherDescription()).append("\n");
        sb.append("战术评级: ").append(getTacticsRating()).append("\n");
        sb.append("攻击力修正: ").append(formatPercent(attackModifier)).append("\n");
        sb.append("防御力修正: ").append(formatPercent(defenseModifier)).append("\n");
        sb.append("移动力修正: ").append(formatPercent(movementModifier)).append("\n");
        sb.append("士气修正: ").append(formatPercent(moraleModifier)).append("\n");
        sb.append("综合效率: ").append(formatPercent(overallEfficiency())).append("\n");
        sb.append("效果来源: ").append(source).append("\n");

        if (hasPositiveBonus()) {
            sb.append("状态: 获得战术优势!\n");
        } else if (hasPenalty()) {
            sb.append("状态: 受到战术惩罚\n");
        } else {
            sb.append("状态: 正常作战\n");
        }

        return sb.toString();
    }

    /**
     * 获取简短报告
     */
    public String getShortReport() {
        return String.format("%s [%s] ATK:%s DEF:%s MOV:%s",
            getWeatherDescription(),
            getTacticsRating(),
            formatPercent(attackModifier),
            formatPercent(defenseModifier),
            formatPercent(movementModifier));
    }

    // ==================== 组合方法 ====================

    /**
     * 叠加另一个战术效果
     */
    public WeatherTacticsEffect combine(WeatherTacticsEffect other) {
        if (other.weather != this.weather) {
            return this;
        }
        return new WeatherTacticsEffect(
            weather,
            this.attackModifier * other.attackModifier,
            this.defenseModifier * other.defenseModifier,
            this.movementModifier * other.movementModifier,
            this.moraleModifier * other.moraleModifier,
            this.source + " + " + other.source
        );
    }

    /**
     * 获取最终战斗修正
     */
    public double getFinalAttackPower(double baseAttack) {
        return baseAttack * attackModifier * moraleModifier;
    }

    /**
     * 获取最终防御修正
     */
    public double getFinalDefensePower(double baseDefense) {
        return baseDefense * defenseModifier;
    }

    // ==================== 静态工厂方法 ====================

    /**
     * 创建默认效果（无加成无惩罚）
     */
    public static WeatherTacticsEffect defaultEffect() {
        return new WeatherTacticsEffect(
            dev.starcore.starcore.module.weather.model.WeatherType.CLEAR,
            1.0, 1.0, 1.0, 1.0,
            "默认"
        );
    }

    /**
     * 创建极端不利效果
     */
    public static WeatherTacticsEffect worstEffect(WeatherType weather) {
        return new WeatherTacticsEffect(
            weather,
            0.2, 0.3, 0.1, 0.5,
            "极端不利"
        );
    }

    /**
     * 创建极端有利效果
     */
    public static WeatherTacticsEffect bestEffect(WeatherType weather) {
        return new WeatherTacticsEffect(
            weather,
            2.0, 2.0, 1.5, 1.5,
            "极端有利"
        );
    }

    // ==================== 辅助方法 ====================

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String formatPercent(double value) {
        return String.format("%.0f%%", value * 100);
    }

    @Override
    public String toString() {
        return getShortReport();
    }
}