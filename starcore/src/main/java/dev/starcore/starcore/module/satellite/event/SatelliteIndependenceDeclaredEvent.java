package dev.starcore.starcore.module.satellite.event;

import dev.starcore.starcore.module.nation.model.NationId;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * 卫星国独立宣言事件
 */
public class SatelliteIndependenceDeclaredEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final NationId formerSatelliteId;
    private final NationId formerSuzerainId;
    private final String declaredBy;

    public SatelliteIndependenceDeclaredEvent(NationId formerSatelliteId, NationId formerSuzerainId, String declaredBy) {
        this.formerSatelliteId = formerSatelliteId;
        this.formerSuzerainId = formerSuzerainId;
        this.declaredBy = declaredBy;
    }

    public NationId getFormerSatelliteId() {
        return formerSatelliteId;
    }

    public NationId getFormerSuzerainId() {
        return formerSuzerainId;
    }

    public String getDeclaredBy() {
        return declaredBy;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
