package dev.starcore.starcore.war;
import java.util.Optional;

import dev.starcore.starcore.module.nation.model.NationId;

import java.time.Instant;
import java.util.*;

/**
 * 战争核心类
 * 代表两个或多个国家之间的战争
 */
public final class War {
    private final UUID id;
    private final String name;
    private final NationId aggressor;          // 进攻方
    private final NationId defender;            // 防守方
    private final Set<NationId> aggressorAllies = new HashSet<>();  // 进攻方盟友
    private final Set<NationId> defenderAllies = new HashSet<>();    // 防守方盟友
    private WarStatus status;
    private final WarGoal goal;
    private int aggressorWarScore;              // 进攻方战争积分
    private int defenderWarScore;               // 防守方战争积分
    private final Instant declaredAt;
    private Instant startedAt;                  // 实际开始时间（准备期结束）
    private Instant endedAt;
    private Instant lastUpdated;

    public War(
        UUID id,
        String name,
        NationId aggressor,
        NationId defender,
        WarGoal goal,
        Instant declaredAt
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.aggressor = Objects.requireNonNull(aggressor, "aggressor");
        this.defender = Objects.requireNonNull(defender, "defender");
        this.goal = Objects.requireNonNull(goal, "goal");
        this.status = WarStatus.PREPARATION;
        this.aggressorWarScore = 0;
        this.defenderWarScore = 0;
        this.declaredAt = Objects.requireNonNull(declaredAt, "declaredAt");
        this.lastUpdated = Instant.now();
    }

    /**
     * 创建新战争
     */
    public static War declare(String name, NationId aggressor, NationId defender, WarGoal goal) {
        return new War(
            UUID.randomUUID(),
            name,
            aggressor,
            defender,
            goal,
            Instant.now()
        );
    }

    /**
     * 包级完整构造函数 - 用于从存储重建 War 对象
     * 绕过 Builder 模式，直接设置所有字段（包括可变字段）
     */
    War(
        UUID id,
        String name,
        NationId aggressor,
        NationId defender,
        WarStatus status,
        WarGoal goal,
        int aggressorWarScore,
        int defenderWarScore,
        Set<NationId> aggressorAllies,
        Set<NationId> defenderAllies,
        Instant declaredAt,
        Instant startedAt,
        Instant endedAt,
        Instant lastUpdated
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.aggressor = Objects.requireNonNull(aggressor, "aggressor");
        this.defender = Objects.requireNonNull(defender, "defender");
        this.status = Objects.requireNonNull(status, "status");
        this.goal = Objects.requireNonNull(goal, "goal");
        this.aggressorWarScore = aggressorWarScore;
        this.defenderWarScore = defenderWarScore;
        this.aggressorAllies.addAll(aggressorAllies);
        this.defenderAllies.addAll(defenderAllies);
        this.declaredAt = Objects.requireNonNull(declaredAt, "declaredAt");
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.lastUpdated = Objects.requireNonNull(lastUpdated, "lastUpdated");
    }

    // ==================== Getters ====================

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public NationId aggressor() {
        return aggressor;
    }

    public NationId defender() {
        return defender;
    }

    public Set<NationId> aggressorAllies() {
        return Collections.unmodifiableSet(aggressorAllies);
    }

    public Set<NationId> defenderAllies() {
        return Collections.unmodifiableSet(defenderAllies);
    }

    public WarStatus status() {
        return status;
    }

    public WarGoal goal() {
        return goal;
    }

    public int aggressorWarScore() {
        return aggressorWarScore;
    }

    public int defenderWarScore() {
        return defenderWarScore;
    }

