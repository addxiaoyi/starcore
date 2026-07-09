package dev.starcore.starcore.module.shop.storage;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.starcore.starcore.core.database.DatabaseService;
import dev.starcore.starcore.module.shop.model.*;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import java.math.BigDecimal;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.logging.Logger;

public class ShopDatabaseStorage {

    private final DatabaseService databaseService;
    private final Logger logger;
    private final Gson gson;

    private static final String TABLE_SHOPS = "shop_shops";
    private static final String TABLE_ITEMS = "shop_items";
    private static final String TABLE_TRANSACTIONS = "shop_transactions";
    private static final String TABLE_NPC_BINDINGS = "shop_npc_bindings";
    private static final String TABLE_TRADE_RECORDS = "shop_trade_records";
    private static final String TABLE_TEMPLATES = "shop_templates";

    public ShopDatabaseStorage(DatabaseService databaseService, Logger logger) {
        this.databaseService = databaseService;
        this.logger = logger;
        // Gson 配置：在 Java 21 中排除可能导致访问异常的内部字段
        this.gson = new GsonBuilder()
            .setExclusionStrategies(new ExclusionStrategy() {
                @Override
                public boolean shouldSkipField(FieldAttributes f) {
                    // 排除 Location 类型字段（包含无法序列化的 World 引用）
                    Class<?> declaringClass = f.getDeclaredClass();
                    if (Location.class.isAssignableFrom(declaringClass)) {
                        return true;
                    }
                    // 排除 java.lang 包中无法访问的内部类字段
                    String className = declaringClass.getName();
                    if (className.startsWith("java.lang.ref") ||
                        className.startsWith("java.lang.invoke")) {
                        return true;
                    }
                    return false;
                }

                @Override
                public boolean shouldSkipClass(Class<?> clazz) {
                    // 跳过 Location 和 World 类型
                    return Location.class.isAssignableFrom(clazz) ||
                           World.class.isAssignableFrom(clazz);
                }
            })
            .create();
        initializeTables();
    }

    private void initializeTables() {
        databaseService.execute(
            "CREATE TABLE IF NOT EXISTS " + TABLE_SHOPS + " (" +
            "shop_id TEXT PRIMARY KEY, owner_id TEXT NOT NULL, owner_type TEXT NOT NULL," +
            "name TEXT NOT NULL, description TEXT, shop_type TEXT NOT NULL," +
            "world TEXT, x REAL, y REAL, z REAL, npc_id INTEGER," +
            "infinite_stock INTEGER DEFAULT 0, buy_enabled INTEGER DEFAULT 1," +
            "sell_enabled INTEGER DEFAULT 0, nation_public INTEGER DEFAULT 0," +
            "global_public INTEGER DEFAULT 0, open INTEGER DEFAULT 1," +
            "allowed_players TEXT, blocked_players TEXT," +
            "created_at TEXT, updated_at TEXT)"
        );

        databaseService.execute(
            "CREATE TABLE IF NOT EXISTS " + TABLE_ITEMS + " (" +
            "item_id TEXT PRIMARY KEY, shop_id TEXT NOT NULL, material TEXT NOT NULL," +
            "display_name TEXT, lore TEXT, amount INTEGER DEFAULT 1," +
            "stock INTEGER DEFAULT 0, max_stock INTEGER DEFAULT 100," +
            "buy_price TEXT, sell_price TEXT, restock_amount INTEGER DEFAULT 0," +
            "restock_interval INTEGER DEFAULT 0, last_restock TEXT," +
            "infinite_stock INTEGER DEFAULT 0, metadata TEXT," +
            "created_at TEXT, updated_at TEXT)"
        );

        databaseService.execute(
            "CREATE TABLE IF NOT EXISTS " + TABLE_TRANSACTIONS + " (" +
            "transaction_id TEXT PRIMARY KEY, shop_id TEXT NOT NULL," +
            "player_id TEXT NOT NULL, type TEXT NOT NULL, amount TEXT NOT NULL," +
            "timestamp TEXT NOT NULL, item_details TEXT)"
        );

        databaseService.execute(
            "CREATE TABLE IF NOT EXISTS " + TABLE_NPC_BINDINGS + " (" +
            "npc_id INTEGER PRIMARY KEY, shop_id TEXT NOT NULL)"
        );

        databaseService.execute(
            "CREATE TABLE IF NOT EXISTS " + TABLE_TRADE_RECORDS + " (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, shop_id TEXT NOT NULL," +
            "player_id TEXT NOT NULL, daily_count INTEGER DEFAULT 0," +
            "weekly_count INTEGER DEFAULT 0, monthly_count INTEGER DEFAULT 0," +
            "last_reset_daily TEXT, last_reset_weekly TEXT, last_reset_monthly TEXT," +
            "limit_daily INTEGER DEFAULT 100, limit_weekly INTEGER DEFAULT 500," +
            "limit_monthly INTEGER DEFAULT 2000, unlimited INTEGER DEFAULT 0)"
        );

        databaseService.execute(
            "CREATE TABLE IF NOT EXISTS " + TABLE_TEMPLATES + " (" +
            "template_id TEXT PRIMARY KEY, data TEXT NOT NULL)"
        );
    }

