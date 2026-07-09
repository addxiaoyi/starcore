package dev.starcore.starcore.module.war.reparations.event;

import dev.starcore.starcore.war.WarReparation;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * 赔款违约事件
 * 在赔款逾期违约时触发
 */
public class ReparationDefaultedEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final WarReparation reparation;

    public ReparationDefaultedEvent(WarReparation reparation) {
        this.reparation = reparation;
    }

    public WarReparation getReparation() {
        return reparation;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}