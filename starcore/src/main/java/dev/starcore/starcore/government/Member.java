package dev.starcore.starcore.government;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 议员记录
 */
public final class Member {
    private final UUID playerId;
    private final int parliamentId;
    private final Instant electedAt;
    private Instant termEndsAt;
    private boolean active;
    private Integer partyId;
    private String constituency;  // 选区
    private int votesReceived;
    private int billsProposed;
    private int votesParticipated;

    public Member(UUID playerId, int parliamentId, Instant electedAt) {
        this.playerId = Objects.requireNonNull(playerId, "playerId");
        this.parliamentId = parliamentId;
        this.electedAt = Objects.requireNonNull(electedAt, "electedAt");
        this.active = true;
        this.billsProposed = 0;
        this.votesParticipated = 0;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public int getParliamentId() {
        return parliamentId;
    }

    public Instant getElectedAt() {
        return electedAt;
    }

    public Optional<Instant> getTermEndsAt() {
        return Optional.ofNullable(termEndsAt);
    }

    public void setTermEndsAt(Instant termEndsAt) {
        this.termEndsAt = termEndsAt;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Optional<Integer> getPartyId() {
        return Optional.ofNullable(partyId);
    }

    public void setPartyId(Integer partyId) {
        this.partyId = partyId;
    }

    public Optional<String> getConstituency() {
        return Optional.ofNullable(constituency);
    }

    public void setConstituency(String constituency) {
        this.constituency = constituency;
    }

    public int getVotesReceived() {
        return votesReceived;
    }

    public void setVotesReceived(int votesReceived) {
        this.votesReceived = votesReceived;
    }

    public int getBillsProposed() {
        return billsProposed;
    }

    public void setBillsProposed(int billsProposed) {
        this.billsProposed = billsProposed;
    }

    public void incrementBillsProposed() {
        this.billsProposed++;
    }

    public int getVotesParticipated() {
        return votesParticipated;
    }

    public void setVotesParticipated(int votesParticipated) {
        this.votesParticipated = votesParticipated;
    }

    public void incrementVotesParticipated() {
        this.votesParticipated++;
    }

    /**
     * 检查任期是否已到期
     */
    public boolean isTermExpired() {
        return termEndsAt != null && Instant.now().isAfter(termEndsAt);
    }

    /**
     * 计算出席率
     */
    public double getAttendanceRate(int totalVotes) {
        if (totalVotes == 0) {
            return 1.0;
        }
        return (double) votesParticipated / totalVotes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Member member = (Member) o;
        return playerId.equals(member.playerId) && parliamentId == member.parliamentId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(playerId, parliamentId);
    }

    @Override
    public String toString() {
        return "Member{" +
                "playerId=" + playerId +
                ", parliamentId=" + parliamentId +
                ", active=" + active +
                ", electedAt=" + electedAt +
                '}';
    }
}
