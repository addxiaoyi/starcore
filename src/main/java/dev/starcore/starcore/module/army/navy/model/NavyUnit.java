package dev.starcore.starcore.module.army.navy.model;

import org.bukkit.Location;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 海军舰队单位
 * 代表一支由多艘舰船组成的舰队
 */
public final class NavyUnit {
    private final UUID id;
    private final UUID nationId;
    private final NavyType type;
    private int ships;                // 舰船数量
    private double health;             // 生命值百分比 (0-100)
    private double morale;             // 士气百分比 (0-100)
    private Location location;         // 当前位置
    private NavyState state;           // 当前状态
    private int supply;                // 补给 (0-100)
    private String name;               // 舰队名称
    private int embarkedUnits;         // 搭载的陆军单位数
    private final Instant createdAt;
    private Instant lastUpdated;

    public NavyUnit(
        UUID id,
        UUID nationId,
        NavyType type,
        int ships,
        double health,
        double morale,
        Location location,
        NavyState state,
        int supply,
        String name,
        int embarkedUnits,
        Instant createdAt
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.nationId = Objects.requireNonNull(nationId, "nationId");
        this.type = Objects.requireNonNull(type, "type");
        this.ships = ships;
        this.health = clamp(health, 0, 100);
        this.morale = clamp(morale, 0, 100);
        this.location = Objects.requireNonNull(location, "location");
        this.state = Objects.requireNonNull(state, "state");
        this.supply = clamp(supply, 0, 100);
        this.name = Objects.requireNonNullElse(name, "Unnamed Fleet");
        this.embarkedUnits = embarkedUnits;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.lastUpdated = Instant.now();
    }

    /**
     * 创建新舰队
     */
    public static NavyUnit create(UUID nationId, NavyType type, int ships, Location location, String name) {
        return new NavyUnit(
            UUID.randomUUID(),
            nationId,
            type,
            ships,
            100.0,  // 满血
            100.0,  // 满士气
            location,
            NavyState.ANCHORED,
            100,    // 满补给
            name,
            0,      // 无搭载陆军
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

    public NavyType type() {
        return type;
    }

    public int ships() {
        return ships;
    }

    public double health() {
        return health;
    }

    public double morale() {
        return morale;
    }

    public Location location() {
        return location;
    }

    public NavyState state() {
        return state;
    }

    public int supply() {
        return supply;
    }

    public String name() {
        return name;
    }

    public int embarkedUnits() {
        return embarkedUnits;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant lastUpdated() {
        return lastUpdated;
    }

    // ==================== 状态修改 ====================

    /**
     * 设置舰队名称
     */
    public void setName(String name) {
        this.name = Objects.requireNonNullElse(name, "Unnamed Fleet");
        this.lastUpdated = Instant.now();
    }

    /**
     * 切换状态
     */
    public void setState(NavyState newState) {
        this.state = Objects.requireNonNull(newState, "newState");
        this.lastUpdated = Instant.now();
    }

    /**
     * 移动到新位置
     */
    public void moveTo(Location newLocation) {
        this.location = Objects.requireNonNull(newLocation, "newLocation");
        this.state = NavyState.SAILING;
        this.lastUpdated = Instant.now();
    }

    /**
     * 受到伤害
     */
    public void takeDamage(double damage) {
        if (damage < 0) {
            throw new IllegalArgumentException("Damage cannot be negative");
        }

        // 伤害先减舰船，再减生命值
        double totalHealth = ships * (health / 100.0) * type.baseHealth();
        totalHealth -= damage;

        if (totalHealth <= 0) {
            // 全灭
            this.ships = 0;
            this.health = 0;
        } else {
            // 计算新的舰船数和生命值
            double healthPerShip = type.baseHealth();
            this.ships = (int) Math.ceil(totalHealth / healthPerShip);
            this.health = clamp((totalHealth / ships / healthPerShip) * 100.0, 0, 100);
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
     * 搭载陆军单位
     */
    public boolean embarkUnits(int count) {
        int capacity = ships * type.transportCapacity();
        if (embarkedUnits + count > capacity) {
            return false; // 超出容量
        }
        this.embarkedUnits += count;
        this.lastUpdated = Instant.now();
        return true;
    }

    /**
     * 卸载陆军单位
     */
    public int disembarkUnits(int count) {
        int actual = Math.min(count, embarkedUnits);
        this.embarkedUnits -= actual;
        this.lastUpdated = Instant.now();
        return actual;
    }

    /**
     * 获取运输容量
     */
    public int transportCapacity() {
        return ships * type.transportCapacity();
    }

    // ==================== 计算属性 ====================

    /**
     * 有效攻击力
     */
    public double effectiveAttack() {
        return type.baseAttack() * ships * (health / 100.0) * (morale / 100.0) * state.combatModifier();
    }

    /**
     * 有效防御力
     */
    public double effectiveDefense() {
        return type.baseDefense() * ships * (health / 100.0) * state.combatModifier();
    }

    /**
     * 是否存活
     */
    public boolean isAlive() {
        return ships > 0 && health > 0;
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
        if (!(o instanceof NavyUnit other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return String.format("NavyUnit{id=%s, name=%s, type=%s, ships=%d, health=%.1f%%, morale=%.1f%%, state=%s}",
            id, name, type, ships, health, morale, state);
    }
}
