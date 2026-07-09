package dev.starcore.starcore.module.army.weather.storage;

import dev.starcore.starcore.core.database.DatabaseService;
import dev.starcore.starcore.core.persistence.PersistenceService;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.army.weather.model.WeatherTacticsBoost;
import dev.starcore.starcore.module.weather.model.WeatherType;
import org.bukkit.plugin.java.JavaPlugin;

import javax.sql.DataSource;
import java.sql.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数据库天气战术存储实现
 */
public final class DatabaseWeatherTacticsStorage implements WeatherTacticsStorage {

    private static final String TABLE_TACTICS = "weather_tactics";
    private static final String TABLE_BOOSTS = "weather_tactics_boosts";

    private final String namespace;
    private final DatabaseService databaseService;
    private final PersistenceService persistenceService;
    private final JavaPlugin plugin;
    private final Map<NationId, Map<String, Integer>> tacticsCache = new ConcurrentHashMap<>();
    private final Map<String, WeatherTacticsBoost> boostsCache = new ConcurrentHashMap<>();

    public DatabaseWeatherTacticsStorage(
        String namespace,
        DatabaseService databaseService,
        PersistenceService persistenceService,
        JavaPlugin plugin
    ) {
        this.namespace = namespace;
        this.databaseService = databaseService;
        this.persistenceService = persistenceService;
        this.plugin = plugin;
    }

    @Override
    public void initialize() {
        databaseService.dataSource().ifPresent(this::createTables);
    }

    private void createTables(DataSource ds) {
        try (Connection conn = ds.getConnection()) {
            boolean isSQLite = "SQLite".equalsIgnoreCase(conn.getMetaData().getDatabaseProductName());

            // 创建战术升级表
            String tacticsSql;
            if (isSQLite) {
                tacticsSql = """
                    CREATE TABLE IF NOT EXISTS %s (
                        nation_id TEXT NOT NULL,
                        tactics_type TEXT NOT NULL,
                        level INTEGER NOT NULL DEFAULT 0,
                        updated_at INTEGER NOT NULL,
                        PRIMARY KEY (nation_id, tactics_type)
                    )
                    """.formatted(TABLE_TACTICS);
            } else {
                tacticsSql = """
                    CREATE TABLE IF NOT EXISTS %s (
                        nation_id VARCHAR(36) NOT NULL,
                        tactics_type VARCHAR(64) NOT NULL,
                        level INT NOT NULL DEFAULT 0,
                        updated_at BIGINT NOT NULL,
                        PRIMARY KEY (nation_id, tactics_type)
                    )
                    """.formatted(TABLE_TACTICS);
            }

            // 创建战术加成表
            String boostsSql;
            if (isSQLite) {
                boostsSql = """
                    CREATE TABLE IF NOT EXISTS %s (
                        nation_id TEXT NOT NULL,
                        weather_type TEXT NOT NULL,
                        attack_mult REAL NOT NULL DEFAULT 1.0,
                        defense_mult REAL NOT NULL DEFAULT 1.0,
                        movement_mult REAL NOT NULL DEFAULT 1.0,
                        morale_bonus REAL NOT NULL DEFAULT 1.0,
                        tactics_name TEXT,
                        description TEXT,
                        updated_at INTEGER NOT NULL,
                        PRIMARY KEY (nation_id, weather_type)
                    )
                    """.formatted(TABLE_BOOSTS);
            } else {
                boostsSql = """
                    CREATE TABLE IF NOT EXISTS %s (
                        nation_id VARCHAR(36) NOT NULL,
                        weather_type VARCHAR(32) NOT NULL,
                        attack_mult DOUBLE NOT NULL DEFAULT 1.0,
                        defense_mult DOUBLE NOT NULL DEFAULT 1.0,
                        movement_mult DOUBLE NOT NULL DEFAULT 1.0,
                        morale_bonus DOUBLE NOT NULL DEFAULT 1.0,
                        tactics_name VARCHAR(64),
                        description TEXT,
                        updated_at BIGINT NOT NULL,
                        PRIMARY KEY (nation_id, weather_type)
                    )
                    """.formatted(TABLE_BOOSTS);
            }

            try (Statement stmt = conn.createStatement()) {
                stmt.execute(tacticsSql);
                stmt.execute(boostsSql);
            }

            plugin.getLogger().info("Weather tactics tables initialized");

        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to initialize weather tactics tables: " + e.getMessage());
        }
    }

    @Override
    public void save() {
        databaseService.dataSource().ifPresent(ds -> {
            // 保存战术数据
            for (Map.Entry<NationId, Map<String, Integer>> entry : tacticsCache.entrySet()) {
                for (Map.Entry<String, Integer> tactic : entry.getValue().entrySet()) {
                    saveTacticsRecord(ds, entry.getKey(), tactic.getKey(), tactic.getValue());
                }
            }

            // 保存加成数据
            for (Map.Entry<String, WeatherTacticsBoost> entry : boostsCache.entrySet()) {
                String[] parts = entry.getKey().split(":");
                if (parts.length == 2) {
                    saveBoostRecord(ds, parts[0], WeatherType.valueOf(parts[1]), entry.getValue());
                }
            }
        });
    }

    @Override
    public void load() {
        tacticsCache.clear();
        boostsCache.clear();

        databaseService.dataSource().ifPresent(ds -> {
            loadAllTactics(ds);
            loadAllBoosts(ds);
        });

        plugin.getLogger().info("Loaded weather tactics: " + tacticsCache.size() + " nations, " +
            boostsCache.size() + " boosts");
    }

