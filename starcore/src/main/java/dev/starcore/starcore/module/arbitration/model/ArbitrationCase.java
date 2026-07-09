package dev.starcore.starcore.module.arbitration.model;

import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.foundation.territory.model.ChunkCoordinate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 仲裁案例
 * 代表一个领土纠纷仲裁案件
 */
public final class ArbitrationCase {
    private final UUID id;
    private final NationId claimant;                    // 申诉方
    private NationId respondent;                        // 被申诉方
    private ArbitrationCaseType caseType;                // 案件类型
    private ArbitrationStatus status;                   // 当前状态
    private final List<ChunkCoordinate> disputedChunks; // 争议区块
    private final List<String> claimantEvidence;        // 申诉方证据
    private final List<String> respondentEvidence;      // 被申诉方证据
    private String defense;                              // 被告答辩
    private UUID arbitrator;                             // 仲裁员
    private Instant acceptedAt;                          // 受理时间
    private Instant rulingAt;                           // 裁决时间
    private ArbitrationResult result;                    // 裁决结果
    private String ruling;                               // 裁决详情
    private String rulingReason;                         // 裁决理由
    private final BigDecimal claimFee;                   // 申诉费用
    private final Instant createdAt;                    // 创建时间
    private Instant updatedAt;                           // 更新时间
    private final List<String> caseHistory;             // 案件历史记录

    public ArbitrationCase(
        UUID id,
        NationId claimant,
        NationId respondent,
        ArbitrationCaseType caseType,
        List<ChunkCoordinate> disputedChunks,
        String evidence,
        BigDecimal claimFee
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.claimant = Objects.requireNonNull(claimant, "claimant");
        this.respondent = Objects.requireNonNull(respondent, "respondent");
        this.caseType = Objects.requireNonNull(caseType, "caseType");
        this.disputedChunks = new ArrayList<>(Objects.requireNonNull(disputedChunks, "disputedChunks"));
        this.claimantEvidence = new ArrayList<>();
        this.respondentEvidence = new ArrayList<>();
        this.claimFee = Objects.requireNonNull(claimFee, "claimFee");
        this.status = ArbitrationStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        this.caseHistory = new ArrayList<>();

        if (evidence != null && !evidence.isBlank()) {
            addClaimantEvidence(evidence);
        }

        addHistory("案件已提交");
    }

    // ==================== Getters ====================

    public UUID id() {
        return id;
    }

    public NationId claimant() {
        return claimant;
    }

    public NationId respondent() {
        return respondent;
    }

    public ArbitrationCaseType caseType() {
        return caseType;
    }

    public ArbitrationStatus status() {
        return status;
    }

    public List<ChunkCoordinate> disputedChunks() {
        return List.copyOf(disputedChunks);
    }

    public List<String> claimantEvidence() {
        return List.copyOf(claimantEvidence);
    }

    public List<String> respondentEvidence() {
        return List.copyOf(respondentEvidence);
    }

    public String defense() {
        return defense;
    }

    public UUID arbitrator() {
        return arbitrator;
    }

    public Instant acceptedAt() {
        return acceptedAt;
    }

    public Instant rulingAt() {
        return rulingAt;
    }

    public ArbitrationResult result() {
        return result;
    }

    public String ruling() {
        return ruling;
    }

    public String rulingReason() {
        return rulingReason;
    }

