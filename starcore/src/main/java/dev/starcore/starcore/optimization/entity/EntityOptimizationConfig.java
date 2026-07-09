package dev.starcore.starcore.optimization.entity;

/**
 * 实体优化配置
 * 基于 let-me-despawn 的理念，增强版实现
 */
public record EntityOptimizationConfig(
    // 基础配置
    boolean enabled,
    int checkIntervalTicks,

    // 持久化生物优化（let-me-despawn核心功能）
    boolean allowPersistentMobDespawn,
    boolean dropItemsOnDespawn,

    // 实体数量限制
    int maxMobsPerChunk,
    int maxHostileMobsPerChunk,
    int maxPassiveMobsPerChunk,
    int maxItemsPerChunk,

    // 清理策略
    int minDistanceFromPlayer,
    boolean clearNamedMobs,
    boolean clearLeashedMobs,
    boolean clearTamedMobs,

    // 自动清理规则
    boolean autoRemoveDroppedItems,
    int droppedItemLifetime, // 秒
    boolean autoRemoveArrowsStuck,
    int arrowStuckLifetime, // 秒

    // 性能优化
    boolean asyncProcessing,
    int maxEntitiesPerTick
) {

    /**
     * 默认配置
     */
    public static EntityOptimizationConfig defaults() {
        return new EntityOptimizationConfig(
            true,                    // enabled
            100,                     // checkIntervalTicks (5秒)
            true,                    // allowPersistentMobDespawn
            true,                    // dropItemsOnDespawn
            50,                      // maxMobsPerChunk
            20,                      // maxHostileMobsPerChunk
            30,                      // maxPassiveMobsPerChunk
            100,                     // maxItemsPerChunk
            64,                      // minDistanceFromPlayer
            false,                   // clearNamedMobs
            false,                   // clearLeashedMobs
            false,                   // clearTamedMobs
            true,                    // autoRemoveDroppedItems
            300,                     // droppedItemLifetime (5分钟)
            true,                    // autoRemoveArrowsStuck
            60,                      // arrowStuckLifetime (1分钟)
            true,                    // asyncProcessing
            100                      // maxEntitiesPerTick
        );
    }

    /**
     * 性能模式（激进清理）
     */
    public static EntityOptimizationConfig performanceMode() {
        return new EntityOptimizationConfig(
            true,
            50,                      // 更频繁检查
            true,
            true,
            30,                      // 更少生物
            10,
            20,
            50,
            48,                      // 更近距离
            false,
            false,
            false,
            true,
            180,                     // 3分钟
            true,
            30,                      // 30秒
            true,
            50
        );
    }

    /**
     * 平衡模式
     */
    public static EntityOptimizationConfig balancedMode() {
        return defaults();
    }

    /**
     * 宽松模式（最小干预）
     */
    public static EntityOptimizationConfig relaxedMode() {
        return new EntityOptimizationConfig(
            true,
            200,                     // 较慢检查
            false,                   // 不清理持久化生物
            true,
            100,                     // 更多生物
            50,
            50,
            200,
            128,                     // 更远距离
            false,
            false,
            false,
            true,
            600,                     // 10分钟
            true,
            120,                     // 2分钟
            true,
            200
        );
    }
}
