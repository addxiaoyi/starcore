package dev.starcore.starcore.module.faith.event;

import dev.starcore.starcore.module.nation.model.NationId;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * 信仰祈福事件
 * 当国家使用信仰值进行祈福时触发
 */
public class FaithBlessingEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final NationId nationId;
    private final String blessingType;
    private final int faithCost;
    private final boolean success;

    public FaithBlessingEvent(
        NationId nationId,
        String blessingType,
        int faithCost,
        boolean success
    ) {
        this.nationId = nationId;
        this.blessingType = blessingType;
        this.faithCost = faithCost;
        this.success = success;
    }

    public NationId getNationId() {
        return nationId;
    }

    public String getBlessingType() {
        return blessingType;
    }

    public int getFaithCost() {
        return faithCost;
    }

    public boolean isSuccess() {
        return success;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}