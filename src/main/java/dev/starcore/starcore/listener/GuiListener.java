package dev.starcore.starcore.listener;

// Nukkit GUI system - removed
// import dev.starcore.starcore.gui.GuiManager;
// import dev.starcore.starcore.gui.Gui;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

/**
 * GUI事件监听器 (Nukkit GUI system removed)
 */
public final class GuiListener implements Listener {

    // GUI functionality temporarily disabled - Nukkit GUI system removed
    /*
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Gui gui = GuiManager.getGui(player);
        if (gui == null) return;

        // 取消点击
        event.setCancelled(true);

        // 处理点击
        int slot = event.getRawSlot();
        gui.handleClick(player, slot);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        Gui gui = GuiManager.getGui(player);
        if (gui != null) {
            GuiManager.unregisterGui(player);
        }
    }
    */
}
