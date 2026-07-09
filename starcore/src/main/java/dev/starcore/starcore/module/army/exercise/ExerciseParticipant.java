package dev.starcore.starcore.module.army.exercise;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 演习参与者数据模型
 * 代表一个参与演习的国家及其军队信息
 */
public final class ExerciseParticipant {
    private final UUID nationId;
    private final String nationName;
    private int soldierCount;
    private int casualties;
    private int kills;
    private double morale;
    private ExerciseRole role;
    private Instant joinedAt;
    private boolean isActive;

    public ExerciseParticipant(
        UUID nationId,
        String nationName,
        int soldierCount,
        ExerciseRole role
    ) {
        this.nationId = Objects.requireNonNull(nationId, "nationId");
        this.nationName = Objects.requireNonNull(nationName, "nationName");
        this.soldierCount = soldierCount;
        this.role = Objects.requireNonNull(role, "role");
        this.casualties = 0;
        this.kills = 0;
        this.morale = 100.0;
        this.joinedAt = Instant.now();
        this.isActive = true;
    }

    /**
     * 创建参与者
     */
    public static ExerciseParticipant create(UUID nationId, String nationName, int soldierCount, ExerciseRole role) {
        return new ExerciseParticipant(nationId, nationName, soldierCount, role);
    }

    // ==================== Getters ====================

    public UUID nationId() {
        return nationId;
    }

    public String nationName() {
        return nationName;
    }

    public int soldierCount() {
        return soldierCount;
    }

    public int casualties() {
        return casualties;
    }

    public int kills() {
        return kills;
    }

    public double morale() {
        return morale;
    }

    public ExerciseRole role() {
        return role;
    }

    public Instant joinedAt() {
        return joinedAt;
    }

    public boolean isActive() {
        return isActive;
    }

    /**
     * 获取有效士兵数（减去伤亡）
     */
    public int effectiveSoldiers() {
        return Math.max(0, soldierCount - casualties);
    }

    /**
     * 获取伤亡率
     */
    public double casualtyRate() {
        if (soldierCount == 0) return 0;
        return (double) casualties / soldierCount;
    }

    /**
     * 获取战斗力评分
     */
    public double combatPower() {
        return effectiveSoldiers() * (morale / 100.0);
    }

    // ==================== Setters/Modifiers ====================

    public void setSoldierCount(int soldierCount) {
        this.soldierCount = Math.max(0, soldierCount);
    }

    public void setRole(ExerciseRole role) {
        this.role = Objects.requireNonNull(role, "role");
    }

    public void setMorale(double morale) {
        this.morale = Math.max(0, Math.min(100, morale));
    }

    public void changeMorale(double delta) {
        this.morale = Math.max(0, Math.min(100, morale + delta));
    }

    public void setActive(boolean active) {
        this.isActive = active;
    }

    /**
     * 添加伤亡
     */
    public void addCasualties(int count) {
        this.casualties = Math.min(soldierCount, casualties + count);
    }

    /**
     * 添加击杀
     */
    public void addKills(int count) {
        this.kills += count;
    }

    /**
     * 应用伤亡率
     */
    public void applyCasualties(double rate) {
        int newCasualties = (int) (soldierCount * rate);
        this.casualties = Math.min(soldierCount, newCasualties);
    }

    /**
     * 恢复士兵（模拟治疗/增援）
     */
    public void healSoldiers(int count) {
        this.casualties = Math.max(0, casualties - count);
    }

    /**
     * 补充士兵（模拟增援）
     */
    public void reinforce(int count) {
        this.soldierCount += count;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ExerciseParticipant that)) return false;
        return nationId.equals(that.nationId);
    }

    @Override
    public int hashCode() {
        return nationId.hashCode();
    }

    @Override
    public String toString() {
        return String.format("Participant{nation=%s, soldiers=%d/%d, morale=%.1f%%, role=%s}",
            nationName, effectiveSoldiers(), soldierCount, morale, role);
    }
}