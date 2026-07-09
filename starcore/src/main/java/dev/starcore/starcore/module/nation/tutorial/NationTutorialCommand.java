package dev.starcore.starcore.module.nation.tutorial;

import dev.starcore.starcore.foundation.message.MessageService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * 国家教程指令处理器
 * 提供 /tutorial 命令来管理教程
 */
public class NationTutorialCommand implements CommandExecutor, TabCompleter {

    private final Plugin plugin;
    private final NationTutorialService tutorialService;
    private final NationTutorialConfig tutorialConfig;
    private final MessageService messages;

    public NationTutorialCommand(Plugin plugin, NationTutorialService tutorialService,
                                 NationTutorialConfig tutorialConfig, MessageService messages) {
        this.plugin = plugin;
        this.tutorialService = tutorialService;
        this.tutorialConfig = tutorialConfig;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(org.bukkit.command.CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("此命令只能由玩家执行"));
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "start", "begin" -> {
                startTutorial(player, args);
            }
            case "next", "n" -> {
                tutorialService.nextStep(player);
            }
            case "prev", "previous", "p" -> {
                tutorialService.previousStep(player);
            }
            case "skip", "s" -> {
                tutorialService.skipTutorial(player);
            }
            case "close", "stop" -> {
                tutorialService.closeTutorial(player);
            }
            case "info", "status" -> {
                showTutorialStatus(player);
            }
            case "reset" -> {
                resetTutorial(player);
            }
            case "list" -> {
                listTutorials(player);
            }
            case "beginner", "newbie" -> {
                startSpecificTutorial(player, "beginner");
            }
            case "visitor", "guest" -> {
                startSpecificTutorial(player, "visitor");
            }
            case "admin" -> {
                startSpecificTutorial(player, "admin");
            }
            default -> {
                player.sendMessage(Component.text("未知指令: " + subCommand, NamedTextColor.RED));
                player.sendMessage(Component.text("使用 /tutorial 查看帮助", NamedTextColor.GRAY));
            }
        }

