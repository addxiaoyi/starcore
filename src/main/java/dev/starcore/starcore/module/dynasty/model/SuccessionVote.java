package dev.starcore.starcore.module.dynasty.model;

import java.time.Instant;
import java.util.UUID;

/**
 * 继承投票记录
 * 记录选举制中的投票
 *
 * @param applicationId 关联的申请ID
 * @param voterId 投票者ID
 * @param voterName 投票者名称
 * @param voteFor 是否投赞成票
 * @param votedAt 投票时间
 * @param comment 投票评论
 */
public record SuccessionVote(
    UUID applicationId,
    UUID voterId,
    String voterName,
    boolean voteFor,
    Instant votedAt,
    String comment
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UUID applicationId;
        private UUID voterId;
        private String voterName;
        private boolean voteFor;
        private Instant votedAt = Instant.now();
        private String comment;

        public Builder applicationId(UUID applicationId) { this.applicationId = applicationId; return this; }
        public Builder voterId(UUID voterId) { this.voterId = voterId; return this; }
        public Builder voterName(String voterName) { this.voterName = voterName; return this; }
        public Builder voteFor(boolean voteFor) { this.voteFor = voteFor; return this; }
        public Builder votedAt(Instant votedAt) { this.votedAt = votedAt; return this; }
        public Builder comment(String comment) { this.comment = comment; return this; }

        public SuccessionVote build() {
            return new SuccessionVote(
                applicationId, voterId, voterName, voteFor, votedAt, comment
            );
        }
    }
}