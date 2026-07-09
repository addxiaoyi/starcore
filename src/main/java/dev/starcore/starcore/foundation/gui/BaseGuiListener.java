package dev.starcore.starcore.foundation.gui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BaseGui 事件监听器
 * 统一处理所有基于 BaseGui 的菜单交互
 */
public class BaseGuiListener implements Listener {

    private static final Map<UUID, BaseGui> OPEN_GUIS = new ConcurrentHashMap<>();
    // E-050: 点击冷却防止快速重复点击
    private static final Map<UUID, Long> CLICK_COOLDOWNS = new ConcurrentHashMap<>();
    private static final long CLICK_COOLDOWN_MS = 200;

    private final Plugin plugin;

    public BaseGuiListener(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 注册打开的 GUI
     */
    public static void registerGui(Player player, BaseGui gui) {
        OPEN_GUIS.put(player.getUniqueId(), gui);
    }

    /**
     * 获取玩家当前打开的 GUI
     */
    public static BaseGui getGui(Player player) {
        return OPEN_GUIS.get(player.getUniqueId());
    }

    /**
     * 注销玩家的 GUI
     */
    public static void unregisterGui(Player player) {
        OPEN_GUIS.remove(player.getUniqueId());
    }

    /**
     * 检查玩家是否有打开的 GUI
     */
    public static boolean hasGui(Player player) {
        return OPEN_GUIS.containsKey(player.getUniqueId());
    }

    // ==================== 事件处理 ====================

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        // E-050: 点击冷却防止快速重复点击导致卡顿或重复操作
        UUID playerId = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long lastClick = CLICK_COOLDOWNS.get(playerId);
        if (lastClick != null && now - lastClick < CLICK_COOLDOWN_MS) {
            event.setCancelled(true);
            return;
        }
        CLICK_COOLDOWNS.put(playerId, now);

        BaseGui gui = OPEN_GUIS.get(playerId);
        if (gui == null) {
            return;
        }

        // 取消点击，防止物品移动
        event.setCancelled(true);

        // 检查是否是同一菜单
        if (!gui.isOwnInventory(event.getInventory())) {
            return;
        }

        // 处理点击
        int slot = event.getRawSlot();
        gui.handleClick(player, slot);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        BaseGui gui = OPEN_GUIS.get(player.getUniqueId());
        if (gui == null) {
            return;
        }

        // 检查是否是同一菜单
        if (!gui.isOwnInventory(event.getInventory())) {
            return;
        }

        // 取消拖拽
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        BaseGui gui = OPEN_GUIS.remove(player.getUniqueId());
        if (gui != null) {
            gui.handleClose();
        }
    }

    /**
     * 强制关闭所有 GUI
     */
    public void closeAll() {
        for (UUID uuid : OPEN_GUIS.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.closeInventory();
            }
        }
        OPEN_GUIS.clear();
    }

    // E-038 修复: 玩家退出时清理 OPEN_GUIS Map 状态
    // E-050 修复: 同时清理点击冷却 Map
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        OPEN_GUIS.remove(playerId);
        CLICK_COOLDOWNS.remove(playerId);
    }
}
