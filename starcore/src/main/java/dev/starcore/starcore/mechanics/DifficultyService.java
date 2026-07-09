package dev.starcore.starcore.mechanics;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 动态难度服务
 * 根据玩家实力自动调整游戏难度，提供平衡的游戏体验
 */
public class DifficultyService implements Listener {

    private final JavaPlugin plugin;
    private final Map<UUID, DifficultyModifier> playerDifficulty;
    private final Map<UUID, Long> lastUpdateTime;

    // 配置项
    private boolean enabled = true;
    private long updateInterval = 5 * 60 * 1000; // 5分钟更新一次
    private double minDifficulty = 0.5;
    private double maxDifficulty = 3.0;

    public DifficultyService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.playerDifficulty = new HashMap<>();
        this.lastUpdateTime = new HashMap<>();
    }

    /**
     * 初始化服务
     */
    public void initialize() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        // 定时更新所有在线玩家的难度
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                updatePlayerDifficulty(player);
            }
        }, 6000L, 6000L); // 每5分钟执行一次

        plugin.getLogger().info("动态难度系统已启用");
    }

    /**
     * 更新玩家难度
     */
    public void updatePlayerDifficulty(Player player) {
        if (!enabled) return;

        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();

        // 检查是否需要更新
        if (lastUpdateTime.containsKey(playerId)) {
            long timeSinceUpdate = currentTime - lastUpdateTime.get(playerId);
            if (timeSinceUpdate < updateInterval) {
                return;
            }
        }

        // 计算玩家实力
        PlayerPowerLevel powerLevel = new PlayerPowerLevel(player);
        double power = powerLevel.getTotalPowerLevel();

        // 将实力转换为难度倍率
        // 实力 0-100 映射到 minDifficulty-maxDifficulty
        double difficultyMultiplier = minDifficulty + (power / 100.0) * (maxDifficulty - minDifficulty);

        // 创建难度调整器
        DifficultyModifier modifier = new DifficultyModifier(difficultyMultiplier);
        playerDifficulty.put(playerId, modifier);
        lastUpdateTime.put(playerId, currentTime);

        // 通知玩家
        player.sendMessage("§6[难度系统] §e你的实力等级: §b" + powerLevel.getPowerTier() +
                " §7(§f" + String.format("%.1f", power) + "§7)");
        player.sendMessage("§6[难度系统] §e当前难度: §c" + modifier.getDifficultyLevel() +
                " §7(§fx" + String.format("%.2f", difficultyMultiplier) + "§7)");
    }

    /**
     * 获取玩家的难度调整器
     */
    public DifficultyModifier getPlayerDifficulty(Player player) {
        UUID playerId = player.getUniqueId();

        if (!playerDifficulty.containsKey(playerId)) {
            updatePlayerDifficulty(player);
        }

        return playerDifficulty.getOrDefault(playerId, new DifficultyModifier(1.0));
    }

    /**
     * 怪物生成时调整难度
     */
    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!enabled) return;
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.CUSTOM) return;

        // 找到最近的玩家
        Player nearestPlayer = findNearestPlayer(event.getLocation());
        if (nearestPlayer == null) return;

        // 获取该玩家的难度
        DifficultyModifier modifier = getPlayerDifficulty(nearestPlayer);

        // 应用难度到怪物
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (event.getEntity().isValid()) {
                modifier.applyToMob(event.getEntity());
            }
        }, 1L);
    }

    /**
     * 怪物死亡时调整掉落和经验
     */
    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (!enabled) return;
        if (event.getEntity().getKiller() == null) return;

        Player killer = event.getEntity().getKiller();
        DifficultyModifier modifier = getPlayerDifficulty(killer);

        // 调整掉落数量
        int dropCount = event.getDrops().size();
        int newDropCount = modifier.modifyDropAmount(dropCount);

        // 如果需要增加掉落，复制现有掉落
        if (newDropCount > dropCount && !event.getDrops().isEmpty()) {
            int toAdd = newDropCount - dropCount;
            for (int i = 0; i < toAdd && i < event.getDrops().size(); i++) {
                event.getDrops().add(event.getDrops().get(i % event.getDrops().size()).clone());
            }
        }

        // 调整经验
        int originalExp = event.getDroppedExp();
        event.setDroppedExp(modifier.modifyExperience(originalExp));
    }

    /**
     * 调整伤害
     */
    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!enabled) return;

        // 怪物对玩家造成伤害
        if (event.getEntity() instanceof Player) {
            Player victim = (Player) event.getEntity();
            DifficultyModifier modifier = getPlayerDifficulty(victim);
            double newDamage = modifier.modifyDamageToPlayer(event.getDamage());
            event.setDamage(newDamage);
        }

        // 玩家对怪物造成伤害
        if (event.getDamager() instanceof Player) {
            Player attacker = (Player) event.getDamager();
            DifficultyModifier modifier = getPlayerDifficulty(attacker);
            double newDamage = modifier.modifyDamageFromPlayer(event.getDamage());
            event.setDamage(newDamage);
        }
    }

    /**
     * 查找最近的玩家
     */
    private Player findNearestPlayer(org.bukkit.Location location) {
        Player nearest = null;
        double minDistance = Double.MAX_VALUE;

        for (Player player : location.getWorld().getPlayers()) {
            double distance = player.getLocation().distanceSquared(location);
            if (distance < minDistance && distance < 10000) { // 100格范围内
                minDistance = distance;
                nearest = player;
            }
        }

        return nearest;
    }

    /**
     * 玩家离线时清理数据
     */
    public void removePlayer(UUID playerId) {
        playerDifficulty.remove(playerId);
        lastUpdateTime.remove(playerId);
    }

    /**
     * 关闭服务
     */
    public void shutdown() {
        playerDifficulty.clear();
        lastUpdateTime.clear();
    }

    // Getters and Setters

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setUpdateInterval(long interval) {
        this.updateInterval = interval;
    }

    public void setMinDifficulty(double min) {
        this.minDifficulty = min;
    }

    public void setMaxDifficulty(double max) {
        this.maxDifficulty = max;
    }
}
