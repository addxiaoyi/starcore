package dev.starcore.starcore.module.merge.event;

import dev.starcore.starcore.module.merge.model.MergeReferendum;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * 合并公投通过事件
 * 当公投获得足够票数通过时触发
 */
public class MergeReferendumApprovedEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final MergeReferendum referendum;

    public MergeReferendumApprovedEvent(MergeReferendum referendum) {
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