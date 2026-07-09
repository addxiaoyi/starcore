package dev.starcore.starcore.economy.rollback;

/**
 * 回滚结果
 */
public record RollbackResult(
    boolean success,
    String message,
    int affectedCount
) {
    @Override
    public String toString() {
        return String.format("RollbackResult[success=%s, affected=%d, message=%s]",
            success, affectedCount, message);
    }
}
