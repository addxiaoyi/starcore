package dev.starcore.starcore.module.split.event;

import dev.starcore.starcore.module.nation.model.Nation;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * 国家分裂请求审批事件
 */
public class SplitRequestApprovedEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final UUID requestId;
    private final UUID approverId;
    private final Nation sourceNation;
    private final Nation newNation;
    private final int transferredChunks;

    public SplitRequestApprovedEvent(
        UUID requestId,
        UUID approverId,
        Nation sourceNation,
        Nation newNation,
        int transferredChunks
    ) {
        this.requestId = requestId;
        this.approverId = approverId;
        this.sourceNation = sourceNation;
        this.newNation = newNation;
        this.transferredChunks = transferredChunks;
    }

    public UUID getRequestId() {
        return requestId;
    }

    public UUID getApproverId() {
        return approverId;
    }

    public Nation getSourceNation() {
        return sourceNation;
    }

    public Nation getNewNation() {
        return newNation;
    }

    public int getTransferredChunks() {
        return transferredChunks;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}