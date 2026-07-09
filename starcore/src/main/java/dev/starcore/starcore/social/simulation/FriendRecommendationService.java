package dev.starcore.starcore.social.simulation;

import dev.starcore.starcore.social.friend.FriendService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 好友推荐服务
 *
 * 提供智能好友推荐功能:
 * - 基于共同好友的推荐
 * - 基于社交圈子的推荐
 * - 基于玩家活跃度的推荐
 * - 推荐理由生成
 */
public class FriendRecommendationService {

    // 推荐配置
    private static final int MAX_RECOMMENDATIONS = 10;
    private static final int MIN_MUTUAL_FRIENDS_FOR_RECOMMENDATION = 1;
    private static final double MUTUAL_FRIEND_WEIGHT = 0.5;
    private static final double INFLUENCE_WEIGHT = 0.3;
    private static final double RECENCY_WEIGHT = 0.2;

    // 推荐缓存: playerId -> 推荐列表
    private final Map<UUID, List<FriendRecommendation>> recommendationCache = new ConcurrentHashMap<>();
    private long lastCacheUpdate = 0;
    private static final long CACHE_TTL = 300000; // 5分钟缓存

    private final JavaPlugin plugin;
    private final RelationshipNetwork relationshipNetwork;
    private final FriendService friendService;

    public FriendRecommendationService(JavaPlugin plugin, RelationshipNetwork relationshipNetwork, FriendService friendService) {
        this.plugin = plugin;
        this.relationshipNetwork = relationshipNetwork;
        this.friendService = friendService;
    }

    // ==================== 核心推荐方法 ====================

    /**
     * 获取玩家的推荐好友列表
     */
    public List<FriendRecommendation> getRecommendations(UUID playerId) {
        // 检查缓存
        if (isCacheValid(playerId)) {
            return recommendationCache.get(playerId);
        }

        // 计算新推荐
        List<FriendRecommendation> recommendations = calculateRecommendations(playerId);
        recommendationCache.put(playerId, recommendations);
        lastCacheUpdate = System.currentTimeMillis();

        return recommendations;
    }

    /**
     * 计算推荐好友列表
     */
    private List<FriendRecommendation> calculateRecommendations(UUID playerId) {
        Set<UUID> myFriends = friendService.getFriends(playerId);
        Set<UUID> blacklist = friendService.getBlacklist(playerId);
        Set<UUID> mySocialCircle = relationshipNetwork.getSocialCircle(playerId, 20);

        // 获取所有可能的候选人（朋友的朋友）
        Map<UUID, CandidateScore> candidates = new HashMap<>();

        // 1. 遍历我的好友，找他们的好友
        for (UUID friendId : myFriends) {
            Set<UUID> theirFriends = friendService.getFriends(friendId);
            for (UUID candidateId : theirFriends) {
                addCandidate(playerId, candidateId, candidates, friendId, myFriends, blacklist);
            }
        }

        // 2. 遍历我的社交圈中的玩家
        for (UUID circleId : mySocialCircle) {
            Set<UUID> theirFriends = friendService.getFriends(circleId);
            for (UUID candidateId : theirFriends) {
                addCandidate(playerId, candidateId, candidates, circleId, myFriends, blacklist);
            }
        }

        // 3. 排序并生成推荐
        return candidates.entrySet().stream()
            .sorted((a, b) -> Double.compare(b.getValue().totalScore, a.getValue().totalScore))
            .limit(MAX_RECOMMENDATIONS)
            .map(entry -> createRecommendation(entry.getKey(), entry.getValue()))
            .collect(Collectors.toList());
    }

    /**
     * 添加候选人到候选列表
     */
    private void addCandidate(UUID playerId, UUID candidateId, Map<UUID, CandidateScore> candidates,
                              UUID connectionId, Set<UUID> myFriends, Set<UUID> blacklist) {
        // 排除自己、已经是好友的、在黑名单的
        if (candidateId.equals(playerId) || myFriends.contains(candidateId) || blacklist.contains(candidateId)) {
            return;
        }

        CandidateScore score = candidates.computeIfAbsent(candidateId, k -> new CandidateScore());
        score.addConnection(connectionId);
    }

    /**
     * 创建推荐对象
     */
    private FriendRecommendation createRecommendation(UUID candidateId, CandidateScore score) {
        String playerName = getPlayerName(candidateId);
        int mutualFriends = score.connections.size();
        double matchScore = calculateMatchScore(score);
        String reason = generateReason(score);

        return new FriendRecommendation(candidateId, playerName, mutualFriends, matchScore, reason);
    }

    // ==================== 匹配度计算 ====================

    /**
     * 计算匹配分数
     */
    private double calculateMatchScore(CandidateScore score) {
        // 共同好友分数 (0-1)
        double mutualFriendScore = Math.min(score.connections.size() / 5.0, 1.0);

        // 影响力分数 (0-1)
        double influenceScore = Math.min(relationshipNetwork.calculateInfluenceScore(score.connections.iterator().next()) / 1000.0, 1.0);

        // 综合分数
        return (mutualFriendScore * MUTUAL_FRIEND_WEIGHT +
                influenceScore * INFLUENCE_WEIGHT) * 100;
    }

