package dev.starcore.starcore.module.army.fatigue.model;

import org.bukkit.configuration.ConfigurationSection;

/**
 * 疲劳度配置模型
 * 定义疲劳度系统的所有可配置参数
 */
public record FatigueConfig(
    boolean enabled,                     // 是否启用疲劳系统
    int maxFatigue,                      // 最大疲劳度值
    int recoveryRate,                    // 每分钟恢复的疲劳度
    int combatFatigueRate,              // 每分钟战斗增加的疲劳度
    int gatheringFatigueRate,            // 每分钟采集增加的疲劳度
    int buildingFatigueRate,             // 每分钟建造增加的疲劳度
    int naturalFatigueRate,              // 每分钟自然增加的疲劳度（活动时）
    boolean enableFatigueEffects,        // 是否启用疲劳效果
    boolean enableMovementPenalty,       // 是否启用移动惩罚
    boolean enableCombatPenalty,         // 是否启用战斗惩罚
    boolean enableRecoveryBonus,          // 是否启用恢复加成（如床铺休息）
    FatigueThresholds fatigueThresholds,  // 疲劳度阈值配置
    RestBonusConfig restBonusConfig,     // 休息加成配置
    // 新增配置项
    boolean showJoinMessage,             // 玩家加入时显示状态消息
    boolean showLevelUpMessage,          // 显示等级变化消息
    boolean showCriticalWarning,         // 显示临界警告
    boolean showActionBar,               // 显示 ActionBar
    int actionBarUpdateInterval,         // ActionBar 更新间隔（秒）
    boolean forcedRestEnabled,           // 是否启用强制休息
    int forcedRestThreshold,            // 触发强制休息的阈值
    int forcedRestDuration,             // 强制休息持续时间（秒）
    int travelFatigueCheckInterval,     // 旅行疲劳检查间隔（tick）
    int travelAccumulationRate,         // 旅行疲劳累积速率
    int onlinePhysicalAccumulation,     // 在线物理疲劳累积
    int onlineMentalAccumulation,       // 在线精神疲劳累积
    int baseRecoveryPerMinute,          // 每分钟基础恢复量
    int onlineAccumulationInterval,     // 在线疲劳累积间隔（秒）
    int restItemRecoveryAmount          // 休息物品恢复量
) {

    /**
     * 默认配置
     */
    public static FatigueConfig defaults() {
        return new FatigueConfig(
            true,    // enabled
            100,     // maxFatigue
            5,       // recoveryRate
            10,      // combatFatigueRate
            2,       // gatheringFatigueRate
            3,       // buildingFatigueRate
            1,       // naturalFatigueRate
            true,    // enableFatigueEffects
            true,    // enableMovementPenalty
            true,    // enableCombatPenalty
            true,    // enableRecoveryBonus
            FatigueThresholds.defaults(),
            RestBonusConfig.defaults(),
            true,    // showJoinMessage
            true,    // showLevelUpMessage
            true,    // showCriticalWarning
            true,    // showActionBar
            5,       // actionBarUpdateInterval
            true,    // forcedRestEnabled
            90,      // forcedRestThreshold
            300,     // forcedRestDuration
            100,     // travelFatigueCheckInterval
            1,       // travelAccumulationRate
            1,       // onlinePhysicalAccumulation
            1,       // onlineMentalAccumulation
            5,       // baseRecoveryPerMinute
            60,      // onlineAccumulationInterval
            20       // restItemRecoveryAmount
        );
    }

    /**
     * 从配置文件读取
     */
    public static FatigueConfig fromConfig(ConfigurationSection section) {
        if (section == null) {
            return defaults();
        }

        return new FatigueConfig(
            section.getBoolean("enabled", true),
            section.getInt("max-fatigue", 100),
            section.getInt("recovery-rate", 5),
            section.getInt("combat-fatigue-rate", 10),
            section.getInt("gathering-fatigue-rate", 2),
            section.getInt("building-fatigue-rate", 3),
            section.getInt("natural-fatigue-rate", 1),
            section.getBoolean("enable-fatigue-effects", true),
            section.getBoolean("enable-movement-penalty", true),
            section.getBoolean("enable-combat-penalty", true),
            section.getBoolean("enable-recovery-bonus", true),
            FatigueThresholds.fromConfig(section.getConfigurationSection("thresholds")),
            RestBonusConfig.fromConfig(section.getConfigurationSection("rest-bonus")),
            section.getBoolean("show-join-message", true),
            section.getBoolean("show-levelup-message", true),
            section.getBoolean("show-critical-warning", true),
            section.getBoolean("show-action-bar", true),
            section.getInt("action-bar-update-interval", 5),
            section.getBoolean("forced-rest-enabled", true),
            section.getInt("forced-rest-threshold", 90),
            section.getInt("forced-rest-duration", 300),
            section.getInt("travel-fatigue-check-interval", 100),
            section.getInt("travel-accumulation-rate", 1),
            section.getInt("online-physical-accumulation", 1),
            section.getInt("online-mental-accumulation", 1),
            section.getInt("base-recovery-per-minute", 5),
            section.getInt("online-accumulation-interval", 60),
            section.getInt("rest-item-recovery-amount", 20)
        );
    }
}
