package dev.starcore.starcore.module.faith.model;

import org.bukkit.configuration.ConfigurationSection;

/**
 * 信仰等级配置
 */
public record FaithLevelConfig(
    String name,
    String description,
    double resourceBonus,
    double defenseBonus,
    double taxBonus,
    double expBonus,
    String icon
) {
    /**
     * 从配置文件加载
     */
    public static FaithLevelConfig fromConfig(ConfigurationSection section) {
        if (section == null) {
            return defaultForLevel(1);
        }
        return new FaithLevelConfig(
            section.getString("name", "未知"),
            section.getString("description", ""),
            section.getDouble("resource-bonus", 0.0),
            section.getDouble("defense-bonus", 0.0),
            section.getDouble("tax-bonus", 0.0),
            section.getDouble("exp-bonus", 0.0),
            section.getString("icon", "BOOK")
        );
    }

    /**
     * 获取默认配置
     */
    public static FaithLevelConfig defaultForLevel(int level) {
        return switch (level) {
            case 1 -> new FaithLevelConfig(
                "迷途者",
                "信仰迷茫，需要指引",
                0.0, 0.0, 0.0, 0.0,
                "BROWN_BANNER"
            );
            case 2 -> new FaithLevelConfig(
                "初信者",
                "开始信奉神祇",
                0.05, 0.0, 0.0, 0.0,
                "WHITE_BANNER"
            );
            case 3 -> new FaithLevelConfig(
                "虔诚信徒",
                "坚定信仰，神眷加护",
                0.10, 0.05, 0.0, 0.05,
                "YELLOW_BANNER"
            );
            case 4 -> new FaithLevelConfig(
                "圣殿守护者",
                "守护圣地，信仰坚定",
                0.15, 0.10, 0.05, 0.10,
                "ORANGE_BANNER"
            );
            case 5 -> new FaithLevelConfig(
                "神选之人",
                "受神明眷顾，国家昌盛",
                0.20, 0.15, 0.10, 0.15,
                "GOLD_BANNER"
            );
            default -> new FaithLevelConfig(
                "迷途者",
                "信仰迷茫，需要指引",
                0.0, 0.0, 0.0, 0.0,
                "BROWN_BANNER"
            );
        };
    }
}