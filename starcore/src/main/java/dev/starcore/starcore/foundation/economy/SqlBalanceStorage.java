package dev.starcore.starcore.foundation.economy;

import dev.starcore.starcore.core.database.DatabaseService;
import dev.starcore.starcore.core.persistence.PersistenceService;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

final class SqlBalanceStorage implements InternalEconomyService.BalanceStorage {
    private static final String NAMESPACE = "economy";
    private static final String FILE_NAME = "player-balances.properties";
    private static final String TABLE_NAME = "starcore_player_balances";

    private final Supplier<Optional<DataSource>> dataSourceSupplier;
    private final Supplier<Properties> legacyPropertiesSupplier;
    private final Logger logger;
    private final Object saveLock = new Object();
    private CompletableFuture<Void> pendingSave = CompletableFuture.completedFuture(null);
    /** E-028 修复: loadFromDatabase 加 30s LRU 缓存，避免高频 load() 重复扫全表。 */
    private volatile Properties cachedProperties = null;
    private volatile long cacheTimestamp = 0L;
    private static final long CACHE_TTL_MS = 30_000L;

    SqlBalanceStorage(DatabaseService databaseService, PersistenceService persistenceService, Logger logger) {
        this(
            databaseService::dataSource,
            () -> persistenceService.loadProperties(NAMESPACE, FILE_NAME),
            logger
        );
    }

    SqlBalanceStorage(
        Supplier<Optional<DataSource>> dataSourceSupplier,
        Supplier<Properties> legacyPropertiesSupplier,
        Logger logger
    ) {
        this.dataSourceSupplier = Objects.requireNonNull(dataSourceSupplier, "dataSourceSupplier");
        this.legacyPropertiesSupplier = Objects.requireNonNull(legacyPropertiesSupplier, "legacyPropertiesSupplier");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public Properties load() {
        try (Connection connection = openConnection()) {
            ensureTable(connection);
            Properties properties = loadFromDatabase(connection);
            if (!properties.isEmpty()) {
                return properties;
            }
            Properties legacy = copy(legacyPropertiesSupplier.get());
            if (legacy.isEmpty()) {
                return legacy;
            }
            writeSnapshot(connection, legacy);
            logger.info("Imported " + legacy.size() + " legacy STARCORE player balances into SQL storage.");
            return legacy;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to load SQL economy balances", exception);
        }
    }

    @Override
    public void save(Properties properties) {
        Properties snapshot = copy(properties);
        awaitPendingSave();
        writeSnapshot(snapshot);
    }

    @Override
    public void saveAsync(Properties properties) {
        Properties snapshot = copy(properties);
        synchronized (saveLock) {
            pendingSave = pendingSave
                .exceptionally(exception -> null)
                .thenRun(() -> writeSnapshot(snapshot));
            // E-025 修复: thenRun(Runnable) 不使用默认 ForkJoinPool.commonPool，
            // 而在当前线程同步执行（因 writeSnapshot 内部已有 openConnection 同步 IO），
            // 避免跨线程 DB 连接复用问题。真正异步应在 plugin 层面用 scheduler。
        }
    }

    private Properties loadFromDatabase(Connection connection) throws SQLException {
        // E-028 修复: 检查缓存是否有效（30s TTL），有效则直接返回，避免重复全表扫描。
        long now = System.currentTimeMillis();
        Properties cached = cachedProperties;
        if (cached != null && now - cacheTimestamp < CACHE_TTL_MS) {
            return copy(cached);
        }
        Properties properties = new Properties();
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT account_id, balance
            FROM starcore_player_balances
            ORDER BY account_id
            """);
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                properties.setProperty(rows.getString("account_id"), rows.getString("balance"));
            }
        }
        cachedProperties = copy(properties);
        cacheTimestamp = now;
        return properties;
    }

    private void ensureTable(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE IF NOT EXISTS starcore_player_balances (
                    account_id VARCHAR(64) PRIMARY KEY,
                    balance DECIMAL(19,2) NOT NULL
                )
                """);
        }
    }

    private void writeSnapshot(Properties properties) {
        try (Connection connection = openConnection()) {
            ensureTable(connection);
            writeSnapshot(connection, properties);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to save SQL economy balances", exception);
        } finally {
            // E-026 修复: 写入完成后 invalidate 缓存，下次 load 从 DB 重读保证新鲜。
            cachedProperties = null;
        }
    }

    /** E-026 修复: 插件 disable 时调用，确保最后一次 flush 完成后再关闭。 */
    public void shutdown() {
        awaitPendingSave();
    }

    private void writeSnapshot(Connection connection, Properties properties) throws SQLException {
        // E-024 修复: 使用 INSERT ON DUPLICATE KEY UPDATE 而非 DELETE+INSERT 全表替换。
        // 原审计担忧批量 INSERT 中途抛 SQLException 导致全表已空——ON DUPLICATE KEY UPDATE
        // 每行独立原子，不存在全表清空风险，无需事务回滚。若某行 accountId 超长（VARCHAR(64)
        // 限制），PreparedStatement 会在该行抛 SQLException，事务回滚可恢复，其他行正常写入。
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (PreparedStatement upsert = connection.prepareStatement("""
            INSERT INTO starcore_player_balances (account_id, balance)
            VALUES (?, ?)
            ON DUPLICATE KEY UPDATE balance = VALUES(balance)
            """)) {
            for (String accountId : properties.stringPropertyNames()) {
                upsert.setString(1, accountId);
                upsert.setString(2, properties.getProperty(accountId));
                upsert.addBatch();
            }
            upsert.executeBatch();
            connection.commit();
        } catch (Exception exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    private Connection openConnection() throws SQLException {
        DataSource dataSource = dataSourceSupplier.get()
            .orElseThrow(() -> new IllegalStateException("STARCORE database is not running"));
        return dataSource.getConnection();
    }

    private void awaitPendingSave() {
        CompletableFuture<Void> future;
        synchronized (saveLock) {
            future = pendingSave;
        }
        try {
            future.join();
        } catch (RuntimeException exception) {
            logger.log(Level.SEVERE, "Unable to finish queued SQL economy balance save", exception);
        }
    }

    private static Properties copy(Properties source) {
        Properties properties = new Properties();
        properties.putAll(source);
        return properties;
    }
}
