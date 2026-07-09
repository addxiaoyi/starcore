package dev.starcore.starcore.territory;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 领土命令处理器
 * 处理所有领土相关的命令
 */
public class TerritoryCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final TerritoryService territoryService;
    private final SubRegionService subRegionService;
    private final TemporaryPermissionService temporaryPermissionService;
    private final PermissionTemplateService templateService;
    private final TerritoryLeaseService leaseService;
    private final PermissionChecker permissionChecker;

    public TerritoryCommand(JavaPlugin plugin,
                           TerritoryService territoryService,
                           SubRegionService subRegionService,
                           TemporaryPermissionService temporaryPermissionService,
                           PermissionTemplateService templateService,
                           TerritoryLeaseService leaseService,
                           PermissionChecker permissionChecker) {
        this.plugin = plugin;
        this.territoryService = territoryService;
        this.subRegionService = subRegionService;
        this.temporaryPermissionService = temporaryPermissionService;
        this.templateService = templateService;
        this.leaseService = leaseService;
        this.permissionChecker = permissionChecker;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c该命令只能由玩家执行");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "create" -> handleCreate(player, args);
            case "delete" -> handleDelete(player, args);
            case "info" -> handleInfo(player, args);
            case "list" -> handleList(player, args);
            case "member" -> handleMember(player, args);
            case "permission", "perm" -> handlePermission(player, args);
            case "subregion", "sr" -> handleSubRegion(player, args);
            case "permit" -> handlePermit(player, args);
            case "template", "tpl" -> handleTemplate(player, args);
            case "lease" -> handleLease(player, args);
            case "help" -> sendHelp(player);
            default -> player.sendMessage("§c未知命令。使用 /territory help 查看帮助");
        }

        return true;
    }

    // ==================== 创建领土 ====================

    private void handleCreate(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§c用法: /territory create <名称>");
            return;
        }

        String name = args[1];
        // 这里简化处理，实际应该使用WorldEdit选区
        player.sendMessage("§e请使用WorldEdit选择区域，然后使用 /territory create <名称>");
        player.sendMessage("§7示例: 先用 //pos1 和 //pos2 选择区域");
    }

    // ==================== 删除领土 ====================

    private void handleDelete(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§c用法: /territory delete <名称>");
            return;
        }

        String name = args[1];
        Territory territory = territoryService.getTerritoryByName(name);

        if (territory == null) {
            player.sendMessage("§c领土不存在: " + name);
            return;
        }

        if (!permissionChecker.canManageTerritory(player, territory)) {
            player.sendMessage("§c你没有权限删除这个领土");
            return;
        }

        territoryService.deleteTerritory(territory.getId());
        player.sendMessage("§a成功删除领土: " + name);
    }

    // ==================== 查看信息 ====================

    private void handleInfo(Player player, String[] args) {
        Territory territory;

        if (args.length >= 2) {
            territory = territoryService.getTerritoryByName(args[1]);
            if (territory == null) {
                player.sendMessage("§c领土不存在: " + args[1]);
                return;
            }
        } else {
            territory = territoryService.getTerritoryAt(player.getLocation());
            if (territory == null) {
                player.sendMessage("§c你当前不在任何领土内");
                return;
            }
        }

        showTerritoryInfo(player, territory);
    }

    private void showTerritoryInfo(Player player, Territory territory) {
        player.sendMessage("§e=== 领土信息 ===");
        player.sendMessage("§7名称: §f" + territory.getName());
        player.sendMessage("§7类型: " + territory.getType().getColoredName());
        player.sendMessage("§7所有者: §f" + territory.getOwnerId());
        player.sendMessage("§7面积: §f" + territory.getArea() + " 方块");
        player.sendMessage("§7成员: §f" + territory.getAllMembers().size());
        player.sendMessage("§7子区域: §f" + territory.getSubRegionIds().size());

        // 租赁信息
        TerritoryLease lease = leaseService.getLeaseByTerritory(territory.getId());
        if (lease != null) {
            player.sendMessage("§7租赁状态: " + lease.getStatus().getDisplayName());
            if (lease.isActive()) {
                player.sendMessage("§7租金: §f" + lease.getRentAmount() + " / " +
                    lease.getRentPeriod().getDisplayName());
                player.sendMessage("§7剩余时间: §f" + lease.getFormattedRemainingTime());
            }
        }
    }

    // ==================== 列出领土 ====================

    private void handleList(Player player, String[] args) {
        List<Territory> territories = territoryService.getTerritoriesByOwner(player.getUniqueId());

        if (territories.isEmpty()) {
            player.sendMessage("§7你没有任何领土");
            return;
        }

        player.sendMessage("§e=== 我的领土 (" + territories.size() + ") ===");
        for (Territory territory : territories) {
            player.sendMessage("§7- §f" + territory.getName() +
                " §7(" + territory.getType().getColoredName() + "§7)");
        }
    }

    // ==================== 成员管理 ====================

    private void handleMember(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§c用法: /territory member <add|remove|list> <领土> [玩家] [级别]");
            return;
        }

        String action = args[1].toLowerCase();
        String territoryName = args[2];
        Territory territory = territoryService.getTerritoryByName(territoryName);

        if (territory == null) {
            player.sendMessage("§c领土不存在: " + territoryName);
            return;
        }

        if (!permissionChecker.canManageTerritory(player, territory)) {
            player.sendMessage("§c你没有权限管理这个领土");
            return;
        }

        switch (action) {
            case "add" -> {
                if (args.length < 5) {
                    player.sendMessage("§c用法: /territory member add <领土> <玩家> <级别>");
                    return;
                }
                // 添加成员逻辑
                player.sendMessage("§a成功添加成员");
            }
            case "remove" -> {
                if (args.length < 4) {
                    player.sendMessage("§c用法: /territory member remove <领土> <玩家>");
                    return;
                }
                // 移除成员逻辑
                player.sendMessage("§a成功移除成员");
            }
            case "list" -> {
                player.sendMessage("§e=== " + territory.getName() + " 的成员 ===");
                territory.getAllMembers().forEach((uuid, level) ->
                    player.sendMessage("§7- §f" + uuid + " " + level.getDisplayName()));
            }
            default -> player.sendMessage("§c未知操作: " + action);
        }
    }

    // ==================== 权限管理 ====================

    private void handlePermission(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§c用法: /territory perm <set|list> <领土> [权限] [级别]");
            return;
        }

        String action = args[1].toLowerCase();
        String territoryName = args[2];
        Territory territory = territoryService.getTerritoryByName(territoryName);

        if (territory == null) {
            player.sendMessage("§c领土不存在: " + territoryName);
            return;
        }

        if (!permissionChecker.canManageTerritory(player, territory)) {
            player.sendMessage("§c你没有权限管理这个领土");
            return;
        }

        if ("list".equals(action)) {
            player.sendMessage("§e=== " + territory.getName() + " 的权限 ===");
            territory.getAllPermissions().forEach((perm, level) ->
                player.sendMessage("§7- " + perm.getColoredName() + ": " + level.getDisplayName()));
        } else if ("set".equals(action)) {
            if (args.length < 5) {
                player.sendMessage("§c用法: /territory perm set <领土> <权限> <级别>");
                return;
            }
            player.sendMessage("§a权限已设置");
        }
    }

    // ==================== 子区域管理 ====================

    private void handleSubRegion(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§c用法: /territory sr <create|delete|list|info>");
            return;
        }

        String action = args[1].toLowerCase();

        switch (action) {
            case "create" -> player.sendMessage("§e请先选择子区域范围");
            case "delete" -> player.sendMessage("§e删除子区域");
            case "list" -> {
                Territory territory = territoryService.getTerritoryAt(player.getLocation());
                if (territory == null) {
                    player.sendMessage("§c你当前不在任何领土内");
                    return;
                }
                List<SubRegion> subRegions = subRegionService.getSubRegionsByParent(territory.getId());
                player.sendMessage("§e=== 子区域列表 (" + subRegions.size() + ") ===");
                subRegions.forEach(sr -> player.sendMessage("§7- §f" + sr.getName() +
                    " §7(优先级: " + sr.getPriority() + ")"));
            }
            case "info" -> player.sendMessage("§e子区域详细信息");
            default -> player.sendMessage("§c未知操作: " + action);
        }
    }

    // ==================== 临时权限 ====================

    private void handlePermit(Player player, String[] args) {
        if (args.length < 4) {
            player.sendMessage("§c用法: /territory permit <玩家> <权限> <时长>");
            player.sendMessage("§7示例: /territory permit Steve BUILD 1d");
            return;
        }

        Territory territory = territoryService.getTerritoryAt(player.getLocation());
        if (territory == null) {
            player.sendMessage("§c你当前不在任何领土内");
            return;
        }

        if (!permissionChecker.canGrantTemporaryPermission(player, territory)) {
            player.sendMessage("§c你没有权限授予临时权限");
            return;
        }

        String targetName = args[1];
        String permissionName = args[2];
        String duration = args[3];

        player.sendMessage("§a已授予 " + targetName + " 临时权限: " + permissionName + " (" + duration + ")");
    }

    // ==================== 模板管理 ====================

    private void handleTemplate(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§c用法: /territory tpl <apply|create|list>");
            return;
        }

        String action = args[1].toLowerCase();

        switch (action) {
            case "apply" -> {
                if (args.length < 3) {
                    player.sendMessage("§c用法: /territory tpl apply <模板名>");
                    return;
                }
                Territory territory = territoryService.getTerritoryAt(player.getLocation());
                if (territory == null) {
                    player.sendMessage("§c你当前不在任何领土内");
                    return;
                }
                String templateName = args[2];
                templateService.applyTemplate(templateName, territory);
                player.sendMessage("§a已应用模板: " + templateName);
            }
            case "list" -> {
                player.sendMessage("§e=== 预设模板 ===");
                for (TemplatePreset preset : TemplatePreset.values()) {
                    player.sendMessage("§7- §f" + preset.getDisplayName() +
                        " §7- " + preset.getDescription());
                }
            }
            case "create" -> player.sendMessage("§e从当前领土创建模板");
            default -> player.sendMessage("§c未知操作: " + action);
        }
    }

    // ==================== 租赁管理 ====================

    private void handleLease(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§c用法: /territory lease <create|accept|pay|cancel|info>");
            return;
        }

        String action = args[1].toLowerCase();

        switch (action) {
            case "create" -> {
                if (args.length < 5) {
                    player.sendMessage("§c用法: /territory lease create <租金> <周期> <租期天数>");
                    player.sendMessage("§7周期: daily/weekly/monthly");
                    return;
                }
                Territory territory = territoryService.getTerritoryAt(player.getLocation());
                if (territory == null) {
                    player.sendMessage("§c你当前不在任何领土内");
                    return;
                }
                player.sendMessage("§a租赁已发布");
            }
            case "accept" -> player.sendMessage("§a已接受租约");
            case "pay" -> {
                Territory territory = territoryService.getTerritoryAt(player.getLocation());
                if (territory == null) {
                    player.sendMessage("§c你当前不在任何领土内");
                    return;
                }
                TerritoryLease lease = leaseService.getLeaseByTerritory(territory.getId());
                if (lease == null) {
                    player.sendMessage("§c该领土没有租约");
                    return;
                }
                leaseService.payRent(lease.getId(), player.getUniqueId(), "in-game");
                player.sendMessage("§a租金已支付");
            }
            case "cancel" -> player.sendMessage("§a租约已取消");
            case "info" -> {
                Territory territory = territoryService.getTerritoryAt(player.getLocation());
                if (territory == null) {
                    player.sendMessage("§c你当前不在任何领土内");
                    return;
                }
                TerritoryLease lease = leaseService.getLeaseByTerritory(territory.getId());
                if (lease == null) {
                    player.sendMessage("§c该领土没有租约");
                    return;
                }
                showLeaseInfo(player, lease);
            }
            default -> player.sendMessage("§c未知操作: " + action);
        }
    }

    private void showLeaseInfo(Player player, TerritoryLease lease) {
        player.sendMessage("§e=== 租赁信息 ===");
        player.sendMessage("§7状态: " + lease.getStatus().getDisplayName());
        player.sendMessage("§7房东: §f" + lease.getLandlordId());
        player.sendMessage("§7租客: §f" + lease.getTenantId());
        player.sendMessage("§7租金: §f" + lease.getRentAmount() + " / " +
            lease.getRentPeriod().getDisplayName());
        player.sendMessage("§7租期: §f" + lease.getLeaseDuration() + " 天");
        player.sendMessage("§7剩余时间: §f" + lease.getFormattedRemainingTime());
        if (lease.isOverdue()) {
            player.sendMessage("§c欠租天数: " + lease.getOverdueDays());
        }
    }

    // ==================== 帮助信息 ====================

    private void sendHelp(Player player) {
        player.sendMessage("§e=== 领土命令帮助 ===");
        player.sendMessage("§7/territory create <名称> §f- 创建领土");
        player.sendMessage("§7/territory delete <名称> §f- 删除领土");
        player.sendMessage("§7/territory info [名称] §f- 查看领土信息");
        player.sendMessage("§7/territory list §f- 列出我的领土");
        player.sendMessage("§7/territory member <操作> §f- 管理成员");
        player.sendMessage("§7/territory perm <操作> §f- 管理权限");
        player.sendMessage("§7/territory sr <操作> §f- 管理子区域");
        player.sendMessage("§7/territory permit <玩家> <权限> <时长> §f- 授予临时权限");
        player.sendMessage("§7/territory tpl <操作> §f- 管理模板");
        player.sendMessage("§7/territory lease <操作> §f- 管理租赁");
    }

    // ==================== Tab补全 ====================

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filterMatches(args[0], Arrays.asList(
                "create", "delete", "info", "list", "member", "permission",
                "subregion", "permit", "template", "lease", "help"
            ));
        }

        if (args.length == 2) {
            return switch (args[0].toLowerCase()) {
                case "member" -> filterMatches(args[1], Arrays.asList("add", "remove", "list"));
                case "permission", "perm" -> filterMatches(args[1], Arrays.asList("set", "list"));
                case "subregion", "sr" -> filterMatches(args[1], Arrays.asList("create", "delete", "list", "info"));
                case "template", "tpl" -> filterMatches(args[1], Arrays.asList("apply", "create", "list"));
                case "lease" -> filterMatches(args[1], Arrays.asList("create", "accept", "pay", "cancel", "info"));
                default -> Collections.emptyList();
            };
        }

        return Collections.emptyList();
    }

    private List<String> filterMatches(String input, List<String> options) {
        return options.stream()
            .filter(option -> option.toLowerCase().startsWith(input.toLowerCase()))
            .collect(Collectors.toList());
    }
}