    /**
     * 生成推荐理由
     */
    private String generateReason(CandidateScore score) {
        int mutualCount = score.connections.size();

        if (mutualCount >= 4) {
            return "你们有 " + mutualCount + " 个共同好友，社交圈高度重合";
        } else if (mutualCount >= 2) {
            return "你们有 " + mutualCount + " 个共同好友，可能志趣相投";
        } else if (mutualCount == 1) {
            return "你们有 1 个共同好友 " + getPlayerName(score.connections.iterator().next());
        } else {
            return "根据你们的社交圈子分析，你们可能合得来";
        }
    }

    // ==================== 共同好友计算 ====================

    /**
     * 计算两个玩家之间的共同好友数量
     */
    public int getMutualFriendsCount(UUID player1, UUID player2) {
        Set<UUID> p1Friends = friendService.getFriends(player1);
        Set<UUID> p2Friends = friendService.getFriends(player2);

        Set<UUID> mutual = new HashSet<>(p1Friends);
        mutual.retainAll(p2Friends);
        return mutual.size();
    }

    /**
     * 获取两个玩家之间的共同好友列表
     */
    public Set<UUID> getMutualFriendsList(UUID player1, UUID player2) {
        Set<UUID> p1Friends = friendService.getFriends(player1);
        Set<UUID> p2Friends = friendService.getFriends(player2);

        Set<UUID> mutual = new HashSet<>(p1Friends);
        mutual.retainAll(p2Friends);
        return mutual;
    }

    // ==================== 社交圈子分析 ====================

    /**
     * 分析玩家的社交圈子
     */
    public SocialCircleAnalysis analyzeSocialCircle(UUID playerId) {
        Set<UUID> friends = friendService.getFriends(playerId);
        Set<UUID> socialCircle = relationshipNetwork.getSocialCircle(playerId, 30);

        // 计算圈子统计
        int totalFriends = friends.size();
        int closeFriends = (int) friends.stream()
            .filter(f -> relationshipNetwork.getRelationship(playerId, f).strength() >= 70)
            .count();
        int onlineFriends = (int) friends.stream()
            .filter(this::isPlayerOnline)
            .count();

        // 分析子圈子（基于共同好友聚类）
        Map<String, Set<UUID>> clusters = identifyClusters(playerId, friends);

        // 计算社交影响力
        int influence = relationshipNetwork.calculateInfluenceScore(playerId);

        // 计算平均关系强度
        double avgStrength = friends.stream()
            .mapToInt(f -> relationshipNetwork.getRelationship(playerId, f).strength())
            .average()
            .orElse(0);

        return new SocialCircleAnalysis(
            playerId,
            totalFriends,
            closeFriends,
            onlineFriends,
            clusters,
            influence,
            avgStrength,
            socialCircle
        );
    }

    /**
     * 识别社交圈子中的子群
     */
    private Map<String, Set<UUID>> identifyClusters(UUID playerId, Set<UUID> friends) {
        Map<String, Set<UUID>> clusters = new HashMap<>();

        // 按关系强度分组
        Set<UUID> closeFriends = new HashSet<>();
        Set<UUID> goodFriends = new HashSet<>();
        Set<UUID> normalFriends = new HashSet<>();

        for (UUID friendId : friends) {
            int strength = relationshipNetwork.getRelationship(playerId, friendId).strength();
            if (strength >= 70) {
                closeFriends.add(friendId);
            } else if (strength >= 40) {
                goodFriends.add(friendId);
            } else {
                normalFriends.add(friendId);
            }
        }

        clusters.put("close", closeFriends);
        clusters.put("good", goodFriends);
        clusters.put("normal", normalFriends);

        return clusters;
    }

    // ==================== 潜在朋友匹配 ====================

    /**
     * 计算两个玩家的潜在朋友匹配度
     */
    public double calculateCompatibility(UUID player1, UUID player2) {
        // 1. 共同好友比例
        Set<UUID> p1Friends = friendService.getFriends(player1);
        Set<UUID> p2Friends = friendService.getFriends(player2);

        if (p1Friends.isEmpty() || p2Friends.isEmpty()) {
            return 0.3; // 默认基础分数
        }

        Set<UUID> mutual = new HashSet<>(p1Friends);
        mutual.retainAll(p2Friends);

        double mutualRatio = (double) mutual.size() / Math.min(p1Friends.size(), p2Friends.size());

        // 2. 关系强度相似度
        double avgStrength1 = p1Friends.stream()
            .mapToInt(f -> relationshipNetwork.getRelationship(player1, f).strength())
            .average()
            .orElse(50);
        double avgStrength2 = p2Friends.stream()
            .mapToInt(f -> relationshipNetwork.getRelationship(player2, f).strength())
            .average()
            .orElse(50);

        double strengthDiff = Math.abs(avgStrength1 - avgStrength2) / 100.0;

        // 3. 社交圈重叠度
        Set<UUID> circle1 = relationshipNetwork.getSocialCircle(player1, 30);
        Set<UUID> circle2 = relationshipNetwork.getSocialCircle(player2, 30);

        Set<UUID> circleOverlap = new HashSet<>(circle1);
        circleOverlap.retainAll(circle2);

        double circleOverlapRatio = circle1.isEmpty() || circle2.isEmpty() ? 0 :
            (double) circleOverlap.size() / Math.min(circle1.size(), circle2.size());

        // 综合分数
        return (mutualRatio * 0.4 + (1 - strengthDiff) * 0.3 + circleOverlapRatio * 0.3) * 100;
    }