    public Instant declaredAt() {
        return declaredAt;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant endedAt() {
        return endedAt;
    }

    public Instant lastUpdated() {
        return lastUpdated;
    }

    // ==================== 战争管理 ====================

    /**
     * 添加盟友
     */
    public void addAlly(NationId nationId, boolean isAggressorSide) {
        if (isAggressorSide) {
            aggressorAllies.add(nationId);
        } else {
            defenderAllies.add(nationId);
        }
        this.lastUpdated = Instant.now();
    }

    /**
     * 移除盟友
     */
    public void removeAlly(NationId nationId) {
        aggressorAllies.remove(nationId);
        defenderAllies.remove(nationId);
        this.lastUpdated = Instant.now();
    }

    /**
     * 开始战争（准备期结束）
     */
    public void start() {
        if (status != WarStatus.PREPARATION) {
            throw new IllegalStateException("War is not in preparation phase");
        }
        this.status = WarStatus.ACTIVE;
        this.startedAt = Instant.now();
        this.lastUpdated = Instant.now();
    }

    /**
     * 停火
     */
    public void ceasefire() {
        if (status != WarStatus.ACTIVE) {
            throw new IllegalStateException("War is not active");
        }
        this.status = WarStatus.CEASEFIRE;
        this.lastUpdated = Instant.now();
    }

    /**
     * 恢复战争
     */
    public void resume() {
        if (status != WarStatus.CEASEFIRE) {
            throw new IllegalStateException("War is not in ceasefire");
        }
        this.status = WarStatus.ACTIVE;
        this.lastUpdated = Instant.now();
    }

    /**
     * 结束战争
     */
    public void end() {
        this.status = WarStatus.ENDED;
        this.endedAt = Instant.now();
        this.lastUpdated = Instant.now();
    }

    /**
     * 增加战争积分
     */
    public void addWarScore(NationId nationId, int points) {
        if (isAggressorSide(nationId)) {
            this.aggressorWarScore += points;
        } else if (isDefenderSide(nationId)) {
            this.defenderWarScore += points;
        }
        this.lastUpdated = Instant.now();
    }

    // ==================== 查询方法 ====================

    /**
     * 检查是否参战
     */
    public boolean isParticipant(NationId nationId) {
        return isAggressorSide(nationId) || isDefenderSide(nationId);
    }

    /**
     * 检查是否在进攻方
     */
    public boolean isAggressorSide(NationId nationId) {
        return aggressor.equals(nationId) || aggressorAllies.contains(nationId);
    }

    /**
     * 检查是否在防守方
     */
    public boolean isDefenderSide(NationId nationId) {
        return defender.equals(nationId) || defenderAllies.contains(nationId);
    }

    /**
     * 检查两国是否在同一阵营
     */
    public boolean areSameSide(NationId nation1, NationId nation2) {
        return (isAggressorSide(nation1) && isAggressorSide(nation2)) ||
               (isDefenderSide(nation1) && isDefenderSide(nation2));
    }

    /**
     * 检查战争是否活跃
     */
    public boolean isActive() {
        return status == WarStatus.ACTIVE || status == WarStatus.PREPARATION;
    }

    /**
     * 检查战争是否已结束
     */
    public boolean isEnded() {
        return status == WarStatus.ENDED;
    }

    /**
     * 获取战争持续时间（秒）
     */
    public long durationSeconds() {
        if (startedAt == null) {
            return 0;
        }
        Instant end = endedAt != null ? endedAt : Instant.now();
        return end.getEpochSecond() - startedAt.getEpochSecond();
    }

    /**
     * 获取战争胜利方
     */
    public Optional<WarWinner> determineWinner() {
        if (!isEnded()) {
            return Optional.empty();
        }

        if (aggressorWarScore > defenderWarScore) {
            return Optional.of(WarWinner.AGGRESSOR);
        } else if (defenderWarScore > aggressorWarScore) {
            return Optional.of(WarWinner.DEFENDER);
        } else {
            return Optional.of(WarWinner.DRAW);
        }
    }

    /**
     * 获取所有参战国家
     */
    public Set<NationId> allParticipants() {
        Set<NationId> participants = new HashSet<>();
        participants.add(aggressor);
        participants.add(defender);
        participants.addAll(aggressorAllies);
        participants.addAll(defenderAllies);
        return participants;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof War other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return String.format("War{id=%s, name='%s', status=%s, aggressor=%s, defender=%s, score=%d:%d}",
            id, name, status, aggressor, defender, aggressorWarScore, defenderWarScore);
    }

    /**
     * 战争胜利方枚举
     */
    public enum WarWinner {
        AGGRESSOR,
        DEFENDER,
        DRAW
    }

    /**
     * 宣战者类型枚举
     */
    public enum Declarer {
        NATION,
        ALLIANCE,
        COALITION,
        SERVER
    }
}
