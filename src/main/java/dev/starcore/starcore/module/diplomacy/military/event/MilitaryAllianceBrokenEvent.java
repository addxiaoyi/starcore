package dev.starcore.starcore.module.diplomacy.military.event;

import dev.starcore.starcore.module.diplomacy.military.MilitaryAllianceService;
import dev.starcore.starcore.module.diplomacy.military.MilitaryAllianceService.PactType;
import dev.starcore.starcore.module.nation.model.NationId;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * 军事联盟破裂事件
 * 在两个国家解除军事联盟关系时触发
 */
public class MilitaryAllianceBrokenEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final NationId nation1;
    private final NationId nation2;
    private final NationId brokenBy;
    private final PactType previousPactType;

    public MilitaryAllianceBrokenEvent(NationId nation1, NationId nation2, NationId brokenBy, PactType previousPactType) {
        this.nation1 = nation1;
        this.nation2 = nation2;
        this.brokenBy = brokenBy;
        this.previousPactType = previousPactType;
    }

    public NationId getNation1() {
        return nation1;
    }

    public NationId getNation2() {
        return nation2;
    }

    public NationId getBrokenBy() {
        return brokenBy;
    }

    public PactType getPreviousPactType() {
        return previousPactType;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
