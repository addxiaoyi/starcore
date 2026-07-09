package dev.starcore.starcore.module.split.event;

import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.split.model.SplitRegion;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * 国家分裂事件
 * 在国家正式分裂前触发，可被取消
 */
public class NationSplitEvent extends Event implements Cancellable {
    private static final HandlerList handlers = new HandlerList();

    private final Nation sourceNation;
    private final UUID splitterId;
    private final String newNationName;
    private final SplitRegion region;
    private final double cost;
    private boolean cancelled;
    private String cancelReason;

    public NationSplitEvent(
        Nation sourceNation,
        UUID splitterId,
        String newNationName,
        SplitRegion region,
        double cost
    ) {
        this.sourceNation = sourceNation;
        this.splitterId = splitterId;
        this.newNationName = newNationName;
        this.region = region;
        this.cost = cost;
    }

    public Nation getSourceNation() {
        return sourceNation;
    }

    public UUID getSplitterId() {
        return splitterId;
    }

    public String getNewNationName() {
        return newNationName;
    }

    public SplitRegion getRegion() {
        return region;
    }

    public double getCost() {
        return cost;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    /**
     * 设置取消原因
     */
    public void setCancelReason(String reason) {
        this.cancelReason = reason;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}