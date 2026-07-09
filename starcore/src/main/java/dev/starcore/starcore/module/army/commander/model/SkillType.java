package dev.starcore.starcore.module.army.commander.model;

import java.util.Locale;

/**
 * 指挥官技能类型枚举
 * 定义所有可用的指挥官技能
 */
public enum SkillType {
    /**
     * 号令 - 提升范围内友军攻击力
     * 被动效果
     */
    RALLY("号令", "提升范围内友军攻击力", 1, true, 1800),

    /**
     * 激励 - 提升本国所有军队士气
     * 需要指挥本国军队
     */
    INSPIRE("激励", "提升本国所有军队士气", 2, true, 1200),

    /**
     * 增援 - 为指定军队补充士兵
     * 需要目标军队
     */
    REINFORCE("增援", "为指定军队补充士兵", 3, true, 600),

    /**
     * 战术撤退 - 脱离战斗并恢复生命
     * 需要目标军队
     */
    TACTICAL_RETREAT("战术撤退", "脱离战斗并恢复生命", 3, true, 900),

    /**
     * 攻城精通 - 提升攻城效率
     * 需要目标军队
     */
    SIEGE_MASTERY("攻城精通", "提升攻城器械效率", 4, true, 1500),

    /**
     * 骑兵冲锋 - 对目标造成额外伤害
     * 需要目标军队
     */
    CAVALRY_CHARGE("骑兵冲锋", "对目标造成额外伤害", 5, true, 1800),

    /**
     * 方阵 - 提升防御力
     * 需要目标军队
     */
    PHALANX("方阵", "大幅提升防御力", 5, true, 1200),

    /**
     * 侦察 - 显示附近所有军队
     * 被动效果
     */
    SCOUT("侦察", "显示附近所有军队信息", 2, false, 300),

    /**
     * 士气激励 - 大幅提升士气
     * 需要指挥本国军队
     */
    MORALE_BOOST("士气激励", "大幅提升所有友军士气", 6, true, 600),

    /**
     * 补给线 - 补给所有友军
     * 需要指挥本国军队
     */
    SUPPLY_LINE("补给线", "补给所有友军", 7, true, 900);

    private final String displayName;
    private final String description;
    private final int requiredLevel;    // 解锁所需最低指挥官等级
    private final boolean requiresArmyCommand;  // 是否需要指挥军队
    private final int cooldownTicks;     // 冷却时间（tick）

    SkillType(String displayName, String description, int requiredLevel,
               boolean requiresArmyCommand, int cooldownTicks) {
        this.displayName = displayName;
        this.description = description;
        this.requiredLevel = requiredLevel;
        this.requiresArmyCommand = requiresArmyCommand;
        this.cooldownTicks = cooldownTicks;
    }

    /**
     * 获取显示名称
     */
    public String displayName() {
        return displayName;
    }

    /**
     * 获取描述
     */
    public String description() {
        return description;
    }

    /**
     * 获取解锁所需等级
     */
    public int requiredLevel() {
        return requiredLevel;
    }

    /**
     * 是否需要指挥军队才能使用
     */
    public boolean requiresArmyCommand() {
        return requiresArmyCommand;
    }

    /**
     * 获取冷却时间（tick）
     */
    public int cooldownTicks() {
        return cooldownTicks;
    }

    /**
     * 获取冷却时间（秒）
     */
    public int cooldownSeconds() {
        return cooldownTicks / 20;
    }

    /**
     * 最大技能等级
     */
    public int maxLevel() {
        return 3;
    }

    /**
     * 解锁技能所需的技能点（经验值）
     * @param level 当前指挥官等级
     */
    public int unlockCost(CommanderLevel level) {
        return switch (this) {
            case RALLY -> 50 + (level.ordinal() * 10);
            case INSPIRE -> 80 + (level.ordinal() * 15);
            case REINFORCE -> 60 + (level.ordinal() * 10);
            case TACTICAL_RETREAT -> 70 + (level.ordinal() * 12);
            case SIEGE_MASTERY -> 100 + (level.ordinal() * 20);
            case CAVALRY_CHARGE -> 120 + (level.ordinal() * 25);
            case PHALANX -> 100 + (level.ordinal() * 20);
            case SCOUT -> 30 + (level.ordinal() * 5);
            case MORALE_BOOST -> 150 + (level.ordinal() * 30);
            case SUPPLY_LINE -> 130 + (level.ordinal() * 25);
        };
    }

    /**
     * 升级技能所需的技能点
     * @param currentLevel 当前技能等级
     */
    public int upgradeCost(int currentLevel) {
        int baseCost = unlockCost(CommanderLevel.fromExperience(0));
        return baseCost * (currentLevel + 1);
    }

    /**
     * 获取技能图标材质
     */
    public String getIcon() {
        return switch (this) {
            case RALLY -> "PLAYER_HEAD";
            case INSPIRE -> "GOLDEN_HELMET";
            case REINFORCE -> "IRON_SWORD";
            case TACTICAL_RETREAT -> "FEATHER";
            case SIEGE_MASTERY -> "FIREWORK_STAR";
            case CAVALRY_CHARGE -> "DIAMOND_HORSE_ARMOR";
            case PHALANX -> "SHIELD";
            case SCOUT -> "COMPASS";
            case MORALE_BOOST -> "GOLDEN_APPLE";
            case SUPPLY_LINE -> "HAY_BLOCK";
        };
    }

    /**
     * 获取技能分类
     */
    public SkillCategory category() {
        return switch (this) {
            case RALLY, INSPIRE, MORALE_BOOST -> SkillCategory.SUPPORT;
            case REINFORCE, SUPPLY_LINE -> SkillCategory.SUPPLY;
            case TACTICAL_RETREAT, PHALANX -> SkillCategory.DEFENSE;
            case SIEGE_MASTERY, CAVALRY_CHARGE -> SkillCategory.OFFENSE;
            case SCOUT -> SkillCategory.RECON;
        };
    }

    /**
     * 国际化描述
     */
    public String getLocalizedDescription(Locale locale) {
        return description;
    }
}
