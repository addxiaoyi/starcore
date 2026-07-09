package dev.starcore.starcore.social.simulation;

/**
 * 排行榜周期枚举
 * 定义不同时间维度的排行榜
 */
public enum LeaderboardPeriod {
    /**
     * 日榜 - 每天0点重置
     */
    DAILY("日榜", 0),

    /**
     * 周榜 - 每周一0点重置
     */
    WEEKLY("周榜", 1),

    /**
     * 月榜 - 每月1号0点重置
     */
    MONTHLY("月榜", 2),

    /**
     * 总榜 - 永不重置
     */
    ALLTIME("总榜", 3);

    private final String displayName;
    private final int index;

    LeaderboardPeriod(String displayName, int index) {
        this.displayName = displayName;
        this.index = index;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getIndex() {
        return index;
    }

    /**
     * 根据索引获取周期
     */
    public static LeaderboardPeriod fromIndex(int index) {
        for (LeaderboardPeriod period : values()) {
            if (period.index == index) {
                return period;
            }
        }
        return ALLTIME;
    }
}
