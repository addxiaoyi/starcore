package dev.starcore.starcore.quest.command;

import dev.starcore.starcore.foundation.player.OnlinePlayerDirectory;
import dev.starcore.starcore.quest.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * 任务命令处理类
 * 提供玩家任务系统的交互界面
 *
 * 命令: /quest <子命令>
 */
public class QuestCommand implements CommandExecutor, TabCompleter {

    private final QuestService questService;
    private final DailyQuestService dailyQuestService;
    private final CommissionService commissionService;
    private final OnlinePlayerDirectory onlinePlayerDirectory;

    private static final int ITEMS_PER_PAGE = 8;
    private static final Logger logger = Logger.getLogger(QuestCommand.class.getName());

    public QuestCommand(QuestService questService, DailyQuestService dailyQuestService,
                        CommissionService commissionService, OnlinePlayerDirectory onlinePlayerDirectory) {
        this.questService = questService;
        this.dailyQuestService = dailyQuestService;
        this.commissionService = commissionService;
        this.onlinePlayerDirectory = onlinePlayerDirectory;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "list" -> handleList(sender, args);
            case "accept" -> handleAccept(sender, args);
            case "abandon" -> handleAbandon(sender, args);
            case "info" -> handleInfo(sender, args);
            case "stats" -> handleStats(sender);
            case "daily" -> handleDaily(sender, args);
            case "progress" -> handleProgress(sender, args);
            case "category" -> handleCategory(sender, args);
            case "available" -> handleAvailable(sender, args);
            case "complete" -> handleComplete(sender, args);
            default -> sendHelp(sender);
        }

