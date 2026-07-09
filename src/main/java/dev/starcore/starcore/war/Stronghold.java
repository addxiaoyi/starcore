package dev.starcore.starcore.war;

import dev.starcore.starcore.module.nation.model.NationId;
import org.bukkit.Location;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 据点
 * 战场中的战略要地
 */
public final class Stronghold {
    private final UUID id;
    private final UUID battlefieldId;
    private final String name;
    private final Location location;
    private final StrongholdType type;
    private NationId owner;
    private int defenseValue;               // 防御值
    private int garrisonStrength;           // 驻军强度
    private final Instant createdAt;
    private Instant lastCapturedAt;
    private boolean underSiege;             // 是否被围攻

    public Stronghold(
        UUID id,
        UUID battlefieldId,
        String name,
        Location location,
        StrongholdType type,
        NationId owner,
        int defenseValue,
        Instant createdAt
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.battlefieldId = Objects.requireNonNull(battlefieldId, "battlefieldId");
        this.name = Objects.requireNonNull(name, "name");
        this.location = Objects.requireNonNull(location, "location");
        this.type = Objects.requireNonNull(type, "type");
        this.owner = owner;
        this.defenseValue = defenseValue;
        this.garrisonStrength = 0;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.underSiege = false;
    }

    public UUID id() {
        return id;
    }

    public UUID battlefieldId() {
        return battlefieldId;
    }

    public String name() {
        return name;
    }

    public Location location() {
        return location;
    }

    public StrongholdType type() {
        return type;
    }

    public NationId owner() {
        return owner;
    }

    public int defenseValue() {
        return defenseValue;
    }

    public int garrisonStrength() {
        return garrisonStrength;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant lastCapturedAt() {
        return lastCapturedAt;
    }

    public boolean isUnderSiege() {
        return underSiege;
    }

    /**
     * 占领据点
     */
    public void capture(NationId newOwner) {
        this.owner = Objects.requireNonNull(newOwner, "newOwner");
        this.lastCapturedAt = Instant.now();
        this.underSiege = false;
    }

    /**
     * 设置驻军
     */
    public void setGarrison(int strength) {
        this.garrisonStrength = Math.max(0, strength);
    }

    /**
     * 增加驻军
     */
    public void reinforceGarrison(int amount) {
        this.garrisonStrength += Math.max(0, amount);
    }

    /**
     * 减少驻军
     */
    public void reduceGarrison(int amount) {
        this.garrisonStrength = Math.max(0, this.garrisonStrength - amount);
    }

    /**
     * 修复防御工事
     */
    public void repair(int amount) {
        this.defenseValue = Math.min(type.maxDefense(), this.defenseValue + amount);
    }

    /**
     * 破坏防御工事
     */
    public void damage(int amount) {
        this.defenseValue = Math.max(0, this.defenseValue - amount);
    }

    /**
     * 开始围攻
     */
    public void startSiege() {
        this.underSiege = true;
    }

    /**
     * 解除围攻
     */
    public void endSiege() {
        this.underSiege = false;
    }

    /**
     * 计算有效防御力
     */
    public int effectiveDefense() {
        int base = defenseValue + garrisonStrength;
        return (int) (base * type.defenseMultiplier());
    }

    /**
     * 是否已被摧毁
     */
    public boolean isDestroyed() {
        return defenseValue <= 0;
    }

    /**
     * 战略价值
     */
    public int strategicValue() {
        return type.baseValue() + (garrisonStrength / 10);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Stronghold other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return String.format("Stronghold{id=%s, name='%s', type=%s, owner=%s, defense=%d, garrison=%d, siege=%s}",
            id, name, type, owner, defenseValue, garrisonStrength, underSiege);
    }

    /**
     * 据点类型
     */
    public enum StrongholdType {
        /**
         * 前哨站 - 提供视野和预警
         */
        OUTPOST("前哨站", 100, 1.0, 10),

        /**
         * 堡垒 - 中等防御，适合驻军
         */
        FORTRESS("堡垒", 500, 1.5, 30),

        /**
         * 要塞 - 强大防御，战略要地
         */
        CITADEL("要塞", 1000, 2.0, 50),

        /**
         * 补给站 - 提供补给，防御较弱
         */
        SUPPLY_DEPOT("补给站", 200, 0.8, 20),

        /**
         * 首都 - 最高价值目标
         */
        CAPITAL("首都", 2000, 3.0, 100);

        private final String displayName;
        private final int maxDefense;
        private final double defenseMultiplier;
        private final int baseValue;

        StrongholdType(String displayName, int maxDefense, double defenseMultiplier, int baseValue) {
            this.displayName = displayName;
            this.maxDefense = maxDefense;
            this.defenseMultiplier = defenseMultiplier;
            this.baseValue = baseValue;
        }

        public String displayName() {
            return displayName;
        }

        public int maxDefense() {
            return maxDefense;
        }

        public double defenseMultiplier() {
            return defenseMultiplier;
        }

        public int baseValue() {
            return baseValue;
        }
    }
}
