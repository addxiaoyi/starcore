package dev.starcore.starcore.module.merge.event;

import dev.starcore.starcore.module.merge.model.MergeReferendum;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * 合并公投发起事件
 * 当两个国家之间发起合并公投时触发
 */
public class MergeReferendumProposedEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final MergeReferendum referendum;

    public MergeReferendumProposedEvent(MergeReferendum referendum) {
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