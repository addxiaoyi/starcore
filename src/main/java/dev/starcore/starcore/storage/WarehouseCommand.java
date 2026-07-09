package dev.starcore.starcore.storage;
import java.util.Optional;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 仓库命令处理器
 * 处理所有仓库相关的命令
 */
public class WarehouseCommand implements CommandExecutor, TabCompleter {
    private final StorageService storageService;

    /**
     * 构造函数
     */
    public WarehouseCommand(StorageService storageService) {
        this.storageService = storageService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c此命令只能由玩家执行");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "open" -> handleOpen(player, args);
            case "remote" -> handleRemote(player, args);
            case "upgrade" -> handleUpgrade(player, args);
            case "logs" -> handleLogs(player, args);
            case "share" -> handleShare(player, args);
            case "revoke" -> handleRevoke(player, args);
            case "list" -> handleList(player, args);
            case "info" -> handleInfo(player, args);
            case "create" -> handleCreate(player, args);
            case "delete" -> handleDelete(player, args);
            case "rename" -> handleRename(player, args);
            case "lock" -> handleLock(player, args);
            case "unlock" -> handleUnlock(player, args);
            default -> sendHelp(player);
        }

        return true;
    }

    /**
     * 打开仓库
     */
    private void handleOpen(Player player, String[] args) {
        Warehouse warehouse;

        if (args.length > 1) {
            // 打开指定ID的仓库
            try {
                UUID warehouseId = UUID.fromString(args[1]);
                Optional<Warehouse> warehouseOpt = storageService.getWarehouse(warehouseId);
                if (warehouseOpt.isEmpty()) {
                    player.sendMessage("§c仓库不存在");
                    return;
                }
                warehouse = warehouseOpt.get();
            } catch (IllegalArgumentException e) {
                player.sendMessage("§c无效的仓库ID");
                return;
            }
        } else {
            // 打开默认仓库
            warehouse = storageService.getOrCreatePlayerWarehouse(player.getUniqueId());
        }

        // 检查权限
        RemoteAccessPermission permission = storageService.getPermission(
                warehouse.getWarehouseId(),
                player.getUniqueId()
        );

        if (!permission.canView()) {
            player.sendMessage("§c您没有权限访问此仓库");
            return;
        }

        // 打开GUI
        WarehouseGUI gui = new WarehouseGUI(storageService, warehouse, player, false);
        gui.open();
    }

    /**
     * 远程访问
     */
    private void handleRemote(Player player, String[] args) {
        if (args.length < 2) {
            // 打开远程访问GUI
            RemoteAccessGUI gui = new RemoteAccessGUI(storageService, player);
            gui.open();
            return;
        }

        // 直接访问指定仓库
        try {
            UUID warehouseId = UUID.fromString(args[1]);
            if (storageService.getRemoteAccessService().executeRemoteAccess(player, warehouseId)) {
                Optional<Warehouse> warehouseOpt = storageService.getWarehouse(warehouseId);
                if (warehouseOpt.isPresent()) {
                    WarehouseGUI gui = new WarehouseGUI(storageService, warehouseOpt.get(), player, true);
                    gui.open();
                }
            }
        } catch (IllegalArgumentException e) {
            player.sendMessage("§c无效的仓库ID");
        }
    }

    /**
     * 升级仓库
     */
    private void handleUpgrade(Player player, String[] args) {
        Warehouse warehouse = storageService.getOrCreatePlayerWarehouse(player.getUniqueId());

        // 打开升级GUI
        WarehouseGUI gui = new WarehouseGUI(storageService, warehouse, player, false);
        gui.switchMode(WarehouseGUI.GUIMode.UPGRADE);
    }

    /**
     * 查看日志
     */
    private void handleLogs(Player player, String[] args) {
        Warehouse warehouse = storageService.getOrCreatePlayerWarehouse(player.getUniqueId());

        if (args.length > 1 && args[1].equalsIgnoreCase("export")) {
            // 导出日志
            String logs = storageService.getLogService().exportLogs(warehouse.getWarehouseId());
            player.sendMessage(logs);
        } else if (args.length > 1 && args[1].equalsIgnoreCase("summary")) {
            // 显示摘要
            int days = args.length > 2 ? parseInt(args[2], 7) : 7;
            String summary = storageService.getLogService().getLogSummary(warehouse.getWarehouseId(), days);
            player.sendMessage(summary);
        } else {
            // 打开日志GUI
            WarehouseGUI gui = new WarehouseGUI(storageService, warehouse, player, false);
            gui.switchMode(WarehouseGUI.GUIMode.LOGS);
        }
    }

    /**
     * 共享权限
     */
    private void handleShare(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§c用法: /warehouse share <玩家名> <权限>");
            player.sendMessage("§c权限: VIEW, WITHDRAW, DEPOSIT, FULL, ADMIN");
            return;
        }

        String targetName = args[1];
        String permissionName = args[2].toUpperCase();

        Player target = player.getServer().getPlayer(targetName);
        if (target == null) {
            player.sendMessage("§c玩家不在线");
            return;
        }

        RemoteAccessPermission permission;
        try {
            permission = RemoteAccessPermission.valueOf(permissionName);
        } catch (IllegalArgumentException e) {
            player.sendMessage("§c无效的权限类型");
            return;
        }

        Warehouse warehouse = storageService.getOrCreatePlayerWarehouse(player.getUniqueId());

        // 检查是否有管理权限
        RemoteAccessPermission senderPermission = storageService.getPermission(
                warehouse.getWarehouseId(),
                player.getUniqueId()
        );

        if (!senderPermission.canAdmin()) {
            player.sendMessage("§c您没有权限授予访问权限");
            return;
        }

        // 设置权限
        storageService.setPermission(warehouse.getWarehouseId(), target.getUniqueId(), permission);

        // 记录日志
        if (storageService.getConfig().isLogsEnabled()) {
            StorageLog log = StorageLog.createPermissionLog(
                    warehouse.getWarehouseId(),
                    player.getUniqueId(),
                    player.getName(),
                    true,
                    target.getName(),
                    permission.getDisplayName()
            );
            storageService.getLogService().addLog(log);
        }

        player.sendMessage("§a已授予 " + targetName + " " + permission.getDisplayName() + " 权限");
        target.sendMessage("§a" + player.getName() + " 授予了您访问其仓库的权限");
    }

    /**
     * 撤销权限
     */
    private void handleRevoke(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§c用法: /warehouse revoke <玩家名>");
            return;
        }

        String targetName = args[1];
        Player target = player.getServer().getPlayer(targetName);
        if (target == null) {
            player.sendMessage("§c玩家不在线");
            return;
        }

        Warehouse warehouse = storageService.getOrCreatePlayerWarehouse(player.getUniqueId());

        // 检查权限
        RemoteAccessPermission senderPermission = storageService.getPermission(
                warehouse.getWarehouseId(),
                player.getUniqueId()
        );

        if (!senderPermission.canAdmin()) {
            player.sendMessage("§c您没有权限撤销访问权限");
            return;
        }

        // 移除权限
        storageService.removePermission(warehouse.getWarehouseId(), target.getUniqueId());

        // 记录日志
        if (storageService.getConfig().isLogsEnabled()) {
            StorageLog log = StorageLog.createPermissionLog(
                    warehouse.getWarehouseId(),
                    player.getUniqueId(),
                    player.getName(),
                    false,
                    target.getName(),
                    "NONE"
            );
            storageService.getLogService().addLog(log);
        }

        player.sendMessage("§a已撤销 " + targetName + " 的访问权限");
    }

    /**
     * 列出仓库
     */
    private void handleList(Player player, String[] args) {
        List<Warehouse> warehouses;

        if (args.length > 1 && args[1].equalsIgnoreCase("all")) {
            // 列出所有可访问的仓库
            warehouses = storageService.getAccessibleWarehouses(player.getUniqueId());
        } else {
            // 列出拥有的仓库
            warehouses = storageService.getPlayerWarehouses(player.getUniqueId());
        }

        if (warehouses.isEmpty()) {
            player.sendMessage("§c您没有任何仓库");
            return;
        }

        player.sendMessage("§6=== 仓库列表 ===");
        for (Warehouse warehouse : warehouses) {
            RemoteAccessPermission permission = storageService.getPermission(
                    warehouse.getWarehouseId(),
                    player.getUniqueId()
            );

            player.sendMessage(String.format("§7- §f%s §7[Lv.%d] §7(%s) §7- %s",
                    warehouse.getName(),
                    warehouse.getLevel(),
                    warehouse.getType().getDisplayName(),
                    permission.getDisplayName()
            ));
        }
    }

    /**
     * 查看仓库信息
     */
    private void handleInfo(Player player, String[] args) {
        Warehouse warehouse = storageService.getOrCreatePlayerWarehouse(player.getUniqueId());

        player.sendMessage("§6=== 仓库信息 ===");
        player.sendMessage("§7名称: §f" + warehouse.getName());
        player.sendMessage("§7类型: §f" + warehouse.getType().getDisplayName());
        player.sendMessage("§7等级: §f" + warehouse.getLevel() + "/" + warehouse.getType().getMaxLevel());
        player.sendMessage("§7容量: §f" + warehouse.getUsedCapacity() + "/" + warehouse.getCapacity());
        player.sendMessage("§7使用率: §f" + String.format("%.1f%%", warehouse.getUsagePercentage() * 100));
        player.sendMessage("§7创建时间: §f" + warehouse.getCreatedTime());
        player.sendMessage("§7最后访问: §f" + warehouse.getLastAccessTime());

        if (warehouse.isLocked()) {
            player.sendMessage("§c状态: 已锁定");
        }
    }

    /**
     * 创建仓库
     */
    private void handleCreate(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§c用法: /warehouse create <类型> <名称>");
            player.sendMessage("§c类型: PERSONAL, SHARED, PREMIUM");
            return;
        }

        String typeName = args[1].toUpperCase();
        String name = String.join(" ", Arrays.copyOfRange(args, 2, args.length));

        WarehouseType type;
        try {
            type = WarehouseType.valueOf(typeName);
        } catch (IllegalArgumentException e) {
            player.sendMessage("§c无效的仓库类型");
            return;
        }

        Warehouse warehouse = storageService.createWarehouse(type, player.getUniqueId(), name);
        player.sendMessage("§a已创建仓库: " + warehouse.getName());
        player.sendMessage("§7ID: §f" + warehouse.getWarehouseId());
    }

    /**
     * 删除仓库
     */
    private void handleDelete(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§c用法: /warehouse delete <仓库ID>");
            return;
        }

        try {
            UUID warehouseId = UUID.fromString(args[1]);
            Optional<Warehouse> warehouseOpt = storageService.getWarehouse(warehouseId);

            if (warehouseOpt.isEmpty()) {
                player.sendMessage("§c仓库不存在");
                return;
            }

            Warehouse warehouse = warehouseOpt.get();

            // 检查所有权
            if (!warehouse.getOwnerId().equals(player.getUniqueId())) {
                player.sendMessage("§c您不是此仓库的所有者");
                return;
            }

            if (storageService.deleteWarehouse(warehouseId)) {
                player.sendMessage("§a已删除仓库");
            } else {
                player.sendMessage("§c删除失败");
            }
        } catch (IllegalArgumentException e) {
            player.sendMessage("§c无效的仓库ID");
        }
    }

    /**
     * 重命名仓库
     */
    private void handleRename(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§c用法: /warehouse rename <新名称>");
            return;
        }

        String newName = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        Warehouse warehouse = storageService.getOrCreatePlayerWarehouse(player.getUniqueId());

        warehouse.setName(newName);
        player.sendMessage("§a已重命名仓库为: " + newName);
    }

    /**
     * 锁定仓库
     */
    private void handleLock(Player player, String[] args) {
        Warehouse warehouse = storageService.getOrCreatePlayerWarehouse(player.getUniqueId());

        if (warehouse.isLocked()) {
            player.sendMessage("§c仓库已经是锁定状态");
            return;
        }

        warehouse.setLocked(true);
        player.sendMessage("§a已锁定仓库");
    }

    /**
     * 解锁仓库
     */
    private void handleUnlock(Player player, String[] args) {
        Warehouse warehouse = storageService.getOrCreatePlayerWarehouse(player.getUniqueId());

        if (!warehouse.isLocked()) {
            player.sendMessage("§c仓库已经是解锁状态");
            return;
        }

        warehouse.setLocked(false);
        player.sendMessage("§a已解锁仓库");
    }

    /**
     * 发送帮助信息
     */
    private void sendHelp(Player player) {
        player.sendMessage("§6=== 仓库系统命令 ===");
        player.sendMessage("§e/warehouse open §7- 打开您的仓库");
        player.sendMessage("§e/warehouse remote §7- 远程访问仓库");
        player.sendMessage("§e/warehouse upgrade §7- 升级仓库");
        player.sendMessage("§e/warehouse logs §7- 查看操作日志");
        player.sendMessage("§e/warehouse share <玩家> <权限> §7- 共享仓库");
        player.sendMessage("§e/warehouse revoke <玩家> §7- 撤销权限");
        player.sendMessage("§e/warehouse list §7- 列出您的仓库");
        player.sendMessage("§e/warehouse info §7- 查看仓库信息");
        player.sendMessage("§e/warehouse rename <名称> §7- 重命名仓库");
        player.sendMessage("§e/warehouse lock/unlock §7- 锁定/解锁仓库");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filterMatches(args[0], "open", "remote", "upgrade", "logs", "share",
                    "revoke", "list", "info", "create", "delete", "rename", "lock", "unlock");
        }

        if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            if (subCommand.equals("share") || subCommand.equals("revoke")) {
                return null; // 返回在线玩家列表
            }
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("share")) {
            return filterMatches(args[2], "VIEW", "WITHDRAW", "DEPOSIT", "FULL", "ADMIN");
        }

        return Collections.emptyList();
    }

    /**
     * 过滤匹配的补全
     */
    private List<String> filterMatches(String input, String... options) {
        String lowerInput = input.toLowerCase();
        return Arrays.stream(options)
                .filter(option -> option.toLowerCase().startsWith(lowerInput))
                .collect(Collectors.toList());
    }

    /**
     * 解析整数
     */
    private int parseInt(String str, int defaultValue) {
        try {
            return Integer.parseInt(str);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
