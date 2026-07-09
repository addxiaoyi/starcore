package dev.starcore.starcore.government;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 法官记录
 */
public final class Judge {
    private final UUID playerId;
    private final Instant appointedAt;
    private Instant termEndsAt;
    private boolean active;
    private int casesHandled;
    private String specialization;  // 专业领域

    public Judge(UUID playerId, Instant appointedAt) {
        this.playerId = Objects.requireNonNull(playerId, "playerId");
        this.appointedAt = Objects.requireNonNull(appointedAt, "appointedAt");
        this.active = true;
        this.casesHandled = 0;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public Instant getAppointedAt() {
        return appointedAt;
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

    public int getCasesHandled() {
        return casesHandled;
    }

    public void incrementCasesHandled() {
        this.casesHandled++;
    }

    public Optional<String> getSpecialization() {
        return Optional.ofNullable(specialization);
    }

    public void setSpecialization(String specialization) {
        this.specialization = specialization;
    }

    /**
     * 检查任期是否已到期
     */
    public boolean isTermExpired() {
        return termEndsAt != null && Instant.now().isAfter(termEndsAt);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Judge judge = (Judge) o;
        return playerId.equals(judge.playerId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(playerId);
    }

    @Override
    public String toString() {
        return "Judge{" +
                "playerId=" + playerId +
                ", appointedAt=" + appointedAt +
                ", active=" + active +
                ", casesHandled=" + casesHandled +
                '}';
    }
}
