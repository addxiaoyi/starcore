package dev.starcore.starcore.pvp.duel;

import java.util.UUID;

/**
 * 决斗
 */
public final class Duel {
    private final UUID id;
    private final UUID challenger;     // 挑战者
    private final UUID opponent;       // 对手
    private final DuelArena arena;    // 竞技场
    private final double wager;       // 赌注
    private final String kitName;     // Kit 名称
    private final int bestOf;          // BO 几

    private DuelState state;          // 状态
    private final long createdTime;   // 创建时间
    private long startTime;           // 开始时间
    private long endTime;             // 结束时间

    private UUID winner;              // 获胜者
    private DuelEndReason endReason; // 结束原因

    // 战斗统计
    private int challengerDamage = 0;
    private int opponentDamage = 0;
    private int challengerHits = 0;
    private int opponentHits = 0;

    public Duel(UUID id, UUID challenger, UUID opponent, DuelArena arena, double wager) {
        this(id, challenger, opponent, arena, wager, "default", 1);
    }

    public Duel(UUID id, UUID challenger, UUID opponent, DuelArena arena, double wager, String kitName) {
        this(id, challenger, opponent, arena, wager, kitName, 1);
    }

    public Duel(UUID id, UUID challenger, UUID opponent, DuelArena arena, double wager, String kitName, int bestOf) {
        this.id = id;
        this.challenger = challenger;
        this.opponent = opponent;
        this.arena = arena;
        this.wager = wager;
        this.kitName = kitName != null ? kitName : "default";
        this.bestOf = bestOf > 0 ? bestOf : 1;
        this.state = DuelState.WAITING;
        this.createdTime = System.currentTimeMillis();
    }

    /**
     * 开始决斗
     */
    public void start() {
        this.state = DuelState.IN_PROGRESS;
        this.startTime = System.currentTimeMillis();
    }

    /**
     * 结束决斗
     */
    public void end(UUID winner, DuelEndReason reason) {
        this.state = DuelState.FINISHED;
        this.winner = winner;
        this.endReason = reason;
        this.endTime = System.currentTimeMillis();
    }

    /**
     * 重置回合统计（用于 BO 多局制）
     */
    public void resetRoundStats() {
        this.challengerDamage = 0;
        this.opponentDamage = 0;
        this.challengerHits = 0;
        this.opponentHits = 0;
    }

    /**
     * 记录击杀
     */
    public void recordKill(UUID killer) {
        recordHit(killer);
    }

    /**
     * 记录伤害
     */
    public void recordDamage(UUID attacker, double damage) {
        if (attacker.equals(challenger)) {
            challengerDamage += (int) damage;
        } else if (attacker.equals(opponent)) {
            opponentDamage += (int) damage;
        }
    }

    /**
     * 记录命中
     */
    public void recordHit(UUID attacker) {
        if (attacker.equals(challenger)) {
            challengerHits++;
        } else if (attacker.equals(opponent)) {
            opponentHits++;
        }
    }

    /**
     * 检查是否是参与者
     */
    public boolean isParticipant(UUID playerId) {
        return challenger.equals(playerId) || opponent.equals(playerId);
    }

    /**
     * 获取对手
     */
    public UUID getOpponent(UUID playerId) {
        if (challenger.equals(playerId)) {
            return opponent;
        } else if (opponent.equals(playerId)) {
            return challenger;
        }
        return null;
    }

    /**
     * 获取决斗时长（秒）
     */
    public long getDuration() {
        if (startTime == 0) return 0;
        long end = endTime > 0 ? endTime : System.currentTimeMillis();
        return (end - startTime) / 1000;
    }

    /**
     * 获取统计信息
     */
    public DuelStats getStats() {
        return new DuelStats(
            challengerDamage,
            opponentDamage,
            challengerHits,
            opponentHits,
            getDuration()
        );
    }

    // Getters
    public UUID getId() { return id; }
    public UUID getChallenger() { return challenger; }
    public UUID getOpponent() { return opponent; }
    public DuelArena getArena() { return arena; }
    public double getWager() { return wager; }
    public String getKitName() { return kitName; }
    public int getBestOf() { return bestOf; }
    public DuelState getState() { return state; }
    public void setState(DuelState state) { this.state = state; }
    public long getCreatedTime() { return createdTime; }
    public long getStartTime() { return startTime; }
    public long getEndTime() { return endTime; }
    public UUID getWinner() { return winner; }
    public DuelEndReason getEndReason() { return endReason; }

    /**
     * 决斗状态
     */
    public enum DuelState {
        WAITING,        // 等待接受
        PREPARING,      // 准备中
        IN_PROGRESS,    // 进行中
        FINISHED,       // 已结束
        CANCELLED       // 已取消
    }

    /**
     * 决斗结束原因
     */
    public enum DuelEndReason {
        DEATH,          // 死亡
        FORFEIT,        // 认输
        DISCONNECT,     // 断线
        TIMEOUT,        // 超时
        ADMIN           // 管理员强制
    }

    /**
     * 决斗统计
     */
    public record DuelStats(
        int challengerDamage,
        int opponentDamage,
        int challengerHits,
        int opponentHits,
        long duration
    ) {}
}
