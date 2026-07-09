package dev.starcore.starcore.module.combat.listener;

import dev.starcore.starcore.module.combat.gui.BattleGui;
import dev.starcore.starcore.module.combat.gui.CombatGui;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * 战斗GUI事件监听器
 * 处理 CombatGui 和 BattleGui 的点击事件
 */
public final class CombatGuiListener implements Listener {
    private final CombatGui combatGui;
    private final BattleGui battleGui;

    public CombatGuiListener(CombatGui combatGui, BattleGui battleGui) {
        this.combatGui = combatGui;
        this.battleGui = battleGui;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        Inventory inventory = event.getInventory();
        String title = event.getView().getTitle();

        // 检查是否是战斗系统GUI
        if (!isCombatGui(title)) {
            return;
        }

        event.setCancelled(true);

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || !clickedItem.hasItemMeta()) {
            return;
        }

        String displayName = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());

        // 处理点击
        if (title.contains("战斗系统")) {
            handleMainMenuClick(player, event.getSlot(), clickedItem);
        } else if (title.contains("战场列表")) {
            handleBattlefieldListClick(player, event.getSlot(), clickedItem);
        }
    }

    private void handleMainMenuClick(Player player, int slot, ItemStack item) {
        String name = ChatColor.stripColor(item.getItemMeta().getDisplayName());

        switch (slot) {
            case 10 -> combatGui.openPlayerStats(player, player.getUniqueId());
            case 12 -> player.sendMessage(ChatColor.YELLOW + "使用 /combat list 查看活跃战斗");
            case 14 -> combatGui.openBattlefieldList(player);
            case 16 -> player.sendMessage(ChatColor.YELLOW + "使用 /combat stats 查看系统统计");
            case 22 -> player.closeInventory();
        }
    }

    private void handleBattlefieldListClick(Player player, int slot, ItemStack item) {
        String name = ChatColor.stripColor(item.getItemMeta().getDisplayName());
        int size = player.getOpenInventory().getTopInventory().getSize();

        if (slot == size - 9 && name.contains("返回")) {
            combatGui.openMainMenu(player);
        }
    }

    private boolean isCombatGui(String title) {
        return title.contains("战斗系统") || title.contains("战场列表") || title.contains("战斗历史") || title.contains("战斗统计");
    }
}
