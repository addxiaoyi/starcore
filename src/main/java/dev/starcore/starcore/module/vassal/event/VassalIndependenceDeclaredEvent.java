package dev.starcore.starcore.module.vassal.event;

import dev.starcore.starcore.module.nation.model.NationId;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * 藩属国宣布独立事件
 * Fired when a vassal declares independence
 */
public class VassalIndependenceDeclaredEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final NationId formerVassalId;
    private final NationId formerSuzerainId;

    public VassalIndependenceDeclaredEvent(NationId formerVassalId, NationId formerSuzerainId) {
        this.formerVassalId = formerVassalId;
        this.formerSuzerainId = formerSuzerainId;
    }

    public NationId formerVassalId() {
        return formerVassalId;
    }

    public NationId formerSuzerainId() {
        return formerSuzerainId;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}