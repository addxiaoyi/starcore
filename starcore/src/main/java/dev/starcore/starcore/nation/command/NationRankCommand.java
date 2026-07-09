package dev.starcore.starcore.nation.command;

import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.nation.permission.NationPermission;
import dev.starcore.starcore.nation.permission.NationPermissionChecker;
import dev.starcore.starcore.nation.rank.NationRank;
import dev.starcore.starcore.nation.rank.NationRankManager;
import dev.starcore.starcore.util.PermissionUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Nation Rank命令 - Bukkit原生实现
 * 命令：/nrank <子命令>
 */
public class NationRankCommand implements CommandExecutor, TabCompleter {

    private final NationService nationService;
    private final NationRankManager rankManager;
    private final NationPermissionChecker permissionChecker;

    public NationRankCommand(NationService nationService, NationRankManager rankManager, NationPermissionChecker permissionChecker) {
        this.nationService = nationService;
        this.rankManager = rankManager;
        this.permissionChecker = permissionChecker;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家可以使用此命令");
            return true;
        }

        if (args.length == 0) {
            return handleList(player);
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "list" -> {
                return handleList(player);
            }
            case "info" -> {
                if (args.length < 2) {
                    player.sendMessage("§c用法: /nrank info <职位名>");
                    return true;
                }
                return handleInfo(player, args[1]);
            }
            case "assign" -> {
                if (args.length < 3) {
                    player.sendMessage("§c用法: /nrank assign <玩家> <职位>");
                    return true;
                }
                // 支持离线玩家：先用 getPlayer，回退到 getOfflinePlayer（审计 A-022）
                Player target = Bukkit.getPlayer(args[1]);
                if (target != null) {
                    return handleAssign(player, target, args[2]);
                }
                org.bukkit.OfflinePlayer offlineTarget = Bukkit.getOfflinePlayer(args[1]);
                if (offlineTarget == null || offlineTarget.getUniqueId() == null) {
                    player.sendMessage("§c找不到玩家: §e" + args[1]);
                    return true;
                }
                player.sendMessage("§7目标玩家当前离线，将记录职位到 UUID");
                return handleAssignOffline(player, offlineTarget, args[2]);
            }
            case "remove" -> {
                if (args.length < 2) {
                    player.sendMessage("§c用法: /nrank remove <玩家>");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target != null) {
                    return handleRemove(player, target);
                }
                org.bukkit.OfflinePlayer offlineTarget = Bukkit.getOfflinePlayer(args[1]);
                if (offlineTarget == null || offlineTarget.getUniqueId() == null) {
                    player.sendMessage("§c找不到玩家: §e" + args[1]);
                    return true;
                }
                player.sendMessage("§7目标玩家当前离线，按 UUID 移除职位");
                return handleRemoveOffline(player, offlineTarget);
            }
            case "check" -> {
                Player target = args.length >= 2 ? Bukkit.getPlayer(args[1]) : player;
                if (target == null) {
                    player.sendMessage("§c玩家不在线");
                    return true;
                }
                return handleCheck(player, target);
            }
            case "test" -> {
                if (args.length < 2) {
                    player.sendMessage("§c用法: /nrank test <权限>");
                    return true;
                }
                return handleTest(player, args[1]);
            }
            case "reload" -> {
                // TODO audit A-023: 显式在 plugin.yml 注册 starcore.admin (default: op)，避免默认 op-only 模糊
                if (!player.hasPermission("starcore.admin")) {
                    player.sendMessage("§c你没有权限");
                    return true;
                }
                return handleReload(player);
            }
            case "stats" -> {
                return handleStats(player);
            }
            default -> {
                player.sendMessage("§c未知子命令: " + subCommand);
                player.sendMessage("§7可用命令: list, info, assign, remove, check, test, reload, stats");
                return true;
            }
        }
    }

    private boolean handleList(Player player) {
        player.sendMessage("§6§l==== Nation职位列表 ====");
        for (NationRank rank : rankManager.getAllGlobalRanks()) {
            player.sendMessage(String.format(
                "§e%s §7- §f%d个权限 §7(优先级: %d)",
                rank.getDisplayName(),
                rank.getPermissionCount(),
                rank.getPriority()
            ));
        }
        player.sendMessage("§7使用 /nrank info <职位名> 查看详情");
        return true;
    }

    private boolean handleInfo(Player player, String rankName) {
        NationRank rank = rankManager.getGlobalRank(rankName);
        if (rank == null) {
            player.sendMessage("§c找不到职位: §e" + rankName);
            return true;
        }

        player.sendMessage("§6§l==== " + rank.getDisplayName() + " ====");
        player.sendMessage("§7职位名称: §e" + rank.getName());
        player.sendMessage("§7优先级: §e" + rank.getPriority());
        player.sendMessage("§7权限数量: §e" + rank.getPermissionCount());
        // 分隔
        player.sendMessage("§6权限列表:");

        var permsByCategory = rank.getPermissionsByCategory();
        for (var entry : permsByCategory.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                player.sendMessage("§e" + entry.getKey() + ":");
                for (NationPermission perm : entry.getValue()) {
                    player.sendMessage("  §7- §f" + perm.getDescription());
                }
            }
        }
        return true;
    }

    private boolean handleAssign(Player admin, Player target, String rankName) {
        // 权限校验：必须与目标玩家同属一个国家，且在该国拥有 RANK_ASSIGN 权限（审计 A-019 修复）
        var adminNation = nationService.nationOf(admin.getUniqueId());
        var targetNation = nationService.nationOf(target.getUniqueId());
        if (adminNation.isEmpty() || targetNation.isEmpty()
                || !adminNation.get().id().equals(targetNation.get().id())) {
            admin.sendMessage("§c你和目标玩家必须在同一个国家");
            return true;
        }
        if (!PermissionUtil.hasNationPermission(admin, targetNation.get().id().value(),
                NationPermission.RANK_ASSIGN, null)) {
            admin.sendMessage("§c你没有分配职位的权限");
            return true;
        }

        NationRank rank = rankManager.getGlobalRank(rankName);
        if (rank == null) {
            admin.sendMessage("§c找不到职位: §e" + rankName);
            return true;
        }

        rankManager.assignRank(target.getUniqueId(), rankName);
        admin.sendMessage(String.format(
            "§a已将 §e%s §a的职位设置为 %s",
            target.getName(),
            rank.getDisplayName()
        ));
        target.sendMessage("§a你的职位已被设置为 " + rank.getDisplayName());
        return true;
    }

    private boolean handleRemove(Player admin, Player target) {
        // 权限校验：必须与目标玩家同属一个国家，且在该国拥有 RANK_ASSIGN 权限（审计 A-020 修复）
        var adminNation = nationService.nationOf(admin.getUniqueId());
        var targetNation = nationService.nationOf(target.getUniqueId());
        if (adminNation.isEmpty() || targetNation.isEmpty()
                || !adminNation.get().id().equals(targetNation.get().id())) {
            admin.sendMessage("§c你和目标玩家必须在同一个国家");
            return true;
        }
        if (!PermissionUtil.hasNationPermission(admin, targetNation.get().id().value(),
                NationPermission.RANK_ASSIGN, null)) {
            admin.sendMessage("§c你没有移除职位的权限");
            return true;
        }

        String oldRankName = rankManager.getPlayerRankName(target.getUniqueId());
        if (oldRankName == null) {
            admin.sendMessage("§c该玩家没有职位");
            return true;
        }

        rankManager.removeRank(target.getUniqueId());
        admin.sendMessage("§a已移除 §e" + target.getName() + " §a的职位");
        target.sendMessage("§c你的职位已被移除");
        return true;
    }

    /** 离线玩家版本的 assign（审计 A-022） */
    private boolean handleAssignOffline(Player admin, org.bukkit.OfflinePlayer target, String rankName) {
        // 权限校验：必须与目标玩家同属一个国家
        var adminNation = nationService.nationOf(admin.getUniqueId());
        var targetNation = nationService.nationOf(target.getUniqueId());
        if (adminNation.isEmpty() || targetNation.isEmpty()
                || !adminNation.get().id().equals(targetNation.get().id())) {
            admin.sendMessage("§c你和目标玩家必须在同一个国家");
            return true;
        }
        if (!PermissionUtil.hasNationPermission(admin, targetNation.get().id().value(),
                NationPermission.RANK_ASSIGN, null)) {
            admin.sendMessage("§c你没有分配职位的权限");
            return true;
        }
        NationRank rank = rankManager.getGlobalRank(rankName);
        if (rank == null) {
            admin.sendMessage("§c找不到职位: §e" + rankName);
            return true;
        }
        rankManager.assignRank(target.getUniqueId(), rankName);
        admin.sendMessage(String.format("§a已将离线玩家 §e%s §a的职位设置为 %s", target.getName(), rank.getDisplayName()));
        return true;
    }

    /** 离线玩家版本的 remove（审计 A-022） */
    private boolean handleRemoveOffline(Player admin, org.bukkit.OfflinePlayer target) {
        var adminNation = nationService.nationOf(admin.getUniqueId());
        var targetNation = nationService.nationOf(target.getUniqueId());
        if (adminNation.isEmpty() || targetNation.isEmpty()
                || !adminNation.get().id().equals(targetNation.get().id())) {
            admin.sendMessage("§c你和目标玩家必须在同一个国家");
            return true;
        }
        if (!PermissionUtil.hasNationPermission(admin, targetNation.get().id().value(),
                NationPermission.RANK_ASSIGN, null)) {
            admin.sendMessage("§c你没有移除职位的权限");
            return true;
        }
        String oldRankName = rankManager.getPlayerRankName(target.getUniqueId());
        if (oldRankName == null) {
            admin.sendMessage("§c该玩家没有职位");
            return true;
        }
        rankManager.removeRank(target.getUniqueId());
        admin.sendMessage("§a已移除离线玩家 §e" + target.getName() + " §a的职位");
        return true;
    }

    private boolean handleCheck(Player player, Player target) {
        String rankName = rankManager.getPlayerRankName(target.getUniqueId());
        if (rankName == null) {
            player.sendMessage("§e" + target.getName() + " §7没有职位");
            return true;
        }

        NationRank rank = rankManager.getGlobalRank(rankName);
        if (rank == null) {
            player.sendMessage("§e" + target.getName() + " §7的职位: §c" + rankName + " (未找到)");
            return true;
        }

        player.sendMessage(String.format(
            "§e%s §7的职位: %s §7(%d个权限)",
            target.getName(),
            rank.getDisplayName(),
            rank.getPermissionCount()
        ));
        return true;
    }

    private boolean handleTest(Player player, String permissionKey) {
        NationPermission permission;
        try {
            permission = NationPermission.valueOf(permissionKey.toUpperCase().replace(".", "_"));
        } catch (IllegalArgumentException e) {
            player.sendMessage("§c无效的权限: §e" + permissionKey);
            return true;
        }

        // 离线校验：用玩家当前所属国家
        var nationOpt = nationService.nationOf(player.getUniqueId());
        NationId testNationId;
        if (nationOpt.isPresent()) {
            testNationId = nationOpt.get().id();
        } else {
            // 无国家：无法测试，提示玩家先加入国家
            player.sendMessage("§c你目前不在任何国家，无法测试国家权限");
            return true;
        }

        NationRank rank = rankManager.getPlayerRank(player.getUniqueId(), testNationId.value());

        boolean hasPermission = PermissionUtil.hasNationPermission(player, testNationId.value(), permission, rank);

        player.sendMessage("§6§l==== 权限测试结果 ====");
        player.sendMessage("§7权限: §e" + permission.getDescription());
        player.sendMessage("§7需求层级: §e" + permission.getDefaultLevel().getDisplayName());
        player.sendMessage("§7你所在国家: §e" + nationOpt.get().name());
        player.sendMessage("§7你的职位: §e" + (rank != null ? rank.getDisplayName() : "无"));
        // 分隔
        player.sendMessage(hasPermission ? "§a✓ 你拥有此权限" : "§c✗ 你没有此权限");
        return true;
    }

    private boolean handleReload(Player player) {
        rankManager.clear();
        rankManager.initDefaultRanks();
        player.sendMessage("§a职位系统已重载！");
        player.sendMessage("§7当前职位数量: §e" + rankManager.getAllGlobalRanks().size());
        return true;
    }

    private boolean handleStats(Player player) {
        var stats = rankManager.getStats();
        player.sendMessage("§6§l==== 职位系统统计 ====");
        player.sendMessage("§7全局职位: §e" + stats.globalRanks());
        player.sendMessage("§7有自定义职位的Nation: §e" + stats.nationsWithCustomRanks());
        player.sendMessage("§7自定义职位总数: §e" + stats.totalCustomRanks());
        player.sendMessage("§7玩家职位分配: §e" + stats.playerAssignments());
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("list", "info", "assign", "remove", "check", "test", "reload", "stats")
                .stream()
                .filter(s -> s.startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }

        if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "info", "assign" -> {
                    return rankManager.getAllGlobalRanks().stream()
                        .map(NationRank::getName)
                        .filter(s -> s.startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
                }
                case "remove", "check" -> {
                    return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
                }
            }
        }

        return Collections.emptyList();
    }
}
