package dev.starcore.starcore.storage;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 仓库事件监听器
 * 处理GUI交互和玩家事件
 */
public class WarehouseListener implements Listener {
    private final StorageService storageService;
    private final Map<UUID, WarehouseGUI> activeGUIs;
    private final Map<UUID, RemoteAccessGUI> activeRemoteGUIs;

    /**
     * 构造函数
     */
    public WarehouseListener(StorageService storageService) {
        this.storageService = storageService;
        this.activeGUIs = new ConcurrentHashMap<>();
        this.activeRemoteGUIs = new ConcurrentHashMap<>();
    }

    /**
     * 注册GUI
     */
    public void registerGUI(WarehouseGUI gui) {
        activeGUIs.put(gui.getViewer().getUniqueId(), gui);
    }

    /**
     * 注册远程访问GUI
     */
    public void registerRemoteGUI(RemoteAccessGUI gui) {
        activeRemoteGUIs.put(gui.getPlayer().getUniqueId(), gui);
    }

    /**
     * 处理聊天输入（用于权限添加的玩家名称输入）
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerChat(PlayerChatEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();

        // 检查仓库GUI是否有待处理的输入
        WarehouseGUI gui = activeGUIs.get(playerId);
        if (gui != null && gui.hasPendingInput()) {
            event.setCancelled(true);
            String playerName = event.getMessage().trim();

            // 处理玩家输入
            gui.handlePlayerNameInput(playerName);

            // 重新打开权限管理界面
            gui.switchMode(WarehouseGUI.GUIMode.PERMISSIONS);
        }
    }

    /**
     * 处理GUI点击事件
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onInventoryClick(InventoryClickEvent event) {
        UUID playerId = event.getWhoClicked().getUniqueId();

        // 检查仓库GUI
        WarehouseGUI gui = activeGUIs.get(playerId);
        if (gui != null && event.getInventory() == gui.getInventory()) {
            gui.handleClick(event);
            return;
        }

        // 检查远程访问GUI
        RemoteAccessGUI remoteGUI = activeRemoteGUIs.get(playerId);
        if (remoteGUI != null && event.getInventory() == remoteGUI.getInventory()) {
            event.setCancelled(true);
            int slot = event.getRawSlot();

            if (slot >= 0 && slot < 45) {
                // 点击仓库
                remoteGUI.handleWarehouseClick(slot);
            } else if (slot == 50) {
                // 刷新按钮
                remoteGUI.open();
            } else if (slot == 53) {
                // 关闭按钮
                event.getWhoClicked().closeInventory();
            }
        }
    }

    /**
     * 处理GUI关闭事件
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();

        // 保存仓库内容
        WarehouseGUI gui = activeGUIs.get(playerId);
        if (gui != null) {
            Inventory inventory = event.getInventory();
            Warehouse warehouse = gui.getWarehouse();

            // E-030: 用 try/finally 保证 remove 总能执行,避免 syncInventoryToWarehouse 抛异常时
            // 玩家 GUI 引用滞留导致后续聊天被吞、内存泄漏
            try {
                // 同步库存到仓库对象
                if (gui.getMode() == WarehouseGUI.GUIMode.NORMAL) {
                    syncInventoryToWarehouse(inventory, warehouse, playerId);
                }
            } catch (Exception e) {
                org.bukkit.Bukkit.getLogger().severe(
                    "WarehouseListener.onInventoryClose: syncInventoryToWarehouse threw for player "
                    + playerId + " warehouse " + warehouse.getWarehouseId() + ": " + e.getMessage());
            } finally {
                // E-031: 关闭 GUI 时清除 pendingInput,避免后续聊天被吞
                try {
                    gui.clearPendingInput();
                } catch (Exception ignored) {
                    // clearPendingInput 抛异常不应影响清理流程
                }
                activeGUIs.remove(playerId);
            }
        }

        // 清理远程访问GUI
        activeRemoteGUIs.remove(playerId);
    }

    /**
     * 处理玩家退出事件
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();

        // 清理GUI引用
        WarehouseGUI gui = activeGUIs.remove(playerId);
        if (gui != null) {
            try {
                // E-031: 退出时也清除 pendingInput
                gui.clearPendingInput();
                // 保存仓库内容
                Inventory inventory = event.getPlayer().getOpenInventory().getTopInventory();
                if (gui.getMode() == WarehouseGUI.GUIMode.NORMAL) {
                    syncInventoryToWarehouse(inventory, gui.getWarehouse(), playerId);
                }
            } catch (Exception e) {
                org.bukkit.Bukkit.getLogger().severe(
                    "WarehouseListener.onPlayerQuit: failed to sync inventory for player "
                    + playerId + ": " + e.getMessage());
            }
        }

        activeRemoteGUIs.remove(playerId);
    }

    /**
     * 同步库存到仓库对象
     * E-029: SHARED 仓库多人同时关闭时,clearItems+setItem 全量覆盖会让对方正在保存的物品丢失。
     * 这里按 warehouseId 维度加锁,串行化同一仓库的同步操作。
     */
    private void syncInventoryToWarehouse(Inventory inventory, Warehouse warehouse, UUID playerId) {
        Object lock = syncLockFor(warehouse.getWarehouseId());
        synchronized (lock) {
            // 清空现有物品
            warehouse.clearItems();

            // 获取仓库容量上限
            int capacity = warehouse.getCapacity();
            int slotsToSync = Math.min(inventory.getSize(), capacity);

            // 保存新的物品（防止容量溢出）
            int syncedCount = 0;
            for (int i = 0; i < inventory.getSize() && syncedCount < slotsToSync; i++) {
                org.bukkit.inventory.ItemStack item = inventory.getItem(i);
                if (item != null && item.getType() != org.bukkit.Material.AIR) {
                    StorageItem storageItem = new StorageItem(item, playerId, syncedCount);
                    warehouse.setItem(syncedCount, storageItem);
                    syncedCount++;
                }
            }

            // 如果有溢出，记录警告
            int overflow = inventory.getSize() - syncedCount;
            if (overflow > 0) {
                org.bukkit.Bukkit.getLogger().warning(
                    "Warehouse overflow prevented: " + overflow + " slots truncated for warehouse " + warehouse.getWarehouseId()
                );
            }
        }
    }

    /** E-029: 每个 warehouse 持有一把同步锁;用 ConcurrentHashMap.computeIfAbsent 保证唯一 */
    private static final java.util.concurrent.ConcurrentHashMap<UUID, Object> WAREHOUSE_SYNC_LOCKS = new java.util.concurrent.ConcurrentHashMap<>();
    private static Object syncLockFor(UUID warehouseId) {
        return WAREHOUSE_SYNC_LOCKS.computeIfAbsent(warehouseId, k -> new Object());
    }

    /**
     * 清理所有GUI
     */
    public void cleanup() {
        // 关闭所有打开的GUI
        for (WarehouseGUI gui : activeGUIs.values()) {
            gui.close();
        }

        activeGUIs.clear();
        activeRemoteGUIs.clear();
    }

    /**
     * 获取活动的GUI数量
     */
    public int getActiveGUICount() {
        return activeGUIs.size() + activeRemoteGUIs.size();
    }
}
