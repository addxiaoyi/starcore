package dev.starcore.starcore.social.simulation;

import dev.starcore.starcore.social.simulation.GossipService.Gossip;
import dev.starcore.starcore.social.simulation.GossipVerification.*;
import dev.starcore.starcore.social.simulation.GossipService.GossipTopic;
import dev.starcore.starcore.social.simulation.GossipService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 八卦验证服务
 *
 * 提供八卦真实性验证系统:
 * 1. 八卦真实性投票系统 - 玩家可以对八卦投票真假
 * 2. 八卦举报功能 - 举报假新闻/不当内容
 * 3. 可信度动态调整算法 - 基于投票/举报动态调整可信度
 * 4. 八卦来源追踪 - 追踪八卦的传播路径
 * 5. 八卦辟谣功能 - 官方或玩家辟谣
 * 6. 与 GossipService 集成
 */
public class GossipVerificationService {

    private final GossipService gossipService;
    private final ReputationService reputationService;
    private final RelationshipNetwork relationshipNetwork;

    // 八卦验证记录缓存
    private final Map<String, GossipVerification> verifications = new ConcurrentHashMap<>();

    // 举报处理队列
    private final Queue<ReportQueueItem> reportQueue = new LinkedList<>();

    // 验证配置
    private final VerificationConfig config;

    /**
     * 验证配置
     */
    public static class VerificationConfig {
        /** 验证通过的投票阈值 */
        public int verificationThreshold = 3;

        /** 辟谣需要的举报数 */
        public int debunkReportThreshold = 5;

        /** 高可信度话题列表 */
        public Set<GossipTopic> highCredibilityTopics = Set.of(
            GossipTopic.POLITICS,
            GossipTopic.BATTLE,
            GossipTopic.SCANDAL
        );

        /** 低可信度话题列表 */
        public Set<GossipTopic> lowCredibilityTopics = Set.of(
            GossipTopic.ROMANCE,
            GossipTopic.MYSTERY
        );

        /** 每次投票的可信度调整量 */
        public double voteCredibilityChange = 0.05;

        /** 每次举报的可信度惩罚 */
        public double reportPenalty = 0.1;

        /** 辟谣可信度惩罚 */
        public double debunkPenalty = 0.5;

        /** 官方辟谣可信度惩罚 */
        public double officialDebunkPenalty = 0.8;

        /** 验证后最低可信度 */
        public double minCredibilityAfterVerification = 0.2;

        /** 自动验证时间 (毫秒) */
        public long autoVerificationTime = 24 * 60 * 60 * 1000; // 24小时
    }

    public GossipVerificationService(
        GossipService gossipService,
        ReputationService reputationService,
        RelationshipNetwork relationshipNetwork
    ) {
        this.gossipService = gossipService;
        this.reputationService = reputationService;
        this.relationshipNetwork = relationshipNetwork;
        this.config = new VerificationConfig();
    }

    public GossipVerificationService(
        GossipService gossipService,
        ReputationService reputationService,
        RelationshipNetwork relationshipNetwork,
        VerificationConfig config
    ) {
        this.gossipService = gossipService;
        this.reputationService = reputationService;
        this.relationshipNetwork = relationshipNetwork;
        this.config = config;
    }

    // ==================== 核心功能 ====================

    /**
     * 获取八卦的验证记录 (不存在则创建)
     */
    public GossipVerification getOrCreateVerification(String gossipId) {
        return verifications.computeIfAbsent(gossipId, GossipVerification::create);
    }

    /**
     * 获取八卦的验证记录 (可能为null)
     */
    public GossipVerification getVerification(String gossipId) {
        return verifications.get(gossipId);
    }

    /**
     * 投票八卦为真实
     */
    public boolean voteTruth(UUID voter, String gossipId) {
        Gossip gossip = gossipService.getGossipById(gossipId);
        if (gossip == null) return false;

        GossipVerification verification = getOrCreateVerification(gossipId);
        if (verification.hasVoted(voter)) return false;

        // 添加投票
        verification = verification.addTruthVote(voter);

        // 计算可信度调整
        double adjustment = calculateVoteAdjustment(voter, gossip, true);
        verification = verification.addCredibilityAdjustment(
            CredibilityAdjustment.create(
                "真实投票",
                adjustment,
                voter,
                gossip.credibility()
            )
        );

        // 更新八卦可信度
        gossipService.updateCredibility(gossipId, adjustment);

        // 检查是否达到验证阈值
        checkAndApplyVerification(verification, gossipId);

        verifications.put(gossipId, verification);
        return true;
    }

