package dev.starcore.starcore.module.faith.model;

import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.Map;

/**
 * 信仰值系统配置
 */
public record FaithConfig(
    int maxFaith,
    int[] levelThresholds,
    Map<Integer, FaithLevelConfig> levelConfigs,
    Map<String, Double> blessingCosts,
    int faithDecayPerDay,
    int prayerFaithGain,
    int dailyFaithGain,
    int maxDailyPrayers,
    long faithEventCooldownMinutes
) {
    /**
     * 从配置文件加载配置
     */
    public static FaithConfig fromConfig(ConfigurationSection section) {
        if (section == null) {
            return defaultConfig();
        }

        int maxFaith = section.getInt("max-faith", 100);
        int[] thresholds = {20, 40, 60, 80}; // 默认等级阈值
        int[] customThresholds = section.getIntegerList("level-thresholds").stream()
            .mapToInt(Integer::intValue)
            .toArray();
        if (customThresholds.length == 4) {
            thresholds = customThresholds;
        }

        // 加载各等级配置
        Map<Integer, FaithLevelConfig> levelConfigs = new HashMap<>();
        ConfigurationSection levels = section.getConfigurationSection("levels");
        if (levels != null) {
            for (String key : levels.getKeys(false)) {
                int level = Integer.parseInt(key);
                ConfigurationSection levelSection = levels.getConfigurationSection(key);
                if (levelSection != null) {
                    levelConfigs.put(level, FaithLevelConfig.fromConfig(levelSection));
                }
            }
        }

        // 确保默认等级配置存在
        for (int i = 1; i <= 5; i++) {
            levelConfigs.putIfAbsent(i, FaithLevelConfig.defaultForLevel(i));
        }

        // 加载祈福消耗
        Map<String, Double> blessingCosts = new HashMap<>();
        ConfigurationSection blessings = section.getConfigurationSection("blessing-costs");
        if (blessings != null) {
            for (String key : blessings.getKeys(false)) {
                blessingCosts.put(key, blessings.getDouble(key, 10.0));
            }
        }
        blessingCosts.putIfAbsent("prosperity", 20.0);   // 繁荣祈福
        blessingCosts.putIfAbsent("protection", 15.0);   // 守护祈福
        blessingCosts.putIfAbsent("harvest", 10.0);      // 丰收祈福
        blessingCosts.putIfAbsent("blessing", 25.0);     // 通用祈福

        return new FaithConfig(
            maxFaith,
            thresholds,
            levelConfigs,
            blessingCosts,
            section.getInt("faith-decay-per-day", 5),
            section.getInt("prayer-faith-gain", 2),
            section.getInt("daily-faith-gain", 3),
            section.getInt("max-daily-prayers", 10),
            section.getLong("faith-event-cooldown-minutes", 60)
        );
    }

    /**
     * 默认配置
     */
    public static FaithConfig defaultConfig() {
        Map<Integer, FaithLevelConfig> defaults = new HashMap<>();
        for (int i = 1; i <= 5; i++) {
            defaults.put(i, FaithLevelConfig.defaultForLevel(i));
        }

        Map<String, Double> blessingCosts = new HashMap<>();
        blessingCosts.put("prosperity", 20.0);
        blessingCosts.put("protection", 15.0);
        blessingCosts.put("harvest", 10.0);
        blessingCosts.put("blessing", 25.0);

        return new FaithConfig(
            100,
            new int[]{20, 40, 60, 80},
            defaults,
            blessingCosts,
            5,   // 每天衰减 5 点
            2,   // 每次祈祷 +2
            3,   // 每日奖励 +3
            10,  // 每日最多祈祷 10 次
            60   // 信仰事件冷却 60 分钟
        );
    }

    /**
     * 根据信仰值计算等级
     */
    public int calculateLevel(int faith) {
        for (int i = levelThresholds.length; i >= 0; i--) {
            if (i == 0 || faith >= levelThresholds[i - 1]) {
                return i + 1;
            }
        }
        return 1;
    }

    /**
     * 获取等级阈值
     */
    public int getThreshold(int level) {
        if (level <= 0 || level > levelThresholds.length + 1) {
            return level > levelThresholds.length + 1 ? 100 : 0;
        }
        if (level == 1) {
            return 0;
        }
        return levelThresholds[level - 2];
    }
}