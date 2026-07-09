package dev.starcore.starcore.module.anniversary.event;

import dev.starcore.starcore.module.anniversary.model.NationAnniversary;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * 纪念日创建事件
 */
public class AnniversaryCreatedEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final NationAnniversary anniversary;

    public AnniversaryCreatedEvent(NationAnniversary anniversary) {
        this.anniversary = anniversary;
    }

    public NationAnniversary getAnniversary() {
        return anniversary;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}