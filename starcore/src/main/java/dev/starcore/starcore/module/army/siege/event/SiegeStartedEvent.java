package dev.starcore.starcore.module.army.siege.event;

import dev.starcore.starcore.module.army.siege.model.SiegeUnit;
import dev.starcore.starcore.module.army.siege.model.WallData;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * 攻城开始事件
 */
public final class SiegeStartedEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    private final SiegeUnit siegeUnit;
    private final WallData targetWall;

    public SiegeStartedEvent(SiegeUnit siegeUnit, WallData targetWall) {
        this.siegeUnit = siegeUnit;
        this.targetWall = targetWall;
    }

    public SiegeUnit getSiegeUnit() {
        return siegeUnit;
    }

    public WallData getTargetWall() {
        return targetWall;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
