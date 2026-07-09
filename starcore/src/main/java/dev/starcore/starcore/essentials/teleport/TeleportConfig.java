package dev.starcore.starcore.essentials.teleport;

import org.bukkit.Location;

import java.time.Duration;

/**
 * 传送系统配置
 */
public record TeleportConfig(
    Location spawnLocation,
    Duration spawnCooldown,
    Duration homeCooldown,
    Duration warpCooldown,
    Duration tpaCooldown,
    Duration backCooldown,
    int maxHomes,
    boolean cancelOnMove,
    boolean cancelOnDamage
) {
    public static TeleportConfig defaults() {
        return new TeleportConfig(
            null,                       // 需要设置
            Duration.ofSeconds(3),      // spawn 冷却
            Duration.ofSeconds(3),      // home 冷却
            Duration.ofSeconds(3),      // warp 冷却
            Duration.ofSeconds(5),      // tpa 冷却
            Duration.ofSeconds(3),      // back 冷却
            5,                          // 最多5个家
            true,                       // 移动取消
            true                        // 受伤取消
        );
    }

    /**
     * 获取spawn传送延迟（秒）
     */
    public long spawnDelay() {
        return spawnCooldown.getSeconds();
    }

    /**
     * 获取home传送延迟（秒）
     */
    public long homeDelay() {
        return homeCooldown.getSeconds();
    }

    /**
     * 获取warp传送延迟（秒）
     */
    public long warpDelay() {
        return warpCooldown.getSeconds();
    }

    /**
     * 获取tpa传送延迟（秒）
     */
    public long tpaDelay() {
        return tpaCooldown.getSeconds();
    }

    /**
     * 获取back传送延迟（秒）
     */
    public long backDelay() {
        return backCooldown.getSeconds();
    }
}
