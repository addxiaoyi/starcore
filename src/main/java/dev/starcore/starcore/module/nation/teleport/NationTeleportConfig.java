package dev.starcore.starcore.module.nation.teleport;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * 国家传送配置
 */
public record NationTeleportConfig(
    boolean enabled,
    Duration cooldown,
    int warmupSeconds,
    BigDecimal capitalCost,
    BigDecimal townCost,
    boolean allowCrossWorld,
    boolean cancelOnMove,
    boolean cancelOnDamage
) {

    /**
     * 默认配置
     */
    public static NationTeleportConfig defaults() {
        return new NationTeleportConfig(
            true,                           // enabled
            Duration.ofMinutes(5),          // 5分钟冷却
            3,                              // 3秒预热
            BigDecimal.ZERO,                // 传送到首都免费
            BigDecimal.valueOf(100),        // 传送到城镇100金币
            true,                           // 允许跨世界
            true,                           // 移动时取消
            true                            // 受伤时取消
        );
    }

    /**
     * 从配置加载
     */
    public static NationTeleportConfig fromConfig(
        boolean enabled,
        long cooldownSeconds,
        int warmupSeconds,
        double capitalCost,
        double townCost,
        boolean allowCrossWorld,
        boolean cancelOnMove,
        boolean cancelOnDamage
    ) {
        return new NationTeleportConfig(
            enabled,
            Duration.ofSeconds(cooldownSeconds),
            warmupSeconds,
            BigDecimal.valueOf(capitalCost),
            BigDecimal.valueOf(townCost),
            allowCrossWorld,
            cancelOnMove,
            cancelOnDamage
        );
    }
}
