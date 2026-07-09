package dev.starcore.starcore.module.army.tunnel.event;

import dev.starcore.starcore.module.army.tunnel.model.Tunnel;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

/**
 * Event fired when a player enters a tunnel
 */
public final class PlayerEnterTunnelEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    private final Tunnel tunnel;
    private final Location entryPoint;
    private boolean cancelled;

    public PlayerEnterTunnelEvent(Player player, Tunnel tunnel) {
        super(player);
        this.tunnel = tunnel;
        this.entryPoint = player.getLocation().clone();
    }

    public Tunnel getTunnel() {
        return tunnel;
    }

    public Location getEntryPoint() {
        return entryPoint;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}