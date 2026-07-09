package dev.starcore.starcore.essentials.command;

import dev.starcore.starcore.essentials.baltop.BalTopService;
import dev.starcore.starcore.foundation.economy.EconomyService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 经济命令
 * /bal, /baltop, /pay, /eco
 */
public final class EconomyCommand implements CommandExecutor, TabCompleter {
    private final EconomyService economyService;
    private final BalTopService balTopService;

    public EconomyCommand(EconomyService economyService, BalTopService balTopService) {
        this.economyService = economyService;
        this.balTopService = balTopService;
    }

    @Override
    public boolean onCommand(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String label,
        @NotNull String[] args
    ) {
        String commandName = command.getName().toLowerCase();

        switch (commandName) {
            case "balance", "bal", "money" -> handleBalance(sender, args);
            case "baltop" -> handleBalTop(sender, args);
            case "pay" -> handlePay(sender, args);
            case "eco", "economy" -> handleEco(sender, args);
        }

        return true;
    }

    /**
     * 查看余额
     */
    private void handleBalance(CommandSender sender, String[] args) {
        Player target;

        if (args.length > 0) {
            // 查看其他玩家
            if (!sender.hasPermission("starcore.balance.others")) {
                sender.sendMessage(Component.text(
                    "你没有权限查看其他玩家的余额",
                    NamedTextColor.RED
                ));
                return;
            }

            target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(Component.text(
                    "玩家不在线: " + args[0],
                    NamedTextColor.RED
                ));
                return;
            }
        } else {
            // 查看自己
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("请指定玩家", NamedTextColor.RED));
                return;
            }
            target = player;
        }

        BigDecimal balance = economyService.getBalance(target.getUniqueId());

        sender.sendMessage(Component.text(
            target.getName() + " 的余额: ",
            NamedTextColor.YELLOW
        ).append(Component.text(
            String.format("%.2f 金币", balance.doubleValue()),
            NamedTextColor.GREEN
        )));
    }

    /**
     * 财富排行榜
     */
    private void handleBalTop(CommandSender sender, String[] args) {
        // 异步更新排行榜
        Bukkit.getScheduler().runTaskAsynchronously(
            Bukkit.getPluginManager().getPlugin("STARCORE"),
            () -> {
                if (balTopService.needsUpdate()) {
                    // 从 EconomyService 获取所有余额
                    Map<UUID, BigDecimal> allBalances = economyService.getAllBalances();
                    balTopService.updateRankings(allBalances);
                }

                // 回到主线程显示
                Bukkit.getScheduler().runTask(
                    Bukkit.getPluginManager().getPlugin("STARCORE"),
                    () -> displayBalTop(sender, args)
                );
            }
        );
    }

    /**
     * 显示排行榜
     */
    private void displayBalTop(CommandSender sender, String[] args) {
        int page = 1;
        if (args.length > 0) {
            try {
                page = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                page = 1;
            }
        }

        int pageSize = 10;
        int start = (page - 1) * pageSize;

        List<BalTopService.BalTopEntry> topPlayers = balTopService.getTopPlayers(100);

        if (topPlayers.isEmpty()) {
            sender.sendMessage(Component.text(
                "暂无排行榜数据",
                NamedTextColor.YELLOW
            ));
            return;
        }

        int totalPages = (topPlayers.size() + pageSize - 1) / pageSize;
        page = Math.max(1, Math.min(page, totalPages));

        // 标题
        sender.sendMessage(Component.text(
            "========== 财富排行榜 (第" + page + "/" + totalPages + "页) ==========",
            NamedTextColor.GOLD
        ));

        // 显示排名
        int end = Math.min(start + pageSize, topPlayers.size());
        for (int i = start; i < end; i++) {
            BalTopService.BalTopEntry entry = topPlayers.get(i);
            OfflinePlayer player = Bukkit.getOfflinePlayer(entry.playerId());

            String rankEmoji = switch (i) {
                case 0 -> "🥇";
                case 1 -> "🥈";
                case 2 -> "🥉";
                default -> String.format("#%d", i + 1);
            };

            sender.sendMessage(Component.text(
                String.format("%s %s - %.2f 金币",
                    rankEmoji,
                    player.getName(),
                    entry.balance().doubleValue()),
                NamedTextColor.YELLOW
            ));
        }

        // 自己的排名
        if (sender instanceof Player player) {
            balTopService.getPlayerRank(player.getUniqueId()).ifPresent(rank -> {
                BigDecimal balance = economyService.getBalance(player.getUniqueId());
                sender.sendMessage(Component.text(
                    String.format("你的排名: #%d (%.2f 金币)",
                        rank,
                        balance.doubleValue()),
                    NamedTextColor.GRAY
                ));
            });
        }
    }

    /**
     * 转账
     */
    private void handlePay(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("只有玩家可以使用此命令", NamedTextColor.RED));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(Component.text(
                "用法: /pay <玩家> <金额>",
                NamedTextColor.YELLOW
            ));
            return;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null || !target.isOnline()) {
            sender.sendMessage(Component.text(
                "玩家不在线: " + args[0],
                NamedTextColor.RED
            ));
            return;
        }

        if (target.equals(player)) {
            sender.sendMessage(Component.text(
                "你不能转账给自己",
                NamedTextColor.RED
            ));
            return;
        }

        BigDecimal amount;
        try {
            amount = new BigDecimal(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text(
                "无效的金额: " + args[1],
                NamedTextColor.RED
            ));
            return;
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            sender.sendMessage(Component.text(
                "金额必须大于0",
                NamedTextColor.RED
            ));
            return;
        }

        // 检查余额
        if (!economyService.has(player.getUniqueId(), amount)) {
            sender.sendMessage(Component.text(
                "余额不足",
                NamedTextColor.RED
            ));
            return;
        }

        // 转账
        boolean success = economyService.transfer(
            player.getUniqueId(),
            target.getUniqueId(),
            amount
        );

        if (success) {
            sender.sendMessage(Component.text(
                String.format("已转账 %.2f 金币给 %s",
                    amount.doubleValue(),
                    target.getName()),
                NamedTextColor.GREEN
            ));

            target.sendMessage(Component.text(
                String.format("收到来自 %s 的 %.2f 金币",
                    player.getName(),
                    amount.doubleValue()),
                NamedTextColor.GREEN
            ));
        } else {
            sender.sendMessage(Component.text(
                "转账失败",
                NamedTextColor.RED
            ));
        }
    }

    /**
     * 经济管理命令
     */
    private void handleEco(CommandSender sender, String[] args) {
        if (!sender.hasPermission("starcore.eco.admin")) {
            sender.sendMessage(Component.text(
                "你没有权限使用此命令",
                NamedTextColor.RED
            ));
            return;
        }

        if (args.length < 3) {
            sender.sendMessage(Component.text(
                "用法: /eco <give|take|set> <玩家> <金额>",
                NamedTextColor.YELLOW
            ));
            return;
        }

        String action = args[0].toLowerCase();
        Player target = Bukkit.getPlayer(args[1]);

        if (target == null) {
            sender.sendMessage(Component.text(
                "玩家不在线: " + args[1],
                NamedTextColor.RED
            ));
            return;
        }

        BigDecimal amount;
        try {
            amount = new BigDecimal(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text(
                "无效的金额: " + args[2],
                NamedTextColor.RED
            ));
            return;
        }

        boolean success = switch (action) {
            case "give" -> economyService.deposit(target.getUniqueId(), amount);
            case "take" -> economyService.withdraw(target.getUniqueId(), amount);
            case "set" -> economyService.setBalance(target.getUniqueId(), amount);
            default -> {
                sender.sendMessage(Component.text(
                    "无效的操作: " + action,
                    NamedTextColor.RED
                ));
                yield false;
            }
        };

        if (success) {
            BigDecimal newBalance = economyService.getBalance(target.getUniqueId());
            sender.sendMessage(Component.text(
                String.format("已%s %s %.2f 金币，当前余额: %.2f",
                    action.equals("give") ? "给予" : action.equals("take") ? "扣除" : "设置",
                    target.getName(),
                    amount.doubleValue(),
                    newBalance.doubleValue()),
                NamedTextColor.GREEN
            ));
        } else {
            sender.sendMessage(Component.text(
                "操作失败",
                NamedTextColor.RED
            ));
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String alias,
        @NotNull String[] args
    ) {
        String commandName = command.getName().toLowerCase();

        if (commandName.equals("pay") && args.length == 1) {
            List<String> players = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!p.equals(sender)) {
                    players.add(p.getName());
                }
            }
            return players;
        }

        if (commandName.equals("eco")) {
            if (args.length == 1) {
                return List.of("give", "take", "set");
            } else if (args.length == 2) {
                List<String> players = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    players.add(p.getName());
                }
                return players;
            }
        }

        return List.of();
    }
}
