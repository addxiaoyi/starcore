package dev.starcore.starcore.module.lease.event;

import dev.starcore.starcore.module.lease.model.LeaseContract;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * 租约激活事件（签署完成，正式生效）
 */
public class LeaseActivatedEvent extends Event implements Cancellable {
    private static final HandlerList handlers = new HandlerList();

    private final LeaseContract contract;
    private boolean cancelled;

    public LeaseActivatedEvent(LeaseContract contract) {
        this.contract = contract;
    }

    public LeaseContract getContract() {
        return contract;
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