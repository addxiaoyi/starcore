package dev.starcore.starcore.module.territory.upgrade;

import dev.starcore.starcore.core.service.ServiceRegistry;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.territory.upgrade.model.*;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Command handler for territory upgrade system.
 * 领地升级系统命令处理器
 */
public class TerritoryUpgradeCommand implements CommandExecutor, TabCompleter {

    private final TerritoryUpgradeService upgradeService;
    private final NationService nationService;

    public TerritoryUpgradeCommand(TerritoryUpgradeService upgradeService, NationService nationService) {
        this.upgradeService = upgradeService;
        this.nationService = nationService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                            @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "help" -> sendHelp(sender);
            case "info" -> handleInfo(sender, args);
            case "paths" -> handlePaths(sender);
            case "status" -> handleStatus(sender);
            case "upgrade" -> handleUpgrade(sender, args);
            case "exp" -> handleExp(sender);
            case "add-exp" -> handleAddExp(sender, args);
            case "reset" -> handleReset(sender, args);
            case "reload" -> handleReload(sender);
            default -> sendHelp(sender);
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6===== 领地升级系统 =====");
        sender.sendMessage("§e/upgrade info [路径] §7- 查看升级路径信息");
        sender.sendMessage("§e/upgrade paths §7- 查看所有可用升级路径");
        sender.sendMessage("§e/upgrade status §7- 查看国家升级状态");
        sender.sendMessage("§e/upgrade upgrade [路径] §7- 开始升级");
        sender.sendMessage("§e/upgrade exp §7- 查看当前经验值");
        sender.sendMessage("§6===== 管理命令 =====");
        sender.sendMessage("§e/upgrade add-exp <玩家> <经验> [来源] §7- 添加经验值");
        sender.sendMessage("§e/upgrade reset <玩家> §7- 重置升级进度");
        sender.sendMessage("§e/upgrade reload §7- 重载配置");
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§c用法: /upgrade info <路径>");
            return;
        }

        String pathId = args[1].toLowerCase();
        Optional<UpgradeTierDefinition> pathOpt = upgradeService.getPathDefinition(pathId);

        if (pathOpt.isEmpty()) {
            sender.sendMessage("§c无效的升级路径: " + pathId);
            return;
        }

        UpgradeTierDefinition path = pathOpt.get();
        sender.sendMessage("§6===== " + path.pathName() + " =====");
        sender.sendMessage("§7" + path.pathDescription());

