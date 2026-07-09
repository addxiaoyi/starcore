package dev.starcore.starcore.module.army.raid.model;

import dev.starcore.starcore.module.nation.model.NationId;

import java.time.Instant;
import java.util.UUID;

/**
 * 突袭警报
 * 用于通知被突袭国家
 */
public record RaidAlert(
    UUID id,
    NationId targetNationId,
    NationId attackerNationId,
    Instant alertTime,
    int estimatedSoldiers,
    String targetLocation,
    int warningSeconds,
    boolean acknowledged
) {
    /**
     * 创建新警报
     */
    public static RaidAlert create(NationId target, NationId attacker, int soldiers, String location, int warningSeconds) {
        return new RaidAlert(
            UUID.randomUUID(),
            target,
            attacker,
            Instant.now(),
            soldiers,
            location,
            warningSeconds,
            false
        );
    }

    /**
     * 标记为已确认
     */
    public RaidAlert acknowledge() {
        return new RaidAlert(
            id(),
            targetNationId(),
            attackerNationId(),
            alertTime(),
            estimatedSoldiers(),
            targetLocation(),
            warningSeconds(),
            true
        );
    }

    /**
     * 获取剩余预警时间（秒）
     */
    public int remainingSeconds() {
        long elapsed = Instant.now().getEpochSecond() - alertTime().getEpochSecond();
        return Math.max(0, warningSeconds() - (int) elapsed);
    }
}