    public void saveShop(Shop shop) {
        String sql = "INSERT OR REPLACE INTO " + TABLE_SHOPS + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        String worldName = null;
        double x = 0, y = 0, z = 0;
        if (shop.location() != null && shop.location().getWorld() != null) {
            worldName = shop.location().getWorld().getName();
            x = shop.location().getX();
            y = shop.location().getY();
            z = shop.location().getZ();
        }

        databaseService.update(sql,
            shop.shopId().toString(), shop.ownerId().toString(), shop.ownerType().name(),
            shop.name(), shop.description(), shop.shopType().name(),
            worldName, x, y, z, shop.npcId(),
            shop.infiniteStock() ? 1 : 0, shop.buyEnabled() ? 1 : 0,
            shop.sellEnabled() ? 1 : 0, shop.nationPublic() ? 1 : 0,
            shop.globalPublic() ? 1 : 0, shop.isOpen() ? 1 : 0,
            gson.toJson(shop.allowedPlayers()), gson.toJson(shop.blockedPlayers()),
            shop.createdAt().toString(), Instant.now().toString()
        );
    }

    public List<Shop> loadAllShops() {
        List<Shop> shops = new ArrayList<>();
        try {
            databaseService.query("SELECT * FROM " + TABLE_SHOPS, rs -> {
                try {
                    while (rs.next()) {
                        try {
                            Shop shop = loadShopFromResultSet(rs);
                            shops.add(shop);
                        } catch (Exception e) {
                            logger.warning("Failed to load shop: " + e.getMessage());
                        }
                    }
                } catch (java.sql.SQLException e) {
                    logger.warning("Failed to iterate shops: " + e.getMessage());
                }
                return shops;
            });
        } catch (Exception e) {
            logger.warning("Failed to load shops: " + e.getMessage());
        }
        return shops;
    }

