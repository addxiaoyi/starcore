package dev.starcore.starcore.module.tournament.model;

import java.time.Instant;
import java.util.UUID;

/**
 * 比赛轮次对阵信息
 */
public record BracketMatch(
    String id,
    int round,
    int matchNumber,
    UUID player1Id,
    UUID player2Id,
    UUID winnerId,
    Instant startTime,
    Instant endTime,
    boolean isBye,
    boolean isCompleted
) {
    /**
     * 创建空位（轮空）
     */
    public static BracketMatch bye(String id, int round, int matchNumber, UUID qualifiedPlayer) {
        return new BracketMatch(id, round, matchNumber, qualifiedPlayer, null, qualifiedPlayer,
            Instant.now(), Instant.now(), true, true);
    }

    /**
     * 检查是否轮空
     */
    public boolean isByeMatch() {
        return player1Id != null && player2Id == null;
    }

    /**
     * 获取胜者
     */
    public UUID getWinner() {
        return winnerId;
    }
}
