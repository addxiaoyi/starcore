package dev.starcore.starcore.module.faith.model;

import java.time.Instant;
import java.util.UUID;

/**
 * 祈祷记录
 */
public record PrayerRecord(
    UUID playerId,
    String nationId,
    int x,
    int y,
    int z,
    String world,
    long timestamp,
    int faithGained
) {
    /**
     * 创建祈祷记录
     */
    public static PrayerRecord create(UUID playerId, String nationId, int x, int y, int z, String world, int faithGained) {
        return new PrayerRecord(
            playerId,
            nationId,
            x, y, z,
            world,
            System.currentTimeMillis(),
            faithGained
        );
    }

    /**
     * 获取位置描述
     */
    public String locationString() {
        return world + ":" + x + "," + y + "," + z;
    }
}