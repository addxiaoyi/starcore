package dev.starcore.starcore.quest.gui;

import dev.starcore.starcore.quest.Quest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;

/**
 * 任务GUI事件监听器
 * 处理玩家在任务GUI中的交互
 * D-139: 事件处理器 onInventoryClick 在主线程同步执行，无需额外锁。
 * 若 questMenu.handleClick 内部有异步操作，需确保玩家状态更新与 GUI 反馈同步。
 */
public class QuestMenuListener implements Listener {

    private final QuestMenu questMenu;

    public QuestMenuListener(QuestMenu questMenu) {
        this.questMenu = questMenu;
    }

    /**
     * 处理GUI点击事件
     */
    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        // 检查是否为任务GUI
        Inventory clickedInventory = event.getClickedInventory();
        if (clickedInventory == null) {
            return;
        }

        if (!(clickedInventory.getHolder() instanceof QuestMenu.QuestHolder)) {
            return;
        }

        // 阻止物品移动
        event.setCancelled(true);

        // 处理点击
        int slot = event.getSlot();
        var clickedItem = event.getCurrentItem();
        if (clickedItem != null) {
            questMenu.handleClick(player, slot, clickedItem);
        }
    }

    /**
     * 玩家退出时清理状态
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        questMenu.clearState(event.getPlayer().getUniqueId());
    }

}
