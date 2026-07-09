package dev.starcore.starcore.government;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 上诉记录
 */
public final class Appeal {
    private final int appealId;
    private final int originalCaseId;
    private final int originalVerdictId;
    private final UUID appellant;       // 上诉人
    private final String grounds;       // 上诉理由
    private final Instant filedAt;
    private AppealStatus status;
    private UUID assignedJudge;
    private Integer newVerdictId;
    private Instant reviewedAt;
    private String decision;

    public enum AppealStatus {
        FILED,          // 已提交
        UNDER_REVIEW,   // 审查中
        SCHEDULED,      // 已排期
        IN_HEARING,     // 听证中
        UPHELD,         // 维持原判
        REVERSED,       // 改判
        REMANDED,       // 发回重审
        DISMISSED       // 驳回上诉
    }

    public Appeal(int appealId, int originalCaseId, int originalVerdictId,
                  UUID appellant, String grounds, Instant filedAt) {
        this.appealId = appealId;
        this.originalCaseId = originalCaseId;
        this.originalVerdictId = originalVerdictId;
        this.appellant = Objects.requireNonNull(appellant, "appellant");
        this.grounds = Objects.requireNonNull(grounds, "grounds");
        this.filedAt = Objects.requireNonNull(filedAt, "filedAt");
        this.status = AppealStatus.FILED;
    }

    public int getAppealId() {
        return appealId;
    }

    public int getOriginalCaseId() {
        return originalCaseId;
    }

    public int getOriginalVerdictId() {
        return originalVerdictId;
    }

    public UUID getAppellant() {
        return appellant;
    }

    public String getGrounds() {
        return grounds;
    }

    public Instant getFiledAt() {
        return filedAt;
    }

    public AppealStatus getStatus() {
        return status;
    }

    public void setStatus(AppealStatus status) {
        this.status = Objects.requireNonNull(status, "status");
    }

    public Optional<UUID> getAssignedJudge() {
        return Optional.ofNullable(assignedJudge);
    }

    public void setAssignedJudge(UUID judgeId) {
        this.assignedJudge = judgeId;
    }

    public Optional<Integer> getNewVerdictId() {
        return Optional.ofNullable(newVerdictId);
    }

    public void setNewVerdictId(Integer verdictId) {
        this.newVerdictId = verdictId;
    }

    public Optional<Instant> getReviewedAt() {
        return Optional.ofNullable(reviewedAt);
    }

    public void setReviewedAt(Instant reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public Optional<String> getDecision() {
        return Optional.ofNullable(decision);
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    /**
     * 检查上诉是否已完成
     */
    public boolean isCompleted() {
        return status == AppealStatus.UPHELD ||
               status == AppealStatus.REVERSED ||
               status == AppealStatus.REMANDED ||
               status == AppealStatus.DISMISSED;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Appeal appeal = (Appeal) o;
        return appealId == appeal.appealId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(appealId);
    }

    @Override
    public String toString() {
        return "Appeal{" +
                "appealId=" + appealId +
                ", originalCaseId=" + originalCaseId +
                ", status=" + status +
                ", filedAt=" + filedAt +
                '}';
    }
}
