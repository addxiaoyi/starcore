package dev.starcore.starcore.module.lease;
import java.util.Optional;

import com.zaxxer.hikari.HikariDataSource;
import dev.starcore.starcore.core.database.DatabaseService;
import dev.starcore.starcore.foundation.economy.EconomyService;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.lease.event.*;
import dev.starcore.starcore.module.lease.model.LeaseContract;
import dev.starcore.starcore.module.lease.model.LeaseStatus;
import dev.starcore.starcore.module.lease.model.LeaseType;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.treasury.TreasuryService;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * 租约契约服务实现
 */
public final class LeaseServiceImpl implements LeaseService {

    private final Plugin plugin;
    private final DataSource dataSource;
    private final EconomyService economyService;
    private final TreasuryService treasuryService;
    private final MessageService messages;
    private final Logger logger;
    private final Map<UUID, LeaseContract> leaseCache = new ConcurrentHashMap<>();

    public LeaseServiceImpl(Plugin plugin, DatabaseService databaseService, EconomyService economyService,
                           TreasuryService treasuryService, MessageService messages) {
        this.plugin = plugin;
        this.dataSource = databaseService.dataSource().orElse(null);
        this.economyService = economyService;
        this.treasuryService = treasuryService;
        this.messages = messages;
        this.logger = plugin.getLogger();
    }

