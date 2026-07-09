package dev.starcore.starcore.module.territory.rent;

import dev.starcore.starcore.foundation.territory.model.ChunkCoordinate;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.territory.rent.model.LeaseContract;
import dev.starcore.starcore.module.territory.rent.model.LeasePayment;
import dev.starcore.starcore.module.territory.rent.model.LeaseProposal;
import dev.starcore.starcore.module.territory.rent.model.LeaseStatus;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 领土租借服务接口
 */
public interface TerritoryRentService {

    // ==================== Contract Management ====================

    /**
     * Create a new lease proposal.
     */
    LeaseProposal createProposal(
        UUID proposerId,
        NationId lessorNationId,
        NationId lesseeNationId,
        UUID lesseePlayerId,
        List<ChunkCoordinate> chunks,
        String world,
        int durationDays,
        BigDecimal rentPerDay,
        BigDecimal rentPerChunk
    );

    /**
     * Accept a lease proposal.
     */
    LeaseContract acceptProposal(UUID proposalId, UUID accepterId);

    /**
     * Reject a lease proposal.
     */
    void rejectProposal(UUID proposalId, UUID rejecterId);

    /**
     * Create a lease contract directly (for confirmed proposals).
     */
    LeaseContract createContract(LeaseProposal proposal);

    /**
     * Get a contract by ID.
     */
    Optional<LeaseContract> getContract(UUID contractId);

    /**
     * Get all contracts for a nation (as lessor or lessee).
     */
    Collection<LeaseContract> getNationContracts(NationId nationId);

    /**
     * Get active contracts for a nation.
     */
    Collection<LeaseContract> getActiveContracts(NationId nationId);

    /**
     * Get contracts where nation is the lessor (出租方).
     */
    Collection<LeaseContract> getContractsAsLessor(NationId nationId);

    /**
     * Get contracts where nation is the lessee (承租方).
     */
    Collection<LeaseContract> getContractsAsLessee(NationId nationId);

    /**
     * Get contract for a specific chunk.
     */
    Optional<LeaseContract> getContractForChunk(String world, int chunkX, int chunkZ);

    /**
     * Check if a chunk is leased.
     */
    boolean isChunkLeased(String world, int chunkX, int chunkZ);

    /**
     * Check if a nation owns the lease rights to a chunk.
     */
    boolean canNationAccessChunk(NationId nationId, String world, int chunkX, int chunkZ);

    // ==================== Contract Lifecycle ====================

    /**
     * Terminate a contract early.
     */
    LeaseContract terminateContract(UUID contractId, UUID terminatorId, String reason);

    /**
     * Renew a contract.
     */
    LeaseContract renewContract(UUID contractId, UUID renewerId, int additionalDays);

    /**
     * Process daily rent collection.
     */
    void processDailyRent();

    /**
     * Expire contracts that have ended.
     */
    void expireContracts();

    // ==================== Rent & Payments ====================

    /**
     * Collect rent for a contract.
     */
    LeasePayment collectRent(LeaseContract contract);

    /**
     * Get payment history for a contract.
     */
    List<LeasePayment> getPaymentHistory(UUID contractId);

    /**
     * Get total rent earned by a nation.
     */
    BigDecimal getTotalRentEarned(NationId nationId);

    /**
     * Get total rent paid by a nation.
     */
    BigDecimal getTotalRentPaid(NationId nationId);

    // ==================== Configuration ====================

    /**
     * Get the default rent per chunk per day.
     */
    BigDecimal getDefaultRentPerChunk();

    /**
     * Get the creation fee.
     */
    BigDecimal getCreationFee();

    // ==================== Statistics ====================

    /**
     * Get lease statistics for a nation.
     */
    LeaseStats getStats(NationId nationId);

    /**
     * Get summary for this service.
     */
    String getSummary();

    /**
     * Reload configuration.
     */
    void reload();

    // ==================== Proposal Access ====================

    /**
     * Get pending proposals for a nation.
     */
    Collection<LeaseProposal> getPendingProposals(NationId nationId);

    /**
     * Get a proposal by ID.
     */
    Optional<LeaseProposal> getProposal(UUID proposalId);

    /**
     * Lease statistics record.
     */
    record LeaseStats(
        int totalContracts,
        int activeContractsAsLessor,
        int activeContractsAsLessee,
        BigDecimal totalRentEarned,
        BigDecimal totalRentPaid,
        int chunksLeasedOut,
        int chunksLeasedIn
    ) {}
}
