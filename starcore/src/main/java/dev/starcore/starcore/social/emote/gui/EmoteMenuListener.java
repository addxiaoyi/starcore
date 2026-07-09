package dev.starcore.starcore.social.emote.gui;

import dev.starcore.starcore.social.emote.EmoteService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

/**
 * 动作菜单点击监听器
 */
public class EmoteMenuListener implements Listener {
    private final EmoteMenu emoteMenu;
    private final EmoteService emoteService;

    public EmoteMenuListener(EmoteMenu emoteMenu, EmoteService emoteService) {
        this.emoteMenu = emoteMenu;
        this.emoteService = emoteService;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        String title = event.getView().getTitle();
        if (!title.contains("动作")) {
            return;
        }

        event.setCancelled(true);

        if (event.getCurrentItem() == null) {
            return;
        }

        emoteMenu.handleClick(player, event.getRawSlot(), event.getCurrentItem());
    }
}
