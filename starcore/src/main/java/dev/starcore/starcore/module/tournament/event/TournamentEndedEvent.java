package dev.starcore.starcore.module.tournament.event;

import dev.starcore.starcore.module.tournament.Tournament;
import dev.starcore.starcore.module.tournament.TournamentStatus;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * 锦标赛结束事件
 * 在比赛结束时触发（正常结束或取消）
 */
public class TournamentEndedEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final Tournament tournament;
    private final TournamentStatus finalStatus;
    private final UUID winnerId;
    private final String reason;

    public TournamentEndedEvent(Tournament tournament, TournamentStatus finalStatus, UUID winnerId, String reason) {
        this.tournament = tournament;
        this.finalStatus = finalStatus;
        this.winnerId = winnerId;
        this.reason = reason;
    }

    public Tournament getTournament() {
        return tournament;
    }

    public TournamentStatus getFinalStatus() {
        return finalStatus;
    }

    public UUID getWinnerId() {
        return winnerId;
    }

    public String getReason() {
        return reason;
    }

    public Player getWinner() {
        if (winnerId == null) {
            return null;
        }
        return tournament.getParticipants().stream()
            .filter(p -> p.equals(winnerId))
            .findFirst()
            .map(id -> org.bukkit.Bukkit.getPlayer(id))
            .orElse(null);
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
