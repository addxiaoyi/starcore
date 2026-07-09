package dev.starcore.starcore.audit;

import dev.starcore.starcore.core.database.DatabaseService;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * 操作审计日志系统
 * 记录所有关键操作用于安全审计和问题追踪
 */
public final class AuditLogService {
    private final Plugin plugin;
    private final DatabaseService databaseService;
    private final Logger logger;
    // E-101: 使用异步队列批量写入，避免每次命令都开连接
    private final java.util.concurrent.ConcurrentLinkedQueue<AuditQueueEntry> auditQueue = new java.util.concurrent.ConcurrentLinkedQueue<>();
    private final java.util.concurrent.ScheduledExecutorService flushScheduler;
    private static final int BATCH_SIZE = 50;

    public AuditLogService(Plugin plugin, DatabaseService databaseService) {
        this.plugin = plugin;
        this.databaseService = databaseService;
        this.logger = plugin.getLogger();
        // E-101: 每秒批量 flush 一次
        this.flushScheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "AuditLogFlush");
            t.setDaemon(true);
            return t;
        });
        this.flushScheduler.scheduleAtFixedRate(this::flushQueue, 1, 1, java.util.concurrent.TimeUnit.SECONDS);
    }

    private void flushQueue() {
        java.util.List<AuditQueueEntry> batch = new java.util.ArrayList<>();
        AuditQueueEntry entry;
        while ((entry = auditQueue.poll()) != null && batch.size() < BATCH_SIZE) {
            batch.add(entry);
        }
        if (batch.isEmpty()) return;
        try {
            writeBatch(batch);
        } catch (Exception e) {
            logger.warning("批量写入审计日志失败: " + e.getMessage());
        }
    }

    /**
     * 启动服务并初始化数据库表
     */
    public void start() {
        try {
            initializeTables();
            logger.info("✅ 审计日志系统已启动");
        } catch (Exception e) {
            logger.severe("❌ 审计日志表初始化失败: " + e.getMessage());
        }
    }

    /**
     * 初始化审计日志表
     * E-103: 使用 SQLite 兼容的 DDL，分数据库类型创建不同索引
     */
    private void initializeTables() throws SQLException {
        // 检测数据库类型
        boolean isMySQL = false;
        try {
            var ds = databaseService.dataSource().orElseThrow();
            isMySQL = ds.getConnection().getMetaData().getDatabaseProductName().toLowerCase().contains("mysql");
        } catch (Exception ignored) {}

        String createTableSQL;
        if (isMySQL) {
            // MySQL 语法
            createTableSQL = """
                CREATE TABLE IF NOT EXISTS audit_logs (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    player_uuid VARCHAR(36),
                    player_name VARCHAR(64),
                    action_type VARCHAR(50) NOT NULL,
                    action_detail TEXT,
                    blocked BOOLEAN DEFAULT FALSE,
                    ip_address VARCHAR(45),
                    world VARCHAR(64),
                    x INT,
                    y INT,
                    z INT,
                    INDEX idx_timestamp (timestamp),
                    INDEX idx_player (player_uuid),
                    INDEX idx_type (action_type),
                    INDEX idx_blocked (blocked)
                )
                """;
        } else {
            // E-103: SQLite 兼容语法，不使用内嵌索引
            createTableSQL = """
                CREATE TABLE IF NOT EXISTS audit_logs (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    timestamp TEXT NOT NULL DEFAULT (datetime('now')),
                    player_uuid TEXT,
                    player_name TEXT,
                    action_type TEXT NOT NULL,
                    action_detail TEXT,
                    blocked INTEGER DEFAULT 0,
                    ip_address TEXT,
                    world TEXT,
                    x INTEGER,
                    y INTEGER,
                    z INTEGER
                )
                """;
            // E-104: SQLite 中索引按需创建
            createIndexIfNotExists("audit_logs", "idx_timestamp", "timestamp");
            createIndexIfNotExists("audit_logs", "idx_player", "player_uuid");
            createIndexIfNotExists("audit_logs", "idx_type", "action_type");
        }

        try (Connection conn = databaseService.dataSource()
                .orElseThrow(() -> new SQLException("Database not available"))
                .getConnection();
             PreparedStatement stmt = conn.prepareStatement(createTableSQL)) {
            stmt.executeUpdate();
        } catch (Exception e) {
            logger.severe("Failed to create audit log table: " + e.getMessage());
            throw new RuntimeException("Failed to initialize audit log table", e);
        }
    }

    private void createIndexIfNotExists(String table, String indexName, String column) {
        try (Connection conn = databaseService.dataSource()
                .orElseThrow(() -> new SQLException("Database not available"))
                .getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "CREATE INDEX IF NOT EXISTS " + indexName + " ON " + table + " (" + column + ")")) {
            stmt.executeUpdate();
        } catch (Exception ignored) {}
    }

    /**
     * 记录命令执行（异步 - 使用队列批量写入）
     * E-101: 改为入队而非直接写入，减少主线程阻塞
     */
    public void logCommand(UUID playerId, String playerName, String command, boolean blocked, String ipAddress) {
        auditQueue.offer(new AuditQueueEntry(playerId, playerName, command, blocked ? 1 : 0, ipAddress, "COMMAND"));
    }

    /**
     * 记录命令执行（异步 - 已改用队列，直接调用 logCommand 即可）
     */
    public void logCommandAsync(UUID playerId, String playerName, String command, boolean blocked, String ipAddress) {
        logCommand(playerId, playerName, command, blocked, ipAddress);
    }

    private void writeBatch(java.util.List<AuditQueueEntry> batch) throws SQLException {
        String sql = "INSERT INTO audit_logs (player_uuid, player_name, action_type, action_detail, blocked, ip_address) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = databaseService.dataSource()
                .orElseThrow(() -> new SQLException("Database not available"))
                .getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                for (AuditQueueEntry e : batch) {
                    stmt.setString(1, e.playerId != null ? e.playerId.toString() : null);
                    stmt.setString(2, e.playerName);
                    stmt.setString(3, e.actionType);
                    stmt.setString(4, e.detail);
                    stmt.setInt(5, e.blocked);
                    stmt.setString(6, e.ipAddress);
                    stmt.addBatch();
                }
                stmt.executeBatch();
                conn.commit();
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            }
        }
    }

    private record AuditQueueEntry(UUID playerId, String playerName, String detail, int blocked, String ipAddress, String actionType) {}

    /**
     * 记录经济交易
     * E-101: 使用队列批量写入
     */
    public void logEconomyTransaction(UUID from, UUID to, String amount, String transactionType) {
        String detail = String.format("Type: %s, From: %s, To: %s, Amount: %s",
            transactionType, from, to, amount);
        auditQueue.offer(new AuditQueueEntry(from, null, detail, 0, null, "ECONOMY_" + transactionType.toUpperCase()));
    }

    /**
     * 记录权限变更
     * E-101: 使用队列批量写入
     */
    public void logPermissionChange(UUID playerId, String playerName, String permission, boolean granted) {
        String detail = String.format("Permission: %s, Action: %s", permission, granted ? "GRANT" : "REVOKE");
        auditQueue.offer(new AuditQueueEntry(playerId, playerName, detail, 0, null, "PERMISSION_CHANGE"));
    }

    /**
     * 记录配置变更
     * E-101: 使用队列批量写入
     */
    public void logConfigChange(UUID playerId, String playerName, String configKey, String oldValue, String newValue) {
        String detail = String.format("Key: %s, Old: %s, New: %s", configKey, oldValue, newValue);
        auditQueue.offer(new AuditQueueEntry(playerId, playerName, detail, 0, null, "CONFIG_CHANGE"));
    }

    /**
     * 按玩家查询日志
     */
    public List<AuditLogEntry> queryByPlayer(UUID playerId, LocalDateTime from, LocalDateTime to, int limit) {
        List<AuditLogEntry> entries = new ArrayList<>();

        String sql = """
            SELECT * FROM audit_logs
            WHERE player_uuid = ?
            AND timestamp BETWEEN ? AND ?
            ORDER BY timestamp DESC
            LIMIT ?
            """;

        try (Connection conn = databaseService.dataSource()
                .orElseThrow(() -> new SQLException("Database not available"))
                .getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerId.toString());
            stmt.setObject(2, from);
            stmt.setObject(3, to);
            stmt.setInt(4, limit);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    entries.add(mapResultSet(rs));
                }
            }
        } catch (Exception e) {
            logger.warning("查询审计日志失败: " + e.getMessage());
        }

        return entries;
    }

    /**
     * 按操作类型查询日志
     */
    public List<AuditLogEntry> queryByType(AuditActionType type, LocalDateTime from, LocalDateTime to, int limit) {
        List<AuditLogEntry> entries = new ArrayList<>();

        String sql = """
            SELECT * FROM audit_logs
            WHERE action_type = ?
            AND timestamp BETWEEN ? AND ?
            ORDER BY timestamp DESC
            LIMIT ?
            """;

        try (Connection conn = databaseService.dataSource()
                .orElseThrow(() -> new SQLException("Database not available"))
                .getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, type.name());
            stmt.setObject(2, from);
            stmt.setObject(3, to);
            stmt.setInt(4, limit);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    entries.add(mapResultSet(rs));
                }
            }
        } catch (Exception e) {
            logger.warning("查询审计日志失败: " + e.getMessage());
        }

        return entries;
    }

    /**
     * 查询被拦截的操作
     */
    public List<AuditLogEntry> queryBlockedActions(LocalDateTime from, LocalDateTime to, int limit) {
        List<AuditLogEntry> entries = new ArrayList<>();

        String sql = """
            SELECT * FROM audit_logs
            WHERE blocked = TRUE
            AND timestamp BETWEEN ? AND ?
            ORDER BY timestamp DESC
            LIMIT ?
            """;

        try (Connection conn = databaseService.dataSource()
                .orElseThrow(() -> new SQLException("Database not available"))
                .getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, from);
            stmt.setObject(2, to);
            stmt.setInt(3, limit);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    entries.add(mapResultSet(rs));
                }
            }
        } catch (Exception e) {
            logger.warning("查询被拦截操作失败: " + e.getMessage());
        }

        return entries;
    }

    /**
     * 生成审计报告统计
     */
    public AuditStatistics generateStatistics(LocalDateTime from, LocalDateTime to) {
        String sql = """
            SELECT
                action_type,
                COUNT(*) as count,
                SUM(CASE WHEN blocked = TRUE THEN 1 ELSE 0 END) as blocked_count
            FROM audit_logs
            WHERE timestamp BETWEEN ? AND ?
            GROUP BY action_type
            """;

        AuditStatistics stats = new AuditStatistics();

        try (Connection conn = databaseService.dataSource()
                .orElseThrow(() -> new SQLException("Database not available"))
                .getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, from);
            stmt.setObject(2, to);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String actionType = rs.getString("action_type");
                    int count = rs.getInt("count");
                    int blockedCount = rs.getInt("blocked_count");
                    stats.add(actionType, count, blockedCount);
                }
            }
        } catch (Exception e) {
            logger.warning("生成审计统计失败: " + e.getMessage());
        }

        return stats;
    }

    /**
     * 清理旧日志（保留最近N天）
     */
    public int cleanOldLogs(int retentionDays) {
        String sql = """
            DELETE FROM audit_logs
            WHERE timestamp < DATE_SUB(NOW(), INTERVAL ? DAY)
            """;

        try (Connection conn = databaseService.dataSource()
                .orElseThrow(() -> new SQLException("Database not available"))
                .getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, retentionDays);
            int deleted = stmt.executeUpdate();
            logger.info("清理审计日志: " + deleted + " 条记录被删除（保留 " + retentionDays + " 天）");
            return deleted;
        } catch (Exception e) {
            logger.warning("清理审计日志失败: " + e.getMessage());
            return 0;
        }
    }

    private AuditLogEntry mapResultSet(ResultSet rs) throws SQLException {
        return new AuditLogEntry(
            rs.getLong("id"),
            rs.getTimestamp("timestamp").toLocalDateTime(),
            rs.getString("player_uuid") != null ? UUID.fromString(rs.getString("player_uuid")) : null,
            rs.getString("player_name"),
            AuditActionType.valueOf(rs.getString("action_type")),
            rs.getString("action_detail"),
            rs.getBoolean("blocked"),
            rs.getString("ip_address"),
            rs.getString("world"),
            rs.getInt("x"),
            rs.getInt("y"),
            rs.getInt("z")
        );
    }
}
