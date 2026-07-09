package dev.starcore.starcore.module.combat.storage;

import dev.starcore.starcore.core.database.DatabaseService;
import dev.starcore.starcore.module.combat.model.PlayerCombatState.CombatStateSnapshot;
import org.bukkit.plugin.Plugin;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * 战斗数据存储 - 支持数据库和文件持久化
 */
public final class CombatStorage {
    private static final String TABLE_PLAYER_STATES = "combat_player_states";
    private static final String TABLE_COMBAT_HISTORY = "combat_history";
    private static final String TABLE_PLAYER_PVP = "combat_player_pvp";

    private final Plugin plugin;
    private final DatabaseService databaseService;
    private volatile boolean databaseEnabled = false;

    public CombatStorage(Plugin plugin, DatabaseService databaseService) {
        this.plugin = plugin;
        this.databaseService = databaseService;
        initializeDatabase();
    }

    /**
     * 初始化数据库表
     */
    private void initializeDatabase() {
        if (databaseService == null || !databaseService.isRunning()) {
            plugin.getLogger().info("CombatStorage: Database not available, using in-memory storage only.");
            return;
        }

        databaseEnabled = true;

        try {
            databaseService.dataSource().ifPresent(ds -> {
                try (Connection conn = ds.getConnection();
                     Statement stmt = conn.createStatement()) {

                    // 创建玩家战斗状态表
                    String createPlayerStatesTable = "CREATE TABLE IF NOT EXISTS " + TABLE_PLAYER_STATES + " (" +
                        "player_id VARCHAR(36) PRIMARY KEY, " +
                        "in_combat BOOLEAN DEFAULT FALSE, " +
                        "combat_start_time BIGINT DEFAULT 0, " +
                        "last_damage_time BIGINT DEFAULT 0, " +
                        "last_tagger_id VARCHAR(36), " +
                        "tag_timeout BIGINT DEFAULT 0, " +
                        "tag_start_time BIGINT DEFAULT 0, " +
                        "tag_type VARCHAR(32), " +
                        "total_damage_dealt INT DEFAULT 0, " +
                        "total_damage_taken INT DEFAULT 0, " +
                        "total_kills INT DEFAULT 0, " +
                        "total_deaths INT DEFAULT 0, " +
                        "last_killer_id VARCHAR(36), " +
                        "last_victim_id VARCHAR(36), " +
                        "updated_at BIGINT NOT NULL)";
                    stmt.execute(createPlayerStatesTable);

                    // 创建战斗历史表
                    String createHistoryTable = "CREATE TABLE IF NOT EXISTS " + TABLE_COMBAT_HISTORY + " (" +
                        "history_id VARCHAR(36) PRIMARY KEY, " +
                        "session_id VARCHAR(36), " +
                        "attacker_id VARCHAR(36) NOT NULL, " +
                        "defender_id VARCHAR(36) NOT NULL, " +
                        "killer_id VARCHAR(36), " +
                        "victim_id VARCHAR(36), " +
                        "attacker_damage INT DEFAULT 0, " +
                        "defender_damage INT DEFAULT 0, " +
                        "end_reason VARCHAR(32), " +
                        "duration_seconds BIGINT DEFAULT 0, " +
                        "world VARCHAR(64), " +
                        "location_x DOUBLE, " +
                        "location_y DOUBLE, " +
                        "location_z DOUBLE, " +
                        "combat_type VARCHAR(32), " +
                        "created_at BIGINT NOT NULL)";
                    stmt.execute(createHistoryTable);

                    // 创建索引
                    stmt.execute("CREATE INDEX IF NOT EXISTS idx_combat_history_attacker ON " + TABLE_COMBAT_HISTORY + " (attacker_id)");
                    stmt.execute("CREATE INDEX IF NOT EXISTS idx_combat_history_defender ON " + TABLE_COMBAT_HISTORY + " (defender_id)");
                    stmt.execute("CREATE INDEX IF NOT EXISTS idx_combat_history_created ON " + TABLE_COMBAT_HISTORY + " (created_at)");

                    // 创建玩家PVP状态表
                    String createPvpTable = "CREATE TABLE IF NOT EXISTS " + TABLE_PLAYER_PVP + " (" +
                        "player_id VARCHAR(36) PRIMARY KEY, " +
                        "pvp_enabled BOOLEAN DEFAULT TRUE, " +
                        "updated_at BIGINT NOT NULL)";
                    stmt.execute(createPvpTable);

                    plugin.getLogger().info("CombatStorage database tables initialized.");

                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Failed to initialize combat database tables", e);
                    databaseEnabled = false;
                }
            });
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to initialize combat storage", e);
            databaseEnabled = false;
        }
    }