    public BigDecimal claimFee() {
        return claimFee;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public List<String> caseHistory() {
        return List.copyOf(caseHistory);
    }

    // ==================== Status Modification ====================

    public void setRespondent(NationId respondent) {
        this.respondent = Objects.requireNonNull(respondent, "respondent");
        markUpdated();
    }

    public void setCaseType(ArbitrationCaseType caseType) {
        this.caseType = Objects.requireNonNull(caseType, "caseType");
        markUpdated();
    }

    public void setStatus(ArbitrationStatus status) {
        ArbitrationStatus oldStatus = this.status;
        this.status = Objects.requireNonNull(status, "status");
        addHistory("状态变更: " + oldStatus.displayName() + " -> " + status.displayName());
        markUpdated();
    }

    // ==================== Evidence Management ====================

    public void addClaimantEvidence(String evidence) {
        if (evidence != null && !evidence.isBlank()) {
            this.claimantEvidence.add("[" + Instant.now() + "] " + evidence);
            addHistory("申诉方添加证据");
            markUpdated();
        }
    }

    public void addRespondentEvidence(String evidence) {
        if (evidence != null && !evidence.isBlank()) {
            this.respondentEvidence.add("[" + Instant.now() + "] " + evidence);
            addHistory("被申诉方添加证据");
            markUpdated();
        }
    }

    public boolean addEvidence(NationId submitter, String evidence) {
        if (submitter.equals(claimant)) {
            addClaimantEvidence(evidence);
            return true;
        } else if (submitter.equals(respondent)) {
            addRespondentEvidence(evidence);
            return true;
        }
        return false;
    }

    // ==================== Defense ====================

    public boolean submitDefense(NationId submitter, String defense) {
        if (!submitter.equals(respondent)) {
            return false;
        }
        if (this.defense != null && !this.defense.isBlank()) {
            return false; // 只能提交一次
        }
        this.defense = defense;
        addHistory("被申诉方提交答辩");
        markUpdated();
        return true;
    }

    // ==================== Arbitration ====================

    public boolean assignArbitrator(UUID arbitrator) {
        if (this.arbitrator != null) {
            return false;
        }
        this.arbitrator = Objects.requireNonNull(arbitrator, "arbitrator");
        this.acceptedAt = Instant.now();
        setStatus(ArbitrationStatus.IN_PROGRESS);
        addHistory("仲裁员已分配: " + arbitrator);
        return true;
    }

    // ==================== Ruling ====================

    public boolean makeRuling(ArbitrationResult result, String ruling, String reason) {
        if (this.arbitrator == null) {
            return false;
        }
        this.result = Objects.requireNonNull(result, "result");
        this.ruling = ruling;
        this.rulingReason = reason;
        this.rulingAt = Instant.now();
        setStatus(ArbitrationStatus.COMPLETED);
        addHistory("裁决已做出: " + result.displayName());
        return true;
    }

    // ==================== Withdrawal ====================

    public boolean withdraw() {
        if (status().isTerminal()) {
            return false;
        }
        setStatus(ArbitrationStatus.WITHDRAWN);
        addHistory("案件已撤销");
        return true;
    }

    // ==================== Utility ====================

    private void addHistory(String entry) {
        String timestamp = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault())
            .format(Instant.now());
        this.caseHistory.add("[" + timestamp + "] " + entry);
    }

    private void markUpdated() {
        this.updatedAt = Instant.now();
    }

    /**
     * 检查是否为相关方
     */
    public boolean involvesNation(NationId nationId) {
        return claimant.equals(nationId) || respondent.equals(nationId);
    }

    /**
     * 检查是否为申诉方
     */
    public boolean isClaimant(NationId nationId) {
        return claimant.equals(nationId);
    }

    /**
     * 检查是否为被申诉方
     */
    public boolean isRespondent(NationId nationId) {
        return respondent.equals(nationId);
    }

    /**
     * 获取争议区块数量
     */
    public int disputedChunkCount() {
        return disputedChunks.size();
    }

    /**
     * 获取总证据数量
     */
    public int totalEvidenceCount() {
        return claimantEvidence.size() + respondentEvidence.size();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ArbitrationCase that)) return false;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return String.format("ArbitrationCase{id=%s, type=%s, status=%s, claimant=%s, respondent=%s}",
            id, caseType, status, claimant, respondent);
    }

    // ==================== Builder ====================

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UUID id;
        private NationId claimant;
        private NationId respondent;
        private ArbitrationCaseType caseType;
        private List<ChunkCoordinate> disputedChunks = new ArrayList<>();
        private String evidence;
        private BigDecimal claimFee = BigDecimal.ZERO;

        public Builder id(UUID id) {
            this.id = id;
            return this;
        }

        public Builder claimant(NationId claimant) {
            this.claimant = claimant;
            return this;
        }

        public Builder respondent(NationId respondent) {
            this.respondent = respondent;
            return this;
        }

        public Builder caseType(ArbitrationCaseType caseType) {
            this.caseType = caseType;
            return this;
        }

        public Builder disputedChunks(List<ChunkCoordinate> disputedChunks) {
            this.disputedChunks = disputedChunks;
            return this;
        }

        public Builder evidence(String evidence) {
            this.evidence = evidence;
            return this;
        }

        public Builder claimFee(BigDecimal claimFee) {
            this.claimFee = claimFee;
            return this;
        }

        public ArbitrationCase build() {
            return new ArbitrationCase(
                id != null ? id : UUID.randomUUID(),
                claimant,
                respondent,
                caseType,
                disputedChunks,
                evidence,
                claimFee
            );
        }
    }
}
