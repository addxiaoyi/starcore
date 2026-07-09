package dev.starcore.starcore.module.blueprint;
import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Registry;
import org.bukkit.entity.Player;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 蓝图服务实现
 */
public class BlueprintServiceImpl implements BlueprintService {
    private static final int MAX_CLIPBOARD_HISTORY = 10;

    private final JavaPlugin plugin;
    private final Path blueprintDir;
    private final Path categoryFile;

    // 蓝图缓存
    private final Map<String, BlueprintImpl> blueprintCache = new ConcurrentHashMap<>();
    private final Map<UUID, BlueprintImpl> playerBlueprints = new ConcurrentHashMap<>();

    // 剪贴板缓存
    private final Map<UUID, BlueprintClipboardImpl> clipboards = new ConcurrentHashMap<>();

    // 分类缓存
    private final Map<String, BlueprintCategoryImpl> categories = new ConcurrentHashMap<>();

    // 蓝图索引
    private final Map<UUID, List<String>> blueprintIndex = new ConcurrentHashMap<>();
    private final Map<String, List<String>> categoryIndex = new ConcurrentHashMap<>();
    private final Map<String, List<String>> nationIndex = new ConcurrentHashMap<>();

    public BlueprintServiceImpl(JavaPlugin plugin) {
        this.plugin = plugin;
        this.blueprintDir = plugin.getDataFolder().toPath().resolve("blueprints");
        this.categoryFile = blueprintDir.resolve("categories.dat");

        // 初始化默认分类
        initDefaultCategories();

        // 加载所有蓝图
        loadAllBlueprints();
    }

    private void initDefaultCategories() {
        addCategory(new BlueprintCategoryImpl("default", "默认", "默认分类", "CHEST", "WHITE"));
        addCategory(new BlueprintCategoryImpl("building", "建筑", "建筑类蓝图", "BRICK", "GOLD"));
        addCategory(new BlueprintCategoryImpl("decoration", "装饰", "装饰类蓝图", "FLOWER_POT", "GREEN"));
        addCategory(new BlueprintCategoryImpl("farm", "农场", "农业类蓝图", "WHEAT", "DARK_GREEN"));
        addCategory(new BlueprintCategoryImpl("redstone", "红石", "红石机械蓝图", "REPEATER", "RED"));
        addCategory(new BlueprintCategoryImpl("storage", "存储", "存储类蓝图", "CHEST", "BLUE"));
    }

    private void addCategory(BlueprintCategoryImpl category) {
        categories.put(category.getId(), category);
        categoryIndex.put(category.getId(), new ArrayList<>());
    }

