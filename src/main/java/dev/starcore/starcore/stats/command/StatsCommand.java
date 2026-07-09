package dev.starcore.starcore.stats.command;

import dev.starcore.starcore.ranking.RankPeriod;
import dev.starcore.starcore.ranking.RankingService;
import dev.starcore.starcore.stats.StatsService;
import dev.starcore.starcore.stats.analysis.TrendAnalysisService;
import dev.starcore.starcore.stats.dashboard.StatsDashboardService;
import dev.starcore.starcore.stats.export.StatsExportService;
import dev.starcore.starcore.stats.report.PeriodicReportService;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 统计报告命令
 *
 * /stats dashboard [player] - 显示统计仪表盘
 * /stats report [hourly|daily|weekly|monthly] - 生成报告
 * /stats export [json|csv|html|txt] [player] - 导出数据
 * /stats trend [player] - 显示趋势分析
 * /stats history - 显示报告历史
 * /stats top [kills|deaths|kdratio|playtime] [period] - 显示排行榜
 */
public class StatsCommand implements CommandExecutor, TabCompleter {

    private final StatsService statsService;
    private final RankingService rankingService;

    public StatsCommand(StatsService statsService, RankingService rankingService) {
        this.statsService = statsService;
        this.rankingService = rankingService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "dashboard", "dash", "d" -> {
                Player target = args.length > 1 ? Bukkit.getPlayer(args[1]) : (sender instanceof Player ? (Player) sender : null);
                if (target == null) {
                    sender.sendMessage("§c玩家不在线或未指定玩家");
                    return true;
                }
                showDashboard(sender, target);
            }
            case "report", "r" -> showReport(sender, args);
            case "export", "e" -> exportData(sender, args);
            case "trend", "t" -> showTrend(sender, args);
            case "history", "h" -> showReportHistory(sender);
            case "top" -> showTop(sender, args);
            case "server", "s" -> showServerDashboard(sender);
            case "refresh" -> {
                statsService.getDashboardService().clearCache();
                sender.sendMessage("§a缓存已刷新");
            }
            default -> sendHelp(sender);
        }

