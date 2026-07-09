package dev.starcore.starcore.module.merge.event;

import dev.starcore.starcore.module.merge.model.MergeReferendum;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * 合并公投投票事件
 * 当玩家对公投进行投票时触发
 */
public class MergeReferendumVotedEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final MergeReferendum referendum;
    private final UUID voterId;
    private final String voterName;
    private final boolean approved;

    public MergeReferendumVotedEvent(MergeReferendum referendum, UUID voterId, String voterName, boolean approved) {
        this.referendum = referendum;
        this.voterId = voterId;
        this.voterName = voterName;
        this.approved = approved;
    }

    public MergeReferendum getReferendum() {
        return referendum;
    }

    public UUID getVoterId() {
        return voterId;
    }

    public String getVoterName() {
        return voterName;
    }

    public boolean isApproved() {
        return approved;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}