package dev.starcore.starcore.module.army.siege.model;

import org.bukkit.Location;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 攻城器械单位
 * 代表一台攻城器械及其当前状态
 */
public final class SiegeUnit {
    private final UUID id;
    private final UUID nationId;
    private final SiegeType type;
    private int crewSize;               // 船员数量
    private double health;               // 生命值百分比 (0-100)
    private double crewMorale;           // 船员士气 (0-100)
    private Location location;            // 当前位置
    private Location deployedLocation;    // 部署位置（如果有）
    private SiegeState state;            // 当前状态
    private int ammunition;              // 弹药数量
    private int siegeExperience;         // 攻城经验值
    private final Instant createdAt;
    private Instant lastUpdated;
    private UUID siegeTarget;           // 攻城目标（城墙ID）

    public SiegeUnit(
        UUID id,
        UUID nationId,
        SiegeType type,
        int crewSize,
        double health,
        double crewMorale,
        Location location,
        Location deployedLocation,
        SiegeState state,
        int ammunition,
        int siegeExperience,
        Instant createdAt
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.nationId = Objects.requireNonNull(nationId, "nationId");
        this.type = Objects.requireNonNull(type, "type");
        this.crewSize = crewSize;
        this.health = clamp(health, 0, 100);
        this.crewMorale = clamp(crewMorale, 0, 100);
        this.location = Objects.requireNonNull(location, "location");
        this.deployedLocation = deployedLocation;
        this.state = Objects.requireNonNull(state, "state");
        this.ammunition = ammunition;
        this.siegeExperience = siegeExperience;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.lastUpdated = Instant.now();
    }