    private Shop loadShopFromResultSet(ResultSet rs) throws SQLException {
        UUID shopId = UUID.fromString(rs.getString("shop_id"));
        UUID ownerId = UUID.fromString(rs.getString("owner_id"));
        ShopOwnerType ownerType = ShopOwnerType.valueOf(rs.getString("owner_type"));
        String name = rs.getString("name");
        ShopType shopType = ShopType.valueOf(rs.getString("shop_type"));

        Shop shop = new Shop(shopId, ownerId, ownerType, name, shopType);

        String description = rs.getString("description");
        if (description != null) shop.setDescription(description);

        String worldName = rs.getString("world");
        if (worldName != null) {
            World world = org.bukkit.Bukkit.getWorld(worldName);
            if (world != null) {
                shop.setLocation(new Location(world, rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z")));
            }
        }

        int npcIdVal = rs.getInt("npc_id");
        if (!rs.wasNull()) shop.setNpcId(npcIdVal);

        shop.setInfiniteStock(rs.getBoolean("infinite_stock"));
        shop.setBuyEnabled(rs.getBoolean("buy_enabled"));
        shop.setSellEnabled(rs.getBoolean("sell_enabled"));
        shop.setNationPublic(rs.getBoolean("nation_public"));
        shop.setGlobalPublic(rs.getBoolean("global_public"));
        shop.setOpen(rs.getBoolean("open"));

        return shop;
    }

    public void deleteShop(UUID shopId) {
        databaseService.update("DELETE FROM " + TABLE_ITEMS + " WHERE shop_id = ?", shopId.toString());
        databaseService.update("DELETE FROM " + TABLE_TRANSACTIONS + " WHERE shop_id = ?", shopId.toString());
        databaseService.update("DELETE FROM " + TABLE_SHOPS + " WHERE shop_id = ?", shopId.toString());
    }

    public void saveShopItem(UUID shopId, ShopItem item) {
        String sql = "INSERT OR REPLACE INTO " + TABLE_ITEMS + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        databaseService.update(sql,
            item.itemId().toString(), shopId.toString(), item.material().name(),
            item.displayName(), gson.toJson(item.lore()), item.amount(), item.stock(), item.maxStock(),
            item.buyPrice().toPlainString(), item.sellPrice().toPlainString(),
            item.restockAmount(), item.restockInterval(),
            item.lastRestock() != null ? item.lastRestock().toString() : null,
            item.infiniteStock() ? 1 : 0, gson.toJson(item.metadata()),
            item.createdAt().toString(), item.lastUpdated().toString()
        );
    }

    public void deleteShopItem(UUID shopId, UUID itemId) {
        databaseService.update("DELETE FROM " + TABLE_ITEMS + " WHERE item_id = ?", itemId.toString());
    }

    public List<ShopItem> loadShopItems(UUID shopId) {
        List<ShopItem> items = new ArrayList<>();
        try {
            databaseService.query("SELECT * FROM " + TABLE_ITEMS + " WHERE shop_id = ?",
                rs -> {
                    try {
                        while (rs.next()) {
                            try {
                                items.add(loadItemFromResultSet(rs));
                            } catch (Exception e) {
                                logger.warning("Failed to load item: " + e.getMessage());
                            }
                        }
                    } catch (java.sql.SQLException e) {
                        logger.warning("Failed to iterate items: " + e.getMessage());
                    }
                    return items;
                }, shopId.toString()
            );
        } catch (Exception e) {
            logger.warning("Failed to load shop items: " + e.getMessage());
        }
        return items;
    }

    private ShopItem loadItemFromResultSet(ResultSet rs) throws SQLException {
        UUID itemId = UUID.fromString(rs.getString("item_id"));
        Material material = Material.valueOf(rs.getString("material"));
        ShopItem item = new ShopItem(itemId, material, rs.getInt("amount"),
            new BigDecimal(rs.getString("buy_price")), new BigDecimal(rs.getString("sell_price")),
            rs.getInt("stock"), rs.getInt("max_stock"));
        item.setInfiniteStock(rs.getBoolean("infinite_stock"));
        item.setRestockAmount(rs.getInt("restock_amount"));
        item.setRestockInterval(rs.getInt("restock_interval"));
        String lastRestock = rs.getString("last_restock");
        if (lastRestock != null) item.setLastRestock(Instant.parse(lastRestock));
        String displayName = rs.getString("display_name");
        if (displayName != null) item.setDisplayName(displayName);
        return item;
    }

    public void saveTransaction(ShopTransaction transaction) {
        databaseService.update("INSERT INTO " + TABLE_TRANSACTIONS + " VALUES (?, ?, ?, ?, ?, ?, ?)",
            transaction.getTransactionId().toString(), transaction.getShopId().toString(),
            transaction.getPlayerId().toString(), transaction.getType().name(),
            transaction.getAmount().toPlainString(), transaction.getTimestamp().toString(),
            transaction.getItemDetails()
        );
    }

    public List<ShopTransaction> loadAllTransactions() {
        return loadTransactionsWithLimit("ORDER BY timestamp DESC LIMIT " + MAX_TRANSACTION_LIMIT, (Object[]) null);
    }

    /**
     * 加载指定商店的交易记录
     * @param shopId 商店ID
     * @return 交易记录列表
     */
    public List<ShopTransaction> loadTransactionsByShop(UUID shopId) {
        return loadTransactionsWithLimit(
            "WHERE shop_id = ? ORDER BY timestamp DESC LIMIT " + MAX_TRANSACTION_LIMIT,
            shopId.toString()
        );
    }

    /**
     * 加载指定玩家的交易记录
     * @param playerId 玩家ID
     * @param limit 返回数量限制（0表示使用默认值）
     * @return 交易记录列表
     */
    public List<ShopTransaction> loadTransactionsByPlayer(UUID playerId, int limit) {
        int effectiveLimit = limit > 0 ? Math.min(limit, MAX_TRANSACTION_LIMIT) : MAX_TRANSACTION_LIMIT;
        return loadTransactionsWithLimit(
            "WHERE player_id = ? ORDER BY timestamp DESC LIMIT " + effectiveLimit,
            playerId.toString()
        );
    }

    private static final int MAX_TRANSACTION_LIMIT = 10000;

    private List<ShopTransaction> loadTransactionsWithLimit(String whereClause, Object... args) {
        List<ShopTransaction> transactions = new ArrayList<>();
        String sql = "SELECT * FROM " + TABLE_TRANSACTIONS + " " + whereClause;
        try {
            databaseService.query(sql, rs -> {
                try {
                    while (rs.next()) {
                        try {
                            transactions.add(new ShopTransaction(
                                UUID.fromString(rs.getString("transaction_id")),
                                UUID.fromString(rs.getString("shop_id")),
                                UUID.fromString(rs.getString("player_id")),
                                TransactionType.valueOf(rs.getString("type")),
                                new BigDecimal(rs.getString("amount")),
                                Instant.parse(rs.getString("timestamp")),
                                rs.getString("item_details")
                            ));
                        } catch (Exception e) {
                            logger.warning("Failed to load transaction: " + e.getMessage());
                        }
                    }
                } catch (java.sql.SQLException e) {
                    logger.warning("Failed to iterate transactions: " + e.getMessage());
                }
                return transactions;
            }, args);
        } catch (Exception e) {
            logger.warning("Failed to load transactions: " + e.getMessage());
        }
        return transactions;
    }

    public void saveNpcBinding(int npcId, UUID shopId) {
        databaseService.update("INSERT OR REPLACE INTO " + TABLE_NPC_BINDINGS + " VALUES (?, ?)", npcId, shopId.toString());
    }

    public void deleteNpcBinding(int npcId) {
        databaseService.update("DELETE FROM " + TABLE_NPC_BINDINGS + " WHERE npc_id = ?", npcId);
    }

    public Map<Integer, UUID> loadNpcBindings() {
        Map<Integer, UUID> bindings = new HashMap<>();
        try {
            databaseService.query("SELECT * FROM " + TABLE_NPC_BINDINGS, rs -> {
                try {
                    while (rs.next()) {
                        bindings.put(rs.getInt("npc_id"), UUID.fromString(rs.getString("shop_id")));
                    }
                } catch (java.sql.SQLException e) {
                    logger.warning("Failed to iterate NPC bindings: " + e.getMessage());
                }
                return bindings;
            });
        } catch (Exception e) {
            logger.warning("Failed to load NPC bindings: " + e.getMessage());
        }
        return bindings;
    }

    public void saveTradeRecord(UUID shopId, UUID playerId, Object record) {
        databaseService.update("INSERT OR REPLACE INTO " + TABLE_TRADE_RECORDS +
            " (shop_id, player_id, daily_count, weekly_count, monthly_count) VALUES (?, ?, ?, ?, ?)",
            shopId.toString(), playerId.toString(), 0, 0, 0
        );
    }

    public Map<UUID, Map<UUID, Object>> loadTradeRecords() {
        Map<UUID, Map<UUID, Object>> result = new HashMap<>();
        try {
            databaseService.query("SELECT * FROM " + TABLE_TRADE_RECORDS, rs -> {
                try {
                    while (rs.next()) {
                        UUID shopId = UUID.fromString(rs.getString("shop_id"));
                        UUID playerId = UUID.fromString(rs.getString("player_id"));

                        // 创建交易记录对象
                        TradeRecordData record = new TradeRecordData();
                        record.dailyCount = rs.getInt("daily_count");
                        record.weeklyCount = rs.getInt("weekly_count");
                        record.monthlyCount = rs.getInt("monthly_count");
                        record.limitDaily = rs.getInt("limit_daily");
                        record.limitWeekly = rs.getInt("limit_weekly");
                        record.limitMonthly = rs.getInt("limit_monthly");
                        record.unlimited = rs.getBoolean("unlimited");

                        String lastResetDaily = rs.getString("last_reset_daily");
                        if (lastResetDaily != null) record.lastResetDaily = Instant.parse(lastResetDaily);
                        String lastResetWeekly = rs.getString("last_reset_weekly");
                        if (lastResetWeekly != null) record.lastResetWeekly = Instant.parse(lastResetWeekly);
                        String lastResetMonthly = rs.getString("last_reset_monthly");
                        if (lastResetMonthly != null) record.lastResetMonthly = Instant.parse(lastResetMonthly);

                        // 按 shopId 分组
                        result.computeIfAbsent(shopId, k -> new HashMap<>()).put(playerId, record);
                    }
                } catch (java.sql.SQLException e) {
                    logger.warning("Failed to iterate trade records: " + e.getMessage());
                }
                return result;
            });
        } catch (Exception e) {
            logger.warning("Failed to load trade records: " + e.getMessage());
        }
        return result;
    }

    /**
     * 交易记录数据结构（用于数据库存储）
     */
    private static class TradeRecordData {
        int dailyCount = 0;
        int weeklyCount = 0;
        int monthlyCount = 0;
        Instant lastResetDaily = Instant.now();
        Instant lastResetWeekly = Instant.now();
        Instant lastResetMonthly = Instant.now();
        int limitDaily = 100;
        int limitWeekly = 500;
        int limitMonthly = 2000;
        boolean unlimited = false;
    }

    /**
     * 保存商店模板 - 手动序列化避免 Gson 的 Java 21 兼容性问题
     */
    public void saveTemplate(Shop template) {
        // 手动构建 JSON 字符串，避免序列化 Location 等问题字段
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"shopId\":\"").append(template.shopId()).append("\",");
        sb.append("\"ownerId\":\"").append(template.ownerId()).append("\",");
        sb.append("\"ownerType\":\"").append(template.ownerType().name()).append("\",");
        sb.append("\"name\":\"").append(escapeJson(template.name())).append("\",");
        if (template.description() != null) {
            sb.append("\"description\":\"").append(escapeJson(template.description())).append("\",");
        }
        sb.append("\"shopType\":\"").append(template.shopType().name()).append("\",");
        sb.append("\"infiniteStock\":").append(template.infiniteStock()).append(",");
        sb.append("\"buyEnabled\":").append(template.buyEnabled()).append(",");
        sb.append("\"sellEnabled\":").append(template.sellEnabled()).append(",");
        sb.append("\"nationPublic\":").append(template.nationPublic()).append(",");
        sb.append("\"globalPublic\":").append(template.globalPublic()).append(",");
        sb.append("\"open\":").append(template.isOpen()).append(",");
        sb.append("\"createdAt\":\"").append(template.createdAt()).append("\"");
        sb.append("}");
        databaseService.update("INSERT OR REPLACE INTO " + TABLE_TEMPLATES + " VALUES (?, ?)",
            template.name().toLowerCase(), sb.toString()
        );
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    public void deleteTemplate(String templateId) {
        databaseService.update("DELETE FROM " + TABLE_TEMPLATES + " WHERE template_id = ?", templateId);
    }

    public List<Shop> loadTemplates() {
        List<Shop> templates = new ArrayList<>();
        try {
            databaseService.query("SELECT * FROM " + TABLE_TEMPLATES, rs -> {
                try {
                    while (rs.next()) {
                        try {
                            Shop template = loadShopFromJson(rs.getString("data"));
                            if (template != null) {
                                templates.add(template);
                            }
                        } catch (Exception e) {
                            logger.warning("Failed to load template: " + e.getMessage());
                        }
                    }
                } catch (java.sql.SQLException e) {
                    logger.warning("Failed to iterate templates: " + e.getMessage());
                }
                return templates;
            });
        } catch (Exception e) {
            logger.warning("Failed to load templates: " + e.getMessage());
        }
        return templates;
    }

    /**
     * 从 JSON 字符串加载商店 - 手动解析避免 Gson 问题
     */
    private Shop loadShopFromJson(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            // 手动解析关键字段
            UUID shopId = parseJsonUuid(json, "shopId");
            UUID ownerId = parseJsonUuid(json, "ownerId");
            String ownerTypeStr = parseJsonString(json, "ownerType");
            String name = parseJsonString(json, "name");
            String shopTypeStr = parseJsonString(json, "shopType");
            String description = parseJsonString(json, "description");

            ShopOwnerType ownerType = ownerTypeStr != null ?
                ShopOwnerType.valueOf(ownerTypeStr) : ShopOwnerType.PLAYER;
            ShopType shopType = shopTypeStr != null ?
                ShopType.valueOf(shopTypeStr) : ShopType.PLAYER;

            Shop shop = Shop.create(ownerId, ownerType, name, shopType);
            // 使用反射设置 shopId（因为 Shop 的 shopId 是 final）
            java.lang.reflect.Field shopIdField = Shop.class.getDeclaredField("shopId");
            shopIdField.setAccessible(true);
            shopIdField.set(shop, shopId);

            if (description != null) {
                shop.setDescription(description);
            }
            return shop;
        } catch (Exception e) {
            logger.warning("Failed to parse shop JSON: " + e.getMessage());
            return null;
        }
    }

    private UUID parseJsonUuid(String json, String field) {
        try {
            String pattern = "\"" + field + "\":\"";
            int start = json.indexOf(pattern);
            if (start == -1) return null;
            start += pattern.length();
            int end = json.indexOf("\"", start);
            if (end == -1) return null;
            return UUID.fromString(json.substring(start, end));
        } catch (Exception e) {
            return null;
        }
    }

    private String parseJsonString(String json, String field) {
        try {
            String pattern = "\"" + field + "\":\"";
            int start = json.indexOf(pattern);
            if (start == -1) return null;
            start += pattern.length();
            int end = start;
            while (end < json.length()) {
                char c = json.charAt(end);
                if (c == '\\') {
                    end += 2;
                } else if (c == '"') {
                    break;
                } else {
                    end++;
                }
            }
            return unescapeJson(json.substring(start, end));
        } catch (Exception e) {
            return null;
        }
    }

    private String unescapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char next = s.charAt(++i);
                switch (next) {
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    default: sb.append(next); break;
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
