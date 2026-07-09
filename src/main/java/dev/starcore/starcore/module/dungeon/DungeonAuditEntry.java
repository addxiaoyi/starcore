package dev.starcore.starcore.module.dungeon;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * 副本审计日志条目
 */
public record DungeonAuditEntry(
    UUID entryId,
    UUID instanceId,
    UUID playerId,
    String playerName,
    DungeonAuditAction action,
    String details,
    Instant timestamp,
    String worldLocation,
    Map<String, Object> metadata
) {
    /**
     * 创建审计日志条目
     */
    public static DungeonAuditEntry create(
        UUID instanceId,
        UUID playerId,
        String playerName,
        DungeonAuditAction action,
        String details
    ) {
        return new DungeonAuditEntry(
            UUID.randomUUID(),
            instanceId,
            playerId,
            playerName,
            action,
            details,
            Instant.now(),
            null,
            Map.of()
        );
    }

    /**
     * 创建带位置的审计日志条目
     */
    public static DungeonAuditEntry createWithLocation(
        UUID instanceId,
        UUID playerId,
        String playerName,
        DungeonAuditAction action,
        String details,
        String worldLocation
    ) {
        return new DungeonAuditEntry(
            UUID.randomUUID(),
            instanceId,
            playerId,
            playerName,
            action,
            details,
            Instant.now(),
            worldLocation,
            Map.of()
        );
    }
}
