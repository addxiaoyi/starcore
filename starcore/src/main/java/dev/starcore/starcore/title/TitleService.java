package dev.starcore.starcore.title;
import java.util.Optional;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import dev.starcore.starcore.core.database.DatabaseService;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * 称号服务核心
 * 负责称号和徽章的管理、装备、解锁
 */
public class TitleService {
    private final Plugin plugin;
    private final DatabaseService databaseService;
    private final Logger logger;

    // 称号和徽章注册表
    private final Map<String, Title> titles = new ConcurrentHashMap<>();
    private final Map<String, Badge> badges = new ConcurrentHashMap<>();

    // 玩家数据缓存
    private final Cache<UUID, PlayerTitle> playerCache = CacheBuilder.newBuilder()
        .expireAfterWrite(30, TimeUnit.MINUTES)
        .maximumSize(1000)
        .build();

    // D-086: 初始化就绪屏障 —— initialize() 仍是异步落表，但通过 ready 标记 + 短时同步等待，
    // 既能避免阻塞主线程太长，又能在表尚在创建时让上层调用 savePlayerData/getPlayerData 不直接 SQLException。
    private final java.util.concurrent.atomic.AtomicBoolean ready = new java.util.concurrent.atomic.AtomicBoolean(false);
    public boolean isReady() { return ready.get(); }
    public void awaitReady(long timeoutMs) {
        long deadline = System.currentTimeMillis() + Math.max(0, timeoutMs);
        while (!ready.get() && System.currentTimeMillis() < deadline) {
            try { Thread.sleep(10); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
        }
    }

    public TitleService(Plugin plugin, DatabaseService databaseService) {
        this.plugin = plugin;
        this.databaseService = databaseService;
        this.logger = plugin.getLogger();
    }

    /**
     * 初始化数据库表。D-086: 完成后置 ready=true；调用方可在 savePlayerData/getPlayerData 前 awaitReady。
     */
    public void initialize() {
        CompletableFuture.runAsync(() -> {
            try (Connection conn = databaseService.dataSource()
                    .orElseThrow(() -> new SQLException("Database not available"))
                    .getConnection()) {
                // 玩家称号表
                conn.prepareStatement("""
                    CREATE TABLE IF NOT EXISTS starcore_player_titles (
                        player_uuid VARCHAR(36) PRIMARY KEY,
                        unlocked_titles TEXT,
                        unlocked_badges TEXT,
                        equipped_title VARCHAR(64),
                        equipped_badge VARCHAR(64),
                        unlock_times TEXT,
                        updated_at BIGINT
                    )
                """).execute();

                logger.info("Title system database initialized");
                ready.set(true);
            } catch (Exception e) {
                logger.severe("Failed to initialize title database: " + e.getMessage());
                ready.set(true); // 即使失败也放行避免无谓阻塞；后续调用各自降级
            }
        });
    }

    /**
     * 注册称号
     */
    public void registerTitle(Title title) {
        titles.put(title.id(), title);
        logger.fine("Registered title: " + title.id());
    }

    /**
     * 注册徽章
     */
    public void registerBadge(Badge badge) {
        badges.put(badge.id(), badge);
        logger.fine("Registered badge: " + badge.id());
    }

    /**
     * 获取称号
     */
    public Optional<Title> getTitle(String id) {
        return Optional.ofNullable(titles.get(id));
    }

    /**
     * 获取徽章
     */
    public Optional<Badge> getBadge(String id) {
        return Optional.ofNullable(badges.get(id));
    }

    /**
     * 获取所有称号
     */
    public Collection<Title> getAllTitles() {
        return Collections.unmodifiableCollection(titles.values());
    }

    /**
     * 获取所有徽章
     */
    public Collection<Badge> getAllBadges() {
        return Collections.unmodifiableCollection(badges.values());
    }

    /**
     * 获取玩家称号数据
     */
    public CompletableFuture<PlayerTitle> getPlayerData(UUID playerId) {
        PlayerTitle cached = playerCache.getIfPresent(playerId);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        return CompletableFuture.supplyAsync(() -> {
            // D-086: 等待建表就绪（最长 500ms），避免表未建即查询导致 SQLException
            if (!ready.get()) awaitReady(500);
            try (Connection conn = databaseService.dataSource()
                    .orElseThrow(() -> new SQLException("Database not available"))
                    .getConnection()) {
                PreparedStatement stmt = conn.prepareStatement(
                    "SELECT * FROM starcore_player_titles WHERE player_uuid = ?"
                );
                stmt.setString(1, playerId.toString());
                ResultSet rs = stmt.executeQuery();

                PlayerTitle data = new PlayerTitle(playerId);
                if (rs.next()) {
                    Set<String> unlockedTitles = parseSet(rs.getString("unlocked_titles"));
                    Set<String> unlockedBadges = parseSet(rs.getString("unlocked_badges"));
                    Map<String, Instant> unlockTimes = parseTimestamps(rs.getString("unlock_times"));
                    String equipped = rs.getString("equipped_title");
                    String equippedBadge = rs.getString("equipped_badge");

                    data.loadFromData(unlockedTitles, unlockedBadges, unlockTimes, equipped, equippedBadge);
                }

                playerCache.put(playerId, data);
                return data;
            } catch (Exception e) {
                logger.severe("Failed to load player title data: " + e.getMessage());
                PlayerTitle data = new PlayerTitle(playerId);
                playerCache.put(playerId, data);
                return data;
            }
        });
    }

    /**
     * 保存玩家称号数据
     */
    public CompletableFuture<Void> savePlayerData(PlayerTitle data) {
        return CompletableFuture.runAsync(() -> {
            // D-086: 等待建表就绪（最长 500ms）
            if (!ready.get()) awaitReady(500);
            try (Connection conn = databaseService.dataSource()
                    .orElseThrow(() -> new SQLException("Database not available"))
                    .getConnection()) {

                String sql;
                String productName = conn.getMetaData().getDatabaseProductName();
                boolean isSQLite = "SQLite".equalsIgnoreCase(productName);

                if (isSQLite) {
                    sql = """
                        INSERT INTO starcore_player_titles
                        (player_uuid, unlocked_titles, unlocked_badges, equipped_title, equipped_badge, unlock_times, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT(player_uuid) DO UPDATE SET
                            unlocked_titles = excluded.unlocked_titles,
                            unlocked_badges = excluded.unlocked_badges,
                            equipped_title = excluded.equipped_title,
                            equipped_badge = excluded.equipped_badge,
                            unlock_times = excluded.unlock_times,
                            updated_at = excluded.updated_at
                    """;
                } else {
                    sql = """
                        INSERT INTO starcore_player_titles
                        (player_uuid, unlocked_titles, unlocked_badges, equipped_title, equipped_badge, unlock_times, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        ON DUPLICATE KEY UPDATE
                            unlocked_titles = VALUES(unlocked_titles),
                            unlocked_badges = VALUES(unlocked_badges),
                            equipped_title = VALUES(equipped_title),
                            equipped_badge = VALUES(equipped_badge),
                            unlock_times = VALUES(unlock_times),
                            updated_at = VALUES(updated_at)
                    """;
                }

                PreparedStatement stmt = conn.prepareStatement(sql);

                stmt.setString(1, data.getPlayerId().toString());
                stmt.setString(2, serializeSet(data.getUnlockedTitles()));
                stmt.setString(3, serializeSet(data.getUnlockedBadges()));
                stmt.setString(4, data.getEquippedTitle().orElse(null));
                stmt.setString(5, data.getEquippedBadge().orElse(null));
                stmt.setString(6, serializeTimestamps(data.getUnlockTimes()));
                stmt.setLong(7, System.currentTimeMillis());

                stmt.executeUpdate();
            } catch (Exception e) {
                logger.severe("Failed to save player title data: " + e.getMessage());
            }
        });
    }

    /**
     * 解锁称号
     */
    public CompletableFuture<Boolean> unlockTitle(UUID playerId, String titleId) {
        return getPlayerData(playerId).thenApply(data -> {
            if (!titles.containsKey(titleId)) {
                return false;
            }

            if (data.unlockTitle(titleId)) {
                savePlayerData(data);
                // D-087: 切回主线程 sendMessage，避免异步线程直接调用违反 Paper 线程规则
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player player = Bukkit.getPlayer(playerId);
                    if (player != null) {
                        Title title = titles.get(titleId);
                        if (title != null) {
                            player.sendMessage(Component.text("§a✓ 解锁新称号: ").append(title.getFormattedName()));
                        }
                    }
                });
                return true;
            }
            return false;
        });
    }

    /**
     * 解锁徽章
     */
    public CompletableFuture<Boolean> unlockBadge(UUID playerId, String badgeId) {
        return getPlayerData(playerId).thenApply(data -> {
            if (!badges.containsKey(badgeId)) {
                return false;
            }

            if (data.unlockBadge(badgeId)) {
                savePlayerData(data);

                // 通知玩家
                Player player = Bukkit.getPlayer(playerId);
                if (player != null) {
                    Badge badge = badges.get(badgeId);
                    player.sendMessage(Component.text("§a✓ 解锁新徽章: ").append(badge.getColoredName()));
                }

                return true;
            }
            return false;
        });
    }

    /**
     * 装备称号
     */
    public CompletableFuture<Boolean> equipTitle(UUID playerId, String titleId) {
        return getPlayerData(playerId).thenApply(data -> {
            if (titleId != null && !data.hasTitleUnlocked(titleId)) {
                return false;
            }

            data.equipTitle(titleId);
            savePlayerData(data);

            // 刷新显示
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                if (titleId == null) {
                    player.sendMessage(Component.text("§7已卸下称号"));
                } else {
                    Title title = titles.get(titleId);
                    player.sendMessage(Component.text("§a已装备称号: ").append(title.getFormattedName()));
                }
            }

            return true;
        });
    }

    /**
     * 装备徽章
     */
    public CompletableFuture<Boolean> equipBadge(UUID playerId, String badgeId) {
        return getPlayerData(playerId).thenApply(data -> {
            if (badgeId != null && !data.hasBadgeUnlocked(badgeId)) {
                return false;
            }

            data.equipBadge(badgeId);
            savePlayerData(data);

            // 刷新显示
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                if (badgeId == null) {
                    player.sendMessage(Component.text("§7已卸下徽章"));
                } else {
                    Badge badge = badges.get(badgeId);
                    player.sendMessage(Component.text("§a已装备徽章: ").append(badge.getColoredName()));
                }
            }

            return true;
        });
    }

    /**
     * 清理缓存
     */
    public void invalidateCache(UUID playerId) {
        playerCache.invalidate(playerId);
    }

    /**
     * 清理所有缓存
     */
    public void invalidateAllCache() {
        playerCache.invalidateAll();
    }

    // 辅助方法

    private Set<String> parseSet(String data) {
        if (data == null || data.isEmpty()) {
            return new HashSet<>();
        }
        return new HashSet<>(Arrays.asList(data.split(",")));
    }

    private String serializeSet(Set<String> set) {
        return String.join(",", set);
    }

    private Map<String, Instant> parseTimestamps(String data) {
        if (data == null || data.isEmpty() || data.equals("{}")) {
            return new HashMap<>();
        }

        Map<String, Instant> result = new HashMap<>();

        // 解析 JSON 格式: {"titleId":timestamp, ...}
        if (data.startsWith("{") && data.endsWith("}")) {
            String json = data.substring(1, data.length() - 1);
            if (json.isEmpty()) {
                return result;
            }

            // 按逗号分割，但要处理引号内的逗号
            List<String> entries = splitJsonEntries(json);
            for (String entry : entries) {
                int colonIndex = entry.indexOf(':');
                if (colonIndex > 0) {
                    String key = entry.substring(0, colonIndex).trim();
                    String value = entry.substring(colonIndex + 1).trim();

                    // 去除引号
                    if (key.startsWith("\"") && key.endsWith("\"")) {
                        key = key.substring(1, key.length() - 1);
                    }

                    // 解析时间戳
                    try {
                        long timestamp = Long.parseLong(value);
                        result.put(key, Instant.ofEpochMilli(timestamp));
                    } catch (NumberFormatException ignored) {
                        // 忽略无效时间戳
                    }
                }
            }
        }

        return result;
    }

    /**
     * 序列化时间戳Map为JSON格式
     */
    private String serializeTimestamps(Map<String, Instant> timestamps) {
        if (timestamps == null || timestamps.isEmpty()) {
            return "{}";
        }

        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Instant> entry : timestamps.entrySet()) {
            if (!first) {
                sb.append(",");
            }
            first = false;
            sb.append("\"").append(escapeJson(entry.getKey())).append("\":")
              .append(entry.getValue().toEpochMilli());
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * 安全分割JSON数组条目
     */
    private List<String> splitJsonEntries(String json) {
        List<String> entries = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (char c : json.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
                current.append(c);
            } else if (c == ',' && !inQuotes) {
                entries.add(current.toString().trim());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }

        if (current.length() > 0) {
            entries.add(current.toString().trim());
        }

        return entries;
    }

    /**
     * 转义JSON字符串中的特殊字符
     */
    private String escapeJson(String str) {
        if (str == null) {
            return "";
        }
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
}
