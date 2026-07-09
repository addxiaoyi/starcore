package dev.starcore.starcore.module.army.fatigue.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * 玩家进入强制休息状态事件
 */
public class PlayerForcedRestEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final UUID playerId;
    private final String playerName;
    private final int durationSeconds;
    private final int currentFatigue;

    public PlayerForcedRestEvent(UUID playerId, String playerName,
                                int durationSeconds, int currentFatigue) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.durationSeconds = durationSeconds;
        this.currentFatigue = currentFatigue;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public String getPlayerName() {
        return playerName;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public int getCurrentFatigue() {
        return currentFatigue;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}