package dev.starcore.starcore.ranking;

import java.util.List;
import java.util.UUID;

/**
 * 排行榜数据库接口
 * 具体实现可以是MySQL、SQLite或H2
 */
public interface RankingDatabase {

    /**
     * 查询玩家排名
     */
    int queryPosition(UUID player, String statType, RankPeriod period);

    /**
     * 查询玩家统计数据
     */
    PlayerRankingData queryStats(UUID player);

    /**
     * 查询排行榜大小
     */
    int querySize(String statType);

    /**
     * 更新玩家统计（设置绝对值）
     *
     * 增量Delta算法：
     * - value: 总值（ALLTIME）
     * - delta: 本周期增量
     * - lasttotal: 上次重置时的总值
     *
     * 计算公式：
     * delta = value - lasttotal
     *
     * 重置时：
     * lasttotal = value
     * delta = 0
     */
    void updateStats(UUID player, String statType, RankPeriod period, long newValue);

    /**
     * 增量更新玩家统计（累加）
     * 适用于击杀、死亡、在线时间等累计统计
     *
     * @param player 玩家UUID
     * @param statType 统计类型
     * @param period 时间周期
     * @param delta 增量值
     */
    default void updateStatsWithDelta(UUID player, String statType, RankPeriod period, long delta) {
        // 默认实现：查询当前值后累加
        // 子类可优化为单条 SQL
    }

    /**
     * Persist a player's all-time online time in seconds.
     */
    default void saveOnlineTime(UUID player, long totalSeconds) {
        updateStats(player, "ONLINE_TIME", RankPeriod.ALLTIME, totalSeconds);
    }

    /**
     * 批量增量更新
     */
    default void batchUpdateWithDelta(List<RankingUpdate> updates) {
        // 默认实现：逐条更新
        // 子类可优化为批量 SQL
        for (RankingUpdate update : updates) {
            updateStatsWithDelta(update.player(), update.statType(), update.period(), update.delta());
        }
    }

    /**
     * 重置周期排行榜
     */
    void resetPeriod(String statType, RankPeriod period);

    /**
     * 批量更新（优化性能）
     */
    void batchUpdate(List<RankingUpdate> updates);

    /**
     * 获取 Top N 玩家
     */
    List<RankingEntry> getTopPlayers(String statType, RankPeriod period, int limit);

    /**
     * 排行榜条目
     */
    record RankingEntry(int position, UUID playerId, long score) {}

    /**
     * 排行榜更新记录（带增量）
     */
    record RankingUpdate(
        UUID player,
        String statType,
        RankPeriod period,
        long delta
    ) {}
}
