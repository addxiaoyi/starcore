package dev.starcore.starcore.pvp.command;

import dev.starcore.starcore.pvp.duel.Duel;
import dev.starcore.starcore.pvp.duel.DuelService;
import dev.starcore.starcore.pvp.duel.DuelSettings;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 决斗命令
 * /duel <玩家> [Kit名称] [赌注] [BO数] - 发起决斗
 * /duel accept [玩家] - 接受决斗
 * /duel deny [玩家] - 拒绝决斗
 * /duel cancel - 取消自己的决斗请求
 * /duel stats [玩家] - 查看决斗统计
 * /duel list - 查看进行中的决斗
 */
public final class DuelCommand implements CommandExecutor, TabCompleter {
    private final DuelService duelService;

    public DuelCommand(DuelService duelService) {
        this.duelService = duelService;
    }

    @Override
    public boolean onCommand(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String label,
        @NotNull String[] args
    ) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("只有玩家可以使用此命令", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "accept" -> handleAccept(player, args);
            case "deny", "reject" -> handleDeny(player, args);
            case "cancel" -> handleCancel(player);
            case "stats" -> handleStats(player, args);
            case "list" -> handleList(player);
            case "forfeit", "surrender" -> handleForfeit(player);
            default -> handleRequest(player, args);
        }

        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage(Component.text("=== 决斗命令帮助 ===", NamedTextColor.GOLD));
        player.sendMessage(Component.text("/duel <玩家> [Kit] [赌注] [BO数]", NamedTextColor.YELLOW)
            .hoverEvent(HoverEvent.showText(Component.text("向玩家发起决斗")))
            .clickEvent(ClickEvent.suggestCommand("/duel ")));
        player.sendMessage(Component.text("/duel accept [玩家]", NamedTextColor.YELLOW)
            .hoverEvent(HoverEvent.showText(Component.text("接受决斗请求")))
            .clickEvent(ClickEvent.suggestCommand("/duel accept ")));
        player.sendMessage(Component.text("/duel deny [玩家]", NamedTextColor.YELLOW)
            .hoverEvent(HoverEvent.showText(Component.text("拒绝决斗请求")))
            .clickEvent(ClickEvent.suggestCommand("/duel deny ")));
        player.sendMessage(Component.text("/duel cancel", NamedTextColor.YELLOW)
            .hoverEvent(HoverEvent.showText(Component.text("取消你的决斗请求")))
            .clickEvent(ClickEvent.suggestCommand("/duel cancel")));
        player.sendMessage(Component.text("/duel stats [玩家]", NamedTextColor.YELLOW)
            .hoverEvent(HoverEvent.showText(Component.text("查看决斗统计")))
            .clickEvent(ClickEvent.suggestCommand("/duel stats ")));
        player.sendMessage(Component.text("/duel list", NamedTextColor.YELLOW)
            .hoverEvent(HoverEvent.showText(Component.text("查看进行中的决斗")))
            .clickEvent(ClickEvent.suggestCommand("/duel list")));
        player.sendMessage(Component.text("/duel forfeit", NamedTextColor.YELLOW)
            .hoverEvent(HoverEvent.showText(Component.text("认输")))
            .clickEvent(ClickEvent.suggestCommand("/duel forfeit")));

