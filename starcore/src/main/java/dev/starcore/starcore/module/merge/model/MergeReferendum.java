package dev.starcore.starcore.module.merge.model;

import dev.starcore.starcore.module.nation.model.NationId;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 合并公投模型
 * 代表一个国家间的合并公投提案
 */
public final class MergeReferendum {
    private final UUID id;
    private final NationId proposerNationId;
    private final UUID proposerId;
    private final String proposerName;
    private final NationId targetNationId;
    private final String targetNationName;
    private final Instant createdAt;
    private final Instant expiresAt;
    private final Set<UUID> votes;
    private final Set<UUID> againstVotes;
    private MergeReferendumState state;
    private final String newNationName;
    private NationId resultNationId;
    private String resultMessage;

    public MergeReferendum(
            UUID id,
            NationId proposerNationId,
            UUID proposerId,
            String proposerName,
            NationId targetNationId,
            String targetNationName,
            String newNationName,
            Instant createdAt,
            Instant expiresAt,
            MergeReferendumState state,
            Set<UUID> votes,
            Set<UUID> againstVotes,
            NationId resultNationId,
            String resultMessage
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.proposerNationId = Objects.requireNonNull(proposerNationId, "proposerNationId");
        this.proposerId = Objects.requireNonNull(proposerId, "proposerId");
        this.proposerName = Objects.requireNonNull(proposerName, "proposerName");
        this.targetNationId = Objects.requireNonNull(targetNationId, "targetNationId");
        this.targetNationName = Objects.requireNonNull(targetNationName, "targetNationName");
        this.newNationName = Objects.requireNonNull(newNationName, "newNationName");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        this.state = Objects.requireNonNull(state, "state");
        this.votes = Objects.requireNonNull(votes, "votes");
        this.againstVotes = Objects.requireNonNull(againstVotes, "againstVotes");
        this.resultNationId = resultNationId;
        this.resultMessage = resultMessage;
    }

    public MergeReferendum(
            NationId proposerNationId,
            UUID proposerId,
            String proposerName,
            NationId targetNationId,
            String targetNationName,
            String newNationName,
            Instant createdAt,
            Instant expiresAt
    ) {
        this(UUID.randomUUID(), proposerNationId, proposerId, proposerName, targetNationId,
             targetNationName, newNationName, createdAt, expiresAt,
             MergeReferendumState.PENDING, Set.of(), Set.of(), null, null);
    }

    public UUID id() { return id; }
    public NationId proposerNationId() { return proposerNationId; }
    public UUID proposerId() { return proposerId; }
    public String proposerName() { return proposerName; }
    public NationId targetNationId() { return targetNationId; }
    public String targetNationName() { return targetNationName; }
    public String newNationName() { return newNationName; }
    public Instant createdAt() { return createdAt; }
    public Instant expiresAt() { return expiresAt; }
    public MergeReferendumState state() { return state; }
    public Set<UUID> votes() { return Set.copyOf(votes); }
    public Set<UUID> againstVotes() { return Set.copyOf(againstVotes); }
    public NationId resultNationId() { return resultNationId; }
    public String resultMessage() { return resultMessage; }

    public boolean isPending() { return state == MergeReferendumState.PENDING; }
    public boolean isApproved() { return state == MergeReferendumState.APPROVED; }
    public boolean isRejected() { return state == MergeReferendumState.REJECTED; }
    public boolean isExpired() { return state == MergeReferendumState.EXPIRED; }
    public boolean isCancelled() { return state == MergeReferendumState.CANCELLED; }
    public boolean isExecuted() { return state == MergeReferendumState.EXECUTED; }
    public boolean isFailed() { return state == MergeReferendumState.FAILED; }

    public boolean isExpired(Instant now) { return isPending() && expiresAt.isBefore(now); }

    public int totalVotes() { return votes.size() + againstVotes.size(); }

    public int approveCount() { return votes.size(); }
    public int rejectCount() { return againstVotes.size(); }

    public boolean hasVoted(UUID playerId) {
        return votes.contains(playerId) || againstVotes.contains(playerId);
    }

    public boolean hasApproved(UUID playerId) {
        return votes.contains(playerId);
    }

    /**
     * 投赞成票
     * @param voterId 投票玩家ID
     * @return 投票成功返回true
     */
    public boolean vote(UUID voterId) {
        if (!isPending()) return false;
        if (hasVoted(voterId)) return false;
        votes.add(voterId);
        return true;
    }

    /**
     * 投反对票
     * @param voterId 投票玩家ID
     * @return 投票成功返回true
     */
    public boolean voteAgainst(UUID voterId) {
        if (!isPending()) return false;
        if (hasVoted(voterId)) return false;
        againstVotes.add(voterId);
        return true;
    }

    /**
     * 检查公投是否通过（简单多数）
     */
    public boolean passes() {
        return votes.size() > againstVotes.size();
    }

    /**
     * 检查公投是否通过（带阈值检查）
     * @param requiredRate 最低投票率 (0.0 - 1.0)
     * @param totalEligible 符合资格的投票人总数
     */
    public boolean passesWithThreshold(double requiredRate, int totalEligible) {
        if (totalEligible <= 0) return false;
        double voteRate = (double) totalVotes() / totalEligible;
        return voteRate >= requiredRate && passes();
    }

    public void markApproved() {
        this.state = MergeReferendumState.APPROVED;
    }

    public void markRejected() {
        this.state = MergeReferendumState.REJECTED;
    }

    public void markExpired() {
        this.state = MergeReferendumState.EXPIRED;
    }

    public void markCancelled() {
        this.state = MergeReferendumState.CANCELLED;
    }

    public void markExecuted(NationId resultNationId) {
        this.state = MergeReferendumState.EXECUTED;
        this.resultNationId = resultNationId;
    }

    public void markFailed(String message) {
        this.state = MergeReferendumState.FAILED;
        this.resultMessage = message;
    }

    public void setResultMessage(String message) {
        this.resultMessage = message;
    }

    /**
     * 检查公投是否涉及指定国家
     */
    public boolean involvesNation(NationId nationId) {
        return proposerNationId.equals(nationId) || targetNationId.equals(nationId);
    }

    @Override
    public String toString() {
        return String.format(
            "MergeReferendum[id=%s, state=%s, proposer=%s->%s, target=%s, votes=%d/%d]",
            id.toString().substring(0, 8),
            state,
            proposerNationId.toString().substring(0, 8),
            targetNationId.toString().substring(0, 8),
            newNationName,
            votes.size(),
            againstVotes.size()
        );
    }
}