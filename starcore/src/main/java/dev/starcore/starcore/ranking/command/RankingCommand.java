package dev.starcore.starcore.ranking.command;

import dev.starcore.starcore.pvp.stats.PvPStats;
import dev.starcore.starcore.pvp.stats.PvPStatsService;
import dev.starcore.starcore.ranking.RankPeriod;
import dev.starcore.starcore.ranking.RankingService;
import dev.starcore.starcore.ranking.RankingService.TopPlayerData;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 排行榜命令
 * 提供 /rank, /leaderboard, /top 等命令
 */
public class RankingCommand implements CommandExecutor, TabCompleter {

    private final Plugin plugin;
    private final RankingService rankingService;
    private final PvPStatsService pvpStatsService;

    // 命令别名
    private static final List<String> STAT_TYPES = Arrays.asList("kills", "deaths", "playtime", "kdratio");
    private static final List<String> PERIODS = Arrays.asList("daily", "weekly", "monthly", "alltime");

    public RankingCommand(Plugin plugin, RankingService rankingService, PvPStatsService pvpStatsService) {
        this.plugin = plugin;
        this.rankingService = rankingService;
        this.pvpStatsService = pvpStatsService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                            @NotNull String label, @NotNull String[] args) {
        String cmdName = command.getName().toLowerCase();

        if (cmdName.equals("rank")) {
            return handleRankCommand(sender, args);
        } else if (cmdName.equals("leaderboard") || cmdName.equals("lb") || cmdName.equals("top")) {
            return handleLeaderboardCommand(sender, args);
        } else if (cmdName.equals("stats")) {
            return handleStatsCommand(sender, args);
        }

        return false;
    }

    /**
     * /rank [玩家] [类型] - 查看玩家排名
     */
    private boolean handleRankCommand(CommandSender sender, String[] args) {
        Player target;
        String statType = "kills";
        RankPeriod period = RankPeriod.ALLTIME;

        if (args.length >= 1) {
            // 解析玩家
            OfflinePlayer offlineTarget = Bukkit.getOfflinePlayer(args[0]);
            target = Bukkit.getPlayer(offlineTarget.getUniqueId());
            if (target == null) {
                target = sender instanceof Player ? (Player) sender : null;
            }
        } else {
            if (!(sender instanceof Player)) {
                sender.sendMessage("§c请指定玩家名称: /rank <玩家> [类型]");
                return true;
            }
            target = (Player) sender;
        }

        if (target == null) {
            sender.sendMessage("§c玩家不存在或未在线");
            return true;
        }

        // 解析统计类型
        if (args.length >= 2) {
            statType = args[1].toLowerCase();
            if (!STAT_TYPES.contains(statType)) {
                sender.sendMessage("§c无效的统计类型: " + statType);
                sender.sendMessage("§e可用类型: " + String.join(", ", STAT_TYPES));
                return true;
            }
        }

        // 解析时间周期
        if (args.length >= 3) {
            period = parsePeriod(args[2]);
            if (period == null) {
                sender.sendMessage("§c无效的时间周期: " + args[2]);
                sender.sendMessage("§e可用周期: " + String.join(", ", PERIODS));
                return true;
            }
        }

        // 获取排名信息
        UUID targetId = target.getUniqueId();
        String targetName = target.getName();
        String displayType = getStatDisplayName(statType);
        String displayPeriod = period.getDisplayName();
        int onlineCount = Bukkit.getOnlinePlayers().size();
        int maxPlayers = Bukkit.getMaxPlayers();
        Plugin pluginRef = plugin;
        String finalStatType = statType;
        RankPeriod finalPeriod = period;

        CompletableFuture<Integer> rankFuture;
        CompletableFuture<Long> valueFuture;

        switch (statType) {
            case "kills" -> {
                rankFuture = rankingService.getKillRank(targetId, finalPeriod);
                valueFuture = rankingService.getKillCount(targetId, finalPeriod);
            }
            case "deaths" -> {
                rankFuture = rankingService.getDeathCount(targetId, finalPeriod)
                    .thenCompose(v -> rankingService.getKillRank(targetId, finalPeriod));
                valueFuture = rankingService.getDeathCount(targetId, finalPeriod);
            }
            case "playtime" -> {
                rankFuture = rankingService.getOnlineTimeRank(targetId, finalPeriod);
                valueFuture = rankingService.getOnlineTime(targetId, finalPeriod);
            }
            case "kdratio" -> {
                rankFuture = rankingService.getKillRank(targetId, finalPeriod);
                valueFuture = rankingService.getKillCount(targetId, finalPeriod);
            }
            default -> {
                rankFuture = rankingService.getKillRank(targetId, finalPeriod);
                valueFuture = rankingService.getKillCount(targetId, finalPeriod);
            }
        }

        // 异步获取排名信息，完成后在主线程发送消息
        rankFuture.thenCombine(valueFuture, (rank, value) -> {
            return new RankingResult(rank, value);
        }).thenAcceptAsync(result -> {
            Bukkit.getScheduler().runTask(pluginRef, () -> {
                sender.sendMessage("§6§l=== 玩家排名 ===");
                sender.sendMessage("§e玩家: §f" + targetName);
                sender.sendMessage("§e类型: §f" + displayType);
                sender.sendMessage("§e周期: §f" + displayPeriod);

                if (result.rank > 0) {
                    sender.sendMessage("§e排名: §a第 " + result.rank + " 名");
                } else {
                    sender.sendMessage("§e排名: §7未上榜");
                }

                sender.sendMessage("§e数值: §f" + formatStatValue(finalStatType, result.value));

                // K/D 比率特别显示
                if ("kdratio".equals(finalStatType) || "kills".equals(finalStatType)) {
                    double kd = rankingService.getKDRatio(targetId);
                    sender.sendMessage("§eK/D: §f" + String.format("%.2f", kd));
                }

                int total = rankingService.getLeaderboardSize(finalStatType);
                if (result.rank > 0 && total > 0) {
                    sender.sendMessage("§e当前在线玩家: §f" + onlineCount + " / " + maxPlayers);
                }
            });
        }, Bukkit.getScheduler().getMainThreadExecutor(plugin)).exceptionally(ex -> {
            Bukkit.getScheduler().runTask(pluginRef, () -> {
                sender.sendMessage("§c获取排名失败: " + ex.getMessage());
            });
            return null;
        });

        return true;
    }

