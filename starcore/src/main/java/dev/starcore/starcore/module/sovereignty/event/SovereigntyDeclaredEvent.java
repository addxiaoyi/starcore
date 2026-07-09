package dev.starcore.starcore.module.sovereignty.event;

import dev.starcore.starcore.module.sovereignty.model.SovereigntyRecord;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * 主权声明事件
 * 当国家正式声明对某区域的主权时触发
 */
public class SovereigntyDeclaredEvent extends Event {

    private static final HandlerList handlers = new HandlerList();

    private final SovereigntyRecord sovereignty;

    public SovereigntyDeclaredEvent(SovereigntyRecord sovereignty) {
        this.sovereignty = sovereignty;
    }

    public SovereigntyRecord getSovereignty() {
        return sovereignty;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}