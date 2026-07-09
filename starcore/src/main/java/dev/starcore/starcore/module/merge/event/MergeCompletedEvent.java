package dev.starcore.starcore.module.merge.event;

import dev.starcore.starcore.module.merge.model.MergeReferendum;
import dev.starcore.starcore.module.nation.model.NationId;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * 国家合并完成事件
 * 当两个国家成功合并为一个新国家时触发
 */
public class MergeCompletedEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final MergeReferendum referendum;
    private final NationId resultNationId;
    private final NationId nation1Id;
    private final NationId nation2Id;

    public MergeCompletedEvent(MergeReferendum referendum, NationId resultNationId,
                               NationId nation1Id, NationId nation2Id) {
        this.referendum = referendum;
        this.resultNationId = resultNationId;
        this.nation1Id = nation1Id;
        this.nation2Id = nation2Id;
    }

    public MergeReferendum getReferendum() {
        return referendum;
    }

    public NationId getResultNationId() {
        return resultNationId;
    }

    public NationId getNation1Id() {
        return nation1Id;
    }

    public NationId getNation2Id() {
        return nation2Id;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}