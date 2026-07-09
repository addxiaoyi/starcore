package dev.starcore.starcore.module.territory.rent.model;

import java.math.BigDecimal;

/**
 * 租借统计数据
 */
public final class LeaseStats {
    private final int totalContracts;
    private final int activeContracts;
    private final int completedContracts;
    private final BigDecimal totalRentEarned;
    private final BigDecimal totalRentPaid;
    private final int totalChunksLeased;
    private final int totalPayments;

    public LeaseStats(int totalContracts, int activeContracts, int completedContracts,
                      BigDecimal totalRentEarned, BigDecimal totalRentPaid,
                      int totalChunksLeased, int totalPayments) {
        this.totalContracts = totalContracts;
        this.activeContracts = activeContracts;
        this.completedContracts = completedContracts;
        this.totalRentEarned = totalRentEarned != null ? totalRentEarned : BigDecimal.ZERO;
        this.totalRentPaid = totalRentPaid != null ? totalRentPaid : BigDecimal.ZERO;
        this.totalChunksLeased = totalChunksLeased;
        this.totalPayments = totalPayments;
    }

    public int totalContracts() {
        return totalContracts;
    }

    public int activeContracts() {
        return activeContracts;
    }

    public int completedContracts() {
        return completedContracts;
    }

    public BigDecimal totalRentEarned() {
        return totalRentEarned;
    }

    public BigDecimal totalRentPaid() {
        return totalRentPaid;
    }

    public int totalChunksLeased() {
        return totalChunksLeased;
    }

    public int totalPayments() {
        return totalPayments;
    }

    public static LeaseStats empty() {
        return new LeaseStats(0, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0);
    }
}
