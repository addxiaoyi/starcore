package dev.starcore.starcore.module.territory.rent;
import java.util.Optional;

import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.config.ConfigurationService;
import dev.starcore.starcore.core.database.DatabaseService;
import dev.starcore.starcore.core.module.ModuleLayer;
import dev.starcore.starcore.core.module.ModuleMetadata;
import dev.starcore.starcore.core.module.StarCoreModule;
import dev.starcore.starcore.core.persistence.PersistenceService;
import dev.starcore.starcore.foundation.economy.EconomyService;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.foundation.territory.TerritoryService;
import dev.starcore.starcore.foundation.territory.model.ChunkCoordinate;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.territory.rent.command.TerritoryRentCommand;
import dev.starcore.starcore.module.territory.rent.listener.TerritoryRentListener;
import dev.starcore.starcore.module.territory.rent.model.LeaseContract;
import dev.starcore.starcore.module.territory.rent.model.LeasePayment;
import dev.starcore.starcore.module.territory.rent.model.LeaseProposal;
import dev.starcore.starcore.module.territory.rent.model.LeaseStats;
import dev.starcore.starcore.module.treasury.TreasuryService;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

import java.math.BigDecimal;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 领土租借服务实现
 */
public final class TerritoryRentModule implements StarCoreModule, TerritoryRentService {
    private static final String FILE_NAME = "lease_contracts.dat";
    private static final String PENDING_FILE = "pending_proposals.dat";

    private static final ModuleMetadata METADATA = new ModuleMetadata(
        "territory_rent",
        "领土租借系统",
        ModuleLayer.MODULE,
        List.of("nation", "treasury"),
        List.of(TerritoryRentService.class),
        "Provides territory lease and rental functionality between nations."
    );

    // Configuration
    private BigDecimal defaultRentPerChunk;
    private BigDecimal creationFee;
    private int proposalExpiryMinutes;
    private int maxLeaseDays;

    // Dependencies
    private org.bukkit.plugin.java.JavaPlugin plugin;
    private NationService nationService;
    private TreasuryService treasuryService;
    private TerritoryService territoryService;
    private EconomyService economyService;
    private MessageService messages;
    private DatabaseService databaseService;
    private PersistenceService persistenceService;

    // State
    private final Map<UUID, LeaseContract> contracts = new ConcurrentHashMap<>();
    private final Map<UUID, LeaseProposal> proposals = new ConcurrentHashMap<>();
    private final Map<String, UUID> chunkToContract = new ConcurrentHashMap<>();
    private final Map<UUID, List<LeasePayment>> paymentHistory = new ConcurrentHashMap<>();
    private final Map<UUID, LeaseStats> nationStats = new ConcurrentHashMap<>();

    private int dailyRentTaskId = -1;

    @Override
    public ModuleMetadata metadata() {
        return METADATA;
    }

    @Override
    public void enable(StarCoreContext context) {
        plugin = context.plugin();
        context.persistenceService().ensureNamespace(metadata().id());
        persistenceService = context.persistenceService();
        databaseService = context.databaseService();

        // Get required services
        nationService = context.serviceRegistry().require(NationService.class);
        treasuryService = context.serviceRegistry().require(TreasuryService.class);
        territoryService = context.serviceRegistry().require(TerritoryService.class);
        economyService = context.economyService();
        messages = context.serviceRegistry().require(MessageService.class);

        // Load configuration
        loadConfiguration(context.configuration());

        // Initialize database tables
        initializeDatabase();

        // Load existing contracts
        loadContracts();

        // Load pending proposals
        loadProposals();

        // Register command
        TerritoryRentCommand command = new TerritoryRentCommand(this, nationService, treasuryService, messages);
        var cmd = plugin.getCommand("rent");
        if (cmd != null) {
            cmd.setExecutor(command);
            cmd.setTabCompleter(command);
        }

        // Also register under sc rent
        // Note: sc command is handled by MainCommandHandler, not needed here

        // Register listener
        TerritoryRentListener listener = new TerritoryRentListener(this, nationService, territoryService, messages);
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);

        // Start daily rent collection task
        startDailyRentTask();

