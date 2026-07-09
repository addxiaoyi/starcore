package dev.starcore.starcore.module.prosperity;

import dev.starcore.starcore.module.nation.model.NationId;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 繁荣度配置
 * 定义繁荣度系统的可配置参数
 */
public record ProsperityConfig(
    double initialProsperity,
    double maxProsperity,
    double minProsperity,
    double dailyDecayRate,
    double decayThreshold,
    int prosperityToLevel10,
    double activityBoostAmount,
    double buildingBoostAmount,
    double tradeBoostAmount,
    double warPenaltyMultiplier,
    double inactivityPenaltyMultiplier,
    double taxBonusPerLevel,
    double resourceBonusPerLevel,
    long decayCheckIntervalTicks
) {
    /**
     * 创建默认配置
     */
    public static ProsperityConfig defaultConfig() {
        return new ProsperityConfig(
            50.0,           // initialProsperity
            100.0,          // maxProsperity
            0.0,            // minProsperity
            0.5,            // dailyDecayRate (每天衰减0.5%)
            30.0,           // decayThreshold (低于30%不衰减)
            100,            // prosperityToLevel10 (int)
            2.0,            // activityBoostAmount
            3.0,            // buildingBoostAmount
            1.5,            // tradeBoostAmount
            5.0,            // warPenaltyMultiplier
            2.0,            // inactivityPenaltyMultiplier
            0.02,           // taxBonusPerLevel (每级+2%)
            0.05,           // resourceBonusPerLevel (每级+5%)
            72000L          // decayCheckIntervalTicks (每小时检查一次)
        );
    }

    /**
     * 从配置文件加载
     */
    public static ProsperityConfig fromConfig(org.bukkit.configuration.ConfigurationSection config) {
        if (config == null) {
            return defaultConfig();
        }
        return new ProsperityConfig(
            config.getDouble("initial-prosperity", 50.0),
            config.getDouble("max-prosperity", 100.0),
            config.getDouble("min-prosperity", 0.0),
            config.getDouble("daily-decay-rate", 0.5),
            config.getDouble("decay-threshold", 30.0),
            (int) config.getDouble("prosperity-to-level-10", 100.0),
            config.getDouble("activity-boost-amount", 2.0),
            config.getDouble("building-boost-amount", 3.0),
            config.getDouble("trade-boost-amount", 1.5),
            config.getDouble("war-penalty-multiplier", 5.0),
            config.getDouble("inactivity-penalty-multiplier", 2.0),
            config.getDouble("tax-bonus-per-level", 0.02),
            config.getDouble("resource-bonus-per-level", 0.05),
            config.getLong("decay-check-interval-ticks", 72000L)
        );
    }

    /**
     * 根据繁荣度值计算等级 (1-10)
     */
    public int calculateLevel(double prosperity) {
        if (prosperity <= 0) return 1;
        double level = (prosperity / (double) prosperityToLevel10) * 10.0;
        return Math.min(10, Math.max(1, (int) Math.ceil(level)));
    }
}
