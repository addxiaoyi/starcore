package dev.starcore.starcore.core.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import dev.starcore.starcore.core.config.ConfigurationService;
import dev.starcore.starcore.core.monitoring.MigrationMonitoringService;
import org.bukkit.plugin.java.JavaPlugin;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DatabaseService {
    private final JavaPlugin plugin;
    private final ConfigurationService configuration;
    private HikariDataSource dataSource;
    private DatabaseSettings settings = DatabaseSettings.disabled();
    private DatabaseMigrationService migrationService;
    private MigrationMonitoringService monitoringService;
    private final Logger logger;
    private final ExecutorService queryExecutor;
    private final AtomicLong queryCount = new AtomicLong(0);
    private final AtomicLong slowQueryCount = new AtomicLong(0);
    private volatile long slowQueryThresholdMs = 100; // Default 100ms threshold

    // Batch operation settings
    private static final int BATCH_SIZE = 100;
    private static final int BATCH_TIMEOUT_SECONDS = 30;

    public DatabaseService(JavaPlugin plugin, ConfigurationService configuration) {
        this.plugin = plugin;
        this.configuration = configuration;
        this.logger = plugin != null ? plugin.getLogger() : Logger.getAnonymousLogger();
        this.monitoringService = new MigrationMonitoringService(logger);
        // E-060: 原固定 2 线程在 webmap/每秒大量请求场景下排队阻塞。
        // 改用可伸缩线程池:核心 4 线程,最大 16 线程,60s 空闲回收,
        // SynchronousQueue 让超出的任务立即新建线程而不是排队（避免主线程被 CallerRuns 阻塞）。
        this.queryExecutor = new java.util.concurrent.ThreadPoolExecutor(
            4, 16,
            60L, TimeUnit.SECONDS,
            new java.util.concurrent.SynchronousQueue<>(),
            r -> {
                Thread t = new Thread(r, "starcore-db-query");
                t.setDaemon(true);
                return t;
            },
            new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    /**
     * Sets the slow query threshold in milliseconds.
     * Queries taking longer than this will be logged as warnings.
     */
    public void setSlowQueryThreshold(long thresholdMs) {
        this.slowQueryThresholdMs = thresholdMs;
    }

    /**
     * Gets the current slow query threshold.
     */
    public long getSlowQueryThreshold() {
        return slowQueryThresholdMs;
    }

    /**
     * Gets the number of queries executed.
     */
    public long getQueryCount() {
        return queryCount.get();
    }

    /**
     * Gets the number of slow queries recorded.
     */
    public long getSlowQueryCount() {
        return slowQueryCount.get();
    }

    /**
     * Gets connection pool statistics.
     */
    public PoolStats getPoolStats() {
        HikariDataSource ds = this.dataSource;
        if (ds == null || ds.isClosed()) {
            return PoolStats.DISABLED;
        }
        try {
            HikariPoolMXBean pool = ds.getHikariPoolMXBean();
            return new PoolStats(
                pool.getActiveConnections(),
                pool.getIdleConnections(),
                pool.getTotalConnections(),
                pool.getThreadsAwaitingConnection(),
                ds.getHikariConfigMXBean().getMaximumPoolSize()
            );
        } catch (Exception e) {
            return PoolStats.DISABLED;
        }
    }

    private java.util.logging.Logger getLogger() {
        return logger;
    }

    public synchronized void start() {
        stop();
        this.settings = DatabaseSettings.from(configuration, plugin.getDataFolder().toPath());
        if (!settings.enabled()) {
            getLogger().info("STARCORE database pool disabled by config.");
            return;
        }
        HikariDataSource opened = null;
        try {
            ensureStorageReady(settings);
            opened = new HikariDataSource(hikariConfig(settings));
            initialize(opened, settings);
            this.dataSource = opened;

            // 执行数据库迁移
            runMigrations(opened);

            getLogger().info("STARCORE database ready: " + summary());
        } catch (RuntimeException | SQLException | IOException exception) {
            closeQuietly(opened);
            dataSource = null;
            getLogger().log(Level.SEVERE, "STARCORE database startup failed: " + exception.getMessage(), exception);
            // E-055: 原 failFast=false 时静默 deleteQuietly 后 dataSource=null,
            // 调用方后续 execute() 直接 return 不报错,query 抛 IllegalStateException,
            // 数据更新静默丢失但对玩家表现为"成功"。这里用 warning 级别持续告警每次调用,
            // 并标记 failQuietly 状态,使 execute/update 路径也能记录告警。
            this.failQuietly = true;
            if (settings.failFast()) {
                throw new IllegalStateException("STARCORE database startup failed", exception);
            }
        }
    }

    // E-055: 启动失败但 failFast=false 时持续给后续 execute/update 调用记 warning
    private volatile boolean failQuietly = false;

    public synchronized void restart() {
        HikariDataSource previousDataSource = dataSource;
        DatabaseSettings previousSettings = settings;
        HikariDataSource opened = null;
        DatabaseSettings nextSettings = DatabaseSettings.from(configuration, plugin.getDataFolder().toPath());
        try {
            if (!nextSettings.enabled()) {
                if (isRunning()) {
                    getLogger().warning("STARCORE database reload requested disable, but the active pool will stay online until the next full restart.");
                    return;
                }
                stop();
                settings = nextSettings;
                getLogger().info("STARCORE database pool disabled by config.");
                return;
            }
            ensureStorageReady(nextSettings);
            opened = new HikariDataSource(hikariConfig(nextSettings));
            initialize(opened, nextSettings);
            dataSource = opened;
            settings = nextSettings;
            if (previousDataSource != null && previousDataSource != opened) {
                closeQuietly(previousDataSource);
            }
            getLogger().info("STARCORE database reloaded: " + summary());
        } catch (RuntimeException | SQLException | IOException exception) {
            closeQuietly(opened);
            dataSource = previousDataSource;
            settings = previousSettings;
            getLogger().log(Level.SEVERE, "STARCORE database reload failed; keeping previous pool active when possible.", exception);
        }
    }

    public synchronized void stop() {
        if (dataSource == null) {
            return;
        }
        closeQuietly(dataSource);
        dataSource = null;
    }

    public synchronized boolean isRunning() {
        return dataSource != null && !dataSource.isClosed();
    }

    public synchronized Optional<DataSource> dataSource() {
        return Optional.ofNullable(dataSource);
    }

    /**
     * Get a database connection directly.
     * Note: Caller is responsible for closing the connection.
     * @return A database connection
     * @throws IllegalStateException if database is not initialized
     */
    public Connection getConnection() {
        try {
            return dataSource.getConnection();
        } catch (Exception e) {
            throw new RuntimeException("Failed to get database connection", e);
        }
    }

    public synchronized String summary() {
        return settings.summary(isRunning());
    }

    public synchronized DatabaseSettings settings() {
        return settings;
    }

    private HikariConfig hikariConfig(DatabaseSettings settings) {
        HikariConfig config = new HikariConfig();
        config.setPoolName("STARCORE-" + settings.type().displayName());
        config.setJdbcUrl(settings.jdbcUrl());
        config.setDriverClassName(settings.driverClassName());
        config.setMaximumPoolSize(settings.pool().maximumPoolSize());
        config.setMinimumIdle(Math.min(settings.pool().minimumIdle(), settings.pool().maximumPoolSize()));
        config.setConnectionTimeout(settings.pool().connectionTimeoutMs());
        config.setIdleTimeout(settings.pool().idleTimeoutMs());
        config.setMaxLifetime(settings.pool().maxLifetimeMs());
        config.setValidationTimeout(settings.pool().validationTimeoutMs());
        if (settings.pool().keepaliveTimeMs() > 0L) {
            config.setKeepaliveTime(settings.pool().keepaliveTimeMs());
        }
        if (settings.pool().leakDetectionThresholdMs() > 0L) {
            config.setLeakDetectionThreshold(settings.pool().leakDetectionThresholdMs());
        }
        if (settings.type() == DatabaseType.MYSQL) {
            config.setUsername(settings.mysqlUsername());
            config.setPassword(settings.mysqlPassword());
            // E-061: 明文密码直接来自 settings.mysqlPassword(),实际缓解责任在 settings 层
            // (支持环境变量/secret placeholder 解析,推荐通过 STARCORE_DB_PASSWORD 环境变量注入)。
            // 这里仅确保 Hikari 不把 jdbcUrl/credentials 写入日志,默认就不会,保持原行为。
        }
        return config;
    }

    private void ensureStorageReady(DatabaseSettings settings) throws IOException {
        if (settings.type() != DatabaseType.SQLITE) {
            return;
        }
        Path parent = settings.sqliteFile().toAbsolutePath().normalize().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    private void initialize(HikariDataSource dataSource, DatabaseSettings settings) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            if (settings.type() == DatabaseType.SQLITE) {
                initializeSqlite(connection);
            }
            createMetadataTable(connection);
            putMetadata(connection, "schema_version", "1");
            putMetadata(connection, "database_type", settings.type().name().toLowerCase());
            putMetadata(connection, "last_started_at", Instant.now().toString());
        }
    }

    private void initializeSqlite(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA foreign_keys=ON");
            statement.execute("PRAGMA busy_timeout=5000");
        }
    }

    private void createMetadataTable(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE IF NOT EXISTS starcore_metadata (
                    metadata_key VARCHAR(96) PRIMARY KEY,
                    metadata_value VARCHAR(255) NOT NULL
                )
                """);
        }
    }

    private void putMetadata(Connection connection, String key, String value) throws SQLException {
        // E-058: 原 DELETE 后 INSERT 两步非事务,高并发同 key 修改可能 DELETE 后双方都 INSERT 引发主键冲突。
        // 改用 INSERT ON DUPLICATE KEY UPDATE(等价 MERGE),单语句原子。
        // MySQL/SQLite 都支持 INSERT ... ON CONFLICT/ON DUPLICATE KEY UPDATE;
        // SQLite 3.24+ 支持 UPSERT,如果旧版本不支持会抛异常回退到事务化 DELETE+INSERT。
        try {
            String upsertSql;
            if (settings != null && settings.type() == DatabaseType.SQLITE) {
                // SQLite UPSERT 语法 (3.24+)
                upsertSql = "INSERT INTO starcore_metadata (metadata_key, metadata_value) VALUES (?, ?) "
                    + "ON CONFLICT(metadata_key) DO UPDATE SET metadata_value = excluded.metadata_value";
            } else {
                // MySQL ON DUPLICATE KEY UPDATE
                upsertSql = "INSERT INTO starcore_metadata (metadata_key, metadata_value) VALUES (?, ?) "
                    + "ON DUPLICATE KEY UPDATE metadata_value = VALUES(metadata_value)";
            }
            try (PreparedStatement upsert = connection.prepareStatement(upsertSql)) {
                upsert.setString(1, key);
                upsert.setString(2, value);
                upsert.executeUpdate();
            }
        } catch (SQLException tryException) {
            // E-058: fallback 包事务化 DELETE+INSERT。某些旧 SQLite 可能不支持 UPSERT。
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement delete = connection.prepareStatement("DELETE FROM starcore_metadata WHERE metadata_key = ?")) {
                    delete.setString(1, key);
                    delete.executeUpdate();
                }
                try (PreparedStatement insert = connection.prepareStatement("INSERT INTO starcore_metadata (metadata_key, metadata_value) VALUES (?, ?)")) {
                    insert.setString(1, key);
                    insert.setString(2, value);
                    insert.executeUpdate();
                }
                connection.commit();
            } catch (SQLException innerEx) {
                connection.rollback();
                throw innerEx;
            } finally {
                try { connection.setAutoCommit(true); } catch (SQLException ignore) { /* E-058: reset 失败不抛 */ }
            }
        }
    }

    /**
     * 执行数据库迁移
     */
    private void runMigrations(HikariDataSource dataSource) {
        try {
            this.migrationService = new DatabaseMigrationService(
                dataSource,
                settings.type().name(),
                getLogger()
            );

            long startTime = System.currentTimeMillis();
            DatabaseMigrationService.MigrationResult result = migrationService.migrate();
            long duration = System.currentTimeMillis() - startTime;

            // 记录迁移监控指标
            monitoringService.recordFlywayMigration(
                result.migrationsExecuted,
                duration,
                result.success
            );

            if (result.success && result.migrationsExecuted > 0) {
                // 更新元数据表中的版本信息
                try (Connection conn = dataSource.getConnection()) {
                    putMetadata(conn, "schema_version", result.targetVersion);
                    putMetadata(conn, "last_migrated_at", Instant.now().toString());
                }
            }

        } catch (Exception e) {
            getLogger().warning("数据库迁移过程中发生错误: " + e.getMessage());

            // 记录迁移失败
            monitoringService.recordFlywayMigration(0, 0, false);

            if (settings.failFast()) {
                throw new IllegalStateException("Database migration failed", e);
            }
        }
    }

    /**
     * 获取迁移服务
     */
    public Optional<DatabaseMigrationService> migrationService() {
        return Optional.ofNullable(migrationService);
    }

    private void closeQuietly(HikariDataSource source) {
        if (source == null) {
            return;
        }
        try {
            source.close();
        } catch (RuntimeException ignored) {
        }
    }

    /**
     * Executes a query asynchronously and returns a CompletableFuture.
     * Queries exceeding the slow query threshold are logged.
     *
     * @param querySupplier the query to execute
     * @param <T> the result type
     * @return CompletableFuture with the query result
     */
    public <T> CompletableFuture<T> executeQueryAsync(java.util.function.Supplier<T> querySupplier) {
        queryCount.incrementAndGet();
        long startMs = System.currentTimeMillis();
        return CompletableFuture.supplyAsync(querySupplier, queryExecutor)
            .whenComplete((result, ex) -> {
                long duration = System.currentTimeMillis() - startMs;
                // E-059: 原代码无论是否慢查询或是否有异常,都在慢查询路径打印 "Slow query detected: " + ex,
                // 没有 ex 时打印 "null" 让人困惑。拆开判定:慢查询和异常分开记录。
                if (ex != null) {
                    getLogger().log(Level.WARNING, "Query execution failed" + (duration > slowQueryThresholdMs ? " (also slow)" : ""), ex);
                    if (duration > slowQueryThresholdMs) {
                        slowQueryCount.incrementAndGet();
                    }
                } else if (duration > slowQueryThresholdMs) {
                    slowQueryCount.incrementAndGet();
                    getLogger().warning("Slow query detected (" + duration + "ms, threshold=" + slowQueryThresholdMs + "ms)");
                }
            });
    }

    /**
     * Executes a batch operation with automatic chunking.
     * <p>
     * E-062: 原实现每批 executeWithConnection 都重新打开 connection,跨批次无统一事务,
     * 任意一批失败前批已提交,部分数据写入不一致。改为单 connection + setAutoCommit(false),
     * 全部批次成功才 commit,任意一批失败则 rollback 全部。
     *
     * @param items items to process
     * @param batchProcessor processor function for each batch
     * @param <T> item type
     * @return true 全部批次成功; false 任一批失败已回滚
     */
    public <T> boolean executeBatch(List<T> items, java.util.function.BiConsumer<List<T>, Connection> batchProcessor) {
        if (items == null || items.isEmpty()) {
            return true;
        }
        if (dataSource == null) {
            if (failQuietly) {
                getLogger().warning("Database executeBatch skipped (not initialized, failQuiet mode); data may be lost");
            }
            return false;
        }
        List<List<T>> batches = partitionList(items, BATCH_SIZE);
        Connection connection = null;
        boolean previousAutoCommit = true;
        try {
            connection = dataSource.getConnection();
            previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            // E-062: 单连接统一事务,所有批次都成功才 commit
            for (List<T> batch : batches) {
                batchProcessor.accept(batch, connection);
            }
            connection.commit();
            return true;
        } catch (Exception e) {
            // E-062: 任意一批失败回滚全部,保证原子性
            if (connection != null) {
                try { connection.rollback(); } catch (SQLException rollbackEx) {
                    getLogger().log(Level.WARNING, "Batch rollback failed", rollbackEx);
                }
            }
            getLogger().log(Level.WARNING, "Database executeBatch failed; all batches rolled back", e);
            return false;
        } finally {
            // E-062: 恢复 autoCommit 状态并关闭连接
            if (connection != null) {
                try {
                    connection.setAutoCommit(previousAutoCommit);
                } catch (SQLException ignore) { /* reset 失败不抛 */ }
                try { connection.close(); } catch (SQLException ignore) { /* close 失败不抛 */ }
            }
        }
    }

    /**
     * Partitions a list into sublists of the specified size.
     */
    private <T> List<List<T>> partitionList(List<T> list, int size) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return partitions;
    }

    /**
     * Executes an operation with a database connection.
     *
     * @param operation the operation to execute
     */
    public void executeWithConnection(java.util.function.Consumer<Connection> operation) {
        if (dataSource == null) {
            throw new IllegalStateException("Database not initialized");
        }
        try (Connection connection = dataSource.getConnection()) {
            operation.accept(connection);
        } catch (Exception e) {
            throw new RuntimeException("Database operation failed", e);
        }
    }

    /**
     * Executes a SQL statement that does not return results (CREATE, INSERT, UPDATE, DELETE).
     * <p>
     * E-056: 警告——此方法接收原始 SQL 字符串并用 Statement.execute(sql) 执行,
     * 如果调用方拼接玩家输入会引入 SQL 注入。仅限 DDL/无变量 SQL 使用。
     * 若需传参,请使用 {@link #execute(String, Object...)}(PreparedStatement)。
     * 本方法已不再静默吞所有异常,失败时记录 warning,以便调用方运维感知。
     *
     * @param sql the SQL statement to execute
     * @return true 表示 SQL 产生了结果集(ResultSet); false 表示更新计数或无结果;
     *         当数据库未初始化或启动降级(failQuietly)时静默返回 false 并记 warning
     */
    public boolean execute(String sql) {
        if (dataSource == null) {
            // E-055/E-056: 启动降级时记录告警,不再静默 return
            if (failQuietly) {
                getLogger().warning("Database execute skipped (not initialized, started in failQuiet mode); data may be lost: " + sql);
            }
            return false;
        }
        try (Connection connection = dataSource.getConnection();
             Statement stmt = connection.createStatement()) {
            return stmt.execute(sql);
        } catch (Exception e) {
            // E-056: 原 catch 静默吞所有错误包括主键冲突/约束失败,调用方完全无感知。
            // 至少记录 warning,以便运维感知。仅"table already exists"是预期 DDL 冲突,降为 info。
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (msg.toLowerCase().contains("already exists")) {
                getLogger().info("DDL skipped (already exists): " + sql);
            } else {
                getLogger().log(Level.WARNING, "Database execute failed (raw SQL, possible injection risk if stitched with user input): " + sql, e);
            }
            return false;
        }
    }

    /**
     * Executes a parameterized SQL statement.
     *
     * @param sql the SQL statement
     * @param params the parameters
     * @return true 表示 SQL 产生了结果集; false 表示更新计数或无结果;
     *         当数据库未初始化或启动降级(failQuietly)时静默返回 false 并记 warning
     */
    public boolean execute(String sql, Object... params) {
        if (dataSource == null) {
            // E-055/E-057: 启动降级时记录告警,不再静默 return
            if (failQuietly) {
                getLogger().warning("Database execute skipped (not initialized, started in failQuiet mode); data may be lost: " + sql);
            }
            return false;
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {
            setParameters(stmt, params);
            return stmt.execute();
        } catch (Exception e) {
            // E-057: 原 catch 静默吞任何 saveData/update 调用失败,玩家数据丢失静默。
            // 至少记录 warning,以便调用方感知;调用方依赖此返回值决定是否重试。
            getLogger().log(Level.WARNING, "Database execute (parameterized) failed: " + sql, e);
            return false;
        }
    }

    /**
     * Executes a query and maps the result set to a value.
     *
     * @param sql the SQL query
     * @param mapper the result set mapper
     * @param <T> the result type
     * @return the mapped result
     */
    public <T> T query(String sql, java.util.function.Function<java.sql.ResultSet, T> mapper) {
        return query(sql, mapper, new Object[]{});
    }

    /**
     * Executes a parameterized query and maps the result set to a value.
     *
     * @param sql the SQL query
     * @param mapper the result set mapper
     * @param params the parameters
     * @param <T> the result type
     * @return the mapped result
     */
    public <T> T query(String sql, java.util.function.Function<java.sql.ResultSet, T> mapper, Object... params) {
        return executeWithConnectionReturning(sql, mapper, params);
    }

    private <T> T executeWithConnectionReturning(String sql, java.util.function.Function<java.sql.ResultSet, T> mapper, Object... params) {
        if (dataSource == null) {
            throw new IllegalStateException("Database not initialized");
        }
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                setParameters(stmt, params);
                try (java.sql.ResultSet rs = stmt.executeQuery()) {
                    return mapper.apply(rs);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Database query failed: " + sql, e);
        }
    }

    /**
     * Executes an update/insert/delete statement and returns the affected row count.
     *
     * @param sql the SQL statement
     * @param params the parameters
     * @return the number of affected rows
     */
    public int update(String sql, Object... params) {
        if (dataSource == null) {
            throw new IllegalStateException("Database not initialized");
        }
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement stmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                setParameters(stmt, params);
                int affected = stmt.executeUpdate();
                // E-063: 原 update 用 RETURN_GENERATED_KEYS 但 stmt.executeUpdate() 后未读取 getGeneratedKeys,
                // 调用方无法获取自增 id。这里把生成键提取并放到 lastGeneratedKeys 线程本地缓存,
                // 提供 getGeneratedKeys() / getLastInsertId() 供调用方按需读取。
                try (java.sql.ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                    List<Long> keys = new ArrayList<>();
                    while (generatedKeys.next()) {
                        try {
                            keys.add(generatedKeys.getLong(1));
                        } catch (SQLException gkEx) {
                            // E-063: 某些 column 可能为非数字类型,跳过
                        }
                    }
                    LAST_GENERATED_KEYS.set(keys);
                } catch (SQLException gkEx) {
                    // E-063: 提取生成键失败不影响 update 返回值,但记录告警
                    getLogger().log(Level.FINE, "Could not retrieve generated keys for update: " + sql, gkEx);
                    LAST_GENERATED_KEYS.remove();
                }
                return affected;
            }
        } catch (Exception e) {
            throw new RuntimeException("Database update failed: " + sql, e);
        }
    }

    // E-063: 上次 update 返回的生成 key 列表(按调用顺序),供调用方在线程本地读取
    private static final ThreadLocal<List<Long>> LAST_GENERATED_KEYS = new ThreadLocal<>();

    /**
     * E-063: 获取最近一次 update 调用返回的自增 key 列表(线程本地),可能为空。
     */
    public List<Long> getLastGeneratedKeys() {
        List<Long> keys = LAST_GENERATED_KEYS.get();
        LAST_GENERATED_KEYS.remove(); // 读后即清,避免下次读到旧值
        return keys == null ? List.of() : keys;
    }

    /**
     * E-063: 获取最近一次 update 调用返回的第一个自增 key,无则返回 -1L。
     */
    public long getLastInsertId() {
        List<Long> keys = getLastGeneratedKeys();
        return keys.isEmpty() ? -1L : keys.get(0);
    }

    private void setParameters(PreparedStatement stmt, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            Object param = params[i];
            if (param == null) {
                stmt.setNull(i + 1, java.sql.Types.NULL);
            } else if (param instanceof String s) {
                stmt.setString(i + 1, s);
            } else if (param instanceof Integer iVal) {
                stmt.setInt(i + 1, iVal);
            } else if (param instanceof Long lVal) {
                stmt.setLong(i + 1, lVal);
            } else if (param instanceof Double dVal) {
                stmt.setDouble(i + 1, dVal);
            } else if (param instanceof Boolean bVal) {
                stmt.setBoolean(i + 1, bVal);
            } else if (param instanceof java.sql.Timestamp ts) {
                stmt.setTimestamp(i + 1, ts);
            } else if (param instanceof java.util.Date date) {
                stmt.setTimestamp(i + 1, new java.sql.Timestamp(date.getTime()));
            } else if (param instanceof java.time.LocalDate ld) {
                stmt.setDate(i + 1, java.sql.Date.valueOf(ld));
            } else {
                stmt.setObject(i + 1, param);
            }
        }
    }

    /**
     * Shuts down the query executor gracefully.
     */
    public void shutdown() {
        queryExecutor.shutdown();
        try {
            if (!queryExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                queryExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            queryExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Pool statistics for monitoring.
     */
    public record PoolStats(
        int activeConnections,
        int idleConnections,
        int totalConnections,
        int threadsAwaitingConnection,
        int maxPoolSize
    ) {
        public static final PoolStats DISABLED = new PoolStats(0, 0, 0, 0, 0);

        public double utilizationPercent() {
            return maxPoolSize > 0 ? (double) activeConnections / maxPoolSize * 100 : 0;
        }
    }
}
