package dev.starcore.starcore.module.lease.model;

import dev.starcore.starcore.module.nation.model.NationId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 租约契约数据模型
 */
public final class LeaseContract {

    private final UUID id;
    private final NationId lessorNationId;
    private final UUID lessorPlayerId;
    private final NationId tenantNationId;
    private final UUID tenantPlayerId;
    private final LeaseType type;
    private final String regionId;
    private final BigDecimal monthlyRent;
    private final BigDecimal totalValue;
    private LeaseStatus status;
    private Instant createdAt;
    private Instant signedAt;
    private Instant startDate;
    private Instant endDate;
    private Instant nextPaymentDue;
    private Instant lastPaymentAt;
    private boolean lessorSigned;
    private boolean tenantSigned;
    private int overdueDays;
    private String terminationReason;

    public LeaseContract(
        UUID id,
        NationId lessorNationId,
        UUID lessorPlayerId,
        NationId tenantNationId,
        UUID tenantPlayerId,
        LeaseType type,
        String regionId,
        BigDecimal monthlyRent,
        BigDecimal totalValue,
        LeaseStatus status,
        Instant createdAt,
        Instant signedAt,
        Instant startDate,
        Instant endDate,
        Instant nextPaymentDue,
        Instant lastPaymentAt,
        boolean lessorSigned,
        boolean tenantSigned,
        int overdueDays,
        String terminationReason
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.lessorNationId = lessorNationId;
        this.lessorPlayerId = Objects.requireNonNull(lessorPlayerId, "lessorPlayerId");
        this.tenantNationId = tenantNationId;
        this.tenantPlayerId = tenantPlayerId;
        this.type = Objects.requireNonNull(type, "type");
        this.regionId = Objects.requireNonNull(regionId, "regionId");
        this.monthlyRent = Objects.requireNonNull(monthlyRent, "monthlyRent");
        this.totalValue = Objects.requireNonNull(totalValue, "totalValue");
        this.status = Objects.requireNonNull(status, "status");
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.signedAt = signedAt;
        this.startDate = startDate;
        this.endDate = endDate;
        this.nextPaymentDue = nextPaymentDue;
        this.lastPaymentAt = lastPaymentAt;
        this.lessorSigned = lessorSigned;
        this.tenantSigned = tenantSigned;
        this.overdueDays = Math.max(0, overdueDays);
        this.terminationReason = terminationReason;
    }

    /**
     * 创建新的租约（草稿状态）
     */
    public static LeaseContract createDraft(
        NationId lessorNationId,
        UUID lessorPlayerId,
        NationId tenantNationId,
        UUID tenantPlayerId,
        LeaseType type,
        String regionId,
        BigDecimal monthlyRent,
        int durationDays
    ) {
        UUID id = UUID.randomUUID();
        BigDecimal totalValue = monthlyRent.multiply(BigDecimal.valueOf(durationDays / 30.0));

        return new LeaseContract(
            id,
            lessorNationId,
            lessorPlayerId,
            tenantNationId,
            tenantPlayerId,
            type,
            regionId,
            monthlyRent,
            totalValue,
            LeaseStatus.PENDING,
            Instant.now(),
            null,  // signedAt
            null,  // startDate
            null,  // endDate
            null,  // nextPaymentDue
            null,  // lastPaymentAt
            false, // lessorSigned
            false, // tenantSigned
            0,     // overdueDays
            null   // terminationReason
        );
    }

    // ==================== Getters ====================

    public UUID id() { return id; }
    public NationId lessorNationId() { return lessorNationId; }
    public UUID lessorPlayerId() { return lessorPlayerId; }
    public NationId tenantNationId() { return tenantNationId; }
    public UUID tenantPlayerId() { return tenantPlayerId; }
    public LeaseType type() { return type; }
    public String regionId() { return regionId; }
    public BigDecimal monthlyRent() { return monthlyRent; }
    public BigDecimal totalValue() { return totalValue; }
    public LeaseStatus status() { return status; }
    public Instant createdAt() { return createdAt; }
    public Instant signedAt() { return signedAt; }
    public Instant startDate() { return startDate; }
    public Instant endDate() { return endDate; }
    public Instant nextPaymentDue() { return nextPaymentDue; }
    public Instant lastPaymentAt() { return lastPaymentAt; }
    public boolean lessorSigned() { return lessorSigned; }
    public boolean tenantSigned() { return tenantSigned; }
    public int overdueDays() { return overdueDays; }
    public String terminationReason() { return terminationReason; }

