package dev.starcore.starcore.module.lease.event;

import dev.starcore.starcore.module.lease.model.LeaseContract;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * 租约终止事件
 */
public class LeaseTerminatedEvent extends Event implements Cancellable {
    private static final HandlerList handlers = new HandlerList();

    private final LeaseContract contract;
    private final UUID terminatorId;
    private final String reason;
    private boolean cancelled;

    public LeaseTerminatedEvent(LeaseContract contract, UUID terminatorId, String reason) {
        this.contract = contract;
        this.terminatorId = terminatorId;
        this.reason = reason;
    }

    public LeaseContract getContract() {
        return contract;
    }

    public UUID getTerminatorId() {
        return terminatorId;
    }

    public String getReason() {
        return reason;
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