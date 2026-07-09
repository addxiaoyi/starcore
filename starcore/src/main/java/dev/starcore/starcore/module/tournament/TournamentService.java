package dev.starcore.starcore.module.tournament;

import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 锦标赛核心服务接口
 * 定义所有比赛相关的核心操作
 */
public interface TournamentService {

    // ==================== 生命周期 ====================

    /**
     * 获取指定类型的配置
     */
    TournamentConfig getConfig(TournamentType type);

    /**
     * 创建新比赛
     */
    Tournament createTournament(String name, TournamentType type, Player creator);

    /**
     * 加入比赛
     */
    boolean joinTournament(String tournamentId, Player player);

    /**
     * 离开比赛
     */
    boolean leaveTournament(Player player);

    /**
     * 开始比赛
     */
    boolean startTournament(String tournamentId);

    /**
     * 取消比赛
     */
    boolean cancelTournament(String tournamentId, String reason);

    /**
     * 传送玩家到观战区域
     */
    boolean teleportToSpectatorArea(Player player, String tournamentId);

    /**
     * 获取观战区域位置
     */
    Optional<org.bukkit.Location> getSpectatorLocation(String tournamentId);

    /**
     * 结束比赛并宣布获胜者
     */
    void finishTournament(String tournamentId, UUID winnerId);

    // ==================== 查询方法 ====================

    /**
     * 获取所有活跃比赛
     */
    List<Tournament> getActiveTournaments();

    /**
     * 获取指定比赛
     */
    Optional<Tournament> getTournament(String id);

    /**
     * 获取玩家当前所在的比赛
     */
    Optional<Tournament> getPlayerTournament(Player player);

    /**
     * 检查玩家是否在某比赛中
     */
    boolean isPlayerInTournament(UUID playerId);

    /**
     * 获取玩家排名
     */
    int getPlayerRank(String tournamentId, UUID playerId);

    // ==================== 比赛逻辑 ====================

    /**
     * 记录击杀
     */
    void recordKill(String tournamentId, UUID killer, UUID victim);

    /**
     * 记录完成时间（竞速赛）
     */
    void recordCompletion(String tournamentId, UUID player, long timeMillis);

    // ==================== 持久化 ====================

    /**
     * 保存所有比赛数据
     */
    void saveAllTournaments();

    /**
     * 取消所有比赛（关闭时调用）
     */
    void cancelAllMatches();

    /**
     * 关闭服务
     */
    void shutdown();

    // ==================== 快照/统计 ====================

    /**
     * 获取比赛快照列表
     */
    Collection<TournamentSnapshot> getActiveSnapshots();

    /**
     * 获取统计数据
     */
    TournamentStats getStats();

    // ==================== 数据记录 ====================

    /**
     * 比赛快照记录
     */
    record TournamentSnapshot(
        String id,
        String name,
        TournamentType type,
        TournamentStatus status,
        int participantCount,
        int maxParticipants,
        long createdAt
    ) {}

    /**
     * 锦标赛统计数据
     */
    record TournamentStats(
        int activeTournaments,
        int totalParticipants,
        long totalMatches,
        double totalPrizePool
    ) {}
}
