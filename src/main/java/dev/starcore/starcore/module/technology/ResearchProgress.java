package dev.starcore.starcore.module.technology;

import org.bukkit.scheduler.BukkitTask;

import java.time.Instant;

/**
 * Represents an ongoing research operation.
 * Note: scheduledTask is not serialized - it needs to be recreated on load.
 */
public final class ResearchProgress {
    private final String technologyKey;
    private final Instant startTime;
    private final Instant estimatedCompletion;
    private final int totalTicks;
    private final int remainingTicks;
    private transient BukkitTask scheduledTask;  // Not serialized - restored on loadState()

    public ResearchProgress(String technologyKey, Instant startTime, Instant estimatedCompletion,
                          int totalTicks, int remainingTicks, BukkitTask scheduledTask) {
        this.technologyKey = technologyKey;
        this.startTime = startTime;
        this.estimatedCompletion = estimatedCompletion;
        this.totalTicks = totalTicks;
        this.remainingTicks = remainingTicks;
        this.scheduledTask = scheduledTask;
    }

    public String technologyKey() {
        return technologyKey;
    }

    public Instant startTime() {
        return startTime;
    }

    public Instant estimatedCompletion() {
        return estimatedCompletion;
    }

    public int totalTicks() {
        return totalTicks;
    }

    public int remainingTicks() {
        return remainingTicks;
    }

    public BukkitTask scheduledTask() {
        return scheduledTask;
    }

    public void setScheduledTask(BukkitTask task) {
        this.scheduledTask = task;
    }

    public double getProgress() {
        if (totalTicks <= 0) return 1.0;
        return 1.0 - ((double) remainingTicks / totalTicks);
    }

    public long getRemainingSeconds() {
        return remainingTicks / 20L;
    }

    /**
     * Creates a copy with a new remaining tick count and null task.
     */
    public ResearchProgress withRemainingTicks(int newRemaining) {
        return new ResearchProgress(
            technologyKey, startTime, estimatedCompletion, totalTicks, newRemaining, null
        );
    }

    /**
     * Creates a copy with a new scheduled task.
     */
    public ResearchProgress withTask(BukkitTask task) {
        return new ResearchProgress(
            technologyKey, startTime, estimatedCompletion, totalTicks, remainingTicks, task
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ResearchProgress that = (ResearchProgress) o;
        return totalTicks == that.totalTicks &&
               remainingTicks == that.remainingTicks &&
               java.util.Objects.equals(technologyKey, that.technologyKey) &&
               java.util.Objects.equals(startTime, that.startTime) &&
               java.util.Objects.equals(estimatedCompletion, that.estimatedCompletion);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(technologyKey, startTime, estimatedCompletion, totalTicks, remainingTicks);
    }

    @Override
    public String toString() {
        return "ResearchProgress{" +
               "technologyKey='" + technologyKey + '\'' +
               ", startTime=" + startTime +
               ", estimatedCompletion=" + estimatedCompletion +
               ", totalTicks=" + totalTicks +
               ", remainingTicks=" + remainingTicks +
               '}';
    }
}