    /**
     * 加载所有玩家战斗状态
     */
    public List<CombatStateSnapshot> loadPlayerStates() {
        List<CombatStateSnapshot> states = new ArrayList<>();

        if (!databaseEnabled || databaseService == null) {
            return states;
        }

        var dataSource = databaseService.dataSource();
        if (dataSource.isEmpty()) {
            return states;
        }

        try (Connection conn = dataSource.get().getConnection()) {
            String sql = "SELECT * FROM " + TABLE_PLAYER_STATES + " WHERE in_combat = TRUE";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {

                while (rs.next()) {
                    CombatStateSnapshot snapshot = parseSnapshot(rs);
                    if (snapshot != null) {
                        states.add(snapshot);
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to load player combat states", e);
        }

        return states;
    }

    /**
     * 保存玩家战斗状态
     */
    public void savePlayerState(CombatStateSnapshot snapshot) {
        if (!databaseEnabled || databaseService == null) {
            return;
        }

        var dataSource = databaseService.dataSource();
        if (dataSource.isEmpty()) {
            return;
        }

        String sql = "INSERT OR REPLACE INTO " + TABLE_PLAYER_STATES + " " +
            "(player_id, in_combat, combat_start_time, last_damage_time, " +
            "last_tagger_id, tag_timeout, tag_start_time, tag_type, " +
            "total_damage_dealt, total_damage_taken, total_kills, total_deaths, " +
            "last_killer_id, last_victim_id, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = dataSource.get().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, snapshot.playerId().toString());
            stmt.setBoolean(2, snapshot.inCombat());
            stmt.setLong(3, snapshot.combatStartTime());
            stmt.setLong(4, snapshot.lastDamageTime());
            stmt.setString(5, snapshot.lastTaggerId() != null ? snapshot.lastTaggerId().toString() : null);
            stmt.setLong(6, snapshot.tagTimeout());
            stmt.setLong(7, snapshot.tagStartTime());
            stmt.setString(8, snapshot.tagType());
            stmt.setInt(9, snapshot.totalDamageDealt());
            stmt.setInt(10, snapshot.totalDamageTaken());
            stmt.setInt(11, snapshot.totalKills());
            stmt.setInt(12, snapshot.totalDeaths());
            stmt.setString(13, snapshot.lastKillerId() != null ? snapshot.lastKillerId().toString() : null);
            stmt.setString(14, snapshot.lastVictimId() != null ? snapshot.lastVictimId().toString() : null);
            stmt.setLong(15, System.currentTimeMillis());

            stmt.executeUpdate();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save player combat state: " + snapshot.playerId(), e);
        }
    }

    /**
     * 批量保存玩家战斗状态
     */
    public void savePlayerStates(List<CombatStateSnapshot> snapshots) {
        if (!databaseEnabled || databaseService == null || snapshots.isEmpty()) {
            return;
        }

        var dataSource = databaseService.dataSource();
        if (dataSource.isEmpty()) {
            return;
        }

        String sql = "INSERT OR REPLACE INTO " + TABLE_PLAYER_STATES + " " +
            "(player_id, in_combat, combat_start_time, last_damage_time, " +
            "last_tagger_id, tag_timeout, tag_start_time, tag_type, " +
            "total_damage_dealt, total_damage_taken, total_kills, total_deaths, " +
            "last_killer_id, last_victim_id, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = dataSource.get().getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                for (CombatStateSnapshot snapshot : snapshots) {
                    stmt.setString(1, snapshot.playerId().toString());
                    stmt.setBoolean(2, snapshot.inCombat());
                    stmt.setLong(3, snapshot.combatStartTime());
                    stmt.setLong(4, snapshot.lastDamageTime());
                    stmt.setString(5, snapshot.lastTaggerId() != null ? snapshot.lastTaggerId().toString() : null);
                    stmt.setLong(6, snapshot.tagTimeout());
                    stmt.setLong(7, snapshot.tagStartTime());
                    stmt.setString(8, snapshot.tagType());
                    stmt.setInt(9, snapshot.totalDamageDealt());
                    stmt.setInt(10, snapshot.totalDamageTaken());
                    stmt.setInt(11, snapshot.totalKills());
                    stmt.setInt(12, snapshot.totalDeaths());
                    stmt.setString(13, snapshot.lastKillerId() != null ? snapshot.lastKillerId().toString() : null);
                    stmt.setString(14, snapshot.lastVictimId() != null ? snapshot.lastVictimId().toString() : null);
                    stmt.setLong(15, System.currentTimeMillis());

                    stmt.addBatch();
                }

                stmt.executeBatch();
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                plugin.getLogger().log(Level.WARNING, "Failed to batch save combat states", e);
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to batch save combat states", e);
        }
    }

