package dev.starcore.starcore.module.weather.model;

import java.util.Map;

/**
 * 天气效果数据模型
 * 记录天气对游戏效果的影响
 *
 * @param type 天气类型
 * @param speedMod 速度修正倍率
 * @param damageMod 伤害修正倍率
 * @param farmingBonus 农业产出加成
 */
public record WeatherEffect(
    WeatherType type,
    double speedMod,
    double damageMod,
    double farmingBonus
) {
    /**
     * 获取所有资源修正
     *
     * @return 资源修正映射
     */
    public Map<String, Double> getResourceModifiers() {
        return Map.of(
            "mineral", speedMod,
            "agricultural", farmingBonus,
            "energy", damageMod,
            "luxury", 1.0
        );
    }

    /**
     * 检查是否适合户外活动
     *
     * @return 是否适合
     */
    public boolean isSuitableForOutdoor() {
        return type == WeatherType.CLEAR || type == WeatherType.RAIN;
    }

    /**
     * 获取战斗修正描述
     *
     * @return 修正描述
     */
    public String getCombatModifierDescription() {
        if (damageMod > 1.0) {
            return "+" + Math.round((damageMod - 1.0) * 100) + "% 伤害风险";
        } else if (damageMod < 1.0) {
            return Math.round((damageMod - 1.0) * 100) + "% 伤害减少";
        }
        return "正常伤害";
    }
}
