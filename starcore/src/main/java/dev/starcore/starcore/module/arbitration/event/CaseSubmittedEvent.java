package dev.starcore.starcore.module.arbitration.event;

import dev.starcore.starcore.module.arbitration.model.ArbitrationCase;
import dev.starcore.starcore.module.arbitration.model.ArbitrationCaseType;
import dev.starcore.starcore.module.nation.model.NationId;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * 仲裁案件提交事件
 * 当一个新的仲裁申请被提交时触发
 */
public class CaseSubmittedEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final UUID caseId;
    private final NationId claimantId;
    private final NationId respondentId;
    private final ArbitrationCaseType caseType;
    private final String respondentName;

    public CaseSubmittedEvent(ArbitrationCase arbitrationCase) {
        this.caseId = arbitrationCase.id();
        this.claimantId = arbitrationCase.claimant();
        this.respondentId = arbitrationCase.respondent();
        this.caseType = arbitrationCase.caseType();
        this.respondentName = null;
    }

    public CaseSubmittedEvent(UUID caseId, NationId claimantId, NationId respondentId, ArbitrationCaseType caseType) {
        this.caseId = caseId;
        this.claimantId = claimantId;
        this.respondentId = respondentId;
        this.caseType = caseType;
        this.respondentName = null;
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

    public ArbitrationCaseType getCaseType() {
        return caseType;
    }

    public String getRespondentName() {
        return respondentName;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
