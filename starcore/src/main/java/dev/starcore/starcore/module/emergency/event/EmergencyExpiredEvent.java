package dev.starcore.starcore.module.emergency.event;

import dev.starcore.starcore.module.emergency.model.EmergencyState;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * 紧急状态过期事件
 * 在紧急状态自然到期时触发
 */
public class EmergencyExpiredEvent extends Event {

    private static final HandlerList handlers = new HandlerList();

    private final EmergencyState emergency;

    public EmergencyExpiredEvent(EmergencyState emergency) {
        this.emergency = emergency;
    }

    public EmergencyState getEmergency() {
        return emergency;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}