    /**
     * 保存战斗历史记录
     */
    public void saveCombatHistory(CombatHistoryRecord record) {
        if (!databaseEnabled || databaseService == null) {
            return;
        }

        var dataSource = databaseService.dataSource();
        if (dataSource.isEmpty()) {
            return;
        }

        String sql = "INSERT INTO " + TABLE_COMBAT_HISTORY + " " +
            "(history_id, session_id, attacker_id, defender_id, killer_id, victim_id, " +
            "attacker_damage, defender_damage, end_reason, duration_seconds, " +
            "world, location_x, location_y, location_z, combat_type, created_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = dataSource.get().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, record.historyId().toString());
            stmt.setString(2, record.sessionId().toString());
            stmt.setString(3, record.attackerId().toString());
            stmt.setString(4, record.defenderId().toString());
            stmt.setString(5, record.killerId() != null ? record.killerId().toString() : null);
            stmt.setString(6, record.victimId() != null ? record.victimId().toString() : null);
            stmt.setInt(7, record.attackerDamage());
            stmt.setInt(8, record.defenderDamage());
            stmt.setString(9, record.endReason());
            stmt.setLong(10, record.durationSeconds());
            stmt.setString(11, record.world());
            stmt.setDouble(12, record.locationX());
            stmt.setDouble(13, record.locationY());
            stmt.setDouble(14, record.locationZ());
            stmt.setString(15, record.combatType());
            stmt.setLong(16, record.createdAt());

            stmt.executeUpdate();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save combat history", e);
        }
    }

    /**
     * 获取玩家的战斗历史
     */
    public List<CombatHistoryRecord> getPlayerCombatHistory(UUID playerId, int limit) {
        List<CombatHistoryRecord> history = new ArrayList<>();

        if (!databaseEnabled || databaseService == null) {
            return history;
        }

        var dataSource = databaseService.dataSource();
        if (dataSource.isEmpty()) {
            return history;
        }

        String sql = "SELECT * FROM " + TABLE_COMBAT_HISTORY + " " +
            "WHERE attacker_id = ? OR defender_id = ? OR killer_id = ? OR victim_id = ? " +
            "ORDER BY created_at DESC LIMIT ?";

        try (Connection conn = dataSource.get().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerId.toString());
            stmt.setString(2, playerId.toString());
            stmt.setString(3, playerId.toString());
            stmt.setString(4, playerId.toString());
            stmt.setInt(5, limit);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    CombatHistoryRecord record = parseHistoryRecord(rs);
                    if (record != null) {
                        history.add(record);
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to load combat history for player: " + playerId, e);
        }

        return history;
    }

    /**
     * 删除玩家的战斗状态
     */
    public void deletePlayerState(UUID playerId) {
        if (!databaseEnabled || databaseService == null) {
            return;
        }

        var dataSource = databaseService.dataSource();
        if (dataSource.isEmpty()) {
            return;
        }

        String sql = "DELETE FROM " + TABLE_PLAYER_STATES + " WHERE player_id = ?";

        try (Connection conn = dataSource.get().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerId.toString());
            stmt.executeUpdate();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to delete player combat state: " + playerId, e);
        }
    }

    /**
     * 清理旧的战斗历史
     */
    public void cleanupOldHistory(int daysToKeep) {
        if (!databaseEnabled || databaseService == null) {
            return;
        }

        var dataSource = databaseService.dataSource();
        if (dataSource.isEmpty()) {
            return;
        }

        String sql = "DELETE FROM " + TABLE_COMBAT_HISTORY + " WHERE created_at < ?";
        long cutoffTime = System.currentTimeMillis() - (daysToKeep * 24L * 60 * 60 * 1000);

        try (Connection conn = dataSource.get().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, cutoffTime);
            int deleted = stmt.executeUpdate();
            if (deleted > 0) {
                plugin.getLogger().info("Cleaned up " + deleted + " old combat history records.");
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to cleanup combat history", e);
        }
    }

    private CombatStateSnapshot parseSnapshot(ResultSet rs) throws SQLException {
        UUID playerId = UUID.fromString(rs.getString("player_id"));
        return new CombatStateSnapshot(
            playerId,
            rs.getBoolean("in_combat"),
            rs.getLong("combat_start_time"),
            rs.getLong("last_damage_time"),
            rs.getString("last_tagger_id") != null ? UUID.fromString(rs.getString("last_tagger_id")) : null,
            rs.getLong("tag_timeout"),
            rs.getLong("tag_start_time"),
            rs.getString("tag_type"),
            rs.getInt("total_damage_dealt"),
            rs.getInt("total_damage_taken"),
            rs.getInt("total_kills"),
            rs.getInt("total_deaths"),
            rs.getString("last_killer_id") != null ? UUID.fromString(rs.getString("last_killer_id")) : null,
            rs.getString("last_victim_id") != null ? UUID.fromString(rs.getString("last_victim_id")) : null
        );
    }

    private CombatHistoryRecord parseHistoryRecord(ResultSet rs) throws SQLException {
        UUID historyId = UUID.fromString(rs.getString("history_id"));
        UUID sessionId = UUID.fromString(rs.getString("session_id"));
        UUID attackerId = UUID.fromString(rs.getString("attacker_id"));
        UUID defenderId = UUID.fromString(rs.getString("defender_id"));

        return new CombatHistoryRecord(
            historyId,
            sessionId,
            attackerId,
            defenderId,
            rs.getString("killer_id") != null ? UUID.fromString(rs.getString("killer_id")) : null,
            rs.getString("victim_id") != null ? UUID.fromString(rs.getString("victim_id")) : null,
            rs.getInt("attacker_damage"),
            rs.getInt("defender_damage"),
            rs.getString("end_reason"),
            rs.getLong("duration_seconds"),
            rs.getString("world"),
            rs.getDouble("location_x"),
            rs.getDouble("location_y"),
            rs.getDouble("location_z"),
            rs.getString("combat_type"),
            rs.getLong("created_at")
        );
    }

    public void close() {
        plugin.getLogger().info("CombatStorage closed.");
    }

    public boolean isDatabaseEnabled() {
        return databaseEnabled;
    }

    /**
     * 保存玩家PVP状态
     */
    public void savePlayerPvpState(UUID playerId, boolean enabled) {
        if (!databaseEnabled || databaseService == null) {
            return;
        }

        var dataSource = databaseService.dataSource();
        if (dataSource.isEmpty()) {
            return;
        }

        String sql = "INSERT OR REPLACE INTO " + TABLE_PLAYER_PVP + " (player_id, pvp_enabled, updated_at) VALUES (?, ?, ?)";

        try (Connection conn = dataSource.get().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerId.toString());
            stmt.setBoolean(2, enabled);
            stmt.setLong(3, System.currentTimeMillis());
            stmt.executeUpdate();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save player PVP state: " + playerId, e);
        }
    }

    /**
     * 加载玩家PVP状态
     * @param playerId 玩家UUID
     * @param defaultValue 数据库未记录时的默认值（通常应为 true）
     * @return 玩家的PVP开关状态，如果数据库不可用则返回 defaultValue
     */
    public boolean loadPlayerPvpState(UUID playerId, boolean defaultValue) {
        if (!databaseEnabled || databaseService == null) {
            return defaultValue;
        }

        var dataSource = databaseService.dataSource();
        if (dataSource.isEmpty()) {
            return defaultValue;
        }

        String sql = "SELECT pvp_enabled FROM " + TABLE_PLAYER_PVP + " WHERE player_id = ?";

        try (Connection conn = dataSource.get().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerId.toString());

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getBoolean("pvp_enabled");
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to load player PVP state: " + playerId, e);
        }

        return defaultValue;
    }

    /**
     * 加载玩家PVP状态（使用默认开启值）
     * @param playerId 玩家UUID
     * @return 玩家的PVP开关状态，未设置或数据库不可用时返回 true（与数据库 DEFAULT TRUE 一致）
     */
    public boolean loadPlayerPvpState(UUID playerId) {
        return loadPlayerPvpState(playerId, true);
    }

    /**
     * 加载所有玩家PVP状态
     */
    public java.util.Map<UUID, Boolean> loadAllPlayerPvpStates() {
        java.util.Map<UUID, Boolean> states = new java.util.HashMap<>();

        if (!databaseEnabled || databaseService == null) {
            return states;
        }

        var dataSource = databaseService.dataSource();
        if (dataSource.isEmpty()) {
            return states;
        }

        String sql = "SELECT player_id, pvp_enabled FROM " + TABLE_PLAYER_PVP;

        try (Connection conn = dataSource.get().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                UUID playerId = UUID.fromString(rs.getString("player_id"));
                boolean enabled = rs.getBoolean("pvp_enabled");
                states.put(playerId, enabled);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to load all player PVP states", e);
        }

        return states;
    }

    /**
     * 战斗历史记录
     */
    public record CombatHistoryRecord(
        UUID historyId,
        UUID sessionId,
        UUID attackerId,
        UUID defenderId,
        UUID killerId,
        UUID victimId,
        int attackerDamage,
        int defenderDamage,
        String endReason,
        long durationSeconds,
        String world,
        double locationX,
        double locationY,
        double locationZ,
        String combatType,
        long createdAt
    ) {}
}
