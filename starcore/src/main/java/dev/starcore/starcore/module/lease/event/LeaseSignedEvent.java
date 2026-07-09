package dev.starcore.starcore.module.lease.event;

import dev.starcore.starcore.module.lease.model.LeaseContract;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * 租约签署事件
 */
public class LeaseSignedEvent extends Event implements Cancellable {
    private static final HandlerList handlers = new HandlerList();

    private final LeaseContract contract;
    private final UUID signerId;
    private final boolean isLessor;
    private boolean cancelled;

    public LeaseSignedEvent(LeaseContract contract, UUID signerId, boolean isLessor) {
        this.contract = contract;
        this.signerId = signerId;
        this.isLessor = isLessor;
    }

    public LeaseContract getContract() {
        return contract;
    }

    public UUID getSignerId() {
        return signerId;
    }

    public boolean isLessor() {
        return isLessor;
    }

    public boolean isTenant() {
        return !isLessor;
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