package dev.starcore.starcore.module.tournament.model;
import java.util.Optional;

import java.util.*;

/**
 * 淘汰赛对阵表
 */
public class Bracket {
    private final String tournamentId;
    private final int totalRounds;
    private final List<List<BracketMatch>> rounds;

    public Bracket(String tournamentId, int totalRounds) {
        this.tournamentId = tournamentId;
        this.totalRounds = totalRounds;
        this.rounds = new ArrayList<>();

        // 初始化每轮的对阵
        for (int r = 1; r <= totalRounds; r++) {
            int matchesInRound = (int) Math.pow(2, totalRounds - r);
            List<BracketMatch> roundMatches = new ArrayList<>();
            for (int m = 0; m < matchesInRound; m++) {
                String matchId = tournamentId + "_R" + r + "_M" + m;
                roundMatches.add(new BracketMatch(matchId, r, m, null, null, null, null, null, false, false));
            }
            rounds.add(roundMatches);
        }
    }

    /**
     * 设置第一轮的对阵
     */
    public void setFirstRound(List<UUID> participants) {
        List<BracketMatch> firstRound = rounds.get(0);
        for (int i = 0; i < firstRound.size() * 2 && i < participants.size(); i += 2) {
            UUID p1 = participants.get(i);
            UUID p2 = i + 1 < participants.size() ? participants.get(i + 1) : null;

            BracketMatch updated = new BracketMatch(
                firstRound.get(i / 2).id(),
                1,
                i / 2,
                p1,
                p2,
                null,
                null,
                null,
                p2 == null,
                p2 == null
            );
            firstRound.set(i / 2, updated);
        }
    }

    /**
     * 晋级选手到下一轮
     */
    public void advanceWinner(int round, int matchNumber, UUID winnerId) {
        if (round >= totalRounds) {
            // 冠军已产生
            return;
        }

        // 更新当前比赛
        List<BracketMatch> currentRound = rounds.get(round - 1);
        BracketMatch currentMatch = currentRound.get(matchNumber);
        BracketMatch updatedMatch = new BracketMatch(
            currentMatch.id(),
            round,
            matchNumber,
            currentMatch.player1Id(),
            currentMatch.player2Id(),
            winnerId,
            currentMatch.startTime(),
            currentMatch.endTime(),
            currentMatch.isBye(),
            true
        );
        currentRound.set(matchNumber, updatedMatch);

        // 设置下一轮的对阵
        List<BracketMatch> nextRound = rounds.get(round);
        int nextMatchNumber = matchNumber / 2;
        BracketMatch nextMatch = nextRound.get(nextMatchNumber);

        BracketMatch updatedNextMatch;
        if (matchNumber % 2 == 0) {
            // 偶数位，设置为 player1
            updatedNextMatch = new BracketMatch(
                nextMatch.id(),
                round + 1,
                nextMatchNumber,
                winnerId,
                nextMatch.player2Id(),
                null,
                null,
                null,
                false,
                false
            );
        } else {
            // 奇数位，设置为 player2
            updatedNextMatch = new BracketMatch(
                nextMatch.id(),
                round + 1,
                nextMatchNumber,
                nextMatch.player1Id(),
                winnerId,
                null,
                null,
                null,
                false,
                false
            );
        }
        nextRound.set(nextMatchNumber, updatedNextMatch);
    }

    /**
     * 获取指定轮的对阵
     */
    public List<BracketMatch> getRound(int round) {
        if (round < 1 || round > totalRounds) {
            return List.of();
        }
        return rounds.get(round - 1);
    }

    /**
     * 获取所有轮
     */
    public List<List<BracketMatch>> getAllRounds() {
        return List.copyOf(rounds);
    }

    /**
     * 获取总轮数
     */
    public int getTotalRounds() {
        return totalRounds;
    }

    /**
     * 获取冠军
     */
    public Optional<UUID> getChampion() {
        if (rounds.isEmpty()) {
            return Optional.empty();
        }
        List<BracketMatch> finalRound = rounds.get(rounds.size() - 1);
        if (finalRound.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(finalRound.get(0).winnerId());
    }
}
