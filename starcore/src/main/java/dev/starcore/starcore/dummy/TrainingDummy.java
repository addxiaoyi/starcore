package dev.starcore.starcore.dummy;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * 训练假人数据类
 * 模拟真实玩家用于 PvP 训练
 */
public class TrainingDummy {
    private final UUID id;
    private String name;
    private Location location;
    private boolean spawned;

    // 外观配置
    private String skin; // 玩家皮肤
    private ItemStack helmet;
    private ItemStack chestplate;
    private ItemStack leggings;
    private ItemStack boots;
    private ItemStack mainHand;
    private ItemStack offHand;

    // 属性配置
    private double health;
    private double maxHealth;
    private boolean invulnerable;
    private boolean showHealthBar;
    private boolean recordDamage;

    // 行为配置
    private boolean lookAtPlayer;
    private boolean autoRespawn;
    private long respawnDelay; // 毫秒

    // 统计数据
    private long totalDamageReceived;
    private int hitCount;
    private long lastHitTime;
    private UUID lastAttacker;

    // 实体引用
    private transient Object npcEntity; // NPC 实体对象

    public TrainingDummy(UUID id, String name, Location location) {
        this.id = id;
        this.name = name;
        this.location = location.clone();
        this.spawned = false;

        // 默认配置
        this.maxHealth = 20.0;
        this.health = maxHealth;
        this.invulnerable = false;
        this.showHealthBar = true;
        this.recordDamage = true;
        this.lookAtPlayer = true;
        this.autoRespawn = true;
        this.respawnDelay = 3000; // 3秒

        // 统计初始化
        this.totalDamageReceived = 0;
        this.hitCount = 0;
        this.lastHitTime = 0;
    }

    // Getters
    public UUID getId() { return id; }
    public String getName() { return name; }
    public Location getLocation() { return location.clone(); }
    public boolean isSpawned() { return spawned; }
    public String getSkin() { return skin; }
    public ItemStack getHelmet() { return helmet; }
    public ItemStack getChestplate() { return chestplate; }
    public ItemStack getLeggings() { return leggings; }
    public ItemStack getBoots() { return boots; }
    public ItemStack getMainHand() { return mainHand; }
    public ItemStack getOffHand() { return offHand; }
    public double getHealth() { return health; }
    public double getMaxHealth() { return maxHealth; }
    public boolean isInvulnerable() { return invulnerable; }
    public boolean isShowHealthBar() { return showHealthBar; }
    public boolean isRecordDamage() { return recordDamage; }
    public boolean isLookAtPlayer() { return lookAtPlayer; }
    public boolean isAutoRespawn() { return autoRespawn; }
    public long getRespawnDelay() { return respawnDelay; }
    public long getTotalDamageReceived() { return totalDamageReceived; }
    public int getHitCount() { return hitCount; }
    public long getLastHitTime() { return lastHitTime; }
    public UUID getLastAttacker() { return lastAttacker; }
    public Object getNpcEntity() { return npcEntity; }

    // Setters
    public void setName(String name) { this.name = name; }
    public void setLocation(Location location) { this.location = location.clone(); }
    public void setSpawned(boolean spawned) { this.spawned = spawned; }
    public void setSkin(String skin) { this.skin = skin; }
    public void setHelmet(ItemStack helmet) { this.helmet = helmet; }
    public void setChestplate(ItemStack chestplate) { this.chestplate = chestplate; }
    public void setLeggings(ItemStack leggings) { this.leggings = leggings; }
    public void setBoots(ItemStack boots) { this.boots = boots; }
    public void setMainHand(ItemStack mainHand) { this.mainHand = mainHand; }
    public void setOffHand(ItemStack offHand) { this.offHand = offHand; }
    public void setHealth(double health) { this.health = Math.max(0, Math.min(health, maxHealth)); }
    public void setMaxHealth(double maxHealth) { this.maxHealth = maxHealth; }
    public void setInvulnerable(boolean invulnerable) { this.invulnerable = invulnerable; }
    public void setShowHealthBar(boolean showHealthBar) { this.showHealthBar = showHealthBar; }
    public void setRecordDamage(boolean recordDamage) { this.recordDamage = recordDamage; }
    public void setLookAtPlayer(boolean lookAtPlayer) { this.lookAtPlayer = lookAtPlayer; }
    public void setAutoRespawn(boolean autoRespawn) { this.autoRespawn = autoRespawn; }
    public void setRespawnDelay(long respawnDelay) { this.respawnDelay = respawnDelay; }
    public void setNpcEntity(Object npcEntity) { this.npcEntity = npcEntity; }

    /**
     * 记录伤害
     */
    public void recordHit(double damage, UUID attacker) {
        if (!recordDamage) {
            return;
        }

        this.health = Math.max(0, this.health - damage);
        this.totalDamageReceived += (long) damage;
        this.hitCount++;
        this.lastHitTime = System.currentTimeMillis();
        this.lastAttacker = attacker;
    }

    /**
     * 检查是否死亡
     */
    public boolean isDead() {
        return health <= 0;
    }

    /**
     * 重置血量
     */
    public void resetHealth() {
        this.health = maxHealth;
    }

    /**
     * 重置统计
     */
    public void resetStats() {
        this.totalDamageReceived = 0;
        this.hitCount = 0;
        this.lastHitTime = 0;
        this.lastAttacker = null;
    }

    /**
     * 获取血量百分比
     */
    public double getHealthPercentage() {
        return (health / maxHealth) * 100.0;
    }

    /**
     * 获取平均每次伤害
     */
    public double getAverageDamage() {
        return hitCount > 0 ? (double) totalDamageReceived / hitCount : 0;
    }

    /**
     * 获取 DPS（每秒伤害）
     */
    public double getDPS() {
        if (hitCount == 0 || lastHitTime == 0) {
            return 0;
        }

        long duration = lastHitTime - (lastHitTime - (hitCount * 1000L));
        if (duration <= 0) {
            return 0;
        }

        return (totalDamageReceived / (double) duration) * 1000.0;
    }

    /**
     * 复制玩家装备
     */
    public void copyEquipment(Player player) {
        this.helmet = player.getInventory().getHelmet();
        this.chestplate = player.getInventory().getChestplate();
        this.leggings = player.getInventory().getLeggings();
        this.boots = player.getInventory().getBoots();
        this.mainHand = player.getInventory().getItemInMainHand();
        this.offHand = player.getInventory().getItemInOffHand();
    }

    /**
     * 复制玩家皮肤
     */
    public void copySkin(Player player) {
        this.skin = player.getName();
    }

    /**
     * 获取健康条显示
     */
    public String getHealthBar() {
        int total = 20;
        int filled = (int) ((health / maxHealth) * total);

        StringBuilder bar = new StringBuilder("§c");
        for (int i = 0; i < total; i++) {
            if (i < filled) {
                bar.append("❤");
            } else {
                bar.append("§7❤");
            }
        }

        return bar.toString() + " §f" + String.format("%.1f", health) + "§7/§f" + String.format("%.1f", maxHealth);
    }
}
