package dev.starcore.starcore.module.army.commander.model;

import java.util.Arrays;
import java.util.Locale;

/**
 * 指挥官等级枚举
 * 共有 10 个等级，从列兵到元帅
 */
public enum CommanderLevel {
    /**
     * 列兵 - 初始等级
     */
    PRIVATE("列兵", 0),

    /**
     * 班长 - 1级
     */
    CORPORAL("班长", 100),

    /**
     * 排长 - 2级
     */
    SERGEANT("排长", 300),

    /**
     * 连长 - 3级
     */
    LIEUTENANT("连长", 600),

    /**
     * 营长 - 4级
     */
    MAJOR("营长", 1000),

    /**
     * 团长 - 5级
     */
    COLONEL("团长", 1500),

    /**
     * 旅长 - 6级
     */
    BRIGADIER("旅长", 2200),

    /**
     * 师长 - 7级
     */
    DIVISION_GENERAL("师长", 3000),

    /**
     * 军长 - 8级
     */
    ARMY_COMMANDER("军长", 4000),

    /**
     * 元帅 - 最高等级
     */
    MARSHAL("元帅", 5500);

    private final String title;
    private final int experienceRequired;

    CommanderLevel(String title, int experienceRequired) {
        this.title = title;
        this.experienceRequired = experienceRequired;
    }

    /**
     * 获取指挥官称号
     */
    public String title() {
        return title;
    }

    /**
     * 获取所需经验值
     */
    public int experienceRequired() {
        return experienceRequired;
    }

    /**
     * 获取下一级
     */
    public CommanderLevel nextLevel() {
        int nextOrdinal = ordinal() + 1;
        if (nextOrdinal >= values().length) {
            return this; // 已是最高级
        }
        return values()[nextOrdinal];
    }

    /**
     * 检查是否已达最高级
     */
    public boolean isMaxLevel() {
        return ordinal() == values().length - 1;
    }

    /**
     * 获取升到下一级还需要多少经验
     */
    public int experienceToNextLevel(int currentExp) {
        int currentRequired = experienceRequired;
        int nextRequired = nextLevel().experienceRequired;
        return Math.max(0, nextRequired - currentExp);
    }

    /**
     * 根据经验值获取等级
     */
    public static CommanderLevel fromExperience(int experience) {
        CommanderLevel result = PRIVATE;
        for (CommanderLevel level : values()) {
            if (experience >= level.experienceRequired) {
                result = level;
            } else {
                break;
            }
        }
        return result;
    }

    /**
     * 获取等级序号（从1开始）
     */
    public int levelNumber() {
        return ordinal() + 1;
    }

    /**
     * 获取可解锁的最大技能等级
     */
    public int maxUnlockableSkillLevel() {
        return Math.min(ordinal() + 1, 3);
    }

    /**
     * 国际化名称
     */
    public String getLocalizedName(Locale locale) {
        return title;
    }
}
