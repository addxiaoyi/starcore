package dev.starcore.starcore.mechanics;

import dev.starcore.starcore.util.ColorCodes;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * 声望服务
 * 管理玩家声望系统
 */
public class ReputationService implements Listener {

    private final JavaPlugin plugin;
    private final Map<UUID, Reputation> reputationCache;
    private Connection connection;

    private boolean enabled = true;

    public ReputationService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.reputationCache = new ConcurrentHashMap<>();  // Bug修复 #5: 线程安全
    }

    /**
     * 初始化服务
     */
    public void initialize() {
        initializeDatabase();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        // 加载所有在线玩家的声望
        for (Player player : Bukkit.getOnlinePlayers()) {
            loadReputation(player.getUniqueId());
        }

        // 定时保存数据
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::saveAllReputation, 6000L, 6000L);

        plugin.getLogger().info("声望系统已启用");
    }

    /**
     * 初始化数据库
     */
    private void initializeDatabase() {
        try {
            // 使用SQLite作为示例
            String dbPath = plugin.getDataFolder().getAbsolutePath() + "/reputation.db";
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);

            // 创建表
            try (Statement stmt = connection.createStatement()) {
                stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS reputation (" +
                    "player_id TEXT PRIMARY KEY," +
                    "total_reputation INTEGER DEFAULT 0," +
                    "war_reputation INTEGER DEFAULT 0," +
                    "quest_reputation INTEGER DEFAULT 0," +
                    "trade_reputation INTEGER DEFAULT 0," +
                    "construction_reputation INTEGER DEFAULT 0," +
                    "diplomacy_reputation INTEGER DEFAULT 0," +
                    "achievement_reputation INTEGER DEFAULT 0," +
                    "event_reputation INTEGER DEFAULT 0," +
                    "pvp_reputation INTEGER DEFAULT 0," +
                    "boss_kill_reputation INTEGER DEFAULT 0," +
                    "exploration_reputation INTEGER DEFAULT 0," +
                    "last_update BIGINT DEFAULT 0" +
                    ")"
                );
            }

            plugin.getLogger().info("声望数据库初始化完成");
        } catch (Exception e) {
            plugin.getLogger().severe("声望数据库初始化失败: " + e.getMessage());
            plugin.getLogger().log(Level.SEVERE, "Database initialization failed", e);
        }
    }

    /**
     * 玩家加入时加载声望
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        loadReputation(event.getPlayer().getUniqueId());
    }

    /**
     * 玩家退出时保存声望
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        saveReputation(playerId);
        reputationCache.remove(playerId);
    }

    /**
     * 加载玩家声望（异步）
     */
    private void loadReputation(UUID playerId) {
        // 捕获 playerId 以在异步任务中使用
        String playerIdStr = playerId.toString();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String sql = "SELECT * FROM reputation WHERE player_id = ?";
                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    stmt.setString(1, playerIdStr);
                    ResultSet rs = stmt.executeQuery();

                    Reputation reputation = new Reputation(playerId);

                    if (rs.next()) {
                        // 加载总声望
                        reputation.setTotalReputation(rs.getInt("total_reputation"));

                        // 加载各来源声望
                        reputation.setReputationFromSource(ReputationSource.WAR,
                            rs.getInt("war_reputation"));
                        reputation.setReputationFromSource(ReputationSource.QUEST,
                            rs.getInt("quest_reputation"));
                        reputation.setReputationFromSource(ReputationSource.TRADE,
                            rs.getInt("trade_reputation"));
                        reputation.setReputationFromSource(ReputationSource.CONSTRUCTION,
                            rs.getInt("construction_reputation"));
                        reputation.setReputationFromSource(ReputationSource.DIPLOMACY,
                            rs.getInt("diplomacy_reputation"));
                        reputation.setReputationFromSource(ReputationSource.ACHIEVEMENT,
                            rs.getInt("achievement_reputation"));
                        reputation.setReputationFromSource(ReputationSource.EVENT,
                            rs.getInt("event_reputation"));
                        reputation.setReputationFromSource(ReputationSource.PVP,
                            rs.getInt("pvp_reputation"));
                        reputation.setReputationFromSource(ReputationSource.BOSS_KILL,
                            rs.getInt("boss_kill_reputation"));
                        reputation.setReputationFromSource(ReputationSource.EXPLORATION,
                            rs.getInt("exploration_reputation"));
                    }

                    // 在主线程更新缓存
                    final Reputation finalReputation = reputation;
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        reputationCache.put(playerId, finalReputation);
                    });
                }
            } catch (Exception e) {
                plugin.getLogger().severe("加载声望失败: " + e.getMessage());
            }
        });
    }

    /**
     * 保存玩家声望（异步）
     */
    private void saveReputation(UUID playerId) {
        Reputation reputation = reputationCache.get(playerId);
        if (reputation == null) return;

        // 捕获需要在线程中使用的变量
        int repId = reputation.getTotalReputation();
        Map<ReputationSource, Integer> sourceMap = new HashMap<>();
        for (ReputationSource source : ReputationSource.values()) {
            sourceMap.put(source, reputation.getReputationFromSource(source));
        }
        long lastUpdate = reputation.getLastUpdate();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String sql = "INSERT OR REPLACE INTO reputation " +
                    "(player_id, total_reputation, war_reputation, quest_reputation, " +
                    "trade_reputation, construction_reputation, diplomacy_reputation, " +
                    "achievement_reputation, event_reputation, pvp_reputation, " +
                    "boss_kill_reputation, exploration_reputation, last_update) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    stmt.setString(1, playerId.toString());
                    stmt.setInt(2, repId);
                    stmt.setInt(3, sourceMap.getOrDefault(ReputationSource.WAR, 0));
                    stmt.setInt(4, sourceMap.getOrDefault(ReputationSource.QUEST, 0));
                    stmt.setInt(5, sourceMap.getOrDefault(ReputationSource.TRADE, 0));
                    stmt.setInt(6, sourceMap.getOrDefault(ReputationSource.CONSTRUCTION, 0));
                    stmt.setInt(7, sourceMap.getOrDefault(ReputationSource.DIPLOMACY, 0));
                    stmt.setInt(8, sourceMap.getOrDefault(ReputationSource.ACHIEVEMENT, 0));
                    stmt.setInt(9, sourceMap.getOrDefault(ReputationSource.EVENT, 0));
                    stmt.setInt(10, sourceMap.getOrDefault(ReputationSource.PVP, 0));
                    stmt.setInt(11, sourceMap.getOrDefault(ReputationSource.BOSS_KILL, 0));
                    stmt.setInt(12, sourceMap.getOrDefault(ReputationSource.EXPLORATION, 0));
                    stmt.setLong(13, lastUpdate);

                    stmt.executeUpdate();
                }
            } catch (Exception e) {
                plugin.getLogger().severe("保存声望失败: " + e.getMessage());
            }
        });
    }

    /**
     * 保存所有玩家声望
     */
    private void saveAllReputation() {
        for (UUID playerId : reputationCache.keySet()) {
            saveReputation(playerId);
        }
    }

    /**
     * 获取玩家声望
     */
    public Reputation getReputation(UUID playerId) {
        return reputationCache.computeIfAbsent(playerId, Reputation::new);
    }

    /**
     * 添加声望
     */
    public void addReputation(UUID playerId, ReputationSource source, int amount) {
        if (!enabled) return;

        Reputation reputation = getReputation(playerId);
        ReputationLevel oldLevel = reputation.getLevel();

        reputation.addReputation(source, amount);

        // 检查是否升级
        ReputationLevel newLevel = reputation.getLevel();
        if (newLevel != oldLevel) {
            notifyLevelUp(playerId, oldLevel, newLevel);
        }

        // 通知玩家
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline()) {
            player.sendMessage("§6[声望] §a+" + source.calculate(amount) +
                " 声望 §7(" + source.getDisplayName() + ")");
        }
    }

    /**
     * 减少声望
     */
    public void removeReputation(UUID playerId, int amount) {
        if (!enabled) return;

        Reputation reputation = getReputation(playerId);
        reputation.removeReputation(amount);

        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline()) {
            player.sendMessage("§6[声望] §c-" + amount + " 声望");
        }
    }

    /**
     * 通知玩家升级
     */
    private void notifyLevelUp(UUID playerId, ReputationLevel oldLevel, ReputationLevel newLevel) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline()) {
            player.sendMessage("§6§l================================");
            player.sendMessage("§e§l         声望等级提升！");
            // 分隔
            player.sendMessage("  " + oldLevel.getColoredName() + " §7→ " + newLevel.getColoredName());
            // 分隔
            player.sendMessage("§6§l================================");

            // 播放升级音效
            player.playSound(player.getLocation(),
                org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        }
    }

    /**
     * 关闭服务
     */
    public void shutdown() {
        // 保存所有数据
        saveAllReputation();

        // 关闭数据库连接
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (Exception e) {
            plugin.getLogger().severe("关闭声望数据库失败: " + e.getMessage());
        }

        reputationCache.clear();
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
