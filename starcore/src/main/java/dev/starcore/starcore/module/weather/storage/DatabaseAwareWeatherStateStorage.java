package dev.starcore.starcore.module.weather.storage;

import dev.starcore.starcore.core.database.DatabaseService;
import dev.starcore.starcore.core.persistence.PersistenceService;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.weather.model.NationWeatherPermission;
import dev.starcore.starcore.module.weather.model.NationWeatherSettings;
import dev.starcore.starcore.module.weather.model.WeatherType;
import org.bukkit.plugin.java.JavaPlugin;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * 基于数据库的天气状态存储实现
 */
public class DatabaseAwareWeatherStateStorage implements WeatherStateStorage {

    private final String namespace;
    private final DatabaseService databaseService;
    private final PersistenceService persistenceService;
    private final Logger logger;
    private boolean initialized = false;

    private static final String CREATE_TABLE_SQL_MYSQL = """
        CREATE TABLE IF NOT EXISTS {table} (
            nation_id VARCHAR(36) PRIMARY KEY,
            current_weather VARCHAR(20) NOT NULL DEFAULT 'CLEAR',
            auto_weather BOOLEAN NOT NULL DEFAULT TRUE,
            permission VARCHAR(20) NOT NULL DEFAULT 'NONE',
            last_weather_change BIGINT NOT NULL DEFAULT 0,
            last_controlled_world VARCHAR(255),
            UNIQUE(nation_id)
        )
        """;

    private static final String CREATE_TABLE_SQL_SQLITE = """
        CREATE TABLE IF NOT EXISTS {table} (
            nation_id TEXT PRIMARY KEY,
            current_weather TEXT NOT NULL DEFAULT 'CLEAR',
            auto_weather INTEGER NOT NULL DEFAULT 1,
            permission TEXT NOT NULL DEFAULT 'NONE',
            last_weather_change INTEGER NOT NULL DEFAULT 0,
            last_controlled_world TEXT,
            UNIQUE(nation_id)
        )
        """;

    private static final String SELECT_ALL_SQL = "SELECT * FROM {table}";

    private static final String DELETE_SQL = "DELETE FROM {table} WHERE nation_id = ?";

    public DatabaseAwareWeatherStateStorage(
            String namespace,
            DatabaseService databaseService,
            PersistenceService persistenceService,
            Logger logger) {
        this.namespace = namespace;
        this.databaseService = databaseService;
        this.persistenceService = persistenceService;
        this.logger = logger;
    }

