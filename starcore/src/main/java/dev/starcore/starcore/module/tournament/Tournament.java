package dev.starcore.starcore.module.tournament;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 锦标赛数据模型
 */
public class Tournament {
    private final String id;
    private final String name;
    private final TournamentType type;
    private final UUID creatorId;
    private final TournamentConfig config;
    private final Instant createdAt;

    private TournamentStatus status;
    private Instant startTime;
    private UUID winner;
    private int currentRound;
    private int totalRounds;

    // 参与者
    private final Set<UUID> participants = new HashSet<>();
    private final Set<UUID> aliveParticipants = new HashSet<>();

    // 击杀记录
    private final Map<UUID, Integer> kills = new ConcurrentHashMap<>();

    // 完成时间记录（竞速赛用）
    private final Map<UUID, Long> completions = new ConcurrentHashMap<>();

    // 对阵表
    private List<Match> matches = new ArrayList<>();

    // 团队
    private final Map<String, Set<UUID>> teams = new ConcurrentHashMap<>();

    public Tournament(String id, String name, TournamentType type, UUID creatorId,
                     TournamentConfig config, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.creatorId = creatorId;
        this.config = config;
        this.createdAt = createdAt;
        this.status = TournamentStatus.WAITING;
    }

    // Getters
    public String getId() { return id; }
    public String getName() { return name; }
    public TournamentType getType() { return type; }
    public UUID getCreatorId() { return creatorId; }
    public TournamentConfig getConfig() { return config; }
    public Instant getCreatedAt() { return createdAt; }
    public TournamentStatus getStatus() { return status; }
    public Instant getStartTime() { return startTime; }
    public UUID getWinner() { return winner; }
    public int getCurrentRound() { return currentRound; }
    public int getTotalRounds() { return totalRounds; }
    public Set<UUID> getParticipants() { return participants; }
    public Set<UUID> getAliveParticipants() { return aliveParticipants; }
    public Map<UUID, Integer> getKills() { return kills; }
    public Map<UUID, Long> getCompletions() { return completions; }
    public List<Match> getMatches() { return matches; }
    public Map<String, Set<UUID>> getTeams() { return teams; }

    // Setters
    public void setStatus(TournamentStatus status) { this.status = status; }
    public void setStartTime(Instant startTime) { this.startTime = startTime; }
    public void setWinner(UUID winner) { this.winner = winner; }
    public void setCurrentRound(int round) { this.currentRound = round; }
    public void setTotalRounds(int rounds) { this.totalRounds = rounds; }
    public void setMatches(List<Match> matches) { this.matches = matches; }

    // 参与者管理
    public void addParticipant(UUID playerId) {
        participants.add(playerId);
        aliveParticipants.add(playerId);
        kills.put(playerId, 0);
    }

    public void removeParticipant(UUID playerId) {
        participants.remove(playerId);
        aliveParticipants.remove(playerId);
        kills.remove(playerId);
        completions.remove(playerId);
    }

    // 击杀记录
    public void recordKill(UUID killer, UUID victim) {
        if (killer != null) {
            kills.merge(killer, 1, Integer::sum);
        }
        aliveParticipants.remove(victim);
    }

    // 完成记录
    public void recordCompletion(UUID player, long timeMillis) {
        completions.compute(player, (k, existing) ->
            (existing == null || existing > timeMillis) ? timeMillis : existing);
    }
}
