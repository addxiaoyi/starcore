package dev.starcore.starcore.module.army.navy.event;

import dev.starcore.starcore.module.army.navy.model.NavyUnit;
import dev.starcore.starcore.module.army.navy.model.NavyState;
import org.bukkit.Location;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * 舰队状态变更事件
 */
public final class NavyStateChangedEvent extends NavyEvent {
    private static final HandlerList handlers = new HandlerList();
    private final NavyState oldState;
    private final NavyState newState;
    private final Location location;

    public NavyStateChangedEvent(NavyUnit navy, UUID nationId, NavyState oldState, NavyState newState, Location location) {
        super(navy, nationId);
        this.oldState = oldState;
        this.newState = newState;
        this.location = location;
    }

    public NavyState getOldState() {
        return oldState;
    }

    public NavyState getNewState() {
        return newState;
    }

    public Location getLocation() {
        return location;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }
}
