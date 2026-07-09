package dev.starcore.starcore.social.simulation.events;

import dev.starcore.starcore.social.simulation.ReputationService.ReputationDimension;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import java.util.UUID;

/**
 * 声望变化事件
 */
public class ReputationChangeEvent extends Event {

    private static final HandlerList handlers = new HandlerList();

    private final UUID playerId;
    private final ReputationDimension dimension;
    private final int amount;
    private final int newValue;
    private final String reason;

    public ReputationChangeEvent(UUID playerId, ReputationDimension dimension, int amount, int newValue, String reason) {
        this.playerId = playerId;
        this.dimension = dimension;
        this.amount = amount;
        this.newValue = newValue;
        this.reason = reason;
    }

    public UUID getPlayerId() { return playerId; }
    public ReputationDimension getDimension() { return dimension; }
    public String getDimensionName() { return dimension.name(); }
    public int getAmount() { return amount; }
    public int getNewValue() { return newValue; }
    public String getReason() { return reason; }

    @Override
    public HandlerList getHandlers() { return handlers; }
    public static HandlerList getHandlerList() { return handlers; }
}
