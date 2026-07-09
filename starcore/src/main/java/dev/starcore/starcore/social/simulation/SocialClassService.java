package dev.starcore.starcore.social.simulation;

import dev.starcore.starcore.module.nation.model.NationId;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 社会阶层服务
 *
 * 管理玩家在社会中的阶层地位:
 * - 阶层分类 (平民/贵族/王室等)
 * - 阶层流动 (晋升/降级)
 * - 阶层特权
 * - 阶层战争
 */
public class SocialClassService {

    private final JavaPlugin plugin;
    private final Map<UUID, SocialClass> playerClasses = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> classPoints = new ConcurrentHashMap<>();
    private final Map<UUID, List<ClassHistory>> classHistory = new ConcurrentHashMap<>();
    private File dataFile;
    private FileConfiguration dataConfig;

    public SocialClassService(JavaPlugin plugin) {
        this.plugin = plugin;
        loadData();
    }

    /**
     * 获取玩家社会阶层
     */
    public SocialClass getClass(UUID playerId) {
        return playerClasses.getOrDefault(playerId, SocialClass.PEASANT);
    }

    /**
     * 获取玩家阶层点数
     */
    public int getClassPoints(UUID playerId) {
        return classPoints.getOrDefault(playerId, 0);
    }

    /**
     * 添加阶层点数
     */
    public void addClassPoints(UUID playerId, int points, String reason) {
        int current = classPoints.getOrDefault(playerId, 0);
        int newPoints = current + points;
        classPoints.put(playerId, newPoints);

        // 记录历史
        classHistory.computeIfAbsent(playerId, k -> new ArrayList<>())
            .add(new ClassHistory(System.currentTimeMillis(), points, reason));

        // 检查是否需要晋升
        checkPromotion(playerId);
    }

    // ==================== YAML 持久化方法 ====================

    /**
     * 保存数据到 YAML 文件
     */
    public void saveData() {
        if (plugin == null) return;

        File dataDir = new File(plugin.getDataFolder(), "social-simulation");
        if (!dataDir.exists()) dataDir.mkdirs();
        dataFile = new File(dataDir, "social_classes.yml");
        dataConfig = new YamlConfiguration();

        try {
            // 保存玩家阶层
            for (Map.Entry<UUID, SocialClass> entry : playerClasses.entrySet()) {
                String path = "players." + entry.getKey().toString();
                dataConfig.set(path + ".class", entry.getValue().name());
                dataConfig.set(path + ".points", classPoints.getOrDefault(entry.getKey(), 0));
            }

            // 保存阶层历史
            for (Map.Entry<UUID, List<ClassHistory>> entry : classHistory.entrySet()) {
                String path = "history." + entry.getKey().toString();
                List<Map<String, Object>> historyList = new ArrayList<>();
                for (ClassHistory history : entry.getValue()) {
                    Map<String, Object> historyMap = new HashMap<>();
                    historyMap.put("timestamp", history.timestamp());
                    historyMap.put("points", history.points());
                    historyMap.put("reason", history.reason());
                    historyList.add(historyMap);
                }
                dataConfig.set(path, historyList);
            }

            dataConfig.save(dataFile);
            plugin.getLogger().info("社会阶层数据已保存: " + playerClasses.size() + " 名玩家");
        } catch (IOException e) {
            plugin.getLogger().warning("保存社会阶层数据失败: " + e.getMessage());
        }
    }

    /**
     * 从 YAML 文件加载数据
     */
    public void loadData() {
        if (plugin == null) return;

        File dataDir = new File(plugin.getDataFolder(), "social-simulation");
        if (!dataDir.exists()) {
            plugin.getLogger().info("social-simulation 目录不存在,使用默认配置");
            return;
        }
        dataFile = new File(dataDir, "social_classes.yml");
        if (!dataFile.exists()) {
            plugin.getLogger().info("社会阶层数据文件不存在,使用默认配置");
            return;
        }

        dataConfig = YamlConfiguration.loadConfiguration(dataFile);

        try {
            // 加载玩家阶层
            if (dataConfig.contains("players")) {
                Map<String, Object> players = dataConfig.getConfigurationSection("players").getValues(false);
                for (Map.Entry<String, Object> entry : players.entrySet()) {
                    UUID playerId = UUID.fromString(entry.getKey());
                    String className = dataConfig.getString("players." + entry.getKey() + ".class");
                    int points = dataConfig.getInt("players." + entry.getKey() + ".points", 0);

                    try {
                        SocialClass socialClass = SocialClass.valueOf(className);
                        playerClasses.put(playerId, socialClass);
                        classPoints.put(playerId, points);
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("无效的社会阶层: " + className + " for " + playerId);
                    }
                }
            }

            // 加载阶层历史
            if (dataConfig.contains("history")) {
                Map<String, Object> history = dataConfig.getConfigurationSection("history").getValues(false);
                for (Map.Entry<String, Object> entry : history.entrySet()) {
                    UUID playerId = UUID.fromString(entry.getKey());
                    List<Map<?, ?>> historyList = dataConfig.getMapList("history." + entry.getKey());
                    List<ClassHistory> histories = new ArrayList<>();

                    for (Map<?, ?> h : historyList) {
                        long timestamp = ((Number) h.get("timestamp")).longValue();
                        int points = ((Number) h.get("points")).intValue();
                        String reason = (String) h.get("reason");
                        histories.add(new ClassHistory(timestamp, points, reason));
                    }
                    classHistory.put(playerId, histories);
                }
            }

            plugin.getLogger().info("社会阶层数据已加载: " + playerClasses.size() + " 名玩家");
        } catch (Exception e) {
            plugin.getLogger().warning("加载社会阶层数据失败: " + e.getMessage());
        }
    }

