package dev.starcore.starcore.module.anniversary.event;

import dev.starcore.starcore.module.anniversary.model.NationAnniversary;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * 纪念日即将到来事件
 * 在纪念日前几天触发（通常为7天和1天前）
 */
public class AnniversaryApproachingEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final NationAnniversary anniversary;
    private final int daysUntil;

    public AnniversaryApproachingEvent(NationAnniversary anniversary, int daysUntil) {
        this.anniversary = anniversary;
        this.daysUntil = daysUntil;
    }

    public NationAnniversary getAnniversary() {
        return anniversary;
    }

    public int getDaysUntil() {
        return daysUntil;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}