package dev.starcore.starcore.war;

import dev.starcore.starcore.module.nation.model.NationId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 间谍
 * 执行情报收集和破坏任务的特工
 */
public final class Spy {
    private final UUID id;
    private final NationId ownerNation;     // 所属国家
    private final NationId targetNation;    // 目标国家
    private final String codeName;          // 代号
    private SpyStatus status;
    private int experience;                 // 经验值
    private int skill;                      // 技能等级 (1-10)
    private SpyMission currentMission;
    private final Instant recruitedAt;
    private Instant lastMissionAt;
    private boolean captured;               // 是否被抓获

    public Spy(
        UUID id,
        NationId ownerNation,
        NationId targetNation,
        String codeName,
        int skill,
        Instant recruitedAt
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.ownerNation = Objects.requireNonNull(ownerNation, "ownerNation");
        this.targetNation = Objects.requireNonNull(targetNation, "targetNation");
        this.codeName = Objects.requireNonNull(codeName, "codeName");
        this.status = SpyStatus.IDLE;
        this.experience = 0;
        this.skill = Math.max(1, Math.min(10, skill));
        this.recruitedAt = Objects.requireNonNull(recruitedAt, "recruitedAt");
        this.captured = false;
    }

    public UUID id() {
        return id;
    }

    public NationId ownerNation() {
        return ownerNation;
    }

    public NationId targetNation() {
        return targetNation;
    }

    public String codeName() {
        return codeName;
    }

    public SpyStatus status() {
        return status;
    }

    public int experience() {
        return experience;
    }

    public int skill() {
        return skill;
    }

    public SpyMission currentMission() {
        return currentMission;
    }

    public Instant recruitedAt() {
        return recruitedAt;
    }

    public Instant lastMissionAt() {
        return lastMissionAt;
    }

    public boolean isCaptured() {
        return captured;
    }

    /**
     * 分配任务
     */
    public void assignMission(SpyMission mission) {
        if (captured) {
            throw new IllegalStateException("Spy is captured");
        }
        if (status != SpyStatus.IDLE) {
            throw new IllegalStateException("Spy is not idle");
        }

        this.currentMission = Objects.requireNonNull(mission, "mission");
        this.status = SpyStatus.ON_MISSION;
        this.lastMissionAt = Instant.now();
    }

    /**
     * 完成任务
     */
    public void completeMission(boolean success) {
        if (currentMission == null) {
            throw new IllegalStateException("No active mission");
        }

        if (success) {
            int expGain = currentMission.difficulty() * 10;
            this.experience += expGain;

            // 升级
            if (experience >= skill * 100 && skill < 10) {
                this.skill++;
            }
        }

        this.currentMission = null;
        this.status = SpyStatus.IDLE;
    }

    /**
     * 被抓获
     */
    public void capture() {
        this.captured = true;
        this.status = SpyStatus.CAPTURED;
        this.currentMission = null;
    }

    /**
     * 被处决
     */
    public void execute() {
        this.status = SpyStatus.EXECUTED;
    }

    /**
     * 获释放/交换
     */
    public void release() {
        this.captured = false;
        this.status = SpyStatus.IDLE;
    }

    /**
     * 计算任务成功率
     */
    public double calculateSuccessRate(SpyMission mission) {
        if (captured) {
            return 0.0;
        }

        // 基础成功率 = 技能 / (技能 + 难度)
        double baseRate = (double) skill / (skill + mission.difficulty());

        // 经验加成
        double expBonus = Math.min(0.2, experience / 1000.0);

        return Math.min(0.95, baseRate + expBonus);
    }

    /**
     * 是否可用
     */
    public boolean isAvailable() {
        return !captured && status == SpyStatus.IDLE;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Spy other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return String.format("Spy{codeName='%s', skill=%d, status=%s, captured=%s}",
            codeName, skill, status, captured);
    }

    /**
     * 间谍状态
     */
    public enum SpyStatus {
        IDLE("待命"),
        ON_MISSION("执行任务"),
        CAPTURED("被抓获"),
        EXECUTED("已处决");

        private final String displayName;

        SpyStatus(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }
    }
}
