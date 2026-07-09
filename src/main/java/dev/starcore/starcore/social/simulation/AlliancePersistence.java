package dev.starcore.starcore.social.simulation;
import java.util.Optional;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.starcore.starcore.core.database.DatabaseService;
import dev.starcore.starcore.core.database.DatabaseUtils;
import dev.starcore.starcore.core.scheduler.StarCoreScheduler;
import dev.starcore.starcore.social.simulation.SocialAllianceService.SocialAlliance;
import org.bukkit.plugin.java.JavaPlugin;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * 社交联盟数据库持久化类
 *
 * 功能：
 * 1. 创建 alliance 数据库表
 * 2. 保存/加载/删除联盟数据
 * 3. 与 SocialAllianceService 集成
 *
 * 数据库表结构:
 * - id: 联盟ID (主键)
 * - name: 联盟名称
 * - tag: 联盟标签
 * - founder: 创始人UUID
 * - created_at: 创建时间戳
 * - type: 联盟类型
 * - legacy_points: 遗产点数
 * - members_json: 成员列表(JSON)
 * - applicants_json: 申请者列表(JSON)
 * - stats_json: 统计数据(JSON)
 */
public class AlliancePersistence {

    private static final String TABLE_NAME = "starcore_alliances";
    private static final Gson GSON = new GsonBuilder().create();

    private final JavaPlugin plugin;
    private final Logger logger;
    private final DatabaseService databaseService;
    private final StarCoreScheduler scheduler;

    // 内存缓存
    private final Map<String, SocialAlliance> allianceCache = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> playerAllianceIndex = new ConcurrentHashMap<>();

