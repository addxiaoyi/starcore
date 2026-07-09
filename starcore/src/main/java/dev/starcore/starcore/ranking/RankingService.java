package dev.starcore.starcore.ranking;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 排名服务接口
 * 提供统一的排名数据查询接口
 */
public interface RankingService {

    /**
     * 获取玩家排名（击杀榜）
     * @param playerId 玩家UUID
     * @param period 时间周期
     * @return 排名位置，未上榜返回-1
     */
    CompletableFuture<Integer> getKillRank(UUID playerId, RankPeriod period);

    /**
     * 获取玩家排名（K/D比率榜）
     * @param playerId 玩家UUID
     * @param period 时间周期
     * @return 排名位置，未上榜返回-1
     */
    default CompletableFuture<Integer> getKDRatioRank(UUID playerId, RankPeriod period) {
        return CompletableFuture.completedFuture(-1); // 默认返回未上榜
    }

    /**
     * 获取玩家排名（综合榜）
     * @param playerId 玩家UUID
     * @param period 时间周期
     * @return 排名位置，未上榜返回-1
     */
    CompletableFuture<Integer> getOverallRank(UUID playerId, RankPeriod period);

    /**
     * 获取玩家排名（在线时间榜）
     * @param playerId 玩家UUID
     * @param period 时间周期
     * @return 排名位置，未上榜返回-1
     */
    CompletableFuture<Integer> getOnlineTimeRank(UUID playerId, RankPeriod period);

    /**
     * 获取玩家综合排名
     * 综合考虑：击杀、在线时间、成就等
     * @param playerId 玩家UUID
     * @return 排名描述，如 "#5" 或 "未上榜"
     */
    default String getPlayerRankDisplay(UUID playerId) {
        return getOverallRank(playerId, RankPeriod.ALLTIME)
            .thenApply(rank -> rank > 0 ? "#" + rank : "未上榜")
            .join();
    }

    /**
     * 获取玩家排名位置（用于显示）
     * @param playerId 玩家UUID
     * @return 排名字符串
     */
    default String getRankPosition(UUID playerId) {
        return getKillRank(playerId, RankPeriod.ALLTIME)
            .thenApply(rank -> rank > 0 ? String.valueOf(rank) : "-")
            .join();
    }

    /**
     * 获取玩家击杀数
     * @param playerId 玩家UUID
     * @param period 时间周期
     * @return 击杀数
     */
    CompletableFuture<Long> getKillCount(UUID playerId, RankPeriod period);

    /**
     * 获取玩家死亡数
     * @param playerId 玩家UUID
     * @param period 时间周期
     * @return 死亡数
     */
    CompletableFuture<Long> getDeathCount(UUID playerId, RankPeriod period);

    /**
     * 获取玩家助攻数
     * @param playerId 玩家UUID
     * @param period 时间周期
     * @return 助攻数
     */
    default CompletableFuture<Long> getAssistCount(UUID playerId, RankPeriod period) {
        return CompletableFuture.completedFuture(0L); // 默认返回0
    }

    /**
     * 获取玩家在线时间（秒）
     * @param playerId 玩家UUID
     * @param period 时间周期
     * @return 在线时间（秒）
     */
    CompletableFuture<Long> getOnlineTime(UUID playerId, RankPeriod period);

    /**
     * 获取排行榜大小
     * @param statType 统计类型
     * @return 排行榜人数
     */
    int getLeaderboardSize(String statType);

    /**
     * 获取 Top N 玩家 UUID 列表
     * @param statType 统计类型 (kills/deaths/playtime/kdratio)
     * @param limit 返回数量上限
     * @return Top N 玩家 UUID 列表
     */
    default List<UUID> getTopN(String statType, int limit) {
        return new ArrayList<>(); // 返回空列表
    }

    /**
     * 获取 Top N 排名数据
     * @param statType 统计类型
     * @param limit 返回数量上限
     * @param period 时间周期
     * @return Top N 玩家排名数据列表
     */
    default List<TopPlayerData> getTopPlayers(String statType, int limit, RankPeriod period) {
        return new ArrayList<>(); // 返回空列表
    }

    /**
     * 玩家在线时间变化事件（供 RankingServiceImpl 内部使用）
     */
    default void onPlayerJoin(UUID playerId) {}

    /**
     * 玩家离线事件（供 RankingServiceImpl 内部使用）
     */
    default void onPlayerQuit(UUID playerId) {}

    /**
     * Top 玩家数据
     */
    record TopPlayerData(int position, UUID playerId, long value) {}

    /**
     * 获取玩家 K/D 比率
     * @param playerId 玩家UUID
     * @return K/D 比率
     */
    default double getKDRatio(UUID playerId) {
        return 0.0;
    }

    /**
     * 获取玩家 KDA 比率 (Kills + Assists) / Deaths
     * @param playerId 玩家UUID
     * @return KDA 比率
     */
    default double getKDARatio(UUID playerId) {
        return 0.0;
    }
}