    private void loadAllTactics(DataSource ds) {
        String sql = "SELECT nation_id, tactics_type, level FROM " + TABLE_TACTICS;

        try (Connection conn = ds.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                String nationId = rs.getString("nation_id");
                String tacticsType = rs.getString("tactics_type");
                int level = rs.getInt("level");

                NationId id = new NationId(java.util.UUID.fromString(nationId));
                Map<String, Integer> tactics = tacticsCache.computeIfAbsent(id, k -> new ConcurrentHashMap<>());
                tactics.put(tacticsType, level);
            }

        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to load tactics: " + e.getMessage());
        }
    }

    private void loadAllBoosts(DataSource ds) {
        String sql = "SELECT nation_id, weather_type, attack_mult, defense_mult, " +
            "movement_mult, morale_bonus, tactics_name, description FROM " + TABLE_BOOSTS;

        try (Connection conn = ds.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                String nationId = rs.getString("nation_id");
                String weatherType = rs.getString("weather_type");
                double atk = rs.getDouble("attack_mult");
                double def = rs.getDouble("defense_mult");
                double mov = rs.getDouble("movement_mult");
                double morale = rs.getDouble("morale_bonus");
                String tacticsName = rs.getString("tactics_name");
                String desc = rs.getString("description");

                WeatherType wt = WeatherType.valueOf(weatherType);
                WeatherTacticsBoost boost = new WeatherTacticsBoost(
                    wt, atk, def, mov, morale, tacticsName, desc
                );

                boostsCache.put(nationId + ":" + weatherType, boost);
            }

        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to load boosts: " + e.getMessage());
        }
    }

    @Override
    public void saveNationTactics(NationId nationId, Map<String, Integer> tactics) {
        databaseService.dataSource().ifPresent(ds -> {
            for (Map.Entry<String, Integer> entry : tactics.entrySet()) {
                saveTacticsRecord(ds, nationId, entry.getKey(), entry.getValue());
            }
        });
    }

    private void saveTacticsRecord(DataSource ds, NationId nationId, String tacticsType, int level) {
        try (Connection conn = ds.getConnection()) {
            boolean isSQLite = "SQLite".equalsIgnoreCase(conn.getMetaData().getDatabaseProductName());

            String sql;
            if (isSQLite) {
                sql = """
                    INSERT INTO %s (nation_id, tactics_type, level, updated_at)
                    VALUES (?, ?, ?, ?)
                    ON CONFLICT(nation_id, tactics_type) DO UPDATE SET level = excluded.level, updated_at = excluded.updated_at
                    """.formatted(TABLE_TACTICS);
            } else {
                sql = """
                    INSERT INTO %s (nation_id, tactics_type, level, updated_at)
                    VALUES (?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE level = VALUES(level), updated_at = VALUES(updated_at)
                    """.formatted(TABLE_TACTICS);
            }

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, nationId.value().toString());
                ps.setString(2, tacticsType);
                ps.setInt(3, level);
                ps.setLong(4, System.currentTimeMillis());
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to save tactics: " + e.getMessage());
        }
    }

    @Override
    public Map<String, Integer> loadNationTactics(NationId nationId) {
        return tacticsCache.getOrDefault(nationId, new ConcurrentHashMap<>());
    }

    @Override
    public void saveTacticsBoost(NationId nationId, WeatherType weather, WeatherTacticsBoost boost) {
        databaseService.dataSource().ifPresent(ds -> {
            saveBoostRecord(ds, nationId.value().toString(), weather, boost);
        });
    }

    private void saveBoostRecord(DataSource ds, String nationId, WeatherType weather, WeatherTacticsBoost boost) {
        try (Connection conn = ds.getConnection()) {
            boolean isSQLite = "SQLite".equalsIgnoreCase(conn.getMetaData().getDatabaseProductName());

            String sql;
            if (isSQLite) {
                sql = """
                    INSERT INTO %s (nation_id, weather_type, attack_mult, defense_mult,
                        movement_mult, morale_bonus, tactics_name, description, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(nation_id, weather_type) DO UPDATE SET
                        attack_mult = excluded.attack_mult,
                        defense_mult = excluded.defense_mult,
                        movement_mult = excluded.movement_mult,
                        morale_bonus = excluded.morale_bonus,
                        tactics_name = excluded.tactics_name,
                        description = excluded.description,
                        updated_at = excluded.updated_at
                    """.formatted(TABLE_BOOSTS);
            } else {
                sql = """
                    INSERT INTO %s (nation_id, weather_type, attack_mult, defense_mult,
                        movement_mult, morale_bonus, tactics_name, description, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                        attack_mult = VALUES(attack_mult),
                        defense_mult = VALUES(defense_mult),
                        movement_mult = VALUES(movement_mult),
                        morale_bonus = VALUES(morale_bonus),
                        tactics_name = VALUES(tactics_name),
                        description = VALUES(description),
                        updated_at = VALUES(updated_at)
                    """.formatted(TABLE_BOOSTS);
            }

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, nationId);
                ps.setString(2, weather.name());
                ps.setDouble(3, boost.attackMultiplier());
                ps.setDouble(4, boost.defenseMultiplier());
                ps.setDouble(5, boost.movementMultiplier());
                ps.setDouble(6, boost.moraleBonus());
                ps.setString(7, boost.tacticsName());
                ps.setString(8, boost.description());
                ps.setLong(9, System.currentTimeMillis());

                ps.executeUpdate();

                // 更新缓存
                boostsCache.put(nationId + ":" + weather.name(), boost);
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to save boost: " + e.getMessage());
        }
    }

    @Override
    public WeatherTacticsBoost loadTacticsBoost(NationId nationId, WeatherType weather) {
        String key = nationId.value().toString() + ":" + weather.name();
        return boostsCache.get(key);
    }

    @Override
    public void close() {
        save();
    }
}