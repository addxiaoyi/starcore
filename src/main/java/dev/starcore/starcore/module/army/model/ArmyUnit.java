package dev.starcore.starcore.module.army.model;

import org.bukkit.Location;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 军队单位
 * 代表一支由多个士兵组成的军队
 */
public final class ArmyUnit {
    private final UUID id;
    private final UUID nationId;
    private final ArmyType type;
    private int soldiers;           // 士兵数量
    private double health;          // 生命值百分比 (0-100)
    private double morale;          // 士气百分比 (0-100)
    private Location location;      // 当前位置
    private ArmyState state;        // 当前状态
    private int supply;             // 补给 (0-100)
    private final Instant createdAt;
    private Instant lastUpdated;

    public ArmyUnit(
        UUID id,
        UUID nationId,
        ArmyType type,
        int soldiers,
        double health,
        double morale,
        Location location,
        ArmyState state,
        int supply,
        Instant createdAt
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.nationId = Objects.requireNonNull(nationId, "nationId");
        this.type = Objects.requireNonNull(type, "type");
        this.soldiers = soldiers;
        this.health = clamp(health, 0, 100);
        this.morale = clamp(morale, 0, 100);
        this.location = Objects.requireNonNull(location, "location");
        this.state = Objects.requireNonNull(state, "state");
        this.supply = clamp(supply, 0, 100);
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.lastUpdated = Instant.now();
    }

    /**
     * 创建新军队
     */
    public static ArmyUnit create(UUID nationId, ArmyType type, int soldiers, Location location) {
        return new ArmyUnit(
            UUID.randomUUID(),
            nationId,
            type,
            soldiers,
            100.0,  // 满血
            100.0,  // 满士气
            location,
            ArmyState.STATIONARY,
            100,    // 满补给
            Instant.now()
        );
    }

    // ==================== Getters ====================

    public UUID id() {
        return id;
    }

    public UUID nationId() {
        return nationId;
    }

    public ArmyType type() {
        return type;
    }

    public int soldiers() {
        return soldiers;
    }

    public double health() {
        return health;
    }

    /**
     * 最大生命值（基于士兵数量）
     */
    public double maxHealth() {
        return soldiers;
    }

    public double morale() {
        return morale;
    }

    public Location location() {
        return location;
    }

    public ArmyState state() {
        return state;
    }

    public int supply() {
        return supply;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant lastUpdated() {
        return lastUpdated;
    }

    // ==================== 状态修改 ====================

    /**
     * 切换状态
     */
    public void setState(ArmyState newState) {
        this.state = Objects.requireNonNull(newState, "newState");
        this.lastUpdated = Instant.now();
    }

    /**
     * 移动到新位置
     */
    public void moveTo(Location newLocation) {
        this.location = Objects.requireNonNull(newLocation, "newLocation");
        this.state = ArmyState.MARCHING;
        this.lastUpdated = Instant.now();
    }

    /**
     * 受到伤害
     */
    public void takeDamage(double damage) {
        if (damage < 0) {
            throw new IllegalArgumentException("Damage cannot be negative");
        }

        // 伤害先减士兵，再减生命值
        double totalHealth = soldiers * (health / 100.0);
        totalHealth -= damage;

        if (totalHealth <= 0) {
            // 全灭
            this.soldiers = 0;
            this.health = 0;
        } else {
            // 计算新的士兵数和生命值
            double healthPerSoldier = type.baseHealth();
            this.soldiers = (int) Math.ceil(totalHealth / healthPerSoldier);
            this.health = clamp((totalHealth / soldiers) * 100.0, 0, 100);
        }

        this.lastUpdated = Instant.now();
    }

    /**
     * 恢复生命值
     */
    public void heal(double amount) {
        this.health = clamp(health + amount, 0, 100);
        this.lastUpdated = Instant.now();
    }

    /**
     * 补充补给
     */
    public void resupply(int amount) {
        this.supply = clamp(supply + amount, 0, 100);
        this.lastUpdated = Instant.now();
    }

    /**
     * 消耗补给
     */
    public void consumeSupply(int amount) {
        this.supply = Math.max(0, supply - amount);

        // 补给不足影响士气
        if (supply < 20) {
            this.morale = Math.max(0, morale - 5);
        }

        this.lastUpdated = Instant.now();
    }

    /**
     * 改变士气
     */
    public void changeMorale(double delta) {
        this.morale = clamp(morale + delta, 0, 100);
        this.lastUpdated = Instant.now();
    }

    /**
     * 增加士兵（招募）
     */
    public void recruitSoldiers(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("Count cannot be negative");
        }
        this.soldiers += count;
        this.lastUpdated = Instant.now();
    }

    // ==================== 计算属性 ====================

    /**
     * 有效攻击力
     */
    public double effectiveAttack() {
        return type.baseAttack() * soldiers * (health / 100.0) * (morale / 100.0) * state.combatModifier();
    }

    /**
     * 有效防御力
     */
    public double effectiveDefense() {
        return type.baseDefense() * soldiers * (health / 100.0) * state.combatModifier();
    }

    /**
     * 是否存活
     */
    public boolean isAlive() {
        return soldiers > 0 && health > 0;
    }

    /**
     * 是否需要补给
     */
    public boolean needsSupply() {
        return supply < 50;
    }

    /**
     * 是否可以移动
     */
    public boolean canMove() {
        return isAlive() && state.canMove() && supply > 0;
    }

    /**
     * 是否可以战斗
     */
    public boolean canFight() {
        return isAlive() && supply > 0 && morale > 20;
    }

    /**
     * 战斗力评分（用于 AI 决策）
     */
    public double combatRating() {
        return effectiveAttack() + effectiveDefense() * 0.5;
    }

    // ==================== 工具方法 ====================

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ArmyUnit other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return String.format("ArmyUnit{id=%s, type=%s, soldiers=%d, health=%.1f%%, morale=%.1f%%, state=%s}",
            id, type, soldiers, health, morale, state);
    }
}
