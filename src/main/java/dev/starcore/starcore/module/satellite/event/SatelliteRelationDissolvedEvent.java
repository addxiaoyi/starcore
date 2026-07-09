package dev.starcore.starcore.module.satellite.event;

import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.satellite.SatelliteRelation;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * 卫星关系解除事件
 */
public class SatelliteRelationDissolvedEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    public enum DissolveReason {
        INDEPENDENCE,       // 卫星国独立
        RELEASE,            // 宗主国释放
        WAR_DEFEAT,         // 战争战败
        NEGOTIATION,        // 协商解除
        ADMIN_OVERRIDE      // 管理员强制解除
    }

    private final NationId suzerainId;
    private final NationId satelliteId;
    private final SatelliteRelation previousRelation;
    private final DissolveReason reason;

    public SatelliteRelationDissolvedEvent(
        NationId suzerainId,
        NationId satelliteId,
        SatelliteRelation previousRelation,
        DissolveReason reason
    ) {
        this.suzerainId = suzerainId;
        this.satelliteId = satelliteId;
        this.previousRelation = previousRelation;
        this.reason = reason;
    }

    public NationId getSuzerainId() {
        return suzerainId;
    }

    public NationId getSatelliteId() {
        return satelliteId;
    }

    public SatelliteRelation getPreviousRelation() {
        return previousRelation;
    }

    public DissolveReason getReason() {
        return reason;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
