package dev.starcore.starcore.module.military;

import dev.starcore.starcore.module.military.gui.BattleStatusMenu;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
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
 * 军事指挥中心命令
 * 提供战况预览、战场管理、军队调遣等功能
 */
public class MilitaryCommand implements CommandExecutor, TabCompleter {

    private final MilitaryModule module;
    private final NationService nationService;

    public MilitaryCommand(MilitaryModule module, NationService nationService) {
        this.module = module;
        this.nationService = nationService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c此命令只能由玩家使用");
            return true;
        }

        // 检查玩家是否属于国家
        if (!hasNation(player)) {
            player.sendMessage("§c你需要先加入一个国家才能使用军事指挥中心");
            return true;
        }

        if (args.length == 0) {
            // 打开战况总览
            openMainMenu(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "status", "overview" -> {
                // 战况总览
                openMainMenu(player);
            }
            case "battlefield", "bf" -> {
                // 战场详情
                openBattlefieldMenu(player, parsePage(args));
            }
            case "army", "armies" -> {
                // 军队分布
                openArmyDistributionMenu(player, parsePage(args));
            }
            case "trend", "trends" -> {
                // 战局趋势
                openWarTrendMenu(player);
            }
            case "intel", "enemy" -> {
                // 敌情分析
                openEnemyAnalysisMenu(player);
            }
            case "refresh" -> {
                // 手动刷新
                refreshMenu(player);
            }
            case "help", "?" -> {
                // 帮助信息
                showHelp(player);
            }
            default -> {
                player.sendMessage("§c未知命令: " + subCommand);
                player.sendMessage("§e使用 /military help 查看帮助");
            }
        }

        return true;
    }

    private boolean hasNation(Player player) {
        if (nationService == null) {
            return true; // 如果服务不可用，允许访问
        }
        return nationService.nationOf(player.getUniqueId()).isPresent();
    }

    private void openMainMenu(Player player) {
        BattleStatusMenu menu = module.getBattleStatusMenu();
        if (menu == null) {
            player.sendMessage("§c军事指挥中心正在初始化，请稍后重试");
            return;
        }
        menu.openMainMenu(player);

        // 启动自动刷新
        module.getBattleStatusMenuListener().startAutoRefresh(player);
    }

    private void openBattlefieldMenu(Player player, int page) {
        BattleStatusMenu menu = module.getBattleStatusMenu();
        if (menu == null) {
            player.sendMessage("§c军事指挥中心正在初始化，请稍后重试");
            return;
        }
        menu.openBattlefieldMenu(player, page);
        module.getBattleStatusMenuListener().startAutoRefresh(player);
    }

    private void openArmyDistributionMenu(Player player, int page) {
        BattleStatusMenu menu = module.getBattleStatusMenu();
        if (menu == null) {
            player.sendMessage("§c军事指挥中心正在初始化，请稍后重试");
            return;
        }
        menu.openArmyDistributionMenu(player, page);
        module.getBattleStatusMenuListener().startAutoRefresh(player);
    }

    private void openWarTrendMenu(Player player) {
        BattleStatusMenu menu = module.getBattleStatusMenu();
        if (menu == null) {
            player.sendMessage("§c军事指挥中心正在初始化，请稍后重试");
            return;
        }
        menu.openWarTrendMenu(player);
        module.getBattleStatusMenuListener().startAutoRefresh(player);
    }

    private void openEnemyAnalysisMenu(Player player) {
        BattleStatusMenu menu = module.getBattleStatusMenu();
        if (menu == null) {
            player.sendMessage("§c军事指挥中心正在初始化，请稍后重试");
            return;
        }
        menu.openEnemyAnalysisMenu(player);
        module.getBattleStatusMenuListener().startAutoRefresh(player);
    }

    private void refreshMenu(Player player) {
        player.sendMessage("§a正在刷新战况数据...");
        openMainMenu(player);
    }

    private void showHelp(Player player) {
        player.sendMessage("§6§l========== 军事指挥中心 ==========");
        // 分隔
        player.sendMessage("§e/military §7- §f打开战况总览");
        player.sendMessage("§e/military status §7- §f战况总览（实时预览）");
        player.sendMessage("§e/military battlefield [页码] §7- §f战场详情");
        player.sendMessage("§e/military army [页码] §7- §f军队分布");
        player.sendMessage("§e/military trend §7- §f战局趋势分析");
        player.sendMessage("§e/military intel §7- §f敌情分析");
        player.sendMessage("§e/military refresh §7- §f手动刷新数据");
        // 分隔
        player.sendMessage("§7别名: §f/battle §7- §f快速打开战况总览");
        player.sendMessage("§7别名: §f/battlestatus §7- §f快速打开战况总览");
        // 分隔
        player.sendMessage("§6§l===================================");
    }

    private int parsePage(String[] args) {
        if (args.length > 1) {
            try {
                return Math.max(0, Integer.parseInt(args[1]) - 1);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            return new ArrayList<>();
        }

        Player player = (Player) sender;

        // 检查玩家是否属于国家
        if (!hasNation(player)) {
            return new ArrayList<>();
        }

        if (args.length == 1) {
            List<String> subCommands = Arrays.asList(
                "status", "battlefield", "army", "trend", "intel", "refresh", "help"
            );
            String input = args[0].toLowerCase();
            return subCommands.stream()
                .filter(cmd -> cmd.startsWith(input))
                .collect(Collectors.toList());
        }

        if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            if (subCommand.equals("battlefield") || subCommand.equals("army")) {
                // 返回页码建议
                List<String> pages = Arrays.asList("1", "2", "3");
                String input = args[1];
                return pages.stream()
                    .filter(p -> p.startsWith(input))
                    .collect(Collectors.toList());
            }
        }

        return new ArrayList<>();
    }
}