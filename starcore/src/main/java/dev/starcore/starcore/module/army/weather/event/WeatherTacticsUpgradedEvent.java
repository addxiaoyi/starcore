package dev.starcore.starcore.module.army.weather.event;

import dev.starcore.starcore.module.nation.model.NationId;

/**
 * 天气战术升级事件
 * 当国家升级天气战术时触发
 */
public record WeatherTacticsUpgradedEvent(
    NationId nationId,
    String tacticsType,
    int newLevel
) {

    // ==================== 访问方法 ====================

    /**
     * 获取国家ID
     */
    public NationId nationId() {
        return nationId;
    }

    /**
     * 获取战术类型
     */
    public String tacticsType() {
        return tacticsType;
    }

    /**
     * 获取新等级
     */
    public int newLevel() {
        return newLevel;
    }

    // ==================== 便捷方法 ====================

    /**
     * 获取战术显示名称
     */
    public String getTacticsDisplayName() {
        return switch (tacticsType) {
            case "weather_mastery" -> "天气掌控";
            case "rain_warfare" -> "雨天战术";
            case "thunder_tactics" -> "雷暴战术";
            case "snow_operations" -> "雪地作战";
            case "storm_assault" -> "风暴突击";
            default -> tacticsType;
        };
    }

    /**
     * 检查是否达到最大等级
     */
    public boolean isMaxLevel() {
        return newLevel >= 5;
    }

    /**
     * 获取升级类型描述
     */
    public String getUpgradeDescription() {
        if (isMaxLevel()) {
            return String.format("%s 已达到最高等级 %d", getTacticsDisplayName(), newLevel);
        }
        return String.format("%s 升级至等级 %d", getTacticsDisplayName(), newLevel);
    }

    /**
     * 获取下一个等级需要的成本
     */
    public double getNextUpgradeCost() {
        if (isMaxLevel()) {
            return -1;
        }
        return 5000.0 * Math.pow(1.5, newLevel);
    }

    @Override
    public String toString() {
        return String.format("WeatherTacticsUpgradedEvent{nation=%s, tactics=%s, level=%d}",
            nationId, tacticsType, newLevel);
    }
}