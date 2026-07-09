package dev.starcore.starcore.module.nation;
import java.util.Optional;

import dev.starcore.starcore.core.database.DatabaseService;
import dev.starcore.starcore.core.persistence.PersistenceService;
import dev.starcore.starcore.core.storage.AbstractModuleStateStorage;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Nation 模块的 SQL 存储实现
 *
 * 使用 AbstractModuleStateStorage 抽象基类，消除重复代码
 */
final class SqlNationStateStorage extends AbstractModuleStateStorage implements NationStateStorage {

    private static final String FILE_NAME = "nations.properties";
    private static final String TABLE_NAME = "starcore_nation_state";

    SqlNationStateStorage(String namespace, DatabaseService databaseService, PersistenceService persistenceService, Logger logger) {
        super(namespace, databaseService, persistenceService, logger);
    }

    /**
     * 测试专用构造函数
     *
     * @param dataSource 测试数据源
     * @param legacyProperties 遗留 Properties 数据
     * @param logger 日志记录器
     */
    SqlNationStateStorage(DataSource dataSource, Properties legacyProperties, Logger logger) {
        this(
            "test",
            new TestDatabaseServiceWrapper(dataSource),
            new TestPersistenceServiceWrapper(legacyProperties),
            logger
        );
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
            statement.execute("CREATE TABLE IF NOT EXISTS starcore_nation_state (" +
                "property_key VARCHAR(191) PRIMARY KEY, " +
                "property_value TEXT NOT NULL)");
        }
    }

    @Override
    protected Properties loadFromDatabase(Connection connection) throws SQLException {
        Properties properties = new Properties();
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT property_key, property_value FROM starcore_nation_state ORDER BY property_key");
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
            try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO starcore_nation_state (property_key, property_value) VALUES (?, ?)")) {
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
