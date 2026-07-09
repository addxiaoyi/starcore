package dev.starcore.starcore.module.army.raid.model;

import java.time.Instant;
import java.util.UUID;

/**
 * 突袭事件记录
 */
public record RaidEvent(
    Instant timestamp,
    RaidEventType type,
    UUID playerId,
    UUID targetId,
    String description
) {
}