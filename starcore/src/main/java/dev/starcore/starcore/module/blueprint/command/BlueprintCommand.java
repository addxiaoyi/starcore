package dev.starcore.starcore.module.blueprint.command;

import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.blueprint.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 蓝图命令处理器
 * /blueprint <save|load|paste|list|delete|rotate|mirror|undo|redo|help>
 */
public class BlueprintCommand implements CommandExecutor, TabCompleter {
    private static final String PERMISSION_USE = "starcore.blueprint.use";
    private static final String PERMISSION_ADMIN = "starcore.blueprint.admin";

    private final BlueprintServiceImpl service;
    private final MessageService messages;
    private final JavaPlugin plugin;

    // 玩家选区缓存
    private final Map<UUID, RegionSelection> playerSelections = new ConcurrentHashMap<>();

    public BlueprintCommand(BlueprintServiceImpl service, MessageService messages, JavaPlugin plugin) {
        this.service = service;
        this.messages = messages;
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                            @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be used by players.", NamedTextColor.RED));
            return true;
        }

        if (!player.hasPermission(PERMISSION_USE)) {
            player.sendMessage(Component.text("You don't have permission to use blueprint commands.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            showHelp(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        try {
            switch (subCommand) {
                case "save", "s" -> handleSave(player, args);
                case "load", "l" -> handleLoad(player, args);
                case "paste", "p" -> handlePaste(player, args);
                case "list", "ls" -> handleList(player, args);
                case "delete", "del", "rm" -> handleDelete(player, args);
                case "rename" -> handleRename(player, args);
                case "copy", "cp" -> handleCopy(player, args);
                case "info" -> handleInfo(player, args);
                case "rotate", "r" -> handleRotate(player, args);
                case "mirror", "m" -> handleMirror(player, args);
                case "undo" -> handleUndo(player);
                case "redo" -> handleRedo(player);
                case "clipboard", "clip" -> handleClipboard(player, args);
                case "selection", "sel" -> handleSelection(player, args);
                case "category", "cat" -> handleCategory(player, args);
                case "search" -> handleSearch(player, args);
                case "import", "imp" -> handleImport(player, args);
                case "export", "exp" -> handleExport(player, args);
                case "help", "?" -> showHelp(player);
                case "stats" -> handleStats(player);
                default -> {
                    player.sendMessage(Component.text("Unknown command. Use /blueprint help for usage.", NamedTextColor.RED));
                }
            }
        } catch (BlueprintTypes.BlueprintException e) {
            player.sendMessage(Component.text("Error: " + e.getMessage(), NamedTextColor.RED));
        } catch (Exception e) {
            player.sendMessage(Component.text("An error occurred: " + e.getMessage(), NamedTextColor.RED));
            plugin.getLogger().warning("Blueprint command error: " + e.getMessage());
        }

        return true;
    }

    /**
     * 保存选区为蓝图
     * /blueprint save <name> [description]
     */
    private void handleSave(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /blueprint save <name> [description]", NamedTextColor.RED));
            return;
        }

        String name = args[1];
        String description = args.length > 2 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : "";

        // 获取玩家选区
        RegionSelection selection = getPlayerSelection(player);
        if (selection == null) {
            player.sendMessage(Component.text("You don't have a selection. Use the selection tool first.", NamedTextColor.RED));
            return;
        }

        if (selection.getBlockCount() == 0) {
            player.sendMessage(Component.text("Selection is empty.", NamedTextColor.RED));
            return;
        }

        // 创建蓝图
        Blueprint blueprint = service.createBlueprint(player, selection, name, description);

        player.sendMessage(Component.text()
            .append(Component.text("Blueprint created: ", NamedTextColor.GREEN))
            .append(Component.text(blueprint.getName(), NamedTextColor.AQUA))
            .append(Component.text(" (" + blueprint.getBlockCount() + " blocks)", NamedTextColor.GRAY)));
    }

    /**
     * 加载蓝图到剪贴板
     * /blueprint load <name|id>
     */
    private void handleLoad(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /blueprint load <name|id>", NamedTextColor.RED));
            return;
        }

        String identifier = args[1];

