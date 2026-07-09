package dev.starcore.starcore.module.tournament.event;

import dev.starcore.starcore.module.tournament.Tournament;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * 锦标赛击杀事件
 * 在比赛中发生击杀时触发
 */
public class TournamentKillEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final Tournament tournament;
    private final Player killer;
    private final Player victim;
    private final UUID killerId;
    private final UUID victimId;
    private final int killerTotalKills;
    private final int remainingPlayers;

    public TournamentKillEvent(Tournament tournament, Player killer, Player victim,
                             int killerTotalKills, int remainingPlayers) {
        this.tournament = tournament;
        this.killer = killer;
        this.victim = victim;
        this.killerId = killer != null ? killer.getUniqueId() : null;
        this.victimId = victim != null ? victim.getUniqueId() : null;
        this.killerTotalKills = killerTotalKills;
        this.remainingPlayers = remainingPlayers;
    }

    public Tournament getTournament() {
        return tournament;
    }

    public Player getKiller() {
        return killer;
    }

    public Player getVictim() {
        return victim;
    }

    public UUID getKillerId() {
        return killerId;
    }

    public UUID getVictimId() {
        return victimId;
    }

    public int getKillerTotalKills() {
        return killerTotalKills;
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
