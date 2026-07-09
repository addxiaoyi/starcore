package dev.starcore.starcore.module.territory.upgrade.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Represents an ongoing upgrade process.
 * 进行中的升级进程
 */
public record UpgradeProcess(
    String pathId,
    int targetLevel,
    int currentExp,
    int targetExp,
    Instant startedAt,
    Instant estimatedCompletion,
    boolean isCompleted
) {
    public UpgradeProcess {
        Objects.requireNonNull(pathId, "pathId cannot be null");
        if (targetLevel < 0) {
            throw new IllegalArgumentException("targetLevel cannot be negative");
        }
        if (currentExp < 0) {
            throw new IllegalArgumentException("currentExp cannot be negative");
        }
        if (targetExp < 0) {
            throw new IllegalArgumentException("targetExp cannot be negative");
        }
    }

    /**
     * Create a new upgrade process.
     */
    public static UpgradeProcess start(String pathId, int targetLevel, int targetExp) {
        Instant now = Instant.now();
        return new UpgradeProcess(
            pathId,
            targetLevel,
            0,
            targetExp,
            now,
            null,
            false
        );
    }

    /**
     * Get progress percentage (0-100).
     */
    public int getProgressPercent() {
        if (targetExp <= 0) {
            return 100;
        }
        int percent = (currentExp * 100) / targetExp;
        return Math.min(100, Math.max(0, percent));
    }

    /**
     * Get remaining exp needed.
     */
    public int getRemainingExp() {
        return Math.max(0, targetExp - currentExp);
    }

    /**
     * Add experience to this process.
     */
    public UpgradeProcess addExp(int exp) {
        int newExp = this.currentExp + exp;
        boolean completed = newExp >= targetExp;
        return new UpgradeProcess(
            pathId,
            targetLevel,
            completed ? targetExp : newExp,
            targetExp,
            startedAt,
            completed ? Instant.now() : estimatedCompletion,
            completed
        );
    }

    /**
     * Check if this process can complete with given exp.
     */
    public boolean canCompleteWith(int exp) {
        return currentExp + exp >= targetExp;
    }

    /**
     * Get time remaining in seconds (estimated).
     */
    public long getEstimatedSecondsRemaining() {
        if (estimatedCompletion != null) {
            long seconds = estimatedCompletion.getEpochSecond() - Instant.now().getEpochSecond();
            return Math.max(0, seconds);
        }
        return -1; // Unknown
    }
}
