package dev.starcore.starcore.optimization.entity;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 实体优化服务
 * 灵感来自 let-me-despawn，提供全面的实体优化
 */
public final class EntityOptimizationService implements Listener {
    private final Plugin plugin;
    private final EntityOptimizationConfig config;

    // 追踪拾取物品的生物（let-me-despawn核心）
    private final Set<UUID> mobsWithPickedItems = ConcurrentHashMap.newKeySet();

    // 实体生成时间追踪
    private final Map<UUID, Long> entitySpawnTime = new ConcurrentHashMap<>();

    // 统计数据
    private long totalCleared = 0;
    private long itemsCleared = 0;
    private long mobsCleared = 0;

    public EntityOptimizationService(Plugin plugin, EntityOptimizationConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    /**
     * 启动优化服务
     */
    public void start() {
        if (!config.enabled()) {
            return;
        }

        // 注册事件监听器
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        // 启动清理任务
        new BukkitRunnable() {
            @Override
            public void run() {
                optimizeEntities();
            }
        }.runTaskTimer(plugin, config.checkIntervalTicks(), config.checkIntervalTicks());

        plugin.getLogger().info("✅ 实体优化服务已启动");
    }

    /**
     * 监听生物拾取物品（let-me-despawn核心）
     */
    @EventHandler
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (!config.allowPersistentMobDespawn()) {
            return;
        }

        Entity entity = event.getEntity();
        if (entity instanceof Mob mob) {
            // 标记这个生物拾取了物品
            mobsWithPickedItems.add(mob.getUniqueId());
        }
    }

    /**
     * 追踪物品生成时间
     */
    @EventHandler
    public void onItemSpawn(ItemSpawnEvent event) {
        if (config.autoRemoveDroppedItems()) {
            entitySpawnTime.put(event.getEntity().getUniqueId(), System.currentTimeMillis());
        }
    }

    /**
     * 主优化逻辑
     */
    private void optimizeEntities() {
        for (World world : plugin.getServer().getWorlds()) {
            List<Entity> entities = world.getEntities();

            int processed = 0;
            for (Entity entity : entities) {
                // 限制每tick处理的实体数量
                if (processed >= config.maxEntitiesPerTick()) {
                    break;
                }

                if (shouldRemoveEntity(entity)) {
                    removeEntity(entity);
                    processed++;
                }
            }
        }

        // 清理过期的追踪数据
        cleanupTracking();
    }

    /**
     * 判断是否应该移除实体
     */
    private boolean shouldRemoveEntity(Entity entity) {
        // 跳过玩家
        if (entity instanceof Player) {
            return false;
        }

        // 检查命名牌
        if (!config.clearNamedMobs() && entity.customName() != null) {
            return false;
        }

        // 检查拴绳
        if (!config.clearLeashedMobs() && entity instanceof LivingEntity living && living.isLeashed()) {
            return false;
        }

        // 检查驯服
        if (!config.clearTamedMobs() && entity instanceof Tameable tameable && tameable.isTamed()) {
            return false;
        }

        // 检查距离玩家
        if (!isfarFromPlayers(entity.getLocation())) {
            return false;
        }

        // 持久化生物优化（let-me-despawn核心）
        if (entity instanceof Mob mob) {
            return shouldRemoveMob(mob);
        }

        // 掉落物优化
        if (entity instanceof Item item) {
            return shouldRemoveItem(item);
        }

        // 箭矢优化
        if (entity instanceof Arrow arrow) {
            return shouldRemoveArrow(arrow);
        }

        return false;
    }

    /**
     * 判断是否应该移除生物（let-me-despawn逻辑）
     */
    private boolean shouldRemoveMob(Mob mob) {
        if (!config.allowPersistentMobDespawn()) {
            return false;
        }

        // 检查是否拾取了物品
        if (!mobsWithPickedItems.contains(mob.getUniqueId())) {
            return false;
        }

        // 检查chunk实体密度
        Chunk chunk = mob.getLocation().getChunk();
        return isChunkOvercrowded(chunk);
    }

    /**
     * 判断是否应该移除掉落物
     */
    private boolean shouldRemoveItem(Item item) {
        if (!config.autoRemoveDroppedItems()) {
            return false;
        }

        Long spawnTime = entitySpawnTime.get(item.getUniqueId());
        if (spawnTime == null) {
            return false;
        }

        long age = (System.currentTimeMillis() - spawnTime) / 1000;
        return age > config.droppedItemLifetime();
    }

