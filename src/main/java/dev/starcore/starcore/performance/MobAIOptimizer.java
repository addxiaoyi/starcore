package dev.starcore.starcore.performance;

import dev.starcore.starcore.core.StarCoreBanner;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 生物AI优化系统
 * 优化各类生物的AI以提升性能
 */
public final class MobAIOptimizer implements Listener {
    private final Plugin plugin;
    private final AsyncTaskManager asyncManager;

    // 配置选项
    private boolean enabled = true;
    private final Map<EntityType, MobOptimizationConfig> configs = new ConcurrentHashMap<>();

    // 统计
    private final AtomicInteger optimizedMobs = new AtomicInteger(0);

    public MobAIOptimizer(Plugin plugin, AsyncTaskManager asyncManager) {
        this.plugin = plugin;
        this.asyncManager = asyncManager;
        loadDefaultConfigs();
    }

    /**
     * 加载默认配置
     */
    private void loadDefaultConfigs() {
        // 村民（最激进优化）
        configs.put(EntityType.VILLAGER, new MobOptimizationConfig(
            true,   // removeAI
            false,  // removeGravity
            true,   // silent
            true    // optimize
        ));

        // 铁傀儡（中等优化）
        configs.put(EntityType.IRON_GOLEM, new MobOptimizationConfig(
            false,  // 保留AI（需要攻击）
            false,
            true,
            true
        ));

        // 流浪商人（移除AI）
        configs.put(EntityType.WANDERING_TRADER, new MobOptimizationConfig(
            true,
            false,
            true,
            true
        ));

        // 猫（轻度优化）
        configs.put(EntityType.CAT, new MobOptimizationConfig(
            false,
            false,
            false,
            true
        ));

        // 狗（轻度优化）
        configs.put(EntityType.WOLF, new MobOptimizationConfig(
            false,
            false,
            false,
            true
        ));
    }

    /**
     * 启动优化器
     */
    public void start() {
        if (!enabled) return;

        StarCoreBanner.printInfo("启动生物AI优化系统...");

        // 注册监听器
        Bukkit.getPluginManager().registerEvents(this, plugin);

        // 异步优化所有已加载的生物
        asyncManager.runAsync(this::optimizeAllLoadedMobs);

        StarCoreBanner.printSuccess("生物AI优化系统已启动");
    }

    /**
     * 监听生物生成
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!enabled) return;

        Entity entity = event.getEntity();
        EntityType type = entity.getType();

        // 检查是否需要优化
        if (!configs.containsKey(type)) return;

        // 异步优化
        asyncManager.runAsync(() -> {
            optimizeMob(entity);
        });
    }

    /**
     * 优化所有已加载的生物
     */
    private void optimizeAllLoadedMobs() {
        int count = 0;

        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (configs.containsKey(entity.getType())) {
                    if (optimizeMob(entity)) {
                        count++;
                    }
                }
            }
        }

        StarCoreBanner.printSuccess("已优化 " + count + " 个生物");
    }

    /**
     * 优化单个生物
     */
    private boolean optimizeMob(Entity entity) {
        try {
            MobOptimizationConfig config = configs.get(entity.getType());
            if (config == null || !config.optimize) return false;

            boolean optimized = false;

            // 移除AI
            if (config.removeAI && entity instanceof Mob mob) {
                mob.setAI(false);
                optimized = true;
            }

            // 移除重力
            if (config.removeGravity) {
                entity.setGravity(false);
                optimized = true;
            }

            // 静音
            if (config.silent) {
                entity.setSilent(true);
                optimized = true;
            }

            if (optimized) {
                optimizedMobs.incrementAndGet();
            }

            return optimized;

        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 设置生物优化配置
     */
    public void setMobConfig(EntityType type, MobOptimizationConfig config) {
        configs.put(type, config);
    }

    /**
     * 获取统计信息
     */
    public int getOptimizedCount() {
        return optimizedMobs.get();
    }

    /**
     * 生物优化配置
     */
    public record MobOptimizationConfig(
        boolean removeAI,
        boolean removeGravity,
        boolean silent,
        boolean optimize
    ) {}
}
