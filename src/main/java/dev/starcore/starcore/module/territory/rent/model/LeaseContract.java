package dev.starcore.starcore.module.territory.rent.model;

import dev.starcore.starcore.foundation.territory.model.ChunkCoordinate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 租借契约
 * Represents a territory lease contract between nations or players.
 */
public final class LeaseContract {
    private final UUID contractId;
    private final UUID lessorNationId;
    private final UUID lesseeNationId;
    private final UUID lesseePlayerId;
    private final Instant startTime;
    private final Instant endTime;
    private final LeaseStatus status;
    private final BigDecimal totalRent;
    private final BigDecimal rentPerDay;
    private final BigDecimal rentPerChunk;
    private final int chunksCount;
    private final String world;
    private final List<ChunkCoordinate> chunkCoords;
    private final BigDecimal creationFee;
    private final boolean autoRenewal;
    private final int renewalCount;
    private final String terminationReason;
    private final UUID terminatedBy;
    private final Instant terminatedAt;
    private final Instant createdAt;
    private final Instant updatedAt;

    public LeaseContract(
        UUID contractId,
        UUID lessorNationId,
        UUID lesseeNationId,
        UUID lesseePlayerId,
        Instant startTime,
        Instant endTime,
        LeaseStatus status,
        BigDecimal totalRent,
        BigDecimal rentPerDay,
        BigDecimal rentPerChunk,
        int chunksCount,
        String world,
        List<ChunkCoordinate> chunkCoords,
        BigDecimal creationFee,
        boolean autoRenewal,
        int renewalCount,
        String terminationReason,
        UUID terminatedBy,
        Instant terminatedAt,
        Instant createdAt,
        Instant updatedAt
    ) {
        this.contractId = Objects.requireNonNull(contractId, "contractId");
        this.lessorNationId = Objects.requireNonNull(lessorNationId, "lessorNationId");
        this.lesseeNationId = lesseeNationId;
        this.lesseePlayerId = lesseePlayerId;
        this.startTime = Objects.requireNonNull(startTime, "startTime");
        this.endTime = Objects.requireNonNull(endTime, "endTime");
        this.status = Objects.requireNonNull(status, "status");
        this.totalRent = Objects.requireNonNull(totalRent, "totalRent");
        this.rentPerDay = Objects.requireNonNull(rentPerDay, "rentPerDay");
        this.rentPerChunk = Objects.requireNonNull(rentPerChunk, "rentPerChunk");
        this.chunksCount = chunksCount;
        this.world = Objects.requireNonNull(world, "world");
        this.chunkCoords = List.copyOf(Objects.requireNonNull(chunkCoords, "chunkCoords"));
        this.creationFee = Objects.requireNonNull(creationFee, "creationFee");
        this.autoRenewal = autoRenewal;
        this.renewalCount = renewalCount;
        this.terminationReason = terminationReason;
        this.terminatedBy = terminatedBy;
        this.terminatedAt = terminatedAt;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    /**
     * Create a new lease contract.
     */
    public static LeaseContract create(
        UUID lessorNationId,
        UUID lesseeNationId,
        UUID lesseePlayerId,
        Instant startTime,
        Instant endTime,
        BigDecimal totalRent,
        BigDecimal rentPerDay,
        BigDecimal rentPerChunk,
        List<ChunkCoordinate> chunkCoords,
        String world,
        BigDecimal creationFee
    ) {
        Instant now = Instant.now();
        return new LeaseContract(
            UUID.randomUUID(),
            lessorNationId,
            lesseeNationId,
            lesseePlayerId,
            startTime,
            endTime,
            LeaseStatus.ACTIVE,
            totalRent,
            rentPerDay,
            rentPerChunk,
            chunkCoords.size(),
            world,
            chunkCoords,
            creationFee,
            false,
            0,
            null,
            null,
            null,
            now,
            now
        );
    }

    // ==================== Getters ====================

    public UUID contractId() {
        return contractId;
    }

    public UUID lessorNationId() {
        return lessorNationId;
    }

    public UUID lesseeNationId() {
        return lesseeNationId;
    }

    public UUID lesseePlayerId() {
        return lesseePlayerId;
    }

    public Instant startTime() {
        return startTime;
    }

    public Instant endTime() {
        return endTime;
    }

    public LeaseStatus status() {
        return status;
    }

    public BigDecimal totalRent() {
        return totalRent;
    }

    public BigDecimal rentPerDay() {
        return rentPerDay;
    }

    public BigDecimal rentPerChunk() {
        return rentPerChunk;
    }

    public int chunksCount() {
        return chunksCount;
    }

    public String world() {
        return world;
    }

    public List<ChunkCoordinate> chunkCoords() {
        return chunkCoords;
    }

    public BigDecimal creationFee() {
        return creationFee;
    }

    public boolean autoRenewal() {
        return autoRenewal;
    }

    public int renewalCount() {
        return renewalCount;
    }

    public String terminationReason() {
        return terminationReason;
    }

    public UUID terminatedBy() {
        return terminatedBy;
    }

    public Instant terminatedAt() {
        return terminatedAt;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    // ==================== Computed Properties ====================

    /**
     * Check if contract is currently active.
     */
    public boolean isActive() {
        return status == LeaseStatus.ACTIVE && Instant.now().isBefore(endTime);
    }

    /**
     * Check if contract is expired.
     */
    public boolean isExpired() {
        return Instant.now().isAfter(endTime);
    }

    /**
     * Get remaining days.
     */
    public long remainingDays() {
        long seconds = endTime.getEpochSecond() - Instant.now().getEpochSecond();
        return Math.max(0, seconds / 86400);
    }

    /**
     * Check if the contract has a specific chunk.
     */
    public boolean hasChunk(ChunkCoordinate coord) {
        return chunkCoords.contains(coord);
    }

    /**
     * Check if lessee is a nation.
     */
    public boolean isNationLease() {
        return lesseeNationId != null;
    }

    // ==================== Mutators ====================

    /**
     * Create a copy with updated status.
     */
    public LeaseContract withStatus(LeaseStatus newStatus) {
        return new LeaseContract(
            contractId, lessorNationId, lesseeNationId, lesseePlayerId,
            startTime, endTime, newStatus, totalRent, rentPerDay, rentPerChunk,
            chunksCount, world, chunkCoords, creationFee, autoRenewal, renewalCount,
            terminationReason, terminatedBy, terminatedAt, createdAt, Instant.now()
        );
    }

    /**
     * Create a copy with termination info.
     */
    public LeaseContract terminated(UUID by, String reason) {
        return new LeaseContract(
            contractId, lessorNationId, lesseeNationId, lesseePlayerId,
            startTime, endTime, LeaseStatus.TERMINATED, totalRent, rentPerDay, rentPerChunk,
            chunksCount, world, chunkCoords, creationFee, autoRenewal, renewalCount,
            reason, by, Instant.now(), createdAt, Instant.now()
        );
    }

    /**
     * Create a copy with auto-renewal enabled.
     */
    public LeaseContract withAutoRenewal(boolean enabled) {
        return new LeaseContract(
            contractId, lessorNationId, lesseeNationId, lesseePlayerId,
            startTime, endTime, status, totalRent, rentPerDay, rentPerChunk,
            chunksCount, world, chunkCoords, creationFee, enabled, renewalCount,
            terminationReason, terminatedBy, terminatedAt, createdAt, Instant.now()
        );
    }

    /**
     * Create a renewed copy.
     */
    public LeaseContract renewed(Instant newEndTime, BigDecimal newTotalRent) {
        return new LeaseContract(
            contractId, lessorNationId, lesseeNationId, lesseePlayerId,
            startTime, newEndTime, LeaseStatus.ACTIVE, newTotalRent, rentPerDay, rentPerChunk,
            chunksCount, world, chunkCoords, BigDecimal.ZERO, autoRenewal, renewalCount + 1,
            terminationReason, terminatedBy, terminatedAt, createdAt, Instant.now()
        );
    }

    /**
     * 审计 A-068: 创建副本并更新 updatedAt 字段，用于收租后刷新时间戳避免重复扣款。
     */
    public LeaseContract withUpdatedAt(Instant newUpdatedAt) {
        return new LeaseContract(
            contractId, lessorNationId, lesseeNationId, lesseePlayerId,
            startTime, endTime, status, totalRent, rentPerDay, rentPerChunk,
            chunksCount, world, chunkCoords, creationFee, autoRenewal, renewalCount,
            terminationReason, terminatedBy, terminatedAt, createdAt, newUpdatedAt
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LeaseContract that)) return false;
        return contractId.equals(that.contractId);
    }

    @Override
    public int hashCode() {
        return contractId.hashCode();
    }

    @Override
    public String toString() {
        return String.format("LeaseContract{id=%s, lessor=%s, lessee=%s, chunks=%d, status=%s, end=%s}",
            contractId, lessorNationId,
            isNationLease() ? lesseeNationId : lesseePlayerId,
            chunksCount, status, endTime);
    }
}