    @Override
    public void initializeTables() {
        if (dataSource == null) {
            logger.warning("Database not available, lease tables not initialized");
            return;
        }

        String sql = """
            CREATE TABLE IF NOT EXISTS lease_contracts (
                id VARCHAR(36) PRIMARY KEY,
                lessor_nation_id VARCHAR(36),
                lessor_player_id VARCHAR(36) NOT NULL,
                tenant_nation_id VARCHAR(36),
                tenant_player_id VARCHAR(36),
                type VARCHAR(32) NOT NULL,
                region_id VARCHAR(255) NOT NULL,
                monthly_rent DECIMAL(19,2) NOT NULL,
                total_value DECIMAL(19,2) NOT NULL,
                status VARCHAR(32) NOT NULL,
                created_at TIMESTAMP NOT NULL,
                signed_at TIMESTAMP,
                start_date TIMESTAMP,
                end_date TIMESTAMP,
                next_payment_due TIMESTAMP,
                last_payment_at TIMESTAMP,
                lessor_signed BOOLEAN DEFAULT FALSE,
                tenant_signed BOOLEAN DEFAULT FALSE,
                overdue_days INT DEFAULT 0,
                termination_reason TEXT,
                INDEX idx_lessor_nation (lessor_nation_id),
                INDEX idx_tenant_nation (tenant_nation_id),
                INDEX idx_region (region_id),
                INDEX idx_status (status),
                INDEX idx_end_date (end_date)
            )
            """;

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            logger.info("Lease contracts table initialized");
        } catch (SQLException e) {
            logger.severe("Failed to initialize lease contracts table: " + e.getMessage());
        }
    }

    @Override
    public LeaseContract createLease(
        NationId lessorNationId,
        UUID lessorPlayerId,
        NationId tenantNationId,
        UUID tenantPlayerId,
        LeaseType type,
        String regionId,
        BigDecimal monthlyRent,
        int durationDays
    ) {
        LeaseContract contract = LeaseContract.createDraft(
            lessorNationId,
            lessorPlayerId,
            tenantNationId,
            tenantPlayerId,
            type,
            regionId,
            monthlyRent,
            durationDays
        );

        // 保存到数据库
        saveLease(contract);

        // 放入缓存
        leaseCache.put(contract.id(), contract);

        // 触发事件
        LeaseCreatedEvent event = new LeaseCreatedEvent(contract, lessorPlayerId);
        Bukkit.getPluginManager().callEvent(event);

        return contract;
    }

    @Override
    public boolean signLease(UUID contractId, UUID signerId) {
        LeaseContract contract = getLease(contractId).orElse(null);
        if (contract == null) {
            return false;
        }

        // 确定是出租方还是承租方
        boolean isLessor = signerId.equals(contract.lessorPlayerId());

        // 触发签署前事件
        LeaseSignedEvent signEvent = new LeaseSignedEvent(contract, signerId, isLessor);
        Bukkit.getPluginManager().callEvent(signEvent);
        if (signEvent.isCancelled()) {
            return false;
        }

        boolean success = contract.sign(signerId);
        if (success) {
            updateLease(contract);

            // 如果完全签署，激活租约
            if (contract.isFullySigned() && contract.status() == LeaseStatus.ACTIVE) {
                Instant now = Instant.now();
                // 注意：租约时长由 totalValue/monthlyRent 计算，而非原始 durationDays
                // 因为 LeaseContract 不存储 durationDays 字段，所以激活时用估算值
                Instant end = now.plusSeconds((long) getDurationDaysFromContract(contract) * 24 * 60 * 60);
                contract.activate(now, end);

                LeaseActivatedEvent activateEvent = new LeaseActivatedEvent(contract);
                Bukkit.getPluginManager().callEvent(activateEvent);
            }
        }

        return success;
    }

    private int getDurationDaysFromContract(LeaseContract contract) {
        if (contract.totalValue() == null || contract.monthlyRent() == null) {
            return 30; // 默认30天
        }
        if (contract.monthlyRent().compareTo(BigDecimal.ZERO) == 0) {
            return 30;
        }
        // 使用 double 计算会有精度损失，但在租约场景下可接受
        double months = contract.totalValue().doubleValue() / contract.monthlyRent().doubleValue();
        return (int) Math.max(1, months * 30);
    }

    @Override
    public boolean rejectLease(UUID contractId, UUID rejecterId) {
        LeaseContract contract = getLease(contractId).orElse(null);
        if (contract == null || contract.status() != LeaseStatus.PENDING) {
            return false;
        }

        // 只有相关方可以拒绝
        if (!rejecterId.equals(contract.lessorPlayerId()) &&
            !rejecterId.equals(contract.tenantPlayerId())) {
            return false;
        }

        contract.setStatus(LeaseStatus.TERMINATED);
        contract.setTerminationReason("Rejected by " + rejecterId);
        updateLease(contract);

        return true;
    }

    @Override
    public boolean terminateLease(UUID contractId, UUID terminatorId, String reason) {
        LeaseContract contract = getLease(contractId).orElse(null);
        if (contract == null) {
            return false;
        }

        // 只有相关方可以终止
        if (!terminatorId.equals(contract.lessorPlayerId()) &&
            !terminatorId.equals(contract.tenantPlayerId())) {
            return false;
        }

        LeaseTerminatedEvent event = new LeaseTerminatedEvent(contract, terminatorId, reason);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return false;
        }

        contract.setStatus(LeaseStatus.TERMINATED);
        contract.setTerminationReason(reason);
        updateLease(contract);

        return true;
    }

    @Override
    public boolean renewLease(UUID contractId, UUID extenderId, int additionalDays) {
        LeaseContract contract = getLease(contractId).orElse(null);
        if (contract == null || !contract.isActive()) {
            return false;
        }

        // 只有承租方可以续租
        if (!extenderId.equals(contract.tenantPlayerId())) {
            return false;
        }

        // 延长租期
        if (contract.endDate() != null) {
            contract.setEndDate(contract.endDate().plusSeconds((long) additionalDays * 24 * 60 * 60));
        }

        updateLease(contract);
        return true;
    }

    @Override
    public boolean payRent(UUID contractId, UUID payerId, int months) {
        LeaseContract contract = getLease(contractId).orElse(null);
        if (contract == null || !contract.isActive()) {
            return false;
        }

        // 只有承租方可以支付租金
        if (!payerId.equals(contract.tenantPlayerId())) {
            return false;
        }

        BigDecimal amount = contract.calculatePayment(months);

        // 从承租方扣除租金
        if (!economyService.has(payerId, amount)) {
            return false;
        }

        // 扣款（检查返回值）
        if (!economyService.withdraw(payerId, amount)) {
            return false;
        }

        // 月份参数校验（防止溢出）
        int safeMonths = Math.max(1, Math.min(months, 120)); // 最多10年

        // 将租金转入出租方国库或玩家账户（使用事务确保扣款与存款一致）
        boolean depositSuccess = false;
        try {
            if (treasuryService != null && contract.lessorNationId() != null) {
                // 如果出租方关联了国家，将租金转入国家国库
                treasuryService.deposit(contract.lessorNationId(), amount,
                    "租金收入: " + contract.tenantPlayerId() + " 支付 " + months + " 个月租金");
            } else {
                // 否则直接发放给出租方玩家
                economyService.deposit(contract.lessorPlayerId(), amount);
            }
            depositSuccess = true;
        } catch (Exception e) {
            // 存款失败，回滚扣款
            economyService.deposit(payerId, amount);
            plugin.getLogger().warning("Rent deposit failed, rolled back: " + e.getMessage());
            return false;
        }

        // 更新支付时间
        Instant now = Instant.now();
        contract.setLastPaymentAt(now);
        contract.setNextPaymentDue(now.plusSeconds((long) safeMonths * 30 * 24 * 60 * 60));
        contract.setOverdueDays(0);
        updateLease(contract);

        // 触发支付事件
        LeasePaymentEvent event = new LeasePaymentEvent(contract, payerId, amount, safeMonths);
        Bukkit.getPluginManager().callEvent(event);

        return true;
    }

    @Override
    public Optional<LeaseContract> getLease(UUID contractId) {
        LeaseContract cached = leaseCache.get(contractId);
        if (cached != null) {
            return Optional.of(cached);
        }

        return loadLeaseFromDb(contractId);
    }

    @Override
    public Collection<LeaseContract> getLeasesByLessor(NationId nationId) {
        List<LeaseContract> results = new ArrayList<>();

        String sql = "SELECT * FROM lease_contracts WHERE lessor_nation_id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, nationId.value().toString());
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                LeaseContract contract = parseLeaseContract(rs);
                leaseCache.put(contract.id(), contract);
                results.add(contract);
            }
        } catch (SQLException e) {
            logger.severe("Failed to load leases by lessor: " + e.getMessage());
        }

        return results;
    }

    @Override
    public Collection<LeaseContract> getLeasesByTenant(NationId nationId) {
        List<LeaseContract> results = new ArrayList<>();

        String sql = "SELECT * FROM lease_contracts WHERE tenant_nation_id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, nationId.value().toString());
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                LeaseContract contract = parseLeaseContract(rs);
                leaseCache.put(contract.id(), contract);
                results.add(contract);
            }
        } catch (SQLException e) {
            logger.severe("Failed to load leases by tenant: " + e.getMessage());
        }

        return results;
    }

    @Override
    public Collection<LeaseContract> getLeasesByRegion(String regionId) {
        List<LeaseContract> results = new ArrayList<>();

        String sql = "SELECT * FROM lease_contracts WHERE region_id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, regionId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                LeaseContract contract = parseLeaseContract(rs);
                leaseCache.put(contract.id(), contract);
                results.add(contract);
            }
        } catch (SQLException e) {
            logger.severe("Failed to load leases by region: " + e.getMessage());
        }

        return results;
    }

    @Override
    public Collection<LeaseContract> getLeasesByPlayer(UUID playerId) {
        List<LeaseContract> results = new ArrayList<>();

        String sql = "SELECT * FROM lease_contracts WHERE lessor_player_id = ? OR tenant_player_id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerId.toString());
            stmt.setString(2, playerId.toString());
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                LeaseContract contract = parseLeaseContract(rs);
                leaseCache.put(contract.id(), contract);
                results.add(contract);
            }
        } catch (SQLException e) {
            logger.severe("Failed to load leases by player: " + e.getMessage());
        }

        return results;
    }

    @Override
    public Collection<LeaseContract> getExpiringLeases(int daysRemaining) {
        List<LeaseContract> results = new ArrayList<>();
        Instant threshold = Instant.now().plusSeconds((long) daysRemaining * 24 * 60 * 60);

        String sql = "SELECT * FROM lease_contracts WHERE status = 'ACTIVE' AND end_date <= ? AND end_date > ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setTimestamp(1, Timestamp.from(threshold));
            stmt.setTimestamp(2, Timestamp.from(Instant.now()));
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                LeaseContract contract = parseLeaseContract(rs);
                leaseCache.put(contract.id(), contract);
                results.add(contract);
            }
        } catch (SQLException e) {
            logger.severe("Failed to load expiring leases: " + e.getMessage());
        }

        return results;
    }

    @Override
    public Collection<LeaseContract> getOverdueLeases() {
        List<LeaseContract> results = new ArrayList<>();

        String sql = "SELECT * FROM lease_contracts WHERE status IN ('ACTIVE', 'OVERDUE') AND next_payment_due < ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setTimestamp(1, Timestamp.from(Instant.now()));
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                LeaseContract contract = parseLeaseContract(rs);
                leaseCache.put(contract.id(), contract);
                results.add(contract);
            }
        } catch (SQLException e) {
            logger.severe("Failed to load overdue leases: " + e.getMessage());
        }

        return results;
    }

    @Override
    public boolean isLeaseActive(UUID contractId) {
        return getLease(contractId).map(LeaseContract::isActive).orElse(false);
    }

    @Override
    public void processExpiredLeases() {
        String sql = "SELECT * FROM lease_contracts WHERE status = 'ACTIVE' AND end_date < ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setTimestamp(1, Timestamp.from(Instant.now()));
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                LeaseContract contract = parseLeaseContract(rs);
                contract.setStatus(LeaseStatus.EXPIRED);
                updateLease(contract);

                LeaseExpiredEvent event = new LeaseExpiredEvent(contract);
                Bukkit.getPluginManager().callEvent(event);
            }
        } catch (SQLException e) {
            logger.severe("Failed to process expired leases: " + e.getMessage());
        }
    }

    @Override
    public void processOverduePayments() {
        String sql = "SELECT * FROM lease_contracts WHERE status = 'ACTIVE' AND next_payment_due < ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setTimestamp(1, Timestamp.from(Instant.now()));
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                LeaseContract contract = parseLeaseContract(rs);
                int daysOverdue = contract.getDaysOverdue();
                contract.setStatus(LeaseStatus.OVERDUE);
                contract.setOverdueDays(daysOverdue);
                updateLease(contract);
            }
        } catch (SQLException e) {
            logger.severe("Failed to process overdue payments: " + e.getMessage());
        }
    }

    @Override
    public String summary() {
        return "Lease service managing " + leaseCache.size() + " contracts";
    }

    // ==================== Database Operations ====================

    private void saveLease(LeaseContract contract) {
        String sql = """
            INSERT INTO lease_contracts (id, lessor_nation_id, lessor_player_id, tenant_nation_id, tenant_player_id,
                type, region_id, monthly_rent, total_value, status, created_at, signed_at, start_date, end_date,
                next_payment_due, last_payment_at, lessor_signed, tenant_signed, overdue_days, termination_reason)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            setLeaseParams(stmt, contract);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.severe("Failed to save lease contract: " + e.getMessage());
        }
    }

    private void updateLease(LeaseContract contract) {
        String sql = """
            UPDATE lease_contracts SET lessor_nation_id = ?, lessor_player_id = ?, tenant_nation_id = ?,
                tenant_player_id = ?, type = ?, region_id = ?, monthly_rent = ?, total_value = ?, status = ?,
                created_at = ?, signed_at = ?, start_date = ?, end_date = ?, next_payment_due = ?,
                last_payment_at = ?, lessor_signed = ?, tenant_signed = ?, overdue_days = ?, termination_reason = ?
            WHERE id = ?
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            setLeaseParams(stmt, contract);
            stmt.setString(20, contract.id().toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.severe("Failed to update lease contract: " + e.getMessage());
        }
    }

    private void setLeaseParams(PreparedStatement stmt, LeaseContract contract) throws SQLException {
        stmt.setString(1, contract.lessorNationId() != null ? contract.lessorNationId().value().toString() : null);
        stmt.setString(2, contract.lessorPlayerId().toString());
        stmt.setString(3, contract.tenantNationId() != null ? contract.tenantNationId().value().toString() : null);
        stmt.setString(4, contract.tenantPlayerId() != null ? contract.tenantPlayerId().toString() : null);
        stmt.setString(5, contract.type().name());
        stmt.setString(6, contract.regionId());
        stmt.setBigDecimal(7, contract.monthlyRent());
        stmt.setBigDecimal(8, contract.totalValue());
        stmt.setString(9, contract.status().name());
        stmt.setTimestamp(10, Timestamp.from(contract.createdAt()));
        stmt.setTimestamp(11, contract.signedAt() != null ? Timestamp.from(contract.signedAt()) : null);
        stmt.setTimestamp(12, contract.startDate() != null ? Timestamp.from(contract.startDate()) : null);
        stmt.setTimestamp(13, contract.endDate() != null ? Timestamp.from(contract.endDate()) : null);
        stmt.setTimestamp(14, contract.nextPaymentDue() != null ? Timestamp.from(contract.nextPaymentDue()) : null);
        stmt.setTimestamp(15, contract.lastPaymentAt() != null ? Timestamp.from(contract.lastPaymentAt()) : null);
        stmt.setBoolean(16, contract.lessorSigned());
        stmt.setBoolean(17, contract.tenantSigned());
        stmt.setInt(18, contract.overdueDays());
        stmt.setString(19, contract.terminationReason());
    }

    private Optional<LeaseContract> loadLeaseFromDb(UUID contractId) {
        String sql = "SELECT * FROM lease_contracts WHERE id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, contractId.toString());
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                LeaseContract contract = parseLeaseContract(rs);
                leaseCache.put(contract.id(), contract);
                return Optional.of(contract);
            }
        } catch (SQLException e) {
            logger.severe("Failed to load lease contract: " + e.getMessage());
        }

        return Optional.empty();
    }

    private LeaseContract parseLeaseContract(ResultSet rs) throws SQLException {
        return new LeaseContract(
            UUID.fromString(rs.getString("id")),
            rs.getString("lessor_nation_id") != null ? new NationId(UUID.fromString(rs.getString("lessor_nation_id"))) : null,
            UUID.fromString(rs.getString("lessor_player_id")),
            rs.getString("tenant_nation_id") != null ? new NationId(UUID.fromString(rs.getString("tenant_nation_id"))) : null,
            rs.getString("tenant_player_id") != null ? UUID.fromString(rs.getString("tenant_player_id")) : null,
            LeaseType.valueOf(rs.getString("type")),
            rs.getString("region_id"),
            rs.getBigDecimal("monthly_rent"),
            rs.getBigDecimal("total_value"),
            LeaseStatus.valueOf(rs.getString("status")),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("signed_at") != null ? rs.getTimestamp("signed_at").toInstant() : null,
            rs.getTimestamp("start_date") != null ? rs.getTimestamp("start_date").toInstant() : null,
            rs.getTimestamp("end_date") != null ? rs.getTimestamp("end_date").toInstant() : null,
            rs.getTimestamp("next_payment_due") != null ? rs.getTimestamp("next_payment_due").toInstant() : null,
            rs.getTimestamp("last_payment_at") != null ? rs.getTimestamp("last_payment_at").toInstant() : null,
            rs.getBoolean("lessor_signed"),
            rs.getBoolean("tenant_signed"),
            rs.getInt("overdue_days"),
            rs.getString("termination_reason")
        );
    }
}
