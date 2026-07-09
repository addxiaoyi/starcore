package dev.starcore.starcore.module.satellite.event;

import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.satellite.SatelliteRelation;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * 卫星关系建立事件
 */
public class SatelliteRelationEstablishedEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final NationId suzerainId;
    private final NationId satelliteId;
    private final SatelliteRelation relation;

    public SatelliteRelationEstablishedEvent(NationId suzerainId, NationId satelliteId, SatelliteRelation relation) {
        this.suzerainId = suzerainId;
        this.satelliteId = satelliteId;
        this.relation = relation;
    }

    public NationId getSuzerainId() {
        return suzerainId;
    }

    public NationId getSatelliteId() {
        return satelliteId;
    }

    public SatelliteRelation getRelation() {
        return relation;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
