package dev.starcore.starcore.module.shop.npc;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.starcore.starcore.core.database.DatabaseService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import java.util.logging.Logger;

/**
 * NPC商店数据持久化
 * 负责商店数据的数据库存储和加载
 */
public class NpcShopStorage {

    private final DatabaseService databaseService;
    private final Logger logger;
    private final Gson gson;

    // 表名常量
    private static final String TABLE_SHOPS = "npcshop_shops";
    private static final String TABLE_ITEMS = "npcshop_items";
    private static final String TABLE_TRADES = "npcshop_trades";

    public NpcShopStorage(DatabaseService databaseService, Logger logger) {
        this.databaseService = databaseService;
        this.logger = logger;
        this.gson = new GsonBuilder().create();
        initializeTables();
    }

    /**
     * 初始化数据库表
     */
    private void initializeTables() {
        // 商店表
        databaseService.execute(
            "CREATE TABLE IF NOT EXISTS " + TABLE_SHOPS + " (" +
            "id TEXT PRIMARY KEY, " +
            "name TEXT NOT NULL, " +
            "owner_id TEXT NOT NULL, " +
            "owner_name TEXT NOT NULL, " +
            "world TEXT, " +
            "x REAL, " +
            "y REAL, " +
            "z REAL, " +
            "npc_id INTEGER, " +
            "infinite_stock INTEGER DEFAULT 1, " +
            "buy_enabled INTEGER DEFAULT 1, " +
            "sell_enabled INTEGER DEFAULT 0, " +
            "created_at TEXT, " +
            "description TEXT)"
        );

        // 物品表
        databaseService.execute(
            "CREATE TABLE IF NOT EXISTS " + TABLE_ITEMS + " (" +
            "id TEXT PRIMARY KEY, " +
            "shop_id TEXT NOT NULL, " +
            "material TEXT NOT NULL, " +
            "display_name TEXT, " +
            "amount INTEGER DEFAULT 1, " +
            "buy_price TEXT NOT NULL, " +
            "sell_price TEXT NOT NULL, " +
            "stock INTEGER DEFAULT 0, " +
            "max_stock INTEGER DEFAULT 100, " +
            "infinite_stock INTEGER DEFAULT 0, " +
            "created_at TEXT)"
        );

        // 交易记录表
        databaseService.execute(
            "CREATE TABLE IF NOT EXISTS " + TABLE_TRADES + " (" +
            "id TEXT PRIMARY KEY, " +
            "shop_id TEXT NOT NULL, " +
            "player_id TEXT NOT NULL, " +
            "player_name TEXT NOT NULL, " +
            "item_id TEXT NOT NULL, " +
            "material TEXT NOT NULL, " +
            "type TEXT NOT NULL, " +
            "quantity INTEGER NOT NULL, " +
            "total_price TEXT NOT NULL, " +
            "timestamp TEXT NOT NULL)"
        );

        // 创建索引
        databaseService.execute(
            "CREATE INDEX IF NOT EXISTS idx_items_shop ON " + TABLE_ITEMS + " (shop_id)"
        );
        databaseService.execute(
            "CREATE INDEX IF NOT EXISTS idx_trades_shop ON " + TABLE_TRADES + " (shop_id)"
        );
        databaseService.execute(
            "CREATE INDEX IF NOT EXISTS idx_trades_player ON " + TABLE_TRADES + " (player_id)"
        );
    }

    // ==================== 商店操作 ====================

    /**
     * 保存商店
     */
    public void saveShop(NpcShopData shop) {
        String sql = "INSERT OR REPLACE INTO " + TABLE_SHOPS +
            " (id, name, owner_id, owner_name, world, x, y, z, npc_id, infinite_stock, buy_enabled, sell_enabled, created_at, description) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        String worldName = null;
        double x = 0, y = 0, z = 0;

        if (shop.location() != null && shop.location().getWorld() != null) {
            worldName = shop.location().getWorld().getName();
            x = shop.location().getX();
            y = shop.location().getY();
            z = shop.location().getZ();
        }

        databaseService.update(sql,
            shop.id().toString(),
            shop.name(),
            shop.ownerId().toString(),
            shop.ownerName(),
            worldName,
            x,
            y,
            z,
            shop.npcId(),
            shop.infiniteStock() ? 1 : 0,
            shop.buyEnabled() ? 1 : 0,
            shop.sellEnabled() ? 1 : 0,
            shop.createdAt().toString(),
            shop.description()
        );
    }

