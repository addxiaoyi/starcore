package dev.starcore.starcore.module.technology;

import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Represents an ongoing research operation.
 * Note: scheduledTask is not serialized - it needs to be recreated on load.
 */
public final class ResearchProgress {
    private final String technologyKey;
    private final Instant startTime;
    private final Instant estimatedCompletion;
    private final int totalTicks;
    private transient BukkitTask scheduledTask;

    // 缓存的计算值，避免每次计算
    private final Integer cachedRemainingTicks;

    public ResearchProgress(String technologyKey, Instant startTime, Instant estimatedCompletion,
                          int totalTicks, int remainingTicks, BukkitTask scheduledTask) {
        this.technologyKey = technologyKey;
        this.startTime = startTime;
        this.estimatedCompletion = estimatedCompletion;
        this.totalTicks = totalTicks;
        this.scheduledTask = scheduledTask;
        this.cachedRemainingTicks = remainingTicks;
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

    /**
     * 获取剩余 ticks。
     * 如果是从持久化恢复的研究任务，通过 estimatedCompletion 重新计算。
     * 如果 scheduledTask 为 null（持久化恢复），使用动态计算。
     * 否则使用缓存值。
     */
    public int remainingTicks() {
        // 如果有正在运行的任务，使用缓存值
        if (scheduledTask != null && cachedRemainingTicks != null) {
            return cachedRemainingTicks;
        }

        // 从 estimatedCompletion 动态计算（用于持久化恢复后的场景）
        return calculateRemainingTicksFromCompletion();
    }

    /**
     * 从 estimatedCompletion 计算剩余 ticks
     */
    private int calculateRemainingTicksFromCompletion() {
        Instant now = Instant.now();
        if (now.isAfter(estimatedCompletion)) {
            return 0;
        }
        Duration duration = Duration.between(now, estimatedCompletion);
        long remainingSeconds = duration.getSeconds();
        return (int) Math.min(remainingSeconds * 20L, Integer.MAX_VALUE - 1);
    }

    /**
     * 获取剩余秒数（动态计算）
     */
    public long getRemainingSeconds() {
        return remainingTicks() / 20L;
    }

    public BukkitTask scheduledTask() {
        return scheduledTask;
    }

    public void setScheduledTask(BukkitTask task) {
        this.scheduledTask = task;
    }

    public double getProgress() {
        int remaining = remainingTicks();
        if (totalTicks <= 0) return 1.0;
        return 1.0 - ((double) remaining / totalTicks);
    }

    /**
     * 检查研究是否已超时（用于持久化恢复后的超时检测）
     */
    public boolean isExpired() {
        return Instant.now().isAfter(estimatedCompletion);
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
            technologyKey, startTime, estimatedCompletion, totalTicks, cachedRemainingTicks != null ? cachedRemainingTicks : calculateRemainingTicksFromCompletion(), task
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ResearchProgress that = (ResearchProgress) o;
        return totalTicks == that.totalTicks &&
               remainingTicks() == that.remainingTicks() &&
               Objects.equals(technologyKey, that.technologyKey) &&
               Objects.equals(startTime, that.startTime) &&
               Objects.equals(estimatedCompletion, that.estimatedCompletion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(technologyKey, startTime, estimatedCompletion, totalTicks, remainingTicks());
    }

    @Override
    public String toString() {
        return "ResearchProgress{" +
               "technologyKey='" + technologyKey + '\'' +
               ", startTime=" + startTime +
               ", estimatedCompletion=" + estimatedCompletion +
               ", totalTicks=" + totalTicks +
               ", remainingTicks=" + remainingTicks() +
               '}';
    }
}
