package dev.starcore.starcore.module.sovereignty;
import java.util.Optional;

import dev.starcore.starcore.core.database.DatabaseService;
import dev.starcore.starcore.core.persistence.PersistenceService;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.sovereignty.event.SovereigntyDeclaredEvent;
import dev.starcore.starcore.module.sovereignty.event.SovereigntyRevokedEvent;
import dev.starcore.starcore.module.sovereignty.model.SovereigntyClaim;
import dev.starcore.starcore.module.sovereignty.model.SovereigntyRecord;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 主权声明服务实现
 * 管理国家主权声明的核心逻辑和数据持久化
 */
public final class SovereigntyServiceImpl implements SovereigntyService {

    private static final String TABLE_NAME = "sovereignty_declarations";
    private static final String CLAIMS_TABLE_NAME = "sovereignty_claims";

    private final Plugin plugin;
    private final NationService nationService;
    private final DatabaseService databaseService;
    private final PersistenceService persistenceService;
    private final MessageService messages;

    // 内存中的主权声明缓存
    private final Map<UUID, SovereigntyRecord> sovereignties = new ConcurrentHashMap<>();
    private final Map<NationId, Set<UUID>> nationSovereignties = new ConcurrentHashMap<>();
    private final Map<String, UUID> regionSovereignty = new ConcurrentHashMap<>(); // regionName -> sovereigntyId

    // 领土索引: sovereigntyId -> Set<SovereigntyClaim>
    private final Map<UUID, Set<SovereigntyClaim>> sovereigntyClaims = new ConcurrentHashMap<>();

    public SovereigntyServiceImpl(
            Plugin plugin,
            NationService nationService,
            DatabaseService databaseService,
            PersistenceService persistenceService,
            MessageService messages
    ) {
        this.plugin = plugin;
        this.nationService = nationService;
        this.databaseService = databaseService;
        this.persistenceService = persistenceService;
        this.messages = messages;
    }

    /**
     * 初始化数据库表
     */
    public void initializeTables() {
        databaseService.dataSource().ifPresent(ds -> {
            try (Connection conn = ds.getConnection()) {
                // 创建主权声明表
                String createTableSql = """
                    CREATE TABLE IF NOT EXISTS %s (
                        id VARCHAR(36) PRIMARY KEY,
                        nation_id VARCHAR(36) NOT NULL,
                        region_name VARCHAR(255) NOT NULL,
                        description TEXT,
                        significance VARCHAR(20) NOT NULL,
                        status VARCHAR(20) NOT NULL,
                        strength INT DEFAULT 0,
                        declared_at TIMESTAMP NOT NULL,
                        updated_at TIMESTAMP NOT NULL,
                        UNIQUE(region_name)
                    )
                """.formatted(TABLE_NAME);

                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(createTableSql);
                }

                // 创建主权领土表
                String createClaimsTableSql = """
                    CREATE TABLE IF NOT EXISTS %s (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        sovereignty_id VARCHAR(36) NOT NULL,
                        world VARCHAR(255) NOT NULL,
                        chunk_x INT NOT NULL,
                        chunk_z INT NOT NULL,
                        FOREIGN KEY (sovereignty_id) REFERENCES %s(id) ON DELETE CASCADE
                    )
                """.formatted(CLAIMS_TABLE_NAME, TABLE_NAME);

                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(createClaimsTableSql);
                }

                plugin.getLogger().info("Sovereignty tables initialized successfully");
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to initialize sovereignty tables: " + e.getMessage());
            }
        });
    }

    @Override
    public SovereigntyRecord declareSovereignty(NationId nationId, String regionName, String description, SovereigntySignificance significance) {
        // 检查国家是否存在
        if (nationService.nationById(nationId).isEmpty()) {
            throw new IllegalArgumentException("Nation not found: " + nationId);
        }

        // 检查是否已存在该区域的主权声明
        if (regionSovereignty.containsKey(regionName)) {
            throw new IllegalStateException("Region already has sovereignty claim: " + regionName);
        }

        Instant now = Instant.now();
        SovereigntyRecord sovereignty = new SovereigntyRecord(
                UUID.randomUUID(),
                nationId,
                regionName,
                description,
                significance,
                SovereigntyStatus.CLAIMED,
                0,
                now,
                now
        );

        // 保存到内存
        sovereignties.put(sovereignty.id(), sovereignty);
        nationSovereignties.computeIfAbsent(nationId, k -> ConcurrentHashMap.newKeySet()).add(sovereignty.id());
        regionSovereignty.put(regionName, sovereignty.id());
        sovereigntyClaims.put(sovereignty.id(), ConcurrentHashMap.newKeySet());

        // 保存到数据库
        saveSovereignty(sovereignty);

        // 触发事件
        SovereigntyDeclaredEvent event = new SovereigntyDeclaredEvent(sovereignty);
        Bukkit.getServer().getPluginManager().callEvent(event);

        plugin.getLogger().info("Sovereignty declared: " + regionName + " by nation " + nationId);
        return sovereignty;
    }

    @Override
    public boolean revokeSovereignty(UUID sovereigntyId) {
        SovereigntyRecord sovereignty = sovereignties.get(sovereigntyId);
        if (sovereignty == null) {
            return false;
        }

        // 从内存移除
        sovereignties.remove(sovereigntyId);
        regionSovereignty.remove(sovereignty.regionName());

        Set<UUID> nationSet = nationSovereignties.get(sovereignty.nationId());
        if (nationSet != null) {
            nationSet.remove(sovereigntyId);
        }

        sovereigntyClaims.remove(sovereigntyId);

        // 更新数据库状态
        databaseService.dataSource().ifPresent(ds -> {
            try (Connection conn = ds.getConnection()) {
                String sql = "UPDATE " + TABLE_NAME + " SET status = ?, updated_at = ? WHERE id = ?";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, SovereigntyStatus.REVOKED.name());
                    stmt.setTimestamp(2, Timestamp.from(Instant.now()));
                    stmt.setString(3, sovereigntyId.toString());
                    stmt.executeUpdate();
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to revoke sovereignty in database: " + e.getMessage());
            }
        });

        // 触发事件
        SovereigntyRevokedEvent event = new SovereigntyRevokedEvent(sovereignty);
        Bukkit.getServer().getPluginManager().callEvent(event);

        plugin.getLogger().info("Sovereignty revoked: " + sovereignty.regionName());
        return true;
    }

    @Override
    public Optional<SovereigntyRecord> getSovereignty(UUID sovereigntyId) {
        return Optional.ofNullable(sovereignties.get(sovereigntyId));
    }

    @Override
    public Collection<SovereigntyRecord> getNationSovereignties(NationId nationId) {
        Set<UUID> ids = nationSovereignties.get(nationId);
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return ids.stream()
                .map(sovereignties::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<SovereigntyRecord> getSovereigntyByRegion(String regionName) {
        UUID id = regionSovereignty.get(regionName);
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(sovereignties.get(id));
    }

    @Override
    public boolean hasSovereigntyClaim(NationId nationId, String regionName) {
        return getSovereigntyByRegion(regionName)
                .map(s -> s.nationId().equals(nationId))
                .orElse(false);
    }

    @Override
    public SovereigntyRecord updateStrength(UUID sovereigntyId, int strengthDelta) {
        SovereigntyRecord sovereignty = sovereignties.get(sovereigntyId);
        if (sovereignty == null) {
            throw new IllegalArgumentException("Sovereignty not found: " + sovereigntyId);
        }

        int newStrength = Math.max(0, sovereignty.strength() + strengthDelta);
        SovereigntyRecord updated = new SovereigntyRecord(
                sovereignty.id(),
                sovereignty.nationId(),
                sovereignty.regionName(),
                sovereignty.description(),
                sovereignty.significance(),
                sovereignty.status(),
                newStrength,
                sovereignty.declaredAt(),
                Instant.now()
        );

        sovereignties.put(sovereigntyId, updated);
        saveSovereignty(updated);

        return updated;
    }

    @Override
    public boolean updateStatus(UUID sovereigntyId, SovereigntyStatus status) {
        SovereigntyRecord sovereignty = sovereignties.get(sovereigntyId);
        if (sovereignty == null) {
            return false;
        }

        SovereigntyRecord updated = new SovereigntyRecord(
                sovereignty.id(),
                sovereignty.nationId(),
                sovereignty.regionName(),
                sovereignty.description(),
                sovereignty.significance(),
                status,
                sovereignty.strength(),
                sovereignty.declaredAt(),
                Instant.now()
        );

        sovereignties.put(sovereigntyId, updated);
        saveSovereignty(updated);

        return true;
    }

    @Override
    public Collection<SovereigntyRecord> getAllActiveSovereignties() {
        return sovereignties.values().stream()
                .filter(s -> s.status() != SovereigntyStatus.REVOKED)
                .collect(Collectors.toList());
    }

    @Override
    public Collection<SovereigntyRecord> getCompetingClaims(String regionName) {
        // 目前每个区域只能有一个主权声明
        return getSovereigntyByRegion(regionName)
                .map(List::of)
                .orElse(Collections.emptyList());
    }

    @Override
    public boolean addClaimedTerritory(UUID sovereigntyId, String world, int chunkX, int chunkZ) {
        SovereigntyRecord sovereignty = sovereignties.get(sovereigntyId);
        if (sovereignty == null) {
            return false;
        }

        SovereigntyClaim claim = new SovereigntyClaim(world, chunkX, chunkZ);
        Set<SovereigntyClaim> claims = sovereigntyClaims.computeIfAbsent(sovereigntyId, k -> ConcurrentHashMap.newKeySet());
        claims.add(claim);

        // 保存到数据库
        saveClaimedTerritory(sovereigntyId, claim);

        return true;
    }

    @Override
    public boolean removeClaimedTerritory(UUID sovereigntyId, String world, int chunkX, int chunkZ) {
        Set<SovereigntyClaim> claims = sovereigntyClaims.get(sovereigntyId);
        if (claims == null) {
            return false;
        }

        SovereigntyClaim claim = new SovereigntyClaim(world, chunkX, chunkZ);
        boolean removed = claims.remove(claim);

        if (removed) {
            removeClaimedTerritoryFromDb(sovereigntyId, world, chunkX, chunkZ);
        }

        return removed;
    }

    @Override
    public Collection<SovereigntyClaim> getClaimedTerritories(UUID sovereigntyId) {
        Set<SovereigntyClaim> claims = sovereigntyClaims.get(sovereigntyId);
        if (claims == null) {
            return Collections.emptyList();
        }
        return Set.copyOf(claims);
    }

    @Override
    public boolean confirmSovereignty(UUID sovereigntyId, Collection<UUID> disputedIds) {
        SovereigntyRecord sovereignty = getSovereignty(sovereigntyId).orElse(null);
        if (sovereignty == null) {
            return false;
        }

        // 更新确认的主权声明状态
        updateStatus(sovereigntyId, SovereigntyStatus.RECOGNIZED);

        // 将争议的主权声明标记为争议中
        for (UUID disputedId : disputedIds) {
            SovereigntyRecord disputed = sovereignties.get(disputedId);
            if (disputed != null) {
                updateStatus(disputedId, SovereigntyStatus.CONTESTED);
            }
        }

        // 增加确认主权声明的强度
        updateStrength(sovereigntyId, disputedIds.size() * 10);

        return true;
    }

    @Override
    public void saveState() {
        databaseService.dataSource().ifPresent(ds -> {
            try (Connection conn = ds.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    for (SovereigntyRecord sovereignty : sovereignties.values()) {
                        saveSovereignty(conn, sovereignty);
                    }
                    conn.commit();
                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(true);
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to save sovereignty state: " + e.getMessage());
            }
        });
    }

    @Override
    public String summary() {
        long active = sovereignties.values().stream()
                .filter(s -> s.status() != SovereigntyStatus.REVOKED)
                .count();
        return active + " active sovereignty declaration(s), " + sovereignties.size() + " total";
    }

    /**
     * 加载所有主权声明
     */
    public void loadSovereignties() {
        databaseService.dataSource().ifPresent(ds -> {
            try (Connection conn = ds.getConnection()) {
                String sql = "SELECT * FROM " + TABLE_NAME;
                try (PreparedStatement stmt = conn.prepareStatement(sql);
                     ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        SovereigntyRecord sovereignty = parseSovereignty(rs);
                        if (sovereignty.status() != SovereigntyStatus.REVOKED) {
                            sovereignties.put(sovereignty.id(), sovereignty);
                            nationSovereignties
                                    .computeIfAbsent(sovereignty.nationId(), k -> ConcurrentHashMap.newKeySet())
                                    .add(sovereignty.id());
                            regionSovereignty.put(sovereignty.regionName(), sovereignty.id());
                        }
                    }
                }

                // 加载领土声明
                String claimsSql = "SELECT * FROM " + CLAIMS_TABLE_NAME;
                try (PreparedStatement stmt = conn.prepareStatement(claimsSql);
                     ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        UUID sovereigntyId = UUID.fromString(rs.getString("sovereignty_id"));
                        SovereigntyClaim claim = new SovereigntyClaim(
                                rs.getString("world"),
                                rs.getInt("chunk_x"),
                                rs.getInt("chunk_z")
                        );
                        sovereigntyClaims
                                .computeIfAbsent(sovereigntyId, k -> ConcurrentHashMap.newKeySet())
                                .add(claim);
                    }
                }

                plugin.getLogger().info("Loaded " + sovereignties.size() + " sovereignties from database");
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to load sovereignties: " + e.getMessage());
            }
        });
    }

    // ==================== 私有方法 ====================

    private void saveSovereignty(SovereigntyRecord sovereignty) {
        databaseService.dataSource().ifPresent(ds -> {
            try (Connection conn = ds.getConnection()) {
                saveSovereignty(conn, sovereignty);
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to save sovereignty: " + e.getMessage());
            }
        });
    }

    private void saveSovereignty(Connection conn, SovereigntyRecord sovereignty) throws SQLException {
        String productName = conn.getMetaData().getDatabaseProductName();
        boolean isSQLite = "SQLite".equalsIgnoreCase(productName);

        String sql;
        if (isSQLite) {
            sql = """
                INSERT INTO %s (id, nation_id, region_name, description, significance, status, strength, declared_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    description = excluded.description,
                    significance = excluded.significance,
                    status = excluded.status,
                    strength = excluded.strength,
                    updated_at = excluded.updated_at
            """.formatted(TABLE_NAME);
        } else {
            sql = """
                INSERT INTO %s (id, nation_id, region_name, description, significance, status, strength, declared_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    description = VALUES(description),
                    significance = VALUES(significance),
                    status = VALUES(status),
                    strength = VALUES(strength),
                    updated_at = VALUES(updated_at)
            """.formatted(TABLE_NAME);
        }

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, sovereignty.id().toString());
            stmt.setString(2, sovereignty.nationId().toString());
            stmt.setString(3, sovereignty.regionName());
            stmt.setString(4, sovereignty.description());
            stmt.setString(5, sovereignty.significance().name());
            stmt.setString(6, sovereignty.status().name());
            stmt.setInt(7, sovereignty.strength());
            stmt.setTimestamp(8, Timestamp.from(sovereignty.declaredAt()));
            stmt.setTimestamp(9, Timestamp.from(sovereignty.updatedAt()));
            stmt.executeUpdate();
        }
    }

    private void saveClaimedTerritory(UUID sovereigntyId, SovereigntyClaim claim) {
        databaseService.dataSource().ifPresent(ds -> {
            try (Connection conn = ds.getConnection()) {
                String sql = "INSERT INTO " + CLAIMS_TABLE_NAME + " (sovereignty_id, world, chunk_x, chunk_z) VALUES (?, ?, ?, ?)";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, sovereigntyId.toString());
                    stmt.setString(2, claim.world());
                    stmt.setInt(3, claim.chunkX());
                    stmt.setInt(4, claim.chunkZ());
                    stmt.executeUpdate();
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to save claimed territory: " + e.getMessage());
            }
        });
    }

    private void removeClaimedTerritoryFromDb(UUID sovereigntyId, String world, int chunkX, int chunkZ) {
        databaseService.dataSource().ifPresent(ds -> {
            try (Connection conn = ds.getConnection()) {
                String sql = "DELETE FROM " + CLAIMS_TABLE_NAME + " WHERE sovereignty_id = ? AND world = ? AND chunk_x = ? AND chunk_z = ?";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, sovereigntyId.toString());
                    stmt.setString(2, world);
                    stmt.setInt(3, chunkX);
                    stmt.setInt(4, chunkZ);
                    stmt.executeUpdate();
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to remove claimed territory: " + e.getMessage());
            }
        });
    }

    private SovereigntyRecord parseSovereignty(ResultSet rs) throws SQLException {
        return new SovereigntyRecord(
                UUID.fromString(rs.getString("id")),
                new NationId(UUID.fromString(rs.getString("nation_id"))),
                rs.getString("region_name"),
                rs.getString("description"),
                SovereigntySignificance.valueOf(rs.getString("significance")),
                SovereigntyStatus.valueOf(rs.getString("status")),
                rs.getInt("strength"),
                rs.getTimestamp("declared_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()
        );
    }
}