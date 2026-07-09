package dev.starcore.starcore.module.territory.rent.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * 租借提议
 * Represents a lease proposal before acceptance.
 */
public record LeaseProposal(
    UUID proposalId,
    UUID proposerId,
    UUID lessorNationId,
    UUID lesseeNationId,
    UUID lesseePlayerId,
    String world,
    List<dev.starcore.starcore.foundation.territory.model.ChunkCoordinate> chunkCoords,
    int durationDays,
    BigDecimal rentPerDay,
    BigDecimal rentPerChunk,
    BigDecimal creationFee,
    long expiresAt
) {
    /**
     * Check if proposal is expired.
     */
    public boolean isExpired() {
        return System.currentTimeMillis() > expiresAt;
    }

    /**
     * Calculate total rent for the duration.
     */
    public BigDecimal calculateTotalRent() {
        return rentPerChunk
            .multiply(BigDecimal.valueOf(chunkCoords.size()))
            .multiply(BigDecimal.valueOf(durationDays));
    }

    /**
     * Get proposer type.
     */
    public boolean isNationProposal() {
        return lesseeNationId != null;
    }
}