        return true;
    }

    /**
     * 显示任务列表
     */
    private void handleList(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("此命令只能由玩家执行", NamedTextColor.RED));
            return;
        }

        int page = 1;
        if (args.length > 1) {
            try {
                page = Math.max(1, Integer.parseInt(args[1]));
            } catch (NumberFormatException e) {
                logger.warning("Invalid page number format: " + args[1] + " - using default page 1");
            }
        }

        PlayerQuest playerQuest = questService.getPlayerQuest(player.getUniqueId());
        Map<String, Quest> activeQuestMap = playerQuest.getActiveQuests();
        List<Quest> activeQuests = new ArrayList<>(activeQuestMap.values());

        sender.sendMessage(Component.text("========== 我的任务 (" + activeQuests.size() + ") ==========", NamedTextColor.GOLD));

        if (activeQuests.isEmpty()) {
            sender.sendMessage(Component.text("  暂无进行中的任务", NamedTextColor.GRAY));
            sender.sendMessage(Component.text("  使用 /quest available 查看可接取的任务", NamedTextColor.YELLOW));
            return;
        }

        int totalPages = (int) Math.ceil(activeQuests.size() / (double) ITEMS_PER_PAGE);
        page = Math.min(page, Math.max(1, totalPages));

        int start = (page - 1) * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, activeQuests.size());

        for (int i = start; i < end; i++) {
            Quest quest = activeQuests.get(i);
            double progress = quest.getCompletionPercentage();
            String progressStr = String.format("%.0f%%", progress * 100);

            Component questItem = Component.text()
                .content(String.format("[%d] ", i + 1))
                .color(NamedTextColor.GRAY)
                .append(Component.text(quest.getName(), NamedTextColor.YELLOW))
                .append(Component.text(" [" + progressStr + "]", NamedTextColor.GREEN))
                .append(Component.text(" " + getDifficultySymbol(quest.getDifficulty()), NamedTextColor.WHITE))
                .hoverEvent(HoverEvent.showText(Component.text("点击查看详情\n/quest info " + quest.getId())))
                .clickEvent(ClickEvent.runCommand("/quest info " + quest.getId()))
                .build();

            sender.sendMessage(questItem);

            // 显示目标进度
            for (int j = 0; j < quest.getObjectives().size(); j++) {
                QuestObjective obj = quest.getObjectives().get(j);
                String objStatus = obj.isCompleted() ? NamedTextColor.GREEN + "[V] " : NamedTextColor.GRAY + "[ ] ";
                sender.sendMessage(Component.text("    " + objStatus + obj.getDescription() + " " +
                    obj.getCurrentProgress() + "/" + obj.getRequiredAmount(), NamedTextColor.WHITE));
            }
        }

        if (totalPages > 1) {
            sender.sendMessage(Component.text("第 " + page + "/" + totalPages + " 页 | 使用 /quest list " + (page + 1) + " 查看更多", NamedTextColor.GRAY));
        }
    }

    /**
     * 显示可接取的任务
     */
    private void handleAvailable(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("此命令只能由玩家执行", NamedTextColor.RED));
            return;
        }

        int page = 1;
        if (args.length > 1) {
            try {
                page = Math.max(1, Integer.parseInt(args[1]));
            } catch (NumberFormatException e) {
                logger.warning("Invalid page number format: " + args[1] + " - using default page 1");
            }
        }

        List<Quest> available = questService.getAvailableQuests(player);

        sender.sendMessage(Component.text("========== 可接取任务 (" + available.size() + ") ==========", NamedTextColor.GOLD));

        if (available.isEmpty()) {
            sender.sendMessage(Component.text("  暂无可接取的任务", NamedTextColor.GRAY));
            return;
        }

        int totalPages = (int) Math.ceil(available.size() / (double) ITEMS_PER_PAGE);
        page = Math.min(page, Math.max(1, totalPages));

        int start = (page - 1) * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, available.size());

        for (int i = start; i < end; i++) {
            Quest quest = available.get(i);
            String typeIcon = getQuestTypeIcon(quest.getType());

            Component questItem = Component.text()
                .content(String.format("[%d] ", i + 1))
                .color(NamedTextColor.GRAY)
                .append(Component.text(typeIcon + " " + quest.getName(), NamedTextColor.YELLOW))
                .append(Component.text(" " + getDifficultySymbol(quest.getDifficulty()), NamedTextColor.WHITE))
                .hoverEvent(HoverEvent.showText(Component.text("点击接取任务\n/quest accept " + quest.getId())))
                .clickEvent(ClickEvent.runCommand("/quest accept " + quest.getId()))
                .build();

            sender.sendMessage(questItem);
            sender.sendMessage(Component.text("    难度: " + quest.getDifficulty().getColoredName() +
                "  类型: " + getQuestTypeName(quest.getType()), NamedTextColor.GRAY));
        }

        if (totalPages > 1) {
            sender.sendMessage(Component.text("第 " + page + "/" + totalPages + " 页 | 使用 /quest available " + (page + 1) + " 查看更多", NamedTextColor.GRAY));
        }
    }

    /**
     * 接取任务
     */
    private void handleAccept(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("此命令只能由玩家执行", NamedTextColor.RED));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(Component.text("用法: /quest accept <任务ID>", NamedTextColor.RED));
            sender.sendMessage(Component.text("使用 /quest available 查看可接取的任务", NamedTextColor.GRAY));
            return;
        }

        String questId = args[1];
        QuestService.QuestAcceptResult result = questService.acceptQuest(player, questId);

        if (result == QuestService.QuestAcceptResult.SUCCESS) {
            sender.sendMessage(Component.text("任务接取成功！", NamedTextColor.GREEN));
            Quest quest = questService.getQuest(questId);
            if (quest != null) {
                sender.sendMessage(Component.text("任务: " + quest.getName(), NamedTextColor.YELLOW));
                sender.sendMessage(Component.text("目标:", NamedTextColor.GRAY));
                for (QuestObjective obj : quest.getObjectives()) {
                    sender.sendMessage(Component.text("  - " + obj.getDescription(), NamedTextColor.WHITE));
                }
            }
        } else {
            sender.sendMessage(Component.text("接取失败: " + result.getMessage(), NamedTextColor.RED));
        }
    }

    /**
     * 放弃任务
     */
    private void handleAbandon(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("此命令只能由玩家执行", NamedTextColor.RED));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(Component.text("用法: /quest abandon <任务ID>", NamedTextColor.RED));
            return;
        }

        String questId = args[1];
        boolean success = questService.abandonQuest(player.getUniqueId(), questId);

        if (success) {
            sender.sendMessage(Component.text("任务已放弃", NamedTextColor.YELLOW));
        } else {
            sender.sendMessage(Component.text("放弃失败: 任务不存在或无法放弃", NamedTextColor.RED));
        }
    }

    /**
     * 查看任务详情
     */
    private void handleInfo(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("此命令只能由玩家执行", NamedTextColor.RED));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(Component.text("用法: /quest info <任务ID>", NamedTextColor.RED));
            return;
        }

        String questId = args[1];
        Quest quest = questService.getQuest(questId);

        if (quest == null) {
            sender.sendMessage(Component.text("未找到任务: " + questId, NamedTextColor.RED));
            return;
        }

        sender.sendMessage(Component.text("========== 任务详情 ==========", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("ID: " + quest.getId(), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("名称: " + quest.getName(), NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("描述: " + quest.getDescription(), NamedTextColor.WHITE));
        sender.sendMessage(Component.text("类型: " + getQuestTypeName(quest.getType()), NamedTextColor.AQUA));
        sender.sendMessage(Component.text("难度: " + quest.getDifficulty().getColoredName(), NamedTextColor.WHITE));
        sender.sendMessage(Component.text("分类: " + quest.getCategory(), NamedTextColor.GRAY));

        if (quest.getMinLevel() > 0) {
            sender.sendMessage(Component.text("最低等级: " + quest.getMinLevel(), NamedTextColor.RED));
        }

        // 显示目标
        sender.sendMessage(Component.text("\n目标:", NamedTextColor.GOLD));
        PlayerQuest playerQuest = questService.getPlayerQuest(player.getUniqueId());
        boolean isActive = playerQuest.hasActiveQuest(questId);
        Quest activeQuest = isActive ? playerQuest.getActiveQuest(questId) : null;

        for (int i = 0; i < quest.getObjectives().size(); i++) {
            QuestObjective obj = quest.getObjectives().get(i);
            boolean completed = activeQuest != null && activeQuest.getObjectives().get(i).isCompleted();
            String status = completed ? NamedTextColor.GREEN + "[V] " : NamedTextColor.GRAY + "[ ] ";
            String progress = activeQuest != null
                ? " " + activeQuest.getObjectives().get(i).getCurrentProgress() + "/" + obj.getRequiredAmount()
                : " 0/" + obj.getRequiredAmount();

            sender.sendMessage(Component.text("  " + status + obj.getDescription() + progress, NamedTextColor.WHITE));
        }

        // 显示奖励
        QuestReward reward = quest.getReward();
        sender.sendMessage(Component.text("\n奖励:", NamedTextColor.GOLD));
        if (reward.getMoney() > 0) {
            sender.sendMessage(Component.text("  金币: " + String.format("%.2f", reward.getMoney()), NamedTextColor.GOLD));
        }
        if (reward.getExperience() > 0) {
            sender.sendMessage(Component.text("  经验: " + reward.getExperience(), NamedTextColor.AQUA));
        }
        if (!reward.getItems().isEmpty()) {
            sender.sendMessage(Component.text("  物品: " + reward.getItems().size() + " 件", NamedTextColor.WHITE));
        }

        if (isActive) {
            double progress = activeQuest.getCompletionPercentage();
            sender.sendMessage(Component.text("\n当前进度: " + String.format("%.0f%%", progress * 100), NamedTextColor.GREEN));

            if (activeQuest.isAllObjectivesCompleted()) {
                sender.sendMessage(Component.text("所有目标已完成！使用 /quest complete " + questId + " 完成任务", NamedTextColor.GREEN));
            }
        } else if (!playerQuest.hasCompletedQuest(questId)) {
            sender.sendMessage(Component.text("\n使用 /quest accept " + questId + " 接取任务", NamedTextColor.GREEN));
        }
    }

    /**
     * 完成任务
     */
    private void handleComplete(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("此命令只能由玩家执行", NamedTextColor.RED));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(Component.text("用法: /quest complete <任务ID>", NamedTextColor.RED));
            return;
        }

        String questId = args[1];
        QuestService.QuestCompleteResult result = questService.completeQuest(player, questId);

        if (result.isSuccess()) {
            sender.sendMessage(Component.text("任务完成！", NamedTextColor.GREEN));
            if (result.getReward() != null) {
                QuestReward reward = result.getReward();
                if (reward.getMoney() > 0) {
                    sender.sendMessage(Component.text("获得金币: " + String.format("%.2f", reward.getMoney()), NamedTextColor.GOLD));
                }
                if (reward.getExperience() > 0) {
                    sender.sendMessage(Component.text("获得经验: " + reward.getExperience(), NamedTextColor.AQUA));
                }
            }
        } else {
            sender.sendMessage(Component.text("完成失败: " + result.getMessage(), NamedTextColor.RED));
        }
    }

    /**
     * 查看任务统计
     */
    private void handleStats(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("此命令只能由玩家执行", NamedTextColor.RED));
            return;
        }

        UUID playerId = player.getUniqueId();
        QuestStatistics stats = questService.getStatistics(playerId);
        PlayerQuest playerQuest = questService.getPlayerQuest(playerId);

        sender.sendMessage(Component.text("========== 任务统计 ==========", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("已完成任务: " + stats.getTotalQuestsCompleted(), NamedTextColor.GREEN));
        sender.sendMessage(Component.text("进行中任务: " + playerQuest.getActiveQuestCount(), NamedTextColor.YELLOW));

        // 显示每日任务进度
        DailyQuestService.DailyProgress dailyProgress = dailyQuestService.getDailyProgress(playerId);
        sender.sendMessage(Component.text("\n========== 每日任务 ==========", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("今日进度: " + dailyProgress.getProgressText(), NamedTextColor.YELLOW));
        sender.sendMessage(Component.text(dailyProgress.getProgressBar(), NamedTextColor.WHITE));

        // 显示委托统计
        List<Commission> acceptedCommissions = commissionService.getPlayerAcceptedCommissions(playerId);
        List<Commission> publishedCommissions = commissionService.getPlayerPublishedCommissions(playerId);
        sender.sendMessage(Component.text("\n========== 委托统计 ==========", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("已接取委托: " + acceptedCommissions.size() + "/" + commissionService.getMaxAcceptedCommissions(), NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("已发布委托: " + publishedCommissions.size() + "/" + commissionService.getMaxCommissionsPerPlayer(), NamedTextColor.YELLOW));
    }

    /**
     * 查看每日任务
     */
    private void handleDaily(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("此命令只能由玩家执行", NamedTextColor.RED));
            return;
        }

        UUID playerId = player.getUniqueId();

        if (args.length > 1 && args[1].equalsIgnoreCase("refresh")) {
            // 手动刷新每日任务
            dailyQuestService.manualRefresh(player, false);
            return;
        }

        List<Quest> dailyQuests = dailyQuestService.generateDailyQuests(player);
        DailyQuestService.DailyProgress progress = dailyQuestService.getDailyProgress(playerId);

        sender.sendMessage(Component.text("========== 每日任务 ==========", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("今日进度: " + progress.getProgressText(), NamedTextColor.YELLOW));
        sender.sendMessage(Component.text(progress.getProgressBar(), NamedTextColor.WHITE));
        sender.sendMessage(Component.text("刷新时间: 每日 " + dailyQuestService.getRefreshHour() + ":00", NamedTextColor.GRAY));

        if (dailyQuests.isEmpty()) {
            sender.sendMessage(Component.text("  暂无每日任务", NamedTextColor.GRAY));
            return;
        }

        sender.sendMessage(Component.text("\n任务列表:", NamedTextColor.GOLD));
        PlayerQuest playerQuest = questService.getPlayerQuest(playerId);

        for (int i = 0; i < dailyQuests.size(); i++) {
            Quest quest = dailyQuests.get(i);
            boolean completed = playerQuest.hasCompletedQuest(quest.getId());
            String status = completed ? NamedTextColor.GREEN + "[V] " : NamedTextColor.GRAY + "[ ] ";
            String prefix = completed ? "" : String.format("[%d] ", i + 1);

            if (completed) {
                sender.sendMessage(Component.text(prefix + status + quest.getName(), NamedTextColor.GRAY));
            } else {
                sender.sendMessage(Component.text(prefix + status + quest.getName(), NamedTextColor.YELLOW));
            }
        }

        sender.sendMessage(Component.text("\n使用 /quest daily refresh 手动刷新（消耗金币）", NamedTextColor.GRAY));
    }

    /**
     * 查看任务进度
     */
    private void handleProgress(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("此命令只能由玩家执行", NamedTextColor.RED));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(Component.text("用法: /quest progress <任务ID>", NamedTextColor.RED));
            return;
        }

        String questId = args[1];
        PlayerQuest playerQuest = questService.getPlayerQuest(player.getUniqueId());
        Quest activeQuest = playerQuest.getActiveQuest(questId);

        if (activeQuest == null) {
            sender.sendMessage(Component.text("未进行此任务或任务已完成", NamedTextColor.RED));
            return;
        }

        sender.sendMessage(Component.text("========== 任务进度 ==========", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("任务: " + activeQuest.getName(), NamedTextColor.YELLOW));

        double overallProgress = activeQuest.getCompletionPercentage();
        sender.sendMessage(Component.text("总进度: " + createProgressBar(overallProgress), NamedTextColor.WHITE));

        sender.sendMessage(Component.text("\n目标进度:", NamedTextColor.GOLD));
        for (int i = 0; i < activeQuest.getObjectives().size(); i++) {
            QuestObjective obj = activeQuest.getObjectives().get(i);
            String status = obj.isCompleted() ? NamedTextColor.GREEN + "[V] " : NamedTextColor.GRAY + "[ ] ";
            String progressBar = createProgressBar(obj.getProgressPercentage() / 100.0);

            sender.sendMessage(Component.text("  " + status + obj.getDescription(), NamedTextColor.WHITE));
            sender.sendMessage(Component.text("      " + obj.getCurrentProgress() + "/" + obj.getRequiredAmount() + " " + progressBar, NamedTextColor.GRAY));
        }

        if (activeQuest.isAllObjectivesCompleted()) {
            sender.sendMessage(Component.text("\n所有目标已完成！可使用 /quest complete " + questId + " 完成任务", NamedTextColor.GREEN));
        }
    }

    /**
     * 按分类查看任务
     */
    private void handleCategory(CommandSender sender, String[] args) {
        if (args.length < 2) {
            // 显示所有分类
            sender.sendMessage(Component.text("========== 任务分类 ==========", NamedTextColor.GOLD));
            sender.sendMessage(Component.text("可用分类:", NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("  main - 主线任务", NamedTextColor.WHITE));
            sender.sendMessage(Component.text("  side - 支线任务", NamedTextColor.WHITE));
            sender.sendMessage(Component.text("  daily - 每日任务", NamedTextColor.WHITE));
            sender.sendMessage(Component.text("  event - 事件任务", NamedTextColor.WHITE));
            sender.sendMessage(Component.text("\n用法: /quest category <分类>", NamedTextColor.GRAY));
            return;
        }

        String category = args[1].toLowerCase();
        List<Quest> quests = questService.getQuestsByCategory(category);

        sender.sendMessage(Component.text("========== " + category + " 任务 (" + quests.size() + ") ==========", NamedTextColor.GOLD));

        if (quests.isEmpty()) {
            sender.sendMessage(Component.text("  暂无该分类的任务", NamedTextColor.GRAY));
            return;
        }

        for (Quest quest : quests) {
            sender.sendMessage(Component.text("  - " + quest.getName() + " " + getDifficultySymbol(quest.getDifficulty()), NamedTextColor.WHITE));
        }
    }

    /**
     * 发送帮助信息
     */
    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("========== 任务系统帮助 ==========", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/quest list [页码] - 查看进行中的任务", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/quest available [页码] - 查看可接取的任务", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/quest accept <ID> - 接取任务", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/quest abandon <ID> - 放弃任务", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/quest complete <ID> - 完成任务", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/quest info <ID> - 查看任务详情", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/quest progress <ID> - 查看任务进度", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/quest stats - 查看任务统计", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/quest daily - 查看每日任务", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/quest category [分类] - 按分类查看任务", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("\n委托系统:", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/commission board - 查看委托板", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/commission create - 发布委托", NamedTextColor.YELLOW));
    }

    // ==================== 辅助方法 ====================

    private String getDifficultySymbol(QuestDifficulty difficulty) {
        return switch (difficulty) {
            case EASY -> "[★]";
            case NORMAL -> "[★★]";
            case HARD -> "[★★★]";
            case EXPERT -> "[★★★★]";
            case LEGENDARY -> "[★★★★★]";
            case NIGHTMARE -> "[★★★★★★]";
        };
    }

    private String getQuestTypeIcon(QuestType type) {
        return switch (type) {
            case DAILY -> "📅";
            case WEEKLY -> "📆";
            case MAIN -> "📜";
            case SIDE -> "📋";
            case COMMISSION -> "📦";
            case REPEATABLE -> "🔄";
            case ACHIEVEMENT -> "🏆";
            case EVENT -> "🎉";
            default -> "📝";
        };
    }

    private String getQuestTypeName(QuestType type) {
        return switch (type) {
            case DAILY -> "每日";
            case WEEKLY -> "每周";
            case MAIN -> "主线";
            case SIDE -> "支线";
            case COMMISSION -> "委托";
            case REPEATABLE -> "重复";
            case ACHIEVEMENT -> "成就";
            case EVENT -> "活动";
            default -> "普通";
        };
    }

    private String createProgressBar(double progress) {
        int barLength = 10;
        int filled = (int) (progress * barLength);
        StringBuilder bar = new StringBuilder("[");
        for (int i = 0; i < barLength; i++) {
            bar.append(i < filled ? "■" : "□");
        }
        bar.append("] ").append(String.format("%.0f%%", progress * 100));
        return bar.toString();
    }

    // ==================== TabCompleter ====================

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(Arrays.asList("list", "available", "accept", "abandon", "info", "complete", "stats", "daily", "progress", "category"));
        } else if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            switch (subCommand) {
                case "accept", "abandon", "info", "complete", "progress" -> {
                    if (sender instanceof Player player) {
                        if (subCommand.equals("accept") || subCommand.equals("info")) {
                            // 可接取任务
                            questService.getAvailableQuests(player).stream()
                                .limit(10)
                                .map(Quest::getId)
                                .filter(id -> id.toLowerCase().startsWith(args[1].toLowerCase()))
                                .forEach(completions::add);
                        }
                        // 进行中任务
                        Map<String, Quest> activeQuests = questService.getPlayerQuest(player.getUniqueId()).getActiveQuests();
                        activeQuests.values().stream()
                            .map(Quest::getId)
                            .filter(id -> id.toLowerCase().startsWith(args[1].toLowerCase()))
                            .forEach(completions::add);
                    }
                }
                case "category" -> {
                    completions.addAll(Arrays.asList("main", "side", "daily", "event", "achievement"));
                }
                case "daily" -> {
                    completions.add("refresh");
                }
            }
        }

        String current = args[args.length - 1].toLowerCase();
        return completions.stream()
            .filter(s -> s.toLowerCase().startsWith(current))
            .collect(Collectors.toList());
    }
}
