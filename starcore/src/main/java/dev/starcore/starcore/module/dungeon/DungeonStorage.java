package dev.starcore.starcore.module.dungeon;

import dev.starcore.starcore.core.persistence.PersistenceService;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * 副本数据持久化存储
 * 负责副本数据的持久化存储，使用 Properties 文件格式
 *
 * 这是 DungeonRepository 的便捷包装类，提供简化的存储接口
 */
public class DungeonStorage {
    private final JavaPlugin plugin;
    private final PersistenceService persistenceService;
    private final Logger logger;

    // 数据缓存
    private final Map<String, String> dungeonData = new ConcurrentHashMap<>();
    private final Map<String, Map<String, String>> playerData = new ConcurrentHashMap<>();
    private final Map<String, Map<String, String>> nationData = new ConcurrentHashMap<>();

    private static final String DUNGEON_DATA_FILE = "dungeon_data.properties";
    private static final String PLAYER_DATA_PREFIX = "player_";
    private static final String NATION_DATA_PREFIX = "nation_";

    public DungeonStorage(JavaPlugin plugin, PersistenceService persistenceService) {
        this.plugin = plugin;
        this.persistenceService = persistenceService;
        this.logger = plugin.getLogger();
    }

    /**
     * 加载所有数据
     */
    public void load() {
        loadDungeonData();
        loadPlayerData();
        loadNationData();
        logger.info("副本存储数据已加载");
    }

    /**
     * 保存所有数据
     */
    public void save() {
        saveDungeonData();
        savePlayerData();
        saveNationData();
        logger.info("副本存储数据已保存");
    }

    /**
     * 保存副本全局数据
     */
    private void saveDungeonData() {
        try {
            var path = persistenceService.namespacePath("dungeon");
            var file = path.resolve(DUNGEON_DATA_FILE);

            StringBuilder content = new StringBuilder();
            for (Map.Entry<String, String> entry : dungeonData.entrySet()) {
                content.append(escapeKey(entry.getKey()))
                    .append("=")
                    .append(escapeValue(entry.getValue()))
                    .append("\n");
            }

            java.nio.file.Files.writeString(file, content.toString());
        } catch (Exception e) {
            logger.warning("保存副本数据失败: " + e.getMessage());
        }
    }

    /**
     * 加载副本全局数据
     */
    private void loadDungeonData() {
        try {
            var path = persistenceService.namespacePath("dungeon");
            var file = path.resolve(DUNGEON_DATA_FILE);

            if (java.nio.file.Files.exists(file)) {
                String content = java.nio.file.Files.readString(file);
                for (String line : content.split("\n")) {
                    if (line.contains("=")) {
                        int idx = line.indexOf("=");
                        String key = unescapeKey(line.substring(0, idx));
                        String value = unescapeValue(line.substring(idx + 1));
                        dungeonData.put(key, value);
                    }
                }
            }
        } catch (Exception e) {
            logger.warning("加载副本数据失败: " + e.getMessage());
        }
    }

    /**
     * 保存玩家数据
     */
    private void savePlayerData() {
        try {
            var path = persistenceService.namespacePath("dungeon");

            for (Map.Entry<String, Map<String, String>> playerEntry : playerData.entrySet()) {
                String filename = PLAYER_DATA_PREFIX + sanitizeFilename(playerEntry.getKey()) + ".properties";
                var file = path.resolve(filename);

                StringBuilder content = new StringBuilder();
                for (Map.Entry<String, String> entry : playerEntry.getValue().entrySet()) {
                    content.append(escapeKey(entry.getKey()))
                        .append("=")
                        .append(escapeValue(entry.getValue()))
                        .append("\n");
                }

                java.nio.file.Files.writeString(file, content.toString());
            }
        } catch (Exception e) {
            logger.warning("保存玩家数据失败: " + e.getMessage());
        }
    }

    /**
     * 加载玩家数据
     */
    private void loadPlayerData() {
        try {
            var path = persistenceService.namespacePath("dungeon");

            if (java.nio.file.Files.exists(path)) {
                try (var files = java.nio.file.Files.list(path)) {
                    files.filter(f -> f.getFileName().toString().startsWith(PLAYER_DATA_PREFIX))
                        .forEach(file -> {
                            try {
                                String content = java.nio.file.Files.readString(file);
                                String playerId = extractPlayerId(file.getFileName().toString());
                                Map<String, String> data = new HashMap<>();

                                for (String line : content.split("\n")) {
                                    if (line.contains("=")) {
                                        int idx = line.indexOf("=");
                                        String key = unescapeKey(line.substring(0, idx));
                                        String value = unescapeValue(line.substring(idx + 1));
                                        data.put(key, value);
                                    }
                                }

                                playerData.put(playerId, data);
                            } catch (Exception ex) {
                                logger.warning("加载玩家数据失败: " + file.getFileName() + " - " + ex.getMessage());
                            }
                        });
                }
            }
        } catch (Exception e) {
            logger.warning("加载玩家数据目录失败: " + e.getMessage());
        }
    }