    // 内部类用于传递结果
    private record RankingResult(int rank, long value) {}

    /**
     * /leaderboard [类型] [数量] - 显示排行榜
     */
    private boolean handleLeaderboardCommand(CommandSender sender, String[] args) {
        String statType = "kills";
        int limit = 10;

        // 解析参数
        if (args.length >= 1) {
            String arg = args[0].toLowerCase();
            if (STAT_TYPES.contains(arg)) {
                statType = arg;
            } else {
                try {
                    limit = Math.min(Integer.parseInt(arg), 20);
                } catch (NumberFormatException e) {
                    // 静默跳过，保持数据兼容
            }
            }
        }

        if (args.length >= 2) {
            try {
                limit = Math.min(Integer.parseInt(args[1]), 20);
            } catch (NumberFormatException e) {
                sender.sendMessage("§c无效的数量: " + args[1]);
                return true;
            }
        }

        // 获取 Top 列表
        List<TopPlayerData> topPlayers = rankingService.getTopPlayers(statType, limit, RankPeriod.ALLTIME);

        sender.sendMessage("§6§l=== " + getStatDisplayName(statType) + "排行榜 ===");
        sender.sendMessage("§7显示前 " + limit + " 名");

        if (topPlayers.isEmpty()) {
            sender.sendMessage("§7暂无数据");
            return true;
        }

        for (TopPlayerData entry : topPlayers) {
            String rankIcon = getRankIcon(entry.position());
            String playerName = getPlayerName(entry.playerId());
            String valueStr = formatStatValue(statType, entry.value());

            String color = switch (entry.position()) {
                case 1 -> "§6";  // 金色
                case 2 -> "§f";  // 银色
                case 3 -> "§c";  // 铜色
                default -> "§7";
            };

            sender.sendMessage(String.format("%s%s §f%s §7- %s%s",
                rankIcon, entry.position(), playerName, color, valueStr));
        }

        sender.sendMessage("§7===================");
        sender.sendMessage("§7使用 §e/rank [玩家] §7查看个人排名");

        return true;
    }

