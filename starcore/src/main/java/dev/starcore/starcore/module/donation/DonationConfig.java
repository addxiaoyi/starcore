package dev.starcore.starcore.module.donation;

import dev.starcore.starcore.module.donation.DonationService.DonationTier;
import org.bukkit.configuration.ConfigurationSection;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 献金配置
 */
public record DonationConfig(
    BigDecimal minDonationAmount,
    BigDecimal maxDonationAmount,
    boolean enabled,
    boolean autoGrantTierRewards,
    Map<String, DonationTier> tiers,
    List<DonationService.DonationReward> rewards,
    boolean announceDonations,
    boolean showRankings
) {
    public static DonationConfig defaults() {
        Map<String, DonationTier> defaultTiers = new HashMap<>();
        defaultTiers.put("bronze", new DonationTier(
            "bronze", "铜牌", new BigDecimal("0"), new BigDecimal("99.99"),
            List.of("基础献金者"),
            Map.of(), 1
        ));
        defaultTiers.put("silver", new DonationTier(
            "silver", "银牌", new BigDecimal("100"), new BigDecimal("499.99"),
            List.of("基础献金者", "专属称号"),
            Map.of("title", "银色献金者"), 2
        ));
        defaultTiers.put("gold", new DonationTier(
            "gold", "金牌", new BigDecimal("500"), new BigDecimal("999.99"),
            List.of("基础献金者", "专属称号", "优先传送"),
            Map.of("title", "金色献金者", "priority_tp", true), 3
        ));
        defaultTiers.put("platinum", new DonationTier(
            "platinum", "白金", new BigDecimal("1000"), new BigDecimal("4999.99"),
            List.of("基础献金者", "专属称号", "优先传送", "专属传送点"),
            Map.of("title", "白金献金者", "priority_tp", true, "home", true), 4
        ));
        defaultTiers.put("diamond", new DonationTier(
            "diamond", "钻石", new BigDecimal("5000"), new BigDecimal("99999.99"),
            List.of("基础献金者", "专属称号", "优先传送", "专属传送点", "专属头衔"),
            Map.of("title", "钻石献金者", "priority_tp", true, "home", true, "prefix", true), 5
        ));
        defaultTiers.put("legend", new DonationTier(
            "legend", "传奇", new BigDecimal("100000"), new BigDecimal("999999999"),
            List.of("全部特权", "传奇称号", "专属旗帜", "传奇特效"),
            Map.of("title", "传奇献金者", "priority_tp", true, "home", true, "prefix", true, "banner", true, "effects", true), 6
        ));

        List<DonationService.DonationReward> defaultRewards = List.of(
            // 注意：eco give 属于经济类命令，在 executeRewardCommands 白名单之外，会被阻止
            // 建议使用非经济类特权替代（如称号、传送点等）
            new DonationService.DonationReward(
                "bronze_welcome", "铜牌欢迎礼", "铜牌献金者的欢迎礼物",
                defaultTiers.get("bronze"), List.of("eco give %player% 50"), Map.of(), true, true
            ),
            new DonationService.DonationReward(
                "silver_bonus", "银牌bonus", "银牌献金者额外奖励",
                defaultTiers.get("silver"), List.of("eco give %player% 200"), Map.of(), true, false
            ),
            new DonationService.DonationReward(
                "gold_pack", "金牌礼包", "金牌献金者专属礼包",
                defaultTiers.get("gold"), List.of("eco give %player% 500"), Map.of(), true, false
            ),
            new DonationService.DonationReward(
                "platinum_chest", "白金宝箱", "白金献金者专属宝箱",
                defaultTiers.get("platinum"), List.of("eco give %player% 2000"), Map.of(), true, false
            ),
            new DonationService.DonationReward(
                "diamond_treasure", "钻石宝箱", "钻石献金者专属宝箱",
                defaultTiers.get("diamond"), List.of("eco give %player% 5000"), Map.of(), true, false
            ),
            new DonationService.DonationReward(
                "legend_crown", "传奇皇冠", "传奇献金者专属皇冠",
                defaultTiers.get("legend"), List.of("eco give %player% 10000"), Map.of(), true, false
            )
        );

        return new DonationConfig(
            new BigDecimal("10.00"),      // 最低献金额
            new BigDecimal("100000.00"),  // 最高献金额（0表示无限制）
            true,                          // 启用献金系统
            false,                         // 自动发放等级奖励
            defaultTiers,                 // 献金等级
            defaultRewards,               // 献金奖励
            true,                         // 公告献金
            true                          // 显示排名
        );
    }

    /**
     * 从配置节读取
     */
    public static DonationConfig fromConfig(org.bukkit.configuration.ConfigurationSection section) {
        if (section == null) {
            return defaults();
        }

        boolean enabled = section.getBoolean("enabled", true);
        BigDecimal minAmount = new BigDecimal(section.getString("min-donation-amount", "10.00"));
        BigDecimal maxAmount = new BigDecimal(section.getString("max-donation-amount", "100000.00"));
        boolean autoGrant = section.getBoolean("auto-grant-tier-rewards", false);
        boolean announce = section.getBoolean("announce-donations", true);
        boolean showRankings = section.getBoolean("show-rankings", true);

        // 读取等级配置
        // 注意：等级边界使用 double 比较（BigDecimal.compareTo），
        // 在边界值（如 99999.99 到 100000）需要精确匹配，可能存在精度问题
        Map<String, DonationTier> tiers = new HashMap<>();
        ConfigurationSection tierSection = section.getConfigurationSection("tiers");
        if (tierSection != null) {
            for (String tierId : tierSection.getKeys(false)) {
                ConfigurationSection tierConf = tierSection.getConfigurationSection(tierId);
                if (tierConf != null) {
                    tiers.put(tierId, new DonationTier(
                        tierId,
                        tierConf.getString("name", tierId),
                        new BigDecimal(tierConf.getString("min-amount", "0")),
                        new BigDecimal(tierConf.getString("max-amount", "999999999")),
                        tierConf.getStringList("benefits"),
                        parsePerks(tierConf.getConfigurationSection("perks")),
                        tierConf.getInt("priority", tiers.size() + 1)
                    ));
                }
            }
        }

        // 如果没有配置等级，使用默认
        if (tiers.isEmpty()) {
            tiers = defaults().tiers();
        }

        // 配置验证：admin 修改 maxDonationAmount 时应确保与最高等级起始金额一致
        // 若 maxDonationAmount 小于某玩家已献金额，该玩家的等级判定会异常

        // 读取奖励配置
        List<DonationService.DonationReward> rewards = List.of();
        ConfigurationSection rewardSection = section.getConfigurationSection("rewards");
        if (rewardSection != null) {
            List<DonationService.DonationReward> rewardList = new java.util.ArrayList<>();
            for (String rewardId : rewardSection.getKeys(false)) {
                ConfigurationSection rewardConf = rewardSection.getConfigurationSection(rewardId);
                if (rewardConf != null) {
                    String tierId = rewardConf.getString("required-tier", "bronze");
                    DonationTier requiredTier = tiers.getOrDefault(tierId, tiers.values().stream().min(DonationTier::compareTo).orElse(null));
                    if (requiredTier != null) {
                        rewardList.add(new DonationService.DonationReward(
                            rewardId,
                            rewardConf.getString("name", rewardId),
                            rewardConf.getString("description", ""),
                            requiredTier,
                            rewardConf.getStringList("commands"),
                            parsePerks(rewardConf.getConfigurationSection("items")),
                            rewardConf.getBoolean("one-time", true),
                            rewardConf.getBoolean("claimable", true)
                        ));
                    }
                }
            }
            rewards = rewardList;
        }

        return new DonationConfig(
            minAmount,
            maxAmount,
            enabled,
            autoGrant,
            tiers,
            rewards,
            announce,
            showRankings
        );
    }

    private static Map<String, Object> parsePerks(ConfigurationSection section) {
        if (section == null) {
            return Map.of();
        }
        Map<String, Object> perks = new HashMap<>();
        for (String key : section.getKeys(false)) {
            perks.put(key, section.get(key));
        }
        return perks;
    }
}