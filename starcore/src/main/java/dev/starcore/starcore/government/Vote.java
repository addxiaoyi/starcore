package dev.starcore.starcore.government;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 投票记录
 */
public final class Vote {
    private final int voteId;
    private final int billId;
    private final UUID voterId;
    private final VoteChoice choice;
    private final Instant votedAt;
    private String comment;

    public enum VoteChoice {
        FOR,        // 赞成
        AGAINST,    // 反对
        ABSTAIN     // 弃权
    }

    public Vote(int voteId, int billId, UUID voterId, VoteChoice choice, Instant votedAt) {
        this.voteId = voteId;
        this.billId = billId;
        this.voterId = Objects.requireNonNull(voterId, "voterId");
        this.choice = Objects.requireNonNull(choice, "choice");
        this.votedAt = Objects.requireNonNull(votedAt, "votedAt");
    }

    public int getVoteId() {
        return voteId;
    }

    public int getBillId() {
        return billId;
    }

    public UUID getVoterId() {
        return voterId;
    }

    public VoteChoice getChoice() {
        return choice;
    }

    public Instant getVotedAt() {
        return votedAt;
    }

    public Optional<String> getComment() {
        return Optional.ofNullable(comment);
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Vote vote = (Vote) o;
        return voteId == vote.voteId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(voteId);
    }

    @Override
    public String toString() {
        return "Vote{" +
                "voteId=" + voteId +
                ", billId=" + billId +
                ", voterId=" + voterId +
                ", choice=" + choice +
                ", votedAt=" + votedAt +
                '}';
    }
}
