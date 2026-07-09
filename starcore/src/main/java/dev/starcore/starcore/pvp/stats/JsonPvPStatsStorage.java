package dev.starcore.starcore.pvp.stats;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * PvP统计JSON文件存储
 * 每个玩家一个 UUID.json 文件
 */
public class JsonPvPStatsStorage {

    private final Plugin plugin;
    private final Gson gson;
    private final File storageFolder;

    public JsonPvPStatsStorage(Plugin plugin) {
        this.plugin = plugin;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.storageFolder = new File(plugin.getDataFolder(), "pvp-stats");

        if (!storageFolder.exists()) {
            storageFolder.mkdirs();
        }
    }

    /**
     * 获取存储文件夹
     */
    public File getStorageFolder() {
        return storageFolder;
    }

    /**
     * 检查玩家统计数据文件是否存在
     */
    public boolean exists(UUID playerId) {
        File file = getPlayerFile(playerId);
        return file.exists();
    }

    /**
     * 获取玩家统计数据文件
     */
    private File getPlayerFile(UUID playerId) {
        return new File(storageFolder, playerId.toString() + ".json");
    }

    /**
     * 加载玩家统计数据（异步）
     */
    public CompletableFuture<PvPStatsData> load(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            File file = getPlayerFile(playerId);
            if (!file.exists()) {
                return null;
            }

            try (FileReader reader = new FileReader(file)) {
                return gson.fromJson(reader, PvPStatsData.class);
            } catch (IOException e) {
                plugin.getLogger().warning("加载PvP统计失败 [" + playerId + "]: " + e.getMessage());
                return null;
            }
        });
    }

    /**
     * 同步加载玩家统计数据
     */
    public PvPStatsData loadSync(UUID playerId) {
        File file = getPlayerFile(playerId);
        if (!file.exists()) {
            return null;
        }

        try (FileReader reader = new FileReader(file)) {
            return gson.fromJson(reader, PvPStatsData.class);
        } catch (IOException e) {
            plugin.getLogger().warning("加载PvP统计失败 [" + playerId + "]: " + e.getMessage());
            return null;
        }
    }

    /**
     * 保存玩家统计数据（异步）
     */
    public CompletableFuture<Boolean> save(UUID playerId, PvPStats stats) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                File file = getPlayerFile(playerId);
                PvPStatsData data = PvPStatsData.fromStats(stats);

                try (FileWriter writer = new FileWriter(file)) {
                    gson.toJson(data, writer);
                }

                return true;
            } catch (IOException e) {
                plugin.getLogger().severe("保存PvP统计失败 [" + playerId + "]: " + e.getMessage());
                return false;
            }
        });
    }

    /**
     * 同步保存玩家统计数据
     */
    public boolean saveSync(UUID playerId, PvPStats stats) {
        try {
            File file = getPlayerFile(playerId);
            PvPStatsData data = PvPStatsData.fromStats(stats);

            try (FileWriter writer = new FileWriter(file)) {
                gson.toJson(data, writer);
            }

            return true;
        } catch (IOException e) {
            plugin.getLogger().severe("保存PvP统计失败 [" + playerId + "]: " + e.getMessage());
            return false;
        }
    }

    /**
     * 保存玩家统计数据（异步）
     */
    public CompletableFuture<Boolean> saveAsync(UUID playerId, PvPStats stats) {
        return CompletableFuture.supplyAsync(() -> saveSync(playerId, stats));
    }

    /**
     * 删除玩家统计数据（异步）
     */
    public CompletableFuture<Boolean> delete(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> deleteSync(playerId));
    }

    /**
     * 删除玩家统计数据（异步）- 别名方法
     */
    public CompletableFuture<Boolean> deleteAsync(UUID playerId) {
        return delete(playerId);
    }

    /**
     * 同步删除玩家统计数据
     */
    public boolean deleteSync(UUID playerId) {
        File file = getPlayerFile(playerId);
        if (file.exists()) {
            return file.delete();
        }
        return true;
    }

    /**
     * 加载所有玩家统计数据（异步）
     */
    public CompletableFuture<java.util.List<PvPStatsData>> loadAll() {
        return CompletableFuture.supplyAsync(() -> {
            java.util.List<PvPStatsData> allStats = new java.util.ArrayList<>();
            File[] files = storageFolder.listFiles((dir, name) -> name.endsWith(".json"));

            if (files != null) {
                for (File file : files) {
                    try (FileReader reader = new FileReader(file)) {
                        PvPStatsData data = gson.fromJson(reader, PvPStatsData.class);
                        if (data != null) {
                            allStats.add(data);
                        }
                    } catch (IOException e) {
                        plugin.getLogger().warning("加载PvP统计失败 [" + file.getName() + "]: " + e.getMessage());
                    }
                }
            }

            return allStats;
        });
    }

    /**
     * 保存所有玩家统计数据（异步）
     */
    public CompletableFuture<Integer> saveAll(java.util.Map<UUID, PvPStats> allStats) {
        return CompletableFuture.supplyAsync(() -> {
            int saved = 0;
            for (var entry : allStats.entrySet()) {
                if (save(entry.getKey(), entry.getValue()).join()) {
                    saved++;
                }
            }
            return saved;
        });
    }
}
