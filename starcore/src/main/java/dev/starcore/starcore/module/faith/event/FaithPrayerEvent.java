package dev.starcore.starcore.module.faith.event;

import dev.starcore.starcore.module.faith.model.FaithData;
import dev.starcore.starcore.module.nation.model.NationId;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * 祈祷事件
 * 当玩家在国家领土内祈祷时触发
 */
public class FaithPrayerEvent extends Event implements Cancellable {
    private static final HandlerList handlers = new HandlerList();

    private final UUID playerId;
    private final NationId nationId;
    private final int x;
    private final int y;
    private final int z;
    private final String world;
    private final FaithData faithData;
    private int faithGained;
    private boolean cancelled;

    public FaithPrayerEvent(
        UUID playerId,
        NationId nationId,
        int x, int y, int z,
        String world,
        FaithData faithData,
        int faithGained
    ) {
        this.playerId = playerId;
        this.nationId = nationId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.world = world;
        this.faithData = faithData;
        this.faithGained = faithGained;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public NationId getNationId() {
        return nationId;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public String getWorld() {
        return world;
    }

    public FaithData getFaithData() {
        return faithData;
    }

    public int getFaithGained() {
        return faithGained;
    }

    public void setFaithGained(int faithGained) {
        this.faithGained = faithGained;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}