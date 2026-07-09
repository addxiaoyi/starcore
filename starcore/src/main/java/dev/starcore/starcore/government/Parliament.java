package dev.starcore.starcore.government;
import java.util.Optional;

import java.time.Instant;
import java.util.*;

/**
 * 议会
 */
public final class Parliament {
    private final int parliamentId;
    private final String nationId;
    private final String name;
    private final Instant establishedAt;
    private int totalSeats;
    private int termLengthDays;
    private boolean active;
    private Instant nextElectionAt;

    public Parliament(int parliamentId, String nationId, String name,
                      Instant establishedAt, int totalSeats, int termLengthDays) {
        this.parliamentId = parliamentId;
        this.nationId = Objects.requireNonNull(nationId, "nationId");
        this.name = Objects.requireNonNull(name, "name");
        this.establishedAt = Objects.requireNonNull(establishedAt, "establishedAt");
        this.totalSeats = totalSeats;
        this.termLengthDays = termLengthDays;
        this.active = true;
    }

    public int getParliamentId() {
        return parliamentId;
    }

    public String getNationId() {
        return nationId;
    }

    public String getName() {
        return name;
    }

    public Instant getEstablishedAt() {
        return establishedAt;
    }

    public int getTotalSeats() {
        return totalSeats;
    }

    public void setTotalSeats(int totalSeats) {
        if (totalSeats < 1) {
            throw new IllegalArgumentException("Total seats must be positive");
        }
        this.totalSeats = totalSeats;
    }

    public int getTermLengthDays() {
        return termLengthDays;
    }

    public void setTermLengthDays(int termLengthDays) {
        if (termLengthDays < 1) {
            throw new IllegalArgumentException("Term length must be positive");
        }
        this.termLengthDays = termLengthDays;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Optional<Instant> getNextElectionAt() {
        return Optional.ofNullable(nextElectionAt);
    }

    public void setNextElectionAt(Instant nextElectionAt) {
        this.nextElectionAt = nextElectionAt;
    }

    /**
     * 检查是否需要选举
     */
    public boolean needsElection() {
        return nextElectionAt != null && Instant.now().isAfter(nextElectionAt);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Parliament that = (Parliament) o;
        return parliamentId == that.parliamentId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(parliamentId);
    }

    @Override
    public String toString() {
        return "Parliament{" +
                "parliamentId=" + parliamentId +
                ", name='" + name + '\'' +
                ", nationId='" + nationId + '\'' +
                ", totalSeats=" + totalSeats +
                ", active=" + active +
                '}';
    }
}
