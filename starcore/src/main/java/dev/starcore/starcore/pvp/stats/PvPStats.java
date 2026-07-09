package dev.starcore.starcore.pvp.stats;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * PvP统计数据
 */
public final class PvPStats {
    private final UUID playerId;

    // 基础统计
    private final AtomicInteger kills = new AtomicInteger(0);
    private final AtomicInteger deaths = new AtomicInteger(0);
    private final AtomicInteger assists = new AtomicInteger(0);

    // 连杀统计
    private final AtomicInteger currentKillStreak = new AtomicInteger(0);
    private final AtomicInteger bestKillStreak = new AtomicInteger(0);

    // 伤害统计
    private final AtomicLong totalDamage = new AtomicLong(0);
    private final AtomicLong totalDamageDealt = new AtomicLong(0);
    private final AtomicLong totalDamageTaken = new AtomicLong(0);

    // 决斗统计
    private final AtomicInteger duelWins = new AtomicInteger(0);
    private final AtomicInteger duelLosses = new AtomicInteger(0);

    public PvPStats(UUID playerId) {
        this.playerId = playerId;
    }

    /**
     * 增加击杀
     */
    public void addKill() {
        kills.incrementAndGet();
        int streak = currentKillStreak.incrementAndGet();
        if (streak > bestKillStreak.get()) {
            bestKillStreak.set(streak);
        }
    }

    /**
     * 增加死亡
     */
    public void addDeath() {
        deaths.incrementAndGet();
        currentKillStreak.set(0);
    }

    /**
     * 增加助攻
     */
    public void addAssist() {
        assists.incrementAndGet();
    }

    /**
     * 增加伤害
     */
    public void addDamage(long damage) {
        totalDamage.addAndGet(damage);
        totalDamageDealt.addAndGet(damage);
    }

    /**
     * 增加受到的伤害
     */
    public void addDamageTaken(long damage) {
        totalDamageTaken.addAndGet(damage);
    }

    /**
     * 增加决斗胜利
     */
    public void addDuelWin() {
        duelWins.incrementAndGet();
    }

    /**
     * 增加决斗失败
     */
    public void addDuelLoss() {
        duelLosses.incrementAndGet();
    }

    /**
     * 计算K/D比率
     */
    public double getKDRatio() {
        int d = deaths.get();
        return d > 0 ? (double) kills.get() / d : kills.get();
    }

    /**
     * 计算KDA
     * KDA = (击杀 + 助攻) / 死亡
     */
    public double getKDA() {
        int d = deaths.get();
        return d > 0 ? (double) (kills.get() + assists.get()) / d : (kills.get() + assists.get());
    }

    /**
     * 计算决斗胜率
     */
    public double getDuelWinRate() {
        int total = duelWins.get() + duelLosses.get();
        return total > 0 ? (double) duelWins.get() / total * 100 : 0;
    }

    /**
     * 计算平均伤害
     */
    public double getAverageDamage() {
        int k = kills.get();
        return k > 0 ? (double) totalDamage.get() / k : 0;
    }

    // Getters
    public UUID getPlayerId() { return playerId; }
    public int getKills() { return kills.get(); }
    public int getDeaths() { return deaths.get(); }
    public int getAssists() { return assists.get(); }
    public long getTotalDamage() { return totalDamage.get(); }
    public long getTotalDamageDealt() { return totalDamageDealt.get(); }
    public long getTotalDamageTaken() { return totalDamageTaken.get(); }
    public int getCurrentKillStreak() { return currentKillStreak.get(); }
    public int getBestKillStreak() { return bestKillStreak.get(); }
    public int getDuelWins() { return duelWins.get(); }
    public int getDuelLosses() { return duelLosses.get(); }

    /**
     * 获取本周击杀数（当前实现返回总击杀数，周数据需要在数据库中追踪）
     */
    public int getWeeklyKills() { return kills.get(); }

    /**
     * 获取本周死亡数（当前实现返回总死亡数，周数据需要在数据库中追踪）
     */
    public int getWeeklyDeaths() { return deaths.get(); }

    // 包内方法用于从 JSON 加载数据
    void applyLoadedDamage(long dealt, long taken) {
        totalDamageDealt.set(dealt);
        totalDamageTaken.set(taken);
        totalDamage.set(dealt);
    }

    void applyLoadedBestKillStreak(int streak) {
        bestKillStreak.set(streak);
    }
}
