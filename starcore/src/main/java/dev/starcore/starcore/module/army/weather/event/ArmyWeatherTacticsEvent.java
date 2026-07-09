package dev.starcore.starcore.module.army.weather.event;

import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.weather.model.WeatherType;
import dev.starcore.starcore.module.army.weather.model.WeatherTacticsEffect;

/**
 * 军队天气战术事件
 * 当军队受到天气战术效果影响时触发
 */
public record ArmyWeatherTacticsEvent(
    NationId nationId,
    WeatherType weather,
    WeatherTacticsEffect effect
) {

    // ==================== 访问方法 ====================

    /**
     * 获取国家ID
     */
    public NationId nationId() {
        return nationId;
    }

    /**
     * 获取天气类型
     */
    public WeatherType weather() {
        return weather;
    }

    /**
     * 获取战术效果
     */
    public WeatherTacticsEffect effect() {
        return effect;
    }

    // ==================== 便捷方法 ====================

    /**
     * 检查是否获得正向加成
     */
    public boolean hasPositiveBonus() {
        return effect.hasPositiveBonus();
    }

    /**
     * 检查是否受到惩罚
     */
    public boolean hasPenalty() {
        return effect.hasPenalty();
    }

    /**
     * 获取天气描述
     */
    public String getWeatherDescription() {
        return weather.getDisplayName();
    }

    /**
     * 获取战术评级
     */
    public String getTacticsRating() {
        return effect.getTacticsRating();
    }

    @Override
    public String toString() {
        return String.format("ArmyWeatherTacticsEvent{nation=%s, weather=%s, rating=%s}",
            nationId, weather.getDisplayName(), getTacticsRating());
    }
}