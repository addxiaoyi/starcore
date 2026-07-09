package dev.starcore.starcore.module.weather.event;

import dev.starcore.starcore.module.weather.model.WeatherType;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Map;

/**
 * 天气资源效果事件
 */
public class WeatherResourceEffectEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final dev.starcore.starcore.module.nation.model.NationId nationId;
    private final String worldName;
    private final WeatherType weather;
    private final Map<String, Double> modifiers;

    public WeatherResourceEffectEvent(dev.starcore.starcore.module.nation.model.NationId nationId,
                                      String worldName, WeatherType weather, Map<String, Double> modifiers) {
        this.nationId = nationId;
        this.worldName = worldName;
        this.weather = weather;
        this.modifiers = modifiers;
    }

    public dev.starcore.starcore.module.nation.model.NationId nationId() {
        return nationId;
    }

    public String worldName() {
        return worldName;
    }

    public WeatherType weather() {
        return weather;
    }

    public Map<String, Double> modifiers() {
        return modifiers;
    }

    /**
     * 获取资源类型列表
     */
    public java.util.Set<String> getAffectedResources() {
        return modifiers.entrySet().stream()
            .filter(e -> e.getValue() != 1.0)
            .map(Map.Entry::getKey)
            .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * 获取增强的资源类型
     */
    public java.util.Set<String> getBoostedResources() {
        return modifiers.entrySet().stream()
            .filter(e -> e.getValue() > 1.0)
            .map(Map.Entry::getKey)
            .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * 获取削弱的资源类型
     */
    public java.util.Set<String> getReducedResources() {
        return modifiers.entrySet().stream()
            .filter(e -> e.getValue() < 1.0)
            .map(Map.Entry::getKey)
            .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * 获取指定资源的修改值
     */
    public double getModifier(String resourceType) {
        return modifiers.getOrDefault(resourceType.toLowerCase(), 1.0);
    }

    /**
     * 格式化为百分比显示
     */
    public String formatAsPercentage(String resourceType) {
        double modifier = getModifier(resourceType);
        int percentage = (int) Math.round(modifier * 100);
        return percentage + "%";
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