    // ==================== Setters with state transitions ====================

    public void setStatus(LeaseStatus status) {
        this.status = Objects.requireNonNull(status, "status");
    }

    public void setSignedAt(Instant signedAt) {
        this.signedAt = signedAt;
    }

    public void setStartDate(Instant startDate) {
        this.startDate = startDate;
    }

    public void setEndDate(Instant endDate) {
        this.endDate = endDate;
    }

    public void setNextPaymentDue(Instant nextPaymentDue) {
        this.nextPaymentDue = nextPaymentDue;
    }

    public void setLastPaymentAt(Instant lastPaymentAt) {
        this.lastPaymentAt = lastPaymentAt;
    }

    public void setLessorSigned(boolean signed) {
        this.lessorSigned = signed;
    }

    public void setTenantSigned(boolean signed) {
        this.tenantSigned = signed;
    }

    public void setOverdueDays(int days) {
        this.overdueDays = Math.max(0, days);
    }

    public void setTerminationReason(String reason) {
        this.terminationReason = reason;
    }

    // ==================== Business Logic ====================

    /**
     * 检查是否需要双方签署
     */
    public boolean requiresBothSignatures() {
        return tenantNationId != null || tenantPlayerId != null;
    }

    /**
     * 检查是否已签署完成
     */
    public boolean isFullySigned() {
        if (!requiresBothSignatures()) {
            return lessorSigned;
        }
        return lessorSigned && tenantSigned;
    }

    /**
     * 签署租约
     */
    public boolean sign(UUID signerId) {
        if (status != LeaseStatus.PENDING) {
            return false;
        }

        if (signerId.equals(lessorPlayerId)) {
            this.lessorSigned = true;
        } else if (signerId.equals(tenantPlayerId)) {
            this.tenantSigned = true;
        } else {
            return false;
        }

        // 检查是否双方都已签署
        if (isFullySigned()) {
            this.status = LeaseStatus.ACTIVE;
            this.signedAt = Instant.now();
        }

        return true;
    }

    /**
     * 激活租约
     */
    public void activate(Instant startDate, Instant endDate) {
        this.status = LeaseStatus.ACTIVE;
        this.startDate = startDate;
        this.endDate = endDate;
        this.nextPaymentDue = startDate.plusSeconds(30L * 24 * 60 * 60); // 30天后首次付款
    }

    /**
     * 检查租约是否有效
     */
    public boolean isActive() {
        return status == LeaseStatus.ACTIVE;
    }

    /**
     * 检查租约是否已过期
     */
    public boolean isExpired() {
        return endDate != null && Instant.now().isAfter(endDate);
    }

    /**
     * 检查是否逾期
     */
    public boolean isOverdue() {
        return nextPaymentDue != null && Instant.now().isAfter(nextPaymentDue);
    }

    /**
     * 获取剩余天数
     */
    public int getRemainingDays() {
        if (endDate == null) {
            return -1;
        }
        long seconds = endDate.getEpochSecond() - Instant.now().getEpochSecond();
        return (int) Math.max(0, seconds / (24 * 60 * 60));
    }

    /**
     * 获取逾期天数
     */
    public int getDaysOverdue() {
        if (!isOverdue()) {
            return 0;
        }
        long seconds = Instant.now().getEpochSecond() - nextPaymentDue.getEpochSecond();
        return (int) (seconds / (24 * 60 * 60));
    }

    /**
     * 计算应付金额
     */
    public BigDecimal calculatePayment(int months) {
        return monthlyRent.multiply(BigDecimal.valueOf(months));
    }

    // ==================== Object Methods ====================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LeaseContract other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return String.format("LeaseContract{id=%s, type=%s, region=%s, status=%s, rent=%s/month}",
            id.toString().substring(0, 8), type, regionId, status, monthlyRent);
    }
}
