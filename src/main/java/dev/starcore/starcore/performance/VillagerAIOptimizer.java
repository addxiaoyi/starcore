package dev.starcore.starcore.performance;

import dev.starcore.starcore.core.StarCoreBanner;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 村民AI优化系统（脑叶切除）
 * 移除不必要的AI行为以提升性能
 */
public final class VillagerAIOptimizer implements Listener {
    private final Plugin plugin;
    private final AsyncTaskManager asyncManager;

    // 配置选项
    private boolean enabled = true;
    private boolean removeAI = true;              // 完全移除AI
    private boolean removeGravity = false;        // 移除重力（谨慎使用）
    private boolean optimizeMerchant = true;      // 优化商人行为
    private boolean optimizePathfinding = true;   // 优化寻路
    private boolean optimizeGoals = true;         // 优化目标系统

    // 世界白名单（这些世界不优化）
    private final ConcurrentHashMap<String, Boolean> worldWhitelist = new ConcurrentHashMap<>();

    // 统计
    private final AtomicInteger optimizedVillagers = new AtomicInteger(0);
    private final AtomicInteger totalVillagers = new AtomicInteger(0);

    public VillagerAIOptimizer(Plugin plugin, AsyncTaskManager asyncManager) {
        this.plugin = plugin;
        this.asyncManager = asyncManager;
    }

    /**
     * 启动优化器
     */
    public void start() {
        if (!enabled) return;

        StarCoreBanner.printInfo("启动村民AI优化系统...");

        // 注册监听器
        Bukkit.getPluginManager().registerEvents(this, plugin);

        // 异步优化所有已加载的村民
        asyncManager.runAsync(() -> {
            optimizeAllLoadedVillagers();
        });

        StarCoreBanner.printSuccess("村民AI优化系统已启动");
    }

    /**
     * 监听生物生成
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!enabled) return;
        if (!(event.getEntity() instanceof Villager villager)) return;

        // 检查世界白名单
        if (isWorldWhitelisted(villager.getWorld().getName())) {
            return;
        }

        // 异步优化
        asyncManager.runAsync(() -> {
            optimizeVillager(villager);
        });
    }

    /**
     * 监听区块加载
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!enabled) return;
        if (isWorldWhitelisted(event.getWorld().getName())) return;

        // 异步优化区块中的村民
        asyncManager.runAsync(() -> {
            for (Entity entity : event.getChunk().getEntities()) {
                if (entity instanceof Villager villager) {
                    optimizeVillager(villager);
                }
            }
        });
    }

    /**
     * 优化所有已加载的村民
     */
    private void optimizeAllLoadedVillagers() {
        int count = 0;

        for (World world : Bukkit.getWorlds()) {
            // 跳过白名单世界
            if (isWorldWhitelisted(world.getName())) {
                continue;
            }

            for (Entity entity : world.getEntities()) {
                if (entity instanceof Villager villager) {
                    totalVillagers.incrementAndGet();
                    if (optimizeVillager(villager)) {
                        count++;
                    }
                }
            }
        }

        int total = totalVillagers.get();
        StarCoreBanner.printSuccess("已优化 " + count + "/" + total + " 个村民");
    }

