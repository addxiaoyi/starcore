package dev.starcore.starcore.module.sovereignty.event;

import dev.starcore.starcore.module.sovereignty.model.SovereigntyRecord;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * 主权撤销事件
 * 当国家撤销对某区域的主权声明时触发
 */
public class SovereigntyRevokedEvent extends Event {

    private static final HandlerList handlers = new HandlerList();

    private final SovereigntyRecord sovereignty;

    public SovereigntyRevokedEvent(SovereigntyRecord sovereignty) {
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