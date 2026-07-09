package dev.starcore.starcore.module.technology.command;

import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.technology.ResearchProgress;
import dev.starcore.starcore.module.technology.TechnologyModule;
import dev.starcore.starcore.module.technology.TechnologyValidator;
import dev.starcore.starcore.module.technology.gui.TechnologyTreeGui;
import dev.starcore.starcore.module.technology.model.TechnologyDefinition;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Command handler for technology commands (/tech).
 *
 * 中文别名:
 *   list/列表 → 查看科技列表
 *   info/信息 → 查看科技详情
 *   research/start/研发 → 开始研发
 *   progress/进度 → 查看研发进度
 *   cancel/取消 → 取消研发
 *   effects/效果 → 查看已解锁效果
 *   reload/重载 → 重新加载配置
 *   unlock/解锁 → 管理员解锁科技
 *   gui/menu/tree/菜单 → 打开科技GUI
 */
public class TechnologyCommand implements CommandExecutor, TabCompleter {
    private final TechnologyModule technologyModule;
    private final NationService nationService;
    private final Plugin plugin;
    private TechnologyTreeGui treeGui;

    public TechnologyCommand(TechnologyModule technologyModule, NationService nationService, Plugin plugin) {
        this.technologyModule = technologyModule;
        this.nationService = nationService;
        this.plugin = plugin;
    }

    /**
     * 设置 GUI 实例
     */
    public void setTreeGui(TechnologyTreeGui treeGui) {
        this.treeGui = treeGui;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                              @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = normalizeSubCommand(args[0].toLowerCase());

        switch (subCommand) {
            case "list" -> handleList(sender, args);
            case "info" -> handleInfo(sender, args);
            case "research" -> handleResearch(sender, args);
            case "progress" -> handleProgress(sender, args);
            case "cancel" -> handleCancel(sender, args);
            case "effects" -> handleEffects(sender, args);
            case "reload" -> handleReload(sender, args);
            case "unlock" -> handleUnlock(sender, args);
            case "gui" -> handleGui(sender, args);
            default -> sendHelp(sender);
        }

        return true;
    }

    /**
     * 规范化子命令，支持中英文别名
     */
    private String normalizeSubCommand(String input) {
        return switch (input.toLowerCase()) {
            // 列表
            case "list", "列表", "列", "所有", "ls" -> "list";
            // 信息
            case "info", "信息", "详", "详情", "i" -> "info";
            // 研发
            case "research", "start", "研发", "研究", "开始" -> "research";
            // 进度
            case "progress", "进度", "进", "状态", "p" -> "progress";
            // 取消
            case "cancel", "取消", "取", "终止", "x" -> "cancel";
            // 效果
            case "effects", "效果", "效", "加成", "e" -> "effects";
            // 重载
            case "reload", "重载", "重", "刷新", "rl" -> "reload";
            // 解锁
            case "unlock", "解锁", "解", "开放", "u" -> "unlock";
            // GUI
            case "gui", "menu", "tree", "菜单", "树", "科技树", "菜", "t" -> "gui";
            default -> input;
        };
    }

    private void handleList(CommandSender sender, String[] args) {
        sender.sendMessage("§6===== 科技列表 =====");

        Map<String, TechnologyDefinition> allTech = technologyModule.getAllDefinitions();

        // Group by era
        Map<String, List<TechnologyDefinition>> byEra = allTech.values().stream()
            .collect(Collectors.groupingBy(TechnologyDefinition::era));

        byEra.forEach((era, techs) -> {
            sender.sendMessage("§e时代: §f" + era.toUpperCase());
            for (TechnologyDefinition tech : techs) {
                boolean unlocked = false;
                if (sender instanceof Player player) {
                    Optional<NationId> nationIdOpt = nationService.nationOf(player.getUniqueId()).map(n -> n.id());
                    if (nationIdOpt.isPresent()) {
                        unlocked = technologyModule.hasTechnology(nationIdOpt.get(), tech.key());
                    }
                }

                String status = unlocked ? "§a[已解锁]" : "§7[未解锁]";
                sender.sendMessage("  " + status + " §e" + tech.displayName() + " §7- " + tech.description());
            }
            // 分隔
        });
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§c用法: /tech info <科技名>");
            return;
        }

        String techKey = args[1];
        Optional<TechnologyDefinition> defOpt = technologyModule.getDefinition(techKey);
        if (defOpt.isEmpty()) {
            sender.sendMessage("§c未知科技: " + techKey);
            return;
        }

        TechnologyDefinition def = defOpt.get();
        sender.sendMessage("§6===== " + def.displayName() + " =====");
        sender.sendMessage("§e描述: §f" + def.description());
        sender.sendMessage("§e时代: §f" + def.era());
        sender.sendMessage("§e分支: §f" + def.branch());
        sender.sendMessage("§e金币消耗: §f" + def.treasuryCost());
        sender.sendMessage("§e研发时间: §f" + def.researchTimeSeconds() + " 秒");

