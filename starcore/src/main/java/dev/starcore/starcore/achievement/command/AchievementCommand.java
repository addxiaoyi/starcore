package dev.starcore.starcore.achievement.command;

import dev.starcore.starcore.achievement.AchievementModule;
import dev.starcore.starcore.achievement.AchievementService;
import dev.starcore.starcore.achievement.AchievementCategory;
import dev.starcore.starcore.core.service.ServiceRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.NamespacedKey;

/**
 * 成就命令
 * /achievements - 打开成就菜单
 * /achievements list - 列出所有成就
 * /achievements stats - 查看成就统计
 */
public class AchievementCommand implements CommandExecutor, TabCompleter {

    private final Plugin plugin;
    private final ServiceRegistry serviceRegistry;

    public AchievementCommand(Plugin plugin, ServiceRegistry serviceRegistry) {
        this.plugin = plugin;
        this.serviceRegistry = serviceRegistry;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            // 打开成就菜单
            if (sender instanceof Player player) {
                AchievementModule module = getAchievementModule();
                if (module != null) {
                    module.openGui(player);
                    return true;
                } else {
                    player.sendMessage(Component.text("成就系统暂不可用", NamedTextColor.RED));
                    return true;
                }
            } else {
                sender.sendMessage(Component.text("此命令只能由玩家执行", NamedTextColor.RED));
                return true;
            }
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "list" -> {
                return handleList(sender, args);
            }
            case "stats" -> {
                return handleStats(sender, args);
            }
            case "category", "cat" -> {
                return handleCategory(sender, args);
            }
            case "give" -> {
                return handleGive(sender, args);
            }
            case "reload" -> {
                return handleReload(sender);
            }
            case "help" -> {
                return handleHelp(sender);
            }
            default -> {
                sender.sendMessage(Component.text("未知命令，使用 /achievements help 查看帮助", NamedTextColor.RED));
                return true;
            }
        }
    }

    /**
     * 列出成就
     */
    private boolean handleList(CommandSender sender, String[] args) {
        AchievementService service = getAchievementService();
        if (service == null) {
            sender.sendMessage(Component.text("成就系统暂不可用", NamedTextColor.RED));
            return true;
        }

        sender.sendMessage(Component.text("═══════════════════════════════════════", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("  成就列表", NamedTextColor.GOLD, TextDecoration.BOLD));
        sender.sendMessage(Component.text("═══════════════════════════════════════", NamedTextColor.GOLD));
        sender.sendMessage(Component.empty());

        var achievements = service.getAllAchievements();
        sender.sendMessage(Component.text("总成就数: " + achievements.size(), NamedTextColor.GRAY));

        if (sender instanceof Player player) {
            int completed = service.getPlayerProgress(player.getUniqueId());
            sender.sendMessage(Component.text("已完成: " + completed + " (" +
                String.format("%.1f%%", achievements.isEmpty() ? 0 : (completed * 100.0 / achievements.size())) + ")",
                NamedTextColor.GREEN));
        }

        sender.sendMessage(Component.empty());

        // 按分类显示
        for (AchievementCategory category : AchievementCategory.values()) {
            var categoryAchievements = service.getAchievementsByCategory(category);
            if (!categoryAchievements.isEmpty()) {
                sender.sendMessage(Component.text("【" + category.getDisplayName() + "】(" + categoryAchievements.size() + ")",
                    category.getColor(), TextDecoration.BOLD));
            }
        }

        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("使用 /achievements help 查看更多命令", NamedTextColor.GRAY));

        return true;
    }

    /**
     * 查看统计
     */
    private boolean handleStats(CommandSender sender, String[] args) {
        AchievementService service = getAchievementService();
        if (service == null) {
            sender.sendMessage(Component.text("成就系统暂不可用", NamedTextColor.RED));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("此命令只能由玩家执行", NamedTextColor.RED));
            return true;
        }

        sender.sendMessage(Component.text("═══════════════════════════════════════", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("  成就统计", NamedTextColor.GOLD, TextDecoration.BOLD));
        sender.sendMessage(Component.text("═══════════════════════════════════════", NamedTextColor.GOLD));
        sender.sendMessage(Component.empty());

        int total = service.getTotalAchievements();
        int completed = service.getPlayerProgress(player.getUniqueId());
        double percentage = total > 0 ? (completed * 100.0) / total : 0;

        sender.sendMessage(Component.text("玩家: " + player.getName(), NamedTextColor.AQUA));
        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("总成就数: " + total, NamedTextColor.GRAY));
        sender.sendMessage(Component.text("已完成: " + completed, NamedTextColor.GREEN));
        sender.sendMessage(Component.text("完成率: " + String.format("%.1f%%", percentage), NamedTextColor.YELLOW));

        sender.sendMessage(Component.empty());

        // 分类统计
        sender.sendMessage(Component.text("分类完成情况:", NamedTextColor.GOLD));
        for (AchievementCategory category : AchievementCategory.values()) {
            var categoryAchievements = service.getAchievementsByCategory(category);
            if (!categoryAchievements.isEmpty()) {
                int categoryCompleted = 0;
                for (var achievement : categoryAchievements) {
                    if (service.hasAchievement(player.getUniqueId(), achievement.getKey())) {
                        categoryCompleted++;
                    }
                }
                String status = categoryCompleted == categoryAchievements.size() ? "✓" : "○";
                sender.sendMessage(Component.text(status + " " + category.getDisplayName() + ": " +
                    categoryCompleted + "/" + categoryAchievements.size(),
                    categoryCompleted == categoryAchievements.size() ? NamedTextColor.GREEN : NamedTextColor.GRAY));
            }
        }

        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("使用 /achievements 打开成就菜单", NamedTextColor.GRAY));

        return true;
    }

    /**
     * 查看分类成就
     */
    private boolean handleCategory(CommandSender sender, String[] args) {
        AchievementModule module = getAchievementModule();
        if (module == null) {
            sender.sendMessage(Component.text("成就系统暂不可用", NamedTextColor.RED));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("此命令只能由玩家执行", NamedTextColor.RED));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(Component.text("请指定分类名称", NamedTextColor.RED));
            sender.sendMessage(Component.text("可用分类: adventure, combat, gathering, farming, social, nation, tech, exploration, economy, special",
                NamedTextColor.GRAY));
            return true;
        }

        String categoryName = args[1].toLowerCase();
        AchievementCategory category = null;

        for (AchievementCategory cat : AchievementCategory.values()) {
            if (cat.name().toLowerCase().equals(categoryName) ||
                cat.getDisplayName().equals(categoryName)) {
                category = cat;
                break;
            }
        }

        if (category == null) {
            sender.sendMessage(Component.text("未找到分类: " + categoryName, NamedTextColor.RED));
            return true;
        }

        module.openCategoryGui(player, category);
        return true;
    }

    /**
     * 给予成就（管理员命令）
     */
    private boolean handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("starcore.achievement.give")) {
            sender.sendMessage(Component.text("你没有权限使用此命令", NamedTextColor.RED));
            return true;
        }

        AchievementService service = getAchievementService();
        if (service == null) {
            sender.sendMessage(Component.text("成就系统暂不可用", NamedTextColor.RED));
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(Component.text("用法: /achievements give <玩家> <成就ID>", NamedTextColor.RED));
            return true;
        }

        String playerName = args[1];
        String achievementKey = args[2];

        Player target = plugin.getServer().getPlayer(playerName);
        if (target == null) {
            sender.sendMessage(Component.text("玩家不在线: " + playerName, NamedTextColor.RED));
            return true;
        }

        var achievementOpt = service.getAchievement(NamespacedKey.fromString(achievementKey));
        if (achievementOpt.isEmpty()) {
            sender.sendMessage(Component.text("未找到成就: " + achievementKey, NamedTextColor.RED));
            return true;
        }

        boolean success = service.grantAchievement(target, achievementOpt.get().getKey());
        if (success) {
            sender.sendMessage(Component.text("已给予 " + target.getName() + " 成就: " +
                achievementOpt.get().getTitle(), NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text("给予成就失败，可能已拥有或条件不满足", NamedTextColor.RED));
        }

        return true;
    }

    /**
     * 重载配置
     */
    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("starcore.achievement.reload")) {
            sender.sendMessage(Component.text("你没有权限使用此命令", NamedTextColor.RED));
            return true;
        }

        sender.sendMessage(Component.text("成就系统不支持重载配置", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("成就配置更改后需要重启服务器生效", NamedTextColor.GRAY));

        return true;
    }

    /**
     * 显示帮助
     */
    private boolean handleHelp(CommandSender sender) {
        sender.sendMessage(Component.text("═══════════════════════════════════════", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("  成就命令帮助", NamedTextColor.GOLD, TextDecoration.BOLD));
        sender.sendMessage(Component.text("═══════════════════════════════════════", NamedTextColor.GOLD));
        sender.sendMessage(Component.empty());

        sender.sendMessage(Component.text().append(Component.text("/achievements", NamedTextColor.YELLOW))
            .append(Component.text(" - 打开成就菜单", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text().append(Component.text("/achievements list", NamedTextColor.YELLOW))
            .append(Component.text(" - 列出所有成就", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text().append(Component.text("/achievements stats", NamedTextColor.YELLOW))
            .append(Component.text(" - 查看个人成就统计", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text().append(Component.text("/achievements category <分类>", NamedTextColor.YELLOW))
            .append(Component.text(" - 查看分类成就", NamedTextColor.GRAY)));

        if (sender.hasPermission("starcore.achievement.give")) {
            sender.sendMessage(Component.text().append(Component.text("/achievements give <玩家> <成就ID>", NamedTextColor.YELLOW))
                .append(Component.text(" - 给予玩家成就（管理员）", NamedTextColor.GRAY)));
        }

        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("可用分类: adventure, combat, gathering, farming, social, nation, tech, exploration, economy, special",
            NamedTextColor.GRAY));

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("list");
            completions.add("stats");
            completions.add("category");
            completions.add("help");
            if (sender.hasPermission("starcore.achievement.give")) {
                completions.add("give");
            }
            if (sender.hasPermission("starcore.achievement.reload")) {
                completions.add("reload");
            }
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("category")) {
                for (AchievementCategory category : AchievementCategory.values()) {
                    completions.add(category.name().toLowerCase());
                }
            } else if (args[0].equalsIgnoreCase("give")) {
                // 在线玩家
                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    completions.add(player.getName());
                }
            }
        } else if (args.length == 3) {
            if (args[0].equalsIgnoreCase("give")) {
                // 成就ID
                AchievementService service = getAchievementService();
                if (service != null) {
                    for (var achievement : service.getAllAchievements()) {
                        completions.add(achievement.getKey().toString());
                    }
                }
            }
        }

        // 过滤
        String current = args[args.length - 1].toLowerCase();
        return completions.stream()
            .filter(s -> s.toLowerCase().startsWith(current))
            .toList();
    }

    private AchievementService getAchievementService() {
        return serviceRegistry.find(AchievementService.class).orElse(null);
    }

    private AchievementModule getAchievementModule() {
        AchievementService service = getAchievementService();
        if (service instanceof AchievementModule module) {
            return module;
        }
        return null;
    }
}
