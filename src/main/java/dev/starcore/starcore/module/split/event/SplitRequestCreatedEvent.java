package dev.starcore.starcore.module.split.event;

import dev.starcore.starcore.module.nation.model.Nation;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * 国家分裂请求事件
 * 在分裂请求创建时触发
 */
public class SplitRequestCreatedEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final UUID requestId;
    private final UUID requesterId;
    private final String requesterName;
    private final Nation sourceNation;
    private final String newNationName;
    private final int chunkCount;

    public SplitRequestCreatedEvent(
        UUID requestId,
        UUID requesterId,
        String requesterName,
        Nation sourceNation,
        String newNationName,
        int chunkCount
    ) {
        this.requestId = requestId;
        this.requesterId = requesterId;
        this.requesterName = requesterName;
        this.sourceNation = sourceNation;
        this.newNationName = newNationName;
        this.chunkCount = chunkCount;
    }

    public String getRequesterName() {
        return requesterName;
    }

    public UUID getRequestId() {
        return requestId;
    }

    public UUID getRequesterId() {
        return requesterId;
    }

    public Nation getSourceNation() {
        return sourceNation;
    }

    public String getNewNationName() {
        return newNationName;
    }

    public int getChunkCount() {
        return chunkCount;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}