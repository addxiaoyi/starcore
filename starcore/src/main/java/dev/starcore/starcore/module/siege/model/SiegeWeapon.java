package dev.starcore.starcore.module.siege.model;

import org.bukkit.Location;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 攻城器械
 * 代表一个可部署的攻城设备
 */
public final class SiegeWeapon {
    private final UUID id;
    private final UUID nationId;
    private final SiegeWeaponType type;
    private Location location;           // 当前位置
    private SiegeWeaponState state;     // 当前状态
    private int durability;            // 耐久度 (0-100)
    private int ammunition;            // 弹药 (0-100)
    private int damage;               // 已造成伤害
    private final Instant createdAt;
    private Instant lastUpdated;

    public SiegeWeapon(
        UUID id,
        UUID nationId,
        SiegeWeaponType type,
        Location location,
        SiegeWeaponState state,
        int durability,
        int ammunition,
        int damage,
        Instant createdAt
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.nationId = Objects.requireNonNull(nationId, "nationId");
        this.type = Objects.requireNonNull(type, "type");
        this.location = Objects.requireNonNull(location, "location");
        this.state = Objects.requireNonNull(state, "state");
        this.durability = clamp(durability, 0, 100);
        this.ammunition = clamp(ammunition, 0, 100);
        this.damage = damage;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.lastUpdated = Instant.now();
    }

    /**
     * 创建新的攻城器械
     */
    public static SiegeWeapon create(UUID nationId, SiegeWeaponType type, Location location) {
        return new SiegeWeapon(
            UUID.randomUUID(),
            nationId,
            type,
            location,
            SiegeWeaponState.DEPLOYED,
            100,
            type.defaultAmmunition(),
            0,
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

    public SiegeWeaponType type() {
        return type;
    }

    public Location location() {
        return location;
    }

    public SiegeWeaponState state() {
        return state;
    }

    public int durability() {
        return durability;
    }

    public int ammunition() {
        return ammunition;
    }

    public int damage() {
        return damage;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant lastUpdated() {
        return lastUpdated;
    }

    // ==================== 状态修改 ====================

    /**
     * 设置状态
     */
    public void setState(SiegeWeaponState newState) {
        this.state = Objects.requireNonNull(newState, "newState");
        this.lastUpdated = Instant.now();
    }

    /**
     * 移动到新位置
     */
    public void moveTo(Location newLocation) {
        if (!canMove()) {
            throw new IllegalStateException("Cannot move in current state");
        }
        this.location = Objects.requireNonNull(newLocation, "newLocation");
        this.lastUpdated = Instant.now();
    }

    /**
     * 部署
     */
    public void deploy() {
        if (state != SiegeWeaponState.STORED) {
            throw new IllegalStateException("Can only deploy from STORED state");
        }
        this.state = SiegeWeaponState.DEPLOYED;
        this.lastUpdated = Instant.now();
    }

    /**
     * 收起
     */
    public void store() {
        if (state != SiegeWeaponState.DEPLOYED && state != SiegeWeaponState.IDLE) {
            throw new IllegalStateException("Can only store from DEPLOYED or IDLE state");
        }
        this.state = SiegeWeaponState.STORED;
        this.lastUpdated = Instant.now();
    }

    /**
     * 射击
     */
    public int fire(Location target) {
        if (!canFire()) {
            throw new IllegalStateException("Cannot fire in current state");
        }
        if (ammunition < type.ammoCost()) {
            throw new IllegalStateException("Insufficient ammunition");
        }

        this.ammunition -= type.ammoCost();
        this.lastUpdated = Instant.now();

        return type.calculateDamage(target, location);
    }

    /**
     * 造成伤害记录
     */
    public void recordDamage(int dmg) {
        this.damage += dmg;
        this.lastUpdated = Instant.now();
    }

    /**
     * 耐久度损失
     */
    public void damage(int amount) {
        this.durability = Math.max(0, durability - amount);
        if (durability == 0) {
            this.state = SiegeWeaponState.DESTROYED;
        }
        this.lastUpdated = Instant.now();
    }

    /**
     * 装填弹药
     */
    public void reload(int amount) {
        this.ammunition = clamp(ammunition + amount, 0, 100);
        this.lastUpdated = Instant.now();
    }

    /**
     * 修理
     */
    public void repair(int amount) {
        this.durability = clamp(durability + amount, 0, 100);
        if (durability > 0 && state == SiegeWeaponState.DESTROYED) {
            this.state = SiegeWeaponState.IDLE;
        }
        this.lastUpdated = Instant.now();
    }

    // ==================== 计算属性 ====================

    /**
     * 有效攻击力
     */
    public double effectiveDamage() {
        if (!canFire()) {
            return 0;
        }
        double stateBonus = state.damageMultiplier();
        double durabilityBonus = durability / 100.0;
        return type.baseDamage() * stateBonus * durabilityBonus;
    }

    /**
     * 是否可用
     */
    public boolean isOperational() {
        return durability > 0 && state != SiegeWeaponState.DESTROYED;
    }

    /**
     * 是否可以移动
     */
    public boolean canMove() {
        return isOperational() && type.movable();
    }

    /**
     * 是否可以射击
     */
    public boolean canFire() {
        return isOperational() && (state == SiegeWeaponState.DEPLOYED || state == SiegeWeaponState.IDLE)
            && ammunition >= type.ammoCost();
    }

    /**
     * 是否需要补给
     */
    public boolean needsSupply() {
        return ammunition < type.ammoCost() * 2 || durability < 50;
    }

    // ==================== 工具方法 ====================

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SiegeWeapon other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return String.format("SiegeWeapon{id=%s, type=%s, state=%s, durability=%d, ammunition=%d}",
            id, type, state, durability, ammunition);
    }
}