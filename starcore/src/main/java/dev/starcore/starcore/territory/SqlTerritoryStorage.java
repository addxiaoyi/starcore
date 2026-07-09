package dev.starcore.starcore.territory;
import java.util.Optional;

import dev.starcore.starcore.core.database.DatabaseService;
import dev.starcore.starcore.core.database.DatabaseUtils;
import dev.starcore.starcore.core.persistence.PersistenceService;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * SQL 领土存储实现
 *
 * 支持双模式存储：
 * 1. SQL 数据库存储（生产模式）
 * 2. Properties 文件存储（降级模式）
 *
 * 持久化内容：
 * - 领土边界、所有者、权限、成员
 * - 子区域边界、权限、成员、优先级
 */
final class SqlTerritoryStorage implements TerritoryStorage {

    private static final String TERRITORY_TABLE = "starcore_territories";
    private static final String SUBREGION_TABLE = "starcore_subregions";
    private static final String MEMBER_TABLE = "starcore_territory_members";
    private static final String TERRITORY_PERM_TABLE = "starcore_territories_permissions";
    private static final String SUBREGION_PERM_TABLE = "starcore_subregions_permissions";

    private final DatabaseService databaseService;
    private final PersistenceService persistenceService;
    private final Logger logger;

    SqlTerritoryStorage(DatabaseService databaseService, PersistenceService persistenceService, Logger logger) {
        this.databaseService = Objects.requireNonNull(databaseService, "databaseService");
        this.persistenceService = Objects.requireNonNull(persistenceService, "persistenceService");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public Collection<Territory> loadTerritories() {
        List<Territory> territories = new ArrayList<>();

        // 尝试从 SQL 加载
        Optional<DataSource> ds = databaseService.dataSource();
        if (ds.isPresent()) {
            try (Connection conn = ds.get().getConnection()) {
                ensureTables(conn);
                territories = loadTerritoriesFromSql(conn);
                if (!territories.isEmpty()) {
                    logger.info("[TerritoryStorage] 从 SQL 加载了 " + territories.size() + " 个领土");
                    return territories;
                }
            } catch (Exception e) {
                logger.warning("[TerritoryStorage] SQL 加载失败，降级到 Properties: " + e.getMessage());
            }
        }

        // 降级到 Properties
        return loadTerritoriesFromProperties();
    }

    @Override
    public void saveTerritories(Collection<Territory> territories) {
        Optional<DataSource> ds = databaseService.dataSource();
        if (ds.isPresent()) {
            try (Connection conn = ds.get().getConnection()) {
                saveTerritoriesToSql(conn, territories);
                return;
            } catch (Exception e) {
                logger.warning("[TerritoryStorage] SQL 保存失败，降级到 Properties: " + e.getMessage());
            }
        }

        // 降级到 Properties
        saveTerritoriesToProperties(territories);
    }

    @Override
    public Collection<SubRegion> loadSubRegions() {
        List<SubRegion> subRegions = new ArrayList<>();

        Optional<DataSource> ds = databaseService.dataSource();
        if (ds.isPresent()) {
            try (Connection conn = ds.get().getConnection()) {
                subRegions = loadSubRegionsFromSql(conn);
                if (!subRegions.isEmpty()) {
                    logger.info("[TerritoryStorage] 从 SQL 加载了 " + subRegions.size() + " 个子区域");
                    return subRegions;
                }
            } catch (Exception e) {
                logger.warning("[TerritoryStorage] 子区域 SQL 加载失败: " + e.getMessage());
            }
        }

        return loadSubRegionsFromProperties();
    }

    @Override
    public void saveSubRegions(Collection<SubRegion> subRegions) {
        Optional<DataSource> ds = databaseService.dataSource();
        if (ds.isPresent()) {
            try (Connection conn = ds.get().getConnection()) {
                saveSubRegionsToSql(conn, subRegions);
                return;
            } catch (Exception e) {
                logger.warning("[TerritoryStorage] 子区域 SQL 保存失败: " + e.getMessage());
            }
        }

        saveSubRegionsToProperties(subRegions);
    }

    @Override
    public void saveAllAsync(Collection<Territory> territories, Collection<SubRegion> subRegions) {
        CompletableFuture.runAsync(() -> {
            saveTerritories(territories);
            saveSubRegions(subRegions);
        });
    }

    @Override
    public boolean isUsingSql() {
        return databaseService.isRunning();
    }

    // ==================== SQL 表操作 ====================

    private void ensureTables(Connection conn) throws SQLException {
        DatabaseUtils.DatabaseType dbType = DatabaseUtils.detectDatabaseType(conn);
        boolean isSQLite = dbType == DatabaseUtils.DatabaseType.SQLITE;

        try (Statement stmt = conn.createStatement()) {
            if (isSQLite) {
                // SQLite 版本 - 使用 TEXT 和 INTEGER 类型
                stmt.execute("CREATE TABLE IF NOT EXISTS " + TERRITORY_TABLE + " (" +
                    "territory_id TEXT PRIMARY KEY," +
                    "name TEXT NOT NULL," +
                    "owner_id TEXT NOT NULL," +
                    "nation_id TEXT," +
                    "world_name TEXT NOT NULL," +
                    "min_x INTEGER NOT NULL,min_y INTEGER NOT NULL,min_z INTEGER NOT NULL," +
                    "max_x INTEGER NOT NULL,max_y INTEGER NOT NULL,max_z INTEGER NOT NULL," +
                    "type TEXT NOT NULL," +
                    "spawn_x REAL,spawn_y REAL,spawn_z REAL," +
                    "enabled INTEGER DEFAULT 1," +
                    "created_time INTEGER NOT NULL)");

                stmt.execute("CREATE TABLE IF NOT EXISTS " + TERRITORY_PERM_TABLE + " (" +
                    "territory_id TEXT NOT NULL," +
                    "permission TEXT NOT NULL," +
                    "level TEXT NOT NULL," +
                    "PRIMARY KEY (territory_id,permission)," +
                    "FOREIGN KEY (territory_id) REFERENCES " + TERRITORY_TABLE + "(territory_id) ON DELETE CASCADE)");

                stmt.execute("CREATE TABLE IF NOT EXISTS " + MEMBER_TABLE + " (" +
                    "entity_id TEXT NOT NULL," +
                    "entity_type TEXT NOT NULL," +
                    "player_id TEXT NOT NULL," +
                    "permission_level TEXT NOT NULL," +
                    "PRIMARY KEY (entity_id,entity_type,player_id))");

                stmt.execute("CREATE TABLE IF NOT EXISTS " + SUBREGION_TABLE + " (" +
                    "subregion_id TEXT PRIMARY KEY," +
                    "parent_territory_id TEXT NOT NULL," +
                    "name TEXT NOT NULL," +
                    "world_name TEXT NOT NULL," +
                    "min_x INTEGER NOT NULL,min_y INTEGER NOT NULL,min_z INTEGER NOT NULL," +
                    "max_x INTEGER NOT NULL,max_y INTEGER NOT NULL,max_z INTEGER NOT NULL," +
                    "priority INTEGER DEFAULT 0," +
                    "inherit_permissions INTEGER DEFAULT 1," +
                    "description TEXT," +
                    "enabled INTEGER DEFAULT 1," +
                    "created_time INTEGER NOT NULL," +
                    "FOREIGN KEY (parent_territory_id) REFERENCES " + TERRITORY_TABLE + "(territory_id) ON DELETE CASCADE)");

                stmt.execute("CREATE TABLE IF NOT EXISTS " + SUBREGION_PERM_TABLE + " (" +
                    "subregion_id TEXT NOT NULL," +
                    "permission TEXT NOT NULL," +
                    "level TEXT NOT NULL," +
                    "PRIMARY KEY (subregion_id,permission)," +
                    "FOREIGN KEY (subregion_id) REFERENCES " + SUBREGION_TABLE + "(subregion_id) ON DELETE CASCADE)");
            } else {
                // MySQL 版本
                stmt.execute("CREATE TABLE IF NOT EXISTS " + TERRITORY_TABLE + " (" +
                    "territory_id CHAR(36) PRIMARY KEY," +
                    "name VARCHAR(64) NOT NULL," +
                    "owner_id CHAR(36) NOT NULL," +
                    "nation_id CHAR(36)," +
                    "world_name VARCHAR(64) NOT NULL," +
                    "min_x INT NOT NULL,min_y INT NOT NULL,min_z INT NOT NULL," +
                    "max_x INT NOT NULL,max_y INT NOT NULL,max_z INT NOT NULL," +
                    "type VARCHAR(32) NOT NULL," +
                    "spawn_x DOUBLE,spawn_y DOUBLE,spawn_z DOUBLE," +
                    "enabled BOOLEAN DEFAULT TRUE," +
                    "created_time BIGINT NOT NULL)");

                stmt.execute("CREATE TABLE IF NOT EXISTS " + TERRITORY_PERM_TABLE + " (" +
                    "territory_id CHAR(36) NOT NULL," +
                    "permission VARCHAR(32) NOT NULL," +
                    "level VARCHAR(16) NOT NULL," +
                    "PRIMARY KEY (territory_id,permission)," +
                    "FOREIGN KEY (territory_id) REFERENCES " + TERRITORY_TABLE + "(territory_id) ON DELETE CASCADE)");

                stmt.execute("CREATE TABLE IF NOT EXISTS " + MEMBER_TABLE + " (" +
                    "entity_id CHAR(36) NOT NULL," +
                    "entity_type VARCHAR(16) NOT NULL," +
                    "player_id CHAR(36) NOT NULL," +
                    "permission_level VARCHAR(16) NOT NULL," +
                    "PRIMARY KEY (entity_id,entity_type,player_id))");

                stmt.execute("CREATE TABLE IF NOT EXISTS " + SUBREGION_TABLE + " (" +
                    "subregion_id CHAR(36) PRIMARY KEY," +
                    "parent_territory_id CHAR(36) NOT NULL," +
                    "name VARCHAR(64) NOT NULL," +
                    "world_name VARCHAR(64) NOT NULL," +
                    "min_x INT NOT NULL,min_y INT NOT NULL,min_z INT NOT NULL," +
                    "max_x INT NOT NULL,max_y INT NOT NULL,max_z INT NOT NULL," +
                    "priority INT DEFAULT 0," +
                    "inherit_permissions BOOLEAN DEFAULT TRUE," +
                    "description TEXT," +
                    "enabled BOOLEAN DEFAULT TRUE," +
                    "created_time BIGINT NOT NULL," +
                    "FOREIGN KEY (parent_territory_id) REFERENCES " + TERRITORY_TABLE + "(territory_id) ON DELETE CASCADE)");

                stmt.execute("CREATE TABLE IF NOT EXISTS " + SUBREGION_PERM_TABLE + " (" +
                    "subregion_id CHAR(36) NOT NULL," +
                    "permission VARCHAR(32) NOT NULL," +
                    "level VARCHAR(16) NOT NULL," +
                    "PRIMARY KEY (subregion_id,permission)," +
                    "FOREIGN KEY (subregion_id) REFERENCES " + SUBREGION_TABLE + "(subregion_id) ON DELETE CASCADE)");
            }
        }
    }

    // ==================== 从 SQL 加载 ====================

    private List<Territory> loadTerritoriesFromSql(Connection conn) throws SQLException {
        Map<UUID, Territory> territoryMap = new LinkedHashMap<>();

        // 加载领土
        try (PreparedStatement stmt = conn.prepareStatement(
            "SELECT * FROM " + TERRITORY_TABLE)) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Territory territory = extractTerritory(rs);
                    territoryMap.put(territory.getId(), territory);
                }
            }
        }

        // 加载权限
        try (PreparedStatement stmt = conn.prepareStatement(
            "SELECT * FROM " + TERRITORY_PERM_TABLE)) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    UUID territoryId = UUID.fromString(rs.getString("territory_id"));
                    Territory territory = territoryMap.get(territoryId);
                    if (territory != null) {
                        TerritoryPermission perm = TerritoryPermission.valueOf(rs.getString("permission"));
                        PermissionLevel level = PermissionLevel.valueOf(rs.getString("level"));
                        territory.setPermission(perm, level);
                    }
                }
            }
        }

        // 加载成员
        loadMembersFromSql(conn, territoryMap, "TERRITORY");

        return new ArrayList<>(territoryMap.values());
    }

    @SuppressWarnings("unchecked")
    private void loadMembersFromSql(Connection conn, Map<UUID, ?> entities, String entityType) throws SQLException {
        if (entities.isEmpty()) return;

        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < entities.size(); i++) {
            if (i > 0) placeholders.append(",");
            placeholders.append("?");
        }

        try (PreparedStatement stmt = conn.prepareStatement(
            "SELECT * FROM " + MEMBER_TABLE + " WHERE entity_id IN (" + placeholders + ") AND entity_type = ?")) {
            int idx = 1;
            for (UUID id : entities.keySet()) {
                stmt.setString(idx++, id.toString());
            }
            stmt.setString(idx, entityType);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    UUID entityId = UUID.fromString(rs.getString("entity_id"));
                    UUID playerId = UUID.fromString(rs.getString("player_id"));
                    PermissionLevel level = PermissionLevel.valueOf(rs.getString("permission_level"));

                    Object entity = entities.get(entityId);
                    if (entity instanceof Territory) {
                        ((Territory) entity).addMember(playerId, level);
                    } else if (entity instanceof SubRegion) {
                        ((SubRegion) entity).addMember(playerId, level);
                    }
                }
            }
        }
    }

    private List<SubRegion> loadSubRegionsFromSql(Connection conn) throws SQLException {
        Map<UUID, SubRegion> subRegionMap = new LinkedHashMap<>();

        // 加载子区域
        try (PreparedStatement stmt = conn.prepareStatement(
            "SELECT * FROM " + SUBREGION_TABLE)) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    SubRegion subRegion = extractSubRegion(rs);
                    subRegionMap.put(subRegion.getId(), subRegion);
                }
            }
        }

        // 加载权限覆盖
        try (PreparedStatement stmt = conn.prepareStatement(
            "SELECT * FROM " + SUBREGION_PERM_TABLE)) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    UUID subRegionId = UUID.fromString(rs.getString("subregion_id"));
                    SubRegion subRegion = subRegionMap.get(subRegionId);
                    if (subRegion != null) {
                        TerritoryPermission perm = TerritoryPermission.valueOf(rs.getString("permission"));
                        PermissionLevel level = PermissionLevel.valueOf(rs.getString("level"));
                        subRegion.setOverridePermission(perm, level);
                    }
                }
            }
        }

        // 加载成员
        loadMembersFromSql(conn, subRegionMap, "SUBREGION");

        return new ArrayList<>(subRegionMap.values());
    }

    private Territory extractTerritory(ResultSet rs) throws SQLException {
        UUID id = UUID.fromString(rs.getString("territory_id"));
        String name = rs.getString("name");
        UUID ownerId = UUID.fromString(rs.getString("owner_id"));
        UUID nationId = null;
        String nationIdStr = rs.getString("nation_id");
        if (nationIdStr != null && !nationIdStr.isEmpty()) {
            nationId = UUID.fromString(nationIdStr);
        }
        String worldName = rs.getString("world_name");
        int minX = rs.getInt("min_x"), minY = rs.getInt("min_y"), minZ = rs.getInt("min_z");
        int maxX = rs.getInt("max_x"), maxY = rs.getInt("max_y"), maxZ = rs.getInt("max_z");
        TerritoryType type = TerritoryType.valueOf(rs.getString("type"));
        // SQLite 使用 INTEGER (0/1)，MySQL 使用 BOOLEAN
        boolean enabled;
        try {
            enabled = rs.getBoolean("enabled");
        } catch (SQLException e) {
            enabled = rs.getInt("enabled") == 1;
        }
        long createdTime = rs.getLong("created_time");

        Territory territory = new Territory(id, name, ownerId, worldName, minX, minY, minZ, maxX, maxY, maxZ);
        territory.setNationId(nationId);
        territory.setType(type);
        territory.setEnabled(enabled);

        // 反射设置 createdTime
        try {
            java.lang.reflect.Field field = Territory.class.getDeclaredField("createdTime");
            field.setAccessible(true);
            field.setLong(territory, createdTime);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            // 字段不存在或无法访问，忽略
        }

        return territory;
    }

    private SubRegion extractSubRegion(ResultSet rs) throws SQLException {
        UUID id = UUID.fromString(rs.getString("subregion_id"));
        UUID parentId = UUID.fromString(rs.getString("parent_territory_id"));
        String name = rs.getString("name");
        String worldName = rs.getString("world_name");
        int minX = rs.getInt("min_x"), minY = rs.getInt("min_y"), minZ = rs.getInt("min_z");
        int maxX = rs.getInt("max_x"), maxY = rs.getInt("max_y"), maxZ = rs.getInt("max_z");
        int priority = rs.getInt("priority");
        // SQLite 使用 INTEGER (0/1)，MySQL 使用 BOOLEAN
        boolean inheritPerms;
        try {
            inheritPerms = rs.getBoolean("inherit_permissions");
        } catch (SQLException e) {
            inheritPerms = rs.getInt("inherit_permissions") == 1;
        }
        String description = rs.getString("description");
        boolean enabled;
        try {
            enabled = rs.getBoolean("enabled");
        } catch (SQLException e) {
            enabled = rs.getInt("enabled") == 1;
        }

        SubRegion subRegion = new SubRegion(id, name, parentId, worldName, minX, minY, minZ, maxX, maxY, maxZ);
        subRegion.setPriority(priority);
        subRegion.setInheritPermissions(inheritPerms);
        subRegion.setDescription(description);
        subRegion.setEnabled(enabled);

        return subRegion;
    }

    // ==================== 保存到 SQL ====================

    private void saveTerritoriesToSql(Connection conn, Collection<Territory> territories) throws SQLException {
        boolean autoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);

        try {
            // 清空旧数据
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("DELETE FROM " + TERRITORY_PERM_TABLE);
                stmt.execute("DELETE FROM " + MEMBER_TABLE + " WHERE entity_type = 'TERRITORY'");
                stmt.execute("DELETE FROM " + TERRITORY_TABLE);
            }

            // 插入领土
            try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO " + TERRITORY_TABLE + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
                for (Territory territory : territories) {
                    stmt.setString(1, territory.getId().toString());
                    stmt.setString(2, territory.getName());
                    stmt.setString(3, territory.getOwnerId().toString());
                    stmt.setString(4, territory.getNationId() != null ? territory.getNationId().toString() : null);
                    stmt.setString(5, territory.getWorldName());
                    stmt.setInt(6, territory.getMinX());
                    stmt.setInt(7, territory.getMinY());
                    stmt.setInt(8, territory.getMinZ());
                    stmt.setInt(9, territory.getMaxX());
                    stmt.setInt(10, territory.getMaxY());
                    stmt.setInt(11, territory.getMaxZ());
                    stmt.setString(12, territory.getType().name());
                    stmt.setObject(13, territory.getSpawnPoint() != null ? territory.getSpawnPoint().getX() : null);
                    stmt.setObject(14, territory.getSpawnPoint() != null ? territory.getSpawnPoint().getY() : null);
                    stmt.setObject(15, territory.getSpawnPoint() != null ? territory.getSpawnPoint().getZ() : null);
                    // SQLite 使用 INTEGER，MySQL 使用 BOOLEAN
                    DatabaseUtils.DatabaseType dbType = DatabaseUtils.detectDatabaseType(conn);
                    if (dbType == DatabaseUtils.DatabaseType.SQLITE) {
                        stmt.setInt(16, territory.isEnabled() ? 1 : 0);
                    } else {
                        stmt.setBoolean(16, territory.isEnabled());
                    }
                    stmt.setLong(17, territory.getCreatedTime());
                    stmt.addBatch();
                }
                stmt.executeBatch();
            }

            // 插入权限
            try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO " + TERRITORY_PERM_TABLE + " VALUES (?,?,?)")) {
                for (Territory territory : territories) {
                    for (Map.Entry<TerritoryPermission, PermissionLevel> entry : territory.getAllPermissions().entrySet()) {
                        stmt.setString(1, territory.getId().toString());
                        stmt.setString(2, entry.getKey().name());
                        stmt.setString(3, entry.getValue().name());
                        stmt.addBatch();
                    }
                }
                stmt.executeBatch();
            }

            // 插入成员
            try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO " + MEMBER_TABLE + " VALUES (?,?,?,?)")) {
                for (Territory territory : territories) {
                    for (Map.Entry<UUID, PermissionLevel> entry : territory.getAllMembers().entrySet()) {
                        stmt.setString(1, territory.getId().toString());
                        stmt.setString(2, "TERRITORY");
                        stmt.setString(3, entry.getKey().toString());
                        stmt.setString(4, entry.getValue().name());
                        stmt.addBatch();
                    }
                }
                stmt.executeBatch();
            }

            conn.commit();
            logger.info("[TerritoryStorage] 已保存 " + territories.size() + " 个领土到数据库");

        } catch (Exception e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(autoCommit);
        }
    }

    private void saveSubRegionsToSql(Connection conn, Collection<SubRegion> subRegions) throws SQLException {
        boolean autoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);

        try {
            // 清空旧数据
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("DELETE FROM " + SUBREGION_PERM_TABLE);
                stmt.execute("DELETE FROM " + MEMBER_TABLE + " WHERE entity_type = 'SUBREGION'");
                stmt.execute("DELETE FROM " + SUBREGION_TABLE);
            }

            // 插入子区域
            try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO " + SUBREGION_TABLE + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
                for (SubRegion subRegion : subRegions) {
                    stmt.setString(1, subRegion.getId().toString());
                    stmt.setString(2, subRegion.getParentTerritoryId().toString());
                    stmt.setString(3, subRegion.getName());
                    stmt.setString(4, subRegion.getWorldName());
                    stmt.setInt(5, subRegion.getMinX());
                    stmt.setInt(6, subRegion.getMinY());
                    stmt.setInt(7, subRegion.getMinZ());
                    stmt.setInt(8, subRegion.getMaxX());
                    stmt.setInt(9, subRegion.getMaxY());
                    stmt.setInt(10, subRegion.getMaxZ());
                    stmt.setInt(11, subRegion.getPriority());
                    // SQLite 使用 INTEGER，MySQL 使用 BOOLEAN
                    DatabaseUtils.DatabaseType dbType = DatabaseUtils.detectDatabaseType(conn);
                    if (dbType == DatabaseUtils.DatabaseType.SQLITE) {
                        stmt.setInt(12, subRegion.isInheritPermissions() ? 1 : 0);
                    } else {
                        stmt.setBoolean(12, subRegion.isInheritPermissions());
                    }
                    stmt.setString(13, subRegion.getDescription());
                    if (dbType == DatabaseUtils.DatabaseType.SQLITE) {
                        stmt.setInt(14, subRegion.isEnabled() ? 1 : 0);
                    } else {
                        stmt.setBoolean(14, subRegion.isEnabled());
                    }
                    stmt.setLong(15, subRegion.getCreatedTime());
                    stmt.addBatch();
                }
                stmt.executeBatch();
            }

            // 插入权限覆盖
            try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO " + SUBREGION_PERM_TABLE + " VALUES (?,?,?)")) {
                for (SubRegion subRegion : subRegions) {
                    for (Map.Entry<TerritoryPermission, PermissionLevel> entry : subRegion.getOverridePermissions().entrySet()) {
                        stmt.setString(1, subRegion.getId().toString());
                        stmt.setString(2, entry.getKey().name());
                        stmt.setString(3, entry.getValue().name());
                        stmt.addBatch();
                    }
                }
                stmt.executeBatch();
            }

            // 插入成员
            try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO " + MEMBER_TABLE + " VALUES (?,?,?,?)")) {
                for (SubRegion subRegion : subRegions) {
                    for (Map.Entry<UUID, PermissionLevel> entry : subRegion.getAllMembers().entrySet()) {
                        stmt.setString(1, subRegion.getId().toString());
                        stmt.setString(2, "SUBREGION");
                        stmt.setString(3, entry.getKey().toString());
                        stmt.setString(4, entry.getValue().name());
                        stmt.addBatch();
                    }
                }
                stmt.executeBatch();
            }

            conn.commit();
            logger.info("[TerritoryStorage] 已保存 " + subRegions.size() + " 个子区域到数据库");

        } catch (Exception e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(autoCommit);
        }
    }

    // ==================== Properties 降级存储 ====================

    private static final String NAMESPACE = "territory";
    private static final String TERRITORY_FILE = "territories.properties";
    private static final String SUBREGION_FILE = "subregions.properties";

    private List<Territory> loadTerritoriesFromProperties() {
        Properties props = persistenceService.loadProperties(NAMESPACE, TERRITORY_FILE);
        List<Territory> territories = TerritoryStateCodec.fromPropertiesTerritories(props);
        if (!territories.isEmpty()) {
            logger.info("[TerritoryStorage] 从 Properties 加载了 " + territories.size() + " 个领土");
        }
        return territories;
    }

    private void saveTerritoriesToProperties(Collection<Territory> territories) {
        Properties props = TerritoryStateCodec.toPropertiesTerritories(territories);
        persistenceService.saveProperties(NAMESPACE, TERRITORY_FILE, props);
        logger.info("[TerritoryStorage] 已保存 " + territories.size() + " 个领土到 Properties");
    }

    private List<SubRegion> loadSubRegionsFromProperties() {
        Properties props = persistenceService.loadProperties(NAMESPACE, SUBREGION_FILE);
        List<SubRegion> subRegions = TerritoryStateCodec.fromPropertiesSubRegions(props);
        if (!subRegions.isEmpty()) {
            logger.info("[TerritoryStorage] 从 Properties 加载了 " + subRegions.size() + " 个子区域");
        }
        return subRegions;
    }

    private void saveSubRegionsToProperties(Collection<SubRegion> subRegions) {
        Properties props = TerritoryStateCodec.toPropertiesSubRegions(subRegions);
        persistenceService.saveProperties(NAMESPACE, SUBREGION_FILE, props);
        logger.info("[TerritoryStorage] 已保存 " + subRegions.size() + " 个子区域到 Properties");
    }
}
