package dev.starcore.starcore.module.tournament;

import java.util.UUID;

/**
 * 单场比赛
 */
public class Match {
    private final String id;
    private final String tournamentId;
    private final UUID player1;
    private final UUID player2;
    private final int round;
    private UUID winner;
    private boolean completed;

    public Match(String id, String tournamentId, UUID player1, UUID player2, int round, int priority) {
        this.id = id;
        this.tournamentId = tournamentId;
        this.player1 = player1;
        this.player2 = player2;
        this.round = round;
        this.completed = false;
    }

    public String getId() { return id; }
    public String getTournamentId() { return tournamentId; }
    public UUID getPlayer1() { return player1; }
    public UUID getPlayer2() { return player2; }
    public int getRound() { return round; }
    public UUID getWinner() { return winner; }
    public boolean isCompleted() { return completed; }

    public void setWinner(UUID winner) { this.winner = winner; }
    public void setCompleted(boolean completed) { this.completed = completed; }

    public boolean involves(UUID playerId) {
        return playerId.equals(player1) || playerId.equals(player2);
    }
}
