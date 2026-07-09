package dev.starcore.starcore.module.army.tunnel.event;

import dev.starcore.starcore.module.army.tunnel.model.Tunnel;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Event fired when a new tunnel is created
 */
public final class TunnelCreatedEvent extends Event {

    private static final HandlerList handlers = new HandlerList();

    private final Tunnel tunnel;

    public TunnelCreatedEvent(Tunnel tunnel) {
        this.tunnel = tunnel;
    }

    public Tunnel getTunnel() {
        return tunnel;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}