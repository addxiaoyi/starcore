package dev.starcore.starcore.module.army.fatigue.model;

import org.bukkit.configuration.ConfigurationSection;

/**
 * 休息加成配置
 * 定义通过休息恢复疲劳度的加成
 */
public record RestBonusConfig(
    double bedRestBonus,           // 在床上休息的恢复加成倍率
    int minBedRestTime,            // 最小床上休息时间（秒）
    double sittingBonus,            // 静坐休息的恢复加成倍率
    int sittingCheckInterval,       // 静坐检查间隔（秒）
    double tavernBonus,             // 酒馆休息的恢复加成倍率
    double campingBonus             // 露营恢复加成倍率
) {

    /**
     * 默认配置
     */
    public static RestBonusConfig defaults() {
        return new RestBonusConfig(
            3.0,    // bedRestBonus
            60,     // minBedRestTime
            2.0,    // sittingBonus
            10,     // sittingCheckInterval
            2.5,    // tavernBonus
            1.5     // campingBonus
        );
    }

    /**
     * 从配置文件读取
     */
    public static RestBonusConfig fromConfig(ConfigurationSection section) {
        if (section == null) {
            return defaults();
        }

        return new RestBonusConfig(
            section.getDouble("bed-rest-bonus", 3.0),
            section.getInt("min-bed-rest-time", 60),
            section.getDouble("sitting-bonus", 2.0),
            section.getInt("sitting-check-interval", 10),
            section.getDouble("tavern-bonus", 2.5),
            section.getDouble("camping-bonus", 1.5)
        );
    }
}
