package dev.starcore.starcore.module.faith.model;

import dev.starcore.starcore.module.nation.model.NationId;

import java.time.Instant;
import java.util.UUID;

/**
 * 信仰值数据记录
 * 存储每个国家的信仰值、等级和相关状态
 */
public record FaithData(
    NationId nationId,
    int faith,
    int totalPrayers,
    int todayPrayers,
    long lastPrayerTime,
    long lastEventTime,
    int consecutiveDays,
    UUID lastPrayingPlayer
) {
    /**
     * 创建默认信仰数据
     */
    public static FaithData create(NationId nationId, UUID founderId) {
        return new FaithData(
            nationId,
            50,  // 初始信仰值 50
            0,
            0,
            System.currentTimeMillis(),
            System.currentTimeMillis(),
            0,
            founderId
        );
    }

    /**
     * 创建带初始值的信仰数据
     */
    public static FaithData create(NationId nationId, int initialFaith, UUID playerId) {
        return new FaithData(
            nationId,
            Math.max(0, Math.min(100, initialFaith)),
            0,
            0,
            System.currentTimeMillis(),
            System.currentTimeMillis(),
            0,
            playerId
        );
    }

    /**
     * 检查是否是新的一天（用于重置每日祈祷计数）
     */
    public boolean isNewDay() {
        if (lastPrayerTime == 0) return true;
        long dayMillis = 24 * 60 * 60 * 1000L;
        return (System.currentTimeMillis() - lastPrayerTime) >= dayMillis;
    }

    /**
     * 增加祈祷次数并更新时间戳
     */
    public FaithData recordPrayer(UUID playerId) {
        int newTodayPrayers = isNewDay() ? 1 : todayPrayers + 1;
        return new FaithData(
            nationId,
            faith,
            totalPrayers + 1,
            newTodayPrayers,
            System.currentTimeMillis(),
            lastEventTime,
            consecutiveDays,
            playerId
        );
    }

    /**
     * 更新信仰值
     */
    public FaithData withFaith(int newFaith) {
        return new FaithData(
            nationId,
            Math.max(0, Math.min(100, newFaith)),
            totalPrayers,
            todayPrayers,
            lastPrayerTime,
            lastEventTime,
            consecutiveDays,
            lastPrayingPlayer
        );
    }

    /**
     * 增加信仰值
     */
    public FaithData addFaith(int amount) {
        return withFaith(faith + amount);
    }

    /**
     * 设置连续天数
     */
    public FaithData withConsecutiveDays(int days) {
        return new FaithData(
            nationId,
            faith,
            totalPrayers,
            todayPrayers,
            lastPrayerTime,
            lastEventTime,
            days,
            lastPrayingPlayer
        );
    }

    /**
     * 更新时间戳
     */
    public FaithData withLastEventTime(long time) {
        return new FaithData(
            nationId,
            faith,
            totalPrayers,
            todayPrayers,
            lastPrayerTime,
            time,
            consecutiveDays,
            lastPrayingPlayer
        );
    }
}