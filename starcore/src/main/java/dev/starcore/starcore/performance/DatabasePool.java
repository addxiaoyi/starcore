package dev.starcore.starcore.performance;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 高性能数据库连接池
 * 使用 HikariCP 实现
 */
public final class DatabasePool {
    private static final Logger LOGGER = Logger.getLogger(DatabasePool.class.getName());
    private HikariDataSource dataSource;
    private final AsyncTaskManager asyncManager;

    public DatabasePool(DatabaseConfig config, AsyncTaskManager asyncManager) {
        this.asyncManager = asyncManager;
        initializePool(config);
    }

    /**
     * 初始化连接池
     */
    private void initializePool(DatabaseConfig config) {
        HikariConfig hikariConfig = new HikariConfig();

        if (config.type == DatabaseType.MYSQL) {
            hikariConfig.setJdbcUrl(String.format(
                "jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC&characterEncoding=utf8",
                config.host, config.port, config.database
            ));
            hikariConfig.setUsername(config.username);
            hikariConfig.setPassword(config.password);
        } else {
            hikariConfig.setJdbcUrl("jdbc:sqlite:" + config.sqliteFile);
        }

        // 连接池配置
        hikariConfig.setMaximumPoolSize(config.maxPoolSize);
        hikariConfig.setMinimumIdle(config.minIdle);
        hikariConfig.setConnectionTimeout(config.connectionTimeout);
        hikariConfig.setIdleTimeout(600000); // 10分钟
        hikariConfig.setMaxLifetime(1800000); // 30分钟

        // 性能优化
        hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        hikariConfig.addDataSourceProperty("useServerPrepStmts", "true");

        // 连接测试
        hikariConfig.setConnectionTestQuery("SELECT 1");

        this.dataSource = new HikariDataSource(hikariConfig);
    }

    /**
     * 获取连接
     */
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    /**
     * 异步执行查询
     */
    public <T> CompletableFuture<T> queryAsync(String sql, ResultSetHandler<T> handler, Object... params) {
        return asyncManager.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                // 设置参数
                for (int i = 0; i < params.length; i++) {
                    stmt.setObject(i + 1, params[i]);
                }

                // 执行查询
                try (ResultSet rs = stmt.executeQuery()) {
                    return handler.handle(rs);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * 异步执行更新
     */
    public CompletableFuture<Integer> updateAsync(String sql, Object... params) {
        return asyncManager.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                // 设置参数
                for (int i = 0; i < params.length; i++) {
                    stmt.setObject(i + 1, params[i]);
                }

                // 执行更新
                return stmt.executeUpdate();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * 批量执行
     */
    public CompletableFuture<int[]> batchAsync(String sql, Object[][] batchParams) {
        return asyncManager.supplyAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                // 添加批量参数
                for (Object[] params : batchParams) {
                    for (int i = 0; i < params.length; i++) {
                        stmt.setObject(i + 1, params[i]);
                    }
                    stmt.addBatch();
                }

                // 执行批量更新
                return stmt.executeBatch();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * 事务执行
     */
    public <T> CompletableFuture<T> transactionAsync(TransactionHandler<T> handler) {
        return asyncManager.supplyAsync(() -> {
            Connection conn = null;
            try {
                conn = getConnection();
                conn.setAutoCommit(false);

                T result = handler.handle(conn);

                conn.commit();
                return result;
            } catch (Exception e) {
                if (conn != null) {
                    try {
                        conn.rollback();
                    } catch (Exception ex) {
                        LOGGER.log(Level.WARNING, "Failed to rollback transaction", ex);
                    }
                }
                throw new RuntimeException(e);
            } finally {
                if (conn != null) {
                    try {
                        conn.setAutoCommit(true);
                        conn.close();
                    } catch (Exception e) {
                        LOGGER.log(Level.WARNING, "Failed to close database connection", e);
                    }
                }
            }
        });
    }

    /**
     * 获取连接池统计
     */
    public PoolStats getStats() {
        return new PoolStats(
            dataSource.getHikariPoolMXBean().getActiveConnections(),
            dataSource.getHikariPoolMXBean().getIdleConnections(),
            dataSource.getHikariPoolMXBean().getTotalConnections(),
            dataSource.getHikariPoolMXBean().getThreadsAwaitingConnection()
        );
    }

    /**
     * 关闭连接池
     */
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    /**
     * 结果集处理器
     */
    @FunctionalInterface
    public interface ResultSetHandler<T> {
        T handle(ResultSet rs) throws SQLException;
    }

    /**
     * 事务处理器
     */
    @FunctionalInterface
    public interface TransactionHandler<T> {
        T handle(Connection conn) throws SQLException;
    }

    /**
     * 数据库配置
     */
    public static class DatabaseConfig {
        public DatabaseType type = DatabaseType.SQLITE;
        public String host = "localhost";
        public int port = 3306;
        public String database = "starcore";
        public String username = "root";
        public String password = "";
        public String sqliteFile = "starcore.db";

        // 优化后的连接池配置（支持150+玩家）
        public int maxPoolSize = 30;      // 从10增加到30
        public int minIdle = 15;          // 从5增加到15
        public long connectionTimeout = 30000;
        public long idleTimeout = 600000;  // 10分钟
        public long maxLifetime = 1800000; // 30分钟

        // 连接池优化配置
        public boolean cachePrepStmts = true;
        public int prepStmtCacheSize = 250;
        public int prepStmtCacheSqlLimit = 2048;
    }

    /**
     * 数据库类型
     */
    public enum DatabaseType {
        MYSQL,
        SQLITE
    }

    /**
     * 连接池统计
     */
    public record PoolStats(
        int activeConnections,
        int idleConnections,
        int totalConnections,
        int waitingThreads
    ) {}
}
