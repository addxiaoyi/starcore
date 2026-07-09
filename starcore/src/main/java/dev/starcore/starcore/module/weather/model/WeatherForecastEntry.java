package dev.starcore.starcore.module.weather.model;

import java.util.Map;
import java.util.Objects;

/**
 * 天气预报条目数据模型
 */
public final class WeatherForecastEntry {

    private final long timestamp;
    private final WeatherType weather;
    private final Map<WeatherType, Double> probabilities;
    private final String description;

    public WeatherForecastEntry(
            long timestamp,
            WeatherType weather,
            Map<WeatherType, Double> probabilities,
            String description) {
        this.timestamp = timestamp;
        this.weather = Objects.requireNonNull(weather, "weather");
        this.probabilities = Map.copyOf(probabilities);
        this.description = description != null ? description : weather.getDisplayName();
    }

    public long getTimestamp() {
        return timestamp;
    }

    public WeatherType getWeather() {
        return weather;
    }

    public Map<WeatherType, Double> getProbabilities() {
        return probabilities;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 获取天气图标
     */
    public String getIcon() {
        return weather.getIcon();
    }

    /**
     * 获取指定天气的概率
     */
    public double getProbability(WeatherType type) {
        return probabilities.getOrDefault(type, 0.0);
    }

    /**
     * 获取最可能的天气
     */
    public WeatherType getMostLikelyWeather() {
        return probabilities.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(WeatherType.CLEAR);
    }

    /**
     * 格式化时间显示
     */
    public String getFormattedDate() {
        java.time.Instant instant = java.time.Instant.ofEpochMilli(timestamp);
        java.time.LocalDateTime dateTime = java.time.LocalDateTime.ofInstant(
            instant, java.time.ZoneId.systemDefault()
        );
        java.time.format.DateTimeFormatter formatter =
            java.time.format.DateTimeFormatter.ofPattern("MM/dd");
        return dateTime.format(formatter);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WeatherForecastEntry that = (WeatherForecastEntry) o;
        return timestamp == that.timestamp;
    }

    @Override
    public int hashCode() {
        return Objects.hash(timestamp);
    }
}
