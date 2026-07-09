package dev.starcore.starcore.nation.database;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.starcore.starcore.nation.relation.NationRelations;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Nation关系数据库
 * 使用JSON文件存储Nation关系数据
 */
public class RelationDatabase {

    private final Plugin plugin;
    private final Gson gson;
    private final File dataFolder;

    public RelationDatabase(Plugin plugin) {
        this.plugin = plugin;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.dataFolder = new File(plugin.getDataFolder(), "relations");

        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
    }

    /**
     * 保存Nation关系（异步）
     */
    public CompletableFuture<Boolean> saveRelations(NationRelations relations) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                File file = new File(dataFolder, relations.getNationId() + ".json");
                RelationData data = convertToData(relations);

                try (FileWriter writer = new FileWriter(file)) {
                    gson.toJson(data, writer);
                }

                return true;
            } catch (IOException e) {
                plugin.getLogger().severe("保存Nation关系失败: " + e.getMessage());
                return false;
            }
        });
    }

    /**
     * 加载Nation关系（异步）
     */
    public CompletableFuture<NationRelations> loadRelations(UUID nationId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                File file = new File(dataFolder, nationId + ".json");
                if (!file.exists()) {
                    return new NationRelations(nationId);
                }

                try (FileReader reader = new FileReader(file)) {
                    RelationData data = gson.fromJson(reader, RelationData.class);
                    return convertFromData(data);
                }
            } catch (IOException e) {
                plugin.getLogger().severe("加载Nation关系失败: " + e.getMessage());
                return new NationRelations(nationId);
            }
        });
    }

    /**
     * 删除Nation关系（异步）
     */
    public CompletableFuture<Boolean> deleteRelations(UUID nationId) {
        return CompletableFuture.supplyAsync(() -> {
            File file = new File(dataFolder, nationId + ".json");
            return file.exists() && file.delete();
        });
    }

    /**
     * 加载所有Nation关系（异步）
     */
    public CompletableFuture<Map<UUID, NationRelations>> loadAllRelations() {
        return CompletableFuture.supplyAsync(() -> {
            Map<UUID, NationRelations> relationsMap = new HashMap<>();
            File[] files = dataFolder.listFiles((dir, name) -> name.endsWith(".json"));

            if (files != null) {
                for (File file : files) {
                    try (FileReader reader = new FileReader(file)) {
                        RelationData data = gson.fromJson(reader, RelationData.class);
                        NationRelations relations = convertFromData(data);
                        if (relations != null) {
                            relationsMap.put(relations.getNationId(), relations);
                        }
                    } catch (IOException e) {
                        plugin.getLogger().warning("加载Nation关系失败: " + file.getName());
                    }
                }
            }

            return relationsMap;
        });
    }

    /**
     * 保存所有Nation关系（异步）
     */
    public CompletableFuture<Integer> saveAllRelations(Collection<NationRelations> allRelations) {
        return CompletableFuture.supplyAsync(() -> {
            int saved = 0;
            for (NationRelations relations : allRelations) {
                if (saveRelations(relations).join()) {
                    saved++;
                }
            }
            return saved;
        });
    }

    // ==================== 数据转换 ====================

    private RelationData convertToData(NationRelations relations) {
        RelationData data = new RelationData();
        data.nationId = relations.getNationId().toString();

        data.allies = relations.getAllies().stream()
            .map(UUID::toString)
            .toList();

        data.enemies = relations.getEnemies().stream()
            .map(UUID::toString)
            .toList();

        return data;
    }

    private NationRelations convertFromData(RelationData data) {
        try {
            UUID nationId = UUID.fromString(data.nationId);
            NationRelations relations = new NationRelations(nationId);

            // 添加盟友
            for (String allyStr : data.allies) {
                relations.addAlly(UUID.fromString(allyStr));
            }

            // 添加敌对
            for (String enemyStr : data.enemies) {
                relations.addEnemy(UUID.fromString(enemyStr));
            }

            return relations;
        } catch (Exception e) {
            plugin.getLogger().severe("转换Nation关系数据失败: " + e.getMessage());
            return null;
        }
    }

    // ==================== 数据类 ====================

    private static class RelationData {
        String nationId;
        List<String> allies;
        List<String> enemies;
    }
}