    /**
     * 投票八卦为虚假
     */
    public boolean voteFalse(UUID voter, String gossipId) {
        Gossip gossip = gossipService.getGossipById(gossipId);
        if (gossip == null) return false;

        GossipVerification verification = getOrCreateVerification(gossipId);
        if (verification.hasVoted(voter)) return false;

        // 添加投票
        verification = verification.addFalseVote(voter);

        // 计算可信度调整
        double adjustment = calculateVoteAdjustment(voter, gossip, false);
        verification = verification.addCredibilityAdjustment(
            CredibilityAdjustment.create(
                "虚假投票",
                adjustment,
                voter,
                gossip.credibility()
            )
        );

        // 更新八卦可信度
        gossipService.updateCredibility(gossipId, adjustment);

        // 检查举报阈值
        checkReportThreshold(verification, gossipId);

        verifications.put(gossipId, verification);
        return true;
    }

    /**
     * 举报八卦
     */
    public boolean reportGossip(UUID reporter, String gossipId, ReportReason reason, String description) {
        Gossip gossip = gossipService.getGossipById(gossipId);
        if (gossip == null) return false;

        GossipVerification verification = getOrCreateVerification(gossipId);
        if (verification.hasReported(reporter)) return false;

        // 添加举报
        verification = verification.addReport(reporter, reason, description);

        // 计算可信度惩罚
        double penalty = config.reportPenalty;
        verification = verification.addCredibilityAdjustment(
            CredibilityAdjustment.create(
                "举报: " + reason.displayName(),
                -penalty,
                reporter,
                gossip.credibility()
            )
        );

        // 更新八卦可信度
        gossipService.updateCredibility(gossipId, -penalty);

        // 检查举报阈值
        checkReportThreshold(verification, gossipId);

        // 加入处理队列
        reportQueue.offer(new ReportQueueItem(gossipId, reporter, reason, description));

        verifications.put(gossipId, verification);
        return true;
    }

    /**
     * 辟谣八卦
     */
    public boolean debunkGossip(UUID debunker, String gossipId, String debunkContent, boolean official) {
        Gossip gossip = gossipService.getGossipById(gossipId);
        if (gossip == null) return false;

        GossipVerification verification = getOrCreateVerification(gossipId);
        if (verification.isDebunked()) return false;

        // 获取辟谣者名称
        Player player = Bukkit.getPlayer(debunker);
        String debunkerName = player != null ? player.getName() : "系统";

        // 创建辟谣信息
        DebunkInfo debunkInfo = DebunkInfo.create(debunker, debunkerName, debunkContent, official);

        // 计算可信度惩罚
        double penalty = official ? config.officialDebunkPenalty : config.debunkPenalty;
        verification = verification.addCredibilityAdjustment(
            CredibilityAdjustment.create(
                official ? "官方辟谣" : "玩家辟谣",
                -penalty,
                debunker,
                gossip.credibility()
            )
        );

        // 设置辟谣信息
        verification = verification.setDebunk(debunkInfo);

        // 更新八卦可信度
        gossipService.updateCredibility(gossipId, -penalty);

        // 惩罚辟谣者声望 (如果是官方辟谣)
        if (official) {
            reputationService.modifyReputation(debunker, 10,
                ReputationService.ReputationReason.PROTECT_WEAK);
        }

        verifications.put(gossipId, verification);
        return true;
    }

    /**
     * 追踪八卦来源
     */
    public GossipVerification trackSource(String gossipId, UUID sourceId, String sourceName) {
        Gossip gossip = gossipService.getGossipById(gossipId);
        if (gossip == null) return getOrCreateVerification(gossipId);

        GossipVerification verification = getOrCreateVerification(gossipId);

        // 确定来源类型
        GossipSource.SourceType sourceType = GossipSource.SourceType.SECONDARY;
        if (verification.sourceCount() == 0) {
            sourceType = GossipSource.SourceType.ORIGINAL;
        } else if (gossip.seenBy().contains(sourceId)) {
            // 检查是否是直接传播
            sourceType = GossipSource.SourceType.FORWARD;
        }

        // 计算来源可信度权重
        double weight = calculateSourceWeight(sourceId, sourceName);

        // 添加来源记录
        GossipSource source = new GossipSource(
            sourceId,
            sourceName,
            sourceType,
            null,
            System.currentTimeMillis(),
            weight
        );

        verification = verification.addSource(source);

        // 如果是原创且高可信度,提升八卦可信度
        if (sourceType == GossipSource.SourceType.ORIGINAL && weight > 0.7) {
            double boost = config.voteCredibilityChange * 2;
            verification = verification.addCredibilityAdjustment(
                CredibilityAdjustment.create(
                    "可信来源",
                    boost,
                    sourceId,
                    gossip.credibility()
                )
            );
            gossipService.updateCredibility(gossipId, boost);
        }

        verifications.put(gossipId, verification);
        return verification;
    }

