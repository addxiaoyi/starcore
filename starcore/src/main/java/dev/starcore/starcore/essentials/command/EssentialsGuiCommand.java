package dev.starcore.starcore.essentials.command;

import dev.starcore.starcore.essentials.baltop.BalTopService;
import dev.starcore.starcore.essentials.gui.BalTopGui;
import dev.starcore.starcore.essentials.gui.HomeGui;
import dev.starcore.starcore.essentials.gui.WarpGui;
import dev.starcore.starcore.essentials.home.HomeService;
import dev.starcore.starcore.essentials.teleport.TeleportService;
import dev.starcore.starcore.essentials.warp.WarpService;
import dev.starcore.starcore.foundation.economy.EconomyService;
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
 * Essentials GUI 命令
 * /homegui, /warpgui, /baltopgui
 */
public final class EssentialsGuiCommand implements CommandExecutor, TabCompleter {
    private final HomeService homeService;
    private final WarpService warpService;
    private final TeleportService teleportService;
    private final BalTopService balTopService;
    private final EconomyService economyService;

    public EssentialsGuiCommand(
        HomeService homeService,
        WarpService warpService,
        TeleportService teleportService,
        BalTopService balTopService,
        EconomyService economyService
    ) {
        this.homeService = homeService;
        this.warpService = warpService;
        this.teleportService = teleportService;
        this.balTopService = balTopService;
        this.economyService = economyService;
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
            case "homegui" -> handleHomeGui(player, args);
            case "warpgui" -> handleWarpGui(player, args);
            case "baltopgui" -> handleBalTopGui(player, args);
            case "teleporter" -> handleTeleporterGui(player, args);
        }

        return true;
    }

    /**
     * 打开家园 GUI
     */
    private void handleHomeGui(Player player, String[] args) {
        int page = 1;
        if (args.length > 0) {
            try {
                page = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                player.sendMessage(Component.text("无效的页码", NamedTextColor.RED));
                return;
            }
        }

        HomeGui gui = new HomeGui(player, homeService, teleportService, page);
        player.openInventory(gui.getInventory());
    }

    /**
     * 打开传送点 GUI
     */
    private void handleWarpGui(Player player, String[] args) {
        if (!player.hasPermission("starcore.warp.use")) {
            player.sendMessage(Component.text("你没有权限使用星港", NamedTextColor.RED));
            return;
        }

        int page = 1;
        if (args.length > 0) {
            try {
                page = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                player.sendMessage(Component.text("无效的页码", NamedTextColor.RED));
                return;
            }
        }

        WarpGui gui = new WarpGui(player, warpService, teleportService, page);
        player.openInventory(gui.getInventory());
    }

    /**
     * 打开财富排行榜 GUI
     */
    private void handleBalTopGui(Player player, String[] args) {
        int page = 1;
        if (args.length > 0) {
            try {
                page = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                player.sendMessage(Component.text("无效的页码", NamedTextColor.RED));
                return;
            }
        }

        BalTopGui gui = new BalTopGui(player, balTopService, economyService, page);
        player.openInventory(gui.getInventory());
    }

    /**
     * 打开传送菜单 GUI（综合传送界面）
     */
    private void handleTeleporterGui(Player player, String[] args) {
        // 综合传送菜单：家园 + 传送点
        TeleportGui gui = new TeleportGui(player, homeService, warpService, teleportService);
        player.openInventory(gui.getInventory());
    }

    @Override
    public @Nullable List<String> onTabComplete(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String alias,
        @NotNull String[] args
    ) {
        if (args.length == 1) {
            return List.of("1", "2", "3", "4", "5");
        }
        return List.of();
    }
}
