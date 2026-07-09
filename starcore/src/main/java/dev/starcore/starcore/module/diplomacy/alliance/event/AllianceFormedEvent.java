package dev.starcore.starcore.module.diplomacy.alliance.event;

import dev.starcore.starcore.module.nation.model.NationId;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * 联盟建立事件
 * 在两个国家正式建立联盟关系时触发
 */
public class AllianceFormedEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final NationId nation1;
    private final NationId nation2;

    public AllianceFormedEvent(NationId nation1, NationId nation2) {
        this.nation1 = nation1;
        this.nation2 = nation2;
    }

    public NationId getNation1() {
        return nation1;
    }

    public NationId getNation2() {
        return nation2;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
