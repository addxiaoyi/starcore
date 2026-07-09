package dev.starcore.starcore.module.technology;

import dev.starcore.starcore.core.database.DatabaseService;
import dev.starcore.starcore.core.persistence.PersistenceService;
import dev.starcore.starcore.core.storage.AbstractModuleStateStorage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * 研究进度的 SQL 存储实现
 * 使用 AbstractModuleStateStorage 抽象基类
 */
final class SqlResearchStateStorage extends AbstractModuleStateStorage implements ResearchStateStorage {

    private static final String FILE_NAME = "research.properties";
    private static final String TABLE_NAME = "starcore_technology_research";

    SqlResearchStateStorage(String namespace, DatabaseService databaseService, PersistenceService persistenceService, Logger logger) {
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
    protected void ensureTable(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE IF NOT EXISTS starcore_technology_research (
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
            FROM starcore_technology_research
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
}
