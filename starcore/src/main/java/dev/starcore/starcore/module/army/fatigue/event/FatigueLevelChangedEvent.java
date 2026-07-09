package dev.starcore.starcore.module.army.fatigue.event;

import dev.starcore.starcore.module.army.fatigue.model.FatigueLevel;
import dev.starcore.starcore.module.army.fatigue.model.PlayerFatigue;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * 玩家疲劳等级变化事件
 */
public class FatigueLevelChangedEvent extends Event implements Cancellable {
    private static final HandlerList handlers = new HandlerList();

    private final UUID playerId;
    private final String playerName;
    private final FatigueLevel previousLevel;
    private final FatigueLevel newLevel;
    private final PlayerFatigue fatigue;
    private boolean cancelled;

    public FatigueLevelChangedEvent(UUID playerId, String playerName,
                                   FatigueLevel previousLevel, FatigueLevel newLevel,
                                   PlayerFatigue fatigue) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.previousLevel = previousLevel;
        this.newLevel = newLevel;
        this.fatigue = fatigue;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public String getPlayerName() {
        return playerName;
    }

    public FatigueLevel getPreviousLevel() {
        return previousLevel;
    }

    public FatigueLevel getNewLevel() {
        return newLevel;
    }

    public PlayerFatigue getFatigue() {
        return fatigue;
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