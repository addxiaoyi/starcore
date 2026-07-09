package dev.starcore.starcore.module.dungeon;

import java.util.List;

/**
 * BOSS定义
 */
public record DungeonBoss(
    String id,
    String name,
    String entityType,
    int baseHealth,
    double healthMultiplier,
    List<String> abilities,
    List<BossPhase> phases,
    BossDefeatCondition defeatCondition,
    List<String> lootTable,
    int respawnTimeSeconds
) {
    /**
     * 获取实际生命值
     */
    public int getActualHealth() {
        return (int) (baseHealth * healthMultiplier);
    }

    /**
     * 获取BOSS总阶段数
     */
    public int totalPhases() {
        return phases.size();
    }

    /**
     * 获取指定阈值的阶段
     */
    public BossPhase getPhaseAt(double healthPercentage) {
        return phases.stream()
            .filter(p -> p.threshold() >= healthPercentage)
            .findFirst()
            .orElse(null);
    }

    /**
     * BOSS阶段定义
     */
    public record BossPhase(
        double threshold,
        List<String> abilities,
        String effect,
        double damageMultiplier
    ) {}

    /**
     * BOSS击败条件
     */
    public record BossDefeatCondition(
        DungeonClearType type,
        int requiredDamage,
        boolean soloKillRequired
    ) {}
}
