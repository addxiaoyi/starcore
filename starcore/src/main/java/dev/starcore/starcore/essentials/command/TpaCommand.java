package dev.starcore.starcore.essentials.command;

import dev.starcore.starcore.essentials.teleport.TeleportService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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

/**
 * TPA传送请求命令
 * /tpa, /tpaccept, /tpdeny, /back
 */
public final class TpaCommand implements CommandExecutor, TabCompleter {
    private final TeleportService teleportService;

    public TpaCommand(TeleportService teleportService) {
        this.teleportService = teleportService;
    }

    @Override
    public boolean onCommand(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String label,
        @NotNull String[] args
    ) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("只有玩家可以使用此命令", NamedTextColor.RED));
            return true;
        }

        String commandName = command.getName().toLowerCase();

        switch (commandName) {
            case "tpa" -> handleTpa(player, args);
            case "tpaccept" -> handleTpAccept(player);
            case "tpdeny" -> handleTpDeny(player);
            case "back" -> handleBack(player);
            case "spawn" -> handleSpawn(player);
            case "setspawn" -> handleSetSpawn(player);
        }

        return true;
    }

    /**
     * 请求传送
     */
    private void handleTpa(Player player, String[] args) {
        if (args.length < 1) {
            player.sendMessage(Component.text(
                "用法: /tpa <玩家>",
                NamedTextColor.YELLOW
            ));
            return;
        }

        Player target = Bukkit.getPlayer(args[0]);

        if (target == null || !target.isOnline()) {
            player.sendMessage(Component.text(
                "玩家不在线: " + args[0],
                NamedTextColor.RED
            ));
            return;
        }

        if (target.equals(player)) {
            player.sendMessage(Component.text(
                "你不能传送到自己",
                NamedTextColor.RED
            ));
            return;
        }

        teleportService.sendTeleportRequest(player, target);
    }

    /**
     * 接受传送
     */
    private void handleTpAccept(Player player) {
        teleportService.acceptTeleportRequest(player);
    }

    /**
     * 拒绝传送
     */
    private void handleTpDeny(Player player) {
        teleportService.denyTeleportRequest(player);
    }

    /**
     * 返回上一个位置
     */
    private void handleBack(Player player) {
        teleportService.teleportBack(player);
    }

    /**
     * 传送到主城
     */
    private void handleSpawn(Player player) {
        if (!player.hasPermission("starcore.spawn")) {
            player.sendMessage(Component.text(
                "你没有权限使用此命令",
                NamedTextColor.RED
            ));
            return;
        }

        teleportService.teleportToSpawn(player);
    }

    /**
     * 设置主城传送点
     */
    private void handleSetSpawn(Player player) {
        // 检查权限
        if (!teleportService.canSetSpawn(player)) {
            player.sendMessage(Component.text(
                "你没有权限设置主城传送点",
                NamedTextColor.RED
            ));
            return;
        }

        // 设置当前位置为主城
        teleportService.setSpawnLocation(player.getLocation());
        player.sendMessage(Component.text(
            "主城传送点已设置在你的当前位置",
            NamedTextColor.GREEN
        ));
    }

    @Override
    public @Nullable List<String> onTabComplete(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String alias,
        @NotNull String[] args
    ) {
        if (command.getName().equalsIgnoreCase("tpa") && args.length == 1) {
            List<String> players = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!p.equals(sender)) {
                    players.add(p.getName());
                }
            }
            return players;
        }

        return List.of();
    }
}