    /**
     * 找出与玩家最匹配的人
     */
    public List<FriendRecommendation> findMostCompatible(UUID playerId, int limit) {
        List<FriendRecommendation> allCandidates = getRecommendations(playerId);
        return allCandidates.stream()
            .sorted((a, b) -> Double.compare(b.matchScore(), a.matchScore()))
            .limit(limit)
            .collect(Collectors.toList());
    }

    // ==================== 推荐列表管理 ====================

    /**
     * 刷新指定玩家的推荐缓存
     */
    public void refreshRecommendations(UUID playerId) {
        recommendationCache.remove(playerId);
    }

    /**
     * 刷新所有推荐缓存
     */
    public void refreshAllRecommendations() {
        recommendationCache.clear();
        lastCacheUpdate = 0;
    }

    /**
     * 清除无效的推荐
     */
    public void clearInvalidRecommendations(UUID playerId) {
        List<FriendRecommendation> current = recommendationCache.get(playerId);
        if (current == null) return;

        Set<UUID> myFriends = friendService.getFriends(playerId);
        Set<UUID> blacklist = friendService.getBlacklist(playerId);

        List<FriendRecommendation> valid = current.stream()
            .filter(r -> !myFriends.contains(r.playerId()) && !blacklist.contains(r.playerId()))
            .collect(Collectors.toList());

        recommendationCache.put(playerId, valid);
    }

    /**
     * 获取推荐统计信息
     */
    public RecommendationStats getStats(UUID playerId) {
        List<FriendRecommendation> recs = recommendationCache.getOrDefault(playerId, Collections.emptyList());

        double avgScore = recs.stream()
            .mapToDouble(FriendRecommendation::matchScore)
            .average()
            .orElse(0);

        int avgMutual = (int) recs.stream()
            .mapToInt(FriendRecommendation::mutualFriends)
            .average()
            .orElse(0);

        return new RecommendationStats(recs.size(), avgScore, avgMutual, System.currentTimeMillis() - lastCacheUpdate);
    }

    // ==================== 辅助方法 ====================

    private boolean isCacheValid(UUID playerId) {
        return recommendationCache.containsKey(playerId) &&
               System.currentTimeMillis() - lastCacheUpdate < CACHE_TTL;
    }

    private String getPlayerName(UUID playerId) {
        OfflinePlayer offline = Bukkit.getOfflinePlayer(playerId);
        return offline.getName() != null ? offline.getName() : "Unknown";
    }

    private boolean isPlayerOnline(UUID playerId) {
        return Bukkit.getPlayer(playerId) != null;
    }

    // ==================== 内部数据类 ====================

    /**
     * 候选人评分
     */
    private static class CandidateScore {
        Set<UUID> connections = new HashSet<>();
        double totalScore = 0;

        void addConnection(UUID connectionId) {
            connections.add(connectionId);
        }
    }

    // ==================== 数据记录 ====================

    /**
     * 好友推荐记录
     */
    public record FriendRecommendation(
        UUID playerId,
        String playerName,
        int mutualFriends,
        double matchScore,
        String reason
    ) {
        public String getMatchLevel() {
            if (matchScore >= 80) return "极高";
            if (matchScore >= 60) return "高";
            if (matchScore >= 40) return "中";
            if (matchScore >= 20) return "低";
            return "一般";
        }
    }

    /**
     * 社交圈子分析结果
     */
    public record SocialCircleAnalysis(
        UUID playerId,
        int totalFriends,
        int closeFriends,
        int onlineFriends,
        Map<String, Set<UUID>> clusters,
        int influence,
        double avgStrength,
        Set<UUID> socialCircle
    ) {
        public String getCircleDescription() {
            if (totalFriends >= 50) return "社交达人";
            if (totalFriends >= 20) return "社交活跃";
            if (totalFriends >= 10) return "社交正常";
            if (totalFriends >= 3) return "社交较少";
            return "社交孤岛";
        }

        public double getOnlineRatio() {
            return totalFriends > 0 ? (double) onlineFriends / totalFriends : 0;
        }
    }

    /**
     * 推荐统计信息
     */
    public record RecommendationStats(
        int totalRecommendations,
        double avgMatchScore,
        int avgMutualFriends,
        long cacheAgeMs
    ) {
        public boolean isCacheFresh() {
            return cacheAgeMs < CACHE_TTL;
        }
    }
}