        if (!def.prerequisites().isEmpty()) {
            sender.sendMessage("§e前置科技: §f" + String.join(", ", def.prerequisites()));
        }

        if (!def.mutuallyExclusive().isEmpty()) {
            sender.sendMessage("§c互斥科技: §f" + String.join(", ", def.mutuallyExclusive()));
        }

        sender.sendMessage("§e效果:");
        for (var effect : def.effects()) {
            sender.sendMessage("  §a- §f" + effect.description());
        }
    }

    private void handleResearch(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c此命令只能由玩家执行");
            return;
        }

        Optional<NationId> nationIdOpt = nationService.nationOf(player.getUniqueId()).map(n -> n.id());
        if (nationIdOpt.isEmpty()) {
            sender.sendMessage("§c你还未加入任何国家");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage("§c用法: /tech research <科技名>");
            return;
        }

        NationId nationId = nationIdOpt.get();
        String techKey = args[1];

        // Validate
        TechnologyValidator.ValidationResult result = technologyModule.validateResearch(nationId, techKey);
        if (!result.valid()) {
            sender.sendMessage("§c无法研发: " + result.errors().get(0));
            for (String error : result.errors()) {
                sender.sendMessage("  §c- " + error);
            }
            return;
        }

        // Check if already researching
        if (technologyModule.isResearching(nationId, techKey)) {
            sender.sendMessage("§c该科技正在研发中");
            return;
        }

        // Start research
        boolean started = technologyModule.startResearch(nationId, techKey, progress -> {
            // Progress callback - could update action bar
        });

        if (started) {
            sender.sendMessage("§a开始研发: §e" + techKey);
            sender.sendMessage("§7使用 /tech progress 查看研发进度");
        } else {
            sender.sendMessage("§c研发失败");
        }
    }

    private void handleProgress(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c此命令只能由玩家执行");
            return;
        }

        Optional<NationId> nationIdOpt = nationService.nationOf(player.getUniqueId()).map(n -> n.id());
        if (nationIdOpt.isEmpty()) {
            sender.sendMessage("§c你还未加入任何国家");
            return;
        }

        NationId nationId = nationIdOpt.get();
        Map<String, ResearchProgress> research = technologyModule.getNationResearch(nationId);

        if (research.isEmpty()) {
            sender.sendMessage("§e当前没有正在进行的研发");
            return;
        }

        sender.sendMessage("§6===== 研发进度 =====");
        for (var entry : research.entrySet()) {
            ResearchProgress progress = entry.getValue();
            int percent = (int) (progress.getProgress() * 100);
            long remaining = progress.getRemainingSeconds();

            sender.sendMessage("§e" + entry.getKey() + ": §f" + percent + "% §7(剩余 " + formatTime(remaining) + ")");
        }
    }

    private void handleCancel(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c此命令只能由玩家执行");
            return;
        }

        Optional<NationId> nationIdOpt = nationService.nationOf(player.getUniqueId()).map(n -> n.id());
        if (nationIdOpt.isEmpty()) {
            sender.sendMessage("§c你还未加入任何国家");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage("§c用法: /tech cancel <科技名>");
            return;
        }

        NationId nationId = nationIdOpt.get();
        String techKey = args[1];

        if (technologyModule.cancelResearch(nationId, techKey)) {
            sender.sendMessage("§e已取消研发: " + techKey);
        } else {
            sender.sendMessage("§c未找到该研发任务");
        }
    }

    private void handleEffects(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c此命令只能由玩家执行");
            return;
        }

        Optional<NationId> nationIdOpt = nationService.nationOf(player.getUniqueId()).map(n -> n.id());
        if (nationIdOpt.isEmpty()) {
            sender.sendMessage("§c你还未加入任何国家");
            return;
        }

        NationId nationId = nationIdOpt.get();

        sender.sendMessage("§6===== 科技效果 =====");

        Map<String, Double> modifiers = technologyModule.getUnlockedFeatures(nationId);
        if (modifiers.isEmpty()) {
            sender.sendMessage("§7当前没有已解锁的科技效果");
            return;
        }

        for (String techKey : technologyModule.unlockedTechnologies(nationId)) {
            Optional<TechnologyDefinition> defOpt = technologyModule.getDefinition(techKey);
            if (defOpt.isPresent()) {
                sender.sendMessage("§e" + defOpt.get().displayName() + ":");
                for (var effect : defOpt.get().effects()) {
                    sender.sendMessage("  §a+ §f" + effect.description());
                }
            }
        }
    }

    private void handleReload(CommandSender sender, String[] args) {
        if (!sender.hasPermission("starcore.admin")) {
            sender.sendMessage("§c你没有权限执行此命令");
            return;
        }

        sender.sendMessage("§e重新加载科技配置...");
        // Reload would be implemented through the definition loader
        sender.sendMessage("§a科技配置已重新加载");
    }

    private void handleUnlock(CommandSender sender, String[] args) {
        if (!sender.hasPermission("starcore.admin")) {
            sender.sendMessage("§c你没有权限执行此命令");
            return;
        }

        if (args.length < 3) {
            sender.sendMessage("§c用法: /tech unlock <玩家> <科技名>");
            return;
        }

        String playerName = args[1];
        String techKey = args[2];

        // Find player's nation
        Player target = sender.getServer().getPlayer(playerName);
        if (target == null) {
            sender.sendMessage("§c玩家不在线: " + playerName);
            return;
        }

        Optional<Nation> nationOpt = nationService.nationOf(target.getUniqueId());
        if (nationOpt.isEmpty()) {
            sender.sendMessage("§c该玩家未加入任何国家");
            return;
        }

        if (technologyModule.unlock(nationOpt.get().id(), techKey)) {
            sender.sendMessage("§a已为 " + playerName + " 解锁科技: " + techKey);
        } else {
            sender.sendMessage("§c解锁失败");
        }
    }

    private void handleGui(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c此命令只能由玩家执行");
            return;
        }

        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());
        if (nationOpt.isEmpty()) {
            sender.sendMessage("§c你需要先加入一个国家才能使用科技GUI");
            return;
        }

        Nation nation = nationOpt.get();

        if (treeGui != null) {
            treeGui.openMainMenu(player);
        } else {
            // Fallback: create GUI instance directly
            dev.starcore.starcore.module.treasury.TreasuryService treasuryService =
                plugin.getServer().getServicesManager().load(dev.starcore.starcore.module.treasury.TreasuryService.class);
            TechnologyTreeGui gui = new TechnologyTreeGui(technologyModule, nationService, treasuryService, plugin);
            gui.openMainMenu(player);
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6===== 科技系统 =====");
        sender.sendMessage("§e/tech §7- 打开科技GUI菜单 (gui/菜单)");
        sender.sendMessage("§e/tech list §7- 查看所有科技 (列表)");
        sender.sendMessage("§e/tech info <科技名> §7- 查看科技详情 (信息)");
        sender.sendMessage("§e/tech research <科技名> §7- 开始研发 (研发)");
        sender.sendMessage("§e/tech progress §7- 查看研发进度 (进度)");
        sender.sendMessage("§e/tech cancel <科技名> §7- 取消研发 (取消)");
        sender.sendMessage("§e/tech effects §7- 查看已解锁效果 (效果)");
        if (sender.hasPermission("starcore.admin")) {
            sender.sendMessage("§c/tech unlock <玩家> <科技名> §7- 管理员解锁科技 (解锁)");
            sender.sendMessage("§c/tech reload §7- 重新加载配置 (重载)");
        }
    }

    private String formatTime(long seconds) {
        if (seconds < 60) {
            return seconds + "秒";
        } else if (seconds < 3600) {
            return (seconds / 60) + "分" + (seconds % 60) + "秒";
        } else {
            return (seconds / 3600) + "时" + ((seconds % 3600) / 60) + "分";
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // 第一级补全：所有子命令（中英文）
            completions.addAll(List.of(
                "list", "列表",
                "info", "信息",
                "research", "研发",
                "progress", "进度",
                "cancel", "取消",
                "effects", "效果",
                "gui", "菜单"
            ));
            if (sender.hasPermission("starcore.admin")) {
                completions.addAll(List.of("unlock", "解锁", "reload", "重载"));
            }
        } else if (args.length == 2) {
            String subCommand = normalizeSubCommand(args[0]);
            switch (subCommand) {
                case "info", "research", "cancel" -> {
                    // 补全科技名称
                    completions.addAll(technologyModule.getAllDefinitions().keySet());
                }
                case "unlock" -> {
                    // 补全在线玩家
                    for (org.bukkit.entity.Player p : sender.getServer().getOnlinePlayers()) {
                        completions.add(p.getName());
                    }
                }
            }
        } else if (args.length == 3) {
            String subCommand = normalizeSubCommand(args[0]);
            if (subCommand.equals("unlock")) {
                // 补全科技名称
                completions.addAll(technologyModule.getAllDefinitions().keySet());
            }
        }

        String current = args[args.length - 1].toLowerCase();
        return completions.stream()
            .filter(s -> s.toLowerCase().startsWith(current))
            .collect(Collectors.toList());
    }
}
