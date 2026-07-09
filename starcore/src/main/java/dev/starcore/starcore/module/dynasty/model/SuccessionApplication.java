package dev.starcore.starcore.module.dynasty.model;

import java.time.Instant;
import java.util.UUID;

/**
 * 继承申请记录
 * 记录玩家提交的继承申请
 *
 * @param id 申请ID
 * @param dynastyId 所属王朝ID
 * @param applicantId 申请者ID
 * @param applicantName 申请者名称
 * @param appliedAt 申请时间
 * @param status 申请状态
 * @param voteResult 投票结果 (如果是选举制)
 * @param approvedBy 审批者ID
 * @param processedAt 处理时间
 * @param reason 申请理由
 */
public record SuccessionApplication(
    UUID id,
    UUID dynastyId,
    UUID applicantId,
    String applicantName,
    Instant appliedAt,
    ApplicationStatus status,
    VoteResult voteResult,
    UUID approvedBy,
    Instant processedAt,
    String reason
) {
    /**
     * 申请状态枚举
     */
    public enum ApplicationStatus {
        PENDING("待审批"),
        APPROVED("已批准"),
        REJECTED("已拒绝"),
        CANCELLED("已取消"),
        EXPIRED("已过期");

        private final String displayName;

        ApplicationStatus(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }
    }

    /**
     * 投票结果记录
     */
    public record VoteResult(
        int votesFor,
        int votesAgainst,
        int totalEligible,
        boolean passed
    ) {
        public double approvalRate() {
            if (totalEligible <= 0) return 0.0;
            return (double) votesFor / totalEligible;
        }
    }

    /**
     * 创建新申请的构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 申请构建器
     */
    public static class Builder {
        private UUID id = UUID.randomUUID();
        private UUID dynastyId;
        private UUID applicantId;
        private String applicantName;
        private Instant appliedAt = Instant.now();
        private ApplicationStatus status = ApplicationStatus.PENDING;
        private VoteResult voteResult;
        private UUID approvedBy;
        private Instant processedAt;
        private String reason;

        public Builder id(UUID id) { this.id = id; return this; }
        public Builder dynastyId(UUID dynastyId) { this.dynastyId = dynastyId; return this; }
        public Builder applicantId(UUID applicantId) { this.applicantId = applicantId; return this; }
        public Builder applicantName(String applicantName) { this.applicantName = applicantName; return this; }
        public Builder appliedAt(Instant appliedAt) { this.appliedAt = appliedAt; return this; }
        public Builder status(ApplicationStatus status) { this.status = status; return this; }
        public Builder voteResult(VoteResult voteResult) { this.voteResult = voteResult; return this; }
        public Builder approvedBy(UUID approvedBy) { this.approvedBy = approvedBy; return this; }
        public Builder processedAt(Instant processedAt) { this.processedAt = processedAt; return this; }
        public Builder reason(String reason) { this.reason = reason; return this; }

        public SuccessionApplication build() {
            return new SuccessionApplication(
                id, dynastyId, applicantId, applicantName, appliedAt,
                status, voteResult, approvedBy, processedAt, reason
            );
        }
    }
}