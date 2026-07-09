package dev.starcore.starcore.module.army.navy.event;

import dev.starcore.starcore.module.army.navy.model.NavyUnit;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * 舰队创建事件
 */
public final class NavyCreatedEvent extends NavyEvent {
    private static final HandlerList handlers = new HandlerList();
    private final String fleetName;
    private final int initialShips;

    public NavyCreatedEvent(NavyUnit navy, UUID nationId, String fleetName, int initialShips) {
        super(navy, nationId);
        this.fleetName = fleetName;
        this.initialShips = initialShips;
    }

    public String getFleetName() {
        return fleetName;
    }

    public int getInitialShips() {
        return initialShips;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }
}
