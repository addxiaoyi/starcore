package dev.starcore.starcore.audit;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 审计日志条目
 */
public record AuditLogEntry(
    long id,
    LocalDateTime timestamp,
    UUID playerId,
    String playerName,
    AuditActionType actionType,
    String actionDetail,
    boolean blocked,
    String ipAddress,
    String world,
    int x,
    int y,
    int z
) {
    @Override
    public String toString() {
        return String.format("[%s] %s (%s) - %s: %s %s",
            timestamp,
            playerName,
            playerId,
            actionType,
            actionDetail,
            blocked ? "[BLOCKED]" : ""
        );
    }
}
