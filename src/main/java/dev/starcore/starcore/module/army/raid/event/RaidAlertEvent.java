package dev.starcore.starcore.module.army.raid.event;

import dev.starcore.starcore.module.army.raid.model.RaidAlert;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * 突袭警报事件
 * 当检测到突袭时触发
 */
public class RaidAlertEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final RaidAlert alert;
    private final String message;

    public RaidAlertEvent(RaidAlert alert, String message) {
        this.alert = alert;
        this.message = message;
    }

    public RaidAlert getAlert() {
        return alert;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}