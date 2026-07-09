package dev.starcore.starcore.war;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.starcore.starcore.core.database.DatabaseService;
import dev.starcore.starcore.module.nation.model.NationId;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * 战争状态存储（数据库持久化实现）
 * 支持 SQLite/MySQL 文件存储和 SQL 数据库存储
 */
public class WarStateStorage {
    private static final String WARS_DIR = "wars";
    private static final String TABLE_NAME = "starcore_war_state";
    private static final Gson GSON = new GsonBuilder().create();

    private final JavaPlugin plugin;
    private final Logger logger;
    private final DatabaseService databaseService;
    private final Path warsDir;
    private final Map<UUID, War> warCache = new ConcurrentHashMap<>();

    public WarStateStorage(JavaPlugin plugin, DatabaseService databaseService) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.databaseService = Objects.requireNonNull(databaseService, "databaseService");
        this.logger = plugin.getLogger();
        this.warsDir = plugin.getDataFolder().toPath().resolve(WARS_DIR);
        initStorage();
    }

    private void initStorage() {
        try {
            Files.createDirectories(warsDir);
        } catch (IOException e) {
            logger.severe("Failed to create wars directory: " + e.getMessage());
        }
    }

    /**
     * 初始化数据库表（如果使用 SQL 存储）
     */
    public void ensureDatabaseTable() {
        if (!databaseService.isRunning()) {
            return;
        }

        try {
            databaseService.dataSource().ifPresent(ds -> {
                try (Connection conn = ds.getConnection()) {
                    String sql = "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                        "war_id VARCHAR(36) PRIMARY KEY, " +
                        "war_json TEXT NOT NULL, " +
                        "updated_at BIGINT NOT NULL)";
                    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                        stmt.execute();
                    }
                } catch (Exception e) {
                    logger.warning("Failed to create war table: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            logger.warning("Database not available, using file storage: " + e.getMessage());
        }
    }

    // ==================== 核心持久化方法 ====================

    /**
     * 保存战争到数据库
     */
    public void saveWar(War war) {
        Objects.requireNonNull(war, "war");
        warCache.put(war.id(), war);

        if (databaseService.isRunning()) {
            saveWarToDatabase(war);
        } else {
            saveWarToFile(war);
        }
    }

    /**
     * 从数据库加载单个战争
     */
    public War loadWar(UUID warId) {
        Objects.requireNonNull(warId, "warId");

        // 先检查缓存
        War cached = warCache.get(warId);
        if (cached != null) {
            return cached;
        }

        // 从存储加载
        War war;
        if (databaseService.isRunning()) {
            war = loadWarFromDatabase(warId);
        } else {
            war = loadWarFromFile(warId);
        }

        if (war != null) {
            warCache.put(warId, war);
        }
        return war;
    }

    /**
     * 从数据库加载所有战争
     */
    public Collection<War> loadAllWars() {
        List<War> allWars;

        if (databaseService.isRunning()) {
            allWars = loadAllWarsFromDatabase();
        } else {
            allWars = loadAllWarsFromFiles();
        }

        // 更新缓存
        warCache.clear();
        warCache.putAll(allWars.stream().collect(HashMap::new, (m, w) -> m.put(w.id(), w), HashMap::putAll));

        return allWars;
    }

    /**
     * 从数据库删除战争
     */
    public void deleteWar(UUID warId) {
        Objects.requireNonNull(warId, "warId");

        warCache.remove(warId);

        if (databaseService.isRunning()) {
            deleteWarFromDatabase(warId);
        } else {
            deleteWarFromFile(warId);
        }
        logger.info("Deleted war from storage: " + warId);
    }

    // ==================== 条约数据持久化 ====================

    private static final String TREATY_TABLE_NAME = "starcore_treaty_data";
    private final Map<UUID, Map<String, String>> treatyDataCache = new ConcurrentHashMap<>();

    /**
     * 保存条约相关数据（赔款、限制等）
     */
    public void saveTreatyData(UUID treatyId, String dataKey, String dataValue) {
        treatyDataCache.computeIfAbsent(treatyId, k -> new ConcurrentHashMap<>()).put(dataKey, dataValue);

        if (databaseService.isRunning()) {
            saveTreatyDataToDatabase(treatyId, dataKey, dataValue);
        } else {
            saveTreatyDataToFile(treatyId, dataKey, dataValue);
        }
    }

    /**
     * 加载条约相关数据
     */
    public Map<String, String> loadTreatyData(UUID treatyId) {
        if (treatyDataCache.containsKey(treatyId)) {
            return treatyDataCache.get(treatyId);
        }

        Map<String, String> data = new HashMap<>();
        if (databaseService.isRunning()) {
            data = loadTreatyDataFromDatabase(treatyId);
        } else {
            data = loadTreatyDataFromFile(treatyId);
        }

        if (!data.isEmpty()) {
            treatyDataCache.put(treatyId, data);
        }
        return data;
    }

    private void saveTreatyDataToDatabase(UUID treatyId, String dataKey, String dataValue) {
        ensureTreatyTable();
        databaseService.dataSource().ifPresent(ds -> {
            try (Connection conn = ds.getConnection()) {
                String productName = conn.getMetaData().getDatabaseProductName();
                boolean isSQLite = "SQLite".equalsIgnoreCase(productName);

                String sql;
                if (isSQLite) {
                    sql = "INSERT INTO " + TREATY_TABLE_NAME + " (treaty_id, data_key, data_value, updated_at) " +
                        "VALUES (?, ?, ?, ?) ON CONFLICT(treaty_id, data_key) DO UPDATE SET " +
                        "data_value = excluded.data_value, updated_at = excluded.updated_at";
                } else {
                    sql = "INSERT INTO " + TREATY_TABLE_NAME + " (treaty_id, data_key, data_value, updated_at) " +
                        "VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE " +
                        "data_value = VALUES(data_value), updated_at = VALUES(updated_at)";
                }

                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, treatyId.toString());
                    stmt.setString(2, dataKey);
                    stmt.setString(3, dataValue);
                    stmt.setLong(4, Instant.now().toEpochMilli());
                    stmt.executeUpdate();
                    logger.fine("Saved treaty data to database: treaty=" + treatyId + ", key=" + dataKey);
                }
            } catch (Exception e) {
                logger.severe("Failed to save treaty data: " + e.getMessage());
            }
        });
    }

    private void saveTreatyDataToFile(UUID treatyId, String dataKey, String dataValue) {
        Path filePath = warsDir.resolve("treaty_" + treatyId.toString() + ".txt");
        try {
            Map<String, String> data = new HashMap<>();
            if (Files.exists(filePath)) {
                Files.lines(filePath).forEach(line -> {
                    String[] parts = line.split("=", 2);
                    if (parts.length == 2) {
                        data.put(parts[0], parts[1]);
                    }
                });
            }
            data.put(dataKey, dataValue);
            Files.writeString(filePath, data.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("\n")));
            logger.fine("Saved treaty data to file: treaty=" + treatyId + ", key=" + dataKey);
        } catch (IOException e) {
            logger.severe("Failed to save treaty data: " + e.getMessage());
        }
    }

    private Map<String, String> loadTreatyDataFromDatabase(UUID treatyId) {
        ensureTreatyTable();
        Map<String, String> data = new HashMap<>();
        databaseService.dataSource().ifPresent(ds -> {
            try (Connection conn = ds.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                     "SELECT data_key, data_value FROM " + TREATY_TABLE_NAME + " WHERE treaty_id = ?")) {
                stmt.setString(1, treatyId.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        data.put(rs.getString("data_key"), rs.getString("data_value"));
                    }
                }
            } catch (Exception e) {
                logger.warning("Failed to load treaty data: " + e.getMessage());
            }
        });
        return data;
    }

    private Map<String, String> loadTreatyDataFromFile(UUID treatyId) {
        Path filePath = warsDir.resolve("treaty_" + treatyId.toString() + ".txt");
        Map<String, String> data = new HashMap<>();
        if (Files.exists(filePath)) {
            try {
                Files.lines(filePath).forEach(line -> {
                    String[] parts = line.split("=", 2);
                    if (parts.length == 2) {
                        data.put(parts[0], parts[1]);
                    }
                });
            } catch (IOException e) {
                logger.warning("Failed to load treaty data from file: " + e.getMessage());
            }
        }
        return data;
    }

    private void ensureTreatyTable() {
        if (!databaseService.isRunning()) return;
        databaseService.dataSource().ifPresent(ds -> {
            try (Connection conn = ds.getConnection()) {
                String sql = "CREATE TABLE IF NOT EXISTS " + TREATY_TABLE_NAME + " (" +
                    "treaty_id VARCHAR(36) NOT NULL, " +
                    "data_key VARCHAR(255) NOT NULL, " +
                    "data_value TEXT NOT NULL, " +
                    "updated_at BIGINT NOT NULL, " +
                    "PRIMARY KEY (treaty_id, data_key))";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.execute();
                }
            } catch (Exception e) {
                logger.warning("Failed to create treaty table: " + e.getMessage());
            }
        });
    }

    /**
     * 查询国家参与的所有战争（优化查询）
     */
    public List<War> getWarsInvolvingNation(NationId nationId) {
        Objects.requireNonNull(nationId, "nationId");

        List<War> result = new ArrayList<>();

        if (databaseService.isRunning()) {
            result = getWarsInvolvingNationFromDatabase(nationId);
        } else {
            // 从缓存中过滤
            for (War war : warCache.values()) {
                if (war.isParticipant(nationId)) {
                    result.add(war);
                }
            }
        }

        return result;
    }

    // ==================== 文件存储实现 ====================

    private void saveWarToFile(War war) {
        Path filePath = warsDir.resolve(war.id().toString() + ".json");
        try {
            JsonObject json = encodeWarToJson(war);
            Files.writeString(filePath, GSON.toJson(json));
            logger.fine("Saved war to file: " + war.id());
        } catch (IOException e) {
            logger.severe("Failed to save war " + war.id() + ": " + e.getMessage());
        }
    }

    private War loadWarFromFile(UUID warId) {
        Path filePath = warsDir.resolve(warId.toString() + ".json");
        if (!Files.exists(filePath)) {
            return null;
        }

        try {
            String content = Files.readString(filePath);
            JsonObject json = JsonParser.parseString(content).getAsJsonObject();
            return decodeWarFromJson(json);
        } catch (Exception e) {
            logger.warning("Failed to load war " + warId + " from file: " + e.getMessage());
            return null;
        }
    }

    private List<War> loadAllWarsFromFiles() {
        List<War> wars = new ArrayList<>();

        try {
            var files = Files.list(warsDir)
                .filter(p -> p.toString().endsWith(".json"))
                .toList();

            for (Path file : files) {
                try {
                    String content = Files.readString(file);
                    JsonObject json = JsonParser.parseString(content).getAsJsonObject();
                    War war = decodeWarFromJson(json);
                    if (war != null) {
                        wars.add(war);
                    }
                } catch (Exception e) {
                    logger.warning("Failed to load war from " + file.getFileName() + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            logger.severe("Failed to list war files: " + e.getMessage());
        }

        logger.info("Loaded " + wars.size() + " wars from file storage");
        return wars;
    }

    private void deleteWarFromFile(UUID warId) {
        Path filePath = warsDir.resolve(warId.toString() + ".json");
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            logger.warning("Failed to delete war file " + warId + ": " + e.getMessage());
        }
    }

    // ==================== 数据库存储实现 ====================

    private void saveWarToDatabase(War war) {
        databaseService.dataSource().ifPresent(ds -> {
            String json = GSON.toJson(encodeWarToJson(war));
            long updatedAt = Instant.now().toEpochMilli();

            try (Connection conn = ds.getConnection()) {
                String productName = conn.getMetaData().getDatabaseProductName();
                boolean isSQLite = "SQLite".equalsIgnoreCase(productName);

                String sql;
                if (isSQLite) {
                    sql = "INSERT INTO " + TABLE_NAME + " (war_id, war_json, updated_at) " +
                        "VALUES (?, ?, ?) ON CONFLICT(war_id) DO UPDATE SET " +
                        "war_json = excluded.war_json, updated_at = excluded.updated_at";
                } else {
                    sql = "INSERT INTO " + TABLE_NAME + " (war_id, war_json, updated_at) " +
                        "VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE " +
                        "war_json = VALUES(war_json), updated_at = VALUES(updated_at)";
                }

                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, war.id().toString());
                    stmt.setString(2, json);
                    stmt.setLong(3, updatedAt);
                    stmt.executeUpdate();
                    logger.fine("Saved war to database: " + war.id());
                }
            } catch (Exception e) {
                logger.severe("Failed to save war " + war.id() + " to database: " + e.getMessage());
            }
        });
    }

    private War loadWarFromDatabase(UUID warId) {
        return databaseService.dataSource().map(ds -> {
            try (Connection conn = ds.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                     "SELECT war_json FROM " + TABLE_NAME + " WHERE war_id = ?")) {
                stmt.setString(1, warId.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        String json = rs.getString("war_json");
                        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
                        return decodeWarFromJson(obj);
                    }
                }
            } catch (Exception e) {
                logger.warning("Failed to load war " + warId + " from database: " + e.getMessage());
            }
            return null;
        }).orElse(null);
    }

    private List<War> loadAllWarsFromDatabase() {
        List<War> wars = new ArrayList<>();

        databaseService.dataSource().ifPresent(ds -> {
            try (Connection conn = ds.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                     "SELECT war_json FROM " + TABLE_NAME);
                 ResultSet rs = stmt.executeQuery()) {

                while (rs.next()) {
                    try {
                        String json = rs.getString("war_json");
                        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
                        War war = decodeWarFromJson(obj);
                        if (war != null) {
                            wars.add(war);
                        }
                    } catch (Exception e) {
                        logger.warning("Failed to parse war JSON: " + e.getMessage());
                    }
                }
            } catch (Exception e) {
                logger.severe("Failed to load wars from database: " + e.getMessage());
            }
        });

        logger.info("Loaded " + wars.size() + " wars from database");
        return wars;
    }

    private void deleteWarFromDatabase(UUID warId) {
        databaseService.dataSource().ifPresent(ds -> {
            try (Connection conn = ds.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                     "DELETE FROM " + TABLE_NAME + " WHERE war_id = ?")) {
                stmt.setString(1, warId.toString());
                stmt.executeUpdate();
            } catch (Exception e) {
                logger.warning("Failed to delete war " + warId + " from database: " + e.getMessage());
            }
        });
    }

    private List<War> getWarsInvolvingNationFromDatabase(NationId nationId) {
        List<War> wars = new ArrayList<>();
        String nationIdStr = nationId.value().toString();

        databaseService.dataSource().ifPresent(ds -> {
            try (Connection conn = ds.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                     "SELECT war_json FROM " + TABLE_NAME)) {

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        try {
                            String json = rs.getString("war_json");
                            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();

                            // 检查是否涉及该国家
                            if (involvesNation(obj, nationIdStr)) {
                                War war = decodeWarFromJson(obj);
                                if (war != null) {
                                    wars.add(war);
                                }
                            }
                        } catch (Exception e) {
                            logger.warning("Failed to parse war JSON: " + e.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                logger.severe("Failed to query wars involving nation: " + e.getMessage());
            }
        });

        return wars;
    }

    // ==================== JSON 编解码 ====================

    private JsonObject encodeWarToJson(War war) {
        JsonObject json = new JsonObject();

        json.addProperty("id", war.id().toString());
        json.addProperty("name", war.name());
        json.addProperty("aggressor", war.aggressor().value().toString());
        json.addProperty("defender", war.defender().value().toString());
        json.addProperty("status", war.status().name());
        json.addProperty("goal", war.goal().name());
        json.addProperty("aggressorWarScore", war.aggressorWarScore());
        json.addProperty("defenderWarScore", war.defenderWarScore());
        json.addProperty("declaredAt", war.declaredAt().toEpochMilli());

        if (war.startedAt() != null) {
            json.addProperty("startedAt", war.startedAt().toEpochMilli());
        }
        if (war.endedAt() != null) {
            json.addProperty("endedAt", war.endedAt().toEpochMilli());
        }
        json.addProperty("lastUpdated", war.lastUpdated().toEpochMilli());

        // 编码盟友
        json.add("aggressorAllies", GSON.toJsonTree(war.aggressorAllies().stream()
            .map(n -> n.value().toString()).toList()).getAsJsonArray());
        json.add("defenderAllies", GSON.toJsonTree(war.defenderAllies().stream()
            .map(n -> n.value().toString()).toList()).getAsJsonArray());

        return json;
    }

    private War decodeWarFromJson(JsonObject json) {
        try {
            UUID id = UUID.fromString(json.get("id").getAsString());
            String name = json.get("name").getAsString();
            NationId aggressor = NationId.of(UUID.fromString(json.get("aggressor").getAsString()));
            NationId defender = NationId.of(UUID.fromString(json.get("defender").getAsString()));
            WarStatus status = WarStatus.valueOf(json.get("status").getAsString());
            WarGoal goal = WarGoal.valueOf(json.get("goal").getAsString());
            int aggressorScore = json.get("aggressorWarScore").getAsInt();
            int defenderScore = json.get("defenderWarScore").getAsInt();
            Instant declaredAt = Instant.ofEpochMilli(json.get("declaredAt").getAsLong());
            Instant lastUpdated = json.has("lastUpdated")
                ? Instant.ofEpochMilli(json.get("lastUpdated").getAsLong())
                : declaredAt;

            // 解码盟友
            Set<NationId> aggressorAllies = new HashSet<>();
            Set<NationId> defenderAllies = new HashSet<>();

            if (json.has("aggressorAllies")) {
                json.getAsJsonArray("aggressorAllies").forEach(e ->
                    aggressorAllies.add(NationId.of(UUID.fromString(e.getAsString()))));
            }
            if (json.has("defenderAllies")) {
                json.getAsJsonArray("defenderAllies").forEach(e ->
                    defenderAllies.add(NationId.of(UUID.fromString(e.getAsString()))));
            }

            // 使用包级构造函数重建 War 对象
            return new War(
                id,
                name,
                aggressor,
                defender,
                status,
                goal,
                aggressorScore,
                defenderScore,
                aggressorAllies,
                defenderAllies,
                declaredAt,
                json.has("startedAt") ? Instant.ofEpochMilli(json.get("startedAt").getAsLong()) : null,
                json.has("endedAt") ? Instant.ofEpochMilli(json.get("endedAt").getAsLong()) : null,
                lastUpdated
            );
        } catch (Exception e) {
            logger.warning("Failed to decode war from JSON: " + e.getMessage());
            return null;
        }
    }

    private boolean involvesNation(JsonObject json, String nationIdStr) {
        // 检查进攻方
        if (nationIdStr.equals(json.get("aggressor").getAsString())) {
            return true;
        }
        // 检查防守方
        if (nationIdStr.equals(json.get("defender").getAsString())) {
            return true;
        }
        // 检查进攻方盟友
        if (json.has("aggressorAllies")) {
            for (JsonElement ally : json.getAsJsonArray("aggressorAllies")) {
                if (nationIdStr.equals(ally.getAsString())) {
                    return true;
                }
            }
        }
        // 检查防守方盟友
        if (json.has("defenderAllies")) {
            for (JsonElement ally : json.getAsJsonArray("defenderAllies")) {
                if (nationIdStr.equals(ally.getAsString())) {
                    return true;
                }
            }
        }
        return false;
    }

}
