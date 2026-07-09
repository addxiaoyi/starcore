package dev.starcore.starcore.module.sovereignty.event;

import dev.starcore.starcore.module.sovereignty.model.SovereigntyRecord;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * 主权状态变更事件
 * 当主权声明的状态发生变更时触发
 */
public class SovereigntyStatusChangedEvent extends Event {

    private static final HandlerList handlers = new HandlerList();

    private final SovereigntyRecord sovereignty;
    private final String previousStatus;
    private final String newStatus;

    public SovereigntyStatusChangedEvent(SovereigntyRecord sovereignty, String previousStatus, String newStatus) {
        this.sovereignty = sovereignty;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
    }

    public SovereigntyRecord getSovereignty() {
        return sovereignty;
    }

    public String getPreviousStatus() {
        return previousStatus;
    }

    public String getNewStatus() {
        return newStatus;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}