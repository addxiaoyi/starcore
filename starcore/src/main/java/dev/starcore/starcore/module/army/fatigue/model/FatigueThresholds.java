package dev.starcore.starcore.module.army.fatigue.model;

import org.bukkit.configuration.ConfigurationSection;

/**
 * 疲劳度阈值配置
 * 定义不同疲劳等级的阈值
 */
public record FatigueThresholds(
    int slightlyTiredThreshold,    // 轻微疲劳阈值
    int fatiguedThreshold,         // 疲劳阈值
    int severelyFatiguedThreshold, // 严重疲劳阈值
    int exhaustedThreshold,        // 精疲力竭阈值
    int breakdownThreshold         // 崩溃边缘阈值
) {

    /**
     * 默认阈值
     */
    public static FatigueThresholds defaults() {
        return new FatigueThresholds(
            20,     // slightlyTiredThreshold
            40,     // fatiguedThreshold
            60,     // severelyFatiguedThreshold
            80,     // exhaustedThreshold
            95      // breakdownThreshold
        );
    }

    /**
     * 从配置文件读取
     */
    public static FatigueThresholds fromConfig(ConfigurationSection section) {
        if (section == null) {
            return defaults();
        }

        return new FatigueThresholds(
            section.getInt("slightly-tired", 20),
            section.getInt("fatigued", 40),
            section.getInt("severely-fatigued", 60),
            section.getInt("exhausted", 80),
            section.getInt("breakdown", 95)
        );
    }
}
