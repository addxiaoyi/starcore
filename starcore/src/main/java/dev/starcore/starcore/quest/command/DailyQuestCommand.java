package dev.starcore.starcore.quest.command;

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
import java.util.stream.Collectors;

/**
 * 每日任务命令处理类
 * 提供每日任务系统的交互界面
 *
 * 命令: /dailyquest <子命令>
 * 别名: /daily, /每日任务
 */
public class DailyQuestCommand implements CommandExecutor, TabCompleter {

    private final DailyQuestService dailyQuestService;
    private final QuestService questService;

    private static final int ITEMS_PER_PAGE = 5;

    public DailyQuestCommand(DailyQuestService dailyQuestService, QuestService questService) {
        this.dailyQuestService = dailyQuestService;
        this.questService = questService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            return handleDaily(sender);
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "list" -> handleList(sender);
            case "refresh" -> handleRefresh(sender);
            case "progress" -> handleProgress(sender);
            case "info" -> handleInfo(sender, args);
            case "reward" -> handleReward(sender);
            case "help" -> sendHelp(sender);
            default -> handleDaily(sender);
        }

        return true;
    }

    /**
     * 查看每日任务（默认命令）
     */
    private boolean handleDaily(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("此命令只能由玩家执行", NamedTextColor.RED));
            return true;
        }

        UUID playerId = player.getUniqueId();
        List<Quest> dailyQuests = dailyQuestService.generateDailyQuests(player);
        DailyQuestService.DailyProgress progress = dailyQuestService.getDailyProgress(playerId);

        sender.sendMessage(Component.text("========== 每日任务 ==========", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("今日进度: " + progress.getProgressText(), NamedTextColor.YELLOW));
        sender.sendMessage(Component.text(progress.getProgressBar(), NamedTextColor.WHITE));

        // 显示刷新时间
        long refreshHour = dailyQuestService.getRefreshHour();
        sender.sendMessage(Component.text("刷新时间: 每日 " + refreshHour + ":00", NamedTextColor.GRAY));

        if (dailyQuests.isEmpty()) {
            sender.sendMessage(Component.text("\n  暂无每日任务", NamedTextColor.GRAY));
            sender.sendMessage(Component.text("  使用 /dailyquest refresh 重新生成", NamedTextColor.YELLOW));
            return true;
        }

        sender.sendMessage(Component.text("\n任务列表:", NamedTextColor.GOLD));
        PlayerQuest playerQuest = questService.getPlayerQuest(playerId);

        for (int i = 0; i < dailyQuests.size(); i++) {
            Quest quest = dailyQuests.get(i);
            boolean completed = playerQuest.hasCompletedQuest(quest.getId());
            String status = completed ? NamedTextColor.GREEN + "[V] " : NamedTextColor.GRAY + "[ ] ";
            String prefix = completed ? "  " : String.format("%d. ", i + 1);

            Component questItem;

            if (completed) {
                questItem = Component.text(prefix + status + quest.getName(), NamedTextColor.GRAY);
            } else {
                questItem = Component.text(prefix + status + quest.getName(), NamedTextColor.YELLOW)
                    .hoverEvent(HoverEvent.showText(
                        Component.text("难度: " + quest.getDifficulty().getColoredName() + "\n" +
                            "奖励: " + formatReward(quest.getReward()) + "\n" +
                            "点击查看详情")
                    ))
                    .clickEvent(ClickEvent.runCommand("/dailyquest info " + (i + 1)));
            }

            sender.sendMessage(questItem);

            if (!completed) {
                // 显示奖励预览
                sender.sendMessage(Component.text("    奖励: " + formatRewardBrief(quest.getReward()), NamedTextColor.GRAY));
            }
        }

        sender.sendMessage(Component.text("\n命令:", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/dailyquest refresh - 刷新任务（消耗金币）", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/dailyquest info <编号> - 查看任务详情", NamedTextColor.YELLOW));

        return true;
    }

    /**
     * 显示每日任务列表
     */
    private void handleList(CommandSender sender) {
        handleDaily(sender);
    }

    /**
     * 手动刷新每日任务
     */
    private void handleRefresh(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("此命令只能由玩家执行", NamedTextColor.RED));
            return;
        }

        // 检查刷新费用
        double refreshCost = dailyQuestService.getRefreshCost();
        if (refreshCost > 0) {
            sender.sendMessage(Component.text("刷新每日任务需要消耗 " + refreshCost + " 金币", NamedTextColor.YELLOW));
        }

        boolean success = dailyQuestService.manualRefresh(player, false);

        if (!success) {
            sender.sendMessage(Component.text("刷新失败：今日任务尚未到刷新时间或金币不足", NamedTextColor.RED));
            sender.sendMessage(Component.text("强制刷新需要管理员权限", NamedTextColor.GRAY));
        }
        // 成功消息由 DailyQuestService.manualRefresh 内部发送
    }

    /**
     * 查看每日任务进度
     */
    private void handleProgress(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("此命令只能由玩家执行", NamedTextColor.RED));
            return;
        }

        UUID playerId = player.getUniqueId();
        DailyQuestService.DailyProgress progress = dailyQuestService.getDailyProgress(playerId);
        List<Quest> dailyQuests = dailyQuestService.getPlayerDailyQuests(playerId);
        PlayerQuest playerQuest = questService.getPlayerQuest(playerId);

        sender.sendMessage(Component.text("========== 每日任务进度 ==========", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("完成情况: " + progress.getProgressText(), NamedTextColor.YELLOW));
        sender.sendMessage(Component.text(progress.getProgressBar(), NamedTextColor.WHITE));

        if (progress.isAllCompleted()) {
            sender.sendMessage(Component.text("\n恭喜！今日所有每日任务已完成！", NamedTextColor.GREEN));
            sender.sendMessage(Component.text("明天再来领取新的每日任务吧~", NamedTextColor.GRAY));
        } else {
            sender.sendMessage(Component.text("\n剩余任务: " + (progress.getTotal() - progress.getCompleted()), NamedTextColor.YELLOW));
        }

        // 显示每个任务的详细进度
        sender.sendMessage(Component.text("\n========== 任务详情 ==========", NamedTextColor.GOLD));

        for (int i = 0; i < dailyQuests.size(); i++) {
            Quest quest = dailyQuests.get(i);
            boolean completed = playerQuest.hasCompletedQuest(quest.getId());
            String status = completed ? NamedTextColor.GREEN + "[已完成]" : NamedTextColor.GRAY + "[进行中]";

            sender.sendMessage(Component.text((i + 1) + ". " + quest.getName() + " " + status, NamedTextColor.WHITE));
            sender.sendMessage(Component.text("   难度: " + quest.getDifficulty().getColoredName(), NamedTextColor.GRAY));
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
            sender.sendMessage(Component.text("用法: /dailyquest info <编号>", NamedTextColor.RED));
            sender.sendMessage(Component.text("编号从 1 开始，对应任务列表中的序号", NamedTextColor.GRAY));
            return;
        }

        int index;
        try {
            index = Integer.parseInt(args[1]) - 1;
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("无效的编号: " + args[1], NamedTextColor.RED));
            return;
        }

        List<Quest> dailyQuests = dailyQuestService.generateDailyQuests(player);

        if (index < 0 || index >= dailyQuests.size()) {
            sender.sendMessage(Component.text("无效的编号，有效范围: 1-" + dailyQuests.size(), NamedTextColor.RED));
            return;
        }

        Quest quest = dailyQuests.get(index);
        UUID playerId = player.getUniqueId();
        PlayerQuest playerQuest = questService.getPlayerQuest(playerId);
        boolean completed = playerQuest.hasCompletedQuest(quest.getId());

        sender.sendMessage(Component.text("========== 每日任务详情 ==========", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("名称: " + quest.getName(), NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("描述: " + quest.getDescription(), NamedTextColor.WHITE));
        sender.sendMessage(Component.text("难度: " + quest.getDifficulty().getColoredName(), NamedTextColor.WHITE));
        sender.sendMessage(Component.text("类型: " + getQuestTypeName(quest.getType()), NamedTextColor.AQUA));
        sender.sendMessage(Component.text("状态: " + (completed ? NamedTextColor.GREEN + "已完成" : NamedTextColor.YELLOW + "进行中"), NamedTextColor.WHITE));

        // 显示目标
        sender.sendMessage(Component.text("\n目标:", NamedTextColor.GOLD));

        // 获取任务目标进度
        Quest activeQuest = playerQuest.getActiveQuest(quest.getId());
        for (int i = 0; i < quest.getObjectives().size(); i++) {
            QuestObjective obj = quest.getObjectives().get(i);
            boolean objCompleted = activeQuest != null && activeQuest.getObjectives().get(i).isCompleted();
            String status = objCompleted ? NamedTextColor.GREEN + "[V] " : NamedTextColor.GRAY + "[ ] ";
            String progress = activeQuest != null
                ? " (" + activeQuest.getObjectives().get(i).getCurrentProgress() + "/" + obj.getRequiredAmount() + ")"
                : " (0/" + obj.getRequiredAmount() + ")";

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
        if (!reward.getReputations().isEmpty()) {
            sender.sendMessage(Component.text("  声望: " + reward.getReputations(), NamedTextColor.LIGHT_PURPLE));
        }
        if (!reward.getTitles().isEmpty()) {
            sender.sendMessage(Component.text("  称号: " + reward.getTitles(), NamedTextColor.YELLOW));
        }

        if (completed) {
            sender.sendMessage(Component.text("\n此任务已完成！", NamedTextColor.GREEN));
        } else if (activeQuest != null && activeQuest.isAllObjectivesCompleted()) {
            sender.sendMessage(Component.text("\n所有目标已完成！使用 /quest complete " + quest.getId() + " 领取奖励", NamedTextColor.GREEN));
        }
    }

    /**
     * 查看奖励预览
     */
    private void handleReward(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("此命令只能由玩家执行", NamedTextColor.RED));
            return;
        }

        List<Quest> dailyQuests = dailyQuestService.getPlayerDailyQuests(player.getUniqueId());

        sender.sendMessage(Component.text("========== 每日任务奖励预览 ==========", NamedTextColor.GOLD));

        double totalMoney = 0;
        int totalExp = 0;
        int questCount = dailyQuests.size();

        for (Quest quest : dailyQuests) {
            QuestReward reward = quest.getReward();
            totalMoney += reward.getMoney();
            totalExp += reward.getExperience();
        }

        sender.sendMessage(Component.text("每日任务总数: " + questCount, NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("预计总金币: " + String.format("%.2f", totalMoney), NamedTextColor.GOLD));
        sender.sendMessage(Component.text("预计总经验: " + totalExp, NamedTextColor.AQUA));

        sender.sendMessage(Component.text("\n各任务奖励:", NamedTextColor.GOLD));

        for (int i = 0; i < dailyQuests.size(); i++) {
            Quest quest = dailyQuests.get(i);
            QuestReward reward = quest.getReward();
            sender.sendMessage(Component.text((i + 1) + ". " + quest.getName() + ": " +
                String.format("%.2f", reward.getMoney()) + " 金 + " + reward.getExperience() + " 经验",
                NamedTextColor.WHITE));
        }
    }

    /**
     * 发送帮助信息
     */
    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("========== 每日任务帮助 ==========", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/dailyquest - 查看今日每日任务", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/dailyquest list - 显示任务列表", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/dailyquest info <编号> - 查看任务详情", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/dailyquest progress - 查看完成进度", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/dailyquest reward - 预览总奖励", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/dailyquest refresh - 刷新每日任务（消耗金币）", NamedTextColor.YELLOW));
    }

    // ==================== 辅助方法 ====================

    private String getQuestTypeName(QuestType type) {
        return switch (type) {
            case DAILY -> "每日";
            case MAIN -> "主线";
            case SIDE -> "支线";
            case EVENT -> "活动";
            case ACHIEVEMENT -> "成就";
            default -> "普通";
        };
    }

    private String formatReward(QuestReward reward) {
        StringBuilder sb = new StringBuilder();
        if (reward.getMoney() > 0) {
            sb.append(String.format("%.2f", reward.getMoney())).append(" 金币");
        }
        if (reward.getExperience() > 0) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(reward.getExperience()).append(" 经验");
        }
        if (!reward.getItems().isEmpty()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(reward.getItems().size()).append(" 件物品");
        }
        return sb.length() > 0 ? sb.toString() : "无";
    }

    private String formatRewardBrief(QuestReward reward) {
        StringBuilder sb = new StringBuilder();
        if (reward.getMoney() > 0) {
            sb.append(String.format("%.2f", reward.getMoney())).append("金");
        }
        if (reward.getExperience() > 0) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(reward.getExperience()).append("经验");
        }
        return sb.length() > 0 ? sb.toString() : "无奖励";
    }

    // ==================== TabCompleter ====================

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(Arrays.asList("list", "info", "progress", "reward", "refresh", "help"));
        } else if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            if (subCommand.equals("info")) {
                if (sender instanceof Player player) {
                    List<Quest> dailyQuests = dailyQuestService.getPlayerDailyQuests(player.getUniqueId());
                    for (int i = 1; i <= dailyQuests.size(); i++) {
                        completions.add(String.valueOf(i));
                    }
                }
            }
        }

        String current = args[args.length - 1].toLowerCase();
        return completions.stream()
            .filter(s -> s.toLowerCase().startsWith(current))
            .collect(Collectors.toList());
    }
}
