package dev.starcore.starcore.module.technology;

import dev.starcore.starcore.core.database.DatabaseService;
import dev.starcore.starcore.core.persistence.PersistenceService;
import dev.starcore.starcore.core.storage.AbstractModuleStateStorage;

import java.util.Properties;
import java.util.logging.Logger;

/**
 * 数据库感知的研究状态存储
 * 优先使用数据库，失败时降级到文件存储
 */
final class DatabaseAwareResearchStateStorage extends AbstractModuleStateStorage implements ResearchStateStorage {

    private static final String FILE_NAME = "research.properties";
    private static final String TABLE_NAME = "starcore_technology_research";

    private volatile boolean usingSql = false;

    DatabaseAwareResearchStateStorage(String namespace, DatabaseService databaseService,
                                       PersistenceService persistenceService, Logger logger) {
        super(namespace, databaseService, persistenceService, logger);
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
    protected void ensureTable(java.sql.Connection connection) throws java.sql.SQLException {
        try (java.sql.Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE IF NOT EXISTS starcore_technology_research (
                    property_key VARCHAR(191) PRIMARY KEY,
                    property_value TEXT NOT NULL
                )
                """);
        }
    }

    @Override
    protected Properties loadFromDatabase(java.sql.Connection connection) throws java.sql.SQLException {
        Properties properties = new Properties();
        try (java.sql.PreparedStatement statement = connection.prepareStatement("""
            SELECT property_key, property_value
            FROM starcore_technology_research
            ORDER BY property_key
            """);
             java.sql.ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                properties.setProperty(rows.getString("property_key"), rows.getString("property_value"));
            }
        }
        usingSql = true;
        return properties;
    }

    @Override
    protected void writeSnapshot(java.sql.Connection connection, Properties properties) throws java.sql.SQLException {
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (java.sql.Statement delete = connection.createStatement()) {
            delete.execute("DELETE FROM " + TABLE_NAME);
            try (java.sql.PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO starcore_technology_research (property_key, property_value)
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

    /**
     * 检查是否使用 SQL 模式
     */
    public boolean isUsingSql() {
        return usingSql;
    }
}
