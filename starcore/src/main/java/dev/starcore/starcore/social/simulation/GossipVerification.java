package dev.starcore.starcore.social.simulation;

import dev.starcore.starcore.social.simulation.GossipService;
import dev.starcore.starcore.social.simulation.VerificationStatus;

import java.util.*;

/**
 * 八卦验证记录
 *
 * 用于记录和管理八卦的验证信息:
 * - 验证状态
 * - 投票记录
 * - 举报记录
 * - 可信度调整历史
 * - 来源追踪
 */
public record GossipVerification(
    /**
     * 关联的八卦ID
     */
    String gossipId,

    /**
     * 当前验证状态
     */
    VerificationStatus status,

    /**
     * 验证状态最后更新时间
     */
    long statusUpdatedAt,

    /**
     * 投票支持真实的玩家列表
     */
    Set<UUID> truthVotes,

    /**
     * 投票支持虚假的玩家列表
     */
    Set<UUID> falseVotes,

    /**
     * 举报记录: playerId -> ReportRecord
     */
    Map<UUID, ReportRecord> reports,

    /**
     * 可信度调整历史
     */
    List<CredibilityAdjustment> credibilityHistory,

    /**
     * 八卦来源追踪路径
     */
    List<GossipSource> sourcePath,

    /**
     * 辟谣信息
     */
    DebunkInfo debunkInfo,

    /**
     * 验证通过的阈值人数
     */
    int verificationThreshold,

    /**
     * 创建时间
     */
    long createdAt
) {

    /**
     * 创建新的验证记录
     */
    public static GossipVerification create(String gossipId) {
        return new GossipVerification(
            gossipId,
            VerificationStatus.UNVERIFIED,
            System.currentTimeMillis(),
            new HashSet<>(),
            new HashSet<>(),
            new HashMap<>(),
            new ArrayList<>(),
            new ArrayList<>(),
            null,
            3,  // 默认阈值
            System.currentTimeMillis()
        );
    }

    /**
     * 获取总投票数
     */
    public int totalVotes() {
        return truthVotes.size() + falseVotes.size();
    }

    /**
     * 获取真实性比例
     */
    public double truthRatio() {
        int total = totalVotes();
        if (total == 0) return 0.5;
        return (double) truthVotes.size() / total;
    }

    /**
     * 检查玩家是否已投票
     */
    public boolean hasVoted(UUID playerId) {
        return truthVotes.contains(playerId) || falseVotes.contains(playerId);
    }

    /**
     * 检查玩家是否已举报
     */
    public boolean hasReported(UUID playerId) {
        return reports.containsKey(playerId);
    }

    /**
     * 获取举报数量
     */
    public int reportCount() {
        return reports.size();
    }

    /**
     * 获取来源数量
     */
    public int sourceCount() {
        return sourcePath.size();
    }

    /**
     * 获取最终可信度调整值
     */
    public double finalCredibilityAdjustment() {
        return credibilityHistory.stream()
            .mapToDouble(CredibilityAdjustment::adjustment)
            .sum();
    }

    /**
     * 是否已辟谣
     */
    public boolean isDebunked() {
        return status == VerificationStatus.DEBUNKED;
    }

    /**
     * 是否已验证
     */
    public boolean isVerified() {
        return status == VerificationStatus.VERIFIED;
    }

    /**
     * 获取状态显示文本
     */
    public String getStatusDisplay() {
        return status.getColor() + status.emoji() + " " + status.displayName();
    }

    // ==================== 内部数据类 ====================

    /**
     * 举报记录
     */
    public record ReportRecord(
        /**
         * 举报原因
         */
        ReportReason reason,

        /**
         * 举报详细描述
         */
        String description,

        /**
         * 举报时间戳
         */
        long timestamp,

        /**
         * 是否处理
         */
        boolean processed
    ) {
        public static ReportRecord create(ReportReason reason, String description) {
            return new ReportRecord(reason, description, System.currentTimeMillis(), false);
        }
    }

    /**
     * 举报原因枚举
     */
    public enum ReportReason {
        FALSE_NEWS("假新闻"),
        MISLEADING("误导性信息"),
        SLANDER("诽谤/人身攻击"),
        HATE_SPEECH("仇恨言论"),
        SPAM("垃圾信息"),
        PRIVACY("侵犯隐私"),
        HARASSMENT("骚扰"),
        OTHER("其他");

        private final String displayName;

        ReportReason(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }
    }

    /**
     * 可信度调整记录
     */
    public record CredibilityAdjustment(
        /**
         * 调整原因
         */
        String reason,

        /**
         * 调整量 (-1.0 到 +1.0)
         */
        double adjustment,

        /**
         * 执行者
         */
        UUID executor,

        /**
         * 时间戳
         */
        long timestamp,

        /**
         * 调整前可信度
         */
        double beforeCredibility,

        /**
         * 调整后可信度
         */
        double afterCredibility
    ) {
        public static CredibilityAdjustment create(
            String reason,
            double adjustment,
            UUID executor,
            double beforeCredibility
        ) {
            double after = Math.min(1.0, Math.max(0.0, beforeCredibility + adjustment));
            return new CredibilityAdjustment(
                reason,
                adjustment,
                executor,
                System.currentTimeMillis(),
                beforeCredibility,
                after
            );
        }
    }

    /**
     * 八卦来源追踪
     */
    public record GossipSource(
        /**
         * 来源玩家ID
         */
        UUID sourceId,

        /**
         * 来源玩家名称
         */
        String sourceName,

        /**
         * 来源类型
         */
        SourceType type,

        /**
         * 关联的八卦ID (如果是转发)
         */
        String relatedGossipId,

        /**
         * 时间戳
         */
        long timestamp,

        /**
         * 该来源的可信度权重
         */
        double credibilityWeight
    ) {
        public enum SourceType {
            ORIGINAL("原创"),
            FORWARD("转发"),
            QUOTE("引用"),
            SECONDARY("二手传播");

            private final String displayName;

            SourceType(String displayName) {
                this.displayName = displayName;
            }

            public String displayName() {
                return displayName;
            }
        }
    }

    /**
     * 辟谣信息
     */
    public record DebunkInfo(
        /**
         * 辟谣者
         */
        UUID debunker,

        /**
         * 辟谣者名称
         */
        String debunkerName,

        /**
         * 辟谣内容
         */
        String debunkContent,

        /**
         * 辟谣证据列表
         */
        List<String> evidence,

        /**
         * 辟谣时间
         */
        long debunkedAt,

        /**
         * 辟谣后调整的可信度
         */
        double credibilityPenalty,

        /**
         * 是否官方辟谣
         */
        boolean official
    ) {
        public static DebunkInfo create(
            UUID debunker,
            String debunkerName,
            String debunkContent,
            boolean official
        ) {
            return new DebunkInfo(
                debunker,
                debunkerName,
                debunkContent,
                new ArrayList<>(),
                System.currentTimeMillis(),
                -0.5,  // 默认惩罚
                official
            );
        }
    }

    // ==================== 变更方法 (返回新实例) ====================

    /**
     * 添加真实投票
     */
    public GossipVerification addTruthVote(UUID playerId) {
        if (hasVoted(playerId)) return this;
        Set<UUID> newTruthVotes = new HashSet<>(truthVotes);
        newTruthVotes.add(playerId);
        return new GossipVerification(
            gossipId, status, statusUpdatedAt,
            newTruthVotes, falseVotes,
            reports, credibilityHistory, sourcePath,
            debunkInfo, verificationThreshold, createdAt
        );
    }

    /**
     * 添加虚假投票
     */
    public GossipVerification addFalseVote(UUID playerId) {
        if (hasVoted(playerId)) return this;
        Set<UUID> newFalseVotes = new HashSet<>(falseVotes);
        newFalseVotes.add(playerId);
        return new GossipVerification(
            gossipId, status, statusUpdatedAt,
            truthVotes, newFalseVotes,
            reports, credibilityHistory, sourcePath,
            debunkInfo, verificationThreshold, createdAt
        );
    }

    /**
     * 添加举报
     */
    public GossipVerification addReport(UUID reporter, ReportReason reason, String description) {
        if (hasReported(reporter)) return this;
        Map<UUID, ReportRecord> newReports = new HashMap<>(reports);
        newReports.put(reporter, ReportRecord.create(reason, description));
        return new GossipVerification(
            gossipId, status, statusUpdatedAt,
            truthVotes, falseVotes,
            newReports, credibilityHistory, sourcePath,
            debunkInfo, verificationThreshold, createdAt
        );
    }

    /**
     * 添加可信度调整
     */
    public GossipVerification addCredibilityAdjustment(CredibilityAdjustment adjustment) {
        List<CredibilityAdjustment> newHistory = new ArrayList<>(credibilityHistory);
        newHistory.add(adjustment);
        return new GossipVerification(
            gossipId, status, statusUpdatedAt,
            truthVotes, falseVotes,
            reports, newHistory, sourcePath,
            debunkInfo, verificationThreshold, createdAt
        );
    }

    /**
     * 添加来源
     */
    public GossipVerification addSource(GossipSource source) {
        List<GossipSource> newPath = new ArrayList<>(sourcePath);
        newPath.add(source);
        return new GossipVerification(
            gossipId, status, statusUpdatedAt,
            truthVotes, falseVotes,
            reports, credibilityHistory, newPath,
            debunkInfo, verificationThreshold, createdAt
        );
    }

    /**
     * 更新状态
     */
    public GossipVerification updateStatus(VerificationStatus newStatus) {
        return new GossipVerification(
            gossipId, newStatus, System.currentTimeMillis(),
            truthVotes, falseVotes,
            reports, credibilityHistory, sourcePath,
            debunkInfo, verificationThreshold, createdAt
        );
    }

    /**
     * 设置辟谣信息
     */
    public GossipVerification setDebunk(DebunkInfo info) {
        return new GossipVerification(
            gossipId, VerificationStatus.DEBUNKED, System.currentTimeMillis(),
            truthVotes, falseVotes,
            reports, credibilityHistory, sourcePath,
            info, verificationThreshold, createdAt
        );
    }

    /**
     * 处理举报
     */
    public GossipVerification markReportProcessed(UUID reporter) {
        ReportRecord record = reports.get(reporter);
        if (record == null) return this;
        Map<UUID, ReportRecord> newReports = new HashMap<>(reports);
        newReports.put(reporter, new ReportRecord(
            record.reason(),
            record.description(),
            record.timestamp(),
            true
        ));
        return new GossipVerification(
            gossipId, status, statusUpdatedAt,
            truthVotes, falseVotes,
            newReports, credibilityHistory, sourcePath,
            debunkInfo, verificationThreshold, createdAt
        );
    }
}
