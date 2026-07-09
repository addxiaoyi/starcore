package dev.starcore.starcore.module.lease.event;

import dev.starcore.starcore.module.lease.model.LeaseContract;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * 租约到期事件
 */
public class LeaseExpiredEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final LeaseContract contract;

    public LeaseExpiredEvent(LeaseContract contract) {
        this.contract = contract;
    }

    public LeaseContract getContract() {
        return contract;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}