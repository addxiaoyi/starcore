package dev.starcore.starcore.module.visualizer;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * 监听器 - 与 InteractionVisualizer 协同工作
 *
 * 负责:
 * - 玩家进出时的状态同步
 * - 方块交互事件（用于触发 IV 显示更新）
 */
public final class VisualizerListener implements Listener {

    private final InteractionVisualizerModule module;
    private boolean registered = true;

    public VisualizerListener(InteractionVisualizerModule module) {
        this.module = module;
    }

    public void unregister() {
        registered = false;
    }

    private boolean isActive() {
        return registered && module.isEnabled();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (isActive()) {
            module.onPlayerJoin(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (isActive()) {
            module.onPlayerQuit(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockInteract(PlayerInteractEvent event) {
        if (isActive() && event.getClickedBlock() != null) {
            module.onBlockInteract(event.getPlayer(), event.getClickedBlock());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (isActive()) {
            module.onBlockPlace(event.getPlayer(), event.getBlock());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (isActive()) {
            module.onBlockBreak(event.getPlayer(), event.getBlock());
        }
    }
}
