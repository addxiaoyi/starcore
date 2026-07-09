package dev.starcore.starcore.module.army.siege.event;

import dev.starcore.starcore.module.army.siege.model.SiegeUnit;
import org.bukkit.Location;
import org.bukkit.event.HandlerList;

/**
 * 攻城器械移动事件
 */
public final class SiegeMovedEvent extends org.bukkit.event.Event {
    private static final HandlerList handlers = new HandlerList();

    private final SiegeUnit siege;
    private final Location newLocation;

    public SiegeMovedEvent(SiegeUnit siege, Location newLocation) {
        this.siege = siege;
        this.newLocation = newLocation;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public SiegeUnit siege() {
        return siege;
    }

    public Location newLocation() {
        return newLocation;
    }
}
