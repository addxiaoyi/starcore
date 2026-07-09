package dev.starcore.starcore.module.faith.command;

import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.faith.FaithService;
import dev.starcore.starcore.module.faith.model.FaithStats;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * 信仰命令处理器
 * /faith <子命令>
 */
public final class FaithCommand implements CommandExecutor, TabCompleter {
    private final FaithService faithService;
    private final NationService nationService;
    private final MessageService messages;

    public FaithCommand(FaithService faithService, NationService nationService, MessageService messages) {
        this.faithService = faithService;
        this.nationService = nationService;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
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
                case "info", "i" -> handleInfo(player);
                case "stat", "s" -> handleStats(player);
                case "blessing", "b" -> handleBlessing(player, args);
                case "leaderboard", "top" -> handleLeaderboard(player, args);
                case "help", "?" -> showHelp(player);
                default -> showHelp(player);
            }
        } catch (Exception e) {
            player.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED));
        }

        return true;
    }

    private void handleInfo(Player player) {
        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());

        if (nationOpt.isEmpty()) {
            player.sendMessage(Component.text(
                messages.format("faith.not-in-nation"),
                NamedTextColor.RED
            ));
            return;
        }

        Nation nation = nationOpt.get();
        FaithStats stats = faithService.getStats(nation.id());

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text(messages.format("faith.info.header", nation.name()), NamedTextColor.GOLD));
        player.sendMessage(Component.text(messages.format("faith.info.faith", stats.currentFaith(), faithService.getMaxFaith()), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("faith.info.level", stats.faithLevel(), stats.faithLevelName()), NamedTextColor.YELLOW));
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text(messages.format("faith.info.prayers-total", stats.totalPrayers()), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("faith.info.prayers-today", stats.todayPrayers()), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("faith.info.consecutive-days", stats.consecutiveDays()), NamedTextColor.GRAY));
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text(messages.format("faith.info.bonuses"), NamedTextColor.GOLD));
        player.sendMessage(Component.text(messages.format("faith.info.resource-bonus", formatPercent(stats.resourceBonus())), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("faith.info.defense-bonus", formatPercent(stats.defenseBonus())), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("faith.info.tax-bonus", formatPercent(stats.taxBonus())), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("faith.info.exp-bonus", formatPercent(stats.expBonus())), NamedTextColor.GRAY));
        player.sendMessage(Component.text(""));
    }

    private void handleStats(Player player) {
        // 显示个人统计信息
        // 这是一个简化的版本，可以扩展为显示更详细的数据
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text(messages.format("faith.stats.header"), NamedTextColor.GOLD));
        player.sendMessage(Component.text(messages.format("faith.stats.prayer-tip"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(""));
    }

    private void handleBlessing(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text(
                messages.format("faith.blessing.usage"),
                NamedTextColor.YELLOW
            ));
            showBlessingTypes(player);
            return;
        }

        String blessingType = args[1].toLowerCase();

        // 验证祈福类型
        if (!faithService.getConfig().blessingCosts().containsKey(blessingType)) {
            player.sendMessage(Component.text(
                messages.format("faith.blessing.invalid-type"),
                NamedTextColor.RED
            ));
            showBlessingTypes(player);
            return;
        }

        Optional<Nation> nationOpt = nationService.nationOf(player.getUniqueId());

        if (nationOpt.isEmpty()) {
            player.sendMessage(Component.text(
                messages.format("faith.not-in-nation"),
                NamedTextColor.RED
            ));
            return;
        }

        Nation nation = nationOpt.get();

        // 检查是否为领导者或官员
        if (!nation.founderId().equals(player.getUniqueId())) {
            player.sendMessage(Component.text(
                messages.format("faith.blessing.leader-only"),
                NamedTextColor.RED
            ));
            return;
        }

        // 执行祈福
        boolean success = faithService.useFaithBlessing(nation.id(), blessingType);

        if (success) {
            int currentFaith = faithService.getFaith(nation.id());
            player.sendMessage(Component.text(
                messages.format("faith.blessing.success", getBlessingDisplayName(blessingType), currentFaith),
                NamedTextColor.GREEN
            ));
        } else {
            player.sendMessage(Component.text(
                messages.format("faith.blessing.failed"),
                NamedTextColor.RED
            ));
        }
    }

    private void handleLeaderboard(Player player, String[] args) {
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text(messages.format("faith.leaderboard.header"), NamedTextColor.GOLD));

        // 这里可以扩展为从 faithService 获取所有国家的信仰排名
        // 目前简化为显示提示信息
        player.sendMessage(Component.text(
            messages.format("faith.leaderboard.tip"),
            NamedTextColor.GRAY
        ));

        player.sendMessage(Component.text(""));
    }

    private void showHelp(Player player) {
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text(messages.format("faith.help.header"), NamedTextColor.GOLD));
        player.sendMessage(Component.text(messages.format("faith.help.info"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("faith.help.blessing"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(messages.format("faith.help.leaderboard"), NamedTextColor.GRAY));
        player.sendMessage(Component.text(""));
    }

    private void showBlessingTypes(Player player) {
        player.sendMessage(Component.text(messages.format("faith.blessing.types"), NamedTextColor.YELLOW));

        var costs = faithService.getConfig().blessingCosts();
        for (var entry : costs.entrySet()) {
            String name = getBlessingDisplayName(entry.getKey());
            int cost = entry.getValue().intValue();
            player.sendMessage(Component.text(
                "  " + name + " - " + cost + " 信仰值",
                NamedTextColor.GRAY
            ));
        }
    }

    private String getBlessingDisplayName(String blessingType) {
        return switch (blessingType.toLowerCase()) {
            case "prosperity" -> "繁荣祈福";
            case "protection" -> "守护祈福";
            case "harvest" -> "丰收祈福";
            case "blessing" -> "通用祈福";
            default -> blessingType;
        };
    }

    private String formatPercent(double value) {
        return String.format("%.1f%%", value * 100);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("info", "stat", "blessing", "leaderboard", "help");
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("blessing")) {
            return new ArrayList<>(faithService.getConfig().blessingCosts().keySet());
        }

        return List.of();
    }
}