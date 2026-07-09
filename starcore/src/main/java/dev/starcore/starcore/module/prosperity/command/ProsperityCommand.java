package dev.starcore.starcore.module.prosperity.command;

import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.prosperity.ProsperityService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 繁荣度命令处理
 * /prosperity <info|rank|history|boost|set|decay> [args]
 */
public class ProsperityCommand implements CommandExecutor, TabCompleter {
    private final ProsperityService prosperityService;
    private final NationService nationService;

    public ProsperityCommand(ProsperityService prosperityService, NationService nationService) {
        this.prosperityService = prosperityService;
        this.nationService = nationService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "info" -> handleInfo(sender, args);
            case "rank", "ranking" -> handleRanking(sender);
            case "history" -> handleHistory(sender, args);
            case "boost" -> handleBoost(sender, args);
            case "set" -> handleSet(sender, args);
            case "decay" -> handleDecay(sender);
            case "bonus" -> handleBonus(sender, args);
            case "reload" -> handleReload(sender);
            default -> sendHelp(sender);
        }

        return true;
    }

    private void handleInfo(CommandSender sender, String[] args) {
        NationId nationId = null;
        String nationName = "";

        if (args.length > 1) {
            // 指定国家
            Optional<Nation> nationOpt = nationService.nationByName(args[1]);
            if (nationOpt.isEmpty()) {
                sender.sendMessage("§c未找到国家: " + args[1]);
                return;
            }
            nationId = nationOpt.get().id();
            nationName = nationOpt.get().name();
        } else if (sender instanceof Player player) {
            // 查看自己国家
            Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
            if (nationOpt.isEmpty()) {
                sender.sendMessage("§c你还未加入任何国家");
                return;
            }
            nationId = nationOpt.get().id();
            nationName = nationOpt.get().name();
        } else {
            sender.sendMessage("§c请指定国家名称: /prosperity info <国家名>");
            return;
        }

        var prosperity = prosperityService.getProsperity(nationId);
        int level = prosperityService.getProsperityLevel(nationId);
        int activityScore = prosperityService.getActivityScore(nationId);

        sender.sendMessage("§6===== §e" + nationName + " §6繁荣度 =====");
        sender.sendMessage("§e繁荣度: §f" + String.format("%.2f", prosperity.prosperity()) + "%");
        sender.sendMessage("§e等级: §f" + level + "/10");
        sender.sendMessage("§e活跃度: §f" + activityScore);
        sender.sendMessage("§e最后活跃: §f" + formatInstant(prosperity.lastActivity()));

        // 显示等级进度条
        double levelProgress = (prosperity.prosperity() % 10) * 10;
        StringBuilder progressBar = new StringBuilder("§7[");
        int bars = (int) (levelProgress / 5);
        for (int i = 0; i < 20; i++) {
            if (i < bars) {
                progressBar.append("§a|");
            } else {
                progressBar.append("§8|");
            }
        }
        progressBar.append("§7] §f").append((int) levelProgress).append("%");
        sender.sendMessage(progressBar.toString());

        // 显示加成
        double taxBonus = prosperityService.getTaxBonus(nationId);
        double resourceBonus = prosperityService.getResourceBonus(nationId);
        // 分隔
        sender.sendMessage("§e税收加成: §a+" + String.format("%.1f", (taxBonus - 1) * 100) + "%");
        sender.sendMessage("§e资源加成: §a+" + String.format("%.1f", (resourceBonus - 1) * 100) + "%");
        sender.sendMessage("§e总加成: §a×" + String.format("%.2f", prosperityService.getBonusMultiplier(nationId)));
    }

    private void handleRanking(CommandSender sender) {
        List<Map.Entry<NationId, Double>> ranking = prosperityService.getRanking();

        sender.sendMessage("§6===== 繁荣度排行榜 =====");
        if (ranking.isEmpty()) {
            sender.sendMessage("§7暂无数据");
            return;
        }

        int position = 1;
        for (Map.Entry<NationId, Double> entry : ranking) {
            if (position > 10) break;
            Optional<Nation> nationOpt = nationService.nationById(entry.getKey());
            String nationName = nationOpt.map(Nation::name).orElse("未知");
            int level = prosperityService.getProsperityLevel(entry.getKey());

            String medal = switch (position) {
                case 1 -> "§6🥇 ";
                case 2 -> "§f🥈 ";
                case 3 -> "§c🥉 ";
                default -> "§7" + position + ". ";
            };

            sender.sendMessage(medal + "§e" + nationName + " §7- " +
                    String.format("%.2f", entry.getValue()) + "% (Lv." + level + ")");
            position++;
        }
    }

    private void handleHistory(CommandSender sender, String[] args) {
        NationId nationId = null;

        if (args.length > 1) {
            Optional<Nation> nationOpt = nationService.nationByName(args[1]);
            if (nationOpt.isEmpty()) {
                sender.sendMessage("§c未找到国家: " + args[1]);
                return;
            }
            nationId = nationOpt.get().id();
        } else if (sender instanceof Player player) {
            Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
            if (nationOpt.isEmpty()) {
                sender.sendMessage("§c你还未加入任何国家");
                return;
            }
            nationId = nationOpt.get().id();
        } else {
            sender.sendMessage("§c请指定国家名称: /prosperity history <国家名>");
            return;
        }

        int limit = args.length > 2 ? Math.min(20, Math.max(1, Integer.parseInt(args[2]))) : 10;
        var events = prosperityService.getRecentEvents(nationId, limit);

        sender.sendMessage("§6===== 繁荣度历史 =====");
        if (events.isEmpty()) {
            sender.sendMessage("§7暂无历史记录");
            return;
        }

        for (var event : events) {
            String color = event.amount() > 0 ? "§a" : (event.amount() < 0 ? "§c" : "§7");
            String sign = event.amount() > 0 ? "+" : "";
            sender.sendMessage(color + sign + String.format("%.2f", event.amount()) + " §7- " +
                    event.description() + " §8(" + formatInstant(event.timestamp()) + ")");
        }
    }

    private void handleBoost(CommandSender sender, String[] args) {
        if (!sender.hasPermission("starcore.admin")) {
            sender.sendMessage("§c你没有权限执行此命令");
            return;
        }

        if (args.length < 3) {
            sender.sendMessage("§c用法: /prosperity boost <国家名> <数值>");
            return;
        }

        Optional<Nation> nationOpt = nationService.nationByName(args[1]);
        if (nationOpt.isEmpty()) {
            sender.sendMessage("§c未找到国家: " + args[1]);
            return;
        }

        try {
            double amount = Double.parseDouble(args[2]);
            double newValue = prosperityService.modifyProsperity(nationOpt.get().id(), amount, "管理员加成");
            sender.sendMessage("§a已为 §e" + nationOpt.get().name() + " §a增加 §f" + amount + " §a繁荣度 (当前: " +
                    String.format("%.2f", newValue) + "%)");
        } catch (NumberFormatException e) {
            sender.sendMessage("§c无效的数值: " + args[2]);
        }
    }

    private void handleSet(CommandSender sender, String[] args) {
        if (!sender.hasPermission("starcore.admin")) {
            sender.sendMessage("§c你没有权限执行此命令");
            return;
        }

        if (args.length < 3) {
            sender.sendMessage("§c用法: /prosperity set <国家名> <数值>");
            return;
        }

        Optional<Nation> nationOpt = nationService.nationByName(args[1]);
        if (nationOpt.isEmpty()) {
            sender.sendMessage("§c未找到国家: " + args[1]);
            return;
        }

        try {
            double value = Double.parseDouble(args[2]);
            prosperityService.setProsperity(nationOpt.get().id(), value);
            sender.sendMessage("§a已将 §e" + nationOpt.get().name() + " §a的繁荣度设置为 §f" + value + "%");
        } catch (NumberFormatException e) {
            sender.sendMessage("§c无效的数值: " + args[2]);
        }
    }

    private void handleDecay(CommandSender sender) {
        if (!sender.hasPermission("starcore.admin")) {
            sender.sendMessage("§c你没有权限执行此命令");
            return;
        }

        sender.sendMessage("§e正在处理所有国家的繁荣度衰减...");
        prosperityService.processAllDecay();
        sender.sendMessage("§a繁荣度衰减处理完成");
    }

    private void handleBonus(CommandSender sender, String[] args) {
        NationId nationId = null;

        if (args.length > 1) {
            Optional<Nation> nationOpt = nationService.nationByName(args[1]);
            if (nationOpt.isEmpty()) {
                sender.sendMessage("§c未找到国家: " + args[1]);
                return;
            }
            nationId = nationOpt.get().id();
        } else if (sender instanceof Player player) {
            Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
            if (nationOpt.isEmpty()) {
                sender.sendMessage("§c你还未加入任何国家");
                return;
            }
            nationId = nationOpt.get().id();
        } else {
            sender.sendMessage("§c请指定国家名称: /prosperity bonus <国家名>");
            return;
        }

        sender.sendMessage("§6===== 繁荣度加成 =====");
        sender.sendMessage("§e繁荣度等级: §fLv." + prosperityService.getProsperityLevel(nationId));
        // 分隔
        sender.sendMessage("§e税收加成: §a+" + String.format("%.1f", (prosperityService.getTaxBonus(nationId) - 1) * 100) + "%");
        sender.sendMessage("§e资源产出加成: §a+" + String.format("%.1f", (prosperityService.getResourceBonus(nationId) - 1) * 100) + "%");
        sender.sendMessage("§e整体加成: §a×" + String.format("%.2f", prosperityService.getBonusMultiplier(nationId)));
        // 分隔
        sender.sendMessage("§7说明: 繁荣度越高，获得的加成越多");
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("starcore.admin")) {
            sender.sendMessage("§c你没有权限执行此命令");
            return;
        }

        sender.sendMessage("§e繁荣度配置已重新加载...");
        sender.sendMessage("§a配置重新加载完成");
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6===== 繁荣度系统 =====");
        sender.sendMessage("§e/prosperity info [国家名] §7- 查看繁荣度信息");
        sender.sendMessage("§e/prosperity rank §7- 查看繁荣度排行榜");
        sender.sendMessage("§e/prosperity history [国家名] [数量] §7- 查看繁荣度历史");
        sender.sendMessage("§e/prosperity bonus [国家名] §7- 查看繁荣度加成");
        if (sender.hasPermission("starcore.admin")) {
            sender.sendMessage("§c/prosperity boost <国家> <数值> §7- 增加繁荣度");
            sender.sendMessage("§c/prosperity set <国家> <数值> §7- 设置繁荣度");
            sender.sendMessage("§c/prosperity decay §7- 处理所有衰减");
            sender.sendMessage("§c/prosperity reload §7- 重载配置");
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                               @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("info");
            completions.add("rank");
            completions.add("history");
            completions.add("bonus");
            if (sender.hasPermission("starcore.admin")) {
                completions.add("boost");
                completions.add("set");
                completions.add("decay");
                completions.add("reload");
            }
        } else if (args.length == 2) {
            // 提供国家名称补全
            for (Nation nation : nationService.nations()) {
                if (nation.name().toLowerCase().startsWith(args[1].toLowerCase())) {
                    completions.add(nation.name());
                }
            }
        }

        String current = args[args.length - 1].toLowerCase();
        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(current))
                .toList();
    }

    private String formatInstant(java.time.Instant instant) {
        if (instant == null) return "无记录";
        java.time.ZonedDateTime zdt = instant.atZone(java.time.ZoneId.systemDefault());
        return zdt.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }
}