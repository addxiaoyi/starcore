package dev.starcore.starcore.module.vassal.event;

import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.vassal.model.VassalType;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * 宗藩关系建立事件
 * Fired when a vassal relationship is established
 */
public class VassalEstablishedEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final NationId suzerainId;
    private final NationId vassalId;
    private final VassalType type;

    public VassalEstablishedEvent(NationId suzerainId, NationId vassalId, VassalType type) {
        this.suzerainId = suzerainId;
        this.vassalId = vassalId;
        this.type = type;
    }

    public NationId suzerainId() {
        return suzerainId;
    }

    public NationId vassalId() {
        return vassalId;
    }

    public VassalType type() {
        return type;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}