    /**
     * /stats [玩家] - 显示玩家详细统计
     */
    private boolean handleStatsCommand(CommandSender sender, String[] args) {
        Player target;

        if (args.length >= 1) {
            OfflinePlayer offlineTarget = Bukkit.getOfflinePlayer(args[0]);
            target = Bukkit.getPlayer(offlineTarget.getUniqueId());
            if (target == null) {
                target = sender instanceof Player ? (Player) sender : null;
            }
        } else {
            if (!(sender instanceof Player)) {
                sender.sendMessage("§c请指定玩家: /stats <玩家>");
                return true;
            }
            target = (Player) sender;
        }

        if (target == null) {
            sender.sendMessage("§c玩家不存在");
            return true;
        }

        UUID targetId = target.getUniqueId();

        // 获取 PvP 统计
        PvPStats pvpStats = pvpStatsService != null ? pvpStatsService.getStats(targetId) : null;

        sender.sendMessage("§6§l=== 玩家统计 ===");
        sender.sendMessage("§e玩家: §f" + target.getName());
        // 分隔

        if (pvpStats != null) {
            sender.sendMessage("§b§lPVP 统计");
            sender.sendMessage("  §7击杀: §f" + pvpStats.getKills());
            sender.sendMessage("  §7死亡: §f" + pvpStats.getDeaths());
            sender.sendMessage("  §7助攻: §f" + pvpStats.getAssists());
            sender.sendMessage("  §7K/D: §f" + String.format("%.2f", pvpStats.getKDRatio()));
            sender.sendMessage("  §7KDA: §f" + String.format("%.2f", pvpStats.getKDA()));
            sender.sendMessage("  §7最高连杀: §f" + pvpStats.getBestKillStreak());
            // 分隔
            sender.sendMessage("§b§l决斗统计");
            sender.sendMessage("  §7胜利: §f" + pvpStats.getDuelWins());
            sender.sendMessage("  §7失败: §f" + pvpStats.getDuelLosses());
            sender.sendMessage("  §7胜率: §f" + String.format("%.1f%%", pvpStats.getDuelWinRate()));
        } else {
            sender.sendMessage("§7无PVP统计数据");
        }

        // 分隔
        sender.sendMessage("§b§l在线时间");
        rankingService.getOnlineTime(targetId, RankPeriod.ALLTIME).thenAccept(onlineTime -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                sender.sendMessage("  §7累计在线: §f" + formatDuration(onlineTime));
            });
        }).exceptionally(ex -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                sender.sendMessage("  §7累计在线: §c获取失败");
            });
            return null;
        });

        // 分隔
        sender.sendMessage("§7===================");

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String input = args[0].toLowerCase();
            // 显示玩家列表或命令子选项
            if (command.getName().equals("rank")) {
                completions.addAll(Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(input))
                    .collect(Collectors.toList()));
                if ("kills".startsWith(input)) completions.add("kills");
                if ("deaths".startsWith(input)) completions.add("deaths");
                if ("playtime".startsWith(input)) completions.add("playtime");
            } else {
                // leaderboard/top 命令
                if ("kills".startsWith(input)) completions.add("kills");
                if ("deaths".startsWith(input)) completions.add("deaths");
                if ("playtime".startsWith(input)) completions.add("playtime");
                if ("kdratio".startsWith(input)) completions.add("kdratio");
                completions.add("10");
                completions.add("5");
            }
        } else if (args.length == 2) {
            String input = args[1].toLowerCase();
            if (command.getName().equals("rank")) {
                if ("kills".startsWith(input)) completions.add("kills");
                if ("deaths".startsWith(input)) completions.add("deaths");
                if ("playtime".startsWith(input)) completions.add("playtime");
            } else {
                try {
                    Integer.parseInt(args[0]);
                    completions.add("5");
                    completions.add("10");
                    completions.add("15");
                    completions.add("20");
                } catch (NumberFormatException e) {
                    completions.add("5");
                    completions.add("10");
                }
            }
        } else if (args.length == 3 && command.getName().equals("rank")) {
            String input = args[2].toLowerCase();
            if ("daily".startsWith(input)) completions.add("daily");
            if ("weekly".startsWith(input)) completions.add("weekly");
            if ("monthly".startsWith(input)) completions.add("monthly");
            if ("alltime".startsWith(input)) completions.add("alltime");
        }

        return completions;
    }

    // ==================== 辅助方法 ====================

    private RankPeriod parsePeriod(String period) {
        return switch (period.toLowerCase()) {
            case "daily", "d" -> RankPeriod.DAILY;
            case "weekly", "w" -> RankPeriod.WEEKLY;
            case "monthly", "m" -> RankPeriod.MONTHLY;
            case "yearly", "y" -> RankPeriod.YEARLY;
            case "alltime", "all", "a" -> RankPeriod.ALLTIME;
            default -> null;
        };
    }

    private String getStatDisplayName(String statType) {
        return switch (statType) {
            case "kills" -> "击杀榜";
            case "deaths" -> "死亡榜";
            case "playtime" -> "在线时间榜";
            case "kdratio" -> "K/D榜";
            default -> statType;
        };
    }

    private String formatStatValue(String statType, long value) {
        return switch (statType) {
            case "kills" -> value + " 击杀";
            case "deaths" -> value + " 死亡";
            case "playtime" -> formatDuration(value);
            case "kdratio" -> String.format("%.2f", (double) value);
            default -> String.valueOf(value);
        };
    }

    private String formatDuration(long seconds) {
        if (seconds < 60) {
            return seconds + " 秒";
        }
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        if (hours > 0) {
            return hours + " 小时 " + minutes + " 分钟";
        }
        return minutes + " 分钟";
    }

    private String getRankIcon(int position) {
        return switch (position) {
            case 1 -> "★";  // ★
            case 2 -> "☆";   // ☆
            case 3 -> "☆";   // ☆
            default -> "";
        };
    }

    private String getPlayerName(UUID playerId) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerId);
        return player.getName() != null ? player.getName() : playerId.toString().substring(0, 8);
    }

    /**
     * 获取 K/D 比率
     */
    private double getKDRatio(UUID playerId) {
        try {
            long kills = rankingService.getKillCount(playerId, RankPeriod.ALLTIME).get();
            long deaths = rankingService.getDeathCount(playerId, RankPeriod.ALLTIME).get();
            return deaths > 0 ? (double) kills / deaths : kills;
        } catch (Exception e) {
            plugin.getLogger().warning("获取 K/D 比率失败: " + e.getMessage());
            return 0.0;
        }
    }
}
