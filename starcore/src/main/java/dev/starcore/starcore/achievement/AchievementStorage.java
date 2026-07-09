package dev.starcore.starcore.achievement;

import dev.starcore.starcore.core.database.DatabaseService;
import dev.starcore.starcore.core.persistence.PersistenceService;
import org.bukkit.NamespacedKey;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * 成就数据持久化存储
 */
public final class AchievementStorage {

    private final String namespace;
    private final DatabaseService databaseService;
    private final PersistenceService persistenceService;
    private final Logger logger;

    // 内存缓存
    private final Map<UUID, AchievementProgress> progressCache = new java.util.concurrent.ConcurrentHashMap<>();

    public AchievementStorage(String namespace, DatabaseService databaseService,
                              PersistenceService persistenceService, Logger logger) {
        this.namespace = namespace;
        this.databaseService = databaseService;
        this.persistenceService = persistenceService;
        this.logger = logger;
        initDatabase();
    }

    /**
     * 初始化数据库表
     */
    private void initDatabase() {
        if (!databaseService.isRunning()) {
            return;
        }

        try (Connection conn = databaseService.getConnection();
             Statement stmt = conn.createStatement()) {

            // 玩家成就完成记录表
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS starcore_achievements (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    player_uuid VARCHAR(36) NOT NULL,
                    achievement_key VARCHAR(128) NOT NULL,
                    namespace VARCHAR(64) NOT NULL,
                    completed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE(player_uuid, achievement_key, namespace)
                )
            """);

            // 玩家成就进度统计表
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS starcore_achievement_stats (
                    player_uuid VARCHAR(36) PRIMARY KEY,
                    trigger_type VARCHAR(64) NOT NULL,
                    count_value INT DEFAULT 0,
                    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE(player_uuid, trigger_type)
                )
            """);

            logger.info("成就数据库表初始化完成");

        } catch (Exception e) {
            logger.warning("初始化成就数据库表失败: " + e.getMessage());
        }
    }

    /**
     * 加载玩家成就进度
     */
    public AchievementProgress loadProgress(UUID playerId) {
        // 先检查缓存
        AchievementProgress cached = progressCache.get(playerId);
        if (cached != null) {
            return cached;
        }

        AchievementProgress progress = new AchievementProgress(playerId);

        if (databaseService.isRunning()) {
            loadFromDatabase(playerId, progress);
        }

        // 从 Properties 文件加载
        loadFromProperties(playerId, progress);

        progressCache.put(playerId, progress);
        return progress;
    }

    /**
     * 从数据库加载
     */
    private void loadFromDatabase(UUID playerId, AchievementProgress progress) {
        if (!databaseService.isRunning()) {
            return;
        }

        String sql = "SELECT achievement_key, namespace FROM starcore_achievements WHERE player_uuid = ?";

        try (Connection conn = databaseService.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, playerId.toString());
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                String key = rs.getString("achievement_key");
                String ns = rs.getString("namespace");
                progress.markCompleted(new NamespacedKey(ns, key));
            }

            // 加载触发器计数
            String statsSql = "SELECT trigger_type, count_value FROM starcore_achievement_stats WHERE player_uuid = ?";
            try (PreparedStatement statsStmt = conn.prepareStatement(statsSql)) {
                statsStmt.setString(1, playerId.toString());
                ResultSet statsRs = statsStmt.executeQuery();

                while (statsRs.next()) {
                    String triggerType = statsRs.getString("trigger_type");
                    int count = statsRs.getInt("count_value");
                    progress.setTriggerCount(triggerType, count);
                }
            }

        } catch (Exception e) {
            logger.warning("加载玩家成就数据失败: " + e.getMessage());
        }
    }

    /**
     * 从 Properties 文件加载
     */
    private void loadFromProperties(UUID playerId, AchievementProgress progress) {
        try {
            String fileName = "achievements_" + playerId.toString() + ".properties";
            Properties props = persistenceService.loadProperties(namespace, fileName);

            if (props != null && !props.isEmpty()) {
                // 加载已完成的成就
                String completedList = props.getProperty("completed", "");
                if (!completedList.isEmpty()) {
                    for (String key : completedList.split(",")) {
                        String[] parts = key.trim().split(":", 2);
                        if (parts.length == 2) {
                            progress.markCompleted(new NamespacedKey(parts[0], parts[1]));
                        }
                    }
                }

                // 加载触发器计数
                for (String name : props.stringPropertyNames()) {
                    if (name.startsWith("trigger.")) {
                        String triggerKey = name.substring(8);
                        int count = Integer.parseInt(props.getProperty(name, "0"));
                        progress.setTriggerCount(triggerKey, count);
                    }
                }
            }
        } catch (Exception e) {
            logger.warning("从 Properties 加载玩家成就数据失败: " + e.getMessage());
        }
    }

    /**
     * 保存玩家成就进度
     */
    public void saveProgress(AchievementProgress progress) {
        progressCache.put(progress.getPlayerId(), progress);

        if (databaseService.isRunning()) {
            saveToDatabaseAsync(progress);
        } else {
            saveToProperties(progress);
        }
    }

    /**
     * 异步保存到数据库
     */
    private void saveToDatabaseAsync(AchievementProgress progress) {
        if (!databaseService.isRunning()) {
            return;
        }

        CompletableFuture.runAsync(() -> {
            try (Connection conn = databaseService.getConnection()) {
                conn.setAutoCommit(false);

                // 保存完成的成就
                String insertSql = "INSERT IGNORE INTO starcore_achievements (player_uuid, achievement_key, namespace) VALUES (?, ?, ?)";
                try (PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
                    for (NamespacedKey key : progress.getCompletedAchievements()) {
                        pstmt.setString(1, progress.getPlayerId().toString());
                        pstmt.setString(2, key.getKey());
                        pstmt.setString(3, key.getNamespace());
                        pstmt.addBatch();
                    }
                    pstmt.executeBatch();
                }

                // 保存触发器计数
                String upsertSql = """
                    INSERT INTO starcore_achievement_stats (player_uuid, trigger_type, count_value)
                    VALUES (?, ?, ?)
                    ON DUPLICATE KEY UPDATE count_value = VALUES(count_value)
                """;
                try (PreparedStatement pstmt = conn.prepareStatement(upsertSql)) {
                    for (Map.Entry<String, Integer> entry : progress.getAllTriggerCounts().entrySet()) {
                        pstmt.setString(1, progress.getPlayerId().toString());
                        pstmt.setString(2, entry.getKey());
                        pstmt.setInt(3, entry.getValue());
                        pstmt.addBatch();
                    }
                    pstmt.executeBatch();
                }

                conn.commit();
            } catch (Exception e) {
                logger.warning("保存玩家成就数据失败: " + e.getMessage());
            }
        });
    }

    /**
     * 保存到 Properties 文件
     */
    private void saveToProperties(AchievementProgress progress) {
        try {
            String fileName = "achievements_" + progress.getPlayerId().toString() + ".properties";
            Properties props = new Properties();

            // 保存已完成的成就
            StringBuilder completed = new StringBuilder();
            for (NamespacedKey key : progress.getCompletedAchievements()) {
                if (completed.length() > 0) {
                    completed.append(",");
                }
                completed.append(key.getNamespace()).append(":").append(key.getKey());
            }
            props.setProperty("completed", completed.toString());

            // 保存触发器计数
            for (Map.Entry<String, Integer> entry : progress.getAllTriggerCounts().entrySet()) {
                props.setProperty("trigger." + entry.getKey(), String.valueOf(entry.getValue()));
            }

            persistenceService.saveProperties(namespace, fileName, props);
        } catch (Exception e) {
            logger.warning("保存成就数据到文件失败: " + e.getMessage());
        }
    }

    /**
     * 记录成就完成
     */
    public void recordAchievementCompletion(UUID playerId, NamespacedKey achievementKey) {
        AchievementProgress progress = progressCache.computeIfAbsent(playerId,
            k -> new AchievementProgress(k));
        progress.markCompleted(achievementKey);
        saveProgress(progress);
    }

    /**
     * 增加触发器计数并保存
     */
    public void incrementAndSaveTriggerCount(UUID playerId, String triggerType, int amount) {
        AchievementProgress progress = progressCache.computeIfAbsent(playerId,
            k -> new AchievementProgress(k));
        progress.incrementTriggerCount(triggerType, amount);
        saveProgress(progress);
    }

    /**
     * 获取玩家成就完成数量
     */
    public int getCompletedCount(UUID playerId) {
        AchievementProgress progress = progressCache.get(playerId);
        if (progress == null) {
            progress = loadProgress(playerId);
        }
        return progress.getCompletedAchievements().size();
    }

    /**
     * 清理缓存
     */
    public void clearCache() {
        progressCache.clear();
    }

    /**
     * 清理特定玩家缓存
     */
    public void clearCache(UUID playerId) {
        progressCache.remove(playerId);
    }
}