    public AlliancePersistence(JavaPlugin plugin, DatabaseService databaseService, StarCoreScheduler scheduler) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.databaseService = Objects.requireNonNull(databaseService, "databaseService");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.logger = plugin.getLogger();
    }

    /**
     * 初始化数据库表
     */
    public void initialize() {
        if (!databaseService.isRunning()) {
            logger.warning("[AlliancePersistence] 数据库未运行，联盟数据将不会持久化");
            return;
        }

        databaseService.dataSource().ifPresent(ds -> {
            try (Connection conn = ds.getConnection()) {
                createTable(conn);
                logger.info("[AlliancePersistence] 数据库表初始化完成");
            } catch (Exception e) {
                logger.severe("[AlliancePersistence] 创建表失败: " + e.getMessage());
            }
        });
    }

    private void createTable(Connection conn) throws SQLException {
        // 检测数据库类型
        boolean isSQLite = DatabaseUtils.detectDatabaseType(conn) == DatabaseUtils.DatabaseType.SQLITE;

        String sql;
        if (isSQLite) {
            sql = "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                "id TEXT PRIMARY KEY, " +
                "name TEXT NOT NULL, " +
                "tag TEXT NOT NULL, " +
                "founder TEXT NOT NULL, " +
                "created_at INTEGER NOT NULL, " +
                "type TEXT NOT NULL, " +
                "legacy_points INTEGER DEFAULT 0, " +
                "members_json TEXT NOT NULL, " +
                "applicants_json TEXT NOT NULL, " +
                "stats_json TEXT NOT NULL, " +
                "updated_at INTEGER NOT NULL" +
                ")";
        } else {
            sql = "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                "id VARCHAR(36) PRIMARY KEY, " +
                "name VARCHAR(64) NOT NULL, " +
                "tag VARCHAR(8) NOT NULL, " +
                "founder VARCHAR(36) NOT NULL, " +
                "created_at BIGINT NOT NULL, " +
                "type VARCHAR(32) NOT NULL, " +
                "legacy_points INT DEFAULT 0, " +
                "members_json TEXT NOT NULL, " +
                "applicants_json TEXT NOT NULL, " +
                "stats_json TEXT NOT NULL, " +
                "updated_at BIGINT NOT NULL" +
                ")";
        }

        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }

        // 创建索引以提高查询性能
        try (Statement stmt = conn.createStatement()) {
            if (isSQLite) {
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_alliances_founder ON " + TABLE_NAME + " (founder)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_alliances_type ON " + TABLE_NAME + " (type)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_alliances_legacy_points ON " + TABLE_NAME + " (legacy_points)");
            } else {
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_alliances_founder ON " + TABLE_NAME + " (founder)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_alliances_type ON " + TABLE_NAME + " (type)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_alliances_legacy_points ON " + TABLE_NAME + " (legacy_points DESC)");
            }
        }
    }

    // ==================== 核心持久化方法 ====================

    /**
     * 保存联盟到数据库
     *
     * @param alliance 要保存的联盟
     * @return 是否保存成功
     */
    public boolean saveAlliance(SocialAlliance alliance) {
        Objects.requireNonNull(alliance, "alliance");

        // 更新缓存
        allianceCache.put(alliance.id(), alliance);
        updatePlayerIndex(alliance);

        if (!databaseService.isRunning()) {
            logger.warning("[AlliancePersistence] 数据库未运行，无法保存联盟: " + alliance.id());
            return false;
        }

        return databaseService.dataSource().map(ds -> {
            try (Connection conn = ds.getConnection()) {
                saveAllianceToDatabase(conn, alliance);
                logger.fine("[AlliancePersistence] 已保存联盟: " + alliance.name() + " (" + alliance.id() + ")");
                return true;
            } catch (Exception e) {
                logger.severe("[AlliancePersistence] 保存联盟失败: " + e.getMessage());
                return false;
            }
        }).orElse(false);
    }

    /**
     * 异步保存联盟到数据库
     *
     * @param alliance 要保存的联盟
     */
    public void saveAllianceAsync(SocialAlliance alliance) {
        Objects.requireNonNull(alliance, "alliance");

        // 先更新缓存
        allianceCache.put(alliance.id(), alliance);
        updatePlayerIndex(alliance);

        if (!databaseService.isRunning()) {
            logger.warning("[AlliancePersistence] 数据库未运行，无法异步保存联盟: " + alliance.id());
            return;
        }

        // 异步执行数据库操作
        SocialAlliance allianceCopy = alliance;
        scheduler.runAsync(() -> {
            databaseService.dataSource().ifPresent(ds -> {
                try (Connection conn = ds.getConnection()) {
                    saveAllianceToDatabase(conn, allianceCopy);
                    logger.fine("[AlliancePersistence] 异步已保存联盟: " + allianceCopy.name());
                } catch (Exception e) {
                    logger.severe("[AlliancePersistence] 异步保存联盟失败: " + e.getMessage());
                }
            });
        });
    }

    /**
     * 加载所有联盟
     *
     * @return 联盟列表
     */
    public List<SocialAlliance> loadAlliances() {
        List<SocialAlliance> alliances = new ArrayList<>();

        // 先清空缓存
        allianceCache.clear();
        playerAllianceIndex.clear();

        if (!databaseService.isRunning()) {
            logger.warning("[AlliancePersistence] 数据库未运行，无法加载联盟");
            return alliances;
        }

        Optional<DataSource> ds = databaseService.dataSource();
        if (ds.isEmpty()) {
            return alliances;
        }

        try (Connection conn = ds.get().getConnection()) {
            String sql = "SELECT * FROM " + TABLE_NAME;
            try (PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {

                while (rs.next()) {
                    try {
                        SocialAlliance alliance = extractAlliance(rs);
                        if (alliance != null) {
                            alliances.add(alliance);
                            allianceCache.put(alliance.id(), alliance);
                            updatePlayerIndex(alliance);
                        }
                    } catch (Exception e) {
                        logger.warning("[AlliancePersistence] 解析联盟数据失败: " + e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            logger.severe("[AlliancePersistence] 加载联盟列表失败: " + e.getMessage());
        }

        logger.info("[AlliancePersistence] 已加载 " + alliances.size() + " 个联盟");
        return alliances;
    }

    /**
     * 加载指定ID的联盟
     *
     * @param id 联盟ID
     * @return 联盟对象，如果不存在则返回null
     */
    public SocialAlliance loadAlliance(String id) {
        Objects.requireNonNull(id, "id");

        // 先检查缓存
        SocialAlliance cached = allianceCache.get(id);
        if (cached != null) {
            return cached;
        }

        if (!databaseService.isRunning()) {
            return null;
        }

        Optional<DataSource> ds = databaseService.dataSource();
        if (ds.isEmpty()) {
            return null;
        }

        try (Connection conn = ds.get().getConnection()) {
            String sql = "SELECT * FROM " + TABLE_NAME + " WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, id);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        SocialAlliance alliance = extractAlliance(rs);
                        if (alliance != null) {
                            allianceCache.put(alliance.id(), alliance);
                            updatePlayerIndex(alliance);
                        }
                        return alliance;
                    }
                }
            }
        } catch (Exception e) {
            logger.severe("[AlliancePersistence] 加载联盟失败 (" + id + "): " + e.getMessage());
        }

        return null;
    }

    /**
     * 删除联盟
     *
     * @param id 联盟ID
     * @return 是否删除成功
     */
    public boolean deleteAlliance(String id) {
        Objects.requireNonNull(id, "id");

        // 从缓存中移除
        SocialAlliance alliance = allianceCache.remove(id);
        if (alliance != null) {
            // 清理玩家索引
            for (UUID memberId : alliance.members()) {
                Set<String> playerAlliances = playerAllianceIndex.get(memberId);
                if (playerAlliances != null) {
                    playerAlliances.remove(id);
                    if (playerAlliances.isEmpty()) {
                        playerAllianceIndex.remove(memberId);
                    }
                }
            }
        }

        if (!databaseService.isRunning()) {
            logger.warning("[AlliancePersistence] 数据库未运行，无法删除联盟: " + id);
            return false;
        }

        return databaseService.dataSource().map(ds -> {
            try (Connection conn = ds.getConnection()) {
                String sql = "DELETE FROM " + TABLE_NAME + " WHERE id = ?";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, id);
                    int affected = stmt.executeUpdate();
                    if (affected > 0) {
                        logger.info("[AlliancePersistence] 已删除联盟: " + id);
                        return true;
                    }
                }
            } catch (Exception e) {
                logger.severe("[AlliancePersistence] 删除联盟失败: " + e.getMessage());
            }
            return false;
        }).orElse(false);
    }

    // ==================== 辅助方法 ====================

    /**
     * 获取玩家参与的所有联盟ID
     *
     * @param playerId 玩家UUID
     * @return 联盟ID集合
     */
    public Set<String> getPlayerAlliances(UUID playerId) {
        Set<String> result = playerAllianceIndex.get(playerId);
        return result != null ? Set.copyOf(result) : Set.of();
    }

    /**
     * 获取缓存中的联盟数量
     */
    public int getCacheSize() {
        return allianceCache.size();
    }

    /**
     * 检查数据库是否可用
     */
    public boolean isDatabaseAvailable() {
        return databaseService.isRunning();
    }

    /**
     * 同步缓存到数据库
     */
    public void syncCacheToDatabase() {
        if (!databaseService.isRunning()) {
            return;
        }

        logger.info("[AlliancePersistence] 正在同步 " + allianceCache.size() + " 个联盟到数据库...");
        for (SocialAlliance alliance : allianceCache.values()) {
            saveAlliance(alliance);
        }
        logger.info("[AlliancePersistence] 缓存同步完成");
    }

    /**
     * 清空所有缓存
     */
    public void clearCache() {
        allianceCache.clear();
        playerAllianceIndex.clear();
        logger.info("[AlliancePersistence] 缓存已清空");
    }

    // ==================== 私有方法 ====================

    private void saveAllianceToDatabase(Connection conn, SocialAlliance alliance) throws SQLException {
        String membersJson = GSON.toJson(alliance.members().stream()
            .map(UUID::toString)
            .toList());
        String applicantsJson = GSON.toJson(alliance.applicants().stream()
            .map(UUID::toString)
            .toList());
        String statsJson = GSON.toJson(alliance.stats());
        long updatedAt = System.currentTimeMillis();

        // 检测数据库类型并使用对应的 UPSERT 语法
        boolean isSQLite = DatabaseUtils.detectDatabaseType(conn) == DatabaseUtils.DatabaseType.SQLITE;
        String sql;

        if (isSQLite) {
            sql = "INSERT INTO " + TABLE_NAME + " " +
                "(id, name, tag, founder, created_at, type, legacy_points, members_json, applicants_json, stats_json, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT(id) DO UPDATE SET " +
                "name = excluded.name, " +
                "tag = excluded.tag, " +
                "type = excluded.type, " +
                "legacy_points = excluded.legacy_points, " +
                "members_json = excluded.members_json, " +
                "applicants_json = excluded.applicants_json, " +
                "stats_json = excluded.stats_json, " +
                "updated_at = excluded.updated_at";
        } else {
            sql = "INSERT INTO " + TABLE_NAME + " " +
                "(id, name, tag, founder, created_at, type, legacy_points, members_json, applicants_json, stats_json, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE " +
                "name = VALUES(name), " +
                "tag = VALUES(tag), " +
                "type = VALUES(type), " +
                "legacy_points = VALUES(legacy_points), " +
                "members_json = VALUES(members_json), " +
                "applicants_json = VALUES(applicants_json), " +
                "stats_json = VALUES(stats_json), " +
                "updated_at = VALUES(updated_at)";
        }

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, alliance.id());
            stmt.setString(2, alliance.name());
            stmt.setString(3, alliance.tag());
            stmt.setString(4, alliance.founder().toString());
            stmt.setLong(5, alliance.createdAt());
            stmt.setString(6, alliance.type().name());
            stmt.setInt(7, alliance.legacyPoints());
            stmt.setString(8, membersJson);
            stmt.setString(9, applicantsJson);
            stmt.setString(10, statsJson);
            stmt.setLong(11, updatedAt);
            stmt.executeUpdate();
        }
    }

    private SocialAlliance extractAlliance(ResultSet rs) throws SQLException {
        String id = rs.getString("id");
        String name = rs.getString("name");
        String tag = rs.getString("tag");
        UUID founder = UUID.fromString(rs.getString("founder"));
        long createdAt = rs.getLong("created_at");
        SocialAllianceService.AllianceType type = SocialAllianceService.AllianceType.valueOf(rs.getString("type"));
        int legacyPoints = rs.getInt("legacy_points");

        // 解析JSON
        Set<UUID> members = parseUuidSet(rs.getString("members_json"));
        Set<UUID> applicants = parseUuidSet(rs.getString("applicants_json"));
        Map<String, Object> stats = parseStats(rs.getString("stats_json"));

        return new SocialAlliance(
            id,
            name,
            tag,
            founder,
            createdAt,
            members,
            applicants,
            type,
            stats,
            legacyPoints
        );
    }

    private Set<UUID> parseUuidSet(String json) {
        Set<UUID> result = new HashSet<>();
        if (json == null || json.isEmpty() || json.equals("[]")) {
            return result;
        }

        try {
            var element = JsonParser.parseString(json);
            if (element.isJsonArray()) {
                element.getAsJsonArray().forEach(e -> {
                    try {
                        result.add(UUID.fromString(e.getAsString()));
                    } catch (Exception e2) {
                        logger.warning("[AlliancePersistence] 解析UUID失败，跳过该条目: " + e.getAsString());
                    }
                });
            }
        } catch (Exception e) {
            logger.warning("[AlliancePersistence] 解析UUID列表失败: " + e.getMessage());
        }

        return result;
    }

    private Map<String, Object> parseStats(String json) {
        Map<String, Object> result = new HashMap<>();
        if (json == null || json.isEmpty() || json.equals("{}")) {
            return result;
        }

        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            obj.entrySet().forEach(entry -> {
                result.put(entry.getKey(), entry.getValue());
            });
        } catch (Exception e) {
            logger.warning("[AlliancePersistence] 解析stats JSON失败: " + e.getMessage());
        }

        return result;
    }

    private void updatePlayerIndex(SocialAlliance alliance) {
        for (UUID memberId : alliance.members()) {
            playerAllianceIndex.computeIfAbsent(memberId, k -> new HashSet<>()).add(alliance.id());
        }
    }
}
