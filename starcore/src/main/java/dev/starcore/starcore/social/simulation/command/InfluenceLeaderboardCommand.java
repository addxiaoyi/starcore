package dev.starcore.starcore.social.simulation.command;

import dev.starcore.starcore.social.simulation.InfluenceLeaderboardGui;
import dev.starcore.starcore.social.simulation.InfluenceLeaderboardService;
import dev.starcore.starcore.social.simulation.LeaderboardPeriod;
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

/**
 * 影响力排行榜命令
 * 提供玩家查看影响力排行榜的入口
 */
public class InfluenceLeaderboardCommand implements CommandExecutor, TabCompleter {

    private final InfluenceLeaderboardGui gui;

    public InfluenceLeaderboardCommand(InfluenceLeaderboardGui gui) {
        this.gui = gui;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                            @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家可以使用此命令");
            return true;
        }

        if (!player.hasPermission("starcore.leaderboard")) {
            player.sendMessage("§c你没有权限使用此命令");
            return true;
        }

        if (args.length == 0) {
            // 打开主菜单
            gui.openMainMenu(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "daily" -> gui.openLeaderboard(player, LeaderboardPeriod.DAILY);
            case "weekly" -> gui.openLeaderboard(player, LeaderboardPeriod.WEEKLY);
            case "monthly" -> gui.openLeaderboard(player, LeaderboardPeriod.MONTHLY);
            case "all", "alltime" -> gui.openLeaderboard(player, LeaderboardPeriod.ALLTIME);
            case "me", "self" -> gui.openPlayerDetail(player, player.getUniqueId());
            case "top" -> {
                if (args.length > 1) {
                    int limit;
                    try {
                        limit = Math.min(Integer.parseInt(args[1]), 10);
                    } catch (NumberFormatException e) {
                        limit = 10;
                    }
                    showTopList(player, LeaderboardPeriod.ALLTIME, limit);
                } else {
                    showTopList(player, LeaderboardPeriod.ALLTIME, 10);
                }
            }
            case "help" -> sendHelp(player);
            default -> player.sendMessage("§c未知参数，使用 /influencelb help 查看帮助");
        }

        return true;
    }

    /**
     * 显示 Top 列表（聊天形式）
     */
    private void showTopList(Player player, LeaderboardPeriod period, int limit) {
        player.sendMessage("§6=== " + period.getDisplayName() + " Top " + limit + " ===");
        // 分隔

        try {
            InfluenceLeaderboardService service = gui.getLeaderboardService();
            var topList = service.getTopPlayers(period, limit);

            if (topList.isEmpty()) {
                player.sendMessage("§7暂无数据");
                return;
            }

            for (var entry : topList) {
                String rankIcon = switch (entry.rank()) {
                    case 1 -> "§6🥇";
                    case 2 -> "§f🥈";
                    case 3 -> "§c🥉";
                    default -> "§7#" + entry.rank();
                };

                String changeIcon = entry.getChangeIcon();
                player.sendMessage(String.format("%s §f%s §7- 影响力: §e%d %s",
                    rankIcon, entry.playerName(), entry.influence(), changeIcon));
            }
        } catch (Exception e) {
            player.sendMessage("§c获取排行榜失败");
        }
    }

    /**
     * 发送帮助信息
     */
    private void sendHelp(Player player) {
        player.sendMessage("§6§l=== 影响力排行榜帮助 ===");
        // 分隔
        player.sendMessage("§e/influencelb §7- 打开排行榜GUI");
        player.sendMessage("§e/influencelb daily §7- 查看日榜");
        player.sendMessage("§e/influencelb weekly §7- 查看周榜");
        player.sendMessage("§e/influencelb monthly §7- 查看月榜");
        player.sendMessage("§e/influencelb alltime §7- 查看总榜");
        player.sendMessage("§e/influencelb me §7- 查看我的排名");
        player.sendMessage("§e/influencelb top [数量] §7- 查看Top列表");
        // 分隔
        player.sendMessage("§7权限: starcore.leaderboard");
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("daily");
            completions.add("weekly");
            completions.add("monthly");
            completions.add("alltime");
            completions.add("me");
            completions.add("top");
            completions.add("help");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("top")) {
            completions.add("5");
            completions.add("10");
        }

        // 过滤输入
        String input = args[args.length - 1].toLowerCase();
        return completions.stream()
            .filter(c -> c.toLowerCase().startsWith(input))
            .toList();
    }
}
