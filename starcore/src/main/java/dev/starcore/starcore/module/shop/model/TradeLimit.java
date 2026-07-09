package dev.starcore.starcore.module.shop.model;

/**
 * 交易限制
 */
public record TradeLimit(
    int dailyLimit,      // 每日交易限制
    int weeklyLimit,     // 每周交易限制
    int monthlyLimit,    // 每月交易限制
    boolean noLimit     // 是否无限制
) {
    public TradeLimit {
        if (dailyLimit < 0) dailyLimit = 0;
        if (weeklyLimit < 0) weeklyLimit = 0;
        if (monthlyLimit < 0) monthlyLimit = 0;
    }

    /**
     * 无限制
     */
    public static TradeLimit unlimited() {
        return new TradeLimit(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, true);
    }

    /**
     * 默认限制
     */
    public static TradeLimit defaultLimit() {
        return new TradeLimit(100, 500, 2000, false);
    }

    /**
     * VIP限制
     */
    public static TradeLimit vipLimit() {
        return new TradeLimit(500, 2000, 10000, false);
    }

    /**
     * 检查是否有每日限制
     */
    public boolean hasDailyLimit() {
        return !noLimit && dailyLimit < Integer.MAX_VALUE;
    }

    /**
     * 检查是否有每周限制
     */
    public boolean hasWeeklyLimit() {
        return !noLimit && weeklyLimit < Integer.MAX_VALUE;
    }

    /**
     * 检查是否有每月限制
     */
    public boolean hasMonthlyLimit() {
        return !noLimit && monthlyLimit < Integer.MAX_VALUE;
    }

    /**
     * 检查是否超过每日限制
     */
    public boolean exceedsDailyLimit(int currentCount) {
        return hasDailyLimit() && currentCount >= dailyLimit;
    }

    /**
     * 检查是否超过每周限制
     */
    public boolean exceedsWeeklyLimit(int currentCount) {
        return hasWeeklyLimit() && currentCount >= weeklyLimit;
    }

    /**
     * 检查是否超过每月限制
     */
    public boolean exceedsMonthlyLimit(int currentCount) {
        return hasMonthlyLimit() && currentCount >= monthlyLimit;
    }
}
