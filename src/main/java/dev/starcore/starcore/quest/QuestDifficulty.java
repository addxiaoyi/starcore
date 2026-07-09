package dev.starcore.starcore.quest;

import net.kyori.adventure.text.format.NamedTextColor;

/**
 * 任务难度枚举
 *
 * @author StarCore Team
 * @since 1.0.0
 */
public enum QuestDifficulty {
    /**
     * 简单 - 新手任务
     */
    EASY("简单", NamedTextColor.GREEN, "§a", 1, 1.0, 0.8),

    /**
     * 普通 - 常规任务
     */
    NORMAL("普通", NamedTextColor.YELLOW, "§e", 2, 1.5, 1.0),

    /**
     * 困难 - 挑战任务
     */
    HARD("困难", NamedTextColor.GOLD, "§6", 3, 2.0, 1.5),

    /**
     * 专家 - 高难度任务
     */
    EXPERT("专家", NamedTextColor.RED, "§c", 4, 3.0, 2.5),

    /**
     * 传说 - 史诗级任务
     */
    LEGENDARY("传说", NamedTextColor.DARK_PURPLE, "§5", 5, 5.0, 4.0),

    /**
     * 噩梦 - 极限挑战任务
     */
    NIGHTMARE("噩梦", NamedTextColor.DARK_RED, "§4", 6, 8.0, 6.0);

    private final String displayName;
    private final NamedTextColor color;
    private final String legacyColor;
    private final int level;
    private final double rewardMultiplier;
    private final double experienceMultiplier;

    QuestDifficulty(String displayName, NamedTextColor color, String legacyColor, int level,
                    double rewardMultiplier, double experienceMultiplier) {
        this.displayName = displayName;
        this.color = color;
        this.legacyColor = legacyColor;
        this.level = level;
        this.rewardMultiplier = rewardMultiplier;
        this.experienceMultiplier = experienceMultiplier;
    }

    /**
     * 获取显示名称
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * 获取颜色代码
     */
    public NamedTextColor getColor() {
        return color;
    }

    /**
     * 获取 legacy 文本颜色码，用于仍返回字符串的命令输出。
     */
    public String getLegacyColor() {
        return legacyColor;
    }

    /**
     * 获取难度等级 (1-5)
     */
    public int getLevel() {
        return level;
    }

    /**
     * 获取奖励倍率
     */
    public double getRewardMultiplier() {
        return rewardMultiplier;
    }

    /**
     * 获取经验倍率
     */
    public double getExperienceMultiplier() {
        return experienceMultiplier;
    }

    /**
     * 获取带颜色的显示名称
     */
    public String getColoredName() {
        return legacyColor + displayName + "§r";
    }

    /**
     * 获取星级显示 (★☆)
     */
    public String getStarRating() {
        StringBuilder stars = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            stars.append(i < level ? "★" : "☆");
        }
        return legacyColor + stars.toString() + "§r";
    }

    /**
     * 根据等级获取难度
     */
    public static QuestDifficulty fromLevel(int level) {
        for (QuestDifficulty difficulty : values()) {
            if (difficulty.level == level) {
                return difficulty;
            }
        }
        return NORMAL;
    }

    /**
     * 根据玩家等级推荐难度
     */
    public static QuestDifficulty recommendForPlayerLevel(int playerLevel) {
        if (playerLevel < 10) return EASY;
        if (playerLevel < 30) return NORMAL;
        if (playerLevel < 50) return HARD;
        if (playerLevel < 75) return EXPERT;
        return LEGENDARY;
    }
}
