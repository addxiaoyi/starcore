package dev.starcore.starcore.module.faith.model;

/**
 * 信仰值统计信息
 */
public record FaithStats(
    int currentFaith,
    int faithLevel,
    String faithLevelName,
    int totalPrayers,
    int todayPrayers,
    int consecutiveDays,
    double resourceBonus,
    double defenseBonus,
    double taxBonus,
    double expBonus
) {
    /**
     * 创建空统计（用于国家不存在时）
     */
    public static FaithStats empty() {
        return new FaithStats(0, 0, "无", 0, 0, 0, 0.0, 0.0, 0.0, 0.0);
    }

    /**
     * 获取信仰值百分比
     */
    public double faithPercentage() {
        return currentFaith / 100.0;
    }

    /**
     * 获取距离下一等级还需要多少信仰值
     */
    public int faithToNextLevel(int[] thresholds) {
        if (faithLevel >= thresholds.length) {
            return 0; // 已达满级
        }
        int nextThreshold = thresholds[faithLevel];
        return Math.max(0, nextThreshold - currentFaith);
    }
}