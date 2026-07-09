package dev.starcore.starcore.module.war.situation;

import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.army.model.ArmyUnit;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 战况快照
 * 记录某一时刻的战场情况
 */
public record WarSituation(
    UUID warId,
    NationId nation1,
    NationId nation2,
    Instant timestamp,
    int totalBattles,
    int territoryChanges,
    int casualties,
    List<ArmyCasualty> armyCasualties,
    List<BattleRecord> recentBattles,
    WarIntensity intensity,
    double nation1Score,
    double nation2Score
) {
    /**
     * 创建战况快照
     */
    public static WarSituation create(
        UUID warId,
        NationId nation1,
        NationId nation2,
        int totalBattles,
        int territoryChanges,
        int casualties,
        List<ArmyCasualty> armyCasualties,
        List<BattleRecord> recentBattles,
        double nation1Score,
        double nation2Score
    ) {
        WarIntensity intensity = calculateIntensity(totalBattles, recentBattles);
        return new WarSituation(
            warId, nation1, nation2, Instant.now(),
            totalBattles, territoryChanges, casualties,
            armyCasualties, recentBattles, intensity,
            nation1Score, nation2Score
        );
    }

    private static WarIntensity calculateIntensity(int totalBattles, List<BattleRecord> recentBattles) {
        if (recentBattles.isEmpty()) {
            return WarIntensity.CALM;
        }

        // 计算最近1小时内的战斗次数
        Instant oneHourAgo = Instant.now().minusSeconds(3600);
        long recentCount = recentBattles.stream()
            .filter(r -> r.timestamp().isAfter(oneHourAgo))
            .count();

        if (recentCount >= 5) {
            return WarIntensity.INTENSE;
        } else if (recentCount >= 2) {
            return WarIntensity.ACTIVE;
        } else if (recentCount >= 1) {
            return WarIntensity.SKIRMISH;
        } else {
            return WarIntensity.CALM;
        }
    }

    /**
     * 获取优势方
     * @return 1 = nation1优势, 2 = nation2优势, 0 = 平局
     */
    public int advantage() {
        if (nation1Score > nation2Score + 10) return 1;
        if (nation2Score > nation1Score + 10) return 2;
        return 0;
    }

    /**
     * 军队伤亡记录
     */
    public record ArmyCasualty(
        UUID armyId,
        String armyName,
        NationId nationId,
        int soldiersLost,
        int soldiersRemaining,
        Instant timestamp
    ) {}

    /**
     * 战斗记录
     */
    public record BattleRecord(
        UUID battleId,
        String battlefieldName,
        NationId winner,
        NationId loser,
        int winnerLosses,
        int loserLosses,
        Instant timestamp
    ) {}
}