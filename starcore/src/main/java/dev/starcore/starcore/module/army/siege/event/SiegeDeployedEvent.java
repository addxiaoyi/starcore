package dev.starcore.starcore.module.army.siege.event;

import dev.starcore.starcore.module.army.siege.model.SiegeUnit;
import org.bukkit.Location;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * 攻城器械部署事件
 */
public final class SiegeDeployedEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    private final SiegeUnit siegeUnit;
    private final Location targetLocation;

    public SiegeDeployedEvent(SiegeUnit siegeUnit, Location targetLocation) {
        this.siegeUnit = siegeUnit;
        this.targetLocation = targetLocation;
    }

    public SiegeUnit getSiegeUnit() {
        return siegeUnit;
    }

    public Location getTargetLocation() {
        return targetLocation;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
