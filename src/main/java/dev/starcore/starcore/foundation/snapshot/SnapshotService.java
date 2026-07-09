package dev.starcore.starcore.foundation.snapshot;

import com.google.gson.Gson;
import org.slf4j.Logger;

import java.sql.*;
import java.time.Instant;
import java.util.Optional;

/**
 * 快照服务
 *
 * 定期保存聚合快照，加速状态重放
 */
public final class SnapshotService {

    private static final int SNAPSHOT_INTERVAL = 100;
    private static final int MAX_SNAPSHOTS = 5;

    private final String tableName;
    private final ConnectionProvider connectionProvider;
    private final Logger logger;
    private final Gson gson = new Gson();

    public SnapshotService(String tableName, ConnectionProvider connectionProvider, Logger logger) {
        this.tableName = tableName;
        this.connectionProvider = connectionProvider;
        this.logger = logger;
        initTable();
    }

    private void initTable() {
        String sql = "CREATE TABLE IF NOT EXISTS " + tableName + " (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "aggregate_type TEXT NOT NULL," +
            "aggregate_id TEXT NOT NULL," +
            "version INTEGER NOT NULL," +
            "state TEXT NOT NULL," +
            "created_at INTEGER NOT NULL" +
            ")";

        try (Connection conn = connectionProvider.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            logger.info("Snapshot table initialized: {}", tableName);
        } catch (SQLException e) {
            logger.error("Failed to initialize snapshot table: {}", e.getMessage(), e);
        }
    }

    /**
     * 保存快照
     */
    public void save(String aggregateType, String aggregateId, int version, Object state) {
        String json = gson.toJson(state);

        String sql = "INSERT INTO " + tableName + " (aggregate_type, aggregate_id, version, state, created_at) VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, aggregateType);
            ps.setString(2, aggregateId);
            ps.setInt(3, version);
            ps.setString(4, json);
            ps.setLong(5, Instant.now().toEpochMilli());
            ps.executeUpdate();

            cleanup(aggregateType, aggregateId);
            logger.debug("Saved snapshot for {}/{} v{}", aggregateType, aggregateId, version);
        } catch (SQLException e) {
            logger.error("Failed to save snapshot: {}", e.getMessage(), e);
        }
    }

    /**
     * 获取最新快照
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<Snapshot<T>> getLatest(String aggregateType, String aggregateId) {
        String sql = "SELECT version, state, created_at " +
            "FROM " + tableName + " " +
            "WHERE aggregate_type = ? AND aggregate_id = ? " +
            "ORDER BY version DESC LIMIT 1";

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, aggregateType);
            ps.setString(2, aggregateId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int version = rs.getInt("version");
                    String json = rs.getString("state");
                    long createdAt = rs.getLong("created_at");
                    T state = (T) gson.fromJson(json, Object.class);
                    return Optional.of(new Snapshot<>(aggregateType, aggregateId, version, state, Instant.ofEpochMilli(createdAt)));
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to get snapshot: {}", e.getMessage(), e);
        }

        return Optional.empty();
    }

    /**
     * 检查是否需要快照
     */
    public boolean shouldSnapshot(int currentVersion, int lastSnapshotVersion) {
        return currentVersion - lastSnapshotVersion >= SNAPSHOT_INTERVAL;
    }

    private void cleanup(String aggregateType, String aggregateId) {
        String sql = "DELETE FROM " + tableName + " " +
            "WHERE aggregate_type = ? AND aggregate_id = ? " +
            "AND id NOT IN (" +
            "  SELECT id FROM " + tableName + " " +
            "  WHERE aggregate_type = ? AND aggregate_id = ? " +
            "  ORDER BY version DESC LIMIT ?" +
            ")";

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, aggregateType);
            ps.setString(2, aggregateId);
            ps.setString(3, aggregateType);
            ps.setString(4, aggregateId);
            ps.setInt(5, MAX_SNAPSHOTS);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.error("Failed to cleanup snapshots: {}", e.getMessage(), e);
        }
    }

    public interface ConnectionProvider {
        Connection getConnection() throws SQLException;
    }

    public record Snapshot<T>(
        String aggregateType,
        String aggregateId,
        int version,
        T state,
        Instant createdAt
    ) {}
}
