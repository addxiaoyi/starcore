package dev.starcore.starcore.module.army.prisoner.event;

import dev.starcore.starcore.module.army.prisoner.model.PrisonerOfWar;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * 俘虏被处决事件
 * 在俘虏被处决时触发
 */
public class PrisonerExecutedEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final PrisonerOfWar prisoner;
    private final UUID executedBy;
    private final String reason;

    public PrisonerExecutedEvent(PrisonerOfWar prisoner, UUID executedBy, String reason) {
        this.prisoner = prisoner;
        this.executedBy = executedBy;
        this.reason = reason != null ? reason : "";
    }

    public PrisonerOfWar getPrisoner() {
        return prisoner;
    }

    public UUID getPrisonerId() {
        return prisoner.prisonerId();
    }

    public UUID getExecutedBy() {
        return executedBy;
    }

    public String getReason() {
        return reason;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
