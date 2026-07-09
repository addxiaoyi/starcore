package dev.starcore.starcore.module.army.commander.model;

/**
 * 技能分类
 */
public enum SkillCategory {
    /**
     * 支援类 - 提升友军能力
     */
    SUPPORT("支援", "green"),

    /**
     * 进攻类 - 造成伤害
     */
    OFFENSE("进攻", "red"),

    /**
     * 防御类 - 保护军队
     */
    DEFENSE("防御", "blue"),

    /**
     * 补给类 - 提供资源
     */
    SUPPLY("补给", "yellow"),

    /**
     * 侦察类 - 情报收集
     */
    RECON("侦察", "gray");

    private final String displayName;
    private final String color;

    SkillCategory(String displayName, String color) {
        this.displayName = displayName;
        this.color = color;
    }

    public String displayName() {
        return displayName;
    }

    public String color() {
        return color;
    }
}