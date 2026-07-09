package dev.starcore.starcore.module.army.weather.model;

import dev.starcore.starcore.module.weather.model.WeatherType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 天气战术效果计算器
 * 根据天气类型计算对战斗的加成和减成
 */
public final class WeatherBattleModifier {

    /**
     * 天气对攻击力的修正 - 使用不可变 Map
     */
    private static final Map<WeatherType, Double> ATTACK_MODIFIERS = Map.of(
        WeatherType.CLEAR, 1.0,
        WeatherType.RAIN, 0.85,
        WeatherType.THUNDER, 0.6,
        WeatherType.SNOW, 0.5,
        WeatherType.STORM, 0.4
    );

    /**
     * 天气对防御力的修正 - 使用不可变 Map
     */
    private static final Map<WeatherType, Double> DEFENSE_MODIFIERS = Map.of(
        WeatherType.CLEAR, 1.0,
        WeatherType.RAIN, 1.1,
        WeatherType.THUNDER, 1.2,
        WeatherType.SNOW, 1.3,
        WeatherType.STORM, 0.9
    );

    /**
     * 天气对士气的修正 - 使用不可变 Map
     */
    private static final Map<WeatherType, Double> MORALE_MODIFIERS = Map.of(
        WeatherType.CLEAR, 0.0,
        WeatherType.RAIN, -5.0,
        WeatherType.THUNDER, -15.0,
        WeatherType.SNOW, -10.0,
        WeatherType.STORM, -20.0
    );

    /**
     * 天气对补给消耗的修正 - 使用不可变 Map
     */
    private static final Map<WeatherType, Double> SUPPLY_MODIFIERS = Map.of(
        WeatherType.CLEAR, 1.0,
        WeatherType.RAIN, 0.8,
        WeatherType.THUNDER, 0.6,
        WeatherType.SNOW, 1.5,
        WeatherType.STORM, 2.0
    );

    /**
     * 天气对视野的修正（影响命中率）- 使用不可变 Map
     */
    private static final Map<WeatherType, Double> VISION_MODIFIERS = Map.of(
        WeatherType.CLEAR, 1.0,
        WeatherType.RAIN, 0.7,
        WeatherType.THUNDER, 0.4,
        WeatherType.SNOW, 0.6,
        WeatherType.STORM, 0.3
    );

    private WeatherBattleModifier() {
    }

    /**
     * 获取攻击修正
     */
    public static double getAttackModifier(WeatherType weather) {
        return ATTACK_MODIFIERS.getOrDefault(weather, 1.0);
    }

    /**
     * 获取防御修正
     */
    public static double getDefenseModifier(WeatherType weather) {
        return DEFENSE_MODIFIERS.getOrDefault(weather, 1.0);
    }

    /**
     * 获取士气修正
     */
    public static double getMoraleModifier(WeatherType weather) {
        return MORALE_MODIFIERS.getOrDefault(weather, 0.0);
    }

    /**
     * 获取补给消耗修正
     */
    public static double getSupplyModifier(WeatherType weather) {
        return SUPPLY_MODIFIERS.getOrDefault(weather, 1.0);
    }

    /**
     * 获取视野修正
     */
    public static double getVisionModifier(WeatherType weather) {
        return VISION_MODIFIERS.getOrDefault(weather, 1.0);
    }

    /**
     * 计算天气对战斗的综合影响
     */
    public static WeatherBattleImpact calculateImpact(WeatherType weather) {
        return new WeatherBattleImpact(
            getAttackModifier(weather),
            getDefenseModifier(weather),
            getMoraleModifier(weather),
            getSupplyModifier(weather),
            getVisionModifier(weather)
        );
    }

    /**
     * 计算战术配合天气的加成
     */
    public static double calculateTacticWeatherBonus(WeatherTacticType tactic, WeatherType currentWeather) {
        var effectiveWeather = tactic.getEffectiveWeather();

        for (WeatherType effective : effectiveWeather) {
            if (effective == currentWeather) {
                // 战术在合适的天气下获得额外加成
                return switch (tactic) {
                    case AMBUSH -> 1.3;      // 伏击配合恶劣天气效果极佳
                    case DEFENSIVE -> 1.2;  // 防御配合恶劣天气更加坚固
                    case ATTRITION -> 1.15; // 消耗战术
                    case RETREAT -> 1.25;   // 撤退需要恶劣天气掩护
                    case PURSUIT -> 1.1;    // 追击需要一定恶劣天气
                    case REINFORCE -> 1.0;  // 增援不受影响
                };
            }
        }

        // 战术在不合适的天气下获得减成
        return 0.5;
    }

    /**
     * 获取天气战斗影响数据
     */
    public record WeatherBattleImpact(
        double attackModifier,
        double defenseModifier,
        double moraleModifier,
        double supplyModifier,
        double visionModifier
    ) {
        /**
         * 获取综合战斗修正（攻击和防御的平均）
         */
        public double getCombatModifier() {
            return (attackModifier + defenseModifier) / 2.0;
        }

        /**
         * 检查是否有利于防守
         */
        public boolean isDefenderFriendly() {
            return defenseModifier > attackModifier;
        }

        /**
         * 获取描述文本
         */
        public String getDescription() {
            StringBuilder sb = new StringBuilder();
            sb.append("天气影响: ");

            if (attackModifier < 1.0) {
                sb.append(String.format("攻击%.0f%% ", attackModifier * 100));
            } else if (attackModifier > 1.0) {
                sb.append(String.format("攻击+%.0f%% ", (attackModifier - 1) * 100));
            }

            if (defenseModifier < 1.0) {
                sb.append(String.format("防御%.0f%% ", defenseModifier * 100));
            } else if (defenseModifier > 1.0) {
                sb.append(String.format("防御+%.0f%% ", (defenseModifier - 1) * 100));
            }

            if (moraleModifier < 0) {
                sb.append(String.format("士气%.0f ", moraleModifier));
            } else if (moraleModifier > 0) {
                sb.append(String.format("士气+%.0f ", moraleModifier));
            }

            return sb.toString();
        }
    }
}
