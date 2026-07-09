package dev.starcore.starcore.module.tournament.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 比赛统计信息记录
 */
public record TournamentStatsRecord(
    int totalTournaments,
    int activeTournaments,
    int completedTournaments,
    int cancelledTournaments,
    int totalParticipants,
    double totalPrizePool,
    List<PlayerStats> topPlayers
) {
    /**
     * 玩家统计数据
     */
    public record PlayerStats(
        UUID playerId,
        String playerName,
        int tournamentsPlayed,
        int tournamentsWon,
        int totalKills,
        double winRate
    ) {}
}
