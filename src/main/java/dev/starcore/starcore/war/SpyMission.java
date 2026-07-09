package dev.starcore.starcore.war;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 间谍任务
 */
public enum SpyMission {
    /**
     * 刺探军情 - 获取敌军数量和位置
     */
    SCOUT_MILITARY("刺探军情", 3, 24, IntelligenceType.MILITARY),

    /**
     * 窃取情报 - 获取战争计划
     */
    STEAL_PLANS("窃取情报", 5, 48, IntelligenceType.STRATEGIC),

    /**
     * 破坏补给线 - 降低敌方补给效率
     */
    SABOTAGE_SUPPLY("破坏补给线", 4, 12, IntelligenceType.TACTICAL),

    /**
     * 刺杀将领 - 削弱敌军指挥能力
     */
    ASSASSINATE("刺杀将领", 8, 72, IntelligenceType.TACTICAL),

    /**
     * 策反敌军 - 降低敌军士气
     */
    SUBVERT("策反敌军", 6, 36, IntelligenceType.POLITICAL),

    /**
     * 散布谣言 - 制造混乱
     */
    SPREAD_RUMOR("散布谣言", 2, 6, IntelligenceType.POLITICAL),

    /**
     * 监视敌国 - 持续获取情报
     */
    SURVEILLANCE("监视敌国", 3, 168, IntelligenceType.STRATEGIC);

    private final String displayName;
    private final int difficulty;       // 难度 (1-10)
    private final int durationHours;    // 任务时长（小时）
    private final IntelligenceType type;

    SpyMission(String displayName, int difficulty, int durationHours, IntelligenceType type) {
        this.displayName = displayName;
        this.difficulty = difficulty;
        this.durationHours = durationHours;
        this.type = type;
    }

    public String displayName() {
        return displayName;
    }

    public int difficulty() {
        return difficulty;
    }

    public int durationHours() {
        return durationHours;
    }

    public IntelligenceType type() {
        return type;
    }

    /**
     * 计算任务完成时间
     */
    public Instant calculateCompletionTime(Instant startTime) {
        return startTime.plusSeconds(durationHours * 3600L);
    }
}
