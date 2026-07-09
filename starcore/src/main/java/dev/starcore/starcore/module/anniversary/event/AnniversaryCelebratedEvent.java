package dev.starcore.starcore.module.anniversary.event;

import dev.starcore.starcore.module.anniversary.model.NationAnniversary;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * 纪念日庆祝事件
 * 在纪念日当天触发
 */
public class AnniversaryCelebratedEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final NationAnniversary anniversary;

    public AnniversaryCelebratedEvent(NationAnniversary anniversary) {
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