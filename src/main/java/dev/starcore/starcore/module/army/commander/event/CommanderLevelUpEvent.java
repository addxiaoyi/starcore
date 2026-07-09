package dev.starcore.starcore.module.army.commander.event;

import dev.starcore.starcore.module.army.commander.model.CommanderLevel;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * 指挥官等级提升事件
 */
public class CommanderLevelUpEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final UUID playerId;
    private final Player player;
    private final CommanderLevel oldLevel;
    private final CommanderLevel newLevel;
    private final int experienceGained;

    public CommanderLevelUpEvent(
        UUID playerId,
        Player player,
        CommanderLevel oldLevel,
        CommanderLevel newLevel,
        int experienceGained
    ) {
        this.playerId = playerId;
        this.player = player;
        this.oldLevel = oldLevel;
        this.newLevel = newLevel;
        this.experienceGained = experienceGained;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public UUID playerId() {
        return playerId;
    }

    public Player player() {
        return player;
    }

    public CommanderLevel oldLevel() {
        return oldLevel;
    }

    public CommanderLevel newLevel() {
        return newLevel;
    }

    public int experienceGained() {
        return experienceGained;
    }

    public boolean isHighestRank() {
        return newLevel == CommanderLevel.MARSHAL;
    }
}