    private void loadAllBlueprints() {
        try {
            Files.createDirectories(blueprintDir);

            // 加载分类
            loadCategories();

            // 加载所有蓝图文件
            Path[] files = Files.list(blueprintDir)
                .filter(p -> p.toString().endsWith(".blueprint"))
                .toArray(Path[]::new);

            for (Path file : files) {
                try {
                    BlueprintImpl blueprint = BlueprintImpl.deserializeFromFile(file);
                    blueprint.setDataFile(file);
                    blueprintCache.put(blueprint.getId(), blueprint);

                    // 更新索引
                    if (blueprint.getOwnerId() != null) {
                        playerBlueprints.put(blueprint.getOwnerId(), blueprint);
                        blueprintIndex.computeIfAbsent(blueprint.getOwnerId(), k -> new ArrayList<>())
                            .add(blueprint.getId());
                    }

                    // 更新分类索引
                    if (blueprint.getCategory() != null) {
                        categoryIndex.computeIfAbsent(blueprint.getCategory(), k -> new ArrayList<>())
                            .add(blueprint.getId());
                    }

                    // 更新国家索引
                    if (blueprint.getNationId() != null) {
                        nationIndex.computeIfAbsent(blueprint.getNationId(), k -> new ArrayList<>())
                            .add(blueprint.getId());
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to load blueprint: " + file.getFileName());
                }
            }

            plugin.getLogger().info("Loaded " + blueprintCache.size() + " blueprints");
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to load blueprints: " + e.getMessage());
        }
    }

    private void loadCategories() {
        if (!Files.exists(categoryFile)) {
            return;
        }

        try (DataInputStream in = new DataInputStream(Files.newInputStream(categoryFile))) {
            int count = in.readInt();
            for (int i = 0; i < count; i++) {
                String id = in.readUTF();
                String name = in.readUTF();
                String desc = in.readUTF();
                String icon = in.readUTF();
                String color = in.readUTF();
                String parent = in.readUTF();
                int order = in.readInt();
                boolean pub = in.readBoolean();

                BlueprintCategoryImpl cat = new BlueprintCategoryImpl(id, name, desc, icon, color);
                cat.setParentId(parent.isEmpty() ? null : parent);
                cat.setSortOrder(order);
                cat.setPublic(pub);

                int blueprintCount = in.readInt();
                for (int j = 0; j < blueprintCount; j++) {
                    cat.addBlueprint(in.readUTF());
                }

                categories.put(id, cat);
                categoryIndex.put(id, new ArrayList<>());
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to load categories: " + e.getMessage());
        }
    }

    private void saveCategories() {
        try (DataOutputStream out = new DataOutputStream(
                Files.newOutputStream(categoryFile))) {

            out.writeInt(categories.size());

            for (BlueprintCategoryImpl cat : categories.values()) {
                out.writeUTF(cat.getId());
                out.writeUTF(cat.getName());
                out.writeUTF(cat.getDescription() != null ? cat.getDescription() : "");
                out.writeUTF(cat.getIcon());
                out.writeUTF(cat.getColor());
                out.writeUTF(cat.getParentId() != null ? cat.getParentId() : "");
                out.writeInt(cat.getSortOrder());
                out.writeBoolean(cat.isPublic());

                out.writeInt(cat.getBlueprintIds().size());
                for (String bpId : cat.getBlueprintIds()) {
                    out.writeUTF(bpId);
                }
            }

            out.flush();
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save categories: " + e.getMessage());
        }
    }

    @Override
    public Blueprint createBlueprint(Player player, RegionSelection region, String name, String description) {
        List<BlueprintBlock> blocks = new ArrayList<>();
        var world = Bukkit.getWorld(region.getWorldName());

        if (world == null) {
            throw new BlueprintException("World not found: " + region.getWorldName());
        }

        BlockVector3 min = region.getMinPoint();

        for (BlockVector3 pos : region.getBlocks()) {
            var block = world.getBlockAt(pos.getX(), pos.getY(), pos.getZ());
            BlueprintBlock bpBlock = BlueprintBlock.fromBlock(block);

            blocks.add(new BlueprintBlock(
                pos.getX() - min.getX(),
                pos.getY() - min.getY(),
                pos.getZ() - min.getZ(),
                bpBlock.getMaterialName(),
                bpBlock.getData(),
                bpBlock.getBlockState(),
                bpBlock.getTileEntityData()
            ));
        }

        String id = UUID.randomUUID().toString();
        BlueprintImpl blueprint = new BlueprintImpl(
            id, name, player.getUniqueId(), player.getName(), region, blocks
        );
        blueprint.setDescription(description);
        blueprint.setOwnerId(player.getUniqueId());

        // 保存到文件
        Path file = blueprintDir.resolve(id + ".blueprint");
        blueprint.setDataFile(file);

        try {
            blueprint.save();
            blueprintCache.put(id, blueprint);

            // 更新索引
            blueprintIndex.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>()).add(id);

            plugin.getLogger().info("Player " + player.getName() + " created blueprint: " + name);
        } catch (BlueprintException e) {
            throw new BlueprintException("Failed to save blueprint: " + e.getMessage(), e);
        }

        return blueprint;
    }

    @Override
    public void saveBlueprint(Blueprint blueprint) throws BlueprintException {
        if (blueprint instanceof BlueprintImpl impl) {
            impl.save();
        } else {
            throw new BlueprintException("Unsupported blueprint type");
        }
    }

    @Override
    public CompletableFuture<Void> saveBlueprintAsync(Blueprint blueprint) {
        return CompletableFuture.runAsync(() -> {
            try {
                saveBlueprint(blueprint);
            } catch (BlueprintException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public Optional<Blueprint> loadBlueprint(String blueprintId) {
        Blueprint cached = blueprintCache.get(blueprintId);
        if (cached != null) {
            return Optional.of(cached);
        }

        Path file = blueprintDir.resolve(blueprintId + ".blueprint");
        if (!Files.exists(file)) {
            return Optional.empty();
        }

        try {
            BlueprintImpl blueprint = BlueprintImpl.deserializeFromFile(file);
            blueprint.setDataFile(file);
            blueprintCache.put(blueprintId, blueprint);
            return Optional.of(blueprint);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to load blueprint " + blueprintId + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public CompletableFuture<Optional<Blueprint>> loadBlueprintAsync(String blueprintId) {
        return CompletableFuture.supplyAsync(() -> loadBlueprint(blueprintId));
    }

    @Override
    public boolean deleteBlueprint(String blueprintId) {
        BlueprintImpl blueprint = blueprintCache.remove(blueprintId);
        if (blueprint == null) {
            return false;
        }

        Path file = blueprint.getDataFile();
        if (file != null && Files.exists(file)) {
            try {
                Files.delete(file);
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to delete blueprint file: " + e.getMessage());
            }
        }

        // 移除索引
        if (blueprint.getOwnerId() != null) {
            List<String> list = blueprintIndex.get(blueprint.getOwnerId());
            if (list != null) {
                list.remove(blueprintId);
            }
        }

        if (blueprint.getCategory() != null) {
            List<String> list = categoryIndex.get(blueprint.getCategory());
            if (list != null) {
                list.remove(blueprintId);
            }
        }

        return true;
    }

    @Override
    public boolean renameBlueprint(String blueprintId, String newName) {
        BlueprintImpl blueprint = blueprintCache.get(blueprintId);
        if (blueprint == null) {
            return false;
        }

        blueprint.setName(newName);

        try {
            blueprint.save();
            return true;
        } catch (BlueprintException e) {
            plugin.getLogger().warning("Failed to save renamed blueprint: " + e.getMessage());
            return false;
        }
    }

    @Override
    public Blueprint copyBlueprint(String blueprintId, String newName) {
        Optional<Blueprint> original = loadBlueprint(blueprintId);
        if (original.isEmpty()) {
            throw new BlueprintException("Blueprint not found: " + blueprintId);
        }

        Blueprint copy = original.get().copy();
        copy.setName(newName);

        try {
            saveBlueprint(copy);
            return copy;
        } catch (BlueprintException e) {
            throw new BlueprintException("Failed to save copied blueprint: " + e.getMessage(), e);
        }
    }

    @Override
    public java.util.List<dev.starcore.starcore.module.blueprint.BlueprintTypes.BlueprintMetadata> getPlayerBlueprints(UUID playerId) {
        List<String> ids = blueprintIndex.getOrDefault(playerId, Collections.emptyList());
        return ids.stream()
            .map(blueprintCache::get)
            .filter(Objects::nonNull)
            .map(Blueprint::getMetadata)
            .collect(Collectors.toList());
    }

    @Override
    public java.util.List<dev.starcore.starcore.module.blueprint.BlueprintTypes.BlueprintMetadata> getNationBlueprints(String nationId) {
        java.util.List<String> ids = nationIndex.getOrDefault(nationId, Collections.emptyList());
        return ids.stream()
            .map(blueprintCache::get)
            .filter(Objects::nonNull)
            .map(Blueprint::getMetadata)
            .collect(Collectors.toList());
    }

    @Override
    public java.util.List<dev.starcore.starcore.module.blueprint.BlueprintTypes.BlueprintMetadata> getPublicBlueprints() {
        return blueprintCache.values().stream()
            .map(Blueprint::getMetadata)
            .collect(Collectors.toList());
    }

    @Override
    public java.util.List<dev.starcore.starcore.module.blueprint.BlueprintTypes.BlueprintMetadata> getBlueprintsByCategory(String categoryId) {
        java.util.List<String> ids = categoryIndex.getOrDefault(categoryId, Collections.emptyList());
        return ids.stream()
            .map(blueprintCache::get)
            .filter(Objects::nonNull)
            .map(Blueprint::getMetadata)
            .collect(Collectors.toList());
    }

    @Override
    public java.util.List<dev.starcore.starcore.module.blueprint.BlueprintTypes.BlueprintMetadata> searchBlueprints(String query) {
        String lower = query.toLowerCase();
        return blueprintCache.values().stream()
            .filter(bp -> bp.getName().toLowerCase().contains(lower) ||
                         (bp.getDescription() != null && bp.getDescription().toLowerCase().contains(lower)))
            .map(Blueprint::getMetadata)
            .collect(Collectors.toList());
    }

    @Override
    public Optional<Blueprint> findBlueprintByName(String name) {
        return blueprintCache.values().stream()
            .filter(bp -> bp.getName().equalsIgnoreCase(name))
            .findFirst()
            .map(bp -> (Blueprint) bp);
    }

    @Override
    public List<BlueprintCategory> getAllCategories() {
        return new ArrayList<>(categories.values());
    }

    @Override
    public BlueprintCategory createCategory(String name, String description, String icon, String color) {
        String id = UUID.randomUUID().toString();
        BlueprintCategoryImpl category = new BlueprintCategoryImpl(id, name, description, icon, color);
        addCategory(category);
        saveCategories();
        return category;
    }

    @Override
    public boolean deleteCategory(String categoryId) {
        if ("default".equals(categoryId)) {
            return false; // 不能删除默认分类
        }

        BlueprintCategoryImpl removed = categories.remove(categoryId);
        if (removed == null) {
            return false;
        }

        categoryIndex.remove(categoryId);
        saveCategories();
        return true;
    }

    @Override
    public boolean updateCategory(String categoryId, String name, String description) {
        BlueprintCategoryImpl category = categories.get(categoryId);
        if (category == null) {
            return false;
        }

        // 更新字段
        category = new BlueprintCategoryImpl(
            category.getId(), name, description, category.getIcon(), category.getColor()
        );
        categories.put(categoryId, category);
        saveCategories();
        return true;
    }

    @Override
    public Optional<BlueprintClipboard> getClipboard(UUID playerId) {
        return Optional.ofNullable(clipboards.get(playerId));
    }

    @Override
    public BlueprintClipboard copyToClipboard(Player player, RegionSelection region) {
        BlueprintClipboardImpl clipboard = new BlueprintClipboardImpl(
            player.getUniqueId(), player.getName()
        );
        clipboard.copyFromRegion(region);
        clipboards.put(player.getUniqueId(), clipboard);
        return clipboard;
    }

    @Override
    public dev.starcore.starcore.module.blueprint.BlueprintTypes.PasteResult pasteClipboard(Player player, Location origin, boolean includeAir) {
        BlueprintClipboardImpl clipboard = clipboards.get(player.getUniqueId());
        if (clipboard == null) {
            return dev.starcore.starcore.module.blueprint.BlueprintTypes.PasteResult.failure("No clipboard data", 0);
        }

        return clipboard.pasteToWorld(origin.getWorld(),
            BlockVector3.fromLocation(origin), includeAir);
    }

    @Override
    public void clearClipboard(UUID playerId) {
        BlueprintClipboardImpl removed = clipboards.remove(playerId);
        if (removed != null) {
            removed.clear();
        }
    }

    @Override
    public Optional<EditSession> getEditSession(UUID playerId) {
        return EditSessionImpl.get(playerId).map(es -> (EditSession) es);
    }

    @Override
    public EditSession createEditSession(Player player) {
        return EditSessionImpl.getOrCreate(player);
    }

    @Override
    public void closeEditSession(UUID playerId) {
        EditSessionImpl.close(playerId);
    }

    @Override
    public boolean undo(UUID playerId) {
        Optional<EditSessionImpl> session = EditSessionImpl.get(playerId);
        return session.map(EditSession::canUndo).orElse(false);
    }

    @Override
    public boolean redo(UUID playerId) {
        Optional<EditSessionImpl> session = EditSessionImpl.get(playerId);
        return session.map(EditSession::canRedo).orElse(false);
    }

    @Override
    public BlueprintTypes.PasteResult pasteBlueprint(Player player, String blueprintId, Location origin,
                                                  boolean includeAir, boolean entities) {
        Optional<Blueprint> blueprint = loadBlueprint(blueprintId);
        if (blueprint.isEmpty()) {
            return BlueprintTypes.PasteResult.failure("Blueprint not found: " + blueprintId, 0);
        }

        return blueprint.get().paste(origin.getWorld(),
            BlockVector3.fromLocation(origin), includeAir, entities);
    }

    @Override
    public CompletableFuture<BlueprintTypes.PasteResult> pasteBlueprintAsync(Player player, String blueprintId,
                                                                           Location origin,
                                                                           boolean includeAir, boolean entities) {
        return CompletableFuture.supplyAsync(() ->
            pasteBlueprint(player, blueprintId, origin, includeAir, entities)
        );
    }

    @Override
    public CompletableFuture<List<BlueprintTypes.PasteResult>> batchPaste(UUID playerId, List<String> blueprintIds,
                                                                       Location origin) {
        return CompletableFuture.supplyAsync(() -> {
            List<BlueprintTypes.PasteResult> results = new ArrayList<>();
            for (String blueprintId : blueprintIds) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null) {
                    results.add(pasteBlueprint(player, blueprintId, origin, true, false));
                } else {
                    results.add(BlueprintTypes.PasteResult.failure("Player not online", 0));
                }
            }
            return results;
        });
    }

    @Override
    public Optional<Blueprint> importBlueprint(String filePath, String format) {
        try {
            Path path = Path.of(filePath);
            if (!Files.exists(path)) {
                plugin.getLogger().warning("Import file not found: " + filePath);
                return Optional.empty();
            }

            String lowerFormat = format.toLowerCase();
            return switch (lowerFormat) {
                case "schematic", "schem" -> importSchematic(path);
                case "blueprint", "bp" -> importBlueprintFile(path);
                default -> {
                    plugin.getLogger().warning("Unsupported import format: " + format);
                    yield Optional.empty();
                }
            };
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to import blueprint: " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 从文件导入蓝图（内部格式）
     */
    private Optional<Blueprint> importBlueprintFile(Path path) {
        try {
            BlueprintImpl blueprint = BlueprintImpl.deserializeFromFile(path);
            blueprint.setDataFile(path);
            blueprintCache.put(blueprint.getId(), blueprint);

            // 更新索引
            if (blueprint.getOwnerId() != null) {
                blueprintIndex.computeIfAbsent(blueprint.getOwnerId(), k -> new ArrayList<>())
                    .add(blueprint.getId());
            }

            plugin.getLogger().info("Imported blueprint: " + blueprint.getName());
            return Optional.of(blueprint);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to import blueprint file: " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 从schematic格式导入
     * Schematic格式: 宽(W)、高(H)、长(L) + 偏移 + 区块数据 + 实体数据
     */
    private Optional<Blueprint> importSchematic(Path path) {
        try (var input = new java.io.DataInputStream(Files.newInputStream(path))) {
            // 读取schematic头部
            String schematicId = readString(input);

            // 检查是否为有效的schematic格式
            if (!schematicId.equals("Schematic") && !schematicId.equals("AA")) {
                plugin.getLogger().warning("Invalid schematic file: " + path);
                return Optional.empty();
            }

            // 读取版本
            short version;
            if (schematicId.equals("AA")) {
                // 旧版格式
                version = 1;
            } else {
                version = input.readShort();
            }

            // 读取尺寸
            int width, height, length;
            int[] offset = new int[3];
            String worldName = "";
            int blockCount = 0;

            if (version >= 2) {
                width = input.readInt();
                height = input.readInt();
                length = input.readInt();
                offset[0] = input.readInt();
                offset[1] = input.readInt();
                offset[2] = input.readInt();

                // 读取额外的元数据
                Map<String, Object> metadata = new java.util.HashMap<>();
                int metadataSize = input.readInt();
                for (int i = 0; i < metadataSize; i++) {
                    String key = readString(input);
                    String value = readString(input);
                    metadata.put(key, value);
                }
                worldName = (String) metadata.getOrDefault("World", "");
            } else {
                width = input.readShort() & 0xFFFF;
                height = input.readShort() & 0xFFFF;
                length = input.readShort() & 0xFFFF;
                offset[0] = input.readInt();
                offset[1] = input.readInt();
                offset[2] = input.readInt();
            }

            // 读取方块数据
            byte[] blockData = new byte[width * height * length];
            input.readFully(blockData);

            // 读取方块状态数据（可选）
            byte[] blockStates = null;
            if (version >= 2) {
                int blockStatesSize = input.readInt();
                if (blockStatesSize > 0) {
                    blockStates = new byte[blockStatesSize];
                    input.readFully(blockStates);
                }
            }

            // 读取方块实体数据
            int tileEntityCount = input.readInt();
            List<String> tileEntityData = new ArrayList<>();
            for (int i = 0; i < tileEntityCount; i++) {
                int size = input.readInt();
                byte[] data = new byte[size];
                input.readFully(data);
                tileEntityData.add(new String(data, java.nio.charset.StandardCharsets.UTF_8));
            }

            // 读取实体数据
            int entityCount = input.readInt();
            for (int i = 0; i < entityCount; i++) {
                int size = input.readInt();
                input.skipBytes(size);
            }

            // 转换为蓝图方块列表
            List<BlueprintBlock> blocks = new ArrayList<>();
            int index = 0;

            for (int y = 0; y < height; y++) {
                for (int z = 0; z < length; z++) {
                    for (int x = 0; x < width; x++) {
                        if (index < blockData.length) {
                            int blockId = blockData[index] & 0xFF;
                            String materialName = getMaterialName(blockId);

                            // 跳过空气方块
                            if (!materialName.equals("AIR")) {
                                blocks.add(new BlueprintBlock(
                                    x,
                                    y,
                                    z,
                                    materialName,
                                    (byte) 0,
                                    null,
                                    null
                                ));
                                blockCount++;
                            }
                            index++;
                        }
                    }
                }
            }

            // 创建蓝图
            String blueprintId = UUID.randomUUID().toString();
            String name = path.getFileName().toString().replaceAll("\\.(schematic|schem)$", "");
            BlueprintImpl blueprint = new BlueprintImpl(blueprintId, name, null, "Schematic Import", null, blocks);

            // 设置区域
            BlockVector3 min = new BlockVector3(0, 0, 0);
            BlockVector3 max = new BlockVector3(width - 1, height - 1, length - 1);
            CuboidRegion region = new CuboidRegion(worldName, min, max);
            blueprint.setRegion(region);

            // 保存蓝图
            Path savePath = blueprintDir.resolve(blueprintId + ".blueprint");
            blueprint.setDataFile(savePath);
            blueprint.save();
            blueprintCache.put(blueprintId, blueprint);

            plugin.getLogger().info("Imported schematic: " + name + " (" + blockCount + " blocks, " + width + "x" + height + "x" + length + ")");
            return Optional.of(blueprint);

        } catch (Exception e) {
            plugin.getLogger().warning("Failed to parse schematic: " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 读取NBT字符串
     */
    private String readString(java.io.DataInputStream input) throws java.io.IOException {
        int length = input.readInt();
        if (length < 0 || length > Short.MAX_VALUE) {
            return "";
        }
        byte[] bytes = new byte[length];
        input.readFully(bytes);
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * 根据方块ID获取材质名称
     */
    private String getMaterialName(int blockId) {
        // Minecraft 1.12-1.21 常见方块ID映射
        // 注意: 新版本使用 namespaced ID，这里做简化处理
        return switch (blockId) {
            case 0 -> "AIR";
            case 1 -> "STONE";
            case 2 -> "DIRT";
            case 3 -> "GRASS_BLOCK";
            case 4 -> "COBBLESTONE";
            case 5 -> "OAK_PLANKS";
            case 6 -> "OAK_LOG";
            case 7 -> "BEDROCK";
            case 8, 9 -> "WATER";
            case 10, 11 -> "LAVA";
            case 12 -> "SAND";
            case 13 -> "GRAVEL";
            case 14 -> "GOLD_ORE";
            case 15 -> "IRON_ORE";
            case 16 -> "COAL_ORE";
            case 17 -> "OAK_WOOD";
            case 18 -> "LEAVES";
            case 20 -> "GLASS";
            case 21 -> "LAPIS_ORE";
            case 22 -> "LAPIS_BLOCK";
            case 23 -> "DISPENSER";
            case 24 -> "SANDSTONE";
            case 25 -> "NOTEBLOCK";
            case 26 -> "OAK_FENCE";
            case 30 -> "COBWEB";
            case 35 -> "WHITE_WOOL";
            case 41 -> "GOLD_BLOCK";
            case 42 -> "IRON_BLOCK";
            case 43 -> "DOUBLE_STONE_SLAB";
            case 44 -> "STONE_SLAB";
            case 45 -> "BRICKS";
            case 46 -> "TNT";
            case 47 -> "BOOKSHELF";
            case 48 -> "MOSSY_COBBLESTONE";
            case 49 -> "OBSIDIAN";
            case 54 -> "CHEST";
            case 56 -> "DIAMOND_ORE";
            case 57 -> "DIAMOND_BLOCK";
            case 58 -> "CRAFTING_TABLE";
            case 60 -> "FARMLAND";
            case 64 -> "OAK_DOOR";
            case 65 -> "LADDER";
            case 67 -> "COBBLESTONE_STAIRS";
            case 71 -> "IRON_DOOR";
            case 73 -> "REDSTONE_ORE";
            case 79 -> "ICE";
            case 80 -> "SNOW_BLOCK";
            case 81 -> "CACTUS";
            case 82 -> "CLAY";
            case 84 -> "JUKEBOX";
            case 85 -> "OAK_FENCE";
            case 89 -> "GLOWSTONE";
            case 91 -> "OAK_FENCE_GATE";
            case 92 -> "WHITE_BED";
            case 95 -> "COLORED_GLASS";
            case 98 -> "STONE_BRICKS";
            case 102 -> "GLASS_PANE";
            case 103 -> "MELON";
            case 108 -> "BRICK_STAIRS";
            case 109 -> "STONE_BRICK_STAIRS";
            case 112 -> "NETHER_BRICKS";
            case 116 -> "ENCHANTING_TABLE";
            case 118 -> "BEACON";
            case 120 -> "END_PORTAL_FRAME";
            case 121 -> "END_STONE";
            case 122 -> "DRAGON_EGG";
            case 123 -> "REDSTONE_LAMP";
            case 130 -> "ENDER_CHEST";
            case 133 -> "EMERALD_BLOCK";
            case 137 -> "COMMAND_BLOCK";
            case 138 -> "BEACON";
            case 144 -> "MOB_SPAWNER";
            case 145 -> "ANVIL";
            case 146 -> "TRAPPED_CHEST";
            case 147 -> "GOLDEN_RAIL";
            case 152 -> "REDSTONE_BLOCK";
            case 153 -> "NETHER_QUARTZ_ORE";
            case 154 -> "HOPPER";
            case 155 -> "QUARTZ_BLOCK";
            case 156 -> "QUARTZ_STAIRS";
            case 157 -> "ACTIVATOR_RAIL";
            case 158 -> "DROPPER";
            case 170 -> "HAY_BALE";
            case 172 -> "TERRACOTTA";
            case 173 -> "COAL_BLOCK";
            case 174 -> "PACKED_ICE";
            case 179 -> "RED_SANDSTONE";
            case 180 -> "RED_SANDSTONE_STAIRS";
            case 181 -> "STONE_SLAB2";
            case 182 -> "SPRUCE_FENCE_GATE";
            case 183 -> "SPRUCE_FENCE";
            case 184 -> "BRICK_FENCE";
            case 185 -> "SPRUCE_DOOR";
            case 186 -> "END_ROD";
            case 187 -> "CHORUS_PLANT";
            case 188 -> "CHORUS_FLOWER";
            case 189 -> "PURPUR_BLOCK";
            case 190 -> "PURPUR_PILLAR";
            case 191 -> "PURPUR_STAIRS";
            case 192 -> "PURPUR_SLAB";
            case 198 -> "GRASS_PATH";
            case 199 -> "END_GATEWAY";
            case 201 -> "FROSTED_ICE";
            case 207 -> "BEETROOT";
            case 208 -> "GRASS_BLOCK";
            case 209 -> "MAGMA_BLOCK";
            case 210 -> "NETHER_WART";
            case 211 -> "RED_MUSHROOM_BLOCK";
            case 212 -> "BROWN_MUSHROOM_BLOCK";
            case 213 -> "MUSHROOM_STEM";
            case 214 -> "COCOA";
            case 215 -> "END_BRICKS";
            case 216 -> "COMMAND_BLOCK";
            case 218 -> "BEACON";
            case 219 -> "COMMAND_BLOCK";
            case 220 -> "COMMAND_BLOCK";
            case 221 -> "COMMAND_BLOCK";
            case 222 -> "BLUE_GLOWSTONE";
            case 223 -> "HOPPER_MINECART";
            case 224 -> "DRAGON_HEAD";
            case 225 -> "COMMAND_BLOCK";
            case 226 -> "CHAIN_COMMAND_BLOCK";
            case 227 -> "GLOWSTONE";
            case 228 -> "BARRIER";
            case 229 -> "IRON_TRAPDOOR";
            case 230 -> "PRISMARINE";
            case 231 -> "SEA_PICKLE";
            case 232 -> "DARK_PRISMARINE";
            case 233 -> "SOUL_SAND";
            case 234 -> "SOUL_SOIL";
            case 235 -> "LIBRARIAN_HUSK";
            case 236 -> "WARPED_FUNGUS";
            case 237 -> "CRIMSON_FUNGUS";
            case 238 -> "SHROOMLIGHT";
            case 239 -> "WEEPING_VINES";
            case 240 -> "TWISTING_VINES";
            case 241 -> "CRIMSON_ROOTS";
            case 242 -> "WARPED_ROOTS";
            case 243 -> "NETHER_SPROUTS";
            case 244 -> "SOUL_CAMPFIRE";
            case 245 -> "BASALT";
            case 246 -> "POLISHED_BASALT";
            case 247 -> "SOUL_TORCH";
            case 248 -> "CHAIN";
            case 249 -> "BLACKSTONE";
            case 250 -> "POLISHED_BLACKSTONE_BRICKS";
            case 251 -> "LEGACY_STONE";
            case 252 -> "SHULKER_BOX";
            default -> "STONE";
        };
    }

    @Override
    public boolean exportBlueprint(String blueprintId, String filePath, String format) {
        try {
            Optional<Blueprint> blueprintOpt = loadBlueprint(blueprintId);
            if (blueprintOpt.isEmpty()) {
                plugin.getLogger().warning("Blueprint not found: " + blueprintId);
                return false;
            }

            Blueprint blueprint = blueprintOpt.get();
            String lowerFormat = format.toLowerCase();

            return switch (lowerFormat) {
                case "schematic", "schem" -> exportSchematic(blueprint, filePath);
                case "blueprint", "bp" -> exportBlueprintFile(blueprint, filePath);
                default -> {
                    plugin.getLogger().warning("Unsupported export format: " + format);
                    yield false;
                }
            };
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to export blueprint: " + e.getMessage());
            return false;
        }
    }

    /**
     * 导出为内部格式
     */
    private boolean exportBlueprintFile(Blueprint blueprint, String filePath) {
        try {
            if (blueprint instanceof BlueprintImpl impl) {
                // 设置新的文件路径并保存
                Path path = Path.of(filePath);
                impl.setDataFile(path);
                impl.save();
                plugin.getLogger().info("Exported blueprint to: " + filePath);
                return true;
            }
            return false;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to export blueprint file: " + e.getMessage());
            return false;
        }
    }

    /**
     * 导出为schematic格式
     */
    private boolean exportSchematic(Blueprint blueprint, String filePath) {
        try (var output = new java.io.DataOutputStream(Files.newOutputStream(Path.of(filePath)))) {
            var region = blueprint.getRegion();
            int width = region.getWidth();
            int height = region.getHeight();
            int length = region.getDepth();

            // 写入schematic头部
            writeString(output, "Schematic");
            output.writeShort(2); // 版本2

            // 写入尺寸
            output.writeInt(width);
            output.writeInt(height);
            output.writeInt(length);

            // 写入偏移（设为0）
            output.writeInt(0);
            output.writeInt(0);
            output.writeInt(0);

            // 写入元数据
            Map<String, String> metadata = new java.util.HashMap<>();
            metadata.put("World", region.getWorldName() != null ? region.getWorldName() : "world");
            metadata.put("Author", blueprint.getAuthorName());
            metadata.put("Name", blueprint.getName());
            metadata.put("RequiredBy", "");

            output.writeInt(metadata.size());
            for (Map.Entry<String, String> entry : metadata.entrySet()) {
                writeString(output, entry.getKey());
                writeString(output, entry.getValue());
            }

            // 创建方块数据数组
            byte[] blockData = new byte[width * height * length];
            java.util.Arrays.fill(blockData, (byte) 0); // 默认为空气

            // 填充方块数据
            Map<String, Integer> materialToId = createMaterialToIdMap();
            int minX = region.getMinPoint().getX();
            int minY = region.getMinPoint().getY();
            int minZ = region.getMinPoint().getZ();

            for (BlueprintBlock block : blueprint.getBlocks()) {
                int relX = block.getX();
                int relY = block.getY();
                int relZ = block.getZ();

                if (relX >= 0 && relX < width && relY >= 0 && relY < height && relZ >= 0 && relZ < length) {
                    int index = relY * (width * length) + relZ * width + relX;
                    int blockId = materialToId.getOrDefault(block.getMaterialName().toUpperCase(), 0);
                    blockData[index] = (byte) blockId;
                }
            }

            // 写入方块数据
            output.write(blockData);

            // 写入方块状态数据（空）
            output.writeInt(0);

            // 写入方块实体数据（空）
            output.writeInt(0);

            // 写入实体数据（空）
            output.writeInt(0);

            plugin.getLogger().info("Exported schematic: " + blueprint.getName() + " to " + filePath);
            return true;

        } catch (Exception e) {
            plugin.getLogger().warning("Failed to export schematic: " + e.getMessage());
            return false;
        }
    }

    /**
     * 写入NBT字符串
     */
    private void writeString(java.io.DataOutputStream output, String str) throws java.io.IOException {
        byte[] bytes = str.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    /**
     * 创建材质名称到ID的映射
     */
    private Map<String, Integer> createMaterialToIdMap() {
        Map<String, Integer> map = new java.util.HashMap<>();
        // 填充常见的材质ID
        map.put("AIR", 0);
        map.put("STONE", 1);
        map.put("GRASS_BLOCK", 1); // 简化处理
        map.put("DIRT", 2);
        map.put("COBBLESTONE", 4);
        map.put("OAK_PLANKS", 5);
        map.put("OAK_LOG", 17);
        map.put("WATER", 9);
        map.put("LAVA", 11);
        map.put("SAND", 12);
        map.put("GRAVEL", 13);
        map.put("GOLD_ORE", 14);
        map.put("IRON_ORE", 15);
        map.put("COAL_ORE", 16);
        map.put("LEAVES", 18);
        map.put("GLASS", 20);
        map.put("LAPIS_ORE", 21);
        map.put("LAPIS_BLOCK", 22);
        map.put("SANDSTONE", 24);
        map.put("WHITE_WOOL", 35);
        map.put("GOLD_BLOCK", 41);
        map.put("IRON_BLOCK", 42);
        map.put("BRICKS", 45);
        map.put("MOSSY_COBBLESTONE", 48);
        map.put("OBSIDIAN", 49);
        map.put("CHEST", 54);
        map.put("DIAMOND_ORE", 56);
        map.put("DIAMOND_BLOCK", 57);
        map.put("CRAFTING_TABLE", 58);
        map.put("FARMLAND", 60);
        map.put("FURNACE", 61);
        map.put("LADDER", 65);
        map.put("COBBLESTONE_STAIRS", 67);
        map.put("REDSTONE_ORE", 73);
        map.put("GLOWSTONE", 89);
        map.put("STONE_BRICKS", 98);
        map.put("GLASS_PANE", 102);
        map.put("MELON", 103);
        map.put("NETHER_BRICKS", 112);
        map.put("ENCHANTING_TABLE", 116);
        map.put("END_PORTAL_FRAME", 120);
        map.put("END_STONE", 121);
        map.put("REDSTONE_LAMP", 123);
        map.put("EMERALD_BLOCK", 133);
        map.put("BEACON", 138);
        map.put("MOB_SPAWNER", 144);
        map.put("ANVIL", 145);
        map.put("GOLDEN_RAIL", 147);
        map.put("REDSTONE_BLOCK", 152);
        map.put("HOPPER", 154);
        map.put("QUARTZ_BLOCK", 155);
        map.put("DROPPER", 158);
        map.put("TERRACOTTA", 172);
        map.put("COAL_BLOCK", 173);
        map.put("RED_SANDSTONE", 179);
        map.put("PURPUR_BLOCK", 201);
        map.put("SHULKER_BOX", 233);
        map.put("BLACKSTONE", 249);
        map.put("BARRIER", 255);
        return map;
    }

    @Override
    public List<String> getSupportedImportFormats() {
        return List.of("schematic", "schem");
    }

    @Override
    public List<String> getSupportedExportFormats() {
        return List.of("blueprint", "schematic", "schem");
    }

    @Override
    public dev.starcore.starcore.module.blueprint.BlueprintTypes.ServiceStats getStats() {
        long totalSize = blueprintCache.values().stream()
            .mapToLong(Blueprint::getDataSize)
            .sum();

        return new dev.starcore.starcore.module.blueprint.BlueprintTypes.ServiceStats(
            blueprintCache.size(),
            categories.size(),
            blueprintIndex.size(),
            nationIndex.size(),
            totalSize
        );
    }

    /**
     * 关闭服务
     */
    public void shutdown() {
        // 保存所有蓝图
        for (Blueprint blueprint : blueprintCache.values()) {
            try {
                saveBlueprint(blueprint);
            } catch (BlueprintException e) {
                plugin.getLogger().warning("Failed to save blueprint on shutdown: " + e.getMessage());
            }
        }

        // 保存分类
        saveCategories();

        // 关闭所有会话
        EditSessionImpl.closeAll();

        // 清空缓存
        blueprintCache.clear();
        clipboards.clear();
    }
}
