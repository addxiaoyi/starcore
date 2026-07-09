package dev.starcore.starcore.module.tournament.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import dev.starcore.starcore.core.database.DatabaseService;
import dev.starcore.starcore.module.tournament.Tournament;
import dev.starcore.starcore.module.tournament.TournamentConfig;
import dev.starcore.starcore.module.tournament.TournamentStatus;
import dev.starcore.starcore.module.tournament.TournamentType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.logging.Level;

/**
 * 数据库感知的锦标赛存储
 * 支持 MySQL/SQLite 持久化
 */
public class DatabaseAwareTournamentStorage {
    private static final String TABLE_NAME = "tournaments";
    private static final String TABLE_HISTORY = "tournament_history";
    private static final java.lang.reflect.Type STRING_INTEGER_MAP = new TypeToken<Map<String, Integer>>() {}.getType();
    private static final java.lang.reflect.Type STRING_LONG_MAP = new TypeToken<Map<String, Long>>() {}.getType();
    private static final java.lang.reflect.Type STRING_LIST_MAP = new TypeToken<Map<String, List<String>>>() {}.getType();
    private static final java.lang.reflect.Type STRING_LIST = new TypeToken<List<String>>() {}.getType();

    private final JavaPlugin plugin;
    private final DatabaseService databaseService;
    private final Gson gson;
    private final Path backupPath;

    public DatabaseAwareTournamentStorage(JavaPlugin plugin, DatabaseService databaseService) {
        this.plugin = plugin;
        this.databaseService = databaseService;
        this.gson = createGson();
        this.backupPath = plugin.getDataFolder().toPath().resolve("tournaments");
        ensureBackupFolder();
    }

    private void ensureBackupFolder() {
        try {
            Files.createDirectories(backupPath);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create backup folder", e);
        }
    }

    private Gson createGson() {
        return new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(Instant.class, new InstantAdapter())
            .registerTypeAdapter(UUID.class, new UUIDAdapter())
            .create();
    }

    /**
     * 确保数据库表存在
     */
    public void ensureDatabaseTable() {
        String createTable = """
            CREATE TABLE IF NOT EXISTS %s (
                id VARCHAR(64) PRIMARY KEY,
                name VARCHAR(255) NOT NULL,
                type VARCHAR(32) NOT NULL,
                creator_id VARCHAR(64) NOT NULL,
                status VARCHAR(32) NOT NULL,
                created_at BIGINT NOT NULL,
                start_time BIGINT,
                winner_id VARCHAR(64),
                current_round INT DEFAULT 0,
                total_rounds INT DEFAULT 0,
                participants_json TEXT,
                alive_participants_json TEXT,
                kills_json TEXT,
                completions_json TEXT,
                teams_json TEXT,
                config_json TEXT NOT NULL
            )
            """.formatted(TABLE_NAME);

        String createHistoryTable = """
            CREATE TABLE IF NOT EXISTS %s (
                id VARCHAR(64) PRIMARY KEY,
                name VARCHAR(255) NOT NULL,
                type VARCHAR(32) NOT NULL,
                creator_id VARCHAR(64) NOT NULL,
                status VARCHAR(32) NOT NULL,
                created_at BIGINT NOT NULL,
                end_time BIGINT NOT NULL,
                winner_id VARCHAR(64),
                total_rounds INT DEFAULT 0,
                participants_count INT DEFAULT 0,
                config_json TEXT NOT NULL,
                data_json TEXT NOT NULL
            )
            """.formatted(TABLE_HISTORY);

        databaseService.execute(createTable);
        databaseService.execute(createHistoryTable);
        plugin.getLogger().info("Tournament tables initialized");
    }

