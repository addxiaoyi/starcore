package dev.starcore.starcore.module.lease.event;

import dev.starcore.starcore.module.lease.model.LeaseContract;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * 租约创建事件
 */
public class LeaseCreatedEvent extends Event implements Cancellable {
    private static final HandlerList handlers = new HandlerList();

    private final LeaseContract contract;
    private final UUID creatorId;
    private boolean cancelled;

    public LeaseCreatedEvent(LeaseContract contract, UUID creatorId) {
        this.contract = contract;
        this.creatorId = creatorId;
    }

    public LeaseContract getContract() {
        return contract;
    }

    public UUID getCreatorId() {
        return creatorId;
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