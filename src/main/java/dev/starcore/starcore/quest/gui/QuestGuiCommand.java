package dev.starcore.starcore.quest.gui;

import dev.starcore.starcore.quest.CommissionService;
import dev.starcore.starcore.quest.DailyQuestService;
import dev.starcore.starcore.quest.QuestService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 任务GUI命令处理器
 * 提供 /questgui 命令打开任务GUI
 */
public class QuestGuiCommand implements CommandExecutor, TabCompleter {

    private final QuestService questService;
    private final DailyQuestService dailyQuestService;
    private final CommissionService commissionService;
    private QuestMenu questMenu;

    public QuestGuiCommand(QuestService questService, DailyQuestService dailyQuestService,
                           CommissionService commissionService) {
        this.questService = questService;
        this.dailyQuestService = dailyQuestService;
        this.commissionService = commissionService;
    }

    /**
     * 设置QuestMenu实例（延迟初始化）
     */
    public void setQuestMenu(QuestMenu questMenu) {
        this.questMenu = questMenu;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("此命令只能由玩家执行");
            return true;
        }

        if (questMenu == null) {
            player.sendMessage("任务GUI未初始化");
            return true;
        }

        // 打开主菜单
        if (args.length == 0) {
            questMenu.openMainMenu(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "daily" -> questMenu.openDailyQuests(player);
            case "list" -> questMenu.openQuestList(player, null, 0);
            case "progress" -> questMenu.openProgress(player);
            case "commission" -> questMenu.openCommissionBoard(player);
            case "main" -> questMenu.openMainMenu(player);
            default -> {
                // 默认打开主菜单
                questMenu.openMainMenu(player);
            }
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(Arrays.asList("daily", "list", "progress", "commission", "main"));
        }

        String current = args.length > 0 ? args[args.length - 1].toLowerCase() : "";
        return completions.stream()
            .filter(s -> s.toLowerCase().startsWith(current))
            .collect(Collectors.toList());
    }
}
