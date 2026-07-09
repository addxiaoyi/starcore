package dev.starcore.starcore.module.dungeon;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 副本统计数据
 */
public class DungeonStatistics {
    private final AtomicLong totalCompletions = new AtomicLong(0);
    private final AtomicLong totalFailures = new AtomicLong(0);
    private final AtomicLong totalDeaths = new AtomicLong(0);
    private final AtomicLong totalPlayTime = new AtomicLong(0);
    private final AtomicLong totalGoldEarned = new AtomicLong(0);
    private final AtomicLong totalBossesDefeated = new AtomicLong(0);
    private final AtomicLong totalRoomsCleared = new AtomicLong(0);

    public void recordCompletion() {
        totalCompletions.incrementAndGet();
    }

    public void recordFailure() {
        totalFailures.incrementAndGet();
    }

    public void recordDeath() {
        totalDeaths.incrementAndGet();
    }

    public void addPlayTime(long seconds) {
        totalPlayTime.addAndGet(seconds);
    }

    public void addGoldEarned(long gold) {
        totalGoldEarned.addAndGet(gold);
    }

    public void recordBossDefeated() {
        totalBossesDefeated.incrementAndGet();
    }

    public void recordRoomCleared() {
        totalRoomsCleared.incrementAndGet();
    }

    public long getTotalCompletions() {
        return totalCompletions.get();
    }

    public long getTotalFailures() {
        return totalFailures.get();
    }

    public long getTotalDeaths() {
        return totalDeaths.get();
    }

    public long getTotalPlayTime() {
        return totalPlayTime.get();
    }

    public long getTotalGoldEarned() {
        return totalGoldEarned.get();
    }

    public long getTotalBossesDefeated() {
        return totalBossesDefeated.get();
    }

    public long getTotalRoomsCleared() {
        return totalRoomsCleared.get();
    }

    public double getSuccessRate() {
        long total = totalCompletions.get() + totalFailures.get();
        if (total == 0) return 0;
        return (double) totalCompletions.get() / total * 100;
    }

    public String getFormattedPlayTime() {
        long seconds = totalPlayTime.get();
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        return String.format("%dh %dm", hours, minutes);
    }
}