        return true;
    }

    private void showDashboard(CommandSender sender, Player target) {
        StatsDashboardService dashboard = statsService.getDashboardService();

        sender.sendMessage("§6正在加载 " + target.getName() + " 的统计仪表盘...");
        // 分隔

        dashboard.getPlayerDashboard(target.getUniqueId(), target.getName())
            .thenAccept(dashboardData -> {
                String formatted = dashboard.getFormattedDashboard(target.getUniqueId(), target.getName());
                Bukkit.getScheduler().runTask(statsService.getExportService() != null ?
                    Bukkit.getPluginManager().getPlugins()[0] : null, () -> {
                    sender.sendMessage(formatted);
                });
            })
            .exceptionally(ex -> {
                sender.sendMessage("§c加载仪表盘失败: " + ex.getMessage());
                return null;
            });
    }

    private void showServerDashboard(CommandSender sender) {
        StatsDashboardService dashboard = statsService.getDashboardService();
        var serverDash = dashboard.getGlobalDashboard();

        sender.sendMessage("§6=== 服务器统计概览 ===");
        // 分隔
        sender.sendMessage("§7总玩家数: §f" + serverDash.globalStats().totalPlayers);
        sender.sendMessage("§7活跃玩家: §f" + serverDash.globalStats().activePlayers);
        sender.sendMessage("§7总击杀数: §f" + serverDash.globalStats().totalKills);
        sender.sendMessage("§7总死亡数: §f" + serverDash.globalStats().totalDeaths);
        // 分隔
        sender.sendMessage("§6--- Top 5 击杀榜 ---");

        var topKills = serverDash.leaderboards().get("kills");
        if (topKills != null) {
            int i = 1;
            for (var player : topKills.stream().limit(5).collect(Collectors.toList())) {
                sender.sendMessage(String.format("§e%d. §f%s §7- §c%d 击杀",
                    i++, player.playerId().toString().substring(0, 8), player.value()));
            }
        }
    }

    private void showReport(CommandSender sender, String[] args) {
        PeriodicReportService reportService = statsService.getPeriodicReportService();

        PeriodicReportService.ReportType type;
        if (args.length > 1) {
            try {
                type = PeriodicReportService.ReportType.valueOf(args[1].toUpperCase());
            } catch (IllegalArgumentException e) {
                sender.sendMessage("§c无效的报告类型: " + args[1]);
                sender.sendMessage("§7可用类型: hourly, daily, weekly, monthly");
                return;
            }
        } else {
            type = PeriodicReportService.ReportType.DAILY;
        }

        sender.sendMessage("§6正在生成 " + type.getDisplayName() + "...");
        // 分隔

        reportService.triggerReport(type)
            .thenAccept(report -> {
                sender.sendMessage("§a=== " + report.period() + " ===");
                // 分隔
                sender.sendMessage(report.content());
            })
            .exceptionally(ex -> {
                sender.sendMessage("§c生成报告失败: " + ex.getMessage());
                return null;
            });
    }

    private void exportData(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§c用法: /stats export <json|csv|html|txt> [player]");
            return;
        }

        String format = args[1].toLowerCase();
        Player target = args.length > 2 ? Bukkit.getPlayer(args[2]) : (sender instanceof Player ? (Player) sender : null);

        if (target == null && !format.equals("zip")) {
            sender.sendMessage("§c请指定玩家或使用 /stats export zip 导出全部数据");
            return;
        }

        StatsExportService exportService = statsService.getExportService();
        sender.sendMessage("§6正在导出 " + format + " 格式...");

        CompletableFuture<Path> future;

        switch (format) {
            case "json" -> future = target != null ?
                exportService.exportToJson(target.getUniqueId(), target.getName()) :
                exportService.exportLeaderboardToJson("kills", 100);
            case "csv" -> future = target != null ?
                exportService.exportToCsv(target.getUniqueId(), target.getName()) :
                exportService.exportLeaderboardToCsv("kills", 100);
            case "html" -> {
                Map<String, Object> content = new LinkedHashMap<>();
                for (String statType : Arrays.asList("kills", "deaths", "kdratio", "playtime")) {
                    var top = rankingService.getTopPlayers(statType, 10, RankPeriod.ALLTIME);
                    content.put(statType + " Top 10", top);
                }
                future = exportService.exportToHtml("StarCore 统计报告", content);
            }
            case "txt" -> {
                StringBuilder content = new StringBuilder();
                content.append("StarCore 统计报告\n\n");
                for (String statType : Arrays.asList("kills", "deaths", "kdratio", "playtime")) {
                    content.append("=== ").append(statType).append(" Top 10 ===\n");
                    var top = rankingService.getTopPlayers(statType, 10, RankPeriod.ALLTIME);
                    int i = 1;
                    for (var p : top) {
                        content.append(String.format("%d. %s - %d\n", i++, p.playerId().toString().substring(0, 8), p.value()));
                    }
                    content.append("\n");
                }
                future = exportService.exportToTxt("StarCore 统计报告", content.toString());
            }
            case "zip" -> future = exportService.exportAllToZip();
            default -> {
                sender.sendMessage("§c不支持的格式: " + format);
                sender.sendMessage("§7支持格式: json, csv, html, txt, zip");
                return;
            }
        }

        future
            .thenAccept(path -> {
                sender.sendMessage("§a导出成功: " + path.getFileName());
                sender.sendMessage("§7文件位置: " + path);
            })
            .exceptionally(ex -> {
                sender.sendMessage("§c导出失败: " + ex.getMessage());
                return null;
            });
    }

    private void showTrend(CommandSender sender, String[] args) {
        Player target = args.length > 1 ? Bukkit.getPlayer(args[1]) : (sender instanceof Player ? (Player) sender : null);

        if (target == null) {
            sender.sendMessage("§c请指定玩家");
            return;
        }

        TrendAnalysisService trendService = statsService.getTrendAnalysisService();

        sender.sendMessage("§6正在分析 " + target.getName() + " 的趋势...");

        trendService.analyzePlayerTrend(target.getUniqueId())
            .thenAccept(analysis -> {
                // 分隔
                sender.sendMessage("§6=== " + target.getName() + " 趋势分析 ===");
                // 分隔

                // 总体趋势
                String trendIcon = switch (analysis.overallTrend()) {
                    case UP -> "§a↑ 上升";
                    case DOWN -> "§c↓ 下降";
                    case STABLE -> "§e↔ 稳定";
                };
                sender.sendMessage("§7总体趋势: " + trendIcon);
                sender.sendMessage(String.format("§7综合得分: §f%.2f", analysis.overallScore()));
                // 分隔

                // 击杀趋势
                String killsIcon = switch (analysis.killsTrend().direction()) {
                    case UP -> "§a↑";
                    case DOWN -> "§c↓";
                    case STABLE -> "§e↔";
                };
                sender.sendMessage("§c击杀趋势: " + killsIcon + String.format(" §7(%.1f%%)", analysis.killsTrend().changePercent() * 100));

                // K/D趋势
                String kdIcon = switch (analysis.kdTrend().direction()) {
                    case UP -> "§a↑";
                    case DOWN -> "§c↓";
                    case STABLE -> "§e↔";
                };
                sender.sendMessage("§aK/D趋势: " + kdIcon + String.format(" §7(%.1f%%)", analysis.kdTrend().changePercent() * 100));
                // 分隔

                // 建议
                if (!analysis.suggestions().isEmpty()) {
                    sender.sendMessage("§6建议:");
                    for (String suggestion : analysis.suggestions()) {
                        sender.sendMessage("  §7- " + suggestion);
                    }
                }
            })
            .exceptionally(ex -> {
                sender.sendMessage("§c分析失败: " + ex.getMessage());
                return null;
            });
    }

    private void showReportHistory(CommandSender sender) {
        PeriodicReportService reportService = statsService.getPeriodicReportService();

        sender.sendMessage("§6=== 报告历史 ===");
        // 分隔

        var history = reportService.getRecentHistory(10);
        if (history.isEmpty()) {
            sender.sendMessage("§7暂无报告历史");
            return;
        }

        for (var record : history) {
            sender.sendMessage(String.format("§e%s §7- %s",
                record.type().getDisplayName(),
                record.timestamp().toString()));
        }
    }

    private void showTop(CommandSender sender, String[] args) {
        String statType = args.length > 1 ? args[1] : "kills";
        String periodStr = args.length > 2 ? args[2] : "alltime";

        RankPeriod period;
        try {
            period = RankPeriod.valueOf(periodStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            sender.sendMessage("§c无效的时间周期: " + periodStr);
            sender.sendMessage("§7可用周期: hourly, daily, weekly, monthly, yearly, alltime");
            return;
        }

        int limit = args.length > 3 ? Math.min(50, Math.max(1, Integer.parseInt(args[3]))) : 10;

        sender.sendMessage("§6=== " + statType + " " + period.getDisplayName() + " Top " + limit + " ===");
        // 分隔

        var topPlayers = rankingService.getTopPlayers(statType, limit, period);
        int i = 1;
        for (var player : topPlayers) {
            String medal = i <= 3 ? switch (i) {
                case 1 -> "§6🥇";
                case 2 -> "§f🥈";
                case 3 -> "§c🥉";
                default -> "";
            } : "";

            sender.sendMessage(String.format("%s%d. §f%s §7- §c%d",
                medal, i++, player.playerId().toString().substring(0, 8), player.value()));
        }

        if (topPlayers.isEmpty()) {
            sender.sendMessage("§7暂无数据");
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6=== StarCore 统计命令 ===");
        // 分隔
        sender.sendMessage("§e/stats dashboard [player] §7- 显示玩家统计仪表盘");
        sender.sendMessage("§e/stats server §7- 显示服务器统计概览");
        sender.sendMessage("§e/stats report [hourly|daily|weekly|monthly] §7- 生成报告");
        sender.sendMessage("§e/stats export <json|csv|html|txt|zip> [player] §7- 导出数据");
        sender.sendMessage("§e/stats trend [player] §7- 显示趋势分析");
        sender.sendMessage("§e/stats history §7- 显示报告历史");
        sender.sendMessage("§e/stats top [kills|deaths|kdratio|playtime] [period] [limit] §7- 排行榜");
        sender.sendMessage("§e/stats refresh §7- 刷新缓存");
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return Arrays.asList("dashboard", "report", "export", "trend", "history", "top", "server", "refresh")
                .stream()
                .filter(s -> s.startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }

        if (args.length == 2) {
            return switch (args[0].toLowerCase()) {
                case "report" -> Arrays.asList("hourly", "daily", "weekly", "monthly");
                case "export" -> Arrays.asList("json", "csv", "html", "txt", "zip");
                case "top" -> Arrays.asList("kills", "deaths", "kdratio", "playtime");
                default -> Collections.emptyList();
            };
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("top")) {
            return Arrays.asList("hourly", "daily", "weekly", "monthly", "yearly", "alltime");
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("export")) {
            return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(args[2].toLowerCase()))
                .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}
