package dev.starcore.starcore.module.dungeon;

import java.util.List;
import java.util.Map;

/**
 * 副本奖励定义
 */
public record DungeonRewards(
    int experience,
    int gold,
    List<RewardItem> items,
    Map<String, Integer> reputation,
    List<String> achievements,
    double completionBonusMultiplier
) {
    /**
     * 奖励物品定义
     */
    public record RewardItem(
        String material,
        int amount,
        String nbtData,
        double dropChance
    ) {}
}