    /**
     * 添加辟谣证据
     */
    public boolean addDebunkEvidence(String gossipId, UUID playerId, String evidence) {
        GossipVerification verification = verifications.get(gossipId);
        if (verification == null || verification.debunkInfo() == null) return false;

        List<String> newEvidence = new ArrayList<>(verification.debunkInfo().evidence());
        newEvidence.add(evidence);

        DebunkInfo newDebunkInfo = new DebunkInfo(
            verification.debunkInfo().debunker(),
            verification.debunkInfo().debunkerName(),
            verification.debunkInfo().debunkContent(),
            newEvidence,
            verification.debunkInfo().debunkedAt(),
            verification.debunkInfo().credibilityPenalty(),
            verification.debunkInfo().official()
        );

        verification = new GossipVerification(
            verification.gossipId(),
            verification.status(),
            verification.statusUpdatedAt(),
            verification.truthVotes(),
            verification.falseVotes(),
            verification.reports(),
            verification.credibilityHistory(),
            verification.sourcePath(),
            newDebunkInfo,
            verification.verificationThreshold(),
            verification.createdAt()
        );

        verifications.put(gossipId, verification);
        return true;
    }

    // ==================== 查询功能 ====================

    /**
     * 获取需要处理的举报队列
     */
    public List<ReportQueueItem> getPendingReports() {
        return new ArrayList<>(reportQueue);
    }

    /**
     * 处理举报
     */
    public void processReport(String gossipId, UUID reporter, boolean valid) {
        GossipVerification verification = verifications.get(gossipId);
        if (verification == null) return;

        verification = verification.markReportProcessed(reporter);

        // 如果举报有效,给予举报者声望奖励
        if (valid) {
            reputationService.modifyReputation(reporter, 5,
                ReputationService.ReputationReason.PROTECT_WEAK);
        }

        verifications.put(gossipId, verification);

        // 从队列中移除
        reportQueue.removeIf(item ->
            item.gossipId().equals(gossipId) && item.reporter().equals(reporter));
    }

    /**
     * 获取待验证的八卦列表
     */
    public List<GossipVerification> getPendingVerifications() {
        return verifications.values().stream()
            .filter(v -> v.status() == VerificationStatus.PENDING ||
                        v.status() == VerificationStatus.UNVERIFIED)
            .filter(v -> v.totalVotes() > 0)
            .sorted((a, b) -> Integer.compare(b.totalVotes(), a.totalVotes()))
            .collect(Collectors.toList());
    }

    /**
     * 获取已辟谣的八卦列表
     */
    public List<GossipVerification> getDebunkedGossips() {
        return verifications.values().stream()
            .filter(GossipVerification::isDebunked)
            .sorted((a, b) -> Long.compare(b.statusUpdatedAt(), a.statusUpdatedAt()))
            .collect(Collectors.toList());
    }

    /**
     * 获取高可信度八卦
     */
    public List<Gossip> getVerifiedGossips() {
        return gossipService.getAllGossips().stream()
            .filter(g -> {
                GossipVerification v = verifications.get(g.id());
                return v != null && v.isVerified();
            })
            .sorted((a, b) -> Double.compare(b.credibility(), a.credibility()))
            .collect(Collectors.toList());
    }

    /**
     * 获取玩家的验证历史
     */
    public List<GossipVerification> getPlayerVerificationHistory(UUID playerId) {
        return verifications.values().stream()
            .filter(v -> v.truthVotes().contains(playerId) ||
                        v.falseVotes().contains(playerId) ||
                        v.reports().containsKey(playerId))
            .sorted((a, b) -> Long.compare(b.statusUpdatedAt(), a.statusUpdatedAt()))
            .limit(50)
            .collect(Collectors.toList());
    }

    /**
     * 检查八卦是否需要自动验证
     */
    public void checkAutoVerification() {
        long now = System.currentTimeMillis();
        for (GossipVerification verification : verifications.values()) {
            if (verification.status() != VerificationStatus.UNVERIFIED &&
                verification.status() != VerificationStatus.PENDING) {
                continue;
            }

            // 检查是否超过自动验证时间
            if (now - verification.createdAt() > config.autoVerificationTime) {
                applyAutoVerification(verification);
            }
        }
    }