    /**
     * 优化单个村民（根据空间大小智能调整）
     */
    private boolean optimizeVillager(Villager villager) {
        try {
            // 1. 检测村民周围的可活动空间
            int spaceSize = calculateAvailableSpace(villager);

            // 2. 根据空间大小决定优化程度
            AIOptimizationLevel level = determineOptimizationLevel(spaceSize);

            // 3. 应用优化
            boolean optimized = applyOptimization(villager, level, spaceSize);

            if (optimized) {
                optimizedVillagers.incrementAndGet();
            }

            return optimized;

        } catch (Exception e) {
            StarCoreBanner.printError("优化村民失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 计算村民可活动空间大小
     * 返回：可移动的方块数量
     */
    private int calculateAvailableSpace(Villager villager) {
        org.bukkit.Location loc = villager.getLocation();
        World world = loc.getWorld();

        int airBlocks = 0;
        int maxRadius = 8; // 最大检测半径

        // 检测周围空气方块数量
        for (int x = -maxRadius; x <= maxRadius; x++) {
            for (int y = -2; y <= 3; y++) {  // 上下范围
                for (int z = -maxRadius; z <= maxRadius; z++) {
                    org.bukkit.Location checkLoc = loc.clone().add(x, y, z);

                    // 检查是否是空气或可通过方块
                    if (world.getBlockAt(checkLoc).isPassable()) {
                        airBlocks++;
                    }
                }
            }
        }

        return airBlocks;
    }

    /**
     * 根据空间大小决定优化等级
     */
    private AIOptimizationLevel determineOptimizationLevel(int spaceSize) {
        if (spaceSize <= 50) {
            // 极小空间（如1x1商店）- 完全移除AI
            return AIOptimizationLevel.MAXIMUM;
        } else if (spaceSize <= 150) {
            // 小空间（如3x3商店）- 高度优化
            return AIOptimizationLevel.HIGH;
        } else if (spaceSize <= 500) {
            // 中等空间（如10x10房间）- 中度优化
            return AIOptimizationLevel.MEDIUM;
        } else if (spaceSize <= 1000) {
            // 大空间 - 轻度优化
            return AIOptimizationLevel.LOW;
        } else {
            // 开放空间 - 不优化
            return AIOptimizationLevel.NONE;
        }
    }

    /**
     * 应用优化
     */
    private boolean applyOptimization(Villager villager, AIOptimizationLevel level, int spaceSize) {
        boolean optimized = false;

        switch (level) {
            case MAXIMUM:
                // 极小空间：完全移除AI
                if (removeAI) {
                    villager.setAI(false);
                    optimized = true;
                }
                if (removeGravity) {
                    villager.setGravity(false);
                    optimized = true;
                }
                villager.setSilent(true);
                villager.setCustomNameVisible(false);
                if (optimizeMerchant) {
                    villager.setRecipes(villager.getRecipes());
                    villager.setBreed(false);
                    optimized = true;
                }
                break;

            case HIGH:
                // 小空间：移除AI但保留重力
                if (removeAI) {
                    villager.setAI(false);
                    optimized = true;
                }
                villager.setSilent(true);
                if (optimizeMerchant) {
                    villager.setBreed(false);
                    optimized = true;
                }
                break;

            case MEDIUM:
                // 中等空间：优化行为但保留基本AI
                if (optimizeMerchant) {
                    villager.setBreed(false);
                    optimized = true;
                }
                villager.setSilent(true);
                break;

            case LOW:
                // 大空间：仅静音
                villager.setSilent(true);
                optimized = true;
                break;

            case NONE:
                // 开放空间：不优化
                break;
        }

        return optimized;
    }

    /**
     * AI优化等级
     */
    private enum AIOptimizationLevel {
        MAXIMUM,    // 完全移除AI（密闭空间）
        HIGH,       // 高度优化（小空间）
        MEDIUM,     // 中度优化（中等空间）
        LOW,        // 轻度优化（大空间）
        NONE        // 不优化（开放空间）
    }

    /**
     * 检查世界是否在白名单
     */
    private boolean isWorldWhitelisted(String worldName) {
        return worldWhitelist.getOrDefault(worldName, false);
    }

    /**
     * 添加世界到白名单
     */
    public void addWorldToWhitelist(String worldName) {
        worldWhitelist.put(worldName, true);
    }

    /**
     * 从白名单移除世界
     */
    public void removeWorldFromWhitelist(String worldName) {
        worldWhitelist.remove(worldName);
    }

    /**
     * 恢复所有村民
     */
    public void restoreAllVillagers() {
        asyncManager.runAsync(() -> {
            int count = 0;

            for (World world : Bukkit.getWorlds()) {
                for (Entity entity : world.getEntities()) {
                    if (entity instanceof Villager villager) {
                        // 恢复AI
                        villager.setAI(true);
                        villager.setGravity(true);
                        villager.setSilent(false);
                        count++;
                    }
                }
            }

            StarCoreBanner.printSuccess("已恢复 " + count + " 个村民");
        });
    }

    /**
     * 获取统计信息
     */
    public OptimizerStats getStats() {
        return new OptimizerStats(
            totalVillagers.get(),
            optimizedVillagers.get(),
            enabled
        );
    }

    // Getters and Setters
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setRemoveAI(boolean removeAI) {
        this.removeAI = removeAI;
    }

    public void setRemoveGravity(boolean removeGravity) {
        this.removeGravity = removeGravity;
    }

    public void setOptimizeMerchant(boolean optimizeMerchant) {
        this.optimizeMerchant = optimizeMerchant;
    }

    public void setOptimizePathfinding(boolean optimizePathfinding) {
        this.optimizePathfinding = optimizePathfinding;
    }

    public void setOptimizeGoals(boolean optimizeGoals) {
        this.optimizeGoals = optimizeGoals;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 优化器统计
     */
    public record OptimizerStats(
        int totalVillagers,
        int optimizedVillagers,
        boolean enabled
    ) {
        public double getOptimizationRate() {
            return totalVillagers > 0 ? (double) optimizedVillagers / totalVillagers * 100 : 0;
        }
    }
}
