package dev.starcore.starcore.module.army.fatigue.model;

import java.util.UUID;

/**
 * 玩家疲劳度数据模型
 * 记录玩家的各类疲劳状态
 */
public final class PlayerFatigue {
    private final UUID playerId;
    private int physicalFatigue;      // 体力疲劳 (0-100)
    private int mentalFatigue;        // 精神疲劳 (0-100)
    private int combatFatigue;        // 战斗疲劳 (0-100)
    private int travelFatigue;        // 旅行疲劳 (0-100)
    private long lastActivityTime;    // 最后活动时间戳
    private long totalPlayTime;       // 累计在线时间（秒）
    private long lastRestTime;        // 上次充分休息时间戳

    public PlayerFatigue(UUID playerId) {
        this.playerId = playerId;
        this.physicalFatigue = 0;
        this.mentalFatigue = 0;
        this.combatFatigue = 0;
        this.travelFatigue = 0;
        this.lastActivityTime = System.currentTimeMillis();
        this.totalPlayTime = 0;
        this.lastRestTime = System.currentTimeMillis();
    }

    public PlayerFatigue(UUID playerId, int physicalFatigue, int mentalFatigue,
                        int combatFatigue, int travelFatigue, long lastActivityTime,
                        long totalPlayTime, long lastRestTime) {
        this.playerId = playerId;
        this.physicalFatigue = clamp(physicalFatigue, 0, 100);
        this.mentalFatigue = clamp(mentalFatigue, 0, 100);
        this.combatFatigue = clamp(combatFatigue, 0, 100);
        this.travelFatigue = clamp(travelFatigue, 0, 100);
        this.lastActivityTime = lastActivityTime;
        this.totalPlayTime = totalPlayTime;
        this.lastRestTime = lastRestTime;
    }

    // ==================== Getters ====================

    public UUID playerId() {
        return playerId;
    }

    public int physicalFatigue() {
        return physicalFatigue;
    }

    public int mentalFatigue() {
        return mentalFatigue;
    }

    public int combatFatigue() {
        return combatFatigue;
    }

    public int travelFatigue() {
        return travelFatigue;
    }

    public long lastActivityTime() {
        return lastActivityTime;
    }

    public long totalPlayTime() {
        return totalPlayTime;
    }

    public long lastRestTime() {
        return lastRestTime;
    }

    /**
     * 获取总体疲劳度（加权平均）
     */
    public int overallFatigue() {
        return (physicalFatigue * 30 + mentalFatigue * 25 + combatFatigue * 30 + travelFatigue * 15) / 100;
    }

    /**
     * 获取疲劳等级
     */
    public FatigueLevel level() {
        int overall = overallFatigue();
        if (overall < 20) return FatigueLevel.FRESH;
        if (overall < 40) return FatigueLevel.NORMAL;
        if (overall < 60) return FatigueLevel.TIRED;
        if (overall < 75) return FatigueLevel.SEVERELY_FATIGUED;
        if (overall < 85) return FatigueLevel.EXHAUSTED;
        return FatigueLevel.CRITICAL;
    }

    // ==================== Fatigue Modifications ====================

    /**
     * 增加体力疲劳
     */
    public void addPhysicalFatigue(int amount) {
        this.physicalFatigue = clamp(this.physicalFatigue + amount, 0, 100);
        this.lastActivityTime = System.currentTimeMillis();
    }

    /**
     * 增加精神疲劳
     */
    public void addMentalFatigue(int amount) {
        this.mentalFatigue = clamp(this.mentalFatigue + amount, 0, 100);
        this.lastActivityTime = System.currentTimeMillis();
    }

    /**
     * 增加战斗疲劳
     */
    public void addCombatFatigue(int amount) {
        this.combatFatigue = clamp(this.combatFatigue + amount, 0, 100);
        this.lastActivityTime = System.currentTimeMillis();
    }

    /**
     * 增加旅行疲劳
     */
    public void addTravelFatigue(int amount) {
        this.travelFatigue = clamp(this.travelFatigue + amount, 0, 100);
        this.lastActivityTime = System.currentTimeMillis();
    }

