package dev.starcore.starcore.module.resolution;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

/**
 * Listens for player chat input for resolution proposals.
 * Redirects input to ResolutionInputHandler when player has active session.
 */
public class ResolutionChatListener implements Listener {

    private final ResolutionInputHandler inputHandler;

    public ResolutionChatListener(ResolutionInputHandler inputHandler) {
        this.inputHandler = inputHandler;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();

        // Check if player has active input session
        if (inputHandler.hasActiveSession(player.getUniqueId())) {
            event.setCancelled(true);

            String message = event.getMessage();

            // Handle on main thread to avoid issues
            player.getServer().getScheduler().runTask(
                player.getServer().getPluginManager().getPlugin("StarCore"),
                () -> {
                    inputHandler.handleChatInput(player, message);
                }
            );
        }
    }
}
