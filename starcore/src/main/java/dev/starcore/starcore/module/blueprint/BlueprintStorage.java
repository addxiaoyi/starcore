package dev.starcore.starcore.module.blueprint;
import java.util.Optional;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import org.bukkit.plugin.Plugin;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 蓝图数据持久化管理器
 * 提供蓝图和分类的 JSON 序列化存储
 */
public final class BlueprintStorage {

    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .serializeNulls()
        .create();

    private final Plugin plugin;
    private final Path dataDir;
    private final Path blueprintsDir;
    private final Path categoriesFile;
    private final Path indexFile;

    // 蓝图索引缓存
    private final Map<String, BlueprintIndexEntry> blueprintIndex = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> playerBlueprintIndex = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> categoryBlueprintIndex = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> nationBlueprintIndex = new ConcurrentHashMap<>();

    /**
     * 蓝图索引条目
     */
    public record BlueprintIndexEntry(
        String id,
        String name,
        UUID ownerId,
        String ownerName,
        String nationId,
        String category,
        long createdAt,
        long modifiedAt,
        int blockCount,
        int dataSize,
        boolean isPublic,
        boolean isShared
    ) {}

    /**
     * 分类数据
     */
    public record CategoryData(
        String id,
        String name,
        String description,
        String icon,
        String color,
        String parentId,
        int sortOrder,
        boolean isPublic,
        Set<String> blueprintIds
    ) {}

    /**
     * 创建蓝图存储
     */
    public BlueprintStorage(Plugin plugin) {
        this.plugin = plugin;
        this.dataDir = plugin.getDataFolder().toPath().resolve("blueprint-data");
        this.blueprintsDir = dataDir.resolve("blueprints");
        this.categoriesFile = dataDir.resolve("categories.json");
        this.indexFile = dataDir.resolve("index.json");

        initDirectories();
        loadIndex();
    }

    /**
     * 初始化目录
     */
    private void initDirectories() {
        try {
            Files.createDirectories(dataDir);
            Files.createDirectories(blueprintsDir);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to create blueprint storage directories: " + e.getMessage());
        }
    }

    // ==================== 蓝图存储 ====================

    /**
     * 保存蓝图
     */
    public void saveBlueprint(Blueprint blueprint) throws IOException {
        String id = blueprint.getId();
        Path file = blueprintsDir.resolve(id + ".json");

        JsonObject json = encodeBlueprint(blueprint);

        try (Writer writer = new BufferedWriter(new FileWriter(file.toFile()))) {
            GSON.toJson(json, writer);
        }

        // 更新索引
        updateIndex(blueprint);
    }

    /**
     * 异步保存蓝图
     */
    public CompletableFuture<Void> saveBlueprintAsync(Blueprint blueprint) {
        return CompletableFuture.runAsync(() -> {
            try {
                saveBlueprint(blueprint);
            } catch (IOException e) {
                throw new RuntimeException("Failed to save blueprint: " + blueprint.getId(), e);
            }
        });
    }

