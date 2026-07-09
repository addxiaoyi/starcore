package dev.starcore.starcore.module.army.siege.event;

import dev.starcore.starcore.module.army.siege.model.SiegeUnit;
import dev.starcore.starcore.module.army.siege.model.WallData;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * 城墙被摧毁事件
 */
public final class WallDestroyedEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    private final WallData wall;
    private final SiegeUnit destroyer;

    public WallDestroyedEvent(WallData wall, SiegeUnit destroyer) {
        this.wall = wall;
        this.destroyer = destroyer;
    }

    public WallData getWall() {
        return wall;
    }

    public SiegeUnit getDestroyer() {
        return destroyer;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
