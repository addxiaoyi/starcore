package dev.starcore.starcore.module.army.siege.event;

import dev.starcore.starcore.module.army.siege.model.SiegeUnit;
import org.bukkit.event.HandlerList;

/**
 * 攻城器械撤退事件
 */
public final class SiegeRetreatedEvent extends org.bukkit.event.Event {
    private static final HandlerList handlers = new HandlerList();

    private final SiegeUnit siege;

    public SiegeRetreatedEvent(SiegeUnit siege) {
        this.siege = siege;
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
}
