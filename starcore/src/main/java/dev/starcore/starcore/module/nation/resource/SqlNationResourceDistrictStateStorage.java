package dev.starcore.starcore.module.nation.resource;

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

final class SqlNationResourceDistrictStateStorage implements NationResourceDistrictStateStorage {
    private static final String FILE_NAME = "resource-districts.properties";
    private static final String TABLE_NAME = "starcore_nation_resource_district_state";

    private final Supplier<Optional<DataSource>> dataSourceSupplier;
    private final Supplier<Properties> legacyPropertiesSupplier;
    private final Logger logger;
    private final Object saveLock = new Object();
    private CompletableFuture<Void> pendingSave = CompletableFuture.completedFuture(null);

    SqlNationResourceDistrictStateStorage(String namespace, DatabaseService databaseService, PersistenceService persistenceService, Logger logger) {
        this(
            databaseService::dataSource,
            () -> persistenceService.loadProperties(namespace, FILE_NAME),
            logger
        );
    }

    SqlNationResourceDistrictStateStorage(
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
            logger.info("Imported legacy STARCORE nation resource district state into SQL storage.");
            return legacy;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to load SQL nation resource district state", exception);
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
                .thenRunAsync(() -> writeSnapshot(snapshot));
        }
    }

    private Properties loadFromDatabase(Connection connection) throws SQLException {
        Properties properties = new Properties();
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT property_key, property_value
            FROM starcore_nation_resource_district_state
            ORDER BY property_key
            """);
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                properties.setProperty(rows.getString("property_key"), rows.getString("property_value"));
            }
        }
        return properties;
    }

    private void ensureTable(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE IF NOT EXISTS starcore_nation_resource_district_state (
                    property_key VARCHAR(191) PRIMARY KEY,
                    property_value TEXT NOT NULL
                )
                """);
        }
    }

    private void writeSnapshot(Properties properties) {
        try (Connection connection = openConnection()) {
            ensureTable(connection);
            writeSnapshot(connection, properties);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to save SQL nation resource district state", exception);
        }
    }

    private void writeSnapshot(Connection connection, Properties properties) throws SQLException {
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (Statement delete = connection.createStatement()) {
            delete.execute("DELETE FROM " + TABLE_NAME);
            try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO starcore_nation_resource_district_state (property_key, property_value)
                VALUES (?, ?)
                """)) {
                for (String key : properties.stringPropertyNames()) {
                    insert.setString(1, key);
                    insert.setString(2, properties.getProperty(key));
                    insert.addBatch();
                }
                insert.executeBatch();
            }
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
            logger.log(Level.SEVERE, "Unable to finish queued SQL nation resource district save", exception);
        }
    }

    private static Properties copy(Properties source) {
        Properties properties = new Properties();
        properties.putAll(source);
        return properties;
    }

    /**
     * 测试专用构造函数
     */
    SqlNationResourceDistrictStateStorage(DataSource dataSource, Properties legacyProperties, Logger logger) {
        this(
            "test",
            new TestDatabaseServiceWrapper(dataSource),
            new TestPersistenceServiceWrapper(legacyProperties),
            logger
        );
    }

    /**
     * 测试用 DatabaseService 包装器
     */
    private static class TestDatabaseServiceWrapper extends DatabaseService {
        private final DataSource dataSource;

        TestDatabaseServiceWrapper(DataSource dataSource) {
            super(null, null);
            this.dataSource = dataSource;
        }

        @Override
        public synchronized java.util.Optional<DataSource> dataSource() {
            return java.util.Optional.of(dataSource);
        }

        @Override
        public synchronized boolean isRunning() {
            return true;
        }
    }

    /**
     * 测试用 PersistenceService 包装器
     */
    private static class TestPersistenceServiceWrapper extends PersistenceService {
        private final Properties properties;

        TestPersistenceServiceWrapper(Properties properties) {
            super(null, null);
            this.properties = properties;
        }

        @Override
        public Properties loadProperties(String namespace, String fileName) {
            return properties;
        }

        @Override
        public void saveProperties(String namespace, String fileName, Properties properties) {
            // 测试环境：不做实际保存
        }

        @Override
        public CompletableFuture<Void> savePropertiesAsync(String namespace, String fileName, Properties properties) {
            return CompletableFuture.completedFuture(null);
        }
    }
}
