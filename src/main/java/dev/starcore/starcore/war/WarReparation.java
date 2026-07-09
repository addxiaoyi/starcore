package dev.starcore.starcore.war;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 战争赔款
 * 记录战争赔款的支付情况
 */
public final class WarReparation {
    private final UUID id;
    private final UUID treatyId;
    private final UUID payerId;         // 支付方
    private final UUID receiverId;      // 接收方
    private final BigDecimal totalAmount;
    private BigDecimal paidAmount;
    private final int totalInstallments;
    private int paidInstallments;
    private final Instant startDate;
    private Instant lastPaymentDate;
    private ReparationStatus status;

    public WarReparation(
        UUID id,
        UUID treatyId,
        UUID payerId,
        UUID receiverId,
        BigDecimal totalAmount,
        int totalInstallments,
        Instant startDate
    ) {
        this(id, treatyId, payerId, receiverId, totalAmount, totalInstallments, startDate,
             BigDecimal.ZERO, 0, null, ReparationStatus.ACTIVE);
    }

    /**
     * 内部构造函数，用于从存储恢复状态
     */
    public WarReparation(
        UUID id,
        UUID treatyId,
        UUID payerId,
        UUID receiverId,
        BigDecimal totalAmount,
        int totalInstallments,
        Instant startDate,
        BigDecimal paidAmount,
        int paidInstallments,
        Instant lastPaymentDate,
        ReparationStatus status
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.treatyId = Objects.requireNonNull(treatyId, "treatyId");
        this.payerId = Objects.requireNonNull(payerId, "payerId");
        this.receiverId = Objects.requireNonNull(receiverId, "receiverId");
        this.totalAmount = Objects.requireNonNull(totalAmount, "totalAmount");
        this.paidAmount = paidAmount != null ? paidAmount : BigDecimal.ZERO;
        this.totalInstallments = Math.max(1, totalInstallments);
        this.paidInstallments = Math.max(0, paidInstallments);
        this.startDate = Objects.requireNonNull(startDate, "startDate");
        this.lastPaymentDate = lastPaymentDate;
        this.status = status != null ? status : ReparationStatus.ACTIVE;
    }

    public UUID id() {
        return id;
    }

    public UUID treatyId() {
        return treatyId;
    }

    public UUID payerId() {
        return payerId;
    }

    public UUID receiverId() {
        return receiverId;
    }

    public BigDecimal totalAmount() {
        return totalAmount;
    }

    public BigDecimal paidAmount() {
        return paidAmount;
    }

    public int totalInstallments() {
        return totalInstallments;
    }

    public int paidInstallments() {
        return paidInstallments;
    }

    public Instant startDate() {
        return startDate;
    }

    public Instant lastPaymentDate() {
        return lastPaymentDate;
    }

    public ReparationStatus status() {
        return status;
    }

    /**
     * 记录一次支付
     */
    public void recordPayment(BigDecimal amount) {
        if (status != ReparationStatus.ACTIVE) {
            throw new IllegalStateException("Reparation is not active");
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }

        this.paidAmount = paidAmount.add(amount);
        this.paidInstallments++;
        this.lastPaymentDate = Instant.now();

        // 检查是否完成
        if (paidAmount.compareTo(totalAmount) >= 0 || paidInstallments >= totalInstallments) {
            this.status = ReparationStatus.COMPLETED;
        }
    }

    /**
     * 违约
     */
    public void markDefault() {
        this.status = ReparationStatus.DEFAULTED;
    }

    /**
     * 免除
     */
    public void forgive() {
        this.status = ReparationStatus.FORGIVEN;
    }

    /**
     * 获取每期应付金额
     */
    public BigDecimal installmentAmount() {
        return totalAmount.divide(BigDecimal.valueOf(totalInstallments), 2, BigDecimal.ROUND_HALF_UP);
    }

    /**
     * 获取剩余应付金额
     */
    public BigDecimal remainingAmount() {
        return totalAmount.subtract(paidAmount);
    }

    /**
     * 获取完成百分比
     */
    public double progressPercentage() {
        if (totalAmount.compareTo(BigDecimal.ZERO) == 0) {
            return 100.0;
        }
        return paidAmount.divide(totalAmount, 4, BigDecimal.ROUND_HALF_UP)
            .multiply(BigDecimal.valueOf(100))
            .doubleValue();
    }

    /**
     * 是否已完成
     */
    public boolean isCompleted() {
        return status == ReparationStatus.COMPLETED || status == ReparationStatus.FORGIVEN;
    }

    /**
     * 是否逾期
     */
    public boolean isOverdue(Instant now) {
        if (status != ReparationStatus.ACTIVE || lastPaymentDate == null) {
            return false;
        }

        // 假设每月支付一次，超过30天未支付即为逾期
        long daysSinceLastPayment = (now.getEpochSecond() - lastPaymentDate.getEpochSecond()) / 86400;
        return daysSinceLastPayment > 30;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WarReparation other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return String.format("WarReparation{id=%s, status=%s, progress=%.1f%% (%s/%s), installments=%d/%d}",
            id, status, progressPercentage(), paidAmount, totalAmount, paidInstallments, totalInstallments);
    }

    /**
     * 赔款状态
     */
    public enum ReparationStatus {
        ACTIVE("进行中"),
        COMPLETED("已完成"),
        DEFAULTED("已违约"),
        FORGIVEN("已免除");

        private final String displayName;

        ReparationStatus(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }
    }
}