    /**
     * 判断是否应该移除箭矢
     */
    private boolean shouldRemoveArrow(Arrow arrow) {
        if (!config.autoRemoveArrowsStuck()) {
            return false;
        }

        if (!arrow.isInBlock()) {
            return false;
        }

        Long spawnTime = entitySpawnTime.get(arrow.getUniqueId());
        if (spawnTime == null) {
            return false;
        }

        long age = (System.currentTimeMillis() - spawnTime) / 1000;
        return age > config.arrowStuckLifetime();
    }

    /**
     * 检查chunk是否过度拥挤
     */
    private boolean isChunkOvercrowded(Chunk chunk) {
        Entity[] entities = chunk.getEntities();

        int mobs = 0;
        int hostileMobs = 0;
        int passiveMobs = 0;
        int items = 0;

        for (Entity entity : entities) {
            if (entity instanceof Monster) {
                hostileMobs++;
                mobs++;
            } else if (entity instanceof Animals || entity instanceof WaterMob) {
                passiveMobs++;
                mobs++;
            } else if (entity instanceof Item) {
                items++;
            }
        }

        return mobs > config.maxMobsPerChunk() ||
               hostileMobs > config.maxHostileMobsPerChunk() ||
               passiveMobs > config.maxPassiveMobsPerChunk() ||
               items > config.maxItemsPerChunk();
    }

    /**
     * 检查实体是否远离所有玩家
     */
    private boolean isfarFromPlayers(Location location) {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.getWorld() == location.getWorld()) {
                if (player.getLocation().distance(location) < config.minDistanceFromPlayer()) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * 移除实体（let-me-despawn: 掉落装备）
     */
    private void removeEntity(Entity entity) {
        // 如果是生物且拾取了物品，先掉落装备
        if (entity instanceof Mob mob && mobsWithPickedItems.contains(mob.getUniqueId())) {
            if (config.dropItemsOnDespawn()) {
                dropEquipment(mob);
            }
            mobsWithPickedItems.remove(mob.getUniqueId());
            mobsCleared++;
        }

        // 统计
        if (entity instanceof Item) {
            itemsCleared++;
        }

        // 移除实体
        entity.remove();
        totalCleared++;

        // 清理追踪
        entitySpawnTime.remove(entity.getUniqueId());
    }

    /**
     * 掉落生物装备（let-me-despawn核心逻辑）
     */
    private void dropEquipment(Mob mob) {
        EntityEquipment equipment = mob.getEquipment();
        if (equipment == null) {
            return;
        }

        Location loc = mob.getLocation();
        World world = mob.getWorld();

        // 掉落所有装备
        dropItemIfPresent(world, loc, equipment.getHelmet());
        dropItemIfPresent(world, loc, equipment.getChestplate());
        dropItemIfPresent(world, loc, equipment.getLeggings());
        dropItemIfPresent(world, loc, equipment.getBoots());
        dropItemIfPresent(world, loc, equipment.getItemInMainHand());
        dropItemIfPresent(world, loc, equipment.getItemInOffHand());
    }

    /**
     * 掉落物品（如果存在）
     */
    private void dropItemIfPresent(World world, Location location, ItemStack item) {
        if (item != null && !item.getType().isAir()) {
            world.dropItemNaturally(location, item.clone());
        }
    }

    /**
     * 清理过期的追踪数据
     */
    private void cleanupTracking() {
        // 移除已经不存在的实体追踪
        entitySpawnTime.entrySet().removeIf(entry -> {
            UUID uuid = entry.getKey();
            return plugin.getServer().getEntity(uuid) == null;
        });

        mobsWithPickedItems.removeIf(uuid ->
            plugin.getServer().getEntity(uuid) == null
        );
    }

    /**
     * 获取统计数据
     */
    public EntityOptimizationStats getStats() {
        int currentEntities = 0;
        int currentMobs = 0;
        int currentItems = 0;

        for (World world : plugin.getServer().getWorlds()) {
            for (Entity entity : world.getEntities()) {
                currentEntities++;
                if (entity instanceof Mob) currentMobs++;
                if (entity instanceof Item) currentItems++;
            }
        }

        return new EntityOptimizationStats(
            totalCleared,
            itemsCleared,
            mobsCleared,
            currentEntities,
            currentMobs,
            currentItems,
            mobsWithPickedItems.size()
        );
    }

    /**
     * 统计数据记录
     */
    public record EntityOptimizationStats(
        long totalCleared,
        long itemsCleared,
        long mobsCleared,
        int currentEntities,
        int currentMobs,
        int currentItems,
        int trackedPersistentMobs
    ) {
    }
}
