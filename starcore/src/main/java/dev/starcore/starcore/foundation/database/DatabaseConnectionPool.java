package dev.starcore.starcore.foundation.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * 数据库连接池 - SSS级性能优化
 * 使用 HikariCP 提供高性能数据库连接
 */
public final class DatabaseConnectionPool {
    private final HikariDataSource dataSource;
    private static final Logger LOGGER = Logger.getLogger("StarCore-DatabasePool");

    public DatabaseConnectionPool(DatabaseConfig config) {
        HikariConfig hikariConfig = new HikariConfig();

        // 基础配置
        hikariConfig.setJdbcUrl(config.jdbcUrl());
        hikariConfig.setUsername(config.username());
        hikariConfig.setPassword(config.password());
        hikariConfig.setDriverClassName(config.driverClassName());

        // 连接池优化配置（SSS级）
        hikariConfig.setPoolName("STARCORE-DB-Pool");
        hikariConfig.setMaximumPoolSize(config.maxPoolSize());
        hikariConfig.setMinimumIdle(config.minIdle());
        hikariConfig.setConnectionTimeout(TimeUnit.SECONDS.toMillis(10));
        hikariConfig.setIdleTimeout(TimeUnit.MINUTES.toMillis(10));
        hikariConfig.setMaxLifetime(TimeUnit.MINUTES.toMillis(30));
        hikariConfig.setKeepaliveTime(TimeUnit.MINUTES.toMillis(5));

        // 性能优化
        // E-021: 不全局 setAutoCommit(true);HikariCP 默认值为 true,
        // 但移除显式设置让调用方在自己事务中 setAutoCommit(false) 时不会被 Hikari 连接归还状态污染
        hikariConfig.setConnectionTestQuery("SELECT 1");
        hikariConfig.setLeakDetectionThreshold(TimeUnit.SECONDS.toMillis(30));

        // 数据库特定优化
        if (config.jdbcUrl().contains("mysql")) {
            hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
            hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
            hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            hikariConfig.addDataSourceProperty("useServerPrepStmts", "true");
            hikariConfig.addDataSourceProperty("useLocalSessionState", "true");
            hikariConfig.addDataSourceProperty("rewriteBatchedStatements", "true");
            hikariConfig.addDataSourceProperty("cacheResultSetMetadata", "true");
            hikariConfig.addDataSourceProperty("cacheServerConfiguration", "true");
            hikariConfig.addDataSourceProperty("elideSetAutoCommits", "true");
            hikariConfig.addDataSourceProperty("maintainTimeStats", "false");
        }

        HikariDataSource ds;
        // E-023: 构造期 new HikariDataSource 在 DB 不可达时会抛异常,这里捕获并显式记录,
        // 让上层显式调用 shutdown() 关闭半初始化资源
        try {
            ds = new HikariDataSource(hikariConfig);
        } catch (RuntimeException ex) {
            LOGGER.severe("Failed to initialize HikariDataSource: " + ex.getMessage());
            throw ex;
        }
        this.dataSource = ds;
    }

    /**
     * 获取连接
     */
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
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
    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    /**
     * 连接池统计
     */
    public record PoolStats(
        int activeConnections,
        int idleConnections,
        int totalConnections,
        int threadsAwaiting
    ) {
        public String format() {
            return String.format(
                "DB Pool: %d active, %d idle, %d total, %d waiting",
                activeConnections,
                idleConnections,
                totalConnections,
                threadsAwaiting
            );
        }
    }

    /**
     * 数据库配置
     */
    public record DatabaseConfig(
        String jdbcUrl,
        String username,
        String password,
        String driverClassName,
        int maxPoolSize,
        int minIdle
    ) {
        /**
         * MySQL 配置
         * E-022: 默认启用 SSL,避免明文凭证被嗅探;allowPublicKeyRetrieval 保留 true 兼容旧服务端
         */
        public static DatabaseConfig mysql(
            String host,
            int port,
            String database,
            String username,
            String password
        ) {
            return new DatabaseConfig(
                String.format("jdbc:mysql://%s:%d/%s?useSSL=true&allowPublicKeyRetrieval=true&verifyServerCertificate=false",
                    host, port, database),
                username,
                password,
                "com.mysql.cj.jdbc.Driver",
                10,
                2
            );
        }

        /**
         * SQLite 配置
         */
        public static DatabaseConfig sqlite(String filepath) {
            return new DatabaseConfig(
                "jdbc:sqlite:" + filepath,
                "",
                "",
                "org.sqlite.JDBC",
                1,
                1
            );
        }
    }
}
