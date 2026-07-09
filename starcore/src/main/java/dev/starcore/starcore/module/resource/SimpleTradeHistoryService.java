package dev.starcore.starcore.module.resource;
import java.util.Optional;

import dev.starcore.starcore.core.database.DatabaseService;
import dev.starcore.starcore.core.scheduler.StarCoreScheduler;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.resource.model.TradeOrder;
import dev.starcore.starcore.module.resource.model.TradeRecord;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 交易历史记录服务实现
 */
public class SimpleTradeHistoryService implements TradeHistoryService {
    private final DatabaseService databaseService;
    private final StarCoreScheduler scheduler;

    // 内存缓存（用于快速查询）
    private final Map<UUID, TradeRecord> recordsById;
    private final Map<UUID, List<UUID>> playerRecords;
    private final Map<String, List<UUID>> nationRecords;
    private final Map<String, List<UUID>> resourceRecords;

    private final String tableName = "trade_history";

    public SimpleTradeHistoryService(DatabaseService databaseService, StarCoreScheduler scheduler) {
        this.databaseService = Objects.requireNonNull(databaseService, "databaseService");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.recordsById = new ConcurrentHashMap<>();
        this.playerRecords = new ConcurrentHashMap<>();
        this.nationRecords = new ConcurrentHashMap<>();
        this.resourceRecords = new ConcurrentHashMap<>();

        initializeTable();
        loadRecordsFromDatabase();

        // 每10分钟持久化一次
        if (scheduler != null) {
            scheduler.runSyncTimer(() -> persistAllRecords(), 10 * 60 * 20L, 10 * 60 * 20L);
        }
    }

    /**
     * 初始化数据库表
     */
    private void initializeTable() {
        String sql = """
            CREATE TABLE IF NOT EXISTS trade_history (
                record_id VARCHAR(36) PRIMARY KEY,
                order_type VARCHAR(10) NOT NULL,
                order_source VARCHAR(20) NOT NULL,
                seller_player_id VARCHAR(36),
                buyer_player_id VARCHAR(36),
                seller_nation_id VARCHAR(36),
                buyer_nation_id VARCHAR(36),
                resource_id VARCHAR(50) NOT NULL,
                amount BIGINT NOT NULL,
                price_per_unit DOUBLE NOT NULL,
                total_value DOUBLE NOT NULL,
                tax_amount DOUBLE NOT NULL,
                net_value DOUBLE NOT NULL,
                executed_at TIMESTAMP NOT NULL,
                buy_order_id VARCHAR(36),
                sell_order_id VARCHAR(36),
                status VARCHAR(20) NOT NULL,
                notes TEXT,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """;

        try (Connection conn = databaseService.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);

            // 创建索引
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_trade_player ON trade_history(buyer_player_id, seller_player_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_trade_resource ON trade_history(resource_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_trade_executed ON trade_history(executed_at)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_trade_nation ON trade_history(buyer_nation_id, seller_nation_id)");
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize trade_history table", e);
        }
    }

    /**
     * 从数据库加载记录
     */
    private void loadRecordsFromDatabase() {
        String sql = "SELECT * FROM trade_history ORDER BY executed_at DESC LIMIT 10000";

        try (Connection conn = databaseService.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                TradeRecord record = extractRecordFromResultSet(rs);
                cacheRecord(record);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load trade records from database", e);
        }
    }

    @Override
    public void saveRecord(TradeRecord record) {
        cacheRecord(record);
        persistRecord(record);
    }

    @Override
    public void saveRecords(Collection<TradeRecord> records) {
        for (TradeRecord record : records) {
            cacheRecord(record);
        }
        persistRecords(records);
    }

    private void cacheRecord(TradeRecord record) {
        recordsById.put(record.recordId(), record);

        // 缓存玩家记录
        record.buyerPlayerId().ifPresent(id -> {
            playerRecords.computeIfAbsent(id, k -> new ArrayList<>()).add(0, record.recordId());
        });
        record.sellerPlayerId().ifPresent(id -> {
            playerRecords.computeIfAbsent(id, k -> new ArrayList<>()).add(0, record.recordId());
        });

        // 缓存国家记录
        record.buyerNationId().ifPresent(id -> {
            nationRecords.computeIfAbsent(id.toString(), k -> new ArrayList<>()).add(0, record.recordId());
        });
        record.sellerNationId().ifPresent(id -> {
            nationRecords.computeIfAbsent(id.toString(), k -> new ArrayList<>()).add(0, record.recordId());
        });

        // 缓存资源记录
        resourceRecords.computeIfAbsent(record.resourceId(), k -> new ArrayList<>()).add(0, record.recordId());
    }

    private void persistRecord(TradeRecord record) {
        String sql = """
            INSERT OR REPLACE INTO trade_history
            (record_id, order_type, order_source, seller_player_id, buyer_player_id,
             seller_nation_id, buyer_nation_id, resource_id, amount, price_per_unit,
             total_value, tax_amount, net_value, executed_at, buy_order_id, sell_order_id,
             status, notes)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = databaseService.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            setRecordParameters(pstmt, record);
            pstmt.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("Failed to persist trade record", e);
        }
    }

    private void persistRecords(Collection<TradeRecord> records) {
        String sql = """
            INSERT OR REPLACE INTO trade_history
            (record_id, order_type, order_source, seller_player_id, buyer_player_id,
             seller_nation_id, buyer_nation_id, resource_id, amount, price_per_unit,
             total_value, tax_amount, net_value, executed_at, buy_order_id, sell_order_id,
             status, notes)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = databaseService.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            for (TradeRecord record : records) {
                setRecordParameters(pstmt, record);
                pstmt.addBatch();
            }
            pstmt.executeBatch();
        } catch (Exception e) {
            throw new RuntimeException("Failed to persist trade records", e);
        }
    }

