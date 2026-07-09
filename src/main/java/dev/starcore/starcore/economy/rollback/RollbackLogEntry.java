package dev.starcore.starcore.economy.rollback;

import java.time.LocalDateTime;

/**
 * 回滚日志条目
 */
public record RollbackLogEntry(
    String rollbackId,
    LocalDateTime timestamp,
    String operator,
    int transactionCount,
    String reason,
    String status,
    String errorMessage
) {
    @Override
    public String toString() {
        return String.format("[%s] Operator: %s, Count: %d, Status: %s, Reason: %s",
            timestamp, operator, transactionCount, status, reason);
    }
}
