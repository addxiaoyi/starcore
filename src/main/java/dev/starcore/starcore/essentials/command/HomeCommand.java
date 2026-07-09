package dev.starcore.starcore.essentials.command;

import dev.starcore.starcore.essentials.home.HomeService;
import dev.starcore.starcore.essentials.teleport.TeleportService;
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
 * 家园传送命令
 * /home, /sethome, /delhome, /homes
 *
 * 中文别名支持:
 *   home/家 → 传送到家
 *   sethome/设家 → 设置家
 *   delhome/删家 → 删除家
 *   homes/家列表 → 列出所有家
 */
public final class HomeCommand implements CommandExecutor, TabCompleter {
    private final HomeService homeService;
    private final TeleportService teleportService;

    public HomeCommand(HomeService homeService, TeleportService teleportService) {
        this.homeService = homeService;
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
            case "home" -> handleHome(player, args);
            case "sethome" -> handleSetHome(player, args);
            case "delhome" -> handleDelHome(player, args);
            case "homes" -> handleListHomes(player);
        }

        return true;
    }

    /**
     * 传送到家 - 使用 TeleportService 实现延迟传送
     */
    private void handleHome(Player player, String[] args) {
        String homeName = args.length > 0 ? args[0] : "home";

        // 检查家园是否存在
        if (homeService.getHome(player.getUniqueId(), homeName).isEmpty()) {
            player.sendMessage(Component.text(
                "家园 '" + homeName + "' 不存在",
                NamedTextColor.RED
            ));
            player.sendMessage(Component.text(
                "提示: 使用 /homes 查看你的所有家园",
                NamedTextColor.GRAY
            ));
            return;
        }

        // 使用 TeleportService 进行延迟传送
        teleportService.teleportToHome(player, homeName);
    }

    /**
     * 设置家
     */
    private void handleSetHome(Player player, String[] args) {
        String homeName = args.length > 0 ? args[0] : "home";

        boolean success = homeService.setHome(player, homeName);

        if (success) {
            // 检查是否是新建的家园
            player.sendMessage(Component.text(
                "已" + (homeService.getHome(player.getUniqueId(), homeName).isPresent() ? "更新" : "设置") + "家园: " + homeName,
                NamedTextColor.GREEN
            ));
        } else {
            // 设置失败时显示提示
            List<String> homes = homeService.getHomeNames(player.getUniqueId());
            if (!homes.isEmpty()) {
                player.sendMessage(Component.text(
                    "已达家园数量上限",
                    NamedTextColor.RED
                ));
            }
            player.sendMessage(Component.text(
                "提示: 使用 /homes 查看你的所有家园",
                NamedTextColor.GRAY
            ));
        }
    }

    /**
     * 删除家
     */
    private void handleDelHome(Player player, String[] args) {
        if (args.length < 1) {
            player.sendMessage(Component.text(
                "用法: /delhome <名称>",
                NamedTextColor.YELLOW
            ));
            return;
        }

        String homeName = args[0];
        boolean success = homeService.deleteHome(player, homeName);

        if (!success) {
            player.sendMessage(Component.text(
                "家园 '" + homeName + "' 不存在",
                NamedTextColor.RED
            ));
        }
    }

    /**
     * 列出所有家
     */
    private void handleListHomes(Player player) {
        homeService.listHomes(player);
    }

    @Override
    public @Nullable List<String> onTabComplete(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String alias,
        @NotNull String[] args
    ) {
        if (!(sender instanceof Player player)) {
            return List.of();
        }

        if (args.length == 1) {
            String commandName = command.getName().toLowerCase();
            if (commandName.equals("home") || commandName.equals("delhome")) {
                return new ArrayList<>(homeService.getHomeNames(player.getUniqueId()));
            }
        }

        return List.of();
    }
}
