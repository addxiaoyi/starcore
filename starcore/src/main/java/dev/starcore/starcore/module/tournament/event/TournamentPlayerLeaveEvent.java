package dev.starcore.starcore.module.tournament.event;

import dev.starcore.starcore.module.tournament.Tournament;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * 玩家离开锦标赛事件
 * 在玩家离开比赛时触发
 */
public class TournamentPlayerLeaveEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final Tournament tournament;
    private final Player player;
    private final UUID playerId;
    private final boolean wasEliminated;
    private final int remainingPlayers;

    public TournamentPlayerLeaveEvent(Tournament tournament, Player player,
                                      boolean wasEliminated, int remainingPlayers) {
        this.tournament = tournament;
        this.player = player;
        this.playerId = player.getUniqueId();
        this.wasEliminated = wasEliminated;
        this.remainingPlayers = remainingPlayers;
    }

    public Tournament getTournament() {
        return tournament;
    }

    public Player getPlayer() {
        return player;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public boolean wasEliminated() {
        return wasEliminated;
    }

    public int getRemainingPlayers() {
        return remainingPlayers;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