    /**
     * 清除所有数据
     */
    public void clearData() {
        playerClasses.clear();
        classPoints.clear();
        classHistory.clear();
    }

    /**
     * 获取已保存的玩家数量
     */
    public int getSavedPlayerCount() {
        return playerClasses.size();
    }

    /**
     * 检查晋升
     */
    private void checkPromotion(UUID playerId) {
        int points = classPoints.get(playerId);
        SocialClass current = playerClasses.get(playerId);

        for (SocialClass higherClass : SocialClass.values()) {
            if (higherClass.ordinal() > current.ordinal() && points >= higherClass.requiredPoints()) {
                promotePlayer(playerId, higherClass);
            }
        }
    }

    /**
     * 晋升玩家
     */
    public void promotePlayer(UUID playerId, SocialClass newClass) {
        SocialClass oldClass = playerClasses.get(playerId);
        playerClasses.put(playerId, newClass);

        // 广播晋升消息
        String playerName = org.bukkit.Bukkit.getPlayer(playerId) != null ? org.bukkit.Bukkit.getPlayer(playerId).getName() : "玩家";
        for (org.bukkit.entity.Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
            p.sendMessage("§6§l【阶层晋升】§e" + playerName + " §6从 " + oldClass.displayName() + " §6晋升为 " + newClass.displayName());
        }
    }

    /**
     * 降级玩家
     */
    public void demotePlayer(UUID playerId, SocialClass newClass) {
        SocialClass oldClass = playerClasses.get(playerId);
        playerClasses.put(playerId, newClass);

        String playerName = org.bukkit.Bukkit.getPlayer(playerId) != null ? org.bukkit.Bukkit.getPlayer(playerId).getName() : "玩家";
        for (org.bukkit.entity.Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
            p.sendMessage("§4§l【阶层变动】§c" + playerName + " §c从 " + oldClass.displayName() + " §c降为 " + newClass.displayName());
        }
    }

    /**
     * 获取玩家阶层特权
     */
    public Set<String> getPrivileges(UUID playerId) {
        SocialClass playerClass = getClass(playerId);
        return new HashSet<>(playerClass.privileges());
    }

    /**
     * 检查是否有特定特权
     */
    public boolean hasPrivilege(UUID playerId, String privilege) {
        return getPrivileges(playerId).contains(privilege);
    }

    /**
     * 获取同阶层的玩家
     */
    public List<UUID> getPlayersInClass(SocialClass targetClass) {
        List<UUID> result = new ArrayList<>();
        for (Map.Entry<UUID, SocialClass> entry : playerClasses.entrySet()) {
            if (entry.getValue() == targetClass) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    /**
     * 获取阶层历史
     */
    public List<ClassHistory> getHistory(UUID playerId) {
        return classHistory.getOrDefault(playerId, List.of());
    }

    // ==================== 数据类 ====================

    public enum SocialClass {
        PEASANT(0, "§7平民", "社会底层", Set.of()),
        WORKER(100, "§f工人", "勤劳的普通人", Set.of("basic_trade")),
        MERCHANT(500, "§6商人", "经济精英", Set.of("basic_trade", "market_access")),
        NOBLE(2000, "§b贵族", "社会名流", Set.of("basic_trade", "market_access", "title_noble")),
        ROYALTY(5000, "§c皇室", "权力顶端", Set.of("all_trade", "all_access", "title_royalty", "nation_founder")),
        DIVINITY(10000, "§5神级", "传说存在", Set.of("all_trade", "all_access", "all_titles", "nation_founder", "event_creator"));

        private final int requiredPoints;
        private final String color;
        private final String displayName;
        private final Set<String> privileges;

        SocialClass(int points, String color, String name, Set<String> privileges) {
            this.requiredPoints = points;
            this.color = color;
            this.displayName = color + name;
            this.privileges = privileges;
        }

        public int requiredPoints() { return requiredPoints; }
        public String color() { return color; }
        public String displayName() { return displayName; }
        public Set<String> privileges() { return privileges; }
    }

    public record ClassHistory(
        long timestamp,
        int points,
        String reason
    ) {}
}
