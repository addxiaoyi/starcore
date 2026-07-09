package dev.starcore.starcore.module.army.siege.event;

import dev.starcore.starcore.module.army.siege.model.SiegeUnit;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * 攻城器械创建事件
 */
public final class SiegeCreatedEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    private final SiegeUnit siegeUnit;

    public SiegeCreatedEvent(SiegeUnit siegeUnit) {
        this.siegeUnit = siegeUnit;
    }

    public SiegeUnit getSiegeUnit() {
        return siegeUnit;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
