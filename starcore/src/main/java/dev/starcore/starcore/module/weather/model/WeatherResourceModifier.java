package dev.starcore.starcore.module.weather.model;

import java.util.Map;
import java.util.Objects;

/**
 * 天气资源修改器数据模型
 */
public final class WeatherResourceModifier {

    private final WeatherType weatherType;
    private final Map<String, Double> modifiers;

    // 资源类型常量
    public static final String MINERAL = "mineral";
    public static final String AGRICULTURAL = "agricultural";
    public static final String ENERGY = "energy";
    public static final String LUXURY = "luxury";
    public static final String INDUSTRIAL = "industrial";
    public static final String CHEMICAL = "chemical";
    public static final String STRATEGIC = "strategic";

    public WeatherResourceModifier(WeatherType weatherType, Map<String, Double> modifiers) {
        this.weatherType = Objects.requireNonNull(weatherType, "weatherType");
        this.modifiers = Map.copyOf(modifiers);
    }

    public WeatherType getWeatherType() {
        return weatherType;
    }

    public Map<String, Double> getModifiers() {
        return modifiers;
    }

    /**
     * 获取指定资源类型的修改器
     */
    public double getModifier(String resourceType) {
        return modifiers.getOrDefault(resourceType.toLowerCase(), 1.0);
    }

    /**
     * 应用修改器到基础产量
     */
    public long apply(long baseProduction, String resourceType) {
        double modifier = getModifier(resourceType);
        return Math.round(baseProduction * modifier);
    }

    /**
     * 检查是否对资源有影响
     */
    public boolean affects(String resourceType) {
        double modifier = getModifier(resourceType.toLowerCase());
        return modifier != 1.0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WeatherResourceModifier that = (WeatherResourceModifier) o;
        return weatherType == that.weatherType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(weatherType);
    }
}
