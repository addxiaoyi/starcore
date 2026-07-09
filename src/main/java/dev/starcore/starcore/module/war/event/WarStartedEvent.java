package dev.starcore.starcore.module.war.event;

import dev.starcore.starcore.war.War;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * 战争开始事件
 * 在宣战正式生效（准备期结束）时触发
 */
public class WarStartedEvent extends Event implements Cancellable {
    private static final HandlerList handlers = new HandlerList();

    private final War war;
    private boolean cancelled;

    public WarStartedEvent(War war) {
        this.war = war;
    }

    public War getWar() {
        return war;
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
