package dev.starcore.starcore.module.army.navy.event;

import dev.starcore.starcore.module.army.navy.model.NavyUnit;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * 舰队解散事件
 */
public final class NavyDisbandedEvent extends NavyEvent {
    private static final HandlerList handlers = new HandlerList();

    public NavyDisbandedEvent(NavyUnit navy, UUID nationId) {
        super(navy, nationId);
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }
}
