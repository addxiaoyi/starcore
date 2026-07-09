package dev.starcore.starcore.module.weather.model;

import dev.starcore.starcore.module.nation.model.NationId;

import java.util.Objects;

/**
 * 世界天气状态数据模型
 */
public final class WorldWeatherState {

    private final String worldName;
    private WeatherType currentWeather;
    private long lastWeatherChange;
    private int weatherDurationMinutes;
    private NationId controlledByNation;

    // 天气历史记录
    private WeatherType previousWeather;
    private long totalWeatherChanges;

    public WorldWeatherState(
            String worldName,
            WeatherType currentWeather,
            long lastWeatherChange,
            int weatherDurationMinutes,
            NationId controlledByNation) {
        this.worldName = Objects.requireNonNull(worldName, "worldName");
        this.currentWeather = currentWeather != null ? currentWeather : WeatherType.CLEAR;
        this.lastWeatherChange = lastWeatherChange;
        this.weatherDurationMinutes = weatherDurationMinutes;
        this.controlledByNation = controlledByNation;
        this.previousWeather = null;
        this.totalWeatherChanges = 0;
    }

    public String getWorldName() {
        return worldName;
    }

    public WeatherType getCurrentWeather() {
        return currentWeather;
    }

    public void setCurrentWeather(WeatherType currentWeather) {
        this.previousWeather = this.currentWeather;
        this.currentWeather = currentWeather != null ? currentWeather : WeatherType.CLEAR;
        this.lastWeatherChange = System.currentTimeMillis();
        this.totalWeatherChanges++;
    }

    public long getLastWeatherChange() {
        return lastWeatherChange;
    }

    public void setLastWeatherChange(long timestamp) {
        this.lastWeatherChange = timestamp;
    }

    public int getWeatherDurationMinutes() {
        return weatherDurationMinutes;
    }

    public void setWeatherDurationMinutes(int minutes) {
        this.weatherDurationMinutes = minutes;
    }

    public NationId getControlledByNation() {
        return controlledByNation;
    }

    public void setControlledByNation(NationId nationId) {
        this.controlledByNation = nationId;
    }

    public WeatherType getPreviousWeather() {
        return previousWeather;
    }

    public long getTotalWeatherChanges() {
        return totalWeatherChanges;
    }

    /**
     * 检查是否正在被国家控制
     */
    public boolean isControlled() {
        return controlledByNation != null;
    }

    /**
     * 获取距离上次天气变化的时间（毫秒）
     */
    public long getTimeSinceLastChange() {
        return System.currentTimeMillis() - lastWeatherChange;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WorldWeatherState that = (WorldWeatherState) o;
        return worldName.equals(that.worldName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(worldName);
    }
}