    /**
     * 恢复所有疲劳
     */
    public void rest() {
        int recoveryRate = 100;
        this.physicalFatigue = clamp(this.physicalFatigue - recoveryRate, 0, 100);
        this.mentalFatigue = clamp(this.mentalFatigue - recoveryRate, 0, 100);
        this.combatFatigue = clamp(this.combatFatigue - recoveryRate, 0, 100);
        this.travelFatigue = clamp(this.travelFatigue - recoveryRate, 0, 100);
        this.lastRestTime = System.currentTimeMillis();
    }

    /**
     * 重置所有疲劳到0
     */
    public void resetAll() {
        this.physicalFatigue = 0;
        this.mentalFatigue = 0;
        this.combatFatigue = 0;
        this.travelFatigue = 0;
        this.lastRestTime = System.currentTimeMillis();
    }

    /**
     * 恢复指定类型的疲劳
     */
    public void rest(FatigueType type, int amount) {
        switch (type) {
            case PHYSICAL -> this.physicalFatigue = clamp(this.physicalFatigue - amount, 0, 100);
            case MENTAL -> this.mentalFatigue = clamp(this.mentalFatigue - amount, 0, 100);
            case COMBAT -> this.combatFatigue = clamp(this.combatFatigue - amount, 0, 100);
            case TRAVEL -> this.travelFatigue = clamp(this.travelFatigue - amount, 0, 100);
        }
        this.lastRestTime = System.currentTimeMillis();
    }

    /**
     * 休息一段时间（秒）
     */
    public void restForSeconds(int seconds, int recoveryPerMinute) {
        int recovery = (seconds * recoveryPerMinute) / 60;
        rest(recovery);
    }

    /**
     * 恢复指定量
     */
    public void rest(int amount) {
        this.physicalFatigue = clamp(this.physicalFatigue - amount, 0, 100);
        this.mentalFatigue = clamp(this.mentalFatigue - amount, 0, 100);
        this.combatFatigue = clamp(this.combatFatigue - amount, 0, 100);
        this.travelFatigue = clamp(this.travelFatigue - amount, 0, 100);
        this.lastRestTime = System.currentTimeMillis();
    }

    /**
     * 更新累计在线时间
     */
    public void updatePlayTime(long seconds) {
        this.totalPlayTime += seconds;
        this.lastActivityTime = System.currentTimeMillis();
    }

    // ==================== Effects ====================

    /**
     * 获取移动速度惩罚倍率
     */
    public double getSpeedPenalty() {
        int overall = overallFatigue();
        if (overall < 30) return 1.0;
        if (overall < 50) return 0.95;
        if (overall < 70) return 0.85;
        if (overall < 85) return 0.70;
        return 0.50;
    }

    /**
     * 获取攻击力惩罚倍率
     */
    public double getAttackPenalty() {
        int overall = overallFatigue();
        if (overall < 40) return 1.0;
        if (overall < 60) return 0.90;
        if (overall < 75) return 0.75;
        if (overall < 90) return 0.55;
        return 0.35;
    }

    /**
     * 获取防御力惩罚倍率
     */
    public double getDefensePenalty() {
        int overall = overallFatigue();
        if (overall < 30) return 1.0;
        if (overall < 50) return 0.95;
        if (overall < 65) return 0.85;
        if (overall < 80) return 0.70;
        return 0.50;
    }

    /**
     * 获取经验获取惩罚倍率
     */
    public double getExpPenalty() {
        int overall = overallFatigue();
        if (overall < 50) return 1.0;
        if (overall < 70) return 0.80;
        if (overall < 85) return 0.60;
        return 0.40;
    }

    /**
     * 是否达到临界状态
     */
    public boolean isCritical() {
        return overallFatigue() >= 90 || physicalFatigue >= 95 || mentalFatigue >= 95;
    }

    /**
     * 是否需要强制休息
     */
    public boolean needsForcedRest() {
        return overallFatigue() >= 95;
    }

    // ==================== Utility ====================

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PlayerFatigue other)) return false;
        return playerId.equals(other.playerId);
    }

    @Override
    public int hashCode() {
        return playerId.hashCode();
    }

    @Override
    public String toString() {
        return String.format("PlayerFatigue{uuid=%s, physical=%d, mental=%d, combat=%d, travel=%d, overall=%d, level=%s}",
            playerId, physicalFatigue, mentalFatigue, combatFatigue, travelFatigue, overallFatigue(), level());
    }
}