    /**
     * 加载所有商店
     */
    public List<NpcShopData> loadAllShops() {
        List<NpcShopData> shops = new ArrayList<>();

        try {
            databaseService.query("SELECT * FROM " + TABLE_SHOPS, rs -> {
                try {
                    while (rs.next()) {
                        try {
                            shops.add(loadShopFromResultSet(rs));
                        } catch (Exception e) {
                            logger.warning("加载商店失败: " + e.getMessage());
                        }
                    }
                } catch (SQLException e) {
                    logger.warning("遍历商店结果失败: " + e.getMessage());
                }
                return shops;
            });
        } catch (Exception e) {
            logger.warning("加载商店列表失败: " + e.getMessage());
        }

        return shops;
    }

    /**
     * 从ResultSet加载商店
     */
    private NpcShopData loadShopFromResultSet(ResultSet rs) throws SQLException {
        UUID id = UUID.fromString(rs.getString("id"));
        String name = rs.getString("name");
        UUID ownerId = UUID.fromString(rs.getString("owner_id"));
        String ownerName = rs.getString("owner_name");

        Location location = null;
        String worldName = rs.getString("world");
        if (worldName != null) {
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                location = new Location(world, rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"));
            }
        }

        Integer npcId = null;
        int npcIdVal = rs.getInt("npc_id");
        if (!rs.wasNull()) {
            npcId = npcIdVal;
        }

        boolean infiniteStock = rs.getBoolean("infinite_stock");
        boolean buyEnabled = rs.getBoolean("buy_enabled");
        boolean sellEnabled = rs.getBoolean("sell_enabled");

        Instant createdAt = Instant.parse(rs.getString("created_at"));

        String description = rs.getString("description");

        return new NpcShopData(
            id, name, ownerId, ownerName, location, npcId,
            infiniteStock, buyEnabled, sellEnabled, createdAt, description
        );
    }

    /**
     * 删除商店
     */
    public void deleteShop(UUID shopId) {
        // 先删除物品
        databaseService.update("DELETE FROM " + TABLE_ITEMS + " WHERE shop_id = ?", shopId.toString());
        // 删除交易记录
        databaseService.update("DELETE FROM " + TABLE_TRADES + " WHERE shop_id = ?", shopId.toString());
        // 删除商店
        databaseService.update("DELETE FROM " + TABLE_SHOPS + " WHERE id = ?", shopId.toString());
    }

    // ==================== 物品操作 ====================

