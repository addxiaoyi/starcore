package dev.starcore.starcore.module.emergency.event;

import dev.starcore.starcore.module.emergency.model.EmergencyState;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * 紧急状态取消事件
 * 在国家取消紧急状态时触发
 */
public class EmergencyCancelledEvent extends Event {

    private static final HandlerList handlers = new HandlerList();

    private final EmergencyState emergency;
    private final String cancelledBy;

    public EmergencyCancelledEvent(EmergencyState emergency) {
        this.emergency = emergency;
        this.cancelledBy = emergency.cancelledBy();
    }

    public EmergencyState getEmergency() {
        return emergency;
    }

    public String getCancelledBy() {
        return cancelledBy;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}