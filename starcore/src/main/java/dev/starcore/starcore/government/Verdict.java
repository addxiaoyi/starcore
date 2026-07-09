package dev.starcore.starcore.government;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 判决记录
 */
public final class Verdict {
    private final int verdictId;
    private final int caseId;
    private final UUID judgeId;
    private final VerdictType verdictType;
    private final String reasoning;
    private final Instant issuedAt;
    private Double fineAmount;
    private Integer jailTimeMinutes;
    private Boolean banishment;
    private String additionalConditions;

    public enum VerdictType {
        GUILTY,
        NOT_GUILTY,
        DISMISSED,
        SETTLED
    }

    public Verdict(int verdictId, int caseId, UUID judgeId,
                   VerdictType verdictType, String reasoning, Instant issuedAt) {
        this.verdictId = verdictId;
        this.caseId = caseId;
        this.judgeId = Objects.requireNonNull(judgeId, "judgeId");
        this.verdictType = Objects.requireNonNull(verdictType, "verdictType");
        this.reasoning = Objects.requireNonNull(reasoning, "reasoning");
        this.issuedAt = Objects.requireNonNull(issuedAt, "issuedAt");
    }

    public int getVerdictId() {
        return verdictId;
    }

    public int getCaseId() {
        return caseId;
    }

    public UUID getJudgeId() {
        return judgeId;
    }

    public VerdictType getVerdictType() {
        return verdictType;
    }

    public String getReasoning() {
        return reasoning;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public Optional<Double> getFineAmount() {
        return Optional.ofNullable(fineAmount);
    }

    public void setFineAmount(Double fineAmount) {
        if (fineAmount != null && fineAmount < 0) {
            throw new IllegalArgumentException("Fine amount cannot be negative");
        }
        this.fineAmount = fineAmount;
    }

    public Optional<Integer> getJailTimeMinutes() {
        return Optional.ofNullable(jailTimeMinutes);
    }

    public void setJailTimeMinutes(Integer jailTimeMinutes) {
        if (jailTimeMinutes != null && jailTimeMinutes < 0) {
            throw new IllegalArgumentException("Jail time cannot be negative");
        }
        this.jailTimeMinutes = jailTimeMinutes;
    }

    public boolean isBanishment() {
        return Boolean.TRUE.equals(banishment);
    }

    public void setBanishment(Boolean banishment) {
        this.banishment = banishment;
    }

    public Optional<String> getAdditionalConditions() {
        return Optional.ofNullable(additionalConditions);
    }

    public void setAdditionalConditions(String additionalConditions) {
        this.additionalConditions = additionalConditions;
    }

    /**
     * 检查判决是否有惩罚措施
     */
    public boolean hasPunishment() {
        return fineAmount != null || jailTimeMinutes != null || Boolean.TRUE.equals(banishment);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Verdict verdict = (Verdict) o;
        return verdictId == verdict.verdictId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(verdictId);
    }

    @Override
    public String toString() {
        return "Verdict{" +
                "verdictId=" + verdictId +
                ", caseId=" + caseId +
                ", verdictType=" + verdictType +
                ", issuedAt=" + issuedAt +
                ", hasPunishment=" + hasPunishment() +
                '}';
    }
}
