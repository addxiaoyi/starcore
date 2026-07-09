package dev.starcore.starcore.module.army.fatigue;

import org.bukkit.configuration.ConfigurationSection;

/**
 * 疲劳系统配置
 */
public record FatigueConfig(
    boolean enabled,
    boolean showActionBar,
    boolean showBossBar,
    int actionBarUpdateInterval,
    int bossBarUpdateInterval,
    // 恢复配置
    int baseRecoveryPerMinute,
    int sleepRecoveryPerMinute,
    int restItemRecoveryAmount,
    // 累积配置
    int physicalAccumulationRate,
    int mentalAccumulationRate,
    int combatAccumulationRate,
    int travelAccumulationRate,
    // 战斗相关
    int combatFatiguePerKill,
    int combatFatiguePerDeath,
    int combatFatiguePerPvP,
    // 旅行相关
    int travelFatiguePerBlock,
    int travelFatigueCheckInterval,
    double travelFatigueMaxDistance,
    // 在线累积
    int onlineAccumulationInterval,
    int onlinePhysicalAccumulation,
    int onlineMentalAccumulation,
    // 强制休息
    boolean forcedRestEnabled,
    int forcedRestThreshold,
    int forcedRestDuration,
    // 消息
    boolean showJoinMessage,
    boolean showLevelUpMessage,
    boolean showCriticalWarning
) {
    /**
     * 默认配置
     */
    public static FatigueConfig defaults() {
        return new FatigueConfig(
            true,
            true,
            false,
            40,           // 2秒更新一次
            100,          // 5秒更新一次
            5,            // 每分钟恢复5点
            50,           // 睡眠时每分钟恢复50点
            25,           // 休息物品恢复25点
            2,            // 每分钟累积2点体力疲劳
            3,            // 每分钟累积3点精神疲劳
            5,            // 每分钟累积5点战斗疲劳
            1,            // 每分钟累积1点旅行疲劳
            10,           // 每次击杀增加10点战斗疲劳
            20,           // 每次死亡增加20点战斗疲劳
            15,           // 每次PVP增加15点战斗疲劳
            1,            // 每100格增加1点旅行疲劳
            200,          // 10秒检查一次
            100.0,        // 最大有效距离
            1200,         // 1分钟累积一次
            2,            // 每分钟2点体力疲劳
            3,            // 每分钟3点精神疲劳
            true,         // 启用强制休息
            95,           // 95%触发强制休息
            60,           // 强制休息60秒
            true,         // 显示加入消息
            true,         // 显示等级变化消息
            true          // 显示临界警告
        );
    }

    /**
     * 从配置节读取
     */
    public static FatigueConfig fromConfig(ConfigurationSection section) {
        if (section == null || !section.getBoolean("enabled", true)) {
            return defaults();
        }

        return new FatigueConfig(
            section.getBoolean("enabled", true),
            section.getBoolean("ui.action-bar", true),
            section.getBoolean("ui.boss-bar", false),
            section.getInt("ui.action-bar-interval", 40),
            section.getInt("ui.boss-bar-interval", 100),
            section.getInt("recovery.base-per-minute", 5),
            section.getInt("recovery.sleep-per-minute", 50),
            section.getInt("recovery.rest-item-amount", 25),
            section.getInt("accumulation.physical", 2),
            section.getInt("accumulation.mental", 3),
            section.getInt("accumulation.combat", 5),
            section.getInt("accumulation.travel", 1),
            section.getInt("combat.fatigue-per-kill", 10),
            section.getInt("combat.fatigue-per-death", 20),
            section.getInt("combat.fatigue-per-pvp", 15),
            section.getInt("travel.fatigue-per-block", 1),
            section.getInt("travel.check-interval", 200),
            section.getDouble("travel.max-distance", 100.0),
            section.getInt("online.accumulation-interval", 1200),
            section.getInt("online.physical", 2),
            section.getInt("online.mental", 3),
            section.getBoolean("forced-rest.enabled", true),
            section.getInt("forced-rest.threshold", 95),
            section.getInt("forced-rest.duration", 60),
            section.getBoolean("messages.show-join", true),
            section.getBoolean("messages.show-level-up", true),
            section.getBoolean("messages.show-critical-warning", true)
        );
    }
}