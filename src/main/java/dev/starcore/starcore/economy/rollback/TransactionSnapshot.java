package dev.starcore.starcore.economy.rollback;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 交易快照记录
 */
public record TransactionSnapshot(
    String transactionId,
    LocalDateTime timestamp,
    TransactionType transactionType,
    UUID fromPlayer,
    UUID toPlayer,
    BigDecimal amount,
    BigDecimal fromBalanceBefore,
    BigDecimal toBalanceBefore,
    BigDecimal fromBalanceAfter,
    BigDecimal toBalanceAfter,
    String metadata,
    boolean rolledBack,
    LocalDateTime rollbackTimestamp,
    String rollbackReason
) {
    @Override
    public String toString() {
        return String.format("[%s] %s: %s -> %s, Amount: %s %s",
            timestamp,
            transactionType,
            fromPlayer,
            toPlayer,
            amount,
            rolledBack ? "[ROLLED BACK]" : ""
        );
    }
}
