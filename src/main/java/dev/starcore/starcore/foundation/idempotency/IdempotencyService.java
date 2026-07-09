package dev.starcore.starcore.foundation.idempotency;

import org.slf4j.Logger;

import java.sql.*;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 幂等性保证服务
 *
 * 确保操作可安全重试，不会产生重复副作用
 */
public final class IdempotencyService {

    private final String tableName;
    private final ConnectionProvider connectionProvider;
    private final Logger logger;

    // 内存缓存（短期幂等）
    private final Map<String, IdempotencyEntry> memoryCache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 60_000;

    public IdempotencyService(String tableName, ConnectionProvider connectionProvider, Logger logger) {
        this.tableName = tableName;
        this.connectionProvider = connectionProvider;
        this.logger = logger;
        initTable();
    }

    private void initTable() {
        String sql = "CREATE TABLE IF NOT EXISTS " + tableName + " (" +
            "idempotency_key TEXT PRIMARY KEY," +
            "result TEXT NOT NULL," +
            "created_at INTEGER NOT NULL," +
            "expires_at INTEGER" +
            ")";

        try (Connection conn = connectionProvider.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            logger.info("Idempotency table initialized: {}", tableName);
        } catch (SQLException e) {
            logger.error("Failed to initialize idempotency table: {}", e.getMessage(), e);
        }
    }

    /**
     * 执行幂等操作
     */
    public <T> IdempotencyResult<T> execute(String key, Operation<T> operation) {
        // 1. 检查内存缓存
        IdempotencyEntry cached = memoryCache.get(key);
        if (cached != null && !cached.isExpired()) {
            logger.debug("Idempotency hit (memory): {}", key);
            return cached.asResult();
        }

        // 2. 检查数据库
        IdempotencyResult<T> dbResult = getFromDatabase(key);
        if (dbResult != null) {
            memoryCache.put(key, new IdempotencyEntry(key, dbResult));
            logger.debug("Idempotency hit (database): {}", key);
            return dbResult;
        }

        // 3. 执行操作
        try {
            T result = operation.execute();
            IdempotencyResult<T> newResult = IdempotencyResult.success(key, result);
            saveToDatabase(key, newResult);
            memoryCache.put(key, new IdempotencyEntry(key, newResult));
            logger.debug("Idempotency executed: {}", key);
            return newResult;
        } catch (Exception e) {
            IdempotencyResult<T> errorResult = IdempotencyResult.failure(key, e.getMessage());
            logger.error("Idempotency operation failed: {} - {}", key, e.getMessage());
            return errorResult;
        }
    }

    private <T> IdempotencyResult<T> getFromDatabase(String key) {
        String sql = "SELECT result FROM " + tableName + " WHERE idempotency_key = ?";
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String json = rs.getString("result");
                    return IdempotencyResult.fromJson(key, json);
                }
            }
        } catch (Exception e) {
            logger.error("Failed to get idempotency result: {}", e.getMessage());
        }
        return null;
    }

    private <T> void saveToDatabase(String key, IdempotencyResult<T> result) {
        String sql = "INSERT OR REPLACE INTO " + tableName + " (idempotency_key, result, created_at) VALUES (?, ?, ?)";
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, key);
            ps.setString(2, result.toJson());
            ps.setLong(3, Instant.now().toEpochMilli());
            ps.executeUpdate();
        } catch (Exception e) {
            logger.error("Failed to save idempotency result: {}", e.getMessage());
        }
    }

    public interface ConnectionProvider {
        Connection getConnection() throws SQLException;
    }

    @FunctionalInterface
    public interface Operation<T> {
        T execute() throws Exception;
    }

    public record IdempotencyResult<T>(
        boolean success,
        String key,
        T result,
        String error
    ) {
        public static <T> IdempotencyResult<T> success(String key, T result) {
            return new IdempotencyResult<>(true, key, result, null);
        }

        public static <T> IdempotencyResult<T> failure(String key, String error) {
            return new IdempotencyResult<>(false, key, null, error);
        }

        @SuppressWarnings("unchecked")
        public static <T> IdempotencyResult<T> fromJson(String key, String json) {
            try {
                var map = new com.google.gson.Gson().fromJson(json, java.util.Map.class);
                boolean success = (Boolean) map.get("success");
                Object result = map.get("result");
                String error = (String) map.get("error");
                return new IdempotencyResult<T>(success, key, (T) result, error);
            } catch (Exception e) {
                return failure(key, "Parse error");
            }
        }

        public String toJson() {
            return new com.google.gson.Gson().toJson(this);
        }
    }

    private record IdempotencyEntry(String key, IdempotencyResult<?> result, long createdAt) {
        public IdempotencyEntry(String key, IdempotencyResult<?> result) {
            this(key, result, System.currentTimeMillis());
        }

        public boolean isExpired() {
            return System.currentTimeMillis() - createdAt > CACHE_TTL_MS;
        }

        @SuppressWarnings("unchecked")
        public <T> IdempotencyResult<T> asResult() {
            return (IdempotencyResult<T>) result;
        }
    }
}