    /**
     * 获取验证统计信息
     */
    public VerificationStats getStats() {
        int total = verifications.size();
        int verified = 0, debunked = 0, pending = 0, unverified = 0;

        for (GossipVerification v : verifications.values()) {
            switch (v.status()) {
                case VERIFIED -> verified++;
                case DEBUNKED -> debunked++;
                case PENDING -> pending++;
                case UNVERIFIED -> unverified++;
                default -> {}
            }
        }

        return new VerificationStats(total, verified, debunked, pending, unverified);
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 计算投票可信度调整
     */
    private double calculateVoteAdjustment(UUID voter, Gossip gossip, boolean truthVote) {
        double baseChange = config.voteCredibilityChange;

        // 投票者声望影响
        int rep = reputationService.getReputation(voter);
        double repMultiplier = 1.0 + (rep / 500.0); // 每500声望增加100%权重

        // 话题影响
        double topicMultiplier = 1.0;
        if (config.highCredibilityTopics.contains(gossip.topic())) {
            topicMultiplier *= 1.2;
        } else if (config.lowCredibilityTopics.contains(gossip.topic())) {
            topicMultiplier *= 0.8;
        }

        // 方向影响
        double direction = truthVote ? 1.0 : -1.0;

        return baseChange * repMultiplier * topicMultiplier * direction;
    }

    /**
     * 计算来源可信度权重
     */
    private double calculateSourceWeight(UUID sourceId, String sourceName) {
        double weight = 0.5;

        // 基于声望
        int rep = reputationService.getReputation(sourceId);
        weight += Math.min(0.3, rep / 1000.0);

        // 基于社交圈大小
        int friends = relationshipNetwork.getFriends(sourceId).size();
        weight += Math.min(0.1, friends * 0.01);

        // 基于关系网络中心性 (简化版)
        int connections = relationshipNetwork.getSocialCircle(sourceId, 10).size();
        weight += Math.min(0.1, connections * 0.01);

        return Math.min(1.0, Math.max(0.0, weight));
    }

    /**
     * 检查并应用验证
     */
    private void checkAndApplyVerification(GossipVerification verification, String gossipId) {
        // 检查是否达到验证阈值
        if (verification.totalVotes() >= verification.verificationThreshold()) {
            double truthRatio = verification.truthRatio();

            if (truthRatio >= 0.7) {
                // 多数认为真实 -> 标记为已验证
                verification = verification.updateStatus(VerificationStatus.VERIFIED);

                // 额外提升可信度
                double boost = config.voteCredibilityChange * verification.totalVotes();
                gossipService.updateCredibility(gossipId, boost);

            } else if (truthRatio <= 0.3) {
                // 多数认为虚假 -> 标记为待验证(待辟谣)
                verification = verification.updateStatus(VerificationStatus.PENDING);
            } else {
                // 争议中
                verification = verification.updateStatus(VerificationStatus.DISPUTED);
            }

            verifications.put(gossipId, verification);
        }
    }

    /**
     * 检查举报阈值
     */
    private void checkReportThreshold(GossipVerification verification, String gossipId) {
        if (verification.reportCount() >= config.debunkReportThreshold) {
            Gossip gossip = gossipService.getGossipById(gossipId);
            if (gossip != null && gossip.credibility() < 0.3) {
                // 举报过多且可信度低 -> 自动辟谣
                debunkGossip(
                    new UUID(0L, 0L), // Console UUID
                    gossipId,
                    "系统自动辟谣: 该八卦因举报过多且可信度极低被标记为谣言",
                    false
                );
            }
        }
    }

    /**
     * 应用自动验证
     */
    private void applyAutoVerification(GossipVerification verification) {
        if (verification.totalVotes() == 0) {
            // 无人投票 -> 保持未验证
            return;
        }

        double truthRatio = verification.truthRatio();
        if (truthRatio >= 0.6) {
            verification = verification.updateStatus(VerificationStatus.VERIFIED);
        } else if (truthRatio <= 0.4) {
            verification = verification.updateStatus(VerificationStatus.DEBUNKED);
            // 添加自动辟谣信息
            DebunkInfo autoDebunk = DebunkInfo.create(
                new UUID(0L, 0L), // Console UUID
                "系统",
                "自动辟谣: 该八卦经过24小时无人处理,社区多数认为不实",
                false
            );
            verification = verification.setDebunk(autoDebunk);
        } else {
            verification = verification.updateStatus(VerificationStatus.DISPUTED);
        }

        verifications.put(verification.gossipId(), verification);
    }

    // ==================== 内部类 ====================

    /**
     * 举报队列项
     */
    public record ReportQueueItem(
        String gossipId,
        UUID reporter,
        ReportReason reason,
        String description
    ) {}

    /**
     * 验证统计信息
     */
    public record VerificationStats(
        int total,
        int verified,
        int debunked,
        int pending,
        int unverified
    ) {
        public double getVerificationRate() {
            if (total == 0) return 0;
            return (double) (verified + debunked) / total;
        }
    }

}
