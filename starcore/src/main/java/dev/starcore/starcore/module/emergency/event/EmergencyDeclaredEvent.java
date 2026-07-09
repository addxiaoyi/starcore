package dev.starcore.starcore.module.emergency.event;

import dev.starcore.starcore.module.emergency.model.EmergencyState;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * 紧急状态宣布事件
 * 在国家宣布紧急状态时触发
 */
public class EmergencyDeclaredEvent extends Event implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    private final EmergencyState emergency;
    private boolean cancelled;

    public EmergencyDeclaredEvent(EmergencyState emergency) {
        this.emergency = emergency;
    }

    public EmergencyState getEmergency() {
        return emergency;
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