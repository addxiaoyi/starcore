package dev.starcore.starcore.ranking;

/**
 * 排行榜时间周期
 * 基于ajLeaderboards的增量Delta设计
 */
public enum RankPeriod {
    /**
     * 小时榜 - 每小时重置
     */
    HOURLY("小时榜", 3600),

    /**
     * 日榜 - 每天0点重置
     */
    DAILY("日榜", 86400),

    /**
     * 周榜 - 每周一0点重置
     */
    WEEKLY("周榜", 604800),

    /**
     * 月榜 - 每月1号0点重置
     */
    MONTHLY("月榜", 2592000),

    /**
     * 年榜 - 每年1月1号重置
     */
    YEARLY("年榜", 31536000),

    /**
     * 全时榜 - 永不重置
     */
    ALLTIME("全时榜", -1);

    private final String displayName;
    private final long seconds; // -1表示永不重置

    RankPeriod(String displayName, long seconds) {
        this.displayName = displayName;
        this.seconds = seconds;
    }

    public String getDisplayName() {
        return displayName;
    }

    public long getSeconds() {
        return seconds;
    }

    public boolean shouldReset() {
        return seconds > 0;
    }
}
