package dev.starcore.starcore.module.tournament.event;

import dev.starcore.starcore.module.tournament.Tournament;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * 锦标赛创建事件
 * 在新比赛创建时触发
 */
public class TournamentCreatedEvent extends Event implements Cancellable {
    private static final HandlerList handlers = new HandlerList();

    private final Tournament tournament;
    private final Player creator;
    private boolean cancelled;

    public TournamentCreatedEvent(Tournament tournament, Player creator) {
        this.tournament = tournament;
        this.creator = creator;
    }

    public Tournament getTournament() {
        return tournament;
    }

    public Player getCreator() {
        return creator;
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
