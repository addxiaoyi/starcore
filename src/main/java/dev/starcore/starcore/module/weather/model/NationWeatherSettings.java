package dev.starcore.starcore.module.weather.model;

import dev.starcore.starcore.module.nation.model.NationId;

import java.util.Map;
import java.util.Objects;

/**
 * 国家天气设置数据模型
 */
public final class NationWeatherSettings {

    private final NationId nationId;
    private WeatherType currentWeather;
    private boolean autoWeather;
    private NationWeatherPermission permission;

    // 冷却追踪
    private long lastWeatherChangeTime;
    private String lastControlledWorld;

    // 资源影响追踪
    private Map<String, Double> lastResourceModifiers;

    public NationWeatherSettings(
            NationId nationId,
            WeatherType currentWeather,
            boolean autoWeather,
            NationWeatherPermission permission) {
        this.nationId = Objects.requireNonNull(nationId, "nationId");
        this.currentWeather = currentWeather != null ? currentWeather : WeatherType.CLEAR;
        this.autoWeather = autoWeather;
        this.permission = permission != null ? permission : NationWeatherPermission.NONE;
        this.lastWeatherChangeTime = 0L;
        this.lastControlledWorld = null;
    }

    public NationId nationId() {
        return nationId;
    }

    public WeatherType getCurrentWeather() {
        return currentWeather;
    }

    public void setCurrentWeather(WeatherType currentWeather) {
        this.currentWeather = currentWeather != null ? currentWeather : WeatherType.CLEAR;
        this.lastWeatherChangeTime = System.currentTimeMillis();
    }

    public boolean isAutoWeather() {
        return autoWeather;
    }

    public void setAutoWeather(boolean autoWeather) {
        this.autoWeather = autoWeather;
    }

    public NationWeatherPermission getPermission() {
        return permission;
    }

    public int getWeatherDurationMinutes() {
        return (int) (lastWeatherChangeTime > 0 ? (System.currentTimeMillis() - lastWeatherChangeTime) / 60000 : 0);
    }

    public void setPermission(NationWeatherPermission permission) {
        this.permission = permission != null ? permission : NationWeatherPermission.NONE;
    }

    public long getLastWeatherChangeTime() {
        return lastWeatherChangeTime;
    }

    public void setLastWeatherChangeTime(long time) {
        this.lastWeatherChangeTime = time;
    }

    public String getLastControlledWorld() {
        return lastControlledWorld;
    }

    public void setLastControlledWorld(String world) {
        this.lastControlledWorld = world;
    }

    public Map<String, Double> getLastResourceModifiers() {
        return lastResourceModifiers;
    }

    public void setLastResourceModifiers(Map<String, Double> modifiers) {
        this.lastResourceModifiers = modifiers;
    }

    /**
     * 创建副本
     */
    public NationWeatherSettings copy() {
        NationWeatherSettings copy = new NationWeatherSettings(
            nationId,
            currentWeather,
            autoWeather,
            permission
        );
        copy.setLastWeatherChangeTime(lastWeatherChangeTime);
        copy.setLastControlledWorld(lastControlledWorld);
        copy.setLastResourceModifiers(lastResourceModifiers);
        return copy;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NationWeatherSettings that = (NationWeatherSettings) o;
        return nationId.equals(that.nationId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nationId);
    }
}
