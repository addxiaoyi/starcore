package dev.starcore.starcore.module.tournament.event;

import dev.starcore.starcore.module.tournament.Tournament;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * 锦标赛开始事件
 * 在比赛正式开始时触发
 */
public class TournamentStartedEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final Tournament tournament;

    public TournamentStartedEvent(Tournament tournament) {
        this.tournament = tournament;
    }

    public Tournament getTournament() {
        return tournament;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
