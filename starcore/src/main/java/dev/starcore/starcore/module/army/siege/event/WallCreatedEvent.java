package dev.starcore.starcore.module.army.siege.event;

import dev.starcore.starcore.module.army.siege.model.WallData;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * 城墙创建事件
 */
public final class WallCreatedEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    private final WallData wall;

    public WallCreatedEvent(WallData wall) {
        this.wall = wall;
    }

    public WallData getWall() {
        return wall;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
