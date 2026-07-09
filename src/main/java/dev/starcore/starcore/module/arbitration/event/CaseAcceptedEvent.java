package dev.starcore.starcore.module.arbitration.event;

import dev.starcore.starcore.module.nation.model.NationId;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * 仲裁案件受理事件
 * 当仲裁员接受一个仲裁案件时触发
 */
public class CaseAcceptedEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final UUID caseId;
    private final NationId claimantId;
    private final NationId respondentId;
    private final UUID arbitratorId;

    public CaseAcceptedEvent(UUID caseId, NationId claimantId, NationId respondentId, UUID arbitratorId) {
        this.caseId = caseId;
        this.claimantId = claimantId;
        this.respondentId = respondentId;
        this.arbitratorId = arbitratorId;
    }

    public UUID getCaseId() {
        return caseId;
    }

    public NationId getClaimantId() {
        return claimantId;
    }

    public NationId getRespondentId() {
        return respondentId;
    }

    public UUID getArbitratorId() {
        return arbitratorId;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
