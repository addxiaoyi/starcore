package dev.starcore.starcore.module.merge.event;

import dev.starcore.starcore.module.merge.model.MergeResult;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * 国家合并完成事件
 */
public class NationsMergedEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final MergeResult result;

    public NationsMergedEvent(MergeResult result) {
        this.result = result;
    }

    public MergeResult getResult() {
        return result;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}