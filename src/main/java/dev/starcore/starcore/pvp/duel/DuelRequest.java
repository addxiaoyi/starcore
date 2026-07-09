package dev.starcore.starcore.pvp.duel;

import java.util.UUID;

/**
 * 决斗请求记录
 */
public record DuelRequest(
    UUID id,
    UUID challengerId,
    UUID opponentId,
    double wager,
    String kitName,
    int bestOf,
    long timestamp,
    long timeout
) {}