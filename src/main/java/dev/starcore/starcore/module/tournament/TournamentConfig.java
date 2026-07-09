package dev.starcore.starcore.module.tournament;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

/**
 * 比赛配置
 */
public record TournamentConfig(
    String displayName,
    String description,
    int maxPlayers,
    int minPlayers,
    int waitTime,
    int maxDuration,
    List<String> tags,
    double prizePool,
    Location spectatorLocation,
    List<RewardItemConfig> rewardItems
) {
    /**
     * 奖励物品配置
     */
    public record RewardItemConfig(String material, int amount) {
        public static RewardItemConfig fromConfig(String material, int amount) {
            return new RewardItemConfig(material.toUpperCase(), amount);
        }
    }

    public static TournamentConfig defaults(String name, String desc) {
        return new TournamentConfig(name, desc, 16, 2, 60, 600, List.of(), 0.0, null, List.of());
    }

    public static TournamentConfig pvp1v1() {
        return new TournamentConfig(
            "PvP 单挑赛", "1v1 个人对决",
            32, 2, 120, 900,
            List.of("pvp", "1v1", "combat"), 500.0, null,
            List.of(
                new RewardItemConfig("DIAMOND", 8),
                new RewardItemConfig("GOLDEN_APPLE", 5)
            )
        );
    }

    public static TournamentConfig pvpFfa() {
        return new TournamentConfig(
            "PvP 乱斗赛", "多人自由战斗",
            32, 8, 60, 600,
            List.of("pvp", "ffa", "battle"), 1000.0, null,
            List.of(
                new RewardItemConfig("DIAMOND", 16),
                new RewardItemConfig("EXPERIENCE_BOTTLE", 32)
            )
        );
    }

    public static TournamentConfig team() {
        return new TournamentConfig(
            "团队赛", "团队对战",
            24, 8, 180, 1200,
            List.of("pvp", "team"), 2000.0, null,
            List.of(
                new RewardItemConfig("NETHERITE_INGOT", 4),
                new RewardItemConfig("ENCHANTED_GOLDEN_APPLE", 8)
            )
        );
    }

    public static TournamentConfig speedrun() {
        return new TournamentConfig(
            "速通挑战", "竞速完成目标",
            20, 2, 60, 1800,
            List.of("speedrun", "pve"), 800.0, null,
            List.of(
                new RewardItemConfig("EMERALD", 32),
                new RewardItemConfig("NETHER_STAR", 1)
            )
        );
    }

    public static TournamentConfig parkour() {
        return new TournamentConfig(
            "跑酷挑战", "跑酷竞速",
            20, 2, 60, 1800,
            List.of("parkour", "adventure"), 800.0, null,
            List.of(
                new RewardItemConfig("DIAMOND", 10),
                new RewardItemConfig("ELYTRA", 1)
            )
        );
    }

    public static TournamentConfig elimination() {
        return new TournamentConfig(
            "淘汰赛", "生存淘汰",
            16, 4, 90, 1200,
            List.of("pvp", "elimination", "survival"), 1500.0, null,
            List.of(
                new RewardItemConfig("DIAMOND", 12),
                new RewardItemConfig("GOLDEN_APPLE", 16)
            )
        );
    }

    public static TournamentConfig forType(TournamentType type) {
        return switch (type) {
            case PVP_1V1 -> pvp1v1();
            case PVP_FFA -> pvpFfa();
            case PVP_TEAM -> team();
            case SPEEDRUN -> speedrun();
            case PARKOUR -> parkour();
            case ELIMINATION -> elimination();
        };
    }

    public static TournamentConfig fromConfig(ConfigurationSection section) {
        if (section == null) {
            return defaults("比赛", "");
        }

        List<RewardItemConfig> items = List.of();
        if (section.contains("reward-items")) {
            List<?> itemList = section.getList("reward-items");
            if (itemList != null) {
                items = itemList.stream()
                    .filter(item -> item instanceof Map<?, ?>)
                    .map(item -> rewardItemFromMap((Map<?, ?>) item))
                    .toList();
            }
        }

        return new TournamentConfig(
            section.getString("display-name", "比赛"),
            section.getString("description", ""),
            section.getInt("max-players", 16),
            section.getInt("min-players", 2),
            section.getInt("wait-time", 60),
            section.getInt("max-duration", 600),
            section.getStringList("tags"),
            section.getDouble("prize-pool", 0.0),
            null,
            items
        );
    }

    private static RewardItemConfig rewardItemFromMap(Map<?, ?> map) {
        Object material = map.get("material");
        Object amount = map.containsKey("amount") ? map.get("amount") : 1;
        int itemAmount = amount instanceof Number number ? number.intValue() : Integer.parseInt(amount.toString());
        return new RewardItemConfig(material.toString().toUpperCase(), itemAmount);
    }

    /**
     * 获取第一名奖励的物品列表
     */
    public List<RewardItemConfig> getRewardItems() {
        return rewardItems != null ? rewardItems : List.of();
    }
}