        // 显示可用 Kit
        Collection<dev.starcore.starcore.pvp.duel.DuelService.DuelKit> kits = duelService.getAvailableKits();
        if (!kits.isEmpty()) {
            player.sendMessage(Component.text("可用装备: " + kits.stream()
                .map(dev.starcore.starcore.pvp.duel.DuelService.DuelKit::id)
                .collect(Collectors.joining(", ")), NamedTextColor.GRAY));
        }
    }

    /**
     * 发起决斗请求
     */
    private void handleRequest(Player player, String[] args) {
        if (args.length < 1) {
            player.sendMessage(Component.text(
                "用法: /duel <玩家> [Kit名称] [赌注] [BO数]",
                NamedTextColor.YELLOW
            ));
            return;
        }

        Player target = Bukkit.getPlayer(args[0]);

        if (target == null || !target.isOnline()) {
            player.sendMessage(Component.text(
                "玩家不在线: " + args[0],
                NamedTextColor.RED
            ));
            return;
        }

        if (target.equals(player)) {
            player.sendMessage(Component.text(
                "你不能和自己决斗",
                NamedTextColor.RED
            ));
            return;
        }

        // 解析参数
        String kitName = "default";
        BigDecimal wager = BigDecimal.ZERO;
        int bestOf = 1;

        if (args.length > 1) {
            // 检查第二个参数是 Kit 还是赌注
            String arg2 = args[1].toLowerCase();
            Collection<dev.starcore.starcore.pvp.duel.DuelService.DuelKit> kits = duelService.getAvailableKits();
            boolean isKit = kits.stream().anyMatch(k -> k.id().equalsIgnoreCase(arg2));

            if (isKit) {
                kitName = arg2;
                if (args.length > 2) {
                    try {
                        wager = new BigDecimal(args[2]);
                    } catch (NumberFormatException e) {
                        player.sendMessage(Component.text("无效的赌注金额: " + args[2], NamedTextColor.RED));
                        return;
                    }
                }
                if (args.length > 3) {
                    try {
                        bestOf = Integer.parseInt(args[3]);
                    } catch (NumberFormatException e) {
                        player.sendMessage(Component.text("无效的BO数: " + args[3], NamedTextColor.RED));
                        return;
                    }
                }
            } else {
                // 第二个参数是赌注
                try {
                    wager = new BigDecimal(args[2]);
                } catch (NumberFormatException e) {
                    player.sendMessage(Component.text("无效的赌注金额: " + args[2], NamedTextColor.RED));
                    return;
                }
            }
        }

        try {
            // 发送请求
            duelService.sendDuelRequest(player.getUniqueId(), target.getUniqueId(), wager.doubleValue(), kitName, bestOf);

            player.sendMessage(Component.text(
                "已向 " + target.getName() + " 发送决斗请求" + (bestOf > 1 ? " (BO" + bestOf + ")" : ""),
                NamedTextColor.GREEN
            ));

            // 提示对方
            target.sendMessage(Component.text(
                player.getName() + " 向你发起了决斗！",
                NamedTextColor.YELLOW
            ));
            target.sendMessage(Component.text(
                "输入 /duel accept 接受 或 /duel deny 拒绝",
                NamedTextColor.GRAY
            ));
        } catch (IllegalStateException e) {
            player.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED));
        }
    }

    /**
     * 解析挑战者 UUID：优先用参数指定的玩家名，否则在挂起请求中自动选择。
     */
    private UUID resolveChallenger(Player player, String[] args, String action) {
        Set<UUID> pending = duelService.getPendingChallengers(player.getUniqueId());
        if (pending.isEmpty()) {
            player.sendMessage(Component.text("你当前没有收到任何决斗请求", NamedTextColor.RED));
            return null;
        }
        if (args.length >= 2) {
            Player challenger = Bukkit.getPlayerExact(args[1]);
            if (challenger == null || !pending.contains(challenger.getUniqueId())) {
                player.sendMessage(Component.text("没有来自 " + args[1] + " 的决斗请求", NamedTextColor.RED));
                return null;
            }
            return challenger.getUniqueId();
        }
        if (pending.size() > 1) {
            player.sendMessage(Component.text("你有多个决斗请求，请指定: /duel " + action + " <玩家>", NamedTextColor.YELLOW));
            return null;
        }
        return pending.iterator().next();
    }

    /**
     * 接受决斗
     */
    private void handleAccept(Player player, String[] args) {
        UUID challenger = resolveChallenger(player, args, "accept");
        if (challenger == null) {
            return;
        }
        try {
            Duel duel = duelService.acceptDuelRequest(player.getUniqueId(), challenger);
            Player ch = Bukkit.getPlayer(challenger);

            player.sendMessage(Component.text("已接受 " + (ch != null ? ch.getName() : "对方") + " 的决斗请求", NamedTextColor.GREEN));
            if (ch != null) {
                ch.sendMessage(Component.text(player.getName() + " 接受了你的决斗请求！", NamedTextColor.GREEN));
            }

            // 开始决斗
            if (ch != null) {
                duelService.startDuel(duel.getId(), ch, player);
            }
        } catch (RuntimeException ex) {
            player.sendMessage(Component.text("无法接受决斗: " + ex.getMessage(), NamedTextColor.RED));
        }
    }

    /**
     * 拒绝决斗
     */
    private void handleDeny(Player player, String[] args) {
        UUID challenger = resolveChallenger(player, args, "deny");
        if (challenger == null) {
            return;
        }
        duelService.rejectDuelRequest(player.getUniqueId(), challenger);
        Player ch = Bukkit.getPlayer(challenger);
        player.sendMessage(Component.text("已拒绝 " + (ch != null ? ch.getName() : "对方") + " 的决斗请求", NamedTextColor.YELLOW));
        if (ch != null) {
            ch.sendMessage(Component.text(player.getName() + " 拒绝了你的决斗请求", NamedTextColor.RED));
        }
    }

    /**
     * 取消自己的决斗请求
     */
    private void handleCancel(Player player) {
        // 检查玩家是否在决斗中
        Duel duel = duelService.getPlayerDuel(player.getUniqueId());
        if (duel != null) {
            player.sendMessage(Component.text("你正在决斗中，无法取消", NamedTextColor.RED));
            return;
        }

        // 获取玩家发出的请求
        List<DuelService.DuelRequest> requests = getSentRequests(player.getUniqueId());
        if (requests.isEmpty()) {
            player.sendMessage(Component.text("你没有发出任何决斗请求", NamedTextColor.YELLOW));
            return;
        }

        // 如果只有一个请求，直接取消
        if (requests.size() == 1) {
            DuelService.DuelRequest req = requests.get(0);
            duelService.rejectDuelRequest(req.opponentId(), req.challengerId());
            Player target = Bukkit.getPlayer(req.opponentId());
            player.sendMessage(Component.text("已取消对 " + (target != null ? target.getName() : "对方") + " 的决斗请求", NamedTextColor.YELLOW));
            return;
        }

        // 多个请求，列出供选择
        player.sendMessage(Component.text("你有 " + requests.size() + " 个决斗请求，请指定要取消的玩家:", NamedTextColor.YELLOW));
        for (DuelService.DuelRequest req : requests) {
            Player target = Bukkit.getPlayer(req.opponentId());
            String targetName = target != null ? target.getName() : req.opponentId().toString();
            player.sendMessage(Component.text("  - " + targetName, NamedTextColor.GRAY)
                .clickEvent(ClickEvent.runCommand("/duel cancel " + targetName)));
        }
    }

    private List<DuelService.DuelRequest> getSentRequests(UUID playerId) {
        // 这是一个简化的实现，实际应该跟踪玩家发出的请求
        return new ArrayList<>();
    }

    /**
     * 查看决斗统计
     */
    private void handleStats(Player player, String[] args) {
        UUID targetId = player.getUniqueId();
        String targetName = player.getName();

        if (args.length >= 2) {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target != null) {
                targetId = target.getUniqueId();
                targetName = target.getName();
            } else {
                player.sendMessage(Component.text("玩家不在线: " + args[1], NamedTextColor.RED));
                return;
            }
        }

        DuelService.PlayerDuelStats stats = duelService.getPlayerStats(targetId);

        player.sendMessage(Component.text("=== " + targetName + " 的决斗统计 ===", NamedTextColor.GOLD));
        player.sendMessage(Component.text("总决斗数: " + stats.totalDuels(), NamedTextColor.YELLOW));
        player.sendMessage(Component.text("胜利: " + stats.wins() + "  |  失败: " + stats.losses() + "  |  平局: " + stats.draws(), NamedTextColor.GREEN));
        player.sendMessage(Component.text("胜率: " + String.format("%.1f", stats.winRate()) + "%", NamedTextColor.AQUA));
        player.sendMessage(Component.text("当前连胜: " + stats.currentWinStreak() + "  |  最长连胜: " + stats.longestWinStreak(), NamedTextColor.LIGHT_PURPLE));
        player.sendMessage(Component.text("总伤害: " + stats.totalDamageDealt() + "  |  受到伤害: " + stats.totalDamageTaken(), NamedTextColor.RED));
        player.sendMessage(Component.text("赌注收益: " + String.format("%.2f", stats.netProfit()), NamedTextColor.GREEN));
    }

    /**
     * 查看进行中的决斗列表
     */
    private void handleList(Player player) {
        List<Duel> activeDuels = duelService.getActiveDuels();

        if (activeDuels.isEmpty()) {
            player.sendMessage(Component.text("当前没有进行中的决斗", NamedTextColor.YELLOW));
            return;
        }

        player.sendMessage(Component.text("=== 进行中的决斗 (" + activeDuels.size() + ") ===", NamedTextColor.GOLD));

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("mm:ss");
        for (int i = 0; i < Math.min(activeDuels.size(), 10); i++) {
            Duel duel = activeDuels.get(i);
            Player c1 = Bukkit.getPlayer(duel.getChallenger());
            Player c2 = Bukkit.getPlayer(duel.getOpponent());

            String name1 = c1 != null ? c1.getName() : duel.getChallenger().toString();
            String name2 = c2 != null ? c2.getName() : duel.getOpponent().toString();
            String arenaName = duel.getArena() != null ? duel.getArena().getName() : "未知";

            long elapsed = (System.currentTimeMillis() - duel.getStartTime()) / 1000;
            String duration = String.format("%02d:%02d", elapsed / 60, elapsed % 60);

            String info = String.format("%d. %s vs %s [%s] %s", i + 1, name1, name2, arenaName, duration);

            player.sendMessage(Component.text(info, NamedTextColor.YELLOW));
        }

        if (activeDuels.size() > 10) {
            player.sendMessage(Component.text("... 还有 " + (activeDuels.size() - 10) + " 场决斗", NamedTextColor.GRAY));
        }
    }

    /**
     * 认输
     */
    private void handleForfeit(Player player) {
        Duel duel = duelService.getPlayerDuel(player.getUniqueId());
        if (duel == null) {
            player.sendMessage(Component.text("你不在决斗中", NamedTextColor.RED));
            return;
        }

        try {
            duelService.forfeit(player.getUniqueId());
        } catch (IllegalStateException e) {
            player.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED));
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String alias,
        @NotNull String[] args
    ) {
        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>();
            suggestions.add("accept");
            suggestions.add("deny");
            suggestions.add("cancel");
            suggestions.add("stats");
            suggestions.add("list");
            suggestions.add("forfeit");

            // 添加在线玩家
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!p.equals(sender)) {
                    suggestions.add(p.getName());
                }
            }

            return suggestions;
        }

        if (args.length == 2) {
            String subCommand = args[0].toLowerCase();

            if (subCommand.equals("accept") || subCommand.equals("deny")) {
                // 提供有请求的玩家
                List<String> suggestions = new ArrayList<>();
                UUID playerId = sender instanceof Player ? ((Player) sender).getUniqueId() : null;
                if (playerId != null) {
                    Set<UUID> pending = duelService.getPendingChallengers(playerId);
                    for (UUID uuid : pending) {
                        Player p = Bukkit.getPlayer(uuid);
                        if (p != null) {
                            suggestions.add(p.getName());
                        }
                    }
                }
                return suggestions;
            }

            if (subCommand.equals("stats")) {
                // 提供在线玩家
                List<String> suggestions = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    suggestions.add(p.getName());
                }
                return suggestions;
            }

            if (subCommand.equals("cancel")) {
                // 提供已发送请求的目标
                return new ArrayList<>();
            }

            // 默认：Kit 名称
            return duelService.getAvailableKits().stream()
                .map(dev.starcore.starcore.pvp.duel.DuelService.DuelKit::id)
                .collect(Collectors.toList());
        }

        if (args.length == 3) {
            String subCommand = args[0].toLowerCase();
            if (!subCommand.equals("accept") && !subCommand.equals("deny") && !subCommand.equals("cancel") && !subCommand.equals("stats") && !subCommand.equals("list") && !subCommand.equals("forfeit")) {
                // 赌注输入
                return List.of("100", "500", "1000", "5000");
            }
        }

        if (args.length == 4) {
            String subCommand = args[0].toLowerCase();
            if (!subCommand.equals("accept") && !subCommand.equals("deny") && !subCommand.equals("cancel") && !subCommand.equals("stats") && !subCommand.equals("list") && !subCommand.equals("forfeit")) {
                // BO 数
                return List.of("1", "3", "5");
            }
        }

        return List.of();
    }
}
