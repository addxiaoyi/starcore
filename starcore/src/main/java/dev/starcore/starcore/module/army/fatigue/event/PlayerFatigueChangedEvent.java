package dev.starcore.starcore.module.army.fatigue.event;

import dev.starcore.starcore.module.army.fatigue.model.FatigueType;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * 玩家疲劳值变化事件
 */
public class PlayerFatigueChangedEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final UUID playerId;
    private final String playerName;
    private final FatigueType fatigueType;
    private final int previousValue;
    private final int newValue;
    private final int changeAmount;

    public PlayerFatigueChangedEvent(UUID playerId, String playerName,
                                    FatigueType fatigueType, int previousValue,
                                    int newValue, int changeAmount) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.fatigueType = fatigueType;
        this.previousValue = previousValue;
        this.newValue = newValue;
        this.changeAmount = changeAmount;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public String getPlayerName() {
        return playerName;
    }

    public FatigueType getFatigueType() {
        return fatigueType;
    }

    public int getPreviousValue() {
        return previousValue;
    }

    public int getNewValue() {
        return newValue;
    }

    public int getChangeAmount() {
        return changeAmount;
    }

    public boolean isIncrease() {
        return changeAmount > 0;
    }

    public boolean isDecrease() {
        return changeAmount < 0;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}