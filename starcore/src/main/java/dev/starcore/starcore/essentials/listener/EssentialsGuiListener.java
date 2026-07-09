package dev.starcore.starcore.essentials.listener;

import dev.starcore.starcore.essentials.baltop.BalTopService;
import dev.starcore.starcore.essentials.command.EssentialsGuiCommand;
import dev.starcore.starcore.essentials.command.TeleportGui;
import dev.starcore.starcore.essentials.gui.BalTopGui;
import dev.starcore.starcore.essentials.gui.HomeGui;
import dev.starcore.starcore.essentials.gui.WarpGui;
import dev.starcore.starcore.essentials.home.HomeService;
import dev.starcore.starcore.essentials.teleport.TeleportService;
import dev.starcore.starcore.essentials.warp.WarpService;
import dev.starcore.starcore.foundation.economy.EconomyService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Essentials GUI 事件监听器
 */
public final class EssentialsGuiListener implements Listener {
    private final Plugin plugin;
    private final HomeService homeService;
    private final WarpService warpService;
    private final TeleportService teleportService;
    private final BalTopService balTopService;
    private final EconomyService economyService;

    // 追踪打开的 GUI
    private final ConcurrentHashMap<UUID, String> openGuis = new ConcurrentHashMap<>();
    private static final String HOME_GUI = "home";
    private static final String WARP_GUI = "warp";
    private static final String BALTOP_GUI = "baltop";
    private static final String TELEPORT_GUI = "teleporter";

