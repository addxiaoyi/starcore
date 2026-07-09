package dev.starcore.starcore.module.diplomacy.military.event;

import dev.starcore.starcore.module.diplomacy.military.MilitaryAllianceService;
import dev.starcore.starcore.module.diplomacy.military.MilitaryAllianceService.PactType;
import dev.starcore.starcore.module.nation.model.NationId;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * 军事联盟条约升级事件
 * 在两个国家升级军事联盟条约时触发
 */
public class MilitaryPactUpgradedEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final NationId nation1;
    private final NationId nation2;
    private final PactType oldPactType;
    private final PactType newPactType;

    public MilitaryPactUpgradedEvent(NationId nation1, NationId nation2, PactType oldPactType, PactType newPactType) {
        this.nation1 = nation1;
        this.nation2 = nation2;
        this.oldPactType = oldPactType;
        this.newPactType = newPactType;
    }

    public NationId getNation1() {
        return nation1;
    }

    public NationId getNation2() {
        return nation2;
    }

    public PactType getOldPactType() {
        return oldPactType;
    }

    public PactType getNewPactType() {
        return newPactType;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
