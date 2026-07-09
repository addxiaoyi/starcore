package dev.starcore.starcore.pvp.stats;

import java.util.UUID;

/**
 * PvP统计数据（用于JSON序列化）
 */
public final class PvPStatsData {
    public String playerId;

    // 基础统计
    public int kills;
    public int deaths;
    public int assists;

    // 连杀统计
    public int currentKillStreak;
    public int bestKillStreak;

    // 伤害统计
    public long totalDamage;
    public long totalDamageDealt;
    public long totalDamageTaken;

    // 决斗统计
    public int duelWins;
    public int duelLosses;

    // 时间戳
    public long lastUpdated;

    public PvPStatsData() {
        // 空构造函数供 Gson 使用
    }

    /**
     * 从 PvPStats 转换为 PvPStatsData
     */
    public static PvPStatsData fromStats(PvPStats stats) {
        PvPStatsData data = new PvPStatsData();
        data.playerId = stats.getPlayerId().toString();
        data.kills = stats.getKills();
        data.deaths = stats.getDeaths();
        data.assists = stats.getAssists();
        data.currentKillStreak = stats.getCurrentKillStreak();
        data.bestKillStreak = stats.getBestKillStreak();
        data.totalDamage = stats.getTotalDamage();
        data.totalDamageDealt = stats.getTotalDamageDealt();
        data.totalDamageTaken = stats.getTotalDamageTaken();
        data.duelWins = stats.getDuelWins();
        data.duelLosses = stats.getDuelLosses();
        data.lastUpdated = System.currentTimeMillis();
        return data;
    }

    /**
     * 从 PvPStatsData 转换为 PvPStats
     */
    public static PvPStats toStats(PvPStatsData data) {
        if (data == null || data.playerId == null) {
            return null;
        }
        try {
            UUID playerId = UUID.fromString(data.playerId);
            PvPStats stats = new PvPStats(playerId);

            // 设置基础统计
            for (int i = 0; i < data.kills; i++) stats.addKill();
            for (int i = 0; i < data.deaths; i++) stats.addDeath();
            for (int i = 0; i < data.assists; i++) stats.addAssist();

            // 设置伤害统计
            if (data.totalDamageDealt > 0) {
                // 直接操作私有字段（通过反射或添加包内方法）
                stats.applyLoadedDamage(data.totalDamageDealt, data.totalDamageTaken);
            }

            // 设置决斗统计
            for (int i = 0; i < data.duelWins; i++) stats.addDuelWin();
            for (int i = 0; i < data.duelLosses; i++) stats.addDuelLoss();

            // 设置连杀统计
            if (data.bestKillStreak > 0) {
                stats.applyLoadedBestKillStreak(data.bestKillStreak);
            }

            return stats;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