        // 尝试按名称或ID查找
        Optional<Blueprint> blueprint = service.findBlueprintByName(identifier);
        if (blueprint.isEmpty()) {
            blueprint = service.loadBlueprint(identifier);
        }

        if (blueprint.isEmpty()) {
            player.sendMessage(Component.text("Blueprint not found: " + identifier, NamedTextColor.RED));
            return;
        }

        Blueprint bp = blueprint.get();

        // 复制到剪贴板
        service.copyToClipboard(player, bp.getRegion());

        player.sendMessage(Component.text()
            .append(Component.text("Loaded blueprint: ", NamedTextColor.GREEN))
            .append(Component.text(bp.getName(), NamedTextColor.AQUA)));
    }

    /**
     * 粘贴蓝图
     * /blueprint paste [includeAir] [entities]
     */
    private void handlePaste(Player player, String[] args) {
        boolean includeAir = args.length > 1 && args[1].equalsIgnoreCase("true");
        boolean entities = args.length > 2 && args[2].equalsIgnoreCase("true");

        // 粘贴剪贴板内容
        var result = service.pasteClipboard(player, player.getLocation(), includeAir);

        if (result.success()) {
            player.sendMessage(Component.text()
                .append(Component.text("Pasted ", NamedTextColor.GREEN))
                .append(Component.text(result.blocksPlaced() + " blocks", NamedTextColor.AQUA))
                .append(Component.text(" in " + result.timeMs() + "ms", NamedTextColor.GRAY)));
        } else {
            player.sendMessage(Component.text("Paste failed: " + result.errorMessage(), NamedTextColor.RED));
        }
    }

    /**
     * 列出蓝图
     * /blueprint list [page|@player|@nation]
     */
    private void handleList(Player player, String[] args) {
        java.util.List<dev.starcore.starcore.module.blueprint.BlueprintTypes.BlueprintMetadata> blueprints;

        if (args.length > 1) {
            String filter = args[1];
            if (filter.startsWith("@")) {
                // 按玩家或国家过滤
                blueprints = new ArrayList<>();
            } else if (filter.matches("\\d+")) {
                // 分页显示
                blueprints = service.getPlayerBlueprints(player.getUniqueId());
            } else {
                blueprints = service.searchBlueprints(filter);
            }
        } else {
            blueprints = service.getPlayerBlueprints(player.getUniqueId());
        }

        if (blueprints.isEmpty()) {
            player.sendMessage(Component.text("No blueprints found.", NamedTextColor.YELLOW));
            return;
        }

        player.sendMessage(Component.text("=== Your Blueprints (" + blueprints.size() + ") ===", NamedTextColor.GOLD));

        for (dev.starcore.starcore.module.blueprint.BlueprintTypes.BlueprintMetadata bp : blueprints) {
            String time = formatTime(bp.modifiedTime());
            player.sendMessage(Component.text()
                .append(Component.text("[", NamedTextColor.DARK_GRAY))
                .append(Component.text(bp.id().substring(0, 8), NamedTextColor.GRAY))
                .append(Component.text("] ", NamedTextColor.DARK_GRAY))
                .append(Component.text(bp.name(), NamedTextColor.AQUA))
                .append(Component.text(" - " + time, NamedTextColor.GRAY)));
        }
    }

    /**
     * 删除蓝图
     * /blueprint delete <name|id>
     */
    private void handleDelete(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /blueprint delete <name|id>", NamedTextColor.RED));
            return;
        }

        String identifier = args[1];

        // 查找蓝图
        Optional<Blueprint> blueprint = service.findBlueprintByName(identifier);
        if (blueprint.isEmpty()) {
            blueprint = service.loadBlueprint(identifier);
        }

        if (blueprint.isEmpty()) {
            player.sendMessage(Component.text("Blueprint not found: " + identifier, NamedTextColor.RED));
            return;
        }

        Blueprint bp = blueprint.get();

        // 检查权限
        if (!bp.getAuthorId().equals(player.getUniqueId()) && !player.hasPermission(PERMISSION_ADMIN)) {
            player.sendMessage(Component.text("You can only delete your own blueprints.", NamedTextColor.RED));
            return;
        }

        if (service.deleteBlueprint(bp.getId())) {
            player.sendMessage(Component.text("Deleted blueprint: " + bp.getName(), NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("Failed to delete blueprint.", NamedTextColor.RED));
        }
    }

    /**
     * 重命名蓝图
     * /blueprint rename <oldName> <newName>
     */
    private void handleRename(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(Component.text("Usage: /blueprint rename <name|id> <newName>", NamedTextColor.RED));
            return;
        }

        String identifier = args[1];
        String newName = args[2];

        Optional<Blueprint> blueprint = service.findBlueprintByName(identifier);
        if (blueprint.isEmpty()) {
            blueprint = service.loadBlueprint(identifier);
        }

        if (blueprint.isEmpty()) {
            player.sendMessage(Component.text("Blueprint not found: " + identifier, NamedTextColor.RED));
            return;
        }

        if (service.renameBlueprint(blueprint.get().getId(), newName)) {
            player.sendMessage(Component.text("Renamed to: " + newName, NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("Failed to rename blueprint.", NamedTextColor.RED));
        }
    }

    /**
     * 复制蓝图
     * /blueprint copy <name|id> <newName>
     */
    private void handleCopy(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(Component.text("Usage: /blueprint copy <name|id> <newName>", NamedTextColor.RED));
            return;
        }

        String identifier = args[1];
        String newName = args[2];

        Optional<Blueprint> blueprint = service.findBlueprintByName(identifier);
        if (blueprint.isEmpty()) {
            blueprint = service.loadBlueprint(identifier);
        }

        if (blueprint.isEmpty()) {
            player.sendMessage(Component.text("Blueprint not found: " + identifier, NamedTextColor.RED));
            return;
        }

        try {
            Blueprint copy = service.copyBlueprint(blueprint.get().getId(), newName);
            player.sendMessage(Component.text("Copied blueprint as: " + copy.getName(), NamedTextColor.GREEN));
        } catch (BlueprintTypes.BlueprintException e) {
            player.sendMessage(Component.text("Copy failed: " + e.getMessage(), NamedTextColor.RED));
        }
    }

    /**
     * 查看蓝图信息
     * /blueprint info <name|id>
     */
    private void handleInfo(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /blueprint info <name|id>", NamedTextColor.RED));
            return;
        }

        String identifier = args[1];

        Optional<Blueprint> blueprint = service.findBlueprintByName(identifier);
        if (blueprint.isEmpty()) {
            blueprint = service.loadBlueprint(identifier);
        }

        if (blueprint.isEmpty()) {
            player.sendMessage(Component.text("Blueprint not found: " + identifier, NamedTextColor.RED));
            return;
        }

        Blueprint bp = blueprint.get();
        dev.starcore.starcore.module.blueprint.BlueprintTypes.BlueprintStats stats = bp.getStats();

        player.sendMessage(Component.text("=== Blueprint Info ===", NamedTextColor.GOLD));
        player.sendMessage(Component.text("Name: ", NamedTextColor.GRAY).append(Component.text(bp.getName(), NamedTextColor.AQUA)));
        player.sendMessage(Component.text("Author: ", NamedTextColor.GRAY).append(Component.text(bp.getAuthorName(), NamedTextColor.WHITE)));
        player.sendMessage(Component.text("Size: ", NamedTextColor.GRAY).append(Component.text(
            stats.width() + "x" + stats.height() + "x" + stats.depth(), NamedTextColor.WHITE)));
        player.sendMessage(Component.text("Blocks: ", NamedTextColor.GRAY).append(Component.text(
            stats.totalBlocks() + " (Solid: " + stats.solidBlocks() + ")", NamedTextColor.WHITE)));
        player.sendMessage(Component.text("Materials: ", NamedTextColor.GRAY).append(Component.text(
            stats.uniqueMaterials() + " unique", NamedTextColor.WHITE)));
        player.sendMessage(Component.text("Created: ", NamedTextColor.GRAY).append(Component.text(
            formatTime(bp.getCreatedTime()), NamedTextColor.WHITE)));

        if (bp.getDescription() != null && !bp.getDescription().isEmpty()) {
            player.sendMessage(Component.text("Description: ", NamedTextColor.GRAY).append(Component.text(
                bp.getDescription(), NamedTextColor.WHITE)));
        }
    }

    /**
     * 旋转蓝图
     * /blueprint rotate [90|180|270]
     */
    private void handleRotate(Player player, String[] args) {
        int degrees = args.length > 1 ? Integer.parseInt(args[1]) : 90;
        int times = degrees / 90;

        var clipboard = service.getClipboard(player.getUniqueId());
        if (clipboard.isEmpty()) {
            player.sendMessage(Component.text("No clipboard data. Use /blueprint load first.", NamedTextColor.RED));
            return;
        }

        if (clipboard.get() instanceof BlueprintClipboardImpl impl) {
            impl.rotate(times);
            player.sendMessage(Component.text("Rotated clipboard " + degrees + " degrees.", NamedTextColor.GREEN));
        }
    }

    /**
     * 镜像蓝图
     * /blueprint mirror <x|y|z|xz>
     */
    private void handleMirror(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /blueprint mirror <x|y|z|xz>", NamedTextColor.RED));
            return;
        }

        String axis = args[1];

        var clipboard = service.getClipboard(player.getUniqueId());
        if (clipboard.isEmpty()) {
            player.sendMessage(Component.text("No clipboard data. Use /blueprint load first.", NamedTextColor.RED));
            return;
        }

        if (clipboard.get() instanceof BlueprintClipboardImpl impl) {
            impl.mirror(axis);
            player.sendMessage(Component.text("Mirrored clipboard on " + axis + " axis.", NamedTextColor.GREEN));
        }
    }

    /**
     * 撤销
     * /blueprint undo
     */
    private void handleUndo(Player player) {
        if (service.undo(player.getUniqueId())) {
            player.sendMessage(Component.text("Undo successful.", NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("Nothing to undo.", NamedTextColor.YELLOW));
        }
    }

    /**
     * 重做
     * /blueprint redo
     */
    private void handleRedo(Player player) {
        if (service.redo(player.getUniqueId())) {
            player.sendMessage(Component.text("Redo successful.", NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("Nothing to redo.", NamedTextColor.YELLOW));
        }
    }

    /**
     * 剪贴板状态
     * /blueprint clipboard
     */
    private void handleClipboard(Player player, String[] args) {
        var clipboard = service.getClipboard(player.getUniqueId());

        if (clipboard.isEmpty()) {
            player.sendMessage(Component.text("No clipboard data.", NamedTextColor.YELLOW));
            return;
        }

        BlueprintClipboard clip = clipboard.get();

        player.sendMessage(Component.text("=== Clipboard ===", NamedTextColor.GOLD));
        player.sendMessage(Component.text("Blocks: ", NamedTextColor.GRAY).append(Component.text(
            clip.getBlockCount() + "", NamedTextColor.AQUA)));
        player.sendMessage(Component.text("Undo history: ", NamedTextColor.GRAY).append(Component.text(
            clip.getUndoStackSize() + "", NamedTextColor.WHITE)));
        player.sendMessage(Component.text("Redo history: ", NamedTextColor.GRAY).append(Component.text(
            clip.getRedoStackSize() + "", NamedTextColor.WHITE)));

        if (clip instanceof BlueprintClipboardImpl impl) {
            player.sendMessage(Component.text("Rotation: ", NamedTextColor.GRAY).append(Component.text(
                (impl.getRotation() * 90) + "°", NamedTextColor.WHITE)));
            player.sendMessage(Component.text("Mirror: ", NamedTextColor.GRAY).append(Component.text(
                impl.getMirrorAxis() != null ? impl.getMirrorAxis() : "None", NamedTextColor.WHITE)));
        }
    }

    /**
     * 设置选区
     * /blueprint selection <pos1|pos2|clear|info>
     */
    private void handleSelection(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /blueprint selection <pos1|pos2|clear|info>", NamedTextColor.RED));
            return;
        }

        String action = args[1].toLowerCase();

        switch (action) {
            case "pos1" -> {
                BlockVector3 pos = BlockVector3.fromLocation(player.getLocation());
                RegionSelection sel = playerSelections.get(player.getUniqueId());
                CuboidRegion region;
                if (sel instanceof CuboidRegion cr) {
                    region = new CuboidRegion(player.getWorld().getName(), pos, cr.getMaxPoint());
                } else {
                    region = new CuboidRegion(player.getWorld().getName(), pos, pos);
                }
                playerSelections.put(player.getUniqueId(), region);
                player.sendMessage(Component.text("Position 1 set to " + pos, NamedTextColor.GREEN));
            }
            case "pos2" -> {
                BlockVector3 pos = BlockVector3.fromLocation(player.getLocation());
                RegionSelection sel = playerSelections.get(player.getUniqueId());
                CuboidRegion region;
                if (sel instanceof CuboidRegion cr) {
                    region = new CuboidRegion(player.getWorld().getName(), cr.getMinPoint(), pos);
                } else {
                    region = new CuboidRegion(player.getWorld().getName(), pos, pos);
                }
                playerSelections.put(player.getUniqueId(), region);
                player.sendMessage(Component.text("Position 2 set to " + pos, NamedTextColor.GREEN));
            }
            case "clear" -> {
                playerSelections.remove(player.getUniqueId());
                player.sendMessage(Component.text("Selection cleared.", NamedTextColor.GREEN));
            }
            case "info" -> {
                RegionSelection selection = playerSelections.get(player.getUniqueId());
                if (selection == null) {
                    player.sendMessage(Component.text("No selection.", NamedTextColor.YELLOW));
                } else {
                    player.sendMessage(Component.text("Selection: " + selection.getType(), NamedTextColor.AQUA));
                    player.sendMessage(Component.text("Size: " + selection.getWidth() + "x" +
                        selection.getHeight() + "x" + selection.getDepth(), NamedTextColor.GRAY));
                    player.sendMessage(Component.text("Blocks: " + selection.getBlockCount(), NamedTextColor.GRAY));
                }
            }
        }
    }

    /**
     * 分类管理
     * /blueprint category <list|create|delete>
     */
    private void handleCategory(Player player, String[] args) {
        if (args.length < 2) {
            showCategoryHelp(player);
            return;
        }

        String action = args[1].toLowerCase();

        switch (action) {
            case "list", "ls" -> {
                var categories = service.getAllCategories();
                player.sendMessage(Component.text("=== Blueprint Categories ===", NamedTextColor.GOLD));
                for (BlueprintCategory cat : categories) {
                    player.sendMessage(Component.text()
                        .append(Component.text("[", NamedTextColor.DARK_GRAY))
                        .append(Component.text(cat.getId(), NamedTextColor.GRAY))
                        .append(Component.text("] ", NamedTextColor.DARK_GRAY))
                        .append(Component.text(cat.getName(), NamedTextColor.AQUA))
                        .append(Component.text(" (" + cat.getBlueprintCount() + " blueprints)", NamedTextColor.GRAY)));
                }
            }
            case "create" -> {
                if (args.length < 3) {
                    player.sendMessage(Component.text("Usage: /blueprint category create <name>", NamedTextColor.RED));
                    return;
                }
                String name = args[2];
                service.createCategory(name, "", "CHEST", "WHITE");
                player.sendMessage(Component.text("Category created: " + name, NamedTextColor.GREEN));
            }
        }
    }

    /**
     * 搜索蓝图
     * /blueprint search <query>
     */
    private void handleSearch(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /blueprint search <query>", NamedTextColor.RED));
            return;
        }

        String query = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        var results = service.searchBlueprints(query);

        if (results.isEmpty()) {
            player.sendMessage(Component.text("No blueprints found for: " + query, NamedTextColor.YELLOW));
            return;
        }

        player.sendMessage(Component.text("=== Search Results (" + results.size() + ") ===", NamedTextColor.GOLD));
        for (dev.starcore.starcore.module.blueprint.BlueprintTypes.BlueprintMetadata bp : results) {
            player.sendMessage(Component.text()
                .append(Component.text(bp.name(), NamedTextColor.AQUA))
                .append(Component.text(" by " + bp.authorName(), NamedTextColor.GRAY)));
        }
    }

    /**
     * 显示统计
     */
    private void handleStats(Player player) {
        var stats = service.getStats();

        player.sendMessage(Component.text("=== Blueprint Statistics ===", NamedTextColor.GOLD));
        player.sendMessage(Component.text("Total Blueprints: ", NamedTextColor.GRAY).append(Component.text(
            stats.totalBlueprints() + "", NamedTextColor.AQUA)));
        player.sendMessage(Component.text("Categories: ", NamedTextColor.GRAY).append(Component.text(
            stats.totalCategories() + "", NamedTextColor.AQUA)));
        player.sendMessage(Component.text("Player Blueprints: ", NamedTextColor.GRAY).append(Component.text(
            stats.playerBlueprints() + "", NamedTextColor.AQUA)));
        player.sendMessage(Component.text("Total Size: ", NamedTextColor.GRAY).append(Component.text(
            formatSize(stats.totalDataSize()), NamedTextColor.AQUA)));
    }

    /**
     * 获取玩家选区
     */
    private RegionSelection getPlayerSelection(Player player) {
        // 首先检查缓存的选区
        RegionSelection cached = playerSelections.get(player.getUniqueId());
        if (cached != null) {
            return cached;
        }

        // 尝试从 WorldEdit 获取选区
        RegionSelection weSelection = getWorldEditSelection(player);
        if (weSelection != null) {
            // 缓存 WorldEdit 选区
            playerSelections.put(player.getUniqueId(), weSelection);
            return weSelection;
        }

        // 尝试从其他支持的选区工具获取
        RegionSelection externalSelection = getExternalSelection(player);
        if (externalSelection != null) {
            playerSelections.put(player.getUniqueId(), externalSelection);
            return externalSelection;
        }

        return null;
    }

    /**
     * 从 WorldEdit 获取选区
     */
    private RegionSelection getWorldEditSelection(Player player) {
        try {
            // 尝试加载 WorldEdit 类
            Class<?> worldEditClass = Class.forName("com.sk89q.worldedit.WorldEdit");
            Class<?> sessionManagerClass = Class.forName("com.sk89q.worldedit.session.SessionManager");
            Class<?> localSessionClass = Class.forName("com.sk89q.worldedit.session.LocalSession");
            Class<?> localPlayerClass = Class.forName("com.sk89q.worldedit.LocalPlayer");
            Class<?> regionClass = Class.forName("com.sk89q.worldedit.regions.Region");

            // 获取 WorldEdit 实例
            Object worldEdit = worldEditClass.getMethod("getInstance").invoke(null);

            // 获取会话管理器
            Object sessionManager = worldEditClass.getMethod("getSessionManager").invoke(worldEdit);

            // 获取当前玩家的会话
            Object session = sessionManagerClass.getMethod("findByName", String.class)
                .invoke(sessionManager, player.getName());
            if (session == null) {
                return null;
            }

            // 获取选区
            Object selection = null;
            try {
                // 方法1: getSelection(LocalPlayer)
                Object localPlayer = worldEditClass.getMethod("getPlayer", org.bukkit.entity.Player.class)
                    .invoke(worldEdit, player);
                if (localPlayer != null) {
                    selection = localSessionClass.getMethod("getSelection", localPlayerClass)
                        .invoke(session, localPlayer);
                }
            } catch (NoSuchMethodException e) {
                // 方法2: 直接获取选区
                try {
                    selection = localSessionClass.getMethod("getSelection").invoke(session);
                } catch (NoSuchMethodException e2) {
                    // 方法3: getRegionSelector().getRegion()
                    Object localPlayerInstance = worldEditClass.getMethod("getPlayer", org.bukkit.entity.Player.class)
                        .invoke(worldEdit, player);
                    Object selector = localSessionClass.getMethod("getRegionSelector", localPlayerClass)
                        .invoke(session, localPlayerInstance);
                    if (selector != null) {
                        try {
                            selection = selector.getClass().getMethod("getRegion").invoke(selector);
                        } catch (NoSuchMethodException e3) {
                            plugin.getLogger().fine("Unsupported region selector type: " + selector.getClass().getName());
                        }
                    }
                }
            }

            if (selection == null || !regionClass.isInstance(selection)) {
                return null;
            }

            // 转换为 BlockVector3
            Class<?> vectorClass = Class.forName("com.sk89q.worldedit.math.BlockVector3");
            Object minPoint = regionClass.getMethod("getMinimumPoint").invoke(selection);
            Object maxPoint = regionClass.getMethod("getMaximumPoint").invoke(selection);

            int minX = ((Number) vectorClass.getMethod("getX").invoke(minPoint)).intValue();
            int minY = ((Number) vectorClass.getMethod("getY").invoke(minPoint)).intValue();
            int minZ = ((Number) vectorClass.getMethod("getZ").invoke(minPoint)).intValue();
            int maxX = ((Number) vectorClass.getMethod("getX").invoke(maxPoint)).intValue();
            int maxY = ((Number) vectorClass.getMethod("getY").invoke(maxPoint)).intValue();
            int maxZ = ((Number) vectorClass.getMethod("getZ").invoke(maxPoint)).intValue();

            BlockVector3 pos1 = BlockVector3.at(minX, minY, minZ);
            BlockVector3 pos2 = BlockVector3.at(maxX, maxY, maxZ);

            return new CuboidRegion(player.getWorld().getName(), pos1, pos2);

        } catch (ClassNotFoundException e) {
            // WorldEdit 未安装
            plugin.getLogger().fine("WorldEdit not found, using internal selection only.");
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to get WorldEdit selection: " + e.getMessage());
        }
        return null;
    }

    /**
     * 从其他外部选区工具获取选区（扩展点）
     */
    private RegionSelection getExternalSelection(Player player) {
        // 可以扩展支持其他选区工具，如：
        // - FastAsyncWorldEdit (FAWE)
        // - BentoBox
        // - GriefDefender 选区工具
        // - Residence 选区工具

        // 目前暂未实现其他工具集成
        // 如需添加，按如下模式扩展：
        // try {
        //     Class<?> faweClass = Class.forName("com.fastasyncworldedit.core.FAWE");
        //     // ... 集成代码
        // } catch (ClassNotFoundException ignored) {
        //     // 静默跳过，保持数据兼容
        // }

        return null;
    }

    /**
     * 导入蓝图
     * /blueprint import <file> [format]
     */
    private void handleImport(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /blueprint import <file> [format]", NamedTextColor.RED));
            player.sendMessage(Component.text("Supported formats: schematic, schem, blueprint", NamedTextColor.GRAY));
            return;
        }

        String filePath = args[1];
        String format = args.length > 2 ? args[2] : "schematic";

        try {
            var blueprint = service.importBlueprint(filePath, format);
            if (blueprint.isPresent()) {
                player.sendMessage(Component.text()
                    .append(Component.text("Imported blueprint: ", NamedTextColor.GREEN))
                    .append(Component.text(blueprint.get().getName(), NamedTextColor.AQUA)));
            } else {
                player.sendMessage(Component.text("Failed to import blueprint. Check console for details.", NamedTextColor.RED));
            }
        } catch (Exception e) {
            player.sendMessage(Component.text("Import error: " + e.getMessage(), NamedTextColor.RED));
        }
    }

    /**
     * 导出蓝图
     * /blueprint export <name|id> <file> [format]
     */
    private void handleExport(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(Component.text("Usage: /blueprint export <name|id> <file> [format]", NamedTextColor.RED));
            player.sendMessage(Component.text("Supported formats: schematic, schem, blueprint", NamedTextColor.GRAY));
            return;
        }

        String identifier = args[1];
        String filePath = args[2];
        String format = args.length > 3 ? args[3] : "schematic";

        // 查找蓝图
        Optional<Blueprint> blueprint = service.findBlueprintByName(identifier);
        if (blueprint.isEmpty()) {
            blueprint = service.loadBlueprint(identifier);
        }

        if (blueprint.isEmpty()) {
            player.sendMessage(Component.text("Blueprint not found: " + identifier, NamedTextColor.RED));
            return;
        }

        try {
            if (service.exportBlueprint(blueprint.get().getId(), filePath, format)) {
                player.sendMessage(Component.text()
                    .append(Component.text("Exported to: ", NamedTextColor.GREEN))
                    .append(Component.text(filePath, NamedTextColor.AQUA)));
            } else {
                player.sendMessage(Component.text("Failed to export blueprint.", NamedTextColor.RED));
            }
        } catch (Exception e) {
            player.sendMessage(Component.text("Export error: " + e.getMessage(), NamedTextColor.RED));
        }
    }

    /**
     * 显示帮助信息
     */
    private void showHelp(Player player) {
        player.sendMessage(Component.text("=== Blueprint Commands ===", NamedTextColor.GOLD));
        player.sendMessage(Component.text("/bp save <name> [desc]", NamedTextColor.AQUA).append(Component.text(" - Save selection as blueprint", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/bp load <name|id>", NamedTextColor.AQUA).append(Component.text(" - Load blueprint to clipboard", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/bp paste [air] [entities]", NamedTextColor.AQUA).append(Component.text(" - Paste clipboard", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/bp list [page]", NamedTextColor.AQUA).append(Component.text(" - List your blueprints", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/bp info <name|id>", NamedTextColor.AQUA).append(Component.text(" - View blueprint info", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/bp delete <name|id>", NamedTextColor.AQUA).append(Component.text(" - Delete blueprint", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/bp rename <old> <new>", NamedTextColor.AQUA).append(Component.text(" - Rename blueprint", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/bp copy <name> <newname>", NamedTextColor.AQUA).append(Component.text(" - Copy blueprint", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/bp rotate [90|180|270]", NamedTextColor.AQUA).append(Component.text(" - Rotate clipboard", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/bp mirror <x|y|z>", NamedTextColor.AQUA).append(Component.text(" - Mirror clipboard", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/bp undo", NamedTextColor.AQUA).append(Component.text(" - Undo last action", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/bp redo", NamedTextColor.AQUA).append(Component.text(" - Redo last undo", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/bp clipboard", NamedTextColor.AQUA).append(Component.text(" - Show clipboard status", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/bp sel <pos1|pos2|info|clear>", NamedTextColor.AQUA).append(Component.text(" - Manage selection", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/bp search <query>", NamedTextColor.AQUA).append(Component.text(" - Search blueprints", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/bp import <file> [format]", NamedTextColor.AQUA).append(Component.text(" - Import schematic", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/bp export <name> <file> [format]", NamedTextColor.AQUA).append(Component.text(" - Export schematic", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/bp cat list", NamedTextColor.AQUA).append(Component.text(" - List categories", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/bp stats", NamedTextColor.AQUA).append(Component.text(" - Show statistics", NamedTextColor.GRAY)));
    }

    private void showCategoryHelp(Player player) {
        player.sendMessage(Component.text("=== Category Commands ===", NamedTextColor.GOLD));
        player.sendMessage(Component.text("/bp cat list", NamedTextColor.AQUA).append(Component.text(" - List all categories", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/bp cat create <name>", NamedTextColor.AQUA).append(Component.text(" - Create new category", NamedTextColor.GRAY)));
    }

    /**
     * 格式化时间
     */
    private String formatTime(long timestamp) {
        long diff = System.currentTimeMillis() - timestamp;
        if (diff < 60000) {
            return "just now";
        } else if (diff < 3600000) {
            return (diff / 60000) + " minutes ago";
        } else if (diff < 86400000) {
            return (diff / 3600000) + " hours ago";
        } else {
            return (diff / 86400000) + " days ago";
        }
    }

    /**
     * 格式化大小
     */
    private String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                       @NotNull String alias, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return filterArgs(args[0], "save", "load", "paste", "list", "delete", "rename",
                "copy", "info", "rotate", "mirror", "undo", "redo", "clipboard", "selection",
                "category", "search", "stats", "help");
        }

        if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            return switch (subCommand) {
                case "load", "info", "delete", "rename", "copy" -> {
                    var blueprints = service.getPlayerBlueprints(player.getUniqueId());
                    yield filterArgs(args[1], blueprints.stream()
                        .map(dev.starcore.starcore.module.blueprint.BlueprintTypes.BlueprintMetadata::name)
                        .toArray(String[]::new));
                }
                case "rotate" -> filterArgs(args[1], "90", "180", "270");
                case "mirror" -> filterArgs(args[1], "x", "y", "z", "xz");
                case "category" -> filterArgs(args[1], "list", "create", "delete");
                default -> Collections.emptyList();
            };
        }

        return Collections.emptyList();
    }

    private List<String> filterArgs(String input, String... options) {
        String lower = input.toLowerCase();
        return Arrays.stream(options)
            .filter(opt -> opt.toLowerCase().startsWith(lower))
            .collect(Collectors.toList());
    }
}