    /**
     * 保存国家数据
     */
    private void saveNationData() {
        try {
            var path = persistenceService.namespacePath("dungeon");

            for (Map.Entry<String, Map<String, String>> nationEntry : nationData.entrySet()) {
                String filename = NATION_DATA_PREFIX + sanitizeFilename(nationEntry.getKey()) + ".properties";
                var file = path.resolve(filename);

                StringBuilder content = new StringBuilder();
                for (Map.Entry<String, String> entry : nationEntry.getValue().entrySet()) {
                    content.append(escapeKey(entry.getKey()))
                        .append("=")
                        .append(escapeValue(entry.getValue()))
                        .append("\n");
                }

                java.nio.file.Files.writeString(file, content.toString());
            }
        } catch (Exception e) {
            logger.warning("保存国家数据失败: " + e.getMessage());
        }
    }

    /**
     * 加载国家数据
     */
    private void loadNationData() {
        try {
            var path = persistenceService.namespacePath("dungeon");

            if (java.nio.file.Files.exists(path)) {
                try (var files = java.nio.file.Files.list(path)) {
                    files.filter(f -> f.getFileName().toString().startsWith(NATION_DATA_PREFIX))
                        .forEach(file -> {
                            try {
                                String content = java.nio.file.Files.readString(file);
                                String nationId = extractNationId(file.getFileName().toString());
                                Map<String, String> data = new HashMap<>();

                                for (String line : content.split("\n")) {
                                    if (line.contains("=")) {
                                        int idx = line.indexOf("=");
                                        String key = unescapeKey(line.substring(0, idx));
                                        String value = unescapeValue(line.substring(idx + 1));
                                        data.put(key, value);
                                    }
                                }

                                nationData.put(nationId, data);
                            } catch (Exception ex) {
                                logger.warning("加载国家数据失败: " + file.getFileName() + " - " + ex.getMessage());
                            }
                        });
                }
            }
        } catch (Exception e) {
            logger.warning("加载国家数据目录失败: " + e.getMessage());
        }
    }

    // ==================== 数据访问方法 ====================

    /**
     * 设置副本数据
     */
    public void setDungeonData(String key, String value) {
        dungeonData.put(key, value);
    }

    /**
     * 获取副本数据
     */
    public String getDungeonData(String key) {
        return dungeonData.get(key);
    }

    /**
     * 获取副本数据（带默认值）
     */
    public String getDungeonData(String key, String defaultValue) {
        return dungeonData.getOrDefault(key, defaultValue);
    }

    /**
     * 设置玩家数据
     */
    public void setPlayerData(String playerId, String key, String value) {
        playerData.computeIfAbsent(playerId, k -> new HashMap<>()).put(key, value);
    }

    /**
     * 获取玩家数据
     */
    public String getPlayerData(String playerId, String key) {
        Map<String, String> data = playerData.get(playerId);
        return data != null ? data.get(key) : null;
    }

    /**
     * 获取玩家数据（带默认值）
     */
    public String getPlayerData(String playerId, String key, String defaultValue) {
        Map<String, String> data = playerData.get(playerId);
        return data != null ? data.getOrDefault(key, defaultValue) : defaultValue;
    }

    /**
     * 获取玩家的所有数据
     */
    public Map<String, String> getPlayerAllData(String playerId) {
        return new HashMap<>(playerData.getOrDefault(playerId, Map.of()));
    }

    /**
     * 设置国家数据
     */
    public void setNationData(String nationId, String key, String value) {
        nationData.computeIfAbsent(nationId, k -> new HashMap<>()).put(key, value);
    }

    /**
     * 获取国家数据
     */
    public String getNationData(String nationId, String key) {
        Map<String, String> data = nationData.get(nationId);
        return data != null ? data.get(key) : null;
    }

    /**
     * 获取国家数据（带默认值）
     */
    public String getNationData(String nationId, String key, String defaultValue) {
        Map<String, String> data = nationData.get(nationId);
        return data != null ? data.getOrDefault(key, defaultValue) : defaultValue;
    }

    /**
     * 获取国家的所有数据
     */
    public Map<String, String> getNationAllData(String nationId) {
        return new HashMap<>(nationData.getOrDefault(nationId, Map.of()));
    }

    /**
     * 删除玩家数据
     */
    public void removePlayerData(String playerId) {
        playerData.remove(playerId);
    }

    /**
     * 删除国家数据
     */
    public void removeNationData(String nationId) {
        nationData.remove(nationId);
    }

    /**
     * 清空所有数据
     */
    public void clearAll() {
        dungeonData.clear();
        playerData.clear();
        nationData.clear();
    }

    // ==================== 工具方法 ====================

    private String escapeKey(String key) {
        return key.replace("\\", "\\\\").replace("=", "\\=").replace("\n", "\\n");
    }

    private String escapeValue(String value) {
        return value.replace("\\", "\\\\").replace("\n", "\\n");
    }

    private String unescapeKey(String key) {
        return key.replace("\\n", "\n").replace("\\=", "=").replace("\\\\", "\\");
    }

    private String unescapeValue(String value) {
        return value.replace("\\n", "\n").replace("\\\\", "\\");
    }

    private String sanitizeFilename(String input) {
        return input.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String extractPlayerId(String filename) {
        return filename.substring(PLAYER_DATA_PREFIX.length(), filename.length() - ".properties".length());
    }

    private String extractNationId(String filename) {
        return filename.substring(NATION_DATA_PREFIX.length(), filename.length() - ".properties".length());
    }

    /**
     * 获取存储统计信息
     */
    public String getStatistics() {
        return String.format("DungeonStorage: %d dungeon entries, %d player records, %d nation records",
            dungeonData.size(), playerData.size(), nationData.size());
    }
}
