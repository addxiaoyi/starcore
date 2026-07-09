package dev.starcore.starcore.module.army.tunnel.event;

import dev.starcore.starcore.module.army.tunnel.model.Tunnel;
import org.bukkit.Location;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Event fired when a tunnel collapses
 */
public final class TunnelCollapsedEvent extends Event {

    private static final HandlerList handlers = new HandlerList();

    private final Tunnel tunnel;
    private final Location collapseCenter;
    private final double radius;

    public TunnelCollapsedEvent(Tunnel tunnel, Location collapseCenter, double radius) {
        this.tunnel = tunnel;
        this.collapseCenter = collapseCenter;
        this.radius = radius;
    }

    public Tunnel getTunnel() {
        return tunnel;
    }

    public Location getCollapseCenter() {
        return collapseCenter;
    }

    public double getRadius() {
        return radius;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}