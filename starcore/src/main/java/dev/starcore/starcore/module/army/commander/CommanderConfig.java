package dev.starcore.starcore.module.army.commander;

import org.bukkit.configuration.ConfigurationSection;

/**
 * 指挥官系统配置
 */
public record CommanderConfig(
    boolean enabled,
    int maxSkillsPerCommander,
    boolean allowSkillReset,
    int skillResetRefundRatio,
    int expFromBattle,
    int expPerKill,
    int expPerVictory,
    int expPerArmyCreated,
    int expPerTerritoryDefended
) {
    /**
     * 默认配置
     */
    public static CommanderConfig defaults() {
        return new CommanderConfig(
            true,           // enabled
            5,              // maxSkillsPerCommander
            true,           // allowSkillReset
            2,              // skillResetRefundRatio (返还一半经验)
            10,             // expFromBattle (每场战斗获得经验)
            5,              // expPerKill (每次击杀获得经验)
            50,             // expPerVictory (每次胜利获得经验)
            20,             // expPerArmyCreated (创建军队获得经验)
            15              // expPerTerritoryDefended (防守领土获得经验)
        );
    }

    /**
     * 从配置节读取
     */
    public static CommanderConfig fromConfig(ConfigurationSection section) {
        if (section == null) {
            return defaults();
        }
        return new CommanderConfig(
            section.getBoolean("enabled", true),
            section.getInt("max-skills-per-commander", 5),
            section.getBoolean("allow-skill-reset", true),
            section.getInt("skill-reset-refund-ratio", 2),
            section.getInt("exp-from-battle", 10),
            section.getInt("exp-per-kill", 5),
            section.getInt("exp-per-victory", 50),
            section.getInt("exp-per-army-created", 20),
            section.getInt("exp-per-territory-defended", 15)
        );
    }
}
