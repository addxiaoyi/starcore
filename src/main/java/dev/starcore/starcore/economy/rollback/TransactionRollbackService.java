package dev.starcore.starcore.economy.rollback;

import dev.starcore.starcore.core.database.DatabaseService;
import dev.starcore.starcore.foundation.economy.EconomyService;
import org.bukkit.plugin.Plugin;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * 交易回滚服务
 * 用于在发现漏洞后回滚错误的经济交易
 */
public final class TransactionRollbackService {
    private final DatabaseService databaseService;
    private final EconomyService economyService;
    private final Logger logger;

    // ✅ audit B-019: 账户级事务锁，防止并发交易导致快照不精准
    private final ConcurrentHashMap<UUID, ReentrantLock> accountLocks = new ConcurrentHashMap<>();

    /**
     * 获取账户锁（用于交易原子性保证）
     */
    private ReentrantLock getLock(UUID accountId) {
        return accountLocks.computeIfAbsent(accountId, k -> new ReentrantLock());
    }

    /**
     * 释放不再需要的锁（减少内存占用）
     */
    private void releaseLock(UUID accountId) {
        accountLocks.remove(accountId);
    }

    public TransactionRollbackService(Plugin plugin, DatabaseService databaseService, EconomyService economyService) {
        this.databaseService = databaseService;
        this.economyService = economyService;
        this.logger = plugin.getLogger();
    }

    /**
     * 启动服务并初始化数据库表
     */
    public void start() {
        try {
            initializeTables();
            logger.info("✅ 交易回滚系统已启动");
        } catch (Exception e) {
            logger.severe("❌ 交易回滚表初始化失败: " + e.getMessage());
        }
    }

    /**
     * 初始化交易快照表
     */
    private void initializeTables() throws SQLException {
        String createSnapshotTable = """
            CREATE TABLE IF NOT EXISTS transaction_snapshots (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                transaction_id VARCHAR(36) UNIQUE NOT NULL,
                timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                transaction_type VARCHAR(50) NOT NULL,
                from_player VARCHAR(36),
                to_player VARCHAR(36),
                amount DECIMAL(20, 2) NOT NULL,
                from_balance_before DECIMAL(20, 2),
                to_balance_before DECIMAL(20, 2),
                from_balance_after DECIMAL(20, 2),
                to_balance_after DECIMAL(20, 2),
                metadata TEXT,
                rolled_back BOOLEAN DEFAULT FALSE,
                rollback_timestamp TIMESTAMP NULL,
                rollback_reason TEXT,
                INDEX idx_timestamp (timestamp),
                INDEX idx_from (from_player),
                INDEX idx_to (to_player),
                INDEX idx_type (transaction_type),
                INDEX idx_rolled_back (rolled_back)
            )
            """;

        String createRollbackLog = """
            CREATE TABLE IF NOT EXISTS rollback_logs (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                rollback_id VARCHAR(36) NOT NULL,
                timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                operator VARCHAR(64),
                transaction_count INT NOT NULL,
                reason TEXT,
                status VARCHAR(20) NOT NULL,
                error_message TEXT,
                INDEX idx_rollback_id (rollback_id),
                INDEX idx_timestamp (timestamp)
            )
            """;

        try (Connection conn = databaseService.dataSource()
                .orElseThrow(() -> new SQLException("Database not available"))
                .getConnection();
             PreparedStatement stmt1 = conn.prepareStatement(createSnapshotTable)) {
            stmt1.executeUpdate();
        } catch (Exception e) {
            logger.severe("Failed to create snapshot table: " + e.getMessage());
            throw e;
        }

        try (Connection conn = databaseService.dataSource()
                .orElseThrow(() -> new SQLException("Database not available"))
                .getConnection();
             PreparedStatement stmt2 = conn.prepareStatement(createRollbackLog)) {
            stmt2.executeUpdate();
        } catch (Exception e) {
            logger.severe("Failed to create rollback log table: " + e.getMessage());
            throw e;
        }
    }

