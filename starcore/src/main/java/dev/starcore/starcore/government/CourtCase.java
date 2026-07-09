package dev.starcore.starcore.government;
import java.util.Optional;

import java.time.Instant;
import java.util.*;

/**
 * 法庭案件记录
 */
public final class CourtCase {
    private final int caseId;
    private final UUID plaintiff;        // 原告
    private final UUID defendant;        // 被告
    private final CaseType caseType;
    private final String description;
    private final Instant filedAt;
    private CaseStatus status;
    private UUID assignedJudge;
    private List<UUID> jury;
    private Integer verdictId;
    private Instant hearingDate;
    private String evidence;

    public enum CaseType {
        CRIMINAL,       // 刑事案件
        CIVIL,          // 民事案件
        ADMINISTRATIVE, // 行政案件
        CONSTITUTIONAL  // 宪法案件
    }

    public enum CaseStatus {
        FILED,          // 已提交
        UNDER_REVIEW,   // 审查中
        SCHEDULED,      // 已排期
        IN_TRIAL,       // 审理中
        VERDICT,        // 已判决
        APPEALED,       // 已上诉
        CLOSED          // 已结案
    }

    public CourtCase(int caseId, UUID plaintiff, UUID defendant,
                     CaseType caseType, String description, Instant filedAt) {
        this.caseId = caseId;
        this.plaintiff = Objects.requireNonNull(plaintiff, "plaintiff");
        this.defendant = Objects.requireNonNull(defendant, "defendant");
        this.caseType = Objects.requireNonNull(caseType, "caseType");
        this.description = Objects.requireNonNull(description, "description");
        this.filedAt = Objects.requireNonNull(filedAt, "filedAt");
        this.status = CaseStatus.FILED;
        this.jury = new ArrayList<>();
    }

    public int getCaseId() {
        return caseId;
    }

    public UUID getPlaintiff() {
        return plaintiff;
    }

    public UUID getDefendant() {
        return defendant;
    }

    public CaseType getCaseType() {
        return caseType;
    }

    public String getDescription() {
        return description;
    }

    public Instant getFiledAt() {
        return filedAt;
    }

    public CaseStatus getStatus() {
        return status;
    }

    public void setStatus(CaseStatus status) {
        this.status = Objects.requireNonNull(status, "status");
    }

    public Optional<UUID> getAssignedJudge() {
        return Optional.ofNullable(assignedJudge);
    }

    public void setAssignedJudge(UUID judgeId) {
        this.assignedJudge = judgeId;
    }

    public List<UUID> getJury() {
        return Collections.unmodifiableList(jury);
    }

    public void setJury(List<UUID> jury) {
        this.jury = new ArrayList<>(jury);
    }

    public Optional<Integer> getVerdictId() {
        return Optional.ofNullable(verdictId);
    }

    public void setVerdictId(Integer verdictId) {
        this.verdictId = verdictId;
    }

    public Optional<Instant> getHearingDate() {
        return Optional.ofNullable(hearingDate);
    }

    public void setHearingDate(Instant hearingDate) {
        this.hearingDate = hearingDate;
    }

    public Optional<String> getEvidence() {
        return Optional.ofNullable(evidence);
    }

    public void setEvidence(String evidence) {
        this.evidence = evidence;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CourtCase courtCase = (CourtCase) o;
        return caseId == courtCase.caseId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(caseId);
    }

    @Override
    public String toString() {
        return "CourtCase{" +
                "caseId=" + caseId +
                ", caseType=" + caseType +
                ", status=" + status +
                ", filedAt=" + filedAt +
                '}';
    }
}
