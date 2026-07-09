package dev.starcore.starcore.foundation.outbox;

import org.slf4j.Logger;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Outbox服务
 *
 * 确保事件至少被处理一次
 */
public final class OutboxService {

    private final String tableName;
    private final ConnectionProvider connectionProvider;
    private final Logger logger;

    public OutboxService(String tableName, ConnectionProvider connectionProvider, Logger logger) {
        this.tableName = tableName;
        this.connectionProvider = connectionProvider;
        this.logger = logger;
        initTable();
    }

    private void initTable() {
        String sql = "CREATE TABLE IF NOT EXISTS " + tableName + " (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "event_type TEXT NOT NULL," +
            "aggregate_type TEXT NOT NULL," +
            "aggregate_id TEXT NOT NULL," +
            "payload TEXT NOT NULL," +
            "status TEXT DEFAULT 'PENDING'," +
            "created_at INTEGER NOT NULL," +
            "processed_at INTEGER," +
            "failure_reason TEXT," +
            "retry_count INTEGER DEFAULT 0" +
            ")";

        try (Connection conn = connectionProvider.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            logger.info("Outbox table initialized: {}", tableName);
        } catch (SQLException e) {
            logger.error("Failed to initialize outbox table: {}", e.getMessage(), e);
        }
    }

    /**
     * 保存事件
     */
    public void saveEvent(String eventType, String aggregateType, String aggregateId, Object payload) {
        String sql = "INSERT INTO " + tableName + " (event_type, aggregate_type, aggregate_id, payload, status, created_at) VALUES (?, ?, ?, ?, 'PENDING', ?)";

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, eventType);
            ps.setString(2, aggregateType);
            ps.setString(3, aggregateId);
            ps.setString(4, new com.google.gson.Gson().toJson(payload));
            ps.setLong(5, Instant.now().toEpochMilli());
            ps.executeUpdate();
            logger.debug("Saved outbox event: {} for {}/{}", eventType, aggregateType, aggregateId);
        } catch (SQLException e) {
            logger.error("Failed to save outbox event: {}", e.getMessage(), e);
        }
    }

    /**
     * 获取待处理事件
     */
    public List<OutboxEvent> findPendingEvents(int limit) {
        String sql = "SELECT id, event_type, aggregate_type, aggregate_id, payload, retry_count " +
            "FROM " + tableName + " " +
            "WHERE status = 'PENDING' " +
            "ORDER BY created_at ASC " +
            "LIMIT ?";

        List<OutboxEvent> events = new ArrayList<>();

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    events.add(new OutboxEvent(
                        rs.getLong("id"),
                        rs.getString("event_type"),
                        rs.getString("aggregate_type"),
                        rs.getString("aggregate_id"),
                        rs.getString("payload"),
                        rs.getInt("retry_count")
                    ));
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to find pending events: {}", e.getMessage(), e);
        }

        return events;
    }

    /**
     * 标记处理中
     */
    public void markProcessing(long id) {
        updateStatus(id, "PROCESSING", null);
    }

    /**
     * 标记已处理
     */
    public void markProcessed(long id) {
        updateStatus(id, "PROCESSED", null);
    }

    /**
     * 标记失败
     */
    public void markFailed(long id, String reason) {
        String sql = "UPDATE " + tableName + " SET status = 'PENDING', retry_count = retry_count + 1, failure_reason = ? WHERE id = ?";

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, reason);
            ps.setLong(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.error("Failed to mark event failed: {}", e.getMessage(), e);
        }
    }

    private void updateStatus(long id, String status, String reason) {
        String sql = "UPDATE " + tableName + " SET status = ?, processed_at = ? WHERE id = ?";

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setLong(2, Instant.now().toEpochMilli());
            ps.setLong(3, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.error("Failed to update event status: {}", e.getMessage(), e);
        }
    }

    public interface ConnectionProvider {
        Connection getConnection() throws SQLException;
    }

    public record OutboxEvent(
        long id,
        String eventType,
        String aggregateType,
        String aggregateId,
        String payload,
        int retryCount
    ) {}
}