    private void setRecordParameters(PreparedStatement pstmt, TradeRecord record) throws SQLException {
        pstmt.setString(1, record.recordId().toString());
        pstmt.setString(2, record.orderType().name());
        pstmt.setString(3, record.orderSource().name());
        pstmt.setString(4, record.sellerPlayerId().map(UUID::toString).orElse(null));
        pstmt.setString(5, record.buyerPlayerId().map(UUID::toString).orElse(null));
        pstmt.setString(6, record.sellerNationId().map(Object::toString).orElse(null));
        pstmt.setString(7, record.buyerNationId().map(Object::toString).orElse(null));
        pstmt.setString(8, record.resourceId());
        pstmt.setLong(9, record.amount());
        pstmt.setDouble(10, record.pricePerUnit());
        pstmt.setDouble(11, record.totalValue());
        pstmt.setDouble(12, record.taxAmount());
        pstmt.setDouble(13, record.netValue());
        pstmt.setTimestamp(14, Timestamp.from(record.executedAt()));
        pstmt.setString(15, record.buyOrderId().map(UUID::toString).orElse(null));
        pstmt.setString(16, record.sellOrderId().map(UUID::toString).orElse(null));
        pstmt.setString(17, record.status().name());
        pstmt.setString(18, record.notes().orElse(null));
    }

    @Override
    public Optional<TradeRecord> getRecord(UUID recordId) {
        TradeRecord cached = recordsById.get(recordId);
        if (cached != null) {
            return Optional.of(cached);
        }

        // 从数据库查询
        String sql = "SELECT * FROM trade_history WHERE record_id = ?";

        try (Connection conn = databaseService.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, recordId.toString());

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    TradeRecord record = extractRecordFromResultSet(rs);
                    cacheRecord(record);
                    return Optional.of(record);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to get trade record", e);
        }

