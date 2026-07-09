package dev.starcore.starcore.module.army.siege.model;

import org.bukkit.Location;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 城墙数据
 * 代表一个可被攻城的城墙结构
 */
public final class WallData {
    private final UUID id;
    private final UUID nationId;
    private final WallType type;
    private final Location location;
    private final String world;
    private final int blockX;
    private final int blockY;
    private final int blockZ;
    private int currentHealth;
    private int maxHealth;
    private int level;
    private boolean isUnderSiege;
    private UUID besiegingSiegeId;
    private Instant siegeStartTime;
    private Instant lastRepairTime;

    public WallData(
        UUID id,
        UUID nationId,
        WallType type,
        Location location,
        int currentHealth,
        int maxHealth,
        int level,
        boolean isUnderSiege,
        UUID besiegingSiegeId,
        Instant siegeStartTime,
        Instant lastRepairTime
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.nationId = Objects.requireNonNull(nationId, "nationId");
        this.type = Objects.requireNonNull(type, "type");
        this.location = Objects.requireNonNull(location, "location");
        this.world = location.getWorld().getName();
        this.blockX = location.getBlockX();
        this.blockY = location.getBlockY();
        this.blockZ = location.getBlockZ();
        this.currentHealth = currentHealth;
        this.maxHealth = maxHealth;
        this.level = level;
        this.isUnderSiege = isUnderSiege;
        this.besiegingSiegeId = besiegingSiegeId;
        this.siegeStartTime = siegeStartTime;
        this.lastRepairTime = lastRepairTime;
    }

    /**
     * 创建新城墙
     */
    public static WallData create(UUID nationId, WallType type, Location location, int level) {
        int maxHealth = type.actualHealth(level);
        return new WallData(
            UUID.randomUUID(),
            nationId,
            type,
            location,
            maxHealth,  // 满血
            maxHealth,
            level,
            false,      // 未被攻城
            null,       // 无攻城者
            null,       // 无开始时间
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

    public WallType type() {
        return type;
    }

    public Location location() {
        return location;
    }

    public String world() {
        return world;
    }

    public int blockX() {
        return blockX;
    }

    public int blockY() {
        return blockY;
    }

    public int blockZ() {
        return blockZ;
    }

    public int currentHealth() {
        return currentHealth;
    }

    public int maxHealth() {
        return maxHealth;
    }

    public int level() {
        return level;
    }

    public boolean isUnderSiege() {
        return isUnderSiege;
    }

    public UUID besiegingSiegeId() {
        return besiegingSiegeId;
    }

    public Instant siegeStartTime() {
        return siegeStartTime;
    }

    public Instant lastRepairTime() {
        return lastRepairTime;
    }

    // ==================== Setters ====================

    /**
     * 升级城墙
     */
    public void upgrade() {
        this.level++;
        this.maxHealth = type.actualHealth(level);
        this.currentHealth = Math.min(currentHealth, maxHealth);
    }

    /**
     * 设置城墙等级
     */
    public void setLevel(int newLevel) {
        if (newLevel < 1) {
            throw new IllegalArgumentException("Level cannot be less than 1");
        }
        this.level = newLevel;
        this.maxHealth = type.actualHealth(level);
        this.currentHealth = Math.min(currentHealth, maxHealth);
    }

    /**
     * 受到伤害
     */
    public void takeDamage(double damage) {
        if (damage < 0) {
            throw new IllegalArgumentException("Damage cannot be negative");
        }
        this.currentHealth = Math.max(0, currentHealth - (int) damage);
    }

    /**
     * 修复城墙
     */
    public void repair(int amount) {
        this.currentHealth = Math.min(maxHealth, currentHealth + amount);
        this.lastRepairTime = Instant.now();
    }

    /**
     * 开始被攻城
     */
    public void startSiege(UUID siegeId) {
        this.isUnderSiege = true;
        this.besiegingSiegeId = siegeId;
        this.siegeStartTime = Instant.now();
    }

    /**
     * 结束攻城
     */
    public void endSiege() {
        this.isUnderSiege = false;
        this.besiegingSiegeId = null;
        this.siegeStartTime = null;
    }

    // ==================== 计算属性 ====================

    /**
     * 生命值百分比
     */
    public double healthPercent() {
        if (maxHealth == 0) return 0;
        return (currentHealth * 100.0) / maxHealth;
    }

    /**
     * 是否已摧毁
     */
    public boolean isDestroyed() {
        return currentHealth <= 0;
    }

    /**
     * 是否严重损坏（低于30%）
     */
    public boolean isHeavilyDamaged() {
        return healthPercent() < 30;
    }

    /**
     * 是否中等损坏（低于60%）
     */
    public boolean isModeratelyDamaged() {
        return healthPercent() < 60;
    }

    /**
     * 获取防御力
     */
    public double defensePower() {
        return type.defenseMultiplier() * (1.0 + (level - 1) * 0.1);
    }

    /**
     * 获取攻城持续时间（秒）
     */
    public long siegeDurationSeconds() {
        if (siegeStartTime == null) return 0;
        return Instant.now().getEpochSecond() - siegeStartTime.getEpochSecond();
    }

    /**
     * 获取修复所需成本
     */
    public int repairCost() {
        int damagedAmount = maxHealth - currentHealth;
        return (damagedAmount / 100) * 50; // 每100点耐久50金币
    }

    /**
     * 距离某位置的平方距离
     */
    public double distanceSquared(Location other) {
        if (!world.equals(other.getWorld().getName())) {
            return Double.MAX_VALUE;
        }
        int dx = blockX - other.getBlockX();
        int dy = blockY - other.getBlockY();
        int dz = blockZ - other.getBlockZ();
        return dx * dx + dy * dy + dz * dz;
    }

    // ==================== 工具方法 ====================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WallData other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return String.format("WallData{id=%s, type=%s, health=%d/%d, level=%d, underSiege=%s}",
            id, type.key(), currentHealth, maxHealth, level, isUnderSiege);
    }
}