    /**
     * 保存活跃比赛到数据库
     */
    public void saveTournament(Tournament tournament) {
        String sql = """
            INSERT OR REPLACE INTO %s
            (id, name, type, creator_id, status, created_at, start_time, winner_id,
             current_round, total_rounds, participants_json, alive_participants_json,
             kills_json, completions_json, teams_json, config_json)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.formatted(TABLE_NAME);

        databaseService.execute(sql,
            tournament.getId(),
            tournament.getName(),
            tournament.getType().name(),
            tournament.getCreatorId().toString(),
            tournament.getStatus().name(),
            tournament.getCreatedAt().toEpochMilli(),
            tournament.getStartTime() != null ? tournament.getStartTime().toEpochMilli() : 0,
            tournament.getWinner() != null ? tournament.getWinner().toString() : null,
            tournament.getCurrentRound(),
            tournament.getTotalRounds(),
            gson.toJson(tournament.getParticipants()),
            gson.toJson(tournament.getAliveParticipants()),
            serializeKills(tournament.getKills()),
            serializeCompletions(tournament.getCompletions()),
            serializeTeams(tournament.getTeams()),
            serializeConfig(tournament.getConfig())
        );
    }

    /**
     * 加载所有活跃比赛
     */
    public Map<String, Tournament> loadActiveTournaments() {
        Map<String, Tournament> result = new HashMap<>();
        String sql = "SELECT * FROM " + TABLE_NAME;

        databaseService.executeWithConnection(connection -> {
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    try {
                        Tournament tournament = parseResultSet(rs);
                        if (!tournament.getStatus().isFinished()) {
                            result.put(tournament.getId(), tournament);
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("Failed to load tournament: " + rs.getString("id"));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to load tournaments: " + e.getMessage());
            }
        });

        return result;
    }

    /**
     * 删除比赛
     */
    public void deleteTournament(String id) {
        String sql = "DELETE FROM " + TABLE_NAME + " WHERE id = ?";
        databaseService.execute(sql, id);
    }

    /**
     * 将比赛添加到历史记录
     */
    public void addToHistory(Tournament tournament) {
        String sql = """
            INSERT OR REPLACE INTO %s
            (id, name, type, creator_id, status, created_at, end_time, winner_id,
             total_rounds, participants_count, config_json, data_json)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.formatted(TABLE_HISTORY);

        databaseService.execute(sql,
            tournament.getId(),
            tournament.getName(),
            tournament.getType().name(),
            tournament.getCreatorId().toString(),
            tournament.getStatus().name(),
            tournament.getCreatedAt().toEpochMilli(),
            System.currentTimeMillis(),
            tournament.getWinner() != null ? tournament.getWinner().toString() : null,
            tournament.getTotalRounds(),
            tournament.getParticipants().size(),
            serializeConfig(tournament.getConfig()),
            gson.toJson(TournamentStorage.TournamentData.fromTournament(tournament))
        );
    }

    /**
     * 获取比赛历史记录
     */
    public List<TournamentStorage.TournamentData> getHistory(int limit) {
        List<TournamentStorage.TournamentData> result = new ArrayList<>();
        String sql = "SELECT data_json FROM " + TABLE_HISTORY + " ORDER BY end_time DESC LIMIT ?";

        databaseService.executeWithConnection(connection -> {
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setInt(1, limit);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String dataJson = rs.getString("data_json");
                        if (dataJson != null) {
                            TournamentStorage.TournamentData data = gson.fromJson(dataJson, TournamentStorage.TournamentData.class);
                            result.add(data);
                        }
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to get history: " + e.getMessage());
            }
        });

