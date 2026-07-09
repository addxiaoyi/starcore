package dev.starcore.starcore.module.dungeon;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * 玩家副本状态
 * 保存玩家进入副本前的完整状态
 */
public class PlayerDungeonState {
    private static final Logger LOGGER = Logger.getLogger(PlayerDungeonState.class.getName());
    private final UUID playerId;
    private final UUID instanceId;
    private Location savedLocation;
    private double health;
    private double maxHealth;
    private int foodLevel;
    private float saturation;
    private float exhaustion;
    private float fallDistance;
    private float saturationLevel;
    private int experienceLevel;
    private int experienceTotal;
    private float experienceProgress;
    private Map<Integer, Map<String, Object>> inventoryContents;
    private Map<Integer, Map<String, Object>> armorContents;
    private Map<String, Object> effects;
    private boolean alive;
    private int respawnsRemaining;
    private long lastDamageTime;
    private String lastDamageSource;
    private int roomDeaths;

    public PlayerDungeonState(UUID playerId, UUID instanceId) {
        this.playerId = playerId;
        this.instanceId = instanceId;
        this.alive = true;
        this.respawnsRemaining = 3;
        this.roomDeaths = 0;
        this.inventoryContents = new HashMap<>();
        this.armorContents = new HashMap<>();
        this.effects = new HashMap<>();
    }

    /**
     * 从玩家创建状态
     */
    public static PlayerDungeonState fromPlayer(Player player, UUID instanceId) {
        PlayerDungeonState state = new PlayerDungeonState(player.getUniqueId(), instanceId);
        state.captureFromPlayer(player);
        return state;
    }

    /**
     * 从玩家捕获状态
     */
    public void captureFromPlayer(Player player) {
        this.savedLocation = player.getLocation().clone();
        this.health = player.getHealth();
        this.maxHealth = player.getMaxHealth();
        this.foodLevel = player.getFoodLevel();
        this.saturation = player.getSaturation();
        this.exhaustion = player.getExhaustion();
        this.fallDistance = player.getFallDistance();
        this.experienceLevel = player.getLevel();
        this.experienceTotal = player.getTotalExperience();
        this.experienceProgress = player.getExp();

        // 保存背包
        this.inventoryContents.clear();
        org.bukkit.inventory.ItemStack[] contents = player.getInventory().getContents();
        for (int index = 0; index < contents.length; index++) {
            org.bukkit.inventory.ItemStack item = contents[index];
            if (item != null) {
                inventoryContents.put(index, item.serialize());
            }
        }

        // 保存装备栏
        this.armorContents.clear();
        org.bukkit.inventory.ItemStack[] armor = player.getInventory().getArmorContents();
        for (int index = 0; index < armor.length; index++) {
            org.bukkit.inventory.ItemStack item = armor[index];
            if (item != null) {
                armorContents.put(index, item.serialize());
            }
        }

        // 保存药水效果
        this.effects.clear();
        player.getActivePotionEffects().forEach(effect -> {
            effects.put(effect.getType().getName(), effect.getAmplifier() + ":" + effect.getDuration());
        });
    }

    /**
     * 恢复玩家状态
     */
    public void restoreToPlayer(Player player) {
        // 恢复位置
        if (savedLocation != null) {
            player.teleport(savedLocation);
        }

        // 恢复属性
        player.setHealth(health);
        player.setFoodLevel(foodLevel);
        player.setSaturation(saturation);
        player.setExhaustion(exhaustion);
        player.setFallDistance(fallDistance);
        player.setLevel(experienceLevel);
        player.setTotalExperience(experienceTotal);
        player.setExp(experienceProgress);

        // 恢复背包
        player.getInventory().clear();
        for (Map.Entry<Integer, Map<String, Object>> entry : inventoryContents.entrySet()) {
            try {
                org.bukkit.inventory.ItemStack item = org.bukkit.inventory.ItemStack.deserialize(entry.getValue());
                player.getInventory().setItem(entry.getKey(), item);
            } catch (IllegalArgumentException | NullPointerException ignored) {
                // 静默捕获反序列化失败（物品数据损坏或格式无效）
            }
        }

        // 恢复装备栏
        if (!armorContents.isEmpty()) {
            org.bukkit.inventory.ItemStack[] armorItems = new org.bukkit.inventory.ItemStack[4];
            for (Map.Entry<Integer, Map<String, Object>> entry : armorContents.entrySet()) {
                try {
                    int slot = entry.getKey();
                    if (slot >= 0 && slot < 4) {
                        armorItems[slot] = org.bukkit.inventory.ItemStack.deserialize(entry.getValue());
                    }
                } catch (IllegalArgumentException | NullPointerException ignored) {
                    // 静默捕获反序列化失败（装备数据损坏或格式无效）
                }
            }
            player.getInventory().setArmorContents(armorItems);
        }

        // 清除药水效果
        player.getActivePotionEffects().forEach(effect ->
            player.removePotionEffect(effect.getType())
        );

        // 恢复药水效果
        effects.forEach((name, value) -> {
            try {
                String[] parts = ((String) value).split(":");
                org.bukkit.potion.PotionEffectType type =
                    org.bukkit.potion.PotionEffectType.getByName(name);
                if (type != null) {
                    int amplifier = Integer.parseInt(parts[0]);
                    int duration = Integer.parseInt(parts[1]);
                    player.addPotionEffect(new org.bukkit.potion.PotionEffect(type, duration, amplifier));
                }
            } catch (Exception e) {
                LOGGER.warning("恢复药水效果失败: effect=" + name + ", player=" + player.getName() + ", error=" + e.getMessage());
            }
        });
    }

    // Getters and setters

    public UUID getPlayerId() {
        return playerId;
    }

    public UUID getInstanceId() {
        return instanceId;
    }

    public Location getSavedLocation() {
        return savedLocation;
    }

    public void setSavedLocation(Location savedLocation) {
        this.savedLocation = savedLocation;
    }

    public double getHealth() {
        return health;
    }

    public double getMaxHealth() {
        return maxHealth;
    }

    public boolean isAlive() {
        return alive;
    }

    public void setAlive(boolean alive) {
        this.alive = alive;
    }

    public int getRespawnsRemaining() {
        return respawnsRemaining;
    }

    public void decrementRespawns() {
        this.respawnsRemaining = Math.max(0, this.respawnsRemaining - 1);
    }

    public long getLastDamageTime() {
        return lastDamageTime;
    }

    public void setLastDamageTime(long lastDamageTime) {
        this.lastDamageTime = lastDamageTime;
    }

    public String getLastDamageSource() {
        return lastDamageSource;
    }

    public void setLastDamageSource(String lastDamageSource) {
        this.lastDamageSource = lastDamageSource;
    }

    public int getRoomDeaths() {
        return roomDeaths;
    }

    public void incrementRoomDeaths() {
        this.roomDeaths++;
    }

    /**
     * 处理死亡事件
     */
    public void handleDeath() {
        this.alive = false;
        this.lastDamageTime = System.currentTimeMillis();
    }

    /**
     * 处理重生事件
     */
    public void handleRespawn() {
        this.alive = true;
        this.respawnsRemaining = Math.max(0, this.respawnsRemaining - 1);
    }
}
