package dev.starcore.starcore.zone;

import dev.starcore.starcore.core.database.DatabaseService;
import dev.starcore.starcore.core.module.ModuleMetadata;
import dev.starcore.starcore.core.persistence.PersistenceService;
import dev.starcore.starcore.core.scheduler.StarCoreScheduler;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * 数据库感知的经济区状态存储
 */
public final class DatabaseAwareZoneStateStorage implements ZoneStateStorage {

    private static final String TABLE_NAME = "zone_state";
    private static final String KEY_COLUMN = "config_key";
    private static final String VALUE_COLUMN = "config_value";

    private final String namespace;
    private final DatabaseService databaseService;
    private final PersistenceService persistenceService;
    private StarCoreScheduler scheduler;
    private final Logger logger;

    public DatabaseAwareZoneStateStorage(String namespace, DatabaseService databaseService,
                                         PersistenceService persistenceService, Logger logger) {
        this.namespace = namespace;
        this.databaseService = databaseService;
        this.persistenceService = persistenceService;
        this.scheduler = null; // Will be set by ZoneModule
        this.logger = logger;
        ensureTable();
    }

    public void setScheduler(StarCoreScheduler scheduler) {
        this.scheduler = scheduler;
    }

    private void ensureTable() {
        String sql = "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
            KEY_COLUMN + " VARCHAR(255) PRIMARY KEY, " +
            VALUE_COLUMN + " TEXT)";

        databaseService.execute(sql);
    }

    @Override
    public Properties load() {
        Properties props = new Properties();
        String sql = "SELECT " + KEY_COLUMN + ", " + VALUE_COLUMN + " FROM " + TABLE_NAME + " WHERE " + KEY_COLUMN + " = ?";

        databaseService.query(sql, rs -> {
            try {
                while (rs.next()) {
                    String key = rs.getString(KEY_COLUMN);
                    String value = rs.getString(VALUE_COLUMN);
                    if (value != null) {
                        // Base64解码
                        try {
                            byte[] decoded = Base64Coder.decode(value);
                            props.setProperty(key, new String(decoded, StandardCharsets.UTF_8));
                        } catch (Exception e) {
                            props.setProperty(key, value);
                        }
                    }
                }
            } catch (java.sql.SQLException e) {
                // Handle ResultSet errors
            }
            return null;
        }, namespace);

        return props;
    }

    @Override
    public void save(Properties properties) {
        if (properties == null || properties.isEmpty()) {
            return;
        }

        for (String key : properties.stringPropertyNames()) {
            String value = properties.getProperty(key);
            // Base64编码
            String encoded = Base64Coder.encodeString(value);
            String sql = "INSERT OR REPLACE INTO " + TABLE_NAME + " (" + KEY_COLUMN + ", " + VALUE_COLUMN + ") VALUES (?, ?)";
            databaseService.update(sql, key, encoded);
        }
    }

    @Override
    public void saveAsync(Properties properties) {
        if (properties == null || properties.isEmpty()) {
            return;
        }

        // 使用同步方式保存（简化处理）
        save(properties);
    }
}