        return result;
    }

    // ==================== 辅助方法 ====================

    /**
     * 从 ResultSet 解析 Tournament
     */
    private Tournament parseResultSet(ResultSet rs) throws SQLException {
        String id = rs.getString("id");
        String name = rs.getString("name");
        TournamentType type = TournamentType.valueOf(rs.getString("type"));
        UUID creatorId = UUID.fromString(rs.getString("creator_id"));

        String configJson = rs.getString("config_json");
        TournamentConfig config = gson.fromJson(configJson, TournamentConfig.class);

        Tournament tournament = new Tournament(id, name, type, creatorId, config, Instant.ofEpochMilli(rs.getLong("created_at")));
        tournament.setStatus(TournamentStatus.valueOf(rs.getString("status")));

        long startTime = rs.getLong("start_time");
        if (startTime > 0) {
            tournament.setStartTime(Instant.ofEpochMilli(startTime));
        }

        String winnerId = rs.getString("winner_id");
        if (winnerId != null && !winnerId.isEmpty()) {
            tournament.setWinner(UUID.fromString(winnerId));
        }

        tournament.setCurrentRound(rs.getInt("current_round"));
        tournament.setTotalRounds(rs.getInt("total_rounds"));

        // 恢复参与者
        String participantsJson = rs.getString("participants_json");
        Set<UUID> participants = deserializeUUIDSet(participantsJson != null ? participantsJson : "[]");
        for (UUID p : participants) {
            tournament.addParticipant(p);
        }

        // 恢复存活状态
        String aliveJson = rs.getString("alive_participants_json");
        Set<UUID> alive = deserializeUUIDSet(aliveJson != null ? aliveJson : "[]");
        tournament.getAliveParticipants().clear();
        tournament.getAliveParticipants().addAll(alive);

        // 恢复击杀
        String killsJson = rs.getString("kills_json");
        Map<UUID, Integer> kills = deserializeKills(killsJson != null ? killsJson : "{}");
        tournament.getKills().putAll(kills);

        // 恢复完成时间
        String completionsJson = rs.getString("completions_json");
        Map<UUID, Long> completions = deserializeCompletions(completionsJson != null ? completionsJson : "{}");
        tournament.getCompletions().putAll(completions);

        // 恢复团队
        String teamsJson = rs.getString("teams_json");
        Map<String, Set<UUID>> teams = deserializeTeams(teamsJson != null ? teamsJson : "{}");
        tournament.getTeams().putAll(teams);

        return tournament;
    }

    private Tournament parseRow(Map<String, Object> row) {
        String id = row.get("id").toString();
        String name = row.get("name").toString();
        TournamentType type = TournamentType.valueOf(row.get("type").toString());
        UUID creatorId = UUID.fromString(row.get("creator_id").toString());

        String configJson = row.get("config_json").toString();
        TournamentConfig config = gson.fromJson(configJson, TournamentConfig.class);

        Tournament tournament = new Tournament(id, name, type, creatorId, config, Instant.ofEpochMilli(getLong(row, "created_at")));
        tournament.setStatus(TournamentStatus.valueOf(row.get("status").toString()));

        long startTime = getLong(row, "start_time");
        if (startTime > 0) {
            tournament.setStartTime(Instant.ofEpochMilli(startTime));
        }

        String winnerId = row.get("winner_id") != null ? row.get("winner_id").toString() : null;
        if (winnerId != null && !winnerId.isEmpty()) {
            tournament.setWinner(UUID.fromString(winnerId));
        }

        tournament.setCurrentRound(getInt(row, "current_round"));
        tournament.setTotalRounds(getInt(row, "total_rounds"));

        // 恢复参与者
        String participantsJson = row.get("participants_json") != null ? row.get("participants_json").toString() : "[]";
        Set<UUID> participants = deserializeUUIDSet(participantsJson);
        for (UUID p : participants) {
            tournament.addParticipant(p);
        }

        // 恢复存活状态
        String aliveJson = row.get("alive_participants_json") != null ? row.get("alive_participants_json").toString() : "[]";
        Set<UUID> alive = deserializeUUIDSet(aliveJson);
        tournament.getAliveParticipants().clear();
        tournament.getAliveParticipants().addAll(alive);

        // 恢复击杀
        String killsJson = row.get("kills_json") != null ? row.get("kills_json").toString() : "{}";
        Map<UUID, Integer> kills = deserializeKills(killsJson);
        tournament.getKills().putAll(kills);

        // 恢复完成时间
        String completionsJson = row.get("completions_json") != null ? row.get("completions_json").toString() : "{}";
        Map<UUID, Long> completions = deserializeCompletions(completionsJson);
        tournament.getCompletions().putAll(completions);

        // 恢复团队
        String teamsJson = row.get("teams_json") != null ? row.get("teams_json").toString() : "{}";
        Map<String, Set<UUID>> teams = deserializeTeams(teamsJson);
        tournament.getTeams().putAll(teams);

        return tournament;
    }

    private long getLong(Map<String, Object> row, String key) {
        Object val = row.get(key);
        if (val == null) return 0;
        if (val instanceof Number) return ((Number) val).longValue();
        return Long.parseLong(val.toString());
    }

    private int getInt(Map<String, Object> row, String key) {
        Object val = row.get(key);
        if (val == null) return 0;
        if (val instanceof Number) return ((Number) val).intValue();
        return Integer.parseInt(val.toString());
    }

    private String serializeConfig(TournamentConfig config) {
        return gson.toJson(config);
    }

    private String serializeKills(Map<UUID, Integer> kills) {
        Map<String, Integer> serializable = new HashMap<>();
        kills.forEach((k, v) -> serializable.put(k.toString(), v));
        return gson.toJson(serializable);
    }

    private Map<UUID, Integer> deserializeKills(String json) {
        if (json == null || json.isEmpty()) {
            return new HashMap<>();
        }
        Map<String, Integer> serializable = gson.fromJson(json, STRING_INTEGER_MAP);
        Map<UUID, Integer> result = new HashMap<>();
        if (serializable != null) {
            serializable.forEach((k, v) -> result.put(UUID.fromString(k), v));
        }
        return result;
    }

    private String serializeCompletions(Map<UUID, Long> completions) {
        Map<String, Long> serializable = new HashMap<>();
        completions.forEach((k, v) -> serializable.put(k.toString(), v));
        return gson.toJson(serializable);
    }

    private Map<UUID, Long> deserializeCompletions(String json) {
        if (json == null || json.isEmpty()) {
            return new HashMap<>();
        }
        Map<String, Long> serializable = gson.fromJson(json, STRING_LONG_MAP);
        Map<UUID, Long> result = new HashMap<>();
        if (serializable != null) {
            serializable.forEach((k, v) -> result.put(UUID.fromString(k), v));
        }
        return result;
    }

    private String serializeTeams(Map<String, Set<UUID>> teams) {
        Map<String, List<String>> serializable = new HashMap<>();
        teams.forEach((k, v) -> serializable.put(k, v.stream().map(UUID::toString).toList()));
        return gson.toJson(serializable);
    }

    private Map<String, Set<UUID>> deserializeTeams(String json) {
        if (json == null || json.isEmpty()) {
            return new HashMap<>();
        }
        Map<String, List<String>> serializable = gson.fromJson(json, STRING_LIST_MAP);
        Map<String, Set<UUID>> result = new HashMap<>();
        if (serializable != null) {
            serializable.forEach((k, v) -> result.put(k, new HashSet<>(v.stream().map(UUID::fromString).toList())));
        }
        return result;
    }

    private Set<UUID> deserializeUUIDSet(String json) {
        if (json == null || json.isEmpty()) {
            return new HashSet<>();
        }
        List<String> list = gson.fromJson(json, STRING_LIST);
        Set<UUID> result = new HashSet<>();
        if (list != null) {
            list.forEach(s -> result.add(UUID.fromString(s)));
        }
        return result;
    }

    // ==================== Gson 适配器 ====================

    private static class InstantAdapter extends TypeAdapter<Instant> {
        @Override
        public void write(JsonWriter out, Instant value) throws IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.value(value.toString());
            }
        }

        @Override
        public Instant read(JsonReader in) throws IOException {
            if (in.peek() == com.google.gson.stream.JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            return Instant.parse(in.nextString());
        }
    }

    private static class UUIDAdapter extends TypeAdapter<UUID> {
        @Override
        public void write(JsonWriter out, UUID value) throws IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.value(value.toString());
            }
        }

        @Override
        public UUID read(JsonReader in) throws IOException {
            if (in.peek() == com.google.gson.stream.JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            return UUID.fromString(in.nextString());
        }
    }
}
