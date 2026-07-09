package dev.starcore.starcore.module.army.weather.event;

import dev.starcore.starcore.module.weather.model.WeatherType;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * 天气战术推荐事件
 * 当天气变化时触发，提示玩家可用的战术
 */
public class WeatherTacticRecommendationEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final UUID nationId;
    private final UUID armyId;
    private final WeatherType previousWeather;
    private final WeatherType newWeather;
    private final String recommendedTactics;
    private final double effectiveness;

    public WeatherTacticRecommendationEvent(
        UUID nationId,
        UUID armyId,
        WeatherType previousWeather,
        WeatherType newWeather,
        String recommendedTactics,
        double effectiveness
    ) {
        this.nationId = nationId;
        this.armyId = armyId;
        this.previousWeather = previousWeather;
        this.newWeather = newWeather;
        this.recommendedTactics = recommendedTactics;
        this.effectiveness = effectiveness;
    }

    public UUID getNationId() {
        return nationId;
    }

    public UUID getArmyId() {
        return armyId;
    }

    public WeatherType getPreviousWeather() {
        return previousWeather;
    }

    public WeatherType getNewWeather() {
        return newWeather;
    }

    public String getRecommendedTactics() {
        return recommendedTactics;
    }

    public double getEffectiveness() {
        return effectiveness;
    }

    /**
     * 检查天气变化是否显著
     */
    public boolean isSignificantChange() {
        // 晴天到恶劣天气是显著变化
        if (previousWeather == WeatherType.CLEAR && newWeather != WeatherType.CLEAR) {
            return true;
        }
        // 暴风雨相关天气变化
        if (previousWeather == WeatherType.STORM || newWeather == WeatherType.STORM) {
            return true;
        }
        // 雪天与其他天气切换
        if (previousWeather == WeatherType.SNOW || newWeather == WeatherType.SNOW) {
            return true;
        }
        return false;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
