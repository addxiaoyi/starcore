package dev.starcore.starcore.module.officer;

import dev.starcore.starcore.core.database.DatabaseService;
import dev.starcore.starcore.core.persistence.PersistenceService;
import dev.starcore.starcore.core.storage.AbstractModuleStateStorage;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

final class SqlOfficerStateStorage extends AbstractModuleStateStorage implements OfficerStateStorage {

    private static final String FILE_NAME = "officers.properties";
    private static final String TABLE_NAME = "starcore_officer_state";

    SqlOfficerStateStorage(String namespace, DatabaseService databaseService, PersistenceService persistenceService, Logger logger) {
        super(namespace, databaseService, persistenceService, logger);
    }

    // 测试构造函数
    SqlOfficerStateStorage(DataSource dataSource, Properties legacyProperties, Logger logger) {
        this("test", mockDatabaseService(dataSource), mockPersistenceService(legacyProperties), logger);
    }

    private static DatabaseService mockDatabaseService(DataSource ds) {
        return new DatabaseService(null, null) {
            @Override
            public synchronized Optional<DataSource> dataSource() {
                return Optional.of(ds);
            }
            @Override
            public synchronized boolean isRunning() {
                return true;
            }
        };
    }

    private static PersistenceService mockPersistenceService(Properties props) {
        return new PersistenceService(null, null) {
            @Override
            public Properties loadProperties(String namespace, String fileName) {
                return props;
            }
            @Override
            public void saveProperties(String namespace, String fileName, Properties properties) {
                // 测试环境：不做实际保存
            }
            @Override
            public CompletableFuture<Void> savePropertiesAsync(String namespace, String fileName, Properties properties) {
                return CompletableFuture.completedFuture(null);
            }
        };
    }

    @Override
    protected String getTableName() {
        return TABLE_NAME;
    }

    @Override
    protected String getFileName() {
        return FILE_NAME;
    }

    @Override
    protected void ensureTable(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE IF NOT EXISTS starcore_officer_state (
                    property_key VARCHAR(191) PRIMARY KEY,
                    property_value TEXT NOT NULL
                )
                """);
        }
    }

    @Override
    protected Properties loadFromDatabase(Connection connection) throws SQLException {
        Properties properties = new Properties();
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT property_key, property_value
            FROM starcore_officer_state
            ORDER BY property_key
            """);
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                properties.setProperty(rows.getString("property_key"), rows.getString("property_value"));
            }
        }
        return properties;
    }

    @Override
    protected void writeSnapshot(Connection connection, Properties properties) throws SQLException {
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (Statement delete = connection.createStatement()) {
            delete.execute("DELETE FROM " + TABLE_NAME);
            try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO starcore_officer_state (property_key, property_value)
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
}