    /**
     * 原子性记录交易快照（在交易前调用）
     * ✅ audit B-019 (FIXED): 自动加锁，保证 snapshot -> 交易 -> updateAfter 原子化
     */
    public CompletableFuture<String> recordAtomicTransaction(
        TransactionType type,
        UUID from,
        UUID to,
        BigDecimal amount,
        Map<String, String> metadata
    ) {
        return CompletableFuture.supplyAsync(() -> {
            // 获取锁并确保释放
            List<ReentrantLock> acquiredLocks = new ArrayList<>();
            try {
                // 按 UUID 排序确保死锁避免
                List<UUID> accounts = Stream.of(from, to)
                    .filter(Objects::nonNull)
                    .sorted()
                    .toList();

                for (UUID account : accounts) {
                    ReentrantLock lock = getLock(account);
                    lock.lock();
                    acquiredLocks.add(lock);
                }

                String transactionId = UUID.randomUUID().toString();
                BigDecimal fromBalanceBefore = from != null ? economyService.getBalance(from) : null;
                BigDecimal toBalanceBefore = to != null ? economyService.getBalance(to) : null;

                String sql = """
                    INSERT INTO transaction_snapshots
                    (transaction_id, transaction_type, from_player, to_player, amount,
                     from_balance_before, to_balance_before, metadata)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """;

                try (Connection conn = databaseService.dataSource()
                        .orElseThrow(() -> new SQLException("Database not available"))
                        .getConnection();
                     PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, transactionId);
                    stmt.setString(2, type.name());
                    stmt.setString(3, from != null ? from.toString() : null);
                    stmt.setString(4, to != null ? to.toString() : null);
                    stmt.setBigDecimal(5, amount);
                    stmt.setBigDecimal(6, fromBalanceBefore);
                    stmt.setBigDecimal(7, toBalanceBefore);
                    stmt.setString(8, metadataToJson(metadata));
                    stmt.executeUpdate();
                }

                return transactionId;
            } catch (Exception e) {
                logger.severe("记录交易快照失败: " + e.getMessage());
                throw new RuntimeException("Failed to record transaction snapshot", e);
            } finally {
                // 释放所有锁
                for (ReentrantLock lock : acquiredLocks) {
                    lock.unlock();
                }
            }
        });
    }

    /**
     * 更新交易后的余额（在交易后调用，配合 recordAtomicTransaction 使用）
     */
    public CompletableFuture<Void> updateAfterBalances(String transactionId, UUID from, UUID to) {
        return CompletableFuture.runAsync(() -> {
            try {
                BigDecimal fromBalanceAfter = from != null ? economyService.getBalance(from) : null;
                BigDecimal toBalanceAfter = to != null ? economyService.getBalance(to) : null;

                String sql = """
                    UPDATE transaction_snapshots
                    SET from_balance_after = ?, to_balance_after = ?
                    WHERE transaction_id = ?
                    """;

                try (Connection conn = databaseService.dataSource()
                        .orElseThrow(() -> new SQLException("Database not available"))
                        .getConnection();
                     PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setBigDecimal(1, fromBalanceAfter);
                    stmt.setBigDecimal(2, toBalanceAfter);
                    stmt.setString(3, transactionId);
                    stmt.executeUpdate();
                } catch (Exception e) {
                    logger.severe("Database error updating after balances: " + e.getMessage());
                    throw new RuntimeException("Failed to update after balances", e);
                }
            } catch (Exception e) {
                logger.warning("更新交易后余额失败: " + e.getMessage());
            }
        });
    }

    /**
     * 回滚单笔交易
     */
    public RollbackResult rollbackTransaction(String transactionId, String reason, String operator) {
        try {
            TransactionSnapshot snapshot = getSnapshot(transactionId);
            if (snapshot == null) {
                return new RollbackResult(false, "交易快照不存在", 0);
            }

            if (snapshot.rolledBack()) {
                return new RollbackResult(false, "交易已被回滚", 0);
            }

            // 执行回滚
            boolean success = executeRollback(snapshot);
            if (!success) {
                return new RollbackResult(false, "回滚执行失败", 0);
            }

            // 标记为已回滚
            markAsRolledBack(transactionId, reason);

            // 记录回滚日志
            logRollback(UUID.randomUUID().toString(), operator, 1, reason, "SUCCESS", null);

            logger.info(String.format("✅ 交易回滚成功: %s (操作员: %s)", transactionId, operator));
            return new RollbackResult(true, "回滚成功", 1);

        } catch (Exception e) {
            logger.severe("回滚交易失败: " + e.getMessage());
            return new RollbackResult(false, "系统错误: " + e.getMessage(), 0);
        }
    }

    /**
     * 批量回滚（按时间范围）
     */
    public RollbackResult rollbackBatch(
        LocalDateTime from,
        LocalDateTime to,
        Predicate<TransactionSnapshot> filter,
        String reason,
        String operator
    ) {
        String rollbackId = UUID.randomUUID().toString();
        int successCount = 0;
        int failCount = 0;
        StringBuilder errors = new StringBuilder();

        try {
            List<TransactionSnapshot> snapshots = getSnapshotsByTimeRange(from, to);
            List<TransactionSnapshot> toRollback = snapshots.stream()
                .filter(s -> !s.rolledBack())
                .filter(filter)
                .toList();

            logger.info(String.format("开始批量回滚: %d 笔交易 (操作员: %s)", toRollback.size(), operator));

            // audit B-020: 区分 executeRollback 失败与 markAsRolledBack 失败两类；
            // markAsRolledBack 失败时已执行了资金回滚但状态未持久化，再次执行会重复 deposit/withdraw 刷钱。
            // 触发此类致命状态时立即终止后续批次并明确报错。
            int markFailCount = 0;
            boolean abortBatch = false;
            for (TransactionSnapshot snapshot : toRollback) {
                // audit B-020: 执行前再次校验未回滚，避免重复执行（防御性）
                if (isRolledBack(snapshot.transactionId())) {
                    continue;
                }
                try {
                    boolean success = executeRollback(snapshot);
                    if (success) {
                        try {
                            markAsRolledBack(snapshot.transactionId(), reason);
                            successCount++;
                        } catch (Exception me) {
                            // markAsRolledBack 失败：资金已变但状态未持久化——这是危险半完成状态
                            markFailCount++;
                            errors.append(snapshot.transactionId()).append(": 已回滚但状态标记失败: ").append(me.getMessage()).append("; ");
                            logger.severe("[Rollback] Batch abort: markAsRolledBack failed for tx=" + snapshot.transactionId() + "; funds already moved, manual fix required. Aborting remaining batch to prevent double-rollback.");
                            abortBatch = true;
                            break;
                        }
                    } else {
                        failCount++;
                        errors.append(snapshot.transactionId()).append(": 执行失败; ");
                    }
                } catch (Exception e) {
                    failCount++;
                    errors.append(snapshot.transactionId()).append(": ").append(e.getMessage()).append("; ");
                }
                if (abortBatch) break;
            }

            // audit B-021: 仅 failCount==0 且 markFailCount==0 才算 SUCCESS；markFailCount>0 算 PARTIAL_ABORT
            String status;
            if (markFailCount > 0) {
                status = "PARTIAL_ABORT";
            } else if (failCount == 0) {
                status = "SUCCESS";
            } else {
                status = "PARTIAL";
            }
            logRollback(rollbackId, operator, successCount, reason, status, errors.toString());

            logger.info(String.format("✅ 批量回滚完成: 成功 %d, 失败 %d", successCount, failCount));
            return new RollbackResult(true, String.format("成功: %d, 失败: %d", successCount, failCount), successCount);

        } catch (Exception e) {
            logger.severe("批量回滚失败: " + e.getMessage());
            logRollback(rollbackId, operator, successCount, reason, "FAILURE", e.getMessage());
            return new RollbackResult(false, "系统错误: " + e.getMessage(), successCount);
        }
    }

    /**
     * 执行回滚操作
     */
    private boolean executeRollback(TransactionSnapshot snapshot) {
        try {
            switch (snapshot.transactionType()) {
                case TRANSFER:
                    // audit B-017: 回滚转账顺序必须先 withdraw(to) 再 deposit(from)，
                    // 否则 deposit(from) 已成功而 withdraw(to) 因余额不足失败会造成凭空产生钱。
                    // audit B-018: 若 to 玩家已花掉那笔钱，withdraw 失败导致回滚失败；
                    // 此处仍按"先扣后补"顺序执行，避免回滚自身凭空印钞；调用方应在 markAsRolledBack 失败时人工处理。
                    if (snapshot.fromPlayer() != null && snapshot.toPlayer() != null) {
                        java.math.BigDecimal amount = snapshot.amount();
                        // 1. 先把 to 的钱扣回（若余额不足则失败，整体回滚中止）
                        boolean withdrawn = economyService.withdraw(snapshot.toPlayer(), amount);
                        if (!withdrawn) {
                            logger.warning("[Rollback] 无法从 to=" + snapshot.toPlayer() + " 扣回转账金额，余额不足，回滚中止。需人工处理。");
                            return false;
                        }
                        // 2. 再 deposit 到 from；若 deposit 失败需回滚刚刚成功的 withdraw(to)
                        boolean deposited = economyService.deposit(snapshot.fromPlayer(), amount);
                        if (!deposited) {
                            logger.severe("[Rollback] CRITICAL: deposit 回 from 失败，已成功 withdraw 的钱未退回；回滚 withdraw(to)。");
                            economyService.deposit(snapshot.toPlayer(), amount);
                            return false;
                        }
                    }
                    break;

                case DEPOSIT:
                    // 回滚存款：减少金额
                    if (snapshot.toPlayer() != null) {
                        // audit B-018: 余额不足时 withdraw 返回 false，回滚失败应记录而非凭空扣
                        boolean ok = economyService.withdraw(snapshot.toPlayer(), snapshot.amount());
                        if (!ok) {
                            logger.warning("[Rollback] DEPOSIT 回滚 withdraw 失败: " + snapshot.toPlayer());
                            return false;
                        }
                    }
                    break;

                case WITHDRAW:
                    // 回滚取款：增加金额
                    if (snapshot.fromPlayer() != null) {
                        boolean ok = economyService.deposit(snapshot.fromPlayer(), snapshot.amount());
                        if (!ok) {
                            logger.warning("[Rollback] WITHDRAW 回滚 deposit 失败: " + snapshot.fromPlayer());
                            return false;
                        }
                    }
                    break;

                default:
                    logger.warning("未知的交易类型: " + snapshot.transactionType());
                    return false;
            }
            return true;
        } catch (Exception e) {
            logger.warning("执行回滚失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 标记交易为已回滚
     */
    private void markAsRolledBack(String transactionId, String reason) throws SQLException {
        String sql = """
            UPDATE transaction_snapshots
            SET rolled_back = TRUE, rollback_timestamp = CURRENT_TIMESTAMP, rollback_reason = ?
            WHERE transaction_id = ?
            """;

        try (Connection conn = databaseService.dataSource()
                .orElseThrow(() -> new SQLException("Database not available"))
                .getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, reason);
            stmt.setString(2, transactionId);
            stmt.executeUpdate();
        } catch (Exception e) {
            logger.severe("Database error marking transaction as rolled back: " + e.getMessage());
            throw e;
        }
    }

    /**
     * 是否已回滚（audit B-020: 用于批量回滚前再次校验，避免重复执行资金操作）。
     */
    private boolean isRolledBack(String transactionId) {
        String sql = "SELECT rolled_back FROM transaction_snapshots WHERE transaction_id = ?";
        try (Connection conn = databaseService.dataSource()
                .orElseThrow(() -> new SQLException("Database not available"))
                .getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, transactionId);
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getBoolean(1);
                }
            }
        } catch (Exception e) {
            logger.severe("isRolledBack query failed for " + transactionId + ": " + e.getMessage());
            // 查询失败按已回滚处理，保守避免重复回滚刷钱
            return true;
        }
        return false;
    }

    /**
     * 记录回滚日志
     */
    private void logRollback(String rollbackId, String operator, int count, String reason, String status, String error) {
        try {
            String sql = """
                INSERT INTO rollback_logs (rollback_id, operator, transaction_count, reason, status, error_message)
                VALUES (?, ?, ?, ?, ?, ?)
                """;

            try (Connection conn = databaseService.dataSource()
                    .orElseThrow(() -> new SQLException("Database not available"))
                    .getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, rollbackId);
                stmt.setString(2, operator);
                stmt.setInt(3, count);
                stmt.setString(4, reason);
                stmt.setString(5, status);
                stmt.setString(6, error);
                stmt.executeUpdate();
            } catch (Exception e) {
                logger.severe("Database error logging rollback: " + e.getMessage());
                throw new RuntimeException("Failed to log rollback", e);
            }
        } catch (Exception e) {
            logger.warning("记录回滚日志失败: " + e.getMessage());
        }
    }

    /**
     * 获取交易快照
     */
    private TransactionSnapshot getSnapshot(String transactionId) throws SQLException {
        String sql = "SELECT * FROM transaction_snapshots WHERE transaction_id = ?";

        try (Connection conn = databaseService.dataSource()
                .orElseThrow(() -> new SQLException("Database not available"))
                .getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, transactionId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapSnapshot(rs);
                }
            }
        }
        return null;
    }

    /**
     * 按时间范围获取快照
     */
    private List<TransactionSnapshot> getSnapshotsByTimeRange(LocalDateTime from, LocalDateTime to) throws SQLException {
        List<TransactionSnapshot> snapshots = new ArrayList<>();
        String sql = """
            SELECT * FROM transaction_snapshots
            WHERE timestamp BETWEEN ? AND ?
            ORDER BY timestamp ASC
            """;

        try (Connection conn = databaseService.dataSource()
                .orElseThrow(() -> new SQLException("Database not available"))
                .getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, from);
            stmt.setObject(2, to);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    snapshots.add(mapSnapshot(rs));
                }
            }
        }
        return snapshots;
    }

    /**
     * 获取回滚历史
     */
    public List<RollbackLogEntry> getRollbackHistory(int limit) {
        List<RollbackLogEntry> logs = new ArrayList<>();
        String sql = """
            SELECT * FROM rollback_logs
            ORDER BY timestamp DESC
            LIMIT ?
            """;

        try (Connection conn = databaseService.dataSource()
                .orElseThrow(() -> new SQLException("Database not available"))
                .getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, limit);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    logs.add(new RollbackLogEntry(
                        rs.getString("rollback_id"),
                        rs.getTimestamp("timestamp").toLocalDateTime(),
                        rs.getString("operator"),
                        rs.getInt("transaction_count"),
                        rs.getString("reason"),
                        rs.getString("status"),
                        rs.getString("error_message")
                    ));
                }
            }
        } catch (Exception e) {
            logger.warning("获取回滚历史失败: " + e.getMessage());
        }
        return logs;
    }

    private TransactionSnapshot mapSnapshot(ResultSet rs) throws SQLException {
        return new TransactionSnapshot(
            rs.getString("transaction_id"),
            rs.getTimestamp("timestamp").toLocalDateTime(),
            TransactionType.valueOf(rs.getString("transaction_type")),
            rs.getString("from_player") != null ? UUID.fromString(rs.getString("from_player")) : null,
            rs.getString("to_player") != null ? UUID.fromString(rs.getString("to_player")) : null,
            rs.getBigDecimal("amount"),
            rs.getBigDecimal("from_balance_before"),
            rs.getBigDecimal("to_balance_before"),
            rs.getBigDecimal("from_balance_after"),
            rs.getBigDecimal("to_balance_after"),
            rs.getString("metadata"),
            rs.getBoolean("rolled_back"),
            rs.getTimestamp("rollback_timestamp") != null ? rs.getTimestamp("rollback_timestamp").toLocalDateTime() : null,
            rs.getString("rollback_reason")
        );
    }

    private String metadataToJson(Map<String, String> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return "{}";
        }
        StringBuilder json = new StringBuilder("{");
        metadata.forEach((k, v) -> json.append("\"").append(k).append("\":\"").append(v).append("\","));
        json.setLength(json.length() - 1); // 移除最后的逗号
        json.append("}");
        return json.toString();
    }
}
