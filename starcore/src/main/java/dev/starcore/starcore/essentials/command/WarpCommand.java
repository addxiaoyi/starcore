package dev.starcore.starcore.essentials.command;

import dev.starcore.starcore.essentials.teleport.TeleportService;
import dev.starcore.starcore.essentials.warp.WarpService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Warp传送点命令
 * /warp, /setwarp, /delwarp, /warps
 */
public final class WarpCommand implements CommandExecutor, TabCompleter {
    private final WarpService warpService;
    private final TeleportService teleportService;

    public WarpCommand(WarpService warpService, TeleportService teleportService) {
        this.warpService = warpService;
        this.teleportService = teleportService;
    }

    @Override
    public boolean onCommand(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String label,
        @NotNull String[] args
    ) {
        String commandName = command.getName().toLowerCase();

        switch (commandName) {
            case "warp" -> handleWarp(sender, args);
            case "setwarp" -> handleSetWarp(sender, args);
            case "delwarp" -> handleDelWarp(sender, args);
            case "warps" -> handleListWarps(sender);
        }

        return true;
    }

    /**
     * 传送到传送点 - 使用 TeleportService 实现延迟传送
     */
    private void handleWarp(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("只有玩家可以使用此命令", NamedTextColor.RED));
            return;
        }

        if (args.length < 1) {
            sender.sendMessage(Component.text(
                "用法: /warp <名称>",
                NamedTextColor.YELLOW
            ));
            sender.sendMessage(Component.text(
                "使用 /warps 查看所有传送点",
                NamedTextColor.GRAY
            ));
            return;
        }

        String warpName = args[0];

        // 检查传送点是否存在
        if (!warpService.warpExists(warpName)) {
            sender.sendMessage(Component.text(
                "传送点不存在: " + warpName,
                NamedTextColor.RED
            ));
            sender.sendMessage(Component.text(
                "使用 /warps 查看所有传送点",
                NamedTextColor.GRAY
            ));
            return;
        }

        // 使用 TeleportService 进行延迟传送
        teleportService.teleportToWarp(player, warpName);
    }

    /**
     * 设置传送点（管理员）
     */
    private void handleSetWarp(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("只有玩家可以使用此命令", NamedTextColor.RED));
            return;
        }

        if (!player.hasPermission("starcore.warp.set")) {
            player.sendMessage(Component.text(
                "你没有权限设置传送点",
                NamedTextColor.RED
            ));
            return;
        }

        if (args.length < 1) {
            sender.sendMessage(Component.text(
                "用法: /setwarp <名称>",
                NamedTextColor.YELLOW
            ));
            return;
        }

        String warpName = args[0];

        // 验证名称
        if (!isValidWarpName(warpName)) {
            player.sendMessage(Component.text(
                "传送点名称无效（只能包含字母、数字、下划线）",
                NamedTextColor.RED
            ));
            return;
        }

        boolean success = warpService.setWarp(warpName, player.getLocation());

        if (success) {
            player.sendMessage(Component.text(
                "已设置传送点: " + warpName,
                NamedTextColor.GREEN
            ));
        } else {
            player.sendMessage(Component.text(
                "设置传送点失败",
                NamedTextColor.RED
            ));
        }
    }

    /**
     * 删除传送点（管理员）
     */
    private void handleDelWarp(CommandSender sender, String[] args) {
        if (!sender.hasPermission("starcore.warp.delete")) {
            sender.sendMessage(Component.text(
                "你没有权限删除传送点",
                NamedTextColor.RED
            ));
            return;
        }

        if (args.length < 1) {
            sender.sendMessage(Component.text(
                "用法: /delwarp <名称>",
                NamedTextColor.YELLOW
            ));
            return;
        }

        String warpName = args[0];
        boolean success = warpService.deleteWarp(warpName);

        if (success) {
            sender.sendMessage(Component.text(
                "已删除传送点: " + warpName,
                NamedTextColor.GREEN
            ));
        } else {
            sender.sendMessage(Component.text(
                "传送点不存在: " + warpName,
                NamedTextColor.RED
            ));
        }
    }

    /**
     * 列出所有传送点
     */
    private void handleListWarps(CommandSender sender) {
        List<String> warpNames = warpService.getWarpNames();

        if (warpNames.isEmpty()) {
            sender.sendMessage(Component.text(
                "当前没有传送点",
                NamedTextColor.YELLOW
            ));
            return;
        }

        sender.sendMessage(Component.text(
            "可用的传送点 (" + warpNames.size() + "):",
            NamedTextColor.GOLD
        ));

        for (String name : warpNames) {
            sender.sendMessage(Component.text(
                "  - " + name,
                NamedTextColor.YELLOW
            ));
        }

        sender.sendMessage(Component.text(
            "使用 /warp <名称> 传送",
            NamedTextColor.GRAY
        ));
    }

    /**
     * 验证传送点名称
     */
    private boolean isValidWarpName(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }

        if (name.length() < 2 || name.length() > 20) {
            return false;
        }

        return name.matches("^[a-zA-Z0-9_]+$");
    }

    @Override
    public @Nullable List<String> onTabComplete(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String alias,
        @NotNull String[] args
    ) {
        String commandName = command.getName().toLowerCase();

        if (args.length == 1) {
            if (commandName.equals("warp") || commandName.equals("delwarp")) {
                return new ArrayList<>(warpService.getWarpNames());
            }
        }

        return List.of();
    }
}
