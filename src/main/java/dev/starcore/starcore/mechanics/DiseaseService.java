package dev.starcore.starcore.mechanics;

import java.util.concurrent.ThreadLocalRandom;
import dev.starcore.starcore.util.ColorCodes;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 疾病服务
 * 管理疾病感染、传播和治疗系统
 */
public class DiseaseService implements Listener {

    private final JavaPlugin plugin;
    private final Map<UUID, List<Disease>> playerDiseases;
    private Connection connection;

    // 配置项
    private boolean enabled = true;
    private double infectionChance = 0.01; // 基础感染概率
    private boolean allowSpread = true; // 允许疾病传播

    public DiseaseService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.playerDiseases = new ConcurrentHashMap<>();
    }

    /**
     * 初始化服务
     */
    public void initialize() {
        initializeDatabase();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        // 加载所有在线玩家的疾病
        for (Player player : Bukkit.getOnlinePlayers()) {
            loadDiseases(player.getUniqueId());
        }

        // 定时应用疾病效果
        Bukkit.getScheduler().runTaskTimer(plugin, this::applyDiseaseEffects, 200L, 200L);

        // 定时疾病传播
        if (allowSpread) {
            Bukkit.getScheduler().runTaskTimer(plugin, this::spreadDiseases, 600L, 600L);
        }

        // 定时自然恢复检查
        Bukkit.getScheduler().runTaskTimer(plugin, this::checkNaturalRecovery, 12000L, 12000L);

        // 定时保存数据
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::saveAllDiseases, 6000L, 6000L);

        plugin.getLogger().info("疾病系统已启用");
    }

    /**
     * 初始化数据库
     */
    private void initializeDatabase() {
        try {
            String dbPath = plugin.getDataFolder().getAbsolutePath() + "/diseases.db";
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);

            try (Statement stmt = connection.createStatement()) {
                stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS player_diseases (" +
                    "id TEXT PRIMARY KEY," +
                    "player_id TEXT NOT NULL," +
                    "disease_type TEXT NOT NULL," +
                    "infection_time BIGINT NOT NULL," +
                    "severity INTEGER DEFAULT 0," +
                    "treated INTEGER DEFAULT 0" +
                    ")"
                );
            }

            plugin.getLogger().info("疾病数据库初始化完成");
        } catch (Exception e) {
            plugin.getLogger().severe("疾病数据库初始化失败: " + e.getMessage());
        }
    }

    /**
     * 玩家加入时加载疾病
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        loadDiseases(event.getPlayer().getUniqueId());

        // 检查玩家疾病状态
        List<Disease> diseases = getDiseases(event.getPlayer().getUniqueId());
        if (!diseases.isEmpty()) {
            event.getPlayer().sendMessage("§c§l警告：你患有以下疾病！");
            for (Disease disease : diseases) {
                event.getPlayer().sendMessage(disease.getDescription());
            }
        }
    }

    /**
     * 玩家退出时保存疾病
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        saveDiseases(playerId);
        playerDiseases.remove(playerId);
    }

    /**
     * 玩家受伤时有概率感染
     */
    @EventHandler
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!enabled) return;
        if (!(event.getEntity() instanceof Player)) return;

        Player player = (Player) event.getEntity();
        EntityDamageEvent.DamageCause cause = event.getCause();

        // 根据伤害类型判断感染概率
        DiseaseType diseaseType = null;
        double chance = infectionChance;

        switch (cause) {
            case POISON:
                diseaseType = DiseaseType.POISON;
                chance = 0.3;
                break;
            case WITHER:
                diseaseType = DiseaseType.INFECTION;
                chance = 0.2;
                break;
            case MAGIC:
                diseaseType = DiseaseType.CURSE;
                chance = 0.1;
                break;
            case ENTITY_ATTACK:
                // 普通攻击小概率感染
                if (ThreadLocalRandom.current().nextDouble() < 0.05) {
                    diseaseType = DiseaseType.INFECTION;
                    chance = 0.1;
                }
                break;
        }

        // 判断是否感染
        if (diseaseType != null && ThreadLocalRandom.current().nextDouble() < chance) {
            infectPlayer(player.getUniqueId(), diseaseType);
        }
    }

    /**
     * 应用疾病效果
     */
    private void applyDiseaseEffects() {
        if (!enabled) return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            List<Disease> diseases = getDiseases(player.getUniqueId());

            for (Disease disease : diseases) {
                disease.applyEffects(player);
            }
        }
    }

    /**
     * 疾病传播
     */
    private void spreadDiseases() {
        if (!enabled || !allowSpread) return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            List<Disease> diseases = getDiseases(player.getUniqueId());

            for (Disease disease : diseases) {
                // 检查附近的玩家
                for (Player nearby : player.getWorld().getPlayers()) {
                    if (nearby.equals(player)) continue;
                    if (nearby.getLocation().distance(player.getLocation()) > 5) continue;

                    // 根据传染性判断是否传播
                    if (ThreadLocalRandom.current().nextDouble() < disease.getType().getContagion() * 0.01) {
                        infectPlayer(nearby.getUniqueId(), disease.getType());
                    }
                }
            }
        }
    }

    /**
     * 自然恢复检查
     */
    private void checkNaturalRecovery() {
        if (!enabled) return;

        for (UUID playerId : playerDiseases.keySet()) {
            List<Disease> diseases = getDiseases(playerId);
            diseases.removeIf(disease -> {
                // 检查是否过期
                if (disease.isExpired()) {
                    Player player = Bukkit.getPlayer(playerId);
                    if (player != null) {
                        disease.removeEffects(player);
                        player.sendMessage("§a你的 " + disease.getType().getDisplayName() + " 已自然痊愈");
                    }
                    return true;
                }

                // 轻微疾病自然恢复
                if (disease.getType().getSeverity() <= 2 && ThreadLocalRandom.current().nextDouble() < 0.1) {
                    disease.improve(10);
                    if (disease.isCured()) {
                        Player player = Bukkit.getPlayer(playerId);
                        if (player != null) {
                            disease.removeEffects(player);
                            player.sendMessage("§a你的 " + disease.getType().getDisplayName() + " 已痊愈");
                        }
                        return true;
                    }
                }

                return false;
            });
        }
    }

    /**
     * 感染玩家
     */
    public void infectPlayer(UUID playerId, DiseaseType diseaseType) {
        List<Disease> diseases = getDiseases(playerId);

        // 检查是否已感染相同疾病
        for (Disease disease : diseases) {
            if (disease.getType() == diseaseType) {
                disease.worsen(10); // 加重病情
                return;
            }
        }

        // 创建新疾病
        Disease disease = new Disease(playerId, diseaseType);
        diseases.add(disease);

        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            player.sendMessage("§c§l你感染了 " + diseaseType.getDisplayName() + "！");
            player.sendMessage("§7症状: §f" + diseaseType.getSymptoms());
            player.sendMessage("§7建议尽快接受治疗");
        }
    }

    /**
     * 治愈玩家疾病
     */
    public void cureDisease(UUID playerId, Disease disease) {
        List<Disease> diseases = getDiseases(playerId);
        diseases.remove(disease);

        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            disease.removeEffects(player);
            player.sendMessage("§a§l你已痊愈！");
        }
    }

    /**
     * 获取玩家疾病列表
     */
    public List<Disease> getDiseases(UUID playerId) {
        return playerDiseases.computeIfAbsent(playerId, k -> new ArrayList<>());
    }

    /**
     * 加载玩家疾病
     */
    private void loadDiseases(UUID playerId) {
        CompletableFuture.runAsync(() -> {
            try {
                String sql = "SELECT * FROM player_diseases WHERE player_id = ?";
                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    stmt.setString(1, playerId.toString());
                    ResultSet rs = stmt.executeQuery();

                    List<Disease> diseases = new ArrayList<>();

                    while (rs.next()) {
                        DiseaseType type = DiseaseType.valueOf(rs.getString("disease_type"));
                        Disease disease = new Disease(playerId, type);
                        disease.setSeverity(rs.getInt("severity"));
                        disease.setTreated(rs.getInt("treated") == 1);

                        diseases.add(disease);
                    }

                    playerDiseases.put(playerId, diseases);
                }
            } catch (Exception e) {
                plugin.getLogger().severe("加载疾病数据失败: " + e.getMessage());
            }
        });
    }

    /**
     * 保存玩家疾病
     */
    private void saveDiseases(UUID playerId) {
        List<Disease> diseases = playerDiseases.get(playerId);
        if (diseases == null) return;

        CompletableFuture.runAsync(() -> {
            try {
                // 先删除旧数据
                String deleteSql = "DELETE FROM player_diseases WHERE player_id = ?";
                try (PreparedStatement stmt = connection.prepareStatement(deleteSql)) {
                    stmt.setString(1, playerId.toString());
                    stmt.executeUpdate();
                }

                // 插入新数据
                String insertSql = "INSERT INTO player_diseases " +
                    "(id, player_id, disease_type, infection_time, severity, treated) " +
                    "VALUES (?, ?, ?, ?, ?, ?)";

                try (PreparedStatement stmt = connection.prepareStatement(insertSql)) {
                    for (Disease disease : diseases) {
                        stmt.setString(1, disease.getId().toString());
                        stmt.setString(2, playerId.toString());
                        stmt.setString(3, disease.getType().name());
                        stmt.setLong(4, disease.getInfectionTime());
                        stmt.setInt(5, disease.getSeverity());
                        stmt.setInt(6, disease.isTreated() ? 1 : 0);
                        stmt.addBatch();
                    }
                    stmt.executeBatch();
                }
            } catch (Exception e) {
                plugin.getLogger().severe("保存疾病数据失败: " + e.getMessage());
            }
        });
    }

    /**
     * 保存所有玩家疾病
     */
    private void saveAllDiseases() {
        for (UUID playerId : playerDiseases.keySet()) {
            saveDiseases(playerId);
        }
    }

    /**
     * 关闭服务
     */
    public void shutdown() {
        // 保存所有数据
        saveAllDiseases();

        // 移除所有玩家的疾病效果
        for (Player player : Bukkit.getOnlinePlayers()) {
            List<Disease> diseases = getDiseases(player.getUniqueId());
            for (Disease disease : diseases) {
                disease.removeEffects(player);
            }
        }

        // 关闭数据库连接
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (Exception e) {
            plugin.getLogger().severe("关闭疾病数据库失败: " + e.getMessage());
        }

        playerDiseases.clear();
    }

    // Getters and Setters

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setInfectionChance(double chance) {
        this.infectionChance = Math.max(0, Math.min(1, chance));
    }

    public void setAllowSpread(boolean allowSpread) {
        this.allowSpread = allowSpread;
    }
}