        plugin.getLogger().info("TerritoryRentModule enabled. Loaded " + contracts.size() + " contracts.");
    }

    @Override
    public void disable(StarCoreContext context) {
        // Cancel daily task
        if (dailyRentTaskId != -1) {
            plugin.getServer().getScheduler().cancelTask(dailyRentTaskId);
            dailyRentTaskId = -1;
        }

        // Save all contracts
        saveContracts();

        plugin.getLogger().info("TerritoryRentModule disabled.");
    }

    private void loadConfiguration(ConfigurationService config) {
        ConfigurationSection section = config.getConfig().getConfigurationSection("territory-rent");
        if (section == null) {
            section = new org.bukkit.configuration.MemoryConfiguration();
        }

        defaultRentPerChunk = BigDecimal.valueOf(section.getDouble("default-rent-per-chunk", 10.0));
        creationFee = BigDecimal.valueOf(section.getDouble("creation-fee", 50.0));
        proposalExpiryMinutes = section.getInt("proposalExpiryMinutes", 1440);
        maxLeaseDays = section.getInt("maxLeaseDays", 365);
    }

    private void initializeDatabase() {
        databaseService.dataSource().ifPresent(ds -> {
            try (Connection conn = ds.getConnection()) {
                // Create contracts table
                conn.createStatement().execute("""
                    CREATE TABLE IF NOT EXISTS starcore_lease_contracts (
                        contract_id VARCHAR(36) PRIMARY KEY,
                        lessor_nation_id VARCHAR(36) NOT NULL,
                        lessee_nation_id VARCHAR(36),
                        lessee_player_id VARCHAR(36),
                        start_time BIGINT NOT NULL,
                        end_time BIGINT NOT NULL,
                        status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
                        total_rent DECIMAL(20, 2) NOT NULL,
                        rent_per_day DECIMAL(20, 2) NOT NULL,
                        rent_per_chunk DECIMAL(20, 2) NOT NULL,
                        chunks_count INT NOT NULL,
                        world VARCHAR(64) NOT NULL,
                        chunk_coords TEXT NOT NULL,
                        creation_fee DECIMAL(20, 2) DEFAULT 0.0,
                        auto_renewal BOOLEAN DEFAULT FALSE,
                        renewal_count INT DEFAULT 0,
                        termination_reason VARCHAR(128),
                        terminated_by VARCHAR(36),
                        terminated_at BIGINT,
                        created_at BIGINT NOT NULL,
                        updated_at BIGINT NOT NULL
                    )
                """);

                // Create payments table
                conn.createStatement().execute("""
                    CREATE TABLE IF NOT EXISTS starcore_lease_payments (
                        payment_id VARCHAR(36) PRIMARY KEY,
                        contract_id VARCHAR(36) NOT NULL,
                        payer_id VARCHAR(36) NOT NULL,
                        payer_type VARCHAR(16) NOT NULL,
                        amount DECIMAL(20, 2) NOT NULL,
                        payment_type VARCHAR(32) NOT NULL,
                        payment_period_start BIGINT,
                        payment_period_end BIGINT,
                        payment_time BIGINT NOT NULL
                    )
                """);

                // Create chunk index
                conn.createStatement().execute("""
                    CREATE TABLE IF NOT EXISTS starcore_lease_chunk_index (
                        chunk_key VARCHAR(128) PRIMARY KEY,
                        contract_id VARCHAR(36) NOT NULL
                    )
                """);

                plugin.getLogger().info("TerritoryRentModule database tables initialized.");
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to initialize database tables: " + e.getMessage());
            }
        });
    }

    // ==================== Service Implementation ====================

    @Override
    public LeaseProposal createProposal(
        UUID proposerId,
        NationId lessorNationId,
        NationId lesseeNationId,
        UUID lesseePlayerId,
        List<ChunkCoordinate> chunks,
        String world,
        int durationDays,
        BigDecimal rentPerDay,
        BigDecimal rentPerChunk
    ) {
        // Validate chunks are owned by lessor
        for (ChunkCoordinate chunk : chunks) {
            var claim = territoryService.claimAt(chunk);
            if (claim.isEmpty() || !claim.get().ownerId().equals(lessorNationId.toString())) {
                throw new IllegalArgumentException("Chunk not owned by lessor nation");
            }
        }

        // Validate duration
        if (durationDays <= 0 || durationDays > maxLeaseDays) {
            throw new IllegalArgumentException("Invalid duration");
        }

        // Calculate fees
        BigDecimal totalRent = rentPerChunk
            .multiply(BigDecimal.valueOf(chunks.size()))
            .multiply(BigDecimal.valueOf(durationDays));
        BigDecimal dailyRent = totalRent.divide(BigDecimal.valueOf(durationDays), 2, java.math.RoundingMode.HALF_UP);

        long expiresAt = System.currentTimeMillis() + (proposalExpiryMinutes * 60 * 1000L);

        LeaseProposal proposal = new LeaseProposal(
            UUID.randomUUID(),
            proposerId,
            lessorNationId.value(),
            lesseeNationId != null ? lesseeNationId.value() : null,
            lesseePlayerId,
            world,
            chunks,
            durationDays,
            dailyRent,
            rentPerChunk,
            creationFee,
            expiresAt
        );

        proposals.put(proposal.proposalId(), proposal);
        saveProposals();

        return proposal;
    }

    @Override
    public LeaseContract acceptProposal(UUID proposalId, UUID accepterId) {
        LeaseProposal proposal = proposals.remove(proposalId);
        if (proposal == null) {
            throw new IllegalArgumentException("Proposal not found");
        }
        if (proposal.isExpired()) {
            throw new IllegalStateException("Proposal has expired");
        }

        // 审计 A-061: 校验 accepterId 为 lessor 或 lessee 任一方成员
        UUID lessorId = proposal.lessorNationId();
        boolean isLessorMember = nationService.nationOf(accepterId)
            .map(n -> n.id().value().equals(lessorId))
            .orElse(false);
        UUID lesseePlayerId = proposal.lesseePlayerId();
        boolean isLessee = lesseePlayerId != null && lesseePlayerId.equals(accepterId);
        UUID lesseeNationId = proposal.lesseeNationId();
        if (lesseeNationId != null) {
            isLessee = isLessee || nationService.nationOf(accepterId)
                .map(n -> n.id().value().equals(lesseeNationId))
                .orElse(false);
        }
        if (!isLessorMember && !isLessee) {
            throw new SecurityException("Only lessor or lessee can accept proposal");
        }

        // Create contract
        LeaseContract contract = createContract(proposal);

        // Charge creation fee
        UUID payerId = proposal.isNationProposal() ? proposal.lesseeNationId() : proposal.lesseePlayerId();
        if (payerId != null && creationFee.signum() > 0) {
            if (proposal.isNationProposal()) {
                // 审计 A-062: 校验 withdraw 返回值，失败时回滚
                if (!treasuryService.withdraw(new NationId(payerId), creationFee)) {
                    throw new IllegalStateException("Insufficient funds for creation fee");
                }
            } else {
                if (!economyService.withdraw(payerId, creationFee)) {
                    throw new IllegalStateException("Insufficient funds for creation fee");
                }
            }
        }

        // Store and save
        contracts.put(contract.contractId(), contract);
        updateChunkIndex(contract);
        saveContracts();
        saveProposals();

        // 新增契约时更新统计
        addNewContractStats(contract.lessorNationId(), contract.lesseeNationId(), contract.chunksCount());

        return contract;
    }

    @Override
    public void rejectProposal(UUID proposalId, UUID rejecterId) {
        // 审计 A-071: 校验 rejecterId 为 lessor 或 lessee 成员
        LeaseProposal proposal = proposals.get(proposalId);
        if (proposal != null) {
            UUID lessorId = proposal.lessorNationId();
            boolean isLessor = nationService.nationOf(rejecterId)
                .map(n -> n.id().value().equals(lessorId)).orElse(false);
            UUID lesseePlayerId = proposal.lesseePlayerId();
            UUID lesseeNationId = proposal.lesseeNationId();
            boolean isLessee = (lesseePlayerId != null && lesseePlayerId.equals(rejecterId))
                || (lesseeNationId != null && nationService.nationOf(rejecterId)
                    .map(n -> n.id().value().equals(lesseeNationId)).orElse(false));
            if (!isLessor && !isLessee) {
                throw new SecurityException("Only lessor or lessee can reject proposal");
            }
        }
        proposals.remove(proposalId);
        saveProposals();
    }

    @Override
    public LeaseContract createContract(LeaseProposal proposal) {
        Instant now = Instant.now();
        Instant endTime = now.plus(java.time.Duration.ofDays(proposal.durationDays()));

        return LeaseContract.create(
            proposal.lessorNationId(),
            proposal.isNationProposal() ? proposal.lesseeNationId() : null,
            proposal.lesseePlayerId(),
            now,
            endTime,
            proposal.calculateTotalRent(),
            proposal.rentPerDay(),
            proposal.rentPerChunk(),
            proposal.chunkCoords(),
            proposal.world(),
            proposal.creationFee()
        );
    }

    @Override
    public Optional<LeaseContract> getContract(UUID contractId) {
        return Optional.ofNullable(contracts.get(contractId));
    }

    @Override
    public Collection<LeaseContract> getNationContracts(NationId nationId) {
        UUID uuid = nationId.value();
        return contracts.values().stream()
            .filter(c -> c.lessorNationId().equals(uuid) ||
                        (c.lesseeNationId() != null && c.lesseeNationId().equals(uuid)))
            .collect(Collectors.toList());
    }

    @Override
    public Collection<LeaseContract> getActiveContracts(NationId nationId) {
        return getNationContracts(nationId).stream()
            .filter(LeaseContract::isActive)
            .collect(Collectors.toList());
    }

    @Override
    public Collection<LeaseContract> getContractsAsLessor(NationId nationId) {
        return contracts.values().stream()
            .filter(c -> c.lessorNationId().equals(nationId.value()))
            .collect(Collectors.toList());
    }

    @Override
    public Collection<LeaseContract> getContractsAsLessee(NationId nationId) {
        UUID uuid = nationId.value();
        return contracts.values().stream()
            .filter(c -> c.lesseeNationId() != null && c.lesseeNationId().equals(uuid))
            .collect(Collectors.toList());
    }

    @Override
    public Optional<LeaseContract> getContractForChunk(String world, int chunkX, int chunkZ) {
        String key = makeChunkKey(world, chunkX, chunkZ);
        UUID contractId = chunkToContract.get(key);
        if (contractId == null) {
            return Optional.empty();
        }
        return getContract(contractId).filter(LeaseContract::isActive);
    }

    @Override
    public boolean isChunkLeased(String world, int chunkX, int chunkZ) {
        return getContractForChunk(world, chunkX, chunkZ).isPresent();
    }

    @Override
    public boolean canNationAccessChunk(NationId nationId, String world, int chunkX, int chunkZ) {
        // Check if nation owns the chunk
        var claim = territoryService.claimAt(new ChunkCoordinate(world, chunkX, chunkZ));
        if (claim.isPresent() && claim.get().ownerId().equals(nationId.toString())) {
            return true;
        }

        // Check if nation has lease rights
        var lease = getContractForChunk(world, chunkX, chunkZ);
        if (lease.isEmpty() || !lease.get().isActive()) {
            return false;
        }

        LeaseContract contract = lease.get();
        return contract.lessorNationId().equals(nationId.value()) ||
               (contract.lesseeNationId() != null && contract.lesseeNationId().equals(nationId.value()));
    }

    @Override
    public LeaseContract terminateContract(UUID contractId, UUID terminatorId, String reason) {
        LeaseContract contract = contracts.get(contractId);
        if (contract == null) {
            throw new IllegalArgumentException("Contract not found");
        }

        // 审计 A-065: 校验 terminatorId 为 lessor 或 lessee 任一方成员
        UUID lessorId = contract.lessorNationId();
        boolean isLessor = nationService.nationOf(terminatorId)
            .map(n -> n.id().value().equals(lessorId)).orElse(false);
        UUID lesseeNationId = contract.lesseeNationId();
        UUID lesseePlayerId = contract.lesseePlayerId();
        boolean isLessee = (lesseeNationId != null && nationService.nationOf(terminatorId)
            .map(n -> n.id().value().equals(lesseeNationId)).orElse(false))
            || (lesseePlayerId != null && lesseePlayerId.equals(terminatorId));
        if (!isLessor && !isLessee) {
            throw new SecurityException("Only lessor or lessee can terminate contract");
        }

        LeaseContract terminated = contract.terminated(terminatorId, reason);
        contracts.put(contractId, terminated);
        updateChunkIndex(terminated);
        saveContracts();

        // 契约终止时减少活跃计数
        decrementActiveContractStats(terminated);

        return terminated;
    }

    @Override
    public LeaseContract renewContract(UUID contractId, UUID renewerId, int additionalDays) {
        LeaseContract contract = contracts.get(contractId);
        if (contract == null) {
            throw new IllegalArgumentException("Contract not found");
        }
        if (!contract.isActive()) {
            throw new IllegalStateException("Contract is not active");
        }

        // 审计 A-063: 校验 renewerId 为 lessor 或 lessee 成员
        UUID lessorId = contract.lessorNationId();
        boolean isLessor = nationService.nationOf(renewerId)
            .map(n -> n.id().value().equals(lessorId))
            .orElse(false);
        UUID lesseeNationId = contract.lesseeNationId();
        UUID lesseePlayerId = contract.lesseePlayerId();
        boolean isLessee = (lesseeNationId != null && nationService.nationOf(renewerId)
            .map(n -> n.id().value().equals(lesseeNationId)).orElse(false))
            || (lesseePlayerId != null && lesseePlayerId.equals(renewerId));
        if (!isLessor && !isLessee) {
            throw new SecurityException("Only lessor or lessee can renew contract");
        }

        BigDecimal additionalRent = contract.rentPerDay()
            .multiply(BigDecimal.valueOf(additionalDays));
        // 审计 A-064: 实际扣款并入账 lessor
        boolean paymentSuccess = false;
        if (contract.lesseeNationId() != null) {
            paymentSuccess = treasuryService.withdraw(new NationId(contract.lesseeNationId()), additionalRent);
        } else if (contract.lesseePlayerId() != null) {
            paymentSuccess = economyService.withdraw(contract.lesseePlayerId(), additionalRent);
        }
        if (!paymentSuccess) {
            throw new IllegalStateException("Rent payment failed");
        }
        // 入账 lessor
        treasuryService.deposit(new NationId(lessorId), additionalRent);

        BigDecimal newTotalRent = contract.totalRent().add(additionalRent);
        Instant newEndTime = contract.endTime().plus(java.time.Duration.ofDays(additionalDays));

        LeaseContract renewed = contract.renewed(newEndTime, newTotalRent);
        contracts.put(contractId, renewed);
        saveContracts();

        return renewed;
    }

    @Override
    public void processDailyRent() {
        Instant now = Instant.now();

        for (LeaseContract contract : contracts.values()) {
            if (!contract.isActive()) {
                continue;
            }

            // Check if rent is due
            if (shouldCollectRent(contract)) {
                try {
                    collectRent(contract);
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to collect rent for contract " + contract.contractId() + ": " + e.getMessage());
                }
            }
        }

        // Expire contracts
        expireContracts();
    }

    @Override
    public void expireContracts() {
        for (Map.Entry<UUID, LeaseContract> entry : contracts.entrySet()) {
            LeaseContract contract = entry.getValue();
            if (contract.status() != dev.starcore.starcore.module.territory.rent.model.LeaseStatus.EXPIRED &&
                contract.isExpired()) {

                LeaseContract expired = contract.withStatus(dev.starcore.starcore.module.territory.rent.model.LeaseStatus.EXPIRED);
                contracts.put(entry.getKey(), expired);
                updateChunkIndex(expired);
                // 契约过期时减少活跃计数
                decrementActiveContractStats(expired);
            }
        }
        saveContracts();
    }

    private boolean shouldCollectRent(LeaseContract contract) {
        // Simplified: collect rent daily based on last update
        long dayInSeconds = 86400;
        long secondsSinceUpdate = Instant.now().getEpochSecond() - contract.updatedAt().getEpochSecond();
        return secondsSinceUpdate >= dayInSeconds;
    }

    @Override
    public LeasePayment collectRent(LeaseContract contract) {
        if (!contract.isActive()) {
            throw new IllegalStateException("Contract is not active");
        }

        // Determine payer
        UUID payerId;
        dev.starcore.starcore.module.territory.rent.model.PaymentPayerType payerType;

        if (contract.lesseeNationId() != null) {
            payerId = contract.lesseeNationId();
            payerType = dev.starcore.starcore.module.territory.rent.model.PaymentPayerType.NATION;
        } else if (contract.lesseePlayerId() != null) {
            payerId = contract.lesseePlayerId();
            payerType = dev.starcore.starcore.module.territory.rent.model.PaymentPayerType.PLAYER;
        } else {
            throw new IllegalStateException("No payer defined");
        }

        // Withdraw rent
        boolean success;
        if (payerType == dev.starcore.starcore.module.territory.rent.model.PaymentPayerType.NATION) {
            success = treasuryService.withdraw(new NationId(payerId), contract.rentPerDay());
        } else {
            success = economyService.withdraw(payerId, contract.rentPerDay());
        }

        if (!success) {
            // 审计 A-067: 扣款失败直接终止合同，不抛异常也不双重处理
            terminateContract(contract.contractId(), payerId, "租金支付失败");
            return null;
        }

        // 审计 A-066: 入账前校验 lessor nation 是否存在
        UUID lessorId = contract.lessorNationId();
        if (!nationService.nationById(new NationId(lessorId)).isPresent()) {
            // lessor 已解散：款项作废并记录告警，不抛出给上层
            plugin.getLogger().warning("collectRent: lessor nation " + lessorId
                + " for contract " + contract.contractId() + " no longer exists. Rent of "
                + contract.rentPerDay() + " is lost.");
        } else {
            treasuryService.deposit(new NationId(lessorId), contract.rentPerDay());
        }

        // Record payment
        // 审计 A-069: 用当前时间作为周期开始，上一个周期结束作为周期开始
        Instant now = Instant.now();
        Instant periodStart = contract.updatedAt().isAfter(now.minus(java.time.Duration.ofDays(1)))
            ? contract.updatedAt() : now.minus(java.time.Duration.ofDays(1));
        LeasePayment payment = dev.starcore.starcore.module.territory.rent.model.LeasePayment.create(
            contract.contractId(),
            payerId,
            payerType,
            contract.rentPerDay(),
            dev.starcore.starcore.module.territory.rent.model.PaymentType.DAILY,
            periodStart,
            now
        );

        paymentHistory.computeIfAbsent(contract.contractId(), k -> new ArrayList<>()).add(payment);
        savePaymentToDb(payment);

        // 审计 A-068: 收租后更新 contract.updatedAt，避免重复扣款
        LeaseContract updatedContract = contract.withUpdatedAt(now);
        contracts.put(contract.contractId(), updatedContract);
        saveContracts();

        // 收租时仅累加收支金额
        accrueStats(contract.lessorNationId(), contract.lesseeNationId(), contract.rentPerDay());

        return payment;
    }

    private void accrueStats(UUID lessorId, UUID lesseeId, BigDecimal amount) {
        // 收租时仅累加收支金额，不改变契约计数
        nationStats.compute(lessorId, (k, v) -> {
            if (v == null) v = new LeaseStats(0, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0);
            return new LeaseStats(
                v.totalContracts(),
                v.activeContractsAsLessor(),
                v.activeContractsAsLessee(),
                v.totalRentEarned().add(amount),
                v.totalRentPaid(),
                v.chunksLeasedOut(),
                v.chunksLeasedIn()
            );
        });

        if (lesseeId != null) {
            nationStats.compute(lesseeId, (k, v) -> {
                if (v == null) v = new LeaseStats(0, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0);
                return new LeaseStats(
                    v.totalContracts(),
                    v.activeContractsAsLessor(),
                    v.activeContractsAsLessee(),
                    v.totalRentEarned(),
                    v.totalRentPaid().add(amount),
                    v.chunksLeasedOut(),
                    v.chunksLeasedIn()
                );
            });
        }
    }

    /**
     * 契约终止/过期时减少活跃契约计数
     */
    private void decrementActiveContractStats(LeaseContract contract) {
        UUID lessorId = contract.lessorNationId();
        UUID lesseeId = contract.lesseeNationId();
        int chunksCount = contract.chunksCount();

        // 减少出租方活跃计数
        nationStats.compute(lessorId, (k, v) -> {
            if (v == null) v = new LeaseStats(0, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0);
            return new LeaseStats(
                v.totalContracts(),
                Math.max(0, v.activeContractsAsLessor() - 1),
                v.activeContractsAsLessee(),
                v.totalRentEarned(),
                v.totalRentPaid(),
                Math.max(0, v.chunksLeasedOut() - chunksCount),
                v.chunksLeasedIn()
            );
        });

        // 减少承租方活跃计数
        if (lesseeId != null) {
            nationStats.compute(lesseeId, (k, v) -> {
                if (v == null) v = new LeaseStats(0, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0);
                return new LeaseStats(
                    v.totalContracts(),
                    v.activeContractsAsLessor(),
                    Math.max(0, v.activeContractsAsLessee() - 1),
                    v.totalRentEarned(),
                    v.totalRentPaid(),
                    v.chunksLeasedOut(),
                    Math.max(0, v.chunksLeasedIn() - chunksCount)
                );
            });
        }
    }

    /**
     * 新增契约时更新统计（增加契约数和区块数）
     */
    private void addNewContractStats(UUID lessorId, UUID lesseeId, int chunksCount) {
        nationStats.compute(lessorId, (k, v) -> {
            if (v == null) v = new LeaseStats(0, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0);
            return new LeaseStats(
                v.totalContracts() + 1,
                v.activeContractsAsLessor() + 1,
                v.activeContractsAsLessee(),
                v.totalRentEarned(),
                v.totalRentPaid(),
                v.chunksLeasedOut() + chunksCount,
                v.chunksLeasedIn()
            );
        });

        if (lesseeId != null) {
            nationStats.compute(lesseeId, (k, v) -> {
                if (v == null) v = new LeaseStats(0, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0);
                return new LeaseStats(
                    v.totalContracts() + 1,
                    v.activeContractsAsLessor(),
                    v.activeContractsAsLessee() + 1,
                    v.totalRentEarned(),
                    v.totalRentPaid(),
                    v.chunksLeasedOut(),
                    v.chunksLeasedIn() + chunksCount
                );
            });
        }
    }

    @Override
    public List<LeasePayment> getPaymentHistory(UUID contractId) {
        return List.copyOf(paymentHistory.getOrDefault(contractId, List.of()));
    }

    @Override
    public BigDecimal getTotalRentEarned(NationId nationId) {
        return nationStats.getOrDefault(nationId.value(), new LeaseStats(0, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0)).totalRentEarned();
    }

    @Override
    public BigDecimal getTotalRentPaid(NationId nationId) {
        return nationStats.getOrDefault(nationId.value(), new LeaseStats(0, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0)).totalRentPaid();
    }

    @Override
    public BigDecimal getDefaultRentPerChunk() {
        return defaultRentPerChunk;
    }

    @Override
    public BigDecimal getCreationFee() {
        return creationFee;
    }

    @Override
    public LeaseStats getStats(NationId nationId) {
        return nationStats.getOrDefault(nationId.value(),
            new LeaseStats(0, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0));
    }

    @Override
    public String getSummary() {
        long activeCount = contracts.values().stream().filter(LeaseContract::isActive).count();
        return activeCount + " active leases, " + proposals.size() + " pending proposals";
    }

    @Override
    public void reload() {
        // Reload configuration
        ConfigurationService config = plugin.getServer().getServicesManager()
            .load(ConfigurationService.class);
        if (config != null) {
            loadConfiguration(config);
        }
    }

    @Override
    public Collection<LeaseProposal> getPendingProposals(NationId nationId) {
        UUID uuid = nationId.value();
        return proposals.values().stream()
            .filter(p -> !p.isExpired())
            .filter(p -> p.lessorNationId().equals(uuid) ||
                        (p.lesseeNationId() != null && p.lesseeNationId().equals(uuid)))
            .collect(Collectors.toList());
    }

    @Override
    public Optional<LeaseProposal> getProposal(UUID proposalId) {
        return Optional.ofNullable(proposals.get(proposalId));
    }

    // ==================== Helpers ====================

    private String makeChunkKey(String world, int x, int z) {
        return world + ":" + x + ":" + z;
    }

    private void updateChunkIndex(LeaseContract contract) {
        // Remove old entries
        chunkToContract.entrySet().removeIf(e -> e.getValue().equals(contract.contractId()));

        // Add new entries if active
        if (contract.isActive()) {
            for (ChunkCoordinate chunk : contract.chunkCoords()) {
                String key = makeChunkKey(chunk.world(), chunk.x(), chunk.z());
                chunkToContract.put(key, contract.contractId());
            }
        }

        // Save index
        saveChunkIndex();
    }

    private void saveChunkIndex() {
        try {
            var props = new Properties();
            for (Map.Entry<String, UUID> entry : chunkToContract.entrySet()) {
                props.setProperty(entry.getKey(), entry.getValue().toString());
            }
            persistenceService.savePropertiesAsync(metadata().id(), "chunk_index.dat", props);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to save chunk index: " + e.getMessage());
        }
    }

    private void loadChunkIndex() {
        try {
            var props = persistenceService.loadProperties(metadata().id(), "chunk_index.dat");
            for (String key : props.stringPropertyNames()) {
                chunkToContract.put(key, UUID.fromString(props.getProperty(key)));
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load chunk index: " + e.getMessage());
        }
    }

    private void saveContracts() {
        try {
            var props = new Properties();
            for (LeaseContract contract : contracts.values()) {
                props.setProperty(contract.contractId().toString(), serializeContract(contract));
            }
            persistenceService.savePropertiesAsync(metadata().id(), FILE_NAME, props);

            // Also save to database
            saveContractsToDb();
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to save contracts: " + e.getMessage());
        }
    }

    private void loadContracts() {
        try {
            var props = persistenceService.loadProperties(metadata().id(), FILE_NAME);
            for (String key : props.stringPropertyNames()) {
                LeaseContract contract = deserializeContract(props.getProperty(key));
                if (contract != null) {
                    contracts.put(contract.contractId(), contract);
                    updateChunkIndex(contract);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load contracts: " + e.getMessage());
        }

        loadChunkIndex();

        // 从数据库加载统计数据
        loadNationStatsFromDb();
    }

    /**
     * 从数据库批量加载国家统计数据
     */
    private void loadNationStatsFromDb() {
        databaseService.dataSource().ifPresent(ds -> {
            try (Connection conn = ds.getConnection()) {
                // 统计每个国家的租借数据
                String sql = """
                    SELECT
                        lessor_nation_id,
                        COUNT(*) as total_contracts,
                        SUM(CASE WHEN status = 'ACTIVE' THEN 1 ELSE 0 END) as active_as_lessor,
                        SUM(chunks_count) as chunks_leased_out,
                        SUM(
                            (SELECT COALESCE(SUM(amount), 0)
                             FROM starcore_lease_payments p
                             WHERE p.contract_id = starcore_lease_contracts.contract_id
                               AND p.payer_type = 'NATION')
                        ) as total_rent_earned
                    FROM starcore_lease_contracts
                    WHERE lessor_nation_id IS NOT NULL
                    GROUP BY lessor_nation_id
                """;

                try (PreparedStatement stmt = conn.prepareStatement(sql);
                     ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        UUID nationId = UUID.fromString(rs.getString("lessor_nation_id"));
                        int totalContracts = rs.getInt("total_contracts");
                        int activeAsLessor = rs.getInt("active_as_lessor");
                        int chunksLeasedOut = rs.getInt("chunks_leased_out");
                        BigDecimal totalRentEarned = rs.getBigDecimal("total_rent_earned");

                        nationStats.compute(nationId, (k, existing) -> {
                            LeaseStats current = existing != null ? existing : new LeaseStats(0, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0);
                            return new LeaseStats(
                                totalContracts,
                                activeAsLessor,
                                current.activeContractsAsLessee(),
                                totalRentEarned != null ? totalRentEarned : BigDecimal.ZERO,
                                current.totalRentPaid(),
                                chunksLeasedOut,
                                current.chunksLeasedIn()
                            );
                        });
                    }
                }

                // 统计作为承租方的数据
                String lesseeSql = """
                    SELECT
                        lessee_nation_id,
                        COUNT(*) as total_contracts,
                        SUM(CASE WHEN status = 'ACTIVE' THEN 1 ELSE 0 END) as active_as_lessee,
                        SUM(chunks_count) as chunks_leased_in,
                        SUM(
                            (SELECT COALESCE(SUM(amount), 0)
                             FROM starcore_lease_payments p
                             WHERE p.contract_id = starcore_lease_contracts.contract_id
                               AND p.payer_type = 'NATION')
                        ) as total_rent_paid
                    FROM starcore_lease_contracts
                    WHERE lessee_nation_id IS NOT NULL AND status = 'ACTIVE'
                    GROUP BY lessee_nation_id
                """;

                try (PreparedStatement stmt = conn.prepareStatement(lesseeSql);
                     ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        UUID nationId = UUID.fromString(rs.getString("lessee_nation_id"));
                        int totalContracts = rs.getInt("total_contracts");
                        int activeAsLessee = rs.getInt("active_as_lessee");
                        int chunksLeasedIn = rs.getInt("chunks_leased_in");
                        BigDecimal totalRentPaid = rs.getBigDecimal("total_rent_paid");

                        nationStats.compute(nationId, (k, existing) -> {
                            LeaseStats current = existing != null ? existing : new LeaseStats(0, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0);
                            return new LeaseStats(
                                current.totalContracts() + totalContracts,
                                current.activeContractsAsLessor(),
                                activeAsLessee,
                                current.totalRentEarned(),
                                totalRentPaid != null ? totalRentPaid : BigDecimal.ZERO,
                                current.chunksLeasedOut(),
                                chunksLeasedIn
                            );
                        });
                    }
                }

                plugin.getLogger().info("Loaded nation rent stats from database.");
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to load nation stats from database: " + e.getMessage());
            }
        });
    }

    /**
     * 重新计算指定国家的统计数据
     */
    public void recalculateStats(NationId nationId) {
        UUID uuid = nationId.value();

        // 重新从数据库计算
        databaseService.dataSource().ifPresent(ds -> {
            try (Connection conn = ds.getConnection()) {
                // 计算作为出租方的统计
                String lessorSql = """
                    SELECT
                        COUNT(*) as total_contracts,
                        SUM(CASE WHEN status = 'ACTIVE' THEN 1 ELSE 0 END) as active_as_lessor,
                        SUM(chunks_count) as chunks_leased_out,
                        SUM(
                            (SELECT COALESCE(SUM(amount), 0)
                             FROM starcore_lease_payments p
                             WHERE p.contract_id = starcore_lease_contracts.contract_id)
                        ) as total_rent_earned
                    FROM starcore_lease_contracts
                    WHERE lessor_nation_id = ?
                """;

                try (PreparedStatement stmt = conn.prepareStatement(lessorSql)) {
                    stmt.setString(1, uuid.toString());
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            int totalContracts = rs.getInt("total_contracts");
                            int activeAsLessor = rs.getInt("active_as_lessor");
                            int chunksLeasedOut = rs.getInt("chunks_leased_out");
                            BigDecimal totalRentEarned = rs.getBigDecimal("total_rent_earned");

                            nationStats.compute(uuid, (k, existing) -> {
                                LeaseStats current = existing != null ? existing : new LeaseStats(0, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0);
                                return new LeaseStats(
                                    totalContracts,
                                    activeAsLessor,
                                    current.activeContractsAsLessee(),
                                    totalRentEarned != null ? totalRentEarned : BigDecimal.ZERO,
                                    current.totalRentPaid(),
                                    chunksLeasedOut,
                                    current.chunksLeasedIn()
                                );
                            });
                        }
                    }
                }

                // 计算作为承租方的统计
                String lesseeSql = """
                    SELECT
                        COUNT(*) as total_contracts,
                        SUM(CASE WHEN status = 'ACTIVE' THEN 1 ELSE 0 END) as active_as_lessee,
                        SUM(chunks_count) as chunks_leased_in,
                        SUM(
                            (SELECT COALESCE(SUM(amount), 0)
                             FROM starcore_lease_payments p
                             WHERE p.contract_id = starcore_lease_contracts.contract_id)
                        ) as total_rent_paid
                    FROM starcore_lease_contracts
                    WHERE lessee_nation_id = ?
                """;

                try (PreparedStatement stmt = conn.prepareStatement(lesseeSql)) {
                    stmt.setString(1, uuid.toString());
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            int activeAsLessee = rs.getInt("active_as_lessee");
                            int chunksLeasedIn = rs.getInt("chunks_leased_in");
                            BigDecimal totalRentPaid = rs.getBigDecimal("total_rent_paid");

                            nationStats.compute(uuid, (k, existing) -> {
                                LeaseStats current = existing != null ? existing : new LeaseStats(0, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0);
                                return new LeaseStats(
                                    current.totalContracts(),
                                    current.activeContractsAsLessor(),
                                    activeAsLessee,
                                    current.totalRentEarned(),
                                    totalRentPaid != null ? totalRentPaid : BigDecimal.ZERO,
                                    current.chunksLeasedOut(),
                                    chunksLeasedIn
                                );
                            });
                        }
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to recalculate stats for nation " + uuid + ": " + e.getMessage());
            }
        });
    }

    private void saveProposals() {
        try {
            var props = new Properties();
            for (LeaseProposal proposal : proposals.values()) {
                props.setProperty(proposal.proposalId().toString(), serializeProposal(proposal));
            }
            persistenceService.savePropertiesAsync(metadata().id(), PENDING_FILE, props);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to save proposals: " + e.getMessage());
        }
    }

    private void loadProposals() {
        try {
            var props = persistenceService.loadProperties(metadata().id(), PENDING_FILE);
            for (String key : props.stringPropertyNames()) {
                LeaseProposal proposal = deserializeProposal(props.getProperty(key));
                if (proposal != null && !proposal.isExpired()) {
                    proposals.put(proposal.proposalId(), proposal);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load proposals: " + e.getMessage());
        }
    }

    private void saveContractsToDb() {
        databaseService.dataSource().ifPresent(ds -> {
            try (Connection conn = ds.getConnection()) {
                conn.setAutoCommit(false);

                for (LeaseContract contract : contracts.values()) {
                    saveContractToDb(conn, contract);
                }

                conn.commit();
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to save contracts to database: " + e.getMessage());
            }
        });
    }

    private void saveContractToDb(Connection conn, LeaseContract contract) throws SQLException {
        String sql = """
            INSERT OR REPLACE INTO starcore_lease_contracts
            (contract_id, lessor_nation_id, lessee_nation_id, lessee_player_id, start_time, end_time,
             status, total_rent, rent_per_day, rent_per_chunk, chunks_count, world, chunk_coords,
             creation_fee, auto_renewal, renewal_count, termination_reason, terminated_by,
             terminated_at, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, contract.contractId().toString());
            stmt.setString(2, contract.lessorNationId().toString());
            stmt.setString(3, contract.lesseeNationId() != null ? contract.lesseeNationId().toString() : null);
            stmt.setString(4, contract.lesseePlayerId() != null ? contract.lesseePlayerId().toString() : null);
            stmt.setLong(5, contract.startTime().getEpochSecond());
            stmt.setLong(6, contract.endTime().getEpochSecond());
            stmt.setString(7, contract.status().name());
            stmt.setString(8, contract.totalRent().toPlainString());
            stmt.setString(9, contract.rentPerDay().toPlainString());
            stmt.setString(10, contract.rentPerChunk().toPlainString());
            stmt.setInt(11, contract.chunksCount());
            stmt.setString(12, contract.world());
            stmt.setString(13, serializeChunkCoords(contract.chunkCoords()));
            stmt.setString(14, contract.creationFee().toPlainString());
            stmt.setBoolean(15, contract.autoRenewal());
            stmt.setInt(16, contract.renewalCount());
            stmt.setString(17, contract.terminationReason());
            stmt.setString(18, contract.terminatedBy() != null ? contract.terminatedBy().toString() : null);
            stmt.setLong(19, contract.terminatedAt() != null ? contract.terminatedAt().getEpochSecond() : 0);
            stmt.setLong(20, contract.createdAt().getEpochSecond());
            stmt.setLong(21, contract.updatedAt().getEpochSecond());

            stmt.executeUpdate();
        }
    }

    private void savePaymentToDb(LeasePayment payment) {
        databaseService.dataSource().ifPresent(ds -> {
            try (Connection conn = ds.getConnection()) {
                String sql = """
                    INSERT INTO starcore_lease_payments
                    (payment_id, contract_id, payer_id, payer_type, amount, payment_type,
                     payment_period_start, payment_period_end, payment_time)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, payment.paymentId().toString());
                    stmt.setString(2, payment.contractId().toString());
                    stmt.setString(3, payment.payerId().toString());
                    stmt.setString(4, payment.payerType().name());
                    stmt.setString(5, payment.amount().toPlainString());
                    stmt.setString(6, payment.paymentType().name());
                    stmt.setLong(7, payment.paymentPeriodStart().getEpochSecond());
                    stmt.setLong(8, payment.paymentPeriodEnd().getEpochSecond());
                    stmt.setLong(9, payment.paymentTime().getEpochSecond());

                    stmt.executeUpdate();
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to save payment: " + e.getMessage());
            }
        });
    }

    private String serializeContract(LeaseContract contract) {
        return String.join("|",
            contract.contractId().toString(),
            contract.lessorNationId().toString(),
            contract.lesseeNationId() != null ? contract.lesseeNationId().toString() : "",
            contract.lesseePlayerId() != null ? contract.lesseePlayerId().toString() : "",
            String.valueOf(contract.startTime().getEpochSecond()),
            String.valueOf(contract.endTime().getEpochSecond()),
            contract.status().name(),
            contract.totalRent().toPlainString(),
            contract.rentPerDay().toPlainString(),
            contract.rentPerChunk().toPlainString(),
            String.valueOf(contract.chunksCount()),
            contract.world(),
            serializeChunkCoords(contract.chunkCoords()),
            contract.creationFee().toPlainString(),
            String.valueOf(contract.autoRenewal()),
            String.valueOf(contract.renewalCount()),
            contract.terminationReason() != null ? contract.terminationReason() : "",
            contract.terminatedBy() != null ? contract.terminatedBy().toString() : "",
            String.valueOf(contract.terminatedAt() != null ? contract.terminatedAt().getEpochSecond() : 0),
            String.valueOf(contract.createdAt().getEpochSecond()),
            String.valueOf(contract.updatedAt().getEpochSecond())
        );
    }

    private LeaseContract deserializeContract(String data) {
        try {
            String[] parts = data.split("\\|");
            if (parts.length < 21) return null;

            int idx = 0;
            UUID contractId = UUID.fromString(parts[idx++]);
            UUID lessorNationId = UUID.fromString(parts[idx++]);
            UUID lesseeNationId = parts[idx].isEmpty() ? null : UUID.fromString(parts[idx++]);
            if (!parts[idx].isEmpty() && parts[idx].length() > 10) {
                // lessee player id
                idx++;
            } else {
                idx++;
            }
            UUID lesseePlayerId = parts[idx].isEmpty() ? null : UUID.fromString(parts[idx++]);
            Instant startTime = Instant.ofEpochSecond(Long.parseLong(parts[idx++]));
            Instant endTime = Instant.ofEpochSecond(Long.parseLong(parts[idx++]));
            dev.starcore.starcore.module.territory.rent.model.LeaseStatus status =
                dev.starcore.starcore.module.territory.rent.model.LeaseStatus.valueOf(parts[idx++]);
            BigDecimal totalRent = new BigDecimal(parts[idx++]);
            BigDecimal rentPerDay = new BigDecimal(parts[idx++]);
            BigDecimal rentPerChunk = new BigDecimal(parts[idx++]);
            int chunksCount = Integer.parseInt(parts[idx++]);
            String world = parts[idx++];
            List<ChunkCoordinate> chunkCoords = deserializeChunkCoords(parts[idx++]);
            BigDecimal creationFee = new BigDecimal(parts[idx++]);
            boolean autoRenewal = Boolean.parseBoolean(parts[idx++]);
            int renewalCount = Integer.parseInt(parts[idx++]);
            String terminationReason = parts[idx].isEmpty() ? null : parts[idx++];
            UUID terminatedBy = parts[idx].isEmpty() ? null : UUID.fromString(parts[idx++]);
            Instant terminatedAt = parts[idx].isEmpty() ? null : Instant.ofEpochSecond(Long.parseLong(parts[idx++]));
            Instant createdAt = Instant.ofEpochSecond(Long.parseLong(parts[idx++]));
            Instant updatedAt = Instant.ofEpochSecond(Long.parseLong(parts[idx]));

            return new LeaseContract(
                contractId, lessorNationId, lesseeNationId, lesseePlayerId, startTime, endTime,
                status, totalRent, rentPerDay, rentPerChunk, chunksCount, world, chunkCoords,
                creationFee, autoRenewal, renewalCount, terminationReason, terminatedBy,
                terminatedAt, createdAt, updatedAt
            );
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to deserialize contract: " + e.getMessage());
            return null;
        }
    }

    private String serializeProposal(LeaseProposal proposal) {
        return String.join("|",
            proposal.proposalId().toString(),
            proposal.proposerId().toString(),
            proposal.lessorNationId().toString(),
            proposal.lesseeNationId() != null ? proposal.lesseeNationId().toString() : "",
            proposal.lesseePlayerId() != null ? proposal.lesseePlayerId().toString() : "",
            proposal.world(),
            serializeChunkCoords(proposal.chunkCoords()),
            String.valueOf(proposal.durationDays()),
            proposal.rentPerDay().toPlainString(),
            proposal.rentPerChunk().toPlainString(),
            proposal.creationFee().toPlainString(),
            String.valueOf(proposal.expiresAt())
        );
    }

    private LeaseProposal deserializeProposal(String data) {
        try {
            String[] parts = data.split("\\|");
            if (parts.length < 12) return null;

            int idx = 0;
            UUID proposalId = UUID.fromString(parts[idx++]);
            UUID proposerId = UUID.fromString(parts[idx++]);
            UUID lessorNationId = UUID.fromString(parts[idx++]);
            UUID lesseeNationId = parts[idx].isEmpty() ? null : UUID.fromString(parts[idx++]);
            UUID lesseePlayerId = parts[idx].isEmpty() ? null : UUID.fromString(parts[idx++]);
            String world = parts[idx++];
            List<ChunkCoordinate> chunkCoords = deserializeChunkCoords(parts[idx++]);
            int durationDays = Integer.parseInt(parts[idx++]);
            BigDecimal rentPerDay = new BigDecimal(parts[idx++]);
            BigDecimal rentPerChunk = new BigDecimal(parts[idx++]);
            BigDecimal creationFee = new BigDecimal(parts[idx++]);
            long expiresAt = Long.parseLong(parts[idx]);

            return new LeaseProposal(
                proposalId, proposerId, lessorNationId, lesseeNationId, lesseePlayerId,
                world, chunkCoords, durationDays, rentPerDay, rentPerChunk, creationFee, expiresAt
            );
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to deserialize proposal: " + e.getMessage());
            return null;
        }
    }

    private String serializeChunkCoords(List<ChunkCoordinate> coords) {
        return coords.stream()
            .map(c -> c.world() + "," + c.x() + "," + c.z())
            .collect(Collectors.joining(";"));
    }

    private List<ChunkCoordinate> deserializeChunkCoords(String data) {
        if (data == null || data.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(data.split(";"))
            .map(s -> {
                String[] parts = s.split(",");
                return new ChunkCoordinate(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
            })
            .collect(Collectors.toList());
    }

    private void startDailyRentTask() {
        dailyRentTaskId = plugin.getServer().getScheduler().runTaskTimer(
            plugin,
            this::processDailyRent,
            20L * 60 * 60, // 1 hour delay
            20L * 60 * 60   // Every hour (will check within the method)
        ).getTaskId();
        plugin.getLogger().info("TerritoryRentModule daily rent task started.");
    }

    // ==================== Package-private Accessors ====================

    public Plugin getPlugin() {
        return plugin;
    }

    MessageService getMessages() {
        return messages;
    }
}
