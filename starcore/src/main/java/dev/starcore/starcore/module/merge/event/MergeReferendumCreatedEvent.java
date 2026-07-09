package dev.starcore.starcore.module.merge.event;

import dev.starcore.starcore.module.merge.model.MergeReferendum;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * 合并公投创建事件
 */
public class MergeReferendumCreatedEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final MergeReferendum referendum;

    public MergeReferendumCreatedEvent(MergeReferendum referendum) {
        this.referendum = referendum;
    }

    public MergeReferendum getReferendum() {
        return referendum;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}