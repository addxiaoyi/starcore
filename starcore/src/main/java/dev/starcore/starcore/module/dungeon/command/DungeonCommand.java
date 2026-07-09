package dev.starcore.starcore.module.dungeon.command;

import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.dungeon.*;
import dev.starcore.starcore.module.dungeon.gui.DungeonGui;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 副本命令
 */
public class DungeonCommand implements CommandExecutor, TabCompleter {
    private final DungeonServiceImpl service;
    private final MessageService messages;
    private final JavaPlugin plugin;

    public DungeonCommand(DungeonServiceImpl service, MessageService messages, JavaPlugin plugin) {
        this.service = service;
        this.messages = messages;
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "list" -> handleList(sender);
            case "info" -> handleInfo(sender, args);
            case "join" -> handleEnter(sender, args); // join 是 enter 的别名
            case "leave" -> handleLeave(sender);
            case "enter" -> handleEnter(sender, args);
            case "history" -> handleHistory(sender);
            case "stats" -> handleStats(sender);
            case "gui" -> handleGui(sender);
            case "reload" -> handleReload(sender);
            case "admin" -> handleAdmin(sender, args);
            default -> sendHelp(sender);
        }

        return true;
    }

    /**
     * 发送帮助信息
     */
    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6========== 副本系统 ==========");
        sender.sendMessage("§e/dungeon list §7- 查看可用副本");
        sender.sendMessage("§e/dungeon info <副本ID> §7- 查看副本信息");
        sender.sendMessage("§e/dungeon enter <副本ID> §7- 进入副本");
        sender.sendMessage("§e/dungeon leave §7- 离开当前副本");
        sender.sendMessage("§e/dungeon history §7- 查看通关历史");
        sender.sendMessage("§e/dungeon stats §7- 查看统计数据");
        sender.sendMessage("§e/dungeon gui §7- 打开副本选择界面");
        if (sender.hasPermission("starcore.dungeon.admin")) {
            sender.sendMessage("§c=== 管理员命令 ===");
            sender.sendMessage("§e/dungeon reload §7- 重载配置");
            sender.sendMessage("§e/dungeon admin <操作> §7- 管理员操作");
        }
        sender.sendMessage("§6==============================");
    }

    /**
     * 列出所有副本
     */
    private void handleList(CommandSender sender) {
        Collection<DungeonDefinition> dungeons = service.getAllDungeons();

        if (dungeons.isEmpty()) {
            sender.sendMessage(messages.format("dungeon.command.no_dungeons"));
            return;
        }

        sender.sendMessage("§6========== 可用副本 ==========");
        for (DungeonDefinition dungeon : dungeons) {
            String difficulty = dungeon.difficulty().getDisplayName();
            String color = switch (dungeon.difficulty()) {
                case EASY -> "§a";
                case NORMAL -> "§e";
                case HARD -> "§c";
                case NIGHTMARE -> "§4";
            };
            sender.sendMessage(String.format("%s%s §7- %s (难度: %s%s§7, 等级: %d+)",
                color, dungeon.name(), dungeon.id(), color, difficulty, dungeon.recommendedLevel()));
        }
        sender.sendMessage("§6==============================");
    }

    /**
     * 查看副本信息
     */
    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§c用法: /dungeon info <副本ID>");
            return;
        }

        String dungeonId = args[1];
        Optional<DungeonDefinition> defOpt = service.getDungeonById(dungeonId);

        if (defOpt.isEmpty()) {
            sender.sendMessage(messages.format("dungeon.command.dungeon_not_found")
                .replace("{dungeon}", dungeonId));
            return;
        }

        DungeonDefinition def = defOpt.get();

        sender.sendMessage("§6========== 副本信息 ==========");
        sender.sendMessage("§e名称: §f" + def.name());
        sender.sendMessage("§e难度: §f" + def.difficulty().getDisplayName());
        sender.sendMessage("§e玩家: §f" + def.minPlayers() + "-" + def.maxPlayers() + "人");
        sender.sendMessage("§e推荐等级: §f" + def.recommendedLevel() + "+");
        sender.sendMessage("§e入场费: §f" + def.entryFee() + " 金币");
        sender.sendMessage("§e房间数: §f" + def.totalRooms());
        // 分隔

        for (String desc : def.description()) {
            sender.sendMessage("§7" + desc);
        }

        // 分隔
        sender.sendMessage("§e奖励:");
        sender.sendMessage("§7  - 经验: " + def.rewards().experience());
        sender.sendMessage("§7  - 金币: " + def.rewards().gold());
        sender.sendMessage("§6==========================");
    }

    /**
     * 进入副本
     */
    private void handleEnter(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家才能使用此命令!");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage("§c用法: /dungeon enter <副本ID>");
            return;
        }

        String dungeonId = args[1];
        boolean success = service.tryEnterDungeon(player, dungeonId);

        if (!success) {
            // 错误消息已由服务发送
        }
    }

    /**
     * 离开副本
     */
    private void handleLeave(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家才能使用此命令!");
            return;
        }

        service.leaveDungeon(player);
    }

    /**
     * 查看通关历史
     */
    private void handleHistory(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家才能使用此命令!");
            return;
        }

        List<DungeonCompletionRecord> history = service.getPlayerHistory(player.getUniqueId());

        if (history.isEmpty()) {
            sender.sendMessage("§7你还没有通关任何副本!");
            return;
        }

        sender.sendMessage("§6========== 通关历史 ==========");
        int count = 0;
        for (DungeonCompletionRecord record : history) {
            if (count >= 10) break; // 只显示最近10条

            String status = record.isSuccess() ? "§a成功" : "§c失败";
            String dungeonName = service.getDungeonById(record.dungeonId())
                .map(DungeonDefinition::name)
                .orElse(record.dungeonId());

            sender.sendMessage(String.format("%s %s §7- %s §7- %s",
                status,
                dungeonName,
                record.difficulty().getDisplayName(),
                java.time.Duration.ofSeconds(record.durationSeconds()).toString()
            ));
            count++;
        }
        sender.sendMessage("§6==============================");
    }

    /**
     * 查看统计数据
     */
    private void handleStats(CommandSender sender) {
        DungeonStatistics stats = service.getStatistics();

        sender.sendMessage("§6========== 副本统计 ==========");
        sender.sendMessage("§e总完成: §f" + stats.getTotalCompletions());
        sender.sendMessage("§e总失败: §f" + stats.getTotalFailures());
        sender.sendMessage("§e成功率: §f" + String.format("%.1f%%", stats.getSuccessRate()));
        sender.sendMessage("§e总死亡: §f" + stats.getTotalDeaths());
        sender.sendMessage("§e总金币: §f" + stats.getTotalGoldEarned());
        sender.sendMessage("§e总用时: §f" + stats.getFormattedPlayTime());
        sender.sendMessage("§6==============================");
    }

    /**
     * 打开GUI
     */
    private void handleGui(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家才能使用此命令!");
            return;
        }

        // 打开副本选择GUI
        new DungeonGui(player, service, messages);
    }

    /**
     * 重载配置
     */
    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("starcore.dungeon.admin")) {
            sender.sendMessage(messages.format("dungeon.command.no_permission"));
            return;
        }

        service.reload();
        sender.sendMessage(messages.format("dungeon.admin.reload"));
    }

    /**
     * 管理员操作
     */
    private void handleAdmin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("starcore.dungeon.admin")) {
            sender.sendMessage(messages.format("dungeon.command.no_permission"));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage("§c用法: /dungeon admin <操作>");
            sender.sendMessage("§7操作: closeall, status, forceexit <玩家>");
            return;
        }

        String action = args[1].toLowerCase();

        switch (action) {
            case "closeall" -> {
                service.closeAllInstances();
                sender.sendMessage("§a已关闭所有副本实例!");
            }
            case "status" -> {
                sender.sendMessage("§6========== 副本状态 ==========");
                sender.sendMessage("§e活跃实例: §f" + service.getActiveInstances().size());
                sender.sendMessage(service.getSummary());
                sender.sendMessage("§6==============================");
            }
            case "forceexit" -> {
                if (args.length < 3) {
                    sender.sendMessage("§c用法: /dungeon admin forceexit <玩家名>");
                    return;
                }
                Player target = Bukkit.getPlayer(args[2]);
                if (target == null) {
                    sender.sendMessage(messages.format("dungeon.command.player_not_found")
                        .replace("{player}", args[2]));
                    return;
                }
                service.leaveDungeon(target);
                sender.sendMessage("§a已将玩家 " + target.getName() + " 移出副本!");
            }
            default -> {
                sender.sendMessage("§c未知操作: " + action);
            }
        }
    }

    /**
     * Tab补全
     */
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subCommands = new ArrayList<>();
            subCommands.add("list");
            subCommands.add("info");
            subCommands.add("enter");
            subCommands.add("leave");
            subCommands.add("history");
            subCommands.add("stats");
            subCommands.add("gui");

            if (sender.hasPermission("starcore.dungeon.admin")) {
                subCommands.add("reload");
                subCommands.add("admin");
            }

            return filterStartsWith(subCommands, args[0]);
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("info") || args[0].equalsIgnoreCase("enter")) {
                return service.getAllDungeons().stream()
                    .map(DungeonDefinition::id)
                    .filter(id -> filterStartsWith(List.of(id), args[1]).isEmpty() == false)
                    .collect(Collectors.toList());
            }
            if (args[0].equalsIgnoreCase("admin")) {
                return filterStartsWith(List.of("closeall", "status", "forceexit"), args[1]);
            }
        }

        return List.of();
    }

    private List<String> filterStartsWith(List<String> list, String prefix) {
        return list.stream()
            .filter(s -> s.toLowerCase().startsWith(prefix.toLowerCase()))
            .collect(Collectors.toList());
    }
}
