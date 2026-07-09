package dev.starcore.starcore.module.army.fatigue.command;

import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.army.fatigue.FatigueService;
import dev.starcore.starcore.module.army.fatigue.model.FatigueLevel;
import dev.starcore.starcore.module.army.fatigue.model.FatigueType;
import dev.starcore.starcore.module.army.fatigue.model.PlayerFatigue;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 疲劳度命令处理器
 * /fatigue <子命令>
 */
public final class FatigueCommand implements CommandExecutor, TabCompleter {
    private final FatigueService fatigueService;
    private final MessageService messages;

    public FatigueCommand(FatigueService fatigueService, MessageService messages) {
        this.fatigueService = fatigueService;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                            @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text(messages.format("command.player-only"), NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            showHelp(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        try {
            switch (subCommand) {
                case "status", "s" -> handleStatus(player);
                case "rest", "r" -> handleRest(player, args);
                case "reset" -> handleReset(player, args);
                case "effects", "e" -> handleEffects(player);
                case "add" -> handleAdd(player, args);
                case "reduce" -> handleReduce(player, args);
                case "check" -> handleCheck(player, args);
                default -> showHelp(player);
            }
        } catch (IllegalArgumentException e) {
            player.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED));
        }

        return true;
    }

    private void handleStatus(Player player) {
        PlayerFatigue fatigue = fatigueService.getOrCreatePlayerFatigue(player.getUniqueId());

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("=== " + messages.format("fatigue.status.title", player.getName()) + " ===", NamedTextColor.GOLD));

        // 总体状态
        FatigueLevel level = fatigue.level();
        String levelColor = level.colorCode();
        player.sendMessage(Component.text(
            messages.format("fatigue.status.level", level.displayName()),
            NamedTextColor.YELLOW
        ));