        return Optional.empty();
    }

    @Override
    public List<TradeRecord> getPlayerRecords(UUID playerId) {
        List<UUID> recordIds = playerRecords.get(playerId);
        if (recordIds == null) {
            return List.of();
        }

        return recordIds.stream()
                .map(recordsById::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public List<TradeRecord> getPlayerRecords(UUID playerId, int offset, int limit) {
        return getPlayerRecords(playerId).stream()
                .skip(offset)
                .limit(limit)
                .collect(Collectors.toList());
    }

    @Override
    public List<TradeRecord> getNationRecords(NationId nationId) {
        List<UUID> recordIds = nationRecords.get(nationId.toString());
        if (recordIds == null) {
            return List.of();
        }

        return recordIds.stream()
                .map(recordsById::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public List<TradeRecord> getNationRecords(NationId nationId, int offset, int limit) {
        return getNationRecords(nationId).stream()
                .skip(offset)
                .limit(limit)
                .collect(Collectors.toList());
    }

    @Override
    public List<TradeRecord> getResourceRecords(String resourceId) {
        List<UUID> recordIds = resourceRecords.get(resourceId.toLowerCase());
        if (recordIds == null) {
            return List.of();
        }

        return recordIds.stream()
                .map(recordsById::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public List<TradeRecord> getResourceRecords(String resourceId, int offset, int limit) {
        return getResourceRecords(resourceId).stream()
                .skip(offset)
                .limit(limit)
                .collect(Collectors.toList());
    }

    @Override
    public List<TradeRecord> getRecordsByTimeRange(Instant startTime, Instant endTime) {
        String sql = """
            SELECT * FROM trade_history
            WHERE executed_at BETWEEN ? AND ?
            ORDER BY executed_at DESC
            """;

        List<TradeRecord> records = new ArrayList<>();

        try (Connection conn = databaseService.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setTimestamp(1, Timestamp.from(startTime));
            pstmt.setTimestamp(2, Timestamp.from(endTime));

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    records.add(extractRecordFromResultSet(rs));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to get records by time range", e);
        }

        return records;
    }

    @Override
    public List<TradeRecord> getPlayerRecordsByTimeRange(UUID playerId, Instant startTime, Instant endTime) {
        String sql = """
            SELECT * FROM trade_history
            WHERE (buyer_player_id = ? OR seller_player_id = ?)
            AND executed_at BETWEEN ? AND ?
            ORDER BY executed_at DESC
            """;

        List<TradeRecord> records = new ArrayList<>();

        try (Connection conn = databaseService.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, playerId.toString());
            pstmt.setString(2, playerId.toString());
            pstmt.setTimestamp(3, Timestamp.from(startTime));
            pstmt.setTimestamp(4, Timestamp.from(endTime));

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    records.add(extractRecordFromResultSet(rs));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to get player records by time range", e);
        }

        return records;
    }

    @Override
    public double getPlayerTotalTradeValue(UUID playerId) {
        return getPlayerRecords(playerId).stream()
                .mapToDouble(TradeRecord::totalValue)
                .sum();
    }

    @Override
    public int getPlayerTradeCount(UUID playerId) {
        List<UUID> ids = playerRecords.get(playerId);
        return ids == null ? 0 : ids.size();
    }

    @Override
    public List<TradeRecord> getNationToNationRecords(NationId nation1, NationId nation2) {
        String sql = """
            SELECT * FROM trade_history
            WHERE (buyer_nation_id = ? AND seller_nation_id = ?)
            OR (buyer_nation_id = ? AND seller_nation_id = ?)
            ORDER BY executed_at DESC
            """;

        List<TradeRecord> records = new ArrayList<>();

        try (Connection conn = databaseService.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, nation1.toString());
            pstmt.setString(2, nation2.toString());
            pstmt.setString(3, nation2.toString());
            pstmt.setString(4, nation1.toString());

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    records.add(extractRecordFromResultSet(rs));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to get nation-to-nation records", e);
        }

        return records;
    }

    @Override
    public int deleteOldRecords(Instant before) {
        String sql = "DELETE FROM trade_history WHERE executed_at < ?";

        try (Connection conn = databaseService.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setTimestamp(1, Timestamp.from(before));
            return pstmt.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete old records", e);
        }
    }

    @Override
    public TradeStatistics getStatistics(Instant startTime, Instant endTime) {
        String sql = """
            SELECT COUNT(*) as count, SUM(amount) as volume, SUM(total_value) as total_value,
                   SUM(tax_amount) as total_tax, AVG(price_per_unit) as avg_price,
                   MAX(price_per_unit) as max_price, MIN(price_per_unit) as min_price,
                   MIN(executed_at) as oldest, MAX(executed_at) as newest
            FROM trade_history
            WHERE executed_at BETWEEN ? AND ?
            """;

        try (Connection conn = databaseService.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setTimestamp(1, Timestamp.from(startTime));
            pstmt.setTimestamp(2, Timestamp.from(endTime));

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new TradeStatistics(
                            rs.getInt("count"),
                            rs.getLong("volume"),
                            rs.getDouble("total_value"),
                            rs.getDouble("total_tax"),
                            rs.getDouble("avg_price"),
                            rs.getDouble("max_price"),
                            rs.getDouble("min_price"),
                            rs.getTimestamp("oldest") != null ? rs.getTimestamp("oldest").toInstant() : Instant.now(),
                            rs.getTimestamp("newest") != null ? rs.getTimestamp("newest").toInstant() : Instant.now()
                    );
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to get trade statistics", e);
        }

        return new TradeStatistics(0, 0, 0, 0, 0, 0, 0, Instant.now(), Instant.now());
    }

    @Override
    public TradeStatistics getPlayerStatistics(UUID playerId, Instant startTime, Instant endTime) {
        String sql = """
            SELECT COUNT(*) as count, SUM(amount) as volume, SUM(total_value) as total_value,
                   SUM(tax_amount) as total_tax, AVG(price_per_unit) as avg_price,
                   MAX(price_per_unit) as max_price, MIN(price_per_unit) as min_price,
                   MIN(executed_at) as oldest, MAX(executed_at) as newest
            FROM trade_history
            WHERE (buyer_player_id = ? OR seller_player_id = ?)
            AND executed_at BETWEEN ? AND ?
            """;

        try (Connection conn = databaseService.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, playerId.toString());
            pstmt.setString(2, playerId.toString());
            pstmt.setTimestamp(3, Timestamp.from(startTime));
            pstmt.setTimestamp(4, Timestamp.from(endTime));

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new TradeStatistics(
                            rs.getInt("count"),
                            rs.getLong("volume"),
                            rs.getDouble("total_value"),
                            rs.getDouble("total_tax"),
                            rs.getDouble("avg_price"),
                            rs.getDouble("max_price"),
                            rs.getDouble("min_price"),
                            rs.getTimestamp("oldest") != null ? rs.getTimestamp("oldest").toInstant() : Instant.now(),
                            rs.getTimestamp("newest") != null ? rs.getTimestamp("newest").toInstant() : Instant.now()
                    );
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to get player trade statistics", e);
        }

        return new TradeStatistics(0, 0, 0, 0, 0, 0, 0, Instant.now(), Instant.now());
    }

    private TradeRecord extractRecordFromResultSet(ResultSet rs) throws SQLException {
        String status = rs.getString("status");
        TradeRecord.TradeRecordStatus recordStatus = TradeRecord.TradeRecordStatus.valueOf(status);

        // 根据 status 判断是买还是卖 (COMPLETED 通常是买单)
        TradeOrder.OrderType orderType = recordStatus == TradeRecord.TradeRecordStatus.COMPLETED
            ? TradeOrder.OrderType.BUY : TradeOrder.OrderType.SELL;

        return new TradeRecord(
                UUID.fromString(rs.getString("record_id")),
                orderType,
                TradeOrder.OrderSource.valueOf(rs.getString("order_source")),
                rs.getString("seller_player_id") != null ? UUID.fromString(rs.getString("seller_player_id")) : null,
                rs.getString("buyer_player_id") != null ? UUID.fromString(rs.getString("buyer_player_id")) : null,
                rs.getString("seller_nation_id") != null ? NationId.of(UUID.fromString(rs.getString("seller_nation_id"))) : null,
                rs.getString("buyer_nation_id") != null ? NationId.of(UUID.fromString(rs.getString("buyer_nation_id"))) : null,
                rs.getString("resource_id"),
                rs.getLong("amount"),
                rs.getDouble("price_per_unit"),
                rs.getDouble("tax_amount"),
                rs.getTimestamp("executed_at").toInstant(),
                rs.getString("buy_order_id") != null ? UUID.fromString(rs.getString("buy_order_id")) : null,
                rs.getString("sell_order_id") != null ? UUID.fromString(rs.getString("sell_order_id")) : null,
                rs.getString("notes")
        );
    }

    /**
     * 持久化所有缓存中的记录
     */
    public void persistAllRecords() {
        if (!recordsById.isEmpty()) {
            persistRecords(recordsById.values());
        }
    }
}
