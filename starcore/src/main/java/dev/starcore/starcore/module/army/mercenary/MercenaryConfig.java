package dev.starcore.starcore.module.army.mercenary;

import org.bukkit.configuration.ConfigurationSection;

/**
 * 雇佣兵配置
 */
public record MercenaryConfig(
    int maxMercenariesPerNation,           // 每个国家最多雇佣兵数量
    int maxContractDays,                   // 最大合同天数
    int minContractDays,                   // 最小合同天数
    int experiencePerKill,                // 每次击杀获得经验
    int experiencePerMission,              // 每次任务获得经验
    int experiencePerDeath,               // 每次死亡扣除经验
    int maxDeathBeforeTermination,         // 最大死亡次数后终止合同
    double rankUpBonus,                    // 晋升奖励倍率
    boolean autoRenewContract,            // 是否自动续约
    int renewDays,                         // 续约天数
    boolean enablePromotionMessages,       // 是否启用晋升消息
    boolean enableContractExpiryMessages   // 是否启用合同到期消息
) {
    /**
     * 默认配置
     */
    public static MercenaryConfig defaults() {
        return new MercenaryConfig(
            10,     // maxMercenariesPerNation
            30,     // maxContractDays
            1,      // minContractDays
            15,     // experiencePerKill
            50,     // experiencePerMission
            10,     // experiencePerDeath
            5,      // maxDeathBeforeTermination
            1.5,    // rankUpBonus
            false,  // autoRenewContract
            7,      // renewDays
            true,   // enablePromotionMessages
            true    // enableContractExpiryMessages
        );
    }

    /**
     * 从配置节读取
     */
    public static MercenaryConfig fromConfig(ConfigurationSection section) {
        if (section == null) {
            return defaults();
        }
        return new MercenaryConfig(
            section.getInt("max-mercenaries-per-nation", 10),
            section.getInt("max-contract-days", 30),
            section.getInt("min-contract-days", 1),
            section.getInt("experience-per-kill", 15),
            section.getInt("experience-per-mission", 50),
            section.getInt("experience-per-death", 10),
            section.getInt("max-death-before-termination", 5),
            section.getDouble("rank-up-bonus", 1.5),
            section.getBoolean("auto-renew-contract", false),
            section.getInt("renew-days", 7),
            section.getBoolean("enable-promotion-messages", true),
            section.getBoolean("enable-contract-expiry-messages", true)
        );
    }
}