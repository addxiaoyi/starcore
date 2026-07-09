package dev.starcore.starcore.module.army.prisoner.event;

import dev.starcore.starcore.module.army.prisoner.model.PrisonerOfWar;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * 俘虏逃跑事件
 * 在俘虏成功逃跑时触发
 */
public class PrisonerEscapedEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final PrisonerOfWar prisoner;
    private final UUID prisonNationId;

    public PrisonerEscapedEvent(PrisonerOfWar prisoner, UUID prisonNationId) {
        this.prisoner = prisoner;
        this.prisonNationId = prisonNationId;
    }

    public PrisonerOfWar getPrisoner() {
        return prisoner;
    }

    public UUID getPrisonerId() {
        return prisoner.prisonerId();
    }

    public UUID getPrisonNationId() {
        return prisonNationId;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
