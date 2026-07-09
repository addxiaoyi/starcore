package dev.starcore.starcore.module.army.raid.model;

import dev.starcore.starcore.module.nation.model.NationId;

import java.time.Instant;
import java.util.UUID;

/**
 * 突袭参与者数据模型
 */
public record RaidParticipant(
    UUID playerId,
    String playerName,
    NationId nationId,
    Instant joinedAt,
    Instant lastSeenAt,
    int kills,
    int deaths,
    double contributionScore,
    boolean isOnline
) {
    public static RaidParticipant create(UUID playerId, String playerName, NationId nationId) {
        return new RaidParticipant(
            playerId,
            playerName,
            nationId,
            Instant.now(),
            Instant.now(),
            0,
            0,
            0.0,
            true
        );
    }

    public RaidParticipant withKill() {
        return new RaidParticipant(
            playerId, playerName, nationId, joinedAt, Instant.now(),
            kills + 1, deaths, contributionScore + 10.0, isOnline
        );
    }

    public RaidParticipant withDeath() {
        return new RaidParticipant(
            playerId, playerName, nationId, joinedAt, Instant.now(),
            kills, deaths + 1, contributionScore - 5.0, isOnline
        );
    }

    public RaidParticipant withContribution(double score) {
        return new RaidParticipant(
            playerId, playerName, nationId, joinedAt, Instant.now(),
            kills, deaths, contributionScore + score, isOnline
        );
    }

    public RaidParticipant withOnlineStatus(boolean online) {
        return new RaidParticipant(
            playerId, playerName, nationId, joinedAt, lastSeenAt,
            kills, deaths, contributionScore, online
        );
    }

    public RaidParticipant markSeen() {
        return new RaidParticipant(
            playerId, playerName, nationId, joinedAt, Instant.now(),
            kills, deaths, contributionScore, isOnline
        );
    }
}