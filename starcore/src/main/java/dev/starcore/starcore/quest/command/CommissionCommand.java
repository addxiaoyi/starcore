package dev.starcore.starcore.quest.command;

import dev.starcore.starcore.foundation.economy.EconomyService;
import dev.starcore.starcore.mechanics.ReputationService;
import dev.starcore.starcore.quest.Commission;
import dev.starcore.starcore.quest.CommissionBoard;
import dev.starcore.starcore.quest.CommissionService;
import dev.starcore.starcore.quest.QuestDifficulty;
import dev.starcore.starcore.quest.QuestService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 委托命令处理类
 * 提供玩家委托系统的交互界面
 */
public class CommissionCommand implements CommandExecutor, TabCompleter {

    private final CommissionService commissionService;
    private final QuestService questService;
    private final EconomyService economyService;
    private final ReputationService reputationService;

    private static final int ITEMS_PER_PAGE = 10;

    public CommissionCommand(CommissionService commissionService, QuestService questService,
                            EconomyService economyService, ReputationService reputationService) {
        this.commissionService = commissionService;
        this.questService = questService;
        this.economyService = economyService;
        this.reputationService = reputationService;
    }

    @Override
    public boolean onCommand(CommandSender sender, org.bukkit.command.Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "board" -> handleBoard(sender, args);
            case "list" -> handleList(sender, args);
            case "create" -> handleCreate(sender, args);
            case "accept" -> handleAccept(sender, args);
            case "complete" -> handleComplete(sender, args);
            case "cancel" -> handleCancel(sender, args);
            case "abandon" -> handleAbandon(sender, args);
            case "confirm" -> handleConfirm(sender, args);
            case "info" -> handleInfo(sender, args);
            case "stats" -> handleStats(sender, args);
            case "rank" -> handleRank(sender, args);
            case "leaderboard" -> handleLeaderboard(sender, args);
            case "progress" -> handleProgress(sender, args);
            case "refresh" -> handleRefresh(sender);
            default -> sender.sendMessage(Component.text("未知命令，使用 /commission 查看帮助", NamedTextColor.RED));
        }

        return true;
    }

    private void handleBoard(CommandSender sender, String[] args) {
        int page = 1;
        if (args.length > 1) {
            try {
                page = Math.max(1, Integer.parseInt(args[1]));
            } catch (NumberFormatException e) {
                Bukkit.getLogger().warning("[CommissionCommand] Invalid page number format: " + args[1]);
            }
                        // 静默跳过，保持数据兼容
        }

        CommissionBoard.SortType sortType = args.length > 2
            ? parseSortType(args[2])
            : CommissionBoard.SortType.REWARD_DESC;

        List<Commission> allCommissions = commissionService.getCommissionBoard()
            .getAllCommissions();
        List<Commission> commissions = commissionService.getCommissionBoard()
            .sort(allCommissions, sortType);

        // 分页过滤
        int totalPages = (int) Math.ceil(commissions.size() / (double) ITEMS_PER_PAGE);
        page = Math.min(page, Math.max(1, totalPages));

        final int finalPage = page;
        sender.sendMessage(Component.text("========== 委托板 (第 " + finalPage + "/" + Math.max(1, totalPages) + " 页) ==========", NamedTextColor.GOLD));

        int start = (finalPage - 1) * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, commissions.size());

        for (int i = start; i < end; i++) {
            Commission commission = commissions.get(i);
            String status = getStatusDisplay(commission);
            String difficultyColor = getDifficultyColor(commission.getDifficulty());

            Component item = Component.text()
                .content(String.format("[%d] ", i + 1))
                .color(NamedTextColor.GRAY)
                .append(Component.text(commission.getTitle(), NamedTextColor.YELLOW))
                .append(Component.text(" - " + difficultyColor + commission.getDifficulty().getDisplayName(), NamedTextColor.WHITE))
                .append(Component.text(" [" + status + "]", NamedTextColor.GREEN))
                .append(Component.text(" 赏金: " + String.format("%.2f", commission.getReward()), NamedTextColor.GOLD))
                .build();

            sender.sendMessage(item);
        }

        if (totalPages > 1) {
            sender.sendMessage(Component.text("使用 /commission board " + (finalPage + 1) + " 查看下一页", NamedTextColor.GRAY));
        }
    }

    private void handleList(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("此命令只能由玩家执行", NamedTextColor.RED));
            return;
        }

        UUID playerId = player.getUniqueId();
        String filter = args.length > 1 ? args[1].toLowerCase() : "all";

        sender.sendMessage(Component.text("========== 我的委托 ==========", NamedTextColor.GOLD));

        // 发布列表
        if (filter.equals("all") || filter.equals("published")) {
            List<Commission> published = commissionService.getPlayerPublishedCommissions(playerId);
            sender.sendMessage(Component.text("已发布 (" + published.size() + "):", NamedTextColor.YELLOW));

            if (published.isEmpty()) {
                sender.sendMessage(Component.text("  暂无发布的委托", NamedTextColor.GRAY));
            } else {
                for (Commission commission : published) {
                    sender.sendMessage(Component.text()
                        .content("  - " + commission.getTitle())
                        .color(NamedTextColor.WHITE)
                        .hoverEvent(HoverEvent.showText(Component.text("点击查看详情\n/comission info " + commission.getId())))
                        .clickEvent(ClickEvent.runCommand("/commission info " + commission.getId()))
                    );
                }
            }
        }

        // 接取列表
        if (filter.equals("all") || filter.equals("accepted")) {
            List<Commission> accepted = commissionService.getPlayerAcceptedCommissions(playerId);
            sender.sendMessage(Component.text("已接取 (" + accepted.size() + "):", NamedTextColor.YELLOW));

            if (accepted.isEmpty()) {
                sender.sendMessage(Component.text("  暂无接取的委托", NamedTextColor.GRAY));
            } else {
                for (Commission commission : accepted) {
                    sender.sendMessage(Component.text()
                        .content("  - " + commission.getTitle())
                        .color(NamedTextColor.WHITE)
                        .hoverEvent(HoverEvent.showText(Component.text("点击查看详情\n/comission info " + commission.getId())))
                        .clickEvent(ClickEvent.runCommand("/commission info " + commission.getId()))
                    );
                }
            }
        }
    }

    private void handleCreate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("此命令只能由玩家执行", NamedTextColor.RED));
            return;
        }

        if (args.length < 4) {
            sender.sendMessage(Component.text("用法: /commission create <标题> <描述> <赏金> [最低等级] [类型] [难度]", NamedTextColor.RED));
            return;
        }

        try {
            String title = args[1];
            String description = args[2];
            double reward = Double.parseDouble(args[3]);
            int minLevel = args.length > 4 ? Integer.parseInt(args[4]) : 0;
            Commission.CommissionType type = args.length > 5
                ? parseCommissionType(args[5])
                : Commission.CommissionType.CUSTOM;
            QuestDifficulty difficulty = args.length > 6
                ? parseQuestDifficulty(args[6])
                : QuestDifficulty.NORMAL;

            Commission commission = new Commission(title, description, reward);
            commission.setPublisherId(player.getUniqueId());
            commission.setPublisherName(player.getName());
            commission.setMinLevel(minLevel);
            commission.setType(type);
            commission.setDifficulty(difficulty);

            CommissionService.CommissionCreateResult result = commissionService.createCommission(player, commission);

            if (result.isSuccess()) {
                sender.sendMessage(Component.text("委托创建成功！ID: " + result.getCommissionId(), NamedTextColor.GREEN));
            } else {
                sender.sendMessage(Component.text("创建失败: " + result.getMessage(), NamedTextColor.RED));
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("参数错误：赏金或等级必须是数字", NamedTextColor.RED));
        }
    }

    private void handleAccept(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("此命令只能由玩家执行", NamedTextColor.RED));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(Component.text("用法: /commission accept <ID或编号>", NamedTextColor.RED));
            return;
        }

        Commission commission = findCommissionId(args[1]);
        if (commission == null) {
            sender.sendMessage(Component.text("未找到委托，请检查ID或编号", NamedTextColor.RED));
            return;
        }

        CommissionService.CommissionAcceptResult result = commissionService.acceptCommission(player, commission.getId());

        if (result.isSuccess()) {
            sender.sendMessage(Component.text("成功接取委托！", NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text("接取失败: " + result.getMessage(), NamedTextColor.RED));
        }
    }

    private void handleComplete(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("此命令只能由玩家执行", NamedTextColor.RED));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(Component.text("用法: /commission complete <ID或编号>", NamedTextColor.RED));
            return;
        }

        Commission commission = findCommissionId(args[1]);
        if (commission == null) {
            sender.sendMessage(Component.text("未找到委托，请检查ID或编号", NamedTextColor.RED));
            return;
        }

        // 使用带玩家验证的新方法
        CommissionService.CommissionCompleteResult result = commissionService.completeCommission(commission.getId(), player.getUniqueId());

        if (result.isSuccess()) {
            sender.sendMessage(Component.text("委托完成！获得赏金: " + String.format("%.2f", result.getReward()), NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text("完成失败: " + result.getMessage(), NamedTextColor.RED));

            // 如果是进度未达标，显示当前进度
            if (commission.needsVerification() && !commission.isTargetComplete()) {
                String progressInfo = getCommissionProgress(commission);
                sender.sendMessage(Component.text(progressInfo, NamedTextColor.YELLOW));
            }
        }
    }

    /**
     * 获取委托进度信息
     */
    private String getCommissionProgress(Commission commission) {
        if (!commission.needsVerification()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("当前进度: ");
        sb.append(commission.getCurrentProgress()).append("/").append(commission.getTargetAmount());

        switch (commission.getType()) {
            case KILL:
                sb.append(" (击杀 ").append(commission.getTargetEntity() != null ? commission.getTargetEntity() : "目标").append(")");
                break;
            case COLLECT:
                sb.append(" (收集 ").append(commission.getTargetItem() != null ? commission.getTargetItem() : "物品").append(")");
                break;
            case BUILD:
                sb.append(" (建造 - 请通知发布者确认)");
                break;
            case EXPLORE:
                sb.append(" (探索 ").append(commission.getTargetLocation() != null ? commission.getTargetLocation() : "目标地点").append(")");
                break;
        }

        return sb.toString();
    }

    private void handleCancel(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("此命令只能由玩家执行", NamedTextColor.RED));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(Component.text("用法: /commission cancel <ID或编号>", NamedTextColor.RED));
            return;
        }

        Commission commission = findCommissionId(args[1]);
        if (commission == null) {
            sender.sendMessage(Component.text("未找到委托，请检查ID或编号", NamedTextColor.RED));
            return;
        }

        boolean success = commissionService.cancelCommission(player.getUniqueId(), commission.getId());

        if (success) {
            sender.sendMessage(Component.text("委托已取消", NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text("取消失败：只有发布者可以取消未被接取的委托", NamedTextColor.RED));
        }
    }

    private void handleAbandon(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("此命令只能由玩家执行", NamedTextColor.RED));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(Component.text("用法: /commission abandon <ID或编号>", NamedTextColor.RED));
            return;
        }

        Commission commission = findCommissionId(args[1]);
        if (commission == null) {
            sender.sendMessage(Component.text("未找到委托，请检查ID或编号", NamedTextColor.RED));
            return;
        }

        boolean success = commissionService.abandonCommission(player.getUniqueId(), commission.getId());

        if (success) {
            sender.sendMessage(Component.text("已放弃委托", NamedTextColor.YELLOW));
        } else {
            sender.sendMessage(Component.text("放弃失败：只有接取者可以放弃委托", NamedTextColor.RED));
        }
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("用法: /commission info <ID或编号>", NamedTextColor.RED));
            return;
        }

        Commission commission = findCommissionId(args[1]);
        if (commission == null) {
            sender.sendMessage(Component.text("未找到委托，请检查ID或编号", NamedTextColor.RED));
            return;
        }

        // 显示详细信息
        sender.sendMessage(Component.text("========== 委托详情 ==========", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("ID: " + commission.getId(), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("标题: " + commission.getTitle(), NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("描述: " + commission.getDescription(), NamedTextColor.WHITE));
        sender.sendMessage(Component.text("类型: " + commission.getType().getDisplayName(), NamedTextColor.AQUA));
        sender.sendMessage(Component.text("难度: " + commission.getDifficulty().getColoredName(), NamedTextColor.WHITE));
        sender.sendMessage(Component.text("赏金: " + String.format("%.2f", commission.getReward()), NamedTextColor.GOLD));
        sender.sendMessage(Component.text("最低等级: " + commission.getMinLevel(), NamedTextColor.RED));
        sender.sendMessage(Component.text("状态: " + commission.getStatus(), NamedTextColor.GREEN));
        sender.sendMessage(Component.text("剩余时间: " + commission.getRemainingTimeText(), NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("发布者: " + commission.getPublisherName(), NamedTextColor.DARK_PURPLE));

        if (!commission.getRequirements().isEmpty()) {
            sender.sendMessage(Component.text("要求:", NamedTextColor.RED));
            for (String req : commission.getRequirements()) {
                sender.sendMessage(Component.text("  - " + req, NamedTextColor.GRAY));
            }
        }
    }

    private void handleStats(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("此命令只能由玩家执行", NamedTextColor.RED));
            return;
        }

        UUID playerId = player.getUniqueId();

        List<Commission> published = commissionService.getPlayerPublishedCommissions(playerId);
        List<Commission> accepted = commissionService.getPlayerAcceptedCommissions(playerId);
        CommissionService.CommissionStats stats = commissionService.getPlayerStats(playerId);

        sender.sendMessage(Component.text("========== 委托统计 ==========", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("已发布: " + published.size() + "/" + commissionService.getMaxCommissionsPerPlayer(), NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("已接取: " + accepted.size() + "/" + commissionService.getMaxAcceptedCommissions(), NamedTextColor.YELLOW));

        // 显示个人统计
        sender.sendMessage(Component.text("", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("个人成就:", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("  总完成: " + stats.getTotalCompleted() + " 次", NamedTextColor.WHITE));
        sender.sendMessage(Component.text("  总收益: " + String.format("%.2f", stats.getTotalEarned()) + " 金币", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("  平均收益: " + String.format("%.2f", stats.getAverageReward()) + " 金币/次", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("  连续天数: " + stats.getCurrentStreak() + " 天", NamedTextColor.GREEN));
        sender.sendMessage(Component.text("  最长连续: " + stats.getLongestStreak() + " 天", NamedTextColor.AQUA));
    }

    /**
     * 查看个人排名
     */
    private void handleRank(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("此命令只能由玩家执行", NamedTextColor.RED));
            return;
        }

        String type = args.length > 1 ? args[1].toLowerCase() : "completed";
        UUID playerId = player.getUniqueId();

        int rank = commissionService.getPlayerRank(playerId, type);
        CommissionService.CommissionStats stats = commissionService.getPlayerStats(playerId);

        sender.sendMessage(Component.text("========== 我的排名 ==========", NamedTextColor.GOLD));

        String rankName = switch (type) {
            case "earned" -> "收益榜";
            case "streak" -> "连续榜";
            default -> "完成榜";
        };

        if (rank > 0) {
            sender.sendMessage(Component.text(rankName + "第 #" + rank + " 名", NamedTextColor.GOLD));
        } else {
            sender.sendMessage(Component.text(rankName + "未上榜", NamedTextColor.GRAY));
        }

        sender.sendMessage(Component.text("  " + stats.getTotalCompleted() + " 次完成", NamedTextColor.WHITE));
        sender.sendMessage(Component.text("  " + String.format("%.2f", stats.getTotalEarned()) + " 金币", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("  " + stats.getCurrentStreak() + " 天连续", NamedTextColor.GREEN));
    }

    /**
     * 查看排行榜
     */
    private void handleLeaderboard(CommandSender sender, String[] args) {
        String type = args.length > 1 ? args[1].toLowerCase() : "completed";
        int limit = args.length > 2 ? Math.min(20, Math.max(1, Integer.parseInt(args[2]))) : 10;

        String rankName = switch (type) {
            case "earned" -> "收益榜";
            case "streak" -> "连续完成榜";
            default -> "完成榜";
        };

        sender.sendMessage(Component.text("========== " + rankName + " ==========", NamedTextColor.GOLD));

        List<CommissionService.CommissionStats> leaderboard = commissionService.getLeaderboard(type, limit);

        if (leaderboard.isEmpty()) {
            sender.sendMessage(Component.text("暂无数据", NamedTextColor.GRAY));
            return;
        }

        for (int i = 0; i < leaderboard.size(); i++) {
            CommissionService.CommissionStats stats = leaderboard.get(i);
            int rank = i + 1;

            // 更新玩家名称
            String playerName = Bukkit.getOfflinePlayer(stats.getPlayerId()).getName();
            if (playerName != null) {
                stats.setPlayerName(playerName);
            }

            String suffix = switch (type) {
                case "earned" -> String.format("%.0f 金币", stats.getTotalEarned());
                case "streak" -> stats.getCurrentStreak() + " 天";
                default -> stats.getTotalCompleted() + " 次";
            };

            NamedTextColor rankColor = rank == 1 ? NamedTextColor.GOLD :
                                      rank == 2 ? NamedTextColor.WHITE :
                                      rank == 3 ? NamedTextColor.YELLOW : NamedTextColor.GRAY;

            sender.sendMessage(Component.text()
                .content(String.format("#%d %s - %s", rank, playerName != null ? playerName : "未知", suffix))
                .color(rankColor));
        }

        sender.sendMessage(Component.text("使用 /commission leaderboard <type> [limit] 查看更多", NamedTextColor.GRAY));
    }

    /**
     * 查看委托进度
     */
    private void handleProgress(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("此命令只能由玩家执行", NamedTextColor.RED));
            return;
        }

        UUID playerId = player.getUniqueId();

        // 如果指定了委托ID，显示详细进度
        if (args.length > 1) {
            Commission commission = commissionService.getCommission(args[1]);
            if (commission == null) {
                sender.sendMessage(Component.text("未找到委托", NamedTextColor.RED));
                return;
            }

            String details = commissionService.getProgressDetails(commission);
            sender.sendMessage(Component.text(details.replace("\n", "\n")));
            return;
        }

        // 显示所有进行中的委托进度
        List<CommissionService.CommissionProgress> progressList = commissionService.getPlayerProgressSummary(playerId);

        sender.sendMessage(Component.text("========== 进行中委托 ==========", NamedTextColor.GOLD));

        if (progressList.isEmpty()) {
            sender.sendMessage(Component.text("暂无进行中的委托", NamedTextColor.GRAY));
            return;
        }

        for (CommissionService.CommissionProgress progress : progressList) {
            String statusIcon = progress.getProgressPercent() >= 100 ? "§a" : "§e";
            sender.sendMessage(Component.text()
                .content(String.format("%s%s - %s [%s]",
                    statusIcon, progress.getTitle(),
                    progress.getProgressText(),
                    progress.getRemainingTimeText()))
                .hoverEvent(HoverEvent.showText(Component.text(
                    "类型: " + progress.getType().getDisplayName() + "\n" +
                    "剩余时间: " + progress.getRemainingTimeText()
                )))
                .clickEvent(ClickEvent.runCommand("/commission progress " + progress.getCommissionId()))
            );
        }
    }

    /**
     * 发布者确认委托完成
     */
    private void handleConfirm(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("此命令只能由玩家执行", NamedTextColor.RED));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(Component.text("用法: /commission confirm <ID或编号>", NamedTextColor.RED));
            return;
        }

        Commission commission = findCommissionId(args[1]);
        if (commission == null) {
            sender.sendMessage(Component.text("未找到委托", NamedTextColor.RED));
            return;
        }

        if (commission.getType() != Commission.CommissionType.BUILD) {
            sender.sendMessage(Component.text("只有建造类委托需要确认", NamedTextColor.RED));
            return;
        }

        if (!commission.getPublisherId().equals(player.getUniqueId())) {
            sender.sendMessage(Component.text("只有发布者可以确认", NamedTextColor.RED));
            return;
        }

        CommissionService.CommissionCompleteResult result = commissionService.confirmCommission(
            commission.getId(), player.getUniqueId());

        if (result.isSuccess()) {
            sender.sendMessage(Component.text("已确认委托完成！", NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text("确认失败: " + result.getMessage(), NamedTextColor.RED));
        }
    }

    private void handleRefresh(CommandSender sender) {
        if (!sender.hasPermission("starcore.commission.admin")) {
            sender.sendMessage(Component.text("没有权限", NamedTextColor.RED));
            return;
        }

        commissionService.cleanupExpiredCommissions();
        sender.sendMessage(Component.text("已清理过期委托", NamedTextColor.GREEN));
    }

    private String getStatusDisplay(Commission commission) {
        if (commission.isCompleted()) {
            return "已完成";
        } else if (commission.isExpired()) {
            return "已过期";
        } else if (commission.isAccepted()) {
            return "进行中";
        } else {
            return "待接取";
        }
    }

    private String getDifficultyColor(QuestDifficulty difficulty) {
        return switch (difficulty) {
            case EASY -> TextColor.fromCSSHexString("#4CAF50").toString();
            case NORMAL -> TextColor.fromCSSHexString("#2196F3").toString();
            case HARD -> TextColor.fromCSSHexString("#FF9800").toString();
            case EXPERT -> TextColor.fromCSSHexString("#F44336").toString();
            case NIGHTMARE -> TextColor.fromCSSHexString("#8B0000").toString();
            case LEGENDARY -> TextColor.fromCSSHexString("#9C27B0").toString();
        };
    }

    private Commission findCommissionId(String idOrIndex) {
        // 尝试直接作为ID查找
        Commission direct = commissionService.getCommissionBoard().getAllCommissions().stream()
            .filter(c -> c.getId().equals(idOrIndex))
            .findFirst()
            .orElse(null);

        if (direct != null) {
            return direct;
        }

        try {
            int index = Integer.parseInt(idOrIndex) - 1;
            List<Commission> available = commissionService.getCommissionBoard().getAvailableCommissions();
            if (index >= 0 && index < available.size()) {
                return available.get(index);
            }
        } catch (NumberFormatException e) {
            Bukkit.getLogger().warning("[CommissionCommand] Invalid commission ID format: " + idOrIndex);
        }
                        // 静默跳过，保持数据兼容

        return null;
    }

    private CommissionBoard.SortType parseSortType(String type) {
        return switch (type.toLowerCase()) {
            case "reward", "rdesc" -> CommissionBoard.SortType.REWARD_DESC;
            case "rlow", "rasc" -> CommissionBoard.SortType.REWARD_ASC;
            case "new", "ndesc" -> CommissionBoard.SortType.TIME_DESC;
            case "old", "nasc" -> CommissionBoard.SortType.TIME_ASC;
            case "high", "hdesc" -> CommissionBoard.SortType.DIFFICULTY_DESC;
            case "lowd", "dasc" -> CommissionBoard.SortType.DIFFICULTY_ASC;
            default -> CommissionBoard.SortType.REWARD_DESC;
        };
    }

    private Commission.CommissionType parseCommissionType(String type) {
        return switch (type.toLowerCase()) {
            case "collect", "col" -> Commission.CommissionType.COLLECT;
            case "kill" -> Commission.CommissionType.KILL;
            case "build" -> Commission.CommissionType.BUILD;
            case "escort" -> Commission.CommissionType.ESCORT;
            case "delivery", "deliver" -> Commission.CommissionType.DELIVERY;
            case "explore", "exploration" -> Commission.CommissionType.EXPLORE;
            default -> Commission.CommissionType.CUSTOM;
        };
    }

    private QuestDifficulty parseQuestDifficulty(String difficulty) {
        return switch (difficulty.toLowerCase()) {
            case "easy", "simple" -> QuestDifficulty.EASY;
            case "normal", "medium" -> QuestDifficulty.NORMAL;
            case "hard", "difficult" -> QuestDifficulty.HARD;
            case "expert" -> QuestDifficulty.EXPERT;
            case "nightmare", "extreme" -> QuestDifficulty.NIGHTMARE;
            case "legendary", "epic" -> QuestDifficulty.LEGENDARY;
            default -> QuestDifficulty.NORMAL;
        };
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("========== 委托系统帮助 ==========", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/commission board [页码] [排序] - 查看委托板", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/commission list [published|accepted] - 查看我的委托", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/commission create <标题> <描述> <赏金> [等级] [类型] [难度] - 发布委托", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/commission accept <ID> - 接取委托", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/commission complete <ID> - 完成委托", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/commission confirm <ID> - 确认完成(建造类)", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/commission cancel <ID> - 取消委托(仅发布者)", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/commission abandon <ID> - 放弃委托(仅接取者)", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/commission info <ID> - 查看委托详情", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/commission stats - 查看我的委托统计", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/commission rank [type] - 查看我的排名", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/commission leaderboard [type] [limit] - 查看排行榜", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/commission progress [ID] - 查看进度", NamedTextColor.YELLOW));

        if (sender.hasPermission("starcore.commission.admin")) {
            sender.sendMessage(Component.text("========== 管理员命令 ==========", NamedTextColor.RED));
            sender.sendMessage(Component.text("/commission refresh - 清理过期委托", NamedTextColor.RED));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, org.bukkit.command.Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(Arrays.asList("board", "list", "create", "accept", "complete", "cancel", "abandon", "confirm", "info", "stats", "rank", "leaderboard", "progress"));
        } else if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            switch (subCommand) {
                case "board" -> {
                    completions.add("1");
                    completions.add("reward");
                    completions.add("new");
                }
                case "list" -> {
                    completions.add("published");
                    completions.add("accepted");
                }
                case "rank" -> {
                    completions.add("completed");
                    completions.add("earned");
                    completions.add("streak");
                }
                case "leaderboard" -> {
                    completions.add("completed");
                    completions.add("earned");
                    completions.add("streak");
                }
                case "accept", "complete", "cancel", "abandon", "info", "confirm", "progress" -> {
                    // 返回可用的委托
                    for (int i = 0; i < Math.min(10, commissionService.getCommissionBoard().getAvailableCommissions().size()); i++) {
                        completions.add(String.valueOf(i + 1));
                    }
                }
            }
        }

        // 过滤以输入开头的选项
        String current = args[args.length - 1].toLowerCase();
        return completions.stream()
            .filter(s -> s.toLowerCase().startsWith(current))
            .collect(Collectors.toList());
    }
}
