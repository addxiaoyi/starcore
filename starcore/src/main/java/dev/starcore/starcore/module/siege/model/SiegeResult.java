package dev.starcore.starcore.module.siege.model;

import org.bukkit.Location;

import java.util.UUID;

/**
 * 攻城结果记录
 */
public record SiegeResult(
    UUID siegeId,
    UUID attackerNationId,
    UUID defenderNationId,
    boolean breached,
    int totalDamage,
    int structuresDestroyed,
    long durationSeconds,
    String summary
) {

    /**
     * 创建成功攻城结果
     */
    public static SiegeResult success(
        UUID siegeId,
        UUID attackerNationId,
        UUID defenderNationId,
        int totalDamage,
        int structuresDestroyed,
        long durationSeconds
    ) {
        return new SiegeResult(
            siegeId,
            attackerNationId,
            defenderNationId,
            true,
            totalDamage,
            structuresDestroyed,
            durationSeconds,
            String.format("Siege successful! Breached after %d seconds. Damage dealt: %d, Structures destroyed: %d",
                durationSeconds, totalDamage, structuresDestroyed)
        );
    }

    /**
     * 创建失败攻城结果
     */
    public static SiegeResult failed(
        UUID siegeId,
        UUID attackerNationId,
        UUID defenderNationId,
        int totalDamage,
        int structuresDestroyed,
        long durationSeconds,
        String reason
    ) {
        return new SiegeResult(
            siegeId,
            attackerNationId,
            defenderNationId,
            false,
            totalDamage,
            structuresDestroyed,
            durationSeconds,
            String.format("Siege failed after %d seconds: %s. Damage dealt: %d",
                durationSeconds, reason, totalDamage)
        );
    }

    /**
     * 是否成功破城
     */
    public boolean isSuccessful() {
        return breached;
    }
}