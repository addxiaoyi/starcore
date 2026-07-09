package dev.starcore.starcore.module.tournament.event;

import dev.starcore.starcore.module.tournament.Tournament;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * 玩家加入锦标赛事件
 * 在玩家成功加入比赛时触发
 */
public class TournamentPlayerJoinEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final Tournament tournament;
    private final Player player;
    private final UUID playerId;
    private final int currentPlayerCount;
    private final int maxPlayerCount;

    public TournamentPlayerJoinEvent(Tournament tournament, Player player,
                                     int currentPlayerCount, int maxPlayerCount) {
        this.tournament = tournament;
        this.player = player;
        this.playerId = player.getUniqueId();
        this.currentPlayerCount = currentPlayerCount;
        this.maxPlayerCount = maxPlayerCount;
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

    public int getCurrentPlayerCount() {
        return currentPlayerCount;
    }

    public int getMaxPlayerCount() {
        return maxPlayerCount;
    }

    public boolean isFull() {
        return currentPlayerCount >= maxPlayerCount;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