    /**
     * 加载蓝图
     */
    public Optional<Blueprint> loadBlueprint(String blueprintId) {
        Path file = blueprintsDir.resolve(blueprintId + ".json");

        if (!Files.exists(file)) {
            return Optional.empty();
        }

        try (Reader reader = new BufferedReader(new FileReader(file.toFile()))) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            return decodeBlueprint(json);
        } catch (IOException | JsonSyntaxException e) {
            plugin.getLogger().warning("Failed to load blueprint " + blueprintId + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 异步加载蓝图
     */
    public CompletableFuture<Optional<Blueprint>> loadBlueprintAsync(String blueprintId) {
        return CompletableFuture.supplyAsync(() -> loadBlueprint(blueprintId));
    }

    /**
     * 删除蓝图
     */
    public boolean deleteBlueprint(String blueprintId) {
        Path file = blueprintsDir.resolve(blueprintId + ".json");

        try {
            boolean deleted = Files.deleteIfExists(file);

            if (deleted) {
                // 从索引中移除
                removeFromIndex(blueprintId);
            }

            return deleted;
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to delete blueprint " + blueprintId + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * 检查蓝图是否存在
     */
    public boolean exists(String blueprintId) {
        return Files.exists(blueprintsDir.resolve(blueprintId + ".json"));
    }

    /**
     * 获取蓝图文件大小
     */
    public long getBlueprintSize(String blueprintId) {
        Path file = blueprintsDir.resolve(blueprintId + ".json");
        try {
            return Files.size(file);
        } catch (IOException e) {
            return 0;
        }
    }

    // ==================== 分类存储 ====================

    /**
     * 保存所有分类
     */
    public void saveCategories(Collection<BlueprintCategory> categories) {
        try (Writer writer = new BufferedWriter(new FileWriter(categoriesFile.toFile()))) {
            List<CategoryData> categoryDataList = categories.stream()
                .map(this::encodeCategory)
                .collect(Collectors.toList());
            GSON.toJson(categoryDataList, writer);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save categories: " + e.getMessage());
        }
    }

    /**
     * 加载所有分类
     */
    public List<CategoryData> loadCategories() {
        if (!Files.exists(categoriesFile)) {
            return getDefaultCategories();
        }

        try (Reader reader = new BufferedReader(new FileReader(categoriesFile.toFile()))) {
            var type = new com.google.gson.reflect.TypeToken<List<CategoryData>>(){}.getType();
            List<CategoryData> categories = GSON.fromJson(reader, type);
            return categories != null ? categories : getDefaultCategories();
        } catch (IOException | JsonSyntaxException e) {
            plugin.getLogger().warning("Failed to load categories: " + e.getMessage());
            return getDefaultCategories();
        }
    }

    /**
     * 获取默认分类
     */
    private List<CategoryData> getDefaultCategories() {
        return List.of(
            new CategoryData("default", "默认", "默认分类", "CHEST", "WHITE", null, 0, true, Set.of()),
            new CategoryData("building", "建筑", "建筑类蓝图", "BRICK", "GOLD", null, 1, true, Set.of()),
            new CategoryData("decoration", "装饰", "装饰类蓝图", "FLOWER_POT", "GREEN", null, 2, true, Set.of()),
            new CategoryData("farm", "农场", "农业类蓝图", "WHEAT", "DARK_GREEN", null, 3, true, Set.of()),
            new CategoryData("redstone", "红石", "红石机械蓝图", "REPEATER", "RED", null, 4, true, Set.of()),
            new CategoryData("storage", "存储", "存储类蓝图", "CHEST", "BLUE", null, 5, true, Set.of())
        );
    }

    // ==================== 索引管理 ====================

    /**
     * 保存索引
     */
    public void saveIndex() {
        try (Writer writer = new BufferedWriter(new FileWriter(indexFile.toFile()))) {
            IndexData indexData = new IndexData(blueprintIndex, playerBlueprintIndex, categoryBlueprintIndex, nationBlueprintIndex);
            GSON.toJson(indexData, writer);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save blueprint index: " + e.getMessage());
        }
    }

    /**
     * 加载索引
     */
    private void loadIndex() {
        if (!Files.exists(indexFile)) {
            rebuildIndex();
            return;
        }

        try (Reader reader = new BufferedReader(new FileReader(indexFile.toFile()))) {
            IndexData indexData = GSON.fromJson(reader, IndexData.class);
            if (indexData != null) {
                blueprintIndex.putAll(indexData.blueprints());
                playerBlueprintIndex.clear();
                playerBlueprintIndex.putAll(indexData.playerBlueprints());
                categoryBlueprintIndex.clear();
                categoryBlueprintIndex.putAll(indexData.categoryBlueprints());
                nationBlueprintIndex.clear();
                nationBlueprintIndex.putAll(indexData.nationBlueprints());
            }
        } catch (IOException | JsonSyntaxException e) {
            plugin.getLogger().warning("Failed to load blueprint index: " + e.getMessage());
            rebuildIndex();
        }
    }

    /**
     * 重建索引
     */
    public void rebuildIndex() {
        blueprintIndex.clear();
        playerBlueprintIndex.clear();
        categoryBlueprintIndex.clear();
        nationBlueprintIndex.clear();

        try {
            Files.list(blueprintsDir)
                .filter(p -> p.toString().endsWith(".json"))
                .forEach(file -> {
                    try {
                        String id = file.getFileName().toString().replace(".json", "");
                        Optional<Blueprint> bp = loadBlueprint(id);
                        bp.ifPresent(this::updateIndex);
                    } catch (Exception e) {
                        plugin.getLogger().warning("Failed to index blueprint: " + file);
                    }
                });
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to rebuild index: " + e.getMessage());
        }

        saveIndex();
    }

    /**
     * 更新索引
     */
    private void updateIndex(Blueprint blueprint) {
        String id = blueprint.getId();
        UUID ownerId = blueprint.getOwnerId();
        String category = blueprint.getCategory();
        String nationId = blueprint.getNationId();

        BlueprintIndexEntry entry = new BlueprintIndexEntry(
            id,
            blueprint.getName(),
            ownerId,
            blueprint.getAuthorName(),
            nationId,
            category,
            blueprint.getCreatedTime(),
            blueprint.getModifiedTime(),
            blueprint.getBlockCount(),
            (int) blueprint.getDataSize(),
            blueprint.getMetadata().isPublic(),
            blueprint.getMetadata().isShared()
        );

        blueprintIndex.put(id, entry);

        // 更新玩家索引
        if (ownerId != null) {
            playerBlueprintIndex.computeIfAbsent(ownerId, k -> ConcurrentHashMap.newKeySet()).add(id);
        }

        // 更新分类索引
        if (category != null && !category.isEmpty()) {
            categoryBlueprintIndex.computeIfAbsent(category, k -> ConcurrentHashMap.newKeySet()).add(id);
        }

        // 更新国家索引
        if (nationId != null && !nationId.isEmpty()) {
            nationBlueprintIndex.computeIfAbsent(nationId, k -> ConcurrentHashMap.newKeySet()).add(id);
        }

        // 异步保存索引
        CompletableFuture.runAsync(this::saveIndex);
    }

    /**
     * 从索引中移除
     */
    private void removeFromIndex(String blueprintId) {
        BlueprintIndexEntry entry = blueprintIndex.remove(blueprintId);

        if (entry != null) {
            // 从玩家索引移除
            if (entry.ownerId() != null) {
                Set<String> playerBlueprints = playerBlueprintIndex.get(entry.ownerId());
                if (playerBlueprints != null) {
                    playerBlueprints.remove(blueprintId);
                }
            }

            // 从分类索引移除
            if (entry.category() != null && !entry.category().isEmpty()) {
                Set<String> categoryBlueprints = categoryBlueprintIndex.get(entry.category());
                if (categoryBlueprints != null) {
                    categoryBlueprints.remove(blueprintId);
                }
            }

            // 从国家索引移除
            if (entry.nationId() != null && !entry.nationId().isEmpty()) {
                Set<String> nationBlueprints = nationBlueprintIndex.get(entry.nationId());
                if (nationBlueprints != null) {
                    nationBlueprints.remove(blueprintId);
                }
            }

            saveIndex();
        }
    }

    // ==================== 查询方法 ====================

    /**
     * 获取玩家的所有蓝图 ID
     */
    public Set<String> getPlayerBlueprintIds(UUID playerId) {
        return playerBlueprintIndex.getOrDefault(playerId, Set.of());
    }

    /**
     * 获取分类的所有蓝图 ID
     */
    public Set<String> getCategoryBlueprintIds(String categoryId) {
        return categoryBlueprintIndex.getOrDefault(categoryId, Set.of());
    }

    /**
     * 获取国家的所有蓝图 ID
     */
    public Set<String> getNationBlueprintIds(String nationId) {
        return nationBlueprintIndex.getOrDefault(nationId, Set.of());
    }

    /**
     * 获取所有蓝图 ID
     */
    public Set<String> getAllBlueprintIds() {
        return new HashSet<>(blueprintIndex.keySet());
    }

    /**
     * 获取索引条目
     */
    public Optional<BlueprintIndexEntry> getIndexEntry(String blueprintId) {
        return Optional.ofNullable(blueprintIndex.get(blueprintId));
    }

    /**
     * 搜索蓝图（按名称）
     */
    public List<BlueprintIndexEntry> searchBlueprints(String query) {
        String lowerQuery = query.toLowerCase();
        return blueprintIndex.values().stream()
            .filter(entry -> entry.name().toLowerCase().contains(lowerQuery))
            .collect(Collectors.toList());
    }

    // ==================== 统计 ====================

    /**
     * 获取存储统计
     */
    public StorageStats getStats() {
        long totalBlueprints = blueprintIndex.size();
        long totalCategories = categoryBlueprintIndex.size();
        long totalPlayers = playerBlueprintIndex.size();
        long totalNations = nationBlueprintIndex.size();
        long totalSize = blueprintIndex.values().stream()
            .mapToLong(BlueprintIndexEntry::dataSize)
            .sum();

        return new StorageStats(totalBlueprints, totalCategories, totalPlayers, totalNations, totalSize);
    }

    public record StorageStats(
        long totalBlueprints,
        long totalCategories,
        long totalPlayers,
        long totalNations,
        long totalSizeBytes
    ) {}

    // ==================== 序列化 ====================

    private JsonObject encodeBlueprint(Blueprint blueprint) {
        JsonObject json = new JsonObject();

        // 基本信息
        json.addProperty("id", blueprint.getId());
        json.addProperty("name", blueprint.getName());
        json.addProperty("description", blueprint.getDescription());
        json.addProperty("authorId", blueprint.getAuthorId() != null ? blueprint.getAuthorId().toString() : null);
        json.addProperty("authorName", blueprint.getAuthorName());
        json.addProperty("ownerId", blueprint.getOwnerId() != null ? blueprint.getOwnerId().toString() : null);
        json.addProperty("nationId", blueprint.getNationId());
        json.addProperty("category", blueprint.getCategory());
        json.addProperty("createdAt", blueprint.getCreatedTime());
        json.addProperty("modifiedAt", blueprint.getModifiedTime());
        json.addProperty("version", blueprint.getVersion());
        json.addProperty("isPublic", blueprint.getMetadata().isPublic());
        json.addProperty("isShared", blueprint.getMetadata().isShared());

        // 区域信息
        RegionSelection region = blueprint.getRegion();
        if (region != null) {
            JsonObject regionJson = new JsonObject();
            regionJson.addProperty("world", region.getWorldName());
            regionJson.addProperty("type", region.getType());

            if (region instanceof CuboidRegion cr) {
                regionJson.addProperty("minX", cr.getMinPoint().getX());
                regionJson.addProperty("minY", cr.getMinPoint().getY());
                regionJson.addProperty("minZ", cr.getMinPoint().getZ());
                regionJson.addProperty("maxX", cr.getMaxPoint().getX());
                regionJson.addProperty("maxY", cr.getMaxPoint().getY());
                regionJson.addProperty("maxZ", cr.getMaxPoint().getZ());
            }

            json.add("region", regionJson);
        }

        // 方块数据（简化为基本信息，实际数据存储在单独文件）
        json.addProperty("blockCount", blueprint.getBlockCount());

        return json;
    }

    /**
     * 解码蓝图 JSON
     * @return 解码成功返回 Optional.of(blueprint)，失败返回 Optional.empty()
     */
    public Optional<Blueprint> decodeBlueprint(JsonObject json) {
        if (json == null) {
            return Optional.empty();
        }
        try {
            String id = json.get("id").getAsString();
            String name = json.get("name").getAsString();
            String description = json.has("description") ? json.get("description").getAsString() : "";
            UUID authorId = json.has("authorId") && !json.get("authorId").isJsonNull()
                ? UUID.fromString(json.get("authorId").getAsString()) : null;
            String authorName = json.get("authorName").getAsString();
            UUID ownerId = json.has("ownerId") && !json.get("ownerId").isJsonNull()
                ? UUID.fromString(json.get("ownerId").getAsString()) : authorId;
            String nationId = json.has("nationId") && !json.get("nationId").isJsonNull()
                ? json.get("nationId").getAsString() : null;
            String category = json.has("category") && !json.get("category").isJsonNull()
                ? json.get("category").getAsString() : "default";
            long createdAt = json.has("createdAt") ? json.get("createdAt").getAsLong() : System.currentTimeMillis();
            long modifiedAt = json.has("modifiedAt") ? json.get("modifiedAt").getAsLong() : createdAt;
            int version = json.has("version") ? json.get("version").getAsInt() : 1;

            // 解码区域
            RegionSelection region = null;
            if (json.has("region")) {
                JsonObject regionJson = json.getAsJsonObject("region");
                String world = regionJson.get("world").getAsString();

                if (regionJson.has("minX") && regionJson.has("maxX")) {
                    BlockVector3 min = BlockVector3.at(
                        regionJson.get("minX").getAsInt(),
                        regionJson.get("minY").getAsInt(),
                        regionJson.get("minZ").getAsInt()
                    );
                    BlockVector3 max = BlockVector3.at(
                        regionJson.get("maxX").getAsInt(),
                        regionJson.get("maxY").getAsInt(),
                        regionJson.get("maxZ").getAsInt()
                    );
                    region = new CuboidRegion(world, min, max);
                }
            }

            int blockCount = json.has("blockCount") ? json.get("blockCount").getAsInt() : 0;

            // 创建 BlueprintImpl
            BlueprintImpl blueprint = new BlueprintImpl(
                id, name, authorId, authorName, region, List.of()
            );
            blueprint.setOwnerId(ownerId);
            blueprint.setNationId(nationId);
            blueprint.setCategory(category);
            blueprint.setDescription(description);
            blueprint.setPublic(json.has("isPublic") && json.get("isPublic").getAsBoolean());
            blueprint.setShared(json.has("isShared") && json.get("isShared").getAsBoolean());

            // 注意：方块数据需要在其他地方加载或懒加载

            return Optional.of(blueprint);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to decode blueprint: " + e.getMessage());
            return Optional.empty();
        }
    }

    private CategoryData encodeCategory(BlueprintCategory category) {
        return new CategoryData(
            category.getId(),
            category.getName(),
            category.getDescription(),
            category.getIcon(),
            category.getColor(),
            category.getParentId(),
            category.getSortOrder(),
            category.isPublic(),
            new HashSet<>(category.getBlueprintIds())
        );
    }

    // ==================== 内部类 ====================

    /**
     * 索引数据结构
     */
    private record IndexData(
        Map<String, BlueprintIndexEntry> blueprints,
        Map<UUID, Set<String>> playerBlueprints,
        Map<String, Set<String>> categoryBlueprints,
        Map<String, Set<String>> nationBlueprints
    ) {
        public IndexData {
            blueprints = blueprints != null ? blueprints : new HashMap<>();
            playerBlueprints = playerBlueprints != null ? playerBlueprints : new HashMap<>();
            categoryBlueprints = categoryBlueprints != null ? categoryBlueprints : new HashMap<>();
            nationBlueprints = nationBlueprints != null ? nationBlueprints : new HashMap<>();
        }
    }

    /**
     * 导出到指定格式
     */
    public boolean exportToFile(String blueprintId, Path targetFile, String format) {
        Optional<Blueprint> blueprint = loadBlueprint(blueprintId);
        if (blueprint.isEmpty()) {
            return false;
        }

        try {
            switch (format.toLowerCase()) {
                case "json" -> {
                    try (Writer writer = new BufferedWriter(new FileWriter(targetFile.toFile()))) {
                        GSON.toJson(encodeBlueprint(blueprint.get()), writer);
                    }
                    return true;
                }
                case "blueprint" -> {
                    try (Writer writer = new BufferedWriter(new FileWriter(targetFile.toFile()))) {
                        GSON.toJson(encodeBlueprint(blueprint.get()), writer);
                    }
                    return true;
                }
                default -> {
                    plugin.getLogger().warning("Unsupported export format: " + format);
                    return false;
                }
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to export blueprint: " + e.getMessage());
            return false;
        }
    }

    /**
     * 从文件导入
     */
    public Optional<Blueprint> importFromFile(Path sourceFile, String format) {
        if (!Files.exists(sourceFile)) {
            return Optional.empty();
        }

        try (Reader reader = new BufferedReader(new FileReader(sourceFile.toFile()))) {
            switch (format.toLowerCase()) {
                case "json", "blueprint" -> {
                    JsonObject json = GSON.fromJson(reader, JsonObject.class);
                    return decodeBlueprint(json);
                }
                default -> {
                    plugin.getLogger().warning("Unsupported import format: " + format);
                    return Optional.empty();
                }
            }
        } catch (IOException | JsonSyntaxException e) {
            plugin.getLogger().warning("Failed to import blueprint: " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 关闭存储，保存所有数据
     */
    public void close() {
        saveIndex();
    }
}