        for (TerritoryUpgradeLevel level : path.tiers()) {
            String status = "";
            if (sender instanceof Player player) {
                NationId nationId = getPlayerNation(player);
                if (nationId != null) {
                    int currentLevel = upgradeService.getCurrentLevel(nationId, pathId);
                    if (level.level() <= currentLevel) {
                        status = " §a[已达成]";
                    } else if (level.level() == currentLevel + 1) {
                        status = " §e[下一级]";
                    } else {
                        status = " §7[未解锁]";
                    }
                }
            }
            sender.sendMessage(String.format("§eLv.%d %s §7- %d 经验%s",
                level.level(), level.name(), level.expRequired(), status));
            if (!level.description().isEmpty()) {
                sender.sendMessage("  §7" + level.description());
            }
        }
    }

    private void handlePaths(CommandSender sender) {
        sender.sendMessage("§6===== 可用升级路径 =====");
        for (String pathId : upgradeService.getAvailablePaths()) {
            Optional<UpgradeTierDefinition> pathOpt = upgradeService.getPathDefinition(pathId);
            pathOpt.ifPresent(path -> {
                int level = 0;
                if (sender instanceof Player player) {
                    NationId nationId = getPlayerNation(player);
                    if (nationId != null) {
                        level = upgradeService.getCurrentLevel(nationId, pathId);
                    }
                }
                sender.sendMessage(String.format("§e%s §7- %s (当前: Lv.%d/%d)",
                    path.pathName(), path.pathDescription(), level, path.maxLevel()));
            });
        }
    }

    private void handleStatus(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c该命令只能由玩家执行");
            return;
        }

        NationId nationId = getPlayerNation(player);
        if (nationId == null) {
            sender.sendMessage("§c你未加入任何国家");
            return;
        }

        sender.sendMessage("§6===== 升级状态 =====");
        sender.sendMessage(String.format("§e总经验: §f%d", upgradeService.getTotalExp(nationId)));
        sender.sendMessage(String.format("§e已消耗: §f%d", upgradeService.getExpSpent(nationId)));
        sender.sendMessage(String.format("§e可用: §f%d",
            upgradeService.getTotalExp(nationId) - upgradeService.getExpSpent(nationId)));

        for (String pathId : upgradeService.getAvailablePaths()) {
            Optional<UpgradeTierDefinition> pathOpt = upgradeService.getPathDefinition(pathId);
            pathOpt.ifPresent(path -> {
                int currentLevel = upgradeService.getCurrentLevel(nationId, pathId);
                int maxLevel = path.maxLevel();
                String levelBar = createLevelBar(currentLevel, maxLevel);

                // 分隔
                sender.sendMessage(String.format("§e%s: %s", path.pathName(), levelBar));

                if (currentLevel < maxLevel) {
                    int progress = upgradeService.getProgressToNextLevel(nationId, pathId);
                    int required = upgradeService.getExpRequiredForNextLevel(nationId, pathId);
                    int available = upgradeService.getTotalExp(nationId) - upgradeService.getExpSpent(nationId);
                    sender.sendMessage(String.format("  §7下一级: Lv.%d (需要 %d 经验)", currentLevel + 1, required));
                    sender.sendMessage(String.format("  §7进度: %d%% (%d/%d 可用经验)",
                        progress, available, required));
                } else {
                    sender.sendMessage("  §a已满级!");
                }
            });
        }
    }

    private void handleUpgrade(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c该命令只能由玩家执行");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage("§c用法: /upgrade upgrade <路径>");
            return;
        }

        NationId nationId = getPlayerNation(player);
        if (nationId == null) {
            sender.sendMessage("§c你未加入任何国家");
            return;
        }

        String pathId = args[1].toLowerCase();
        UpgradeCheckResult result = upgradeService.startUpgrade(nationId, pathId);

        if (result.isSuccess()) {
            player.sendMessage("§a升级成功!");
        } else {
            player.sendMessage("§c升级失败: " + result.errorMessage());
        }
    }

    private void handleExp(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c该命令只能由玩家执行");
            return;
        }

        NationId nationId = getPlayerNation(player);
        if (nationId == null) {
            sender.sendMessage("§c你未加入任何国家");
            return;
        }

        int total = upgradeService.getTotalExp(nationId);
        int spent = upgradeService.getExpSpent(nationId);
        int available = total - spent;

        sender.sendMessage("§6===== 经验值信息 =====");
        sender.sendMessage(String.format("§e总经验: §f%d", total));
        sender.sendMessage(String.format("§e已消耗: §f%d", spent));
        sender.sendMessage(String.format("§e可用: §f%d", available));
    }

    private void handleAddExp(CommandSender sender, String[] args) {
        if (!sender.hasPermission("starcore.admin.upgrade")) {
            sender.sendMessage("§c你没有权限使用此命令");
            return;
        }

        if (args.length < 3) {
            sender.sendMessage("§c用法: /upgrade add-exp <玩家> <经验> [来源]");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§c玩家不存在: " + args[1]);
            return;
        }

        int exp;
        try {
            exp = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§c无效的经验值: " + args[2]);
            return;
        }

        String source = args.length > 3 ? args[3] : "admin";

        NationId nationId = getPlayerNation(target);
        if (nationId == null) {
            sender.sendMessage("§c玩家未加入任何国家");
            return;
        }

        upgradeService.addExperience(nationId, exp, source);
        sender.sendMessage(String.format("§a已为 %s 的国家添加 %d 经验 (来源: %s)",
            target.getName(), exp, source));
    }

    private void handleReset(CommandSender sender, String[] args) {
        if (!sender.hasPermission("starcore.admin.upgrade")) {
            sender.sendMessage("§c你没有权限使用此命令");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage("§c用法: /upgrade reset <玩家>");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§c玩家不存在: " + args[1]);
            return;
        }

        NationId nationId = getPlayerNation(target);
        if (nationId == null) {
            sender.sendMessage("§c玩家未加入任何国家");
            return;
        }

        upgradeService.resetProgress(nationId);
        sender.sendMessage("§a已重置 " + target.getName() + " 的升级进度");
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("starcore.admin.upgrade")) {
            sender.sendMessage("§c你没有权限使用此命令");
            return;
        }

        upgradeService.reloadDefinitions();
        sender.sendMessage("§a领地升级配置已重载");
    }

    private NationId getPlayerNation(Player player) {
        if (nationService == null) {
            return null;
        }
        return nationService.nationOf(player.getUniqueId())
            .map(Nation::id)
            .orElse(null);
    }

    private String createLevelBar(int current, int max) {
        StringBuilder bar = new StringBuilder();
        int filled = current;
        int empty = max - current;

        bar.append("§a");
        for (int i = 0; i < filled; i++) {
            bar.append("■");
        }
        bar.append("§7");
        for (int i = 0; i < empty; i++) {
            bar.append("■");
        }
        bar.append("§f ").append(current).append("/").append(max);

        return bar.toString();
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String label, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subCommands = List.of("help", "info", "paths", "status", "upgrade", "exp");
            if (sender.hasPermission("starcore.admin.upgrade")) {
                subCommands = new ArrayList<>(subCommands);
                ((ArrayList<String>) subCommands).addAll(List.of("add-exp", "reset", "reload"));
            }
            String prefix = args[0].toLowerCase();
            return subCommands.stream()
                .filter(s -> s.startsWith(prefix))
                .collect(Collectors.toList());
        }

        if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            String prefix = args[1].toLowerCase();

            switch (subCommand) {
                case "info", "upgrade" -> {
                    return upgradeService.getAvailablePaths().stream()
                        .filter(p -> p.startsWith(prefix))
                        .collect(Collectors.toList());
                }
                case "add-exp" -> {
                    return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(n -> n.toLowerCase().startsWith(prefix))
                        .collect(Collectors.toList());
                }
                case "reset" -> {
                    return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(n -> n.toLowerCase().startsWith(prefix))
                        .collect(Collectors.toList());
                }
            }
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("add-exp")) {
            return List.of("100", "500", "1000", "5000");
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("add-exp")) {
            return List.of("territory_claim", "resource_gathering", "combat", "economy", "admin");
        }

        return completions;
    }
}
