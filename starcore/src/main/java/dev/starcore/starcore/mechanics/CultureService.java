package dev.starcore.starcore.mechanics;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 文化服务
 * 管理文化系统
 */
public class CultureService {

    private final JavaPlugin plugin;
    private final Map<UUID, Culture> cultureCache;
    private Connection connection;

    private boolean enabled = true;

    public CultureService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.cultureCache = new ConcurrentHashMap<>();  // Bug修复 #5: 线程安全
    }

    /**
     * 初始化服务
     */
    public void initialize() {
        initializeDatabase();

        // 定时保存数据
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::saveAllCulture, 6000L, 6000L);

        plugin.getLogger().info("文化系统已启用");
    }

    /**
     * 初始化数据库
     */
    private void initializeDatabase() {
        try {
            String dbPath = plugin.getDataFolder().getAbsolutePath() + "/culture.db";
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);

            try (Statement stmt = connection.createStatement()) {
                stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS culture (" +
                    "owner_id TEXT PRIMARY KEY," +
                    "owner_name TEXT NOT NULL," +
                    "culture_points INTEGER DEFAULT 0," +
                    "literature INTEGER DEFAULT 0," +
                    "art INTEGER DEFAULT 0," +
                    "music INTEGER DEFAULT 0," +
                    "architecture INTEGER DEFAULT 0," +
                    "philosophy INTEGER DEFAULT 0," +
                    "last_update BIGINT DEFAULT 0" +
                    ")"
                );
            }

            plugin.getLogger().info("文化数据库初始化完成");
        } catch (Exception e) {
            plugin.getLogger().severe("文化数据库初始化失败: " + e.getMessage());
        }
    }

    /**
     * 加载文化数据
     */
    public void loadCulture(UUID ownerId) {
        CompletableFuture.runAsync(() -> {
            try {
                String sql = "SELECT * FROM culture WHERE owner_id = ?";
                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    stmt.setString(1, ownerId.toString());
                    ResultSet rs = stmt.executeQuery();

                    Culture culture;
                    if (rs.next()) {
                        culture = new Culture(ownerId, rs.getString("owner_name"));
                        culture.setCulturePoints(rs.getInt("culture_points"));
                        culture.setLiterature(rs.getInt("literature"));
                        culture.setArt(rs.getInt("art"));
                        culture.setMusic(rs.getInt("music"));
                        culture.setArchitecture(rs.getInt("architecture"));
                        culture.setPhilosophy(rs.getInt("philosophy"));
                    } else {
                        culture = new Culture(ownerId, "Unknown");
                    }

                    cultureCache.put(ownerId, culture);
                }
            } catch (Exception e) {
                plugin.getLogger().severe("加载文化数据失败: " + e.getMessage());
            }
        });
    }

    /**
     * 保存文化数据
     */
    private void saveCulture(UUID ownerId) {
        Culture culture = cultureCache.get(ownerId);
        if (culture == null) return;

        CompletableFuture.runAsync(() -> {
            try {
                String sql = "INSERT OR REPLACE INTO culture " +
                    "(owner_id, owner_name, culture_points, literature, art, music, " +
                    "architecture, philosophy, last_update) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    stmt.setString(1, ownerId.toString());
                    stmt.setString(2, culture.getOwnerName());
                    stmt.setInt(3, culture.getCulturePoints());
                    stmt.setInt(4, culture.getLiterature());
                    stmt.setInt(5, culture.getArt());
                    stmt.setInt(6, culture.getMusic());
                    stmt.setInt(7, culture.getArchitecture());
                    stmt.setInt(8, culture.getPhilosophy());
                    stmt.setLong(9, culture.getLastUpdate());

                    stmt.executeUpdate();
                }
            } catch (Exception e) {
                plugin.getLogger().severe("保存文化数据失败: " + e.getMessage());
            }
        });
    }

    /**
     * 保存所有文化数据
     */
    private void saveAllCulture() {
        for (UUID ownerId : cultureCache.keySet()) {
            saveCulture(ownerId);
        }
    }

    /**
     * 获取或创建文化数据
     */
    public Culture getCulture(UUID ownerId, String ownerName) {
        return cultureCache.computeIfAbsent(ownerId, id -> {
            Culture culture = new Culture(id, ownerName);
            loadCulture(id);
            return culture;
        });
    }

    /**
     * 增加文化值
     */
    public void addCulture(UUID ownerId, String ownerName, String type, int amount) {
        if (!enabled) return;

        Culture culture = getCulture(ownerId, ownerName);

        switch (type.toLowerCase()) {
            case "literature":
            case "文学":
                culture.addLiterature(amount);
                break;
            case "art":
            case "艺术":
                culture.addArt(amount);
                break;
            case "music":
            case "音乐":
                culture.addMusic(amount);
                break;
            case "architecture":
            case "建筑":
                culture.addArchitecture(amount);
                break;
            case "philosophy":
            case "哲学":
                culture.addPhilosophy(amount);
                break;
            default:
                culture.addCulturePoints(amount);
        }
    }

    /**
     * 关闭服务
     */
    public void shutdown() {
        saveAllCulture();

        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (Exception e) {
            plugin.getLogger().severe("关闭文化数据库失败: " + e.getMessage());
        }

        cultureCache.clear();
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