        // 详细数值
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text(
            messages.format("fatigue.status.overall", fatigue.overallFatigue()),
            NamedTextColor.GRAY
        ));

        // 分类疲劳
        player.sendMessage(Component.text(
            messages.format("fatigue.status.physical", fatigue.physicalFatigue()),
            NamedTextColor.AQUA
        ));
        player.sendMessage(Component.text(
            messages.format("fatigue.status.mental", fatigue.mentalFatigue()),
            NamedTextColor.DARK_PURPLE
        ));
        player.sendMessage(Component.text(
            messages.format("fatigue.status.combat", fatigue.combatFatigue()),
            NamedTextColor.RED
        ));
        player.sendMessage(Component.text(
            messages.format("fatigue.status.travel", fatigue.travelFatigue()),
            NamedTextColor.GREEN
        ));

        // 在线时间
        long playTime = fatigue.totalPlayTime() / 60;
        player.sendMessage(Component.text(
            messages.format("fatigue.status.playtime", playTime),
            NamedTextColor.GRAY
        ));

        player.sendMessage(Component.text(""));
    }

    private void handleRest(Player player, String[] args) {
        FatigueType type = null;
        int amount = 100;

        if (args.length >= 2) {
            try {
                type = FatigueType.fromString(args[1]);
            } catch (IllegalArgumentException e) {
                try {
                    amount = Integer.parseInt(args[1]);
                } catch (NumberFormatException ex) {
                    player.sendMessage(Component.text(
                        messages.format("fatigue.rest.usage"),
                        NamedTextColor.YELLOW
                    ));
                    return;
                }
            }
        }

        if (args.length >= 3 && type == null) {
            try {
                type = FatigueType.fromString(args[2]);
            } catch (IllegalArgumentException e) {
                player.sendMessage(Component.text(
                    messages.format("fatigue.rest.usage"),
                    NamedTextColor.YELLOW
                ));
                return;
            }
        }

        if (type != null) {
            fatigueService.rest(player, type, amount);
            player.sendMessage(Component.text(
                messages.format("fatigue.rest.success", type.displayName(), amount),
                NamedTextColor.GREEN
            ));
        } else {
            fatigueService.rest(player);
            player.sendMessage(Component.text(
                messages.format("fatigue.rest.all", amount),
                NamedTextColor.GREEN
            ));
        }
    }

    private void handleReset(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text(
                messages.format("fatigue.reset.usage"),
                NamedTextColor.YELLOW
            ));
            return;
        }

        UUID targetId;
        if (args[1].equalsIgnoreCase("me") || args[1].equalsIgnoreCase("@")) {
            targetId = player.getUniqueId();
        } else {
            // 需要 op 权限才能重置其他玩家
            if (!player.hasPermission("starcore.fatigue.reset.others")) {
                player.sendMessage(Component.text(
                    messages.format("command.no-permission"),
                    NamedTextColor.RED
                ));
                return;
            }

            Player target = player.getServer().getPlayer(args[1]);
            if (target == null) {
                player.sendMessage(Component.text(
                    messages.format("fatigue.reset.player-not-found", args[1]),
                    NamedTextColor.RED
                ));
                return;
            }
            targetId = target.getUniqueId();
        }

        fatigueService.resetFatigue(targetId);
        player.sendMessage(Component.text(
            messages.format("fatigue.reset.success", args[1]),
            NamedTextColor.GREEN
        ));
    }

    private void handleEffects(Player player) {
        PlayerFatigue fatigue = fatigueService.getOrCreatePlayerFatigue(player.getUniqueId());

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("=== " + messages.format("fatigue.effects.title") + " ===", NamedTextColor.GOLD));

        // 属性惩罚
        double attackMod = fatigue.getAttackPenalty();
        double defenseMod = fatigue.getDefensePenalty();
        double speedMod = fatigue.getSpeedPenalty();
        double expMod = fatigue.getExpPenalty();

        player.sendMessage(Component.text(
            messages.format("fatigue.effects.attack", String.format("%.0f%%", attackMod * 100)),
            attackMod >= 1.0 ? NamedTextColor.GREEN : NamedTextColor.YELLOW
        ));
        player.sendMessage(Component.text(
            messages.format("fatigue.effects.defense", String.format("%.0f%%", defenseMod * 100)),
            defenseMod >= 1.0 ? NamedTextColor.GREEN : NamedTextColor.YELLOW
        ));
        player.sendMessage(Component.text(
            messages.format("fatigue.effects.speed", String.format("%.0f%%", speedMod * 100)),
            speedMod >= 1.0 ? NamedTextColor.GREEN : NamedTextColor.YELLOW
        ));
        player.sendMessage(Component.text(
            messages.format("fatigue.effects.exp", String.format("%.0f%%", expMod * 100)),
            expMod >= 1.0 ? NamedTextColor.GREEN : NamedTextColor.YELLOW
        ));

        player.sendMessage(Component.text(""));

        // 临界警告
        if (fatigue.isCritical()) {
            player.sendMessage(Component.text(
                messages.format("fatigue.effects.critical-warning"),
                NamedTextColor.RED
            ));
        }

        if (fatigue.needsForcedRest()) {
            player.sendMessage(Component.text(
                messages.format("fatigue.effects.forced-rest-warning"),
                NamedTextColor.DARK_RED
            ));
        }

        player.sendMessage(Component.text(""));
    }

    private void handleAdd(Player player, String[] args) {
        // 需要 op 权限
        if (!player.hasPermission("starcore.fatigue.admin")) {
            player.sendMessage(Component.text(
                messages.format("command.no-permission"),
                NamedTextColor.RED
            ));
            return;
        }

        if (args.length < 3) {
            player.sendMessage(Component.text(
                messages.format("fatigue.admin.add.usage"),
                NamedTextColor.YELLOW
            ));
            return;
        }

        UUID targetId;
        Player target = player.getServer().getPlayer(args[1]);
        if (target != null) {
            targetId = target.getUniqueId();
        } else {
            player.sendMessage(Component.text(
                messages.format("fatigue.admin.player-not-found", args[1]),
                NamedTextColor.RED
            ));
            return;
        }

        int amount;
        try {
            amount = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text(
                messages.format("fatigue.admin.invalid-amount", args[2]),
                NamedTextColor.RED
            ));
            return;
        }

        fatigueService.addFatigue(targetId, amount);
        player.sendMessage(Component.text(
            messages.format("fatigue.admin.add.success", target.getName(), amount),
            NamedTextColor.GREEN
        ));
    }

    private void handleReduce(Player player, String[] args) {
        // 需要 op 权限
        if (!player.hasPermission("starcore.fatigue.admin")) {
            player.sendMessage(Component.text(
                messages.format("command.no-permission"),
                NamedTextColor.RED
            ));
            return;
        }

        if (args.length < 3) {
            player.sendMessage(Component.text(
                messages.format("fatigue.admin.reduce.usage"),
                NamedTextColor.YELLOW
            ));
            return;
        }

        UUID targetId;
        Player target = player.getServer().getPlayer(args[1]);
        if (target != null) {
            targetId = target.getUniqueId();
        } else {
            player.sendMessage(Component.text(
                messages.format("fatigue.admin.player-not-found", args[1]),
                NamedTextColor.RED
            ));
            return;
        }

        int amount;
        try {
            amount = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text(
                messages.format("fatigue.admin.invalid-amount", args[2]),
                NamedTextColor.RED
            ));
            return;
        }

        fatigueService.reduceFatigue(targetId, amount);
        player.sendMessage(Component.text(
            messages.format("fatigue.admin.reduce.success", target.getName(), amount),
            NamedTextColor.GREEN
        ));
    }

    private void handleCheck(Player player, String[] args) {
        if (args.length < 2) {
            showHelp(player);
            return;
        }

        Player target = player.getServer().getPlayer(args[1]);
        if (target == null) {
            player.sendMessage(Component.text(
                messages.format("fatigue.check.player-not-found", args[1]),
                NamedTextColor.RED
            ));
            return;
        }

        UUID targetId = target.getUniqueId();
        int fatigue = fatigueService.getFatigue(targetId);
        FatigueLevel level = fatigueService.getFatigueLevel(targetId);
        double penalty = fatigueService.getFatiguePenalty(targetId);

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text(
            messages.format("fatigue.check.title", target.getName()),
            NamedTextColor.GOLD
        ));
        player.sendMessage(Component.text(
            messages.format("fatigue.check.fatigue", fatigue, level.displayName()),
            NamedTextColor.GRAY
        ));
        player.sendMessage(Component.text(
            messages.format("fatigue.check.penalty", String.format("%.0f%%", penalty * 100)),
            NamedTextColor.GRAY
        ));
        player.sendMessage(Component.text(""));
    }

    private void handleStats(Player player) {
        Map<String, Object> stats = fatigueService.getStatistics();
        String statsStr = String.format("总玩家: %d | 临界: %d | 精疲: %d | 平均疲劳: %.1f",
            stats.get("totalPlayers"),
            stats.get("criticalCount"),
            stats.get("exhaustedCount"),
            stats.get("averageFatigue"));
        player.sendMessage(Component.text(
            messages.format("fatigue.stats", statsStr),
            NamedTextColor.GRAY
        ));
    }

    private void showHelp(Player player) {
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("=== " + messages.format("fatigue.help.title") + " ===", NamedTextColor.GOLD));
        player.sendMessage(Component.text(messages.format("fatigue.help.status"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("fatigue.help.rest"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("fatigue.help.effects"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("fatigue.help.reset"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("fatigue.help.check"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(""));

        if (player.hasPermission("starcore.fatigue.admin")) {
            player.sendMessage(Component.text("=== " + messages.format("fatigue.help.admin-title") + " ===", NamedTextColor.DARK_GREEN));
            player.sendMessage(Component.text(messages.format("fatigue.help.admin-add"), NamedTextColor.GRAY));
            player.sendMessage(Component.text(messages.format("fatigue.help.admin-reduce"), NamedTextColor.GRAY));
            player.sendMessage(Component.text(""));
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> subCommands = new java.util.ArrayList<>();
            subCommands.add("status");
            subCommands.add("rest");
            subCommands.add("effects");
            subCommands.add("reset");
            subCommands.add("check");

            if (sender.hasPermission("starcore.fatigue.admin")) {
                subCommands.add("add");
                subCommands.add("reduce");
            }

            return subCommands.stream()
                .filter(s -> s.startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }

        if (args.length == 2) {
            String subCommand = args[0].toLowerCase();

            switch (subCommand) {
                case "rest" -> {
                    return java.util.stream.Stream.concat(
                        java.util.stream.Stream.of("me", "all"),
                        java.util.Arrays.stream(FatigueType.values()).map(Enum::name)
                    ).filter(s -> s.startsWith(args[1].toUpperCase()))
                     .collect(Collectors.toList());
                }
                case "reset", "check" -> {
                    return sender.getServer().getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
                }
                case "add", "reduce" -> {
                    if (sender.hasPermission("starcore.fatigue.admin")) {
                        return sender.getServer().getOnlinePlayers().stream()
                            .map(Player::getName)
                            .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                            .collect(Collectors.toList());
                    }
                }
            }
        }

        if (args.length == 3) {
            String subCommand = args[0].toLowerCase();
            if ("add".equals(subCommand) || "reduce".equals(subCommand)) {
                return List.of("10", "25", "50", "100");
            }
            if ("rest".equals(subCommand) && args[1].matches("\\d+")) {
                return java.util.Arrays.stream(FatigueType.values())
                    .map(Enum::name)
                    .filter(s -> s.startsWith(args[2].toUpperCase()))
                    .collect(Collectors.toList());
            }
        }

        return List.of();
    }
}