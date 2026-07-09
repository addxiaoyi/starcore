package dev.starcore.starcore.ranking;

import java.util.UUID;

/**
 * 玩家排行榜数据
 */
public class PlayerRankingData {
    private final UUID playerId;

    // 各时间周期的数据
    private long allTimeValue;
    private long hourlyValue;
    private long dailyValue;
    private long weeklyValue;
    private long monthlyValue;
    private long yearlyValue;

    // 排名
    private int allTimePosition;
    private int hourlyPosition;
    private int dailyPosition;
    private int weeklyPosition;
    private int monthlyPosition;
    private int yearlyPosition;

    public PlayerRankingData(UUID playerId) {
        this.playerId = playerId;
    }

    // ==================== Getter/Setter ====================

    public UUID getPlayerId() {
        return playerId;
    }

    public long getAllTimeValue() {
        return allTimeValue;
    }

    public void setAllTimeValue(long allTimeValue) {
        this.allTimeValue = allTimeValue;
    }

    public long getDailyValue() {
        return dailyValue;
    }

    public void setDailyValue(long dailyValue) {
        this.dailyValue = dailyValue;
    }

    public long getWeeklyValue() {
        return weeklyValue;
    }

    public void setWeeklyValue(long weeklyValue) {
        this.weeklyValue = weeklyValue;
    }

    public long getMonthlyValue() {
        return monthlyValue;
    }

    public void setMonthlyValue(long monthlyValue) {
        this.monthlyValue = monthlyValue;
    }

    public long getHourlyValue() {
        return hourlyValue;
    }

    public void setHourlyValue(long hourlyValue) {
        this.hourlyValue = hourlyValue;
    }

    public long getYearlyValue() {
        return yearlyValue;
    }

    public void setYearlyValue(long yearlyValue) {
        this.yearlyValue = yearlyValue;
    }

    public int getAllTimePosition() {
        return allTimePosition;
    }

    public void setAllTimePosition(int allTimePosition) {
        this.allTimePosition = allTimePosition;
    }

    public int getDailyPosition() {
        return dailyPosition;
    }

    public void setDailyPosition(int dailyPosition) {
        this.dailyPosition = dailyPosition;
    }

    public int getWeeklyPosition() {
        return weeklyPosition;
    }

    public void setWeeklyPosition(int weeklyPosition) {
        this.weeklyPosition = weeklyPosition;
    }

    public int getMonthlyPosition() {
        return monthlyPosition;
    }

    public void setMonthlyPosition(int monthlyPosition) {
        this.monthlyPosition = monthlyPosition;
    }

    public int getHourlyPosition() {
        return hourlyPosition;
    }

    public void setHourlyPosition(int hourlyPosition) {
        this.hourlyPosition = hourlyPosition;
    }

    public int getYearlyPosition() {
        return yearlyPosition;
    }

    public void setYearlyPosition(int yearlyPosition) {
        this.yearlyPosition = yearlyPosition;
    }

    /**
     * 根据周期获取数值
     */
    public long getValue(RankPeriod period) {
        return switch (period) {
            case ALLTIME -> allTimeValue;
            case HOURLY -> hourlyValue;
            case DAILY -> dailyValue;
            case WEEKLY -> weeklyValue;
            case MONTHLY -> monthlyValue;
            case YEARLY -> yearlyValue;
        };
    }

    /**
     * 根据周期获取排名
     */
    public int getPosition(RankPeriod period) {
        return switch (period) {
            case ALLTIME -> allTimePosition;
            case HOURLY -> hourlyPosition;
            case DAILY -> dailyPosition;
            case WEEKLY -> weeklyPosition;
            case MONTHLY -> monthlyPosition;
            case YEARLY -> yearlyPosition;
        };
    }
}
