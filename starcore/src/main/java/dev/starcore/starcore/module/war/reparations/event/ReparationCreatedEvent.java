package dev.starcore.starcore.module.war.reparations.event;

import dev.starcore.starcore.war.WarReparation;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * 赔款创建事件
 * 在赔款记录创建时触发
 */
public class ReparationCreatedEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final WarReparation reparation;

    public ReparationCreatedEvent(WarReparation reparation) {
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