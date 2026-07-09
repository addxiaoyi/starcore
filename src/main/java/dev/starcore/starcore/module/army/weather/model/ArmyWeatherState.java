package dev.starcore.starcore.module.army.weather.model;

import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.weather.model.WeatherType;

import java.util.Objects;
import java.util.UUID;

/**
 * 军队天气状态
 * 记录军队当前的天气状态和适应信息
 */
public final class ArmyWeatherState {
    private final UUID armyId;
    private final NationId nationId;
    private final String unitType;
    private WeatherType currentWeather;
    private WeatherType previousWeather;
    private long lastWeatherChangeTime;
    private double accumulatedAdvantage;

    public ArmyWeatherState(
        UUID armyId,
        NationId nationId,
        String unitType,
        WeatherType currentWeather
    ) {
        this.armyId = Objects.requireNonNull(armyId, "armyId");
        this.nationId = Objects.requireNonNull(nationId, "nationId");
        this.unitType = Objects.requireNonNull(unitType, "unitType");
        this.currentWeather = Objects.requireNonNull(currentWeather, "currentWeather");
        this.previousWeather = currentWeather;
        this.lastWeatherChangeTime = System.currentTimeMillis();
        this.accumulatedAdvantage = 0.0;
    }

    // ==================== Getters ====================

    public UUID armyId() {
        return armyId;
    }

    public NationId nationId() {
        return nationId;
    }

    public String unitType() {
        return unitType;
    }

    public WeatherType currentWeather() {
        return currentWeather;
    }

    public WeatherType previousWeather() {
        return previousWeather;
    }

    public long lastWeatherChangeTime() {
        return lastWeatherChangeTime;
    }

    public double accumulatedAdvantage() {
        return accumulatedAdvantage;
    }

    // ==================== Setters / Mutations ====================

    public void updateWeather(WeatherType newWeather) {
        if (newWeather != currentWeather) {
            this.previousWeather = currentWeather;
            this.currentWeather = newWeather;
            this.lastWeatherChangeTime = System.currentTimeMillis();
        }
    }

    public void addAdvantage(double amount) {
        this.accumulatedAdvantage = Math.max(-100.0, Math.min(100.0, accumulatedAdvantage + amount));
    }

    public void resetAdvantage() {
        this.accumulatedAdvantage = 0.0;
    }

    // ==================== 计算方法 ====================

    /**
     * 获取在当前天气下的适应性等级
     */
    public double getAdaptationLevel() {
        return switch (unitType.toLowerCase()) {
            case "infantry" -> getInfantryAdaptation(currentWeather);
            case "cavalry" -> getCavalryAdaptation(currentWeather);
            case "archer" -> getArcherAdaptation(currentWeather);
            case "siege" -> getSiegeAdaptation(currentWeather);
            case "defensive" -> getDefensiveAdaptation(currentWeather);
            default -> 1.0;
        };
    }

    private double getInfantryAdaptation(WeatherType weather) {
        return switch (weather) {
            case CLEAR -> 1.0;
            case RAIN -> 0.9;
            case THUNDER -> 0.8;
            case SNOW -> 0.7;
            case STORM -> 0.6;
        };
    }

    private double getCavalryAdaptation(WeatherType weather) {
        return switch (weather) {
            case CLEAR -> 1.0;
            case RAIN -> 0.6;
            case THUNDER -> 0.3;
            case SNOW -> 0.0;
            case STORM -> 0.0;
        };
    }

    private double getArcherAdaptation(WeatherType weather) {
        return switch (weather) {
            case CLEAR -> 1.2;  // 远程优势
            case RAIN -> 0.4;
            case THUNDER -> 0.1;
            case SNOW -> 0.2;
            case STORM -> 0.0;
        };
    }

    private double getSiegeAdaptation(WeatherType weather) {
        return switch (weather) {
            case CLEAR -> 1.0;
            case RAIN -> 0.3;
            case THUNDER -> 0.1;
            case SNOW -> 0.0;
            case STORM -> 0.0;
        };
    }

    private double getDefensiveAdaptation(WeatherType weather) {
        return switch (weather) {
            case CLEAR -> 0.9;
            case RAIN -> 1.1;
            case THUNDER -> 1.3;
            case SNOW -> 1.2;
            case STORM -> 1.5;
        };
    }

    /**
     * 检查天气是否发生了变化
     */
    public boolean hasWeatherChanged() {
        return currentWeather != previousWeather;
    }

    /**
     * 获取天气变化持续时间（毫秒）
     */
    public long getWeatherDuration() {
        return System.currentTimeMillis() - lastWeatherChangeTime;
    }

    // ==================== Object Methods ====================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ArmyWeatherState other)) return false;
        return armyId.equals(other.armyId);
    }

    @Override
    public int hashCode() {
        return armyId.hashCode();
    }

    @Override
    public String toString() {
        return String.format("ArmyWeatherState{army=%s, unit=%s, weather=%s, advantage=%.1f}",
            armyId.toString().substring(0, 8),
            unitType,
            currentWeather.getDisplayName(),
            accumulatedAdvantage);
    }
}