package dev.starcore.starcore.module.war.event;

import dev.starcore.starcore.war.War;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * 战争宣战事件
 * 在宣战声明时触发（在准备期开始时）
 */
public class WarDeclaredEvent extends Event implements Cancellable {
    private static final HandlerList handlers = new HandlerList();

    private final War war;
    private final War.Declarer declarer;
    private boolean cancelled;

    public WarDeclaredEvent(War war, War.Declarer declarer) {
        this.war = war;
        this.declarer = declarer;
    }

    public War getWar() {
        return war;
    }

    public War.Declarer getDeclarer() {
        return declarer;
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
