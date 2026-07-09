package dev.starcore.starcore.module.army.prisoner.event;

import dev.starcore.starcore.module.army.prisoner.model.PrisonerOfWar;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * 俘虏被捕获事件
 * 在玩家被俘虏时触发
 */
public class PrisonerCapturedEvent extends Event implements Cancellable {
    private static final HandlerList handlers = new HandlerList();

    private final PrisonerOfWar prisoner;
    private boolean cancelled;

    public PrisonerCapturedEvent(PrisonerOfWar prisoner) {
        this.prisoner = prisoner;
    }

    public PrisonerOfWar getPrisoner() {
        return prisoner;
    }

    public UUID getPrisonerId() {
        return prisoner.prisonerId();
    }

    public UUID getCaptorNationId() {
        return prisoner.captorNationId();
    }

    public UUID getCapturedNationId() {
        return prisoner.capturedNationId();
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