    /**
     * 创建新攻城器械
     */
    public static SiegeUnit create(UUID nationId, SiegeType type, int crewSize, Location location) {
        return new SiegeUnit(
            UUID.randomUUID(),
            nationId,
            type,
            crewSize,
            100.0,  // 满血
            100.0,  // 满士气
            location,
            null,   // 未部署
            SiegeState.IDLE,
            50,     // 初始弹药
            0,      // 无经验
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

    public SiegeType type() {
        return type;
    }

    public int crewSize() {
        return crewSize;
    }

    public double health() {
        return health;
    }

    public double crewMorale() {
        return crewMorale;
    }

    public Location location() {
        return location;
    }

    public Location deployedLocation() {
        return deployedLocation;
    }

    public SiegeState state() {
        return state;
    }

    public int ammunition() {
        return ammunition;
    }

    public int siegeExperience() {
        return siegeExperience;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant lastUpdated() {
        return lastUpdated;
    }

    public UUID siegeTarget() {
        return siegeTarget;
    }

    // ==================== Setters ====================

    /**
     * 设置攻城目标
     */
    public void setSiegeTarget(UUID targetId) {
        this.siegeTarget = targetId;
        this.lastUpdated = Instant.now();
    }

    /**
     * 切换状态
     */
    public void setState(SiegeState newState) {
        this.state = Objects.requireNonNull(newState, "newState");
        this.lastUpdated = Instant.now();
    }

    /**
     * 移动到新位置
     */
    public void moveTo(Location newLocation) {
        this.location = Objects.requireNonNull(newLocation, "newLocation");
        this.state = SiegeState.MARCHING;
        this.deployedLocation = null;
        this.siegeTarget = null;
        this.lastUpdated = Instant.now();
    }

    /**
     * 部署到位置
     */
    public void deploy(Location targetLocation) {
        this.deployedLocation = Objects.requireNonNull(targetLocation, "targetLocation");
        this.location = deployedLocation.clone();
        this.state = SiegeState.DEPLOYING;
        this.lastUpdated = Instant.now();
    }

    /**
     * 完成部署
     */
    public void completeDeployment() {
        if (this.state == SiegeState.DEPLOYING) {
            this.state = SiegeState.READY;
            this.lastUpdated = Instant.now();
        }
    }

    /**
     * 受到伤害
     */
    public void takeDamage(double damage) {
        if (damage < 0) {
            throw new IllegalArgumentException("Damage cannot be negative");
        }

        // 伤害计算
        double totalHealth = type.baseDamage() * 10 * (health / 100.0);
        totalHealth -= damage;

        if (totalHealth <= 0) {
            this.health = 0;
        } else {
            this.health = clamp((totalHealth / (type.baseDamage() * 10)) * 100.0, 0, 100);
        }

        // 血量低于30%进入损坏状态
        if (this.health < 30 && this.state != SiegeState.DESTROYED) {
            this.state = SiegeState.DAMAGED;
        }

        this.lastUpdated = Instant.now();
    }

    /**
     * 修复攻城器械
     */
    public void repair(double amount) {
        this.health = clamp(health + amount, 0, 100);
        if (this.health >= 30 && this.state == SiegeState.DAMAGED) {
            this.state = SiegeState.READY;
        }
        this.lastUpdated = Instant.now();
    }

    /**
     * 消耗弹药
     */
    public boolean useAmmunition(int amount) {
        if (ammunition < amount) {
            return false;
        }
        this.ammunition -= amount;
        this.lastUpdated = Instant.now();
        return true;
    }

    /**
     * 补充弹药
     */
    public void reload(int amount) {
        this.ammunition = clamp(ammunition + amount, 0, 100);
        this.lastUpdated = Instant.now();
    }

    /**
     * 改变士气
     */
    public void changeMorale(double delta) {
        this.crewMorale = clamp(crewMorale + delta, 0, 100);
        this.lastUpdated = Instant.now();
    }

    /**
     * 增加经验
     */
    public void addExperience(int amount) {
        this.siegeExperience += amount;
        // 每100点经验升一级（效果略微提升）
        this.lastUpdated = Instant.now();
    }

    /**
     * 补充船员
     */
    public void recruitCrew(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("Count cannot be negative");
        }
        this.crewSize += count;
        this.lastUpdated = Instant.now();
    }

    /**
     * 损失船员
     */
    public void loseCrew(int count) {
        this.crewSize = Math.max(0, crewSize - count);
        // 损失船员影响士气
        if (count > 0) {
            changeMorale(-count * 2);
        }
    }

    // ==================== 计算属性 ====================

    /**
     * 有效攻击力
     */
    public double effectiveAttack() {
        return type.baseDamage() * crewSize * (health / 100.0) * (crewMorale / 100.0) * state.combatModifier();
    }

    /**
     * 攻城伤害（对城墙）
     */
    public double siegeDamage() {
        return type.siegeDamageMultiplier() * effectiveAttack();
    }

    /**
     * 是否存活
     */
    public boolean isAlive() {
        return health > 0 && crewSize > 0;
    }

    /**
     * 是否可以移动
     */
    public boolean canMove() {
        return isAlive() && state.canMove() && crewMorale > 20;
    }

    /**
     * 是否可以开火
     */
    public boolean canFire() {
        return isAlive() && state.canFire() && ammunition > 0 && crewMorale > 10;
    }

    /**
     * 是否正在攻城
     */
    public boolean isBesieging() {
        return state == SiegeState.BESIEGING && siegeTarget != null;
    }

    /**
     * 是否已部署
     */
    public boolean isDeployed() {
        return deployedLocation != null && (state == SiegeState.READY || state == SiegeState.BESIEGING);
    }

    /**
     * 检查是否在攻击范围内
     */
    public boolean isInRange(Location target) {
        if (deployedLocation == null) {
            return false;
        }
        return deployedLocation.distance(target) <= type.effectiveRange();
    }

    /**
     * 获取经验等级
     */
    public int experienceLevel() {
        return (siegeExperience / 100) + 1;
    }

    /**
     * 获取经验等级名称
     */
    public String experienceLevelName() {
        return switch (experienceLevel()) {
            case 1 -> "新手";
            case 2 -> "老兵";
            case 3 -> "精英";
            case 4 -> "大师";
            default -> "传奇";
        };
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
        if (!(o instanceof SiegeUnit other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return String.format("SiegeUnit{id=%s, type=%s, health=%.1f%%, morale=%.1f%%, state=%s}",
            id, type.key(), health, crewMorale, state);
    }
}