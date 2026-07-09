package dev.starcore.starcore.module.army.prisoner.event;

import dev.starcore.starcore.module.army.prisoner.model.PrisonerOfWar;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * 俘虏交换事件
 * 在两名俘虏被交换时触发
 */
public class PrisonerExchangedEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final PrisonerOfWar releasedPrisoner;
    private final PrisonerOfWar capturedPrisoner;
    private final UUID exchangeInitiatedBy;

    public PrisonerExchangedEvent(
        PrisonerOfWar releasedPrisoner,
        PrisonerOfWar capturedPrisoner,
        UUID exchangeInitiatedBy
    ) {
        this.releasedPrisoner = releasedPrisoner;
        this.capturedPrisoner = capturedPrisoner;
        this.exchangeInitiatedBy = exchangeInitiatedBy;
    }

    public PrisonerOfWar getReleasedPrisoner() {
        return releasedPrisoner;
    }

    public PrisonerOfWar getCapturedPrisoner() {
        return capturedPrisoner;
    }

    public UUID getExchangeInitiatedBy() {
        return exchangeInitiatedBy;
    }

    public UUID getPrisonerReleasedNationId() {
        return releasedPrisoner.capturedNationId();
    }

    public UUID getPrisonerCapturedNationId() {
        return capturedPrisoner.capturedNationId();
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
