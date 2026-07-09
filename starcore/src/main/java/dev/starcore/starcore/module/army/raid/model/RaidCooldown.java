package dev.starcore.starcore.module.army.raid.model;

import dev.starcore.starcore.module.nation.model.NationId;

import java.time.Instant;
import java.util.UUID;

/**
 * 突袭冷却记录
 * 追踪国家发起突袭的冷却时间
 */
public record RaidCooldown(
    NationId nationId,
    Instant lastRaidTime,
    int raidsToday
) {
    /**
     * 创建新的冷却记录
     */
    public static RaidCooldown fresh(NationId nationId) {
        return new RaidCooldown(nationId, Instant.now(), 1);
    }

    /**
     * 检查是否可以发起突袭
     * @param cooldownHours 冷却时间（小时）
     * @return true 如果冷却已结束
     */
    public boolean canRaidAgain(int cooldownHours) {
        long hoursSinceLastRaid = (Instant.now().getEpochSecond() - lastRaidTime().getEpochSecond()) / 3600;
        return hoursSinceLastRaid >= cooldownHours;
    }

    /**
     * 获取剩余冷却时间（小时）
     */
    public long remainingCooldownHours(int cooldownHours) {
        long hoursSinceLastRaid = (Instant.now().getEpochSecond() - lastRaidTime().getEpochSecond()) / 3600;
        return Math.max(0, cooldownHours - hoursSinceLastRaid);
    }

    /**
     * 更新冷却记录
     */
    public RaidCooldown update() {
        return new RaidCooldown(nationId(), Instant.now(), raidsToday() + 1);
    }

    /**
     * 重置每日计数
     */
    public RaidCooldown resetDailyCount() {
        return new RaidCooldown(nationId(), lastRaidTime(), 0);
    }
}