package dev.starcore.starcore.module.diplomacy.military.event;

import dev.starcore.starcore.module.diplomacy.military.MilitaryAllianceService;
import dev.starcore.starcore.module.diplomacy.military.MilitaryAllianceService.PactType;
import dev.starcore.starcore.module.nation.model.NationId;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * 军事联盟建立事件
 * 在两个国家正式建立军事联盟关系时触发
 */
public class MilitaryAllianceFormedEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final NationId nation1;
    private final NationId nation2;
    private final PactType pactType;

    public MilitaryAllianceFormedEvent(NationId nation1, NationId nation2, PactType pactType) {
        this.nation1 = nation1;
        this.nation2 = nation2;
        this.pactType = pactType;
    }

    public NationId getNation1() {
        return nation1;
    }

    public NationId getNation2() {
        return nation2;
    }

    public PactType getPactType() {
        return pactType;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
