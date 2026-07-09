package dev.starcore.starcore.module.arbitration.event;

import dev.starcore.starcore.module.arbitration.model.ArbitrationResult;
import dev.starcore.starcore.module.nation.model.NationId;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * 仲裁裁决事件
 * 当仲裁员做出最终裁决时触发
 */
public class CaseRuledEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final UUID caseId;
    private final NationId claimantId;
    private final NationId respondentId;
    private final UUID arbitratorId;
    private final ArbitrationResult result;
    private final String ruling;
    private final boolean territoryTransfer;

    public CaseRuledEvent(
        UUID caseId,
        NationId claimantId,
        NationId respondentId,
        UUID arbitratorId,
        ArbitrationResult result,
        String ruling,
        boolean territoryTransfer
    ) {
        this.caseId = caseId;
        this.claimantId = claimantId;
        this.respondentId = respondentId;
        this.arbitratorId = arbitratorId;
        this.result = result;
        this.ruling = ruling;
        this.territoryTransfer = territoryTransfer;
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

    public ArbitrationResult getResult() {
        return result;
    }

    public String getRuling() {
        return ruling;
    }

    public boolean isTerritoryTransfer() {
        return territoryTransfer;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
