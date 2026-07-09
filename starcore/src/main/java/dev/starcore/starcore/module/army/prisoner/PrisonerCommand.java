package dev.starcore.starcore.module.army.prisoner;

import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.army.prisoner.model.PrisonerOfWar;
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
import java.util.stream.Collectors;

/**
 * 俘虏命令处理器
 * /prisoner <子命令>
 */
public final class PrisonerCommand implements CommandExecutor, TabCompleter {
    private final PrisonerService prisonerService;
    private final MessageService messages;

    public PrisonerCommand(PrisonerService prisonerService, MessageService messages) {
        this.prisonerService = prisonerService;
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
                case "list", "ls" -> handleList(player);
                case "info", "i" -> handleInfo(player, args);
                case "release" -> handleRelease(player, args);
                case "execute" -> handleExecute(player, args);
                case "ransom" -> handleRansom(player, args);
                case "exchange" -> handleExchange(player, args);
                case "escape" -> handleEscape(player);
                case "status" -> handleStatus(player);
                case "help" -> showHelp(player);
                default -> showHelp(player);
            }
        } catch (IllegalArgumentException e) {
            player.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED));
        }

        return true;
    }

    private void handleList(Player player) {
        // 获取玩家国家
        // 这里需要集成国家服务
        player.sendMessage(Component.text("俘虏列表功能开发中...", NamedTextColor.YELLOW));
    }

    private void handleInfo(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text(
                messages.format("prisoner.info.usage"),
                NamedTextColor.YELLOW
            ));
            return;
        }

        player.sendMessage(Component.text("俘虏信息功能开发中...", NamedTextColor.YELLOW));
    }

    private void handleRelease(Player player, String[] args) {
        player.sendMessage(Component.text("释放俘虏功能开发中...", NamedTextColor.YELLOW));
    }

    private void handleExecute(Player player, String[] args) {
        player.sendMessage(Component.text("处决俘虏功能开发中...", NamedTextColor.YELLOW));
    }

    private void handleRansom(Player player, String[] args) {
        player.sendMessage(Component.text("赎金功能开发中...", NamedTextColor.YELLOW));
    }

    private void handleExchange(Player player, String[] args) {
        player.sendMessage(Component.text("交换俘虏功能开发中...", NamedTextColor.YELLOW));
    }

    private void handleEscape(Player player) {
        // 检查玩家是否为俘虏
        Optional<PrisonerOfWar> prisonerOpt = prisonerService.getPrisonerByPlayer(player.getUniqueId());
        if (prisonerOpt.isEmpty()) {
            player.sendMessage(Component.text(
                messages.format("prisoner.not-prisoner"),
                NamedTextColor.RED
            ));
            return;
        }

        // 检查是否允许逃跑
        if (prisonerService.getConfig().escapeChancePerHour() <= 0) {
            player.sendMessage(Component.text(
                messages.format("prisoner.escape.disabled"),
                NamedTextColor.RED
            ));
            return;
        }

        prisonerService.startEscape(prisonerOpt.get().id());
        player.sendMessage(Component.text(
            messages.format("prisoner.escape.started"),
            NamedTextColor.YELLOW
        ));
    }

    private void handleStatus(Player player) {
        Optional<PrisonerOfWar> prisonerOpt = prisonerService.getPrisonerByPlayer(player.getUniqueId());

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("=== 俘虏状态 ===", NamedTextColor.GOLD));

        if (prisonerOpt.isEmpty()) {
            player.sendMessage(Component.text(
                "你当前没有被俘虏",
                NamedTextColor.GREEN
            ));
        } else {
            PrisonerOfWar prisoner = prisonerOpt.get();
            player.sendMessage(Component.text(
                "状态: " + prisoner.status(),
                NamedTextColor.YELLOW
            ));
            player.sendMessage(Component.text(
                "俘虏方: " + (prisoner.captorId() != null ? prisoner.captorId().toString() : "未知"),
                NamedTextColor.GRAY
            ));
            player.sendMessage(Component.text(
                "捕获者: " + prisoner.captorName(),
                NamedTextColor.GRAY
            ));
            player.sendMessage(Component.text(
                "赎金: " + prisoner.ransomAmount(),
                NamedTextColor.GRAY
            ));
        }
        player.sendMessage(Component.text(""));
    }

    private void showHelp(Player player) {
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("=== 俘虏系统帮助 ===", NamedTextColor.GOLD));
        player.sendMessage(Component.text("/prisoner list - 查看俘虏列表", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/prisoner info <ID> - 查看俘虏详情", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/prisoner status - 查看自己的俘虏状态", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/prisoner escape - 尝试逃跑", NamedTextColor.GRAY));
        player.sendMessage(Component.text(""));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("list", "info", "release", "execute", "ransom", "exchange", "escape", "status", "help");
        }
        return List.of();
    }
}