    public EssentialsGuiListener(
        Plugin plugin,
        HomeService homeService,
        WarpService warpService,
        TeleportService teleportService,
        BalTopService balTopService,
        EconomyService economyService
    ) {
        this.plugin = plugin;
        this.homeService = homeService;
        this.warpService = warpService;
        this.teleportService = teleportService;
        this.balTopService = balTopService;
        this.economyService = economyService;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        String guiType = openGuis.get(player.getUniqueId());
        if (guiType == null) {
            return;
        }

        // 取消点击
        event.setCancelled(true);

        int slot = event.getRawSlot();
        String title = event.getView().getTitle();

        switch (guiType) {
            case HOME_GUI -> handleHomeGuiClick(player, slot, title);
            case WARP_GUI -> handleWarpGuiClick(player, slot, title);
            case BALTOP_GUI -> handleBalTopGuiClick(player, slot, title);
            case TELEPORT_GUI -> handleTeleportGuiClick(player, slot);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        openGuis.remove(player.getUniqueId());
    }

    // ==================== 家园 GUI 处理 ====================

    private void handleHomeGuiClick(Player player, int slot, String title) {
        HomeGui.HomeAction action = HomeGui.getActionFromSlot(slot);

        switch (action) {
            case CLOSE -> player.closeInventory();
            case SET_HOME -> {
                player.closeInventory();
                player.sendMessage(Component.text("使用 /sethome [名称] 来设置新家园", NamedTextColor.YELLOW));
            }
            case PREV_PAGE -> {
                int page = extractPage(title);
                openHomeGui(player, page - 1);
            }
            case NEXT_PAGE -> {
                int page = extractPage(title);
                openHomeGui(player, page + 1);
            }
            case TELEPORT_HOME -> {
                HomeGui gui = new HomeGui(player, homeService, teleportService, extractPage(title));
                String homeName = gui.getHomeNameFromSlot(slot);
                if (homeName != null) {
                    player.closeInventory();
                    teleportService.teleportToHome(player, homeName);
                }
            }
            default -> { }
        }
    }

    // ==================== 传送点 GUI 处理 ====================

    private void handleWarpGuiClick(Player player, int slot, String title) {
        WarpGui.WarpAction action = WarpGui.getActionFromSlot(slot);

        switch (action) {
            case CLOSE -> player.closeInventory();
            case PREV_PAGE -> {
                int page = extractPage(title);
                openWarpGui(player, page - 1);
            }
            case NEXT_PAGE -> {
                int page = extractPage(title);
                openWarpGui(player, page + 1);
            }
            case TELEPORT_WARP -> {
                WarpGui gui = new WarpGui(player, warpService, teleportService, extractPage(title));
                String warpName = gui.getWarpNameFromSlot(slot);
                if (warpName != null) {
                    player.closeInventory();
                    teleportService.teleportToWarp(player, warpName);
                }
            }
            default -> { }
        }
    }

    // ==================== 财富排行榜 GUI 处理 ====================

    private void handleBalTopGuiClick(Player player, int slot, String title) {
        BalTopGui.BalTopAction action = BalTopGui.getActionFromSlot(slot);

        switch (action) {
            case CLOSE -> player.closeInventory();
            case REFRESH -> {
                int page = extractPage(title);
                // 刷新排行榜
                balTopService.updateRankings(economyService.getAllBalances());
                player.sendMessage(Component.text("排行榜已刷新", NamedTextColor.GREEN));
                openBalTopGui(player, page);
            }
            case PREV_PAGE -> {
                int page = extractPage(title);
                openBalTopGui(player, page - 1);
            }
            case NEXT_PAGE -> {
                int page = extractPage(title);
                openBalTopGui(player, page + 1);
            }
            case VIEW_PLAYER -> {
                BalTopGui gui = new BalTopGui(player, balTopService, economyService, extractPage(title));
                BalTopGui.BalTopAction currentAction = BalTopGui.getActionFromSlot(slot);
                if (currentAction == BalTopGui.BalTopAction.VIEW_PLAYER) {
                    BalTopService.BalTopEntry entry = gui.getEntryFromSlot(slot);
                    if (entry != null) {
                        player.sendMessage(Component.text(
                            "玩家: " + player.getServer().getOfflinePlayer(entry.playerId()).getName(),
                            NamedTextColor.GOLD
                        ));
                        player.sendMessage(Component.text(
                            "余额: " + String.format("%.2f", entry.balance().doubleValue()),
                            NamedTextColor.GREEN
                        ));
                    }
                }
            }
            default -> { }
        }
    }

    // ==================== 综合传送 GUI 处理 ====================

    private void handleTeleportGuiClick(Player player, int slot) {
        TeleportGui.TeleportAction action = TeleportGui.getActionFromSlot(slot);

        switch (action) {
            case CLOSE -> player.closeInventory();
            case SPAWN -> {
                player.closeInventory();
                teleportService.teleportToSpawn(player);
            }
            case HOME_MENU -> {
                openHomeGui(player, 1);
            }
            case WARP_MENU -> {
                if (!player.hasPermission("starcore.warp.use")) {
                    player.sendMessage(Component.text("你没有权限使用星港", NamedTextColor.RED));
                    return;
                }
                openWarpGui(player, 1);
            }
            case BACK -> {
                player.closeInventory();
                teleportService.teleportBack(player);
            }
            default -> { }
        }
    }

    // ==================== 辅助方法 ====================

    private int extractPage(String title) {
        // 从标题中提取页码，例如 "家园管理 (第2页)" -> 2
        try {
            int start = title.indexOf("(第");
            int end = title.indexOf("页)");
            if (start != -1 && end != -1) {
                String pageStr = title.substring(start + 2, end);
                return Integer.parseInt(pageStr.trim());
            }
        } catch (Exception e) {
            // 忽略
        }
        return 1;
    }

    private void openHomeGui(Player player, int page) {
        player.closeInventory();
        HomeGui gui = new HomeGui(player, homeService, teleportService, page);
        player.openInventory(gui.getInventory());
        openGuis.put(player.getUniqueId(), HOME_GUI);
    }

    private void openWarpGui(Player player, int page) {
        player.closeInventory();
        WarpGui gui = new WarpGui(player, warpService, teleportService, page);
        player.openInventory(gui.getInventory());
        openGuis.put(player.getUniqueId(), WARP_GUI);
    }

    private void openBalTopGui(Player player, int page) {
        player.closeInventory();
        BalTopGui gui = new BalTopGui(player, balTopService, economyService, page);
        player.openInventory(gui.getInventory());
        openGuis.put(player.getUniqueId(), BALTOP_GUI);
    }

    private void openTeleporterGui(Player player) {
        player.closeInventory();
        TeleportGui gui = new TeleportGui(player, homeService, warpService, teleportService);
        player.openInventory(gui.getInventory());
        openGuis.put(player.getUniqueId(), TELEPORT_GUI);
    }

    // E-039 修复: 玩家退出时清理 openGuis Map 状态
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        openGuis.remove(event.getPlayer().getUniqueId());
    }
}
