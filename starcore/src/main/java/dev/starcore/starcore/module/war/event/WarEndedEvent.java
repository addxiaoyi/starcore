package dev.starcore.starcore.module.war.event;

import dev.starcore.starcore.war.War;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * 战争结束事件
 * 在战争正式结束时触发
 */
public class WarEndedEvent extends Event implements Cancellable {
    private static final HandlerList handlers = new HandlerList();

    private final War war;
    private final WarEndReason reason;
    private boolean cancelled;

    public WarEndedEvent(War war, WarEndReason reason) {
        this.war = war;
        this.reason = reason;
    }

    public War getWar() {
        return war;
    }

    public WarEndReason getReason() {
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

    /**
     * 战争结束原因枚举
     */
    public enum WarEndReason {
        /** 正常结束 - 一方投降 */
        SURRENDER,
        /** 协议停战 */
        PEACE_TREATY,
        /** 超时自动结束 */
        TIMEOUT,
        /** 最大持续时间 */
        MAX_DURATION,
        /** 管理员强制结束 */
        ADMIN_FORCE,
        /** 未知 */
        UNKNOWN
    }
}
