package dev.starcore.starcore.module.merge.event;

import dev.starcore.starcore.module.merge.model.MergeReferendum;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * 合并公投拒绝事件
 * 当公投被否决或过期时触发
 */
public class MergeReferendumRejectedEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final MergeReferendum referendum;

    public MergeReferendumRejectedEvent(MergeReferendum referendum) {
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