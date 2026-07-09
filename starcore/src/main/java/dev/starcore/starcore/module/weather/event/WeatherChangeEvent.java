package dev.starcore.starcore.module.weather.event;

import dev.starcore.starcore.module.weather.model.WeatherType;
import dev.starcore.starcore.module.nation.model.NationId;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * 天气变化事件
 */
public class WeatherChangeEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final NationId nationId;
    private final String worldName;
    private final WeatherType previousWeather;
    private final WeatherType newWeather;
    private final WeatherChangeCause cause;

    public WeatherChangeEvent(NationId nationId, String worldName, WeatherType previousWeather,
                              WeatherType newWeather, WeatherChangeCause cause) {
        this.nationId = nationId;
        this.worldName = worldName;
        this.previousWeather = previousWeather;
        this.newWeather = newWeather;
        this.cause = cause;
    }

    public NationId nationId() {
        return nationId;
    }

    public String worldName() {
        return worldName;
    }

    public WeatherType previousWeather() {
        return previousWeather;
    }

    public WeatherType newWeather() {
        return newWeather;
    }

    public WeatherChangeCause cause() {
        return cause;
    }

    /**
     * 获取影响描述
     */
    public String getImpactDescription() {
        StringBuilder sb = new StringBuilder();

        // 天气类型影响
        switch (newWeather) {
            case CLEAR -> sb.append("晴朗天气，资源产出正常");
            case RAIN -> sb.append("雨天，农业产出增加");
            case THUNDER -> sb.append("雷暴天气，能源产出减少");
            case SNOW -> sb.append("雪天，资源产出大幅降低");
            case STORM -> sb.append("暴风雨，严重影响资源产出");
        }

        // 特殊效果
        switch (newWeather) {
            case THUNDER, STORM -> sb.append("，需要注意防雷");
            case SNOW -> sb.append("，注意保暖");
            default -> {}
        }

        return sb.toString();
    }

    /**
     * 检查是否是政策导致的天气变化
     */
    public boolean isPolicyDriven() {
        return cause == WeatherChangeCause.POLICY_EFFECT;
    }

    /**
     * 检查是否是自然天气变化
     */
    public boolean isNatural() {
        return cause == WeatherChangeCause.NATURAL;
    }

    /**
     * 检查是否是人工控制的天气变化
     */
    public boolean isControlled() {
        return cause == WeatherChangeCause.NATION_CONTROL;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