    /**
     * 保存商店物品
     */
    public void saveShopItem(NpcShopItemData item) {
        String sql = "INSERT OR REPLACE INTO " + TABLE_ITEMS +
            " (id, shop_id, material, display_name, amount, buy_price, sell_price, stock, max_stock, infinite_stock, created_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        databaseService.update(sql,
            item.id().toString(),
            item.shopId().toString(),
            item.material(),
            item.displayName(),
            item.amount(),
            item.buyPrice().toPlainString(),
            item.sellPrice().toPlainString(),
            item.stock(),
            item.maxStock(),
            item.infiniteStock() ? 1 : 0,
            item.createdAt().toString()
        );
    }

    /**
     * 加载商店物品
     */
    public List<NpcShopItemData> loadShopItems(UUID shopId) {
        List<NpcShopItemData> items = new ArrayList<>();

        try {
            databaseService.query(
                "SELECT * FROM " + TABLE_ITEMS + " WHERE shop_id = ?",
                rs -> {
                    try {
                        while (rs.next()) {
                            try {
                                items.add(loadItemFromResultSet(rs));
                            } catch (Exception e) {
                                logger.warning("加载物品失败: " + e.getMessage());
                            }
                        }
                    } catch (SQLException e) {
                        logger.warning("遍历物品结果失败: " + e.getMessage());
                    }
                    return items;
                },
                shopId.toString()
            );
        } catch (Exception e) {
            logger.warning("加载商店物品失败: " + e.getMessage());
        }

        return items;
    }

    /**
     * 从ResultSet加载物品
     */
    private NpcShopItemData loadItemFromResultSet(ResultSet rs) throws SQLException {
        UUID id = UUID.fromString(rs.getString("id"));
        UUID shopId = UUID.fromString(rs.getString("shop_id"));
        String material = rs.getString("material");
        String displayName = rs.getString("display_name");
        int amount = rs.getInt("amount");
        BigDecimal buyPrice = new BigDecimal(rs.getString("buy_price"));
        BigDecimal sellPrice = new BigDecimal(rs.getString("sell_price"));
        int stock = rs.getInt("stock");
        int maxStock = rs.getInt("max_stock");
        boolean infiniteStock = rs.getBoolean("infinite_stock");
        Instant createdAt = Instant.parse(rs.getString("created_at"));

        return new NpcShopItemData(
            id, shopId, material, displayName, amount,
            buyPrice, sellPrice, stock, maxStock, infiniteStock, createdAt
        );
    }

    /**
     * 删除商店物品
     */
    public void deleteShopItem(UUID shopId, UUID itemId) {
        databaseService.update(
            "DELETE FROM " + TABLE_ITEMS + " WHERE id = ? AND shop_id = ?",
            itemId.toString(), shopId.toString()
        );
    }

    // ==================== 交易记录操作 ====================

    /**
     * NPC商店交易记录（简化版）
     */
    public record NpcTradeRecord(
        UUID id,
        UUID shopId,
        UUID playerId,
        String playerName,
        UUID itemId,
        String material,
        TradeType type,
        int quantity,
        BigDecimal totalPrice,
        Instant timestamp
    ) {
        public enum TradeType {
            BUY, SELL
        }
    }

    /**
     * 保存交易记录
     */
    public void saveTradeRecord(NpcTradeRecord record) {
        String sql = "INSERT INTO " + TABLE_TRADES +
            " (id, shop_id, player_id, player_name, item_id, material, type, quantity, total_price, timestamp) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        databaseService.update(sql,
            record.id().toString(),
            record.shopId().toString(),
            record.playerId().toString(),
            record.playerName(),
            record.itemId().toString(),
            record.material(),
            record.type().name(),
            record.quantity(),
            record.totalPrice().toPlainString(),
            record.timestamp().toString()
        );
    }

    /**
     * 加载交易记录
     */
    public List<NpcTradeRecord> loadTradeHistory() {
        List<NpcTradeRecord> records = new ArrayList<>();

        try {
            // 加载最近10000条记录
            databaseService.query(
                "SELECT * FROM " + TABLE_TRADES + " ORDER BY timestamp DESC LIMIT 10000",
                rs -> {
                    try {
                        while (rs.next()) {
                            try {
                                records.add(loadTradeFromResultSet(rs));
                            } catch (Exception e) {
                                logger.warning("加载交易记录失败: " + e.getMessage());
                            }
                        }
                    } catch (SQLException e) {
                        logger.warning("遍历交易记录失败: " + e.getMessage());
                    }
                    return records;
                }
            );
        } catch (Exception e) {
            logger.warning("加载交易历史失败: " + e.getMessage());
        }

        return records;
    }

    /**
     * 从ResultSet加载交易记录
     */
    private NpcTradeRecord loadTradeFromResultSet(ResultSet rs) throws SQLException {
        UUID id = UUID.fromString(rs.getString("id"));
        UUID shopId = UUID.fromString(rs.getString("shop_id"));
        UUID playerId = UUID.fromString(rs.getString("player_id"));
        String playerName = rs.getString("player_name");
        UUID itemId = UUID.fromString(rs.getString("item_id"));
        String material = rs.getString("material");
        NpcTradeRecord.TradeType type = NpcTradeRecord.TradeType.valueOf(rs.getString("type"));
        int quantity = rs.getInt("quantity");
        BigDecimal totalPrice = new BigDecimal(rs.getString("total_price"));
        Instant timestamp = Instant.parse(rs.getString("timestamp"));

        return new NpcTradeRecord(
            id, shopId, playerId, playerName, itemId, material,
            type, quantity, totalPrice, timestamp
        );
    }

    /**
     * 清理旧交易记录（保留最近N条）
     */
    public void cleanupOldTrades(int keepCount) {
        try {
            databaseService.update(
                "DELETE FROM " + TABLE_TRADES + " WHERE id NOT IN " +
                "(SELECT id FROM " + TABLE_TRADES + " ORDER BY timestamp DESC LIMIT ?)",
                keepCount
            );
        } catch (Exception e) {
            logger.warning("清理旧交易记录失败: " + e.getMessage());
        }
    }
}