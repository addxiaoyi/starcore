package dev.starcore.starcore.ai.matchmaking;
import java.util.Optional;

import dev.starcore.starcore.pvp.stats.PvPStats;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 智能匹配服务
 * 基于K/D、胜率等数据进行公平匹配
 */
public final class MatchmakingService {
    // 匹配队列 UUID -> MatchmakingPlayer
    private final ConcurrentHashMap<UUID, MatchmakingPlayer> queue =
        new ConcurrentHashMap<>();

    // 匹配历史（防止重复匹配）
    private final Map<UUID, Set<UUID>> recentMatches = new ConcurrentHashMap<>();

    /**
     * 加入匹配队列
     */
    public boolean joinQueue(UUID playerId, PvPStats stats) {
        if (queue.containsKey(playerId)) {
            return false;
        }

        MatchmakingPlayer player = new MatchmakingPlayer(
            playerId,
            calculateMatchmakingRating(stats),
            System.currentTimeMillis()
        );

        queue.put(playerId, player);
        return true;
    }

    /**
     * 离开匹配队列
     */
    public boolean leaveQueue(UUID playerId) {
        return queue.remove(playerId) != null;
    }

    /**
     * 寻找匹配
     */
    public Optional<MatchResult> findMatch(UUID playerId) {
        MatchmakingPlayer player = queue.get(playerId);
        if (player == null) {
            return Optional.empty();
        }

        // 寻找最佳对手
        MatchmakingPlayer bestOpponent = null;
        double bestScore = Double.MAX_VALUE;

        for (MatchmakingPlayer opponent : queue.values()) {
            if (opponent.playerId().equals(playerId)) {
                continue;
            }

            // 检查最近是否匹配过
            if (hasRecentMatch(playerId, opponent.playerId())) {
                continue;
            }

            // 计算匹配分数
            double score = calculateMatchScore(player, opponent);

            if (score < bestScore) {
                bestScore = score;
                bestOpponent = opponent;
            }
        }

        // 检查是否找到合适的对手
        if (bestOpponent != null && bestScore < getAcceptableScoreDifference(player)) {
            // 移除队列
            queue.remove(playerId);
            queue.remove(bestOpponent.playerId());

            // 记录匹配历史
            recordMatch(playerId, bestOpponent.playerId());

            return Optional.of(new MatchResult(
                player.playerId(),
                bestOpponent.playerId(),
                bestScore
            ));
        }

        return Optional.empty();
    }

    /**
     * 计算匹配评分
     */
    private double calculateMatchmakingRating(PvPStats stats) {
        // 权重配置
        final double KD_WEIGHT = 0.4;
        final double WINRATE_WEIGHT = 0.3;
        final double LEVEL_WEIGHT = 0.2;
        final double PLAYTIME_WEIGHT = 0.1;

        // 归一化各项指标（0-100）
        double kdScore = Math.min(stats.getKDRatio() * 20, 100);
        double winrateScore = stats.getDuelWinRate();
        double levelScore = Math.min((stats.getKills() + stats.getDuelWins()) / 10.0, 100);
        double playtimeScore = Math.min((stats.getKills() + stats.getDeaths()) / 10.0, 100); // 使用击杀+死亡作为经验指标


        // 加权计算
        return kdScore * KD_WEIGHT +
               winrateScore * WINRATE_WEIGHT +
               levelScore * LEVEL_WEIGHT +
               playtimeScore * PLAYTIME_WEIGHT;
    }

    /**
     * 计算匹配分数（越小越好）
     */
    private double calculateMatchScore(MatchmakingPlayer p1, MatchmakingPlayer p2) {
        // 评分差距
        double ratingDiff = Math.abs(p1.rating() - p2.rating());

        // 等待时间因素（等待越久，容忍度越高）
        long waitTime1 = System.currentTimeMillis() - p1.joinTime();
        long waitTime2 = System.currentTimeMillis() - p2.joinTime();
        double avgWaitTime = (waitTime1 + waitTime2) / 2.0;

        // 等待时间越长，评分差距的惩罚越小
        double waitTimeFactor = 1.0 - Math.min(avgWaitTime / 120000.0, 0.5); // 2分钟后降低50%要求

        return ratingDiff * waitTimeFactor;
    }

    /**
     * 获取可接受的分数差异
     */
    private double getAcceptableScoreDifference(MatchmakingPlayer player) {
        long waitTime = System.currentTimeMillis() - player.joinTime();

        // 基础差异：100分
        double baseDiff = 100;

        // 等待0-30秒：100分
        // 等待30-60秒：150分
        // 等待60-120秒：200分
        // 等待120秒+：300分

        if (waitTime < 30000) {
            return baseDiff;
        } else if (waitTime < 60000) {
            return baseDiff * 1.5;
        } else if (waitTime < 120000) {
            return baseDiff * 2.0;
        } else {
            return baseDiff * 3.0;
        }
    }

    /**
     * 检查最近是否匹配过
     */
    private boolean hasRecentMatch(UUID player1, UUID player2) {
        Set<UUID> recent = recentMatches.get(player1);
        return recent != null && recent.contains(player2);
    }

    /**
     * 记录匹配历史
     */
    private void recordMatch(UUID player1, UUID player2) {
        recentMatches.computeIfAbsent(player1, k -> ConcurrentHashMap.newKeySet()).add(player2);
        recentMatches.computeIfAbsent(player2, k -> ConcurrentHashMap.newKeySet()).add(player1);

        // 只保留最近5场
        cleanupRecentMatches(player1);
        cleanupRecentMatches(player2);
    }

    /**
     * 清理匹配历史
     */
    private void cleanupRecentMatches(UUID playerId) {
        Set<UUID> recent = recentMatches.get(playerId);
        if (recent != null && recent.size() > 5) {
            // 移除最旧的记录
            Iterator<UUID> it = recent.iterator();
            it.next();
            it.remove();
        }
    }

    /**
     * 获取队列大小
     */
    public int getQueueSize() {
        return queue.size();
    }

    /**
     * 获取平均等待时间
     */
    public long getAverageWaitTime() {
        if (queue.isEmpty()) {
            return 0;
        }

        long now = System.currentTimeMillis();
        long totalWait = queue.values().stream()
            .mapToLong(p -> now - p.joinTime())
            .sum();

        return totalWait / queue.size();
    }

    /**
     * 匹配玩家
     */
    record MatchmakingPlayer(
        UUID playerId,
        double rating,
        long joinTime
    ) {}

    /**
     * 匹配结果
     */
    public record MatchResult(
        UUID player1,
        UUID player2,
        double matchScore
    ) {
        public boolean isPerfectMatch() {
            return matchScore < 100;
        }

        public boolean isGoodMatch() {
            return matchScore < 300;
        }
    }
}