        return true;
    }

    /**
     * 发送帮助信息
     */
    private void sendHelp(Player player) {
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("═══════════════════════════════════", NamedTextColor.GOLD));
        player.sendMessage(Component.text("📖 国家教程系统", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("═══════════════════════════════════", NamedTextColor.GOLD));
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("  /tutorial start [类型]  - 开始教程", NamedTextColor.WHITE));
        player.sendMessage(Component.text("  /tutorial next        - 下一步", NamedTextColor.WHITE));
        player.sendMessage(Component.text("  /tutorial prev        - 上一步", NamedTextColor.WHITE));
        player.sendMessage(Component.text("  /tutorial skip        - 跳过教程", NamedTextColor.WHITE));
        player.sendMessage(Component.text("  /tutorial close       - 关闭教程", NamedTextColor.WHITE));
        player.sendMessage(Component.text("  /tutorial info        - 显示当前状态", NamedTextColor.WHITE));
        player.sendMessage(Component.text("  /tutorial reset       - 重置教程进度", NamedTextColor.WHITE));
        player.sendMessage(Component.text("  /tutorial list        - 查看所有教程", NamedTextColor.WHITE));
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("  快速教程:", NamedTextColor.AQUA));
        player.sendMessage(Component.text("  /tutorial beginner    - 新手教程", NamedTextColor.GRAY));
        player.sendMessage(Component.text("  /tutorial visitor     - 访客指南", NamedTextColor.GRAY));
        player.sendMessage(Component.text("  /tutorial admin       - 管理员指南", NamedTextColor.GRAY));
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("═══════════════════════════════════", NamedTextColor.GOLD));
        player.sendMessage(Component.text(""));
    }

    /**
     * 开始教程
     */
    private void startTutorial(Player player, String[] args) {
        UUID playerId = player.getUniqueId();

        // 如果有活跃教程，先关闭
        if (tutorialService.hasActiveTutorial(playerId)) {
            tutorialService.closeTutorial(player);
            // 延迟一下再开始新教程
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (args.length > 1) {
                    startSpecificTutorial(player, args[1]);
                } else {
                    // 根据玩家状态选择教程
                    tutorialService.checkAndTriggerTutorial(player);
                }
            }, 10L);
            return;
        }

        if (args.length > 1) {
            startSpecificTutorial(player, args[1]);
        } else {
            tutorialService.checkAndTriggerTutorial(player);
        }
    }

    /**
     * 开始指定教程
     */
    private void startSpecificTutorial(Player player, String tutorialId) {
        NationTutorialConfig.TutorialContent tutorial = tutorialConfig.getTutorial(tutorialId);

        if (tutorial == null || !tutorial.enabled()) {
            player.sendMessage(Component.text("教程不存在: " + tutorialId, NamedTextColor.RED));
            player.sendMessage(Component.text("使用 /tutorial list 查看可用教程", NamedTextColor.GRAY));
            return;
        }

        UUID playerId = player.getUniqueId();

        // 如果有活跃教程，先关闭
        if (tutorialService.hasActiveTutorial(playerId)) {
            tutorialService.closeTutorial(player);
        }

        tutorialService.startTutorial(player, tutorial);
    }

    /**
     * 显示教程状态
     */
    private void showTutorialStatus(Player player) {
        UUID playerId = player.getUniqueId();

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("═══════════════════════════════════", NamedTextColor.GOLD));
        player.sendMessage(Component.text("📖 教程状态", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("═══════════════════════════════════", NamedTextColor.GOLD));
        player.sendMessage(Component.text(""));

        if (tutorialService.hasActiveTutorial(playerId)) {
            tutorialService.getActiveTutorial(playerId).ifPresent(active -> {
                var task = active.task();
                if (task != null && task.isRunning()) {
                    player.sendMessage(Component.text("状态: " + (task.isRunning() ? "进行中" : "已完成"), NamedTextColor.GREEN));
                    player.sendMessage(Component.text("教程: " + active.tutorial().title(), NamedTextColor.WHITE));
                    player.sendMessage(Component.text("进度: " + (task.getCurrentStep() + 1) + "/" + task.getTotalSteps(), NamedTextColor.AQUA));

                    var step = task.getCurrentStepData();
                    if (step != null) {
                        player.sendMessage(Component.text("当前: " + step.title(), NamedTextColor.GRAY));
                    }
                }
            });
        } else {
            player.sendMessage(Component.text("状态: 无进行中的教程", NamedTextColor.GRAY));
        }

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("═══════════════════════════════════", NamedTextColor.GOLD));
        player.sendMessage(Component.text(""));
    }

    /**
     * 重置教程
     */
    private void resetTutorial(Player player) {
        UUID playerId = player.getUniqueId();

        if (tutorialService.hasActiveTutorial(playerId)) {
            tutorialService.closeTutorial(player);
        }

        player.sendMessage(Component.text("教程进度已重置。使用 /tutorial start 重新开始", NamedTextColor.YELLOW));
    }

    /**
     * 列出所有教程
     */
    private void listTutorials(Player player) {
        List<NationTutorialConfig.TutorialContent> tutorials = tutorialConfig.getTutorials();

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("═══════════════════════════════════", NamedTextColor.GOLD));
        player.sendMessage(Component.text("📚 可用教程列表", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("═══════════════════════════════════", NamedTextColor.GOLD));
        player.sendMessage(Component.text(""));

        for (NationTutorialConfig.TutorialContent tutorial : tutorials) {
            if (tutorial.enabled()) {
                String status = tutorialService.hasActiveTutorial(player.getUniqueId()) &&
                    tutorialService.getActiveTutorial(player.getUniqueId())
                        .map(a -> a.tutorial().id().equals(tutorial.id()))
                        .orElse(false)
                    ? " §a[进行中]" : "";

                player.sendMessage(Component.text("  §e• " + tutorial.title() + " §7(" + tutorial.id() + ")" + status, NamedTextColor.WHITE));
                player.sendMessage(Component.text("    " + tutorial.description(), NamedTextColor.GRAY));
                player.sendMessage(Component.text("    步骤: " + tutorial.steps().size(), NamedTextColor.DARK_GRAY));
                player.sendMessage(Component.text(""));
            }
        }

        player.sendMessage(Component.text("使用 /tutorial start <教程名> 开始教程", NamedTextColor.AQUA));
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("═══════════════════════════════════", NamedTextColor.GOLD));
        player.sendMessage(Component.text(""));
    }

    @Override
    public List<String> onTabComplete(org.bukkit.command.CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(Arrays.asList(
                "start", "next", "prev", "skip", "close",
                "info", "reset", "list",
                "beginner", "visitor", "admin"
            ));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("start")) {
            for (NationTutorialConfig.TutorialContent tutorial : tutorialConfig.getTutorials()) {
                if (tutorial.enabled()) {
                    completions.add(tutorial.id());
                }
            }
        }

        // 过滤匹配
        String input = args[args.length - 1].toLowerCase();
        return completions.stream()
            .filter(s -> s.toLowerCase().startsWith(input))
            .toList();
    }
}