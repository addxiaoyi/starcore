package dev.starcore.starcore.achievement.gui;

import dev.starcore.starcore.achievement.gui.AchievementGui;
import dev.starcore.starcore.achievement.AchievementModule;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;

/**
 * 成就GUI点击事件监听器
 */
public class AchievementGuiListener implements Listener {

    private final AchievementGui gui;

    public AchievementGuiListener(AchievementGui gui) {
        this.gui = gui;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        Inventory inventory = event.getInventory();
        if (inventory == null) {
            return;
        }

        // 获取 GUI 标题 - 使用 event.getView().title() (Paper 1.21+)
        Component titleComponent = event.getView().title();
        if (titleComponent == null) {
            return;
        }
        String title = PlainTextComponentSerializer.plainText().serialize(titleComponent);

        // 检查是否是成就GUI
        if (title.contains("成就")) {
            event.setCancelled(true);

            if (event.getCurrentItem() == null) {
                return;
            }

            gui.handleClick(player, event.getRawSlot(), event.getCurrentItem());
        }
    }

    // audit H-001: 修复 PlayerQuitEvent 未清理 playerMenuState 导致的内存泄漏
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        gui.clearPlayerState(event.getPlayer());
    }
}
