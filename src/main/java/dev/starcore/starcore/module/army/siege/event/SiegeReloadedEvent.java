package dev.starcore.starcore.module.army.siege.event;

import dev.starcore.starcore.module.army.siege.model.SiegeUnit;
import org.bukkit.Location;
import org.bukkit.event.HandlerList;

/**
 * 攻城器械弹药补充事件
 */
public final class SiegeReloadedEvent extends org.bukkit.event.Event {
    private static final HandlerList handlers = new HandlerList();

    private final SiegeUnit siege;
    private final int amount;

    public SiegeReloadedEvent(SiegeUnit siege, int amount) {
        this.siege = siege;
        this.amount = amount;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public SiegeUnit siege() {
        return siege;
    }

    public int amount() {
        return amount;
    }
}
