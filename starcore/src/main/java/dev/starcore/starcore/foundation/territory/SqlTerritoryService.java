package dev.starcore.starcore.foundation.territory;

import dev.starcore.starcore.core.database.DatabaseService;
import dev.starcore.starcore.core.persistence.PersistenceService;
import dev.starcore.starcore.foundation.territory.model.ChunkCoordinate;
import dev.starcore.starcore.foundation.territory.model.TerritoryClaim;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public final class SqlTerritoryService implements TerritoryService {
    private static final String NAMESPACE = "territory";
    private static final String FILE_NAME = "claims.properties";
    private static final String TABLE_NAME = "starcore_territory_claims";

    private final Supplier<Optional<DataSource>> dataSourceSupplier;
    private final Supplier<Collection<TerritoryClaim>> legacyClaimSupplier;
    private final Logger logger;
    private final ConcurrentMap<ChunkCoordinate, TerritoryClaim> claims = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Set<ChunkCoordinate>> ownerIndex = new ConcurrentHashMap<>();

    // 异步操作支持
    private final ConcurrentMap<ChunkCoordinate, PendingOperation> pendingOperations = new ConcurrentHashMap<>();

    // 统计信息
    private volatile int cacheHits = 0;
    private volatile int cacheMisses = 0;

    public SqlTerritoryService(DatabaseService databaseService, PersistenceService persistenceService, Logger logger) {
        this(
            databaseService::dataSource,
            () -> TerritoryStateCodec.fromProperties(persistenceService.loadProperties(NAMESPACE, FILE_NAME)).values(),
            logger
        );
    }

    SqlTerritoryService(
        Supplier<Optional<DataSource>> dataSourceSupplier,
        Supplier<Collection<TerritoryClaim>> legacyClaimSupplier,
        Logger logger
    ) {
        this.dataSourceSupplier = Objects.requireNonNull(dataSourceSupplier, "dataSourceSupplier");
        this.legacyClaimSupplier = Objects.requireNonNull(legacyClaimSupplier, "legacyClaimSupplier");
        this.logger = Objects.requireNonNull(logger, "logger");
        initialize();
    }

    @Override
    public Optional<TerritoryClaim> claimAt(ChunkCoordinate coordinate) {
        Objects.requireNonNull(coordinate, "coordinate");

        // 首先检查缓存
        TerritoryClaim cached = claims.get(coordinate);
        if (cached != null) {
            cacheHits++;
            return Optional.of(cached);
        }

        cacheMisses++;
        return Optional.empty();
    }

    @Override
    public boolean isClaimed(ChunkCoordinate coordinate) {
        Objects.requireNonNull(coordinate, "coordinate");
        return claims.containsKey(coordinate);
    }

    @Override
    public synchronized boolean claim(String ownerId, ChunkCoordinate coordinate) {
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(coordinate, "coordinate");

        // 检查是否已有声明
        if (claims.containsKey(coordinate)) {
            return false;
        }

        // 检查是否有待处理的冲突操作
        PendingOperation pending = pendingOperations.get(coordinate);
        if (pending != null && pending.type() == OperationType.CLAIM) {
            return false; // 正在claim中
        }

        TerritoryClaim claim = new TerritoryClaim(ownerId, coordinate);

        // 先更新缓存
        cache(claim);

        // 异步写入数据库
        persistClaimAsync(ownerId, coordinate);

        return true;
    }

    @Override
    public synchronized boolean unclaim(String ownerId, ChunkCoordinate coordinate) {
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(coordinate, "coordinate");

        TerritoryClaim existing = claims.get(coordinate);
        if (existing == null || !existing.ownerId().equals(ownerId)) {
            return false;
        }

        // 检查是否有待处理的冲突操作
        PendingOperation pending = pendingOperations.get(coordinate);
        if (pending != null && pending.type() == OperationType.UNCLAIM) {
            return false; // 正在unclaim中
        }

        // 从缓存移除
        uncache(existing);

        // 异步删除数据库记录
        persistUnclaimAsync(ownerId, coordinate);

        return true;
    }

    @Override
    public int claimedChunkCount() {
        return claims.size();
    }

    @Override
    public Collection<TerritoryClaim> claimsByOwner(String ownerId) {
        Set<ChunkCoordinate> coordinates = ownerIndex.get(ownerId);
        if (coordinates == null || coordinates.isEmpty()) {
            return List.of();
        }
        return coordinates.stream()
            .map(claims::get)
            .filter(Objects::nonNull)
            .sorted(Comparator.comparing(claim -> claim.coordinate().toString()))
            .collect(Collectors.toList());
    }

    /**
     * 获取缓存命中率
     */
    public double getCacheHitRate() {
        int total = cacheHits + cacheMisses;
        return total > 0 ? (double) cacheHits / total : 1.0;
    }

    /**
     * 获取缓存统计信息
     */
    public String getCacheStats() {
        return String.format("TerritoryCache[hits=%d, misses=%d, hitRate=%.1f%%, size=%d]",
            cacheHits, cacheMisses, getCacheHitRate() * 100, claims.size());
    }

    /**
     * 异步持久化声明操作
     */
    private void persistClaimAsync(String ownerId, ChunkCoordinate coordinate) {
        PendingOperation existing = pendingOperations.put(coordinate, new PendingOperation(OperationType.CLAIM, ownerId));
        if (existing != null) {
            // 已有待处理操作，忽略
            return;
        }

        new Thread(() -> {
            try {
                insertClaim(ownerId, coordinate);
            } catch (Exception e) {
                logger.warning("Failed to persist territory claim: " + coordinate + " - " + e.getMessage());
            } finally {
                pendingOperations.remove(coordinate);
            }
        }).start();
    }

    /**
     * 异步持久化取消声明操作
     */
    private void persistUnclaimAsync(String ownerId, ChunkCoordinate coordinate) {
        PendingOperation existing = pendingOperations.put(coordinate, new PendingOperation(OperationType.UNCLAIM, ownerId));
        if (existing != null) {
            // 已有待处理操作，忽略
            return;
        }

        new Thread(() -> {
            try {
                deleteClaim(ownerId, coordinate);
            } catch (Exception e) {
                logger.warning("Failed to delete territory claim: " + coordinate + " - " + e.getMessage());
            } finally {
                pendingOperations.remove(coordinate);
            }
        }).start();
    }

    private void initialize() {
        try (Connection connection = openConnection()) {
            ensureTable(connection);
            loadFromDatabase(connection);
            if (claims.isEmpty()) {
                importLegacyClaims(connection);
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to initialize SQL territory repository", exception);
        }
    }

    private void ensureTable(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE IF NOT EXISTS starcore_territory_claims (
                    world_name VARCHAR(191) NOT NULL,
                    chunk_x INTEGER NOT NULL,
                    chunk_z INTEGER NOT NULL,
                    owner_id VARCHAR(64) NOT NULL,
                    PRIMARY KEY (world_name, chunk_x, chunk_z)
                )
                """);
        }
        ensureOwnerIndex(connection);
    }

    private void loadFromDatabase(Connection connection) throws SQLException {
        clearCache();
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT world_name, chunk_x, chunk_z, owner_id
            FROM starcore_territory_claims
            ORDER BY world_name, chunk_x, chunk_z
            """);
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                TerritoryClaim claim = new TerritoryClaim(
                    rows.getString("owner_id"),
                    new ChunkCoordinate(
                        rows.getString("world_name"),
                        rows.getInt("chunk_x"),
                        rows.getInt("chunk_z")
                    )
                );
                cache(claim);
            }
        }
    }

    private void importLegacyClaims(Connection connection) throws SQLException {
        Collection<TerritoryClaim> legacyClaims = legacyClaimSupplier.get();
        if (legacyClaims.isEmpty()) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement("""
            INSERT INTO starcore_territory_claims (world_name, chunk_x, chunk_z, owner_id)
            VALUES (?, ?, ?, ?)
            """)) {
            for (TerritoryClaim claim : legacyClaims) {
                statement.setString(1, claim.coordinate().world());
                statement.setInt(2, claim.coordinate().x());
                statement.setInt(3, claim.coordinate().z());
                statement.setString(4, claim.ownerId());
                statement.addBatch();
            }
            statement.executeBatch();
        }
        clearCache();
        for (TerritoryClaim claim : legacyClaims) {
            cache(claim);
        }
        logger.info("Imported " + legacyClaims.size() + " legacy STARCORE territory claims into SQL storage.");
    }

    private void insertClaim(String ownerId, ChunkCoordinate coordinate) {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                 INSERT INTO starcore_territory_claims (world_name, chunk_x, chunk_z, owner_id)
                 VALUES (?, ?, ?, ?)
                 """)) {
            statement.setString(1, coordinate.world());
            statement.setInt(2, coordinate.x());
            statement.setInt(3, coordinate.z());
            statement.setString(4, ownerId);
            statement.executeUpdate();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to persist territory claim " + coordinate, exception);
        }
    }

    private void deleteClaim(String ownerId, ChunkCoordinate coordinate) {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                 DELETE FROM starcore_territory_claims
                 WHERE world_name = ? AND chunk_x = ? AND chunk_z = ? AND owner_id = ?
                 """)) {
            statement.setString(1, coordinate.world());
            statement.setInt(2, coordinate.x());
            statement.setInt(3, coordinate.z());
            statement.setString(4, ownerId);
            int affected = statement.executeUpdate();
            if (affected == 0) {
                throw new IllegalStateException("No SQL territory claim row was deleted for " + coordinate);
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to delete territory claim " + coordinate, exception);
        }
    }

    private void ensureOwnerIndex(Connection connection) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet indexes = metadata.getIndexInfo(null, null, TABLE_NAME, false, false)) {
            while (indexes.next()) {
                String indexName = indexes.getString("INDEX_NAME");
                if ("idx_starcore_territory_claims_owner".equalsIgnoreCase(indexName)) {
                    return;
                }
            }
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE INDEX idx_starcore_territory_claims_owner ON " + TABLE_NAME + " (owner_id)");
        }
    }

    private Connection openConnection() throws SQLException {
        DataSource dataSource = dataSourceSupplier.get()
            .orElseThrow(() -> new IllegalStateException("STARCORE database is not running"));
        return dataSource.getConnection();
    }

    private void cache(TerritoryClaim claim) {
        claims.put(claim.coordinate(), claim);
        ownerIndex.computeIfAbsent(claim.ownerId(), ignored -> ConcurrentHashMap.newKeySet()).add(claim.coordinate());
    }

    private void uncache(TerritoryClaim claim) {
        claims.remove(claim.coordinate(), claim);
        ownerIndex.computeIfPresent(claim.ownerId(), (ignored, coordinates) -> {
            coordinates.remove(claim.coordinate());
            return coordinates.isEmpty() ? null : coordinates;
        });
    }

    private void clearCache() {
        claims.clear();
        ownerIndex.clear();
    }

    // 内部类：待处理操作
    private enum OperationType {
        CLAIM, UNCLAIM
    }

    private record PendingOperation(OperationType type, String ownerId) {}
}
