package dev.starcore.starcore.module.army.siege.event;

import dev.starcore.starcore.module.army.siege.model.SiegeUnit;
import org.bukkit.Location;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * 攻城器械开火事件
 */
public final class SiegeFiredEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    private final SiegeUnit siegeUnit;
    private final Location targetLocation;
    private final double damageDealt;

    public SiegeFiredEvent(SiegeUnit siegeUnit, Location targetLocation, double damageDealt) {
        this.siegeUnit = siegeUnit;
        this.targetLocation = targetLocation;
        this.damageDealt = damageDealt;
    }

    public SiegeUnit getSiegeUnit() {
        return siegeUnit;
    }

    public Location getTargetLocation() {
        return targetLocation;
    }

    public double getDamageDealt() {
        return damageDealt;
    }

    public double damageDealt() {
        return damageDealt;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
