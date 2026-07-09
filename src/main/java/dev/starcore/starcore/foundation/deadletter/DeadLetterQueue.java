package dev.starcore.starcore.foundation.deadletter;

import org.slf4j.Logger;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 死信队列服务
 *
 * 无法处理的事件移入此处，等待人工介入
 */
public final class DeadLetterQueue {

    private final String tableName;
    private final ConnectionProvider connectionProvider;
    private final Logger logger;

    public DeadLetterQueue(String tableName, ConnectionProvider connectionProvider, Logger logger) {
        this.tableName = tableName;
        this.connectionProvider = connectionProvider;
        this.logger = logger;
        initTable();
    }

    private void initTable() {
        String sql = "CREATE TABLE IF NOT EXISTS " + tableName + " (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "original_payload TEXT NOT NULL," +
            "error_message TEXT NOT NULL," +
            "failure_count INTEGER DEFAULT 0," +
            "created_at INTEGER NOT NULL," +
            "last_attempt_at INTEGER," +
            "resolved_at INTEGER," +
            "resolution TEXT" +
            ")";

        try (Connection conn = connectionProvider.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            logger.info("Dead letter table initialized: {}", tableName);
        } catch (SQLException e) {
            logger.error("Failed to initialize dead letter table: {}", e.getMessage(), e);
        }
    }

    /**
     * 添加死信
     */
    public void add(String payload, String error) {
        String sql = "INSERT INTO " + tableName + " (original_payload, error_message, failure_count, created_at) VALUES (?, ?, 1, ?)";

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, payload);
            ps.setString(2, error);
            ps.setLong(3, Instant.now().toEpochMilli());
            ps.executeUpdate();
            logger.warn("Added to dead letter queue: {}", error);
        } catch (SQLException e) {
            logger.error("Failed to add dead letter: {}", e.getMessage(), e);
        }
    }

    /**
     * 获取待处理死信
     */
    public List<DeadLetter> getPending(int limit) {
        String sql = "SELECT id, original_payload, error_message, failure_count, created_at, last_attempt_at " +
            "FROM " + tableName + " " +
            "WHERE resolved_at IS NULL " +
            "ORDER BY created_at ASC " +
            "LIMIT ?";

        List<DeadLetter> letters = new ArrayList<>();

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    letters.add(new DeadLetter(
                        rs.getLong("id"),
                        rs.getString("original_payload"),
                        rs.getString("error_message"),
                        rs.getInt("failure_count"),
                        Instant.ofEpochMilli(rs.getLong("created_at")),
                        rs.getObject("last_attempt_at") != null
                            ? Instant.ofEpochMilli(rs.getLong("last_attempt_at"))
                            : null
                    ));
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to get pending dead letters: {}", e.getMessage(), e);
        }

        return letters;
    }

    /**
     * 标记已处理
     */
    public void resolve(long id, String resolution) {
        String sql = "UPDATE " + tableName + " SET resolved_at = ?, resolution = ? WHERE id = ?";

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, Instant.now().toEpochMilli());
            ps.setString(2, resolution);
            ps.setLong(3, id);
            ps.executeUpdate();
            logger.info("Resolved dead letter {}: {}", id, resolution);
        } catch (SQLException e) {
            logger.error("Failed to resolve dead letter: {}", e.getMessage(), e);
        }
    }

    public interface ConnectionProvider {
        Connection getConnection() throws SQLException;
    }

    public record DeadLetter(
        long id,
        String payload,
        String error,
        int failureCount,
        Instant createdAt,
        Instant lastAttemptAt
    ) {}
}
