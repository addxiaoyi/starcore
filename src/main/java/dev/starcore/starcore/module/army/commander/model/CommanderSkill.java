package dev.starcore.starcore.module.army.commander.model;

/**
 * 指挥官技能记录
 * 表示一个已解锁的技能及其等级
 */
public record CommanderSkill(
    SkillType type,
    int level
) {
    /**
     * 创建一个新的指挥官技能
     */
    public CommanderSkill {
        if (level < 1 || level > type.maxLevel()) {
            throw new IllegalArgumentException("Invalid skill level: " + level);
        }
    }

    /**
     * 检查技能是否已达最高级
     */
    public boolean isMaxLevel() {
        return level >= type.maxLevel();
    }

    /**
     * 获取升级到下一级需要的经验
     */
    public int costToNextLevel() {
        if (isMaxLevel()) {
            return 0;
        }
        return type.upgradeCost(level);
    }

    /**
     * 获取效果描述
     */
    public String getEffectDescription() {
        return switch (type) {
            case RALLY -> String.format("提升 %d 格范围内友军攻击力 %d%%", 50 + (level * 10), level * 5);
            case INSPIRE -> String.format("提升本国所有军队士气 %d%%", level * 10);
            case REINFORCE -> String.format("为指定军队补充 %d 名士兵", level * 10);
            case TACTICAL_RETREAT -> String.format("脱离战斗并恢复 %d%% 生命", level * 20);
            case SIEGE_MASTERY -> String.format("攻城效率提升 %d%%", level * 50);
            case CAVALRY_CHARGE -> String.format("造成 %d%% 最大生命伤害", level * 10);
            case PHALANX -> String.format("防御力提升 %d%%", level * 20);
            case SCOUT -> String.format("显示 %d 格内所有军队", 200 + (level * 100));
            case MORALE_BOOST -> String.format("提升所有友军士气 %d%%", level * 15);
            case SUPPLY_LINE -> String.format("补给所有友军 %d%%", level * 25);
        };
    }
}