    @Override
    public void initialize() {
        if (initialized) {
            return;
        }

        // 创建表
        databaseService.dataSource().ifPresent(ds -> {
            try (Connection conn = ds.getConnection()) {
                boolean isSQLite = "SQLite".equalsIgnoreCase(conn.getMetaData().getDatabaseProductName());
                String sql = (isSQLite ? CREATE_TABLE_SQL_SQLITE : CREATE_TABLE_SQL_MYSQL)
                    .replace("{table}", getTableName());
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(sql);
                }
                initialized = true;
                logger.info("Weather state table initialized: " + getTableName());

            } catch (SQLException e) {
                logger.warning("Failed to initialize weather state table: " + e.getMessage());
            }
        });
    }

    @Override
    public boolean isInitialized() {
        return initialized;
    }

    @Override
    public Map<NationId, NationWeatherSettings> load() {
        Map<NationId, NationWeatherSettings> result = new ConcurrentHashMap<>();

        databaseService.dataSource().ifPresent(ds -> {
            try (Connection conn = ds.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(SELECT_ALL_SQL.replace("{table}", getTableName()));
                 ResultSet rs = stmt.executeQuery()) {

                while (rs.next()) {
                    NationWeatherSettings settings = parseResultSet(rs);
                    if (settings != null) {
                        result.put(settings.nationId(), settings);
                    }
                }

            } catch (SQLException e) {
                logger.warning("Failed to load weather states: " + e.getMessage());
            }
        });

        return result;
    }

    @Override
    public void saveAsync(Map<NationId, NationWeatherSettings> states) {
        // 使用同步保存（数据库操作已经在异步线程中执行）
        save(states);
    }

    @Override
    public void save(Map<NationId, NationWeatherSettings> states) {
        databaseService.dataSource().ifPresent(ds -> {
            try (Connection conn = ds.getConnection()) {
                boolean isSQLite = "SQLite".equalsIgnoreCase(conn.getMetaData().getDatabaseProductName());
                String sql = getInsertSql(getTableName(), isSQLite);

                conn.setAutoCommit(false);

                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    for (Map.Entry<NationId, NationWeatherSettings> entry : states.entrySet()) {
                        NationWeatherSettings settings = entry.getValue();
                        setStatementParameters(stmt, settings);
                        stmt.addBatch();
                    }

                    stmt.executeBatch();
                    conn.commit();
                }

            } catch (SQLException e) {
                logger.warning("Failed to save weather states: " + e.getMessage());
            }
        });
    }

    @Override
    public void saveNation(NationId nationId, NationWeatherSettings settings) {
        databaseService.dataSource().ifPresent(ds -> {
            try (Connection conn = ds.getConnection()) {
                boolean isSQLite = "SQLite".equalsIgnoreCase(conn.getMetaData().getDatabaseProductName());
                String sql = getInsertSql(getTableName(), isSQLite);

                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    setStatementParameters(stmt, settings);
                    stmt.executeUpdate();
                }

            } catch (SQLException e) {
                logger.warning("Failed to save nation weather state: " + e.getMessage());
            }
        });
    }

    @Override
    public void deleteNation(NationId nationId) {
        databaseService.dataSource().ifPresent(ds -> {
            try (Connection conn = ds.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(DELETE_SQL.replace("{table}", getTableName()))) {

                stmt.setString(1, nationId.toString());
                stmt.executeUpdate();

            } catch (SQLException e) {
                logger.warning("Failed to delete nation weather state: " + e.getMessage());
            }
        });
    }

    private static String getInsertSql(String tableName, boolean isSQLite) {
        if (isSQLite) {
            return """
                INSERT INTO %s (nation_id, current_weather, auto_weather, permission, last_weather_change, last_controlled_world)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT(nation_id) DO UPDATE SET
                    current_weather = excluded.current_weather,
                    auto_weather = excluded.auto_weather,
                    permission = excluded.permission,
                    last_weather_change = excluded.last_weather_change,
                    last_controlled_world = excluded.last_controlled_world
                """.formatted(tableName);
        } else {
            return """
                INSERT INTO %s (nation_id, current_weather, auto_weather, permission, last_weather_change, last_controlled_world)
                VALUES (?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    current_weather = VALUES(current_weather),
                    auto_weather = VALUES(auto_weather),
                    permission = VALUES(permission),
                    last_weather_change = VALUES(last_weather_change),
                    last_controlled_world = VALUES(last_controlled_world)
                """.formatted(tableName);
        }
    }

    private void setStatementParameters(PreparedStatement stmt, NationWeatherSettings settings) throws SQLException {
        stmt.setString(1, settings.nationId().toString());
        stmt.setString(2, settings.getCurrentWeather().name());
        stmt.setBoolean(3, settings.isAutoWeather());
        stmt.setString(4, settings.getPermission().name());
        stmt.setLong(5, settings.getLastWeatherChangeTime());
        stmt.setString(6, settings.getLastControlledWorld());
    }

    private NationWeatherSettings parseResultSet(ResultSet rs) throws SQLException {
        String nationIdStr = rs.getString("nation_id");
        if (nationIdStr == null) {
            return null;
        }

        try {
            NationId nationId = new NationId(UUID.fromString(nationIdStr));
            WeatherType weather = WeatherType.fromName(rs.getString("current_weather"));
            boolean autoWeather = rs.getBoolean("auto_weather");
            NationWeatherPermission permission = NationWeatherPermission.valueOf(
                rs.getString("permission")
            );

            NationWeatherSettings settings = new NationWeatherSettings(
                nationId, weather, autoWeather, permission
            );

            settings.setLastWeatherChangeTime(rs.getLong("last_weather_change"));
            settings.setLastControlledWorld(rs.getString("last_controlled_world"));

            return settings;

        } catch (IllegalArgumentException e) {
            logger.warning("Invalid nation ID in weather state: " + nationIdStr);
            return null;
        }
    }

    private String getTableName() {
        return namespace + "_weather_state";
    }
}
