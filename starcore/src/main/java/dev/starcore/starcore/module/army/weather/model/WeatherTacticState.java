package dev.starcore.starcore.module.army.weather.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 天气战术状态
 * 记录一个战术实例的执行状态
 */
public final class WeatherTacticState {

    public enum Status {
        /** 就绪 */
        READY,
        /** 执行中 */
        ACTIVE,
        /** 完成 */
        COMPLETED,
        /** 失败 */
        FAILED,
        /** 冷却中 */
        COOLDOWN
    }

    private final UUID tacticId;
    private final UUID nationId;
    private final UUID armyId;
    private final WeatherTacticType tacticType;
    private Status status;
    private final Instant startTime;
    private Instant endTime;
    private double successRate;
    private double moraleModifier;
    private double defenseModifier;
    private double attackModifier;
    private int cooldownSeconds;

    public WeatherTacticState(
        UUID tacticId,
        UUID nationId,
        UUID armyId,
        WeatherTacticType tacticType
    ) {
        this.tacticId = Objects.requireNonNull(tacticId, "tacticId");
        this.nationId = Objects.requireNonNull(nationId, "nationId");
        this.armyId = Objects.requireNonNull(armyId, "armyId");
        this.tacticType = Objects.requireNonNull(tacticType, "tacticType");
        this.status = Status.READY;
        this.startTime = Instant.now();
        this.successRate = tacticType.getBaseSuccessRate();
        this.cooldownSeconds = tacticType.getCooldownSeconds();

        // 根据战术类型设置属性加成
        initializeModifiers();
    }

    private void initializeModifiers() {
        switch (tacticType) {
            case AMBUSH -> {
                this.attackModifier = 1.5;
                this.defenseModifier = 0.5;
                this.moraleModifier = -5;
            }
            case DEFENSIVE -> {
                this.attackModifier = 0.7;
                this.defenseModifier = 2.0;
                this.moraleModifier = 10;
            }
            case ATTRITION -> {
                this.attackModifier = 1.2;
                this.defenseModifier = 1.0;
                this.moraleModifier = 0;
            }
            case RETREAT -> {
                this.attackModifier = 0.3;
                this.defenseModifier = 0.8;
                this.moraleModifier = 5;
            }
            case PURSUIT -> {
                this.attackModifier = 1.3;
                this.defenseModifier = 0.6;
                this.moraleModifier = 15;
            }
            case REINFORCE -> {
                this.attackModifier = 1.2;
                this.defenseModifier = 1.3;
                this.moraleModifier = 10;
            }
        }
    }

    // Getters
    public UUID tacticId() {
        return tacticId;
    }

    public UUID nationId() {
        return nationId;
    }

    public UUID armyId() {
        return armyId;
    }

    public WeatherTacticType tacticType() {
        return tacticType;
    }

    public Status status() {
        return status;
    }

    public Instant startTime() {
        return startTime;
    }

    public Instant endTime() {
        return endTime;
    }

    public double successRate() {
        return successRate;
    }

    public double moraleModifier() {
        return moraleModifier;
    }

    public double defenseModifier() {
        return defenseModifier;
    }

    public double attackModifier() {
        return attackModifier;
    }

    public int cooldownSeconds() {
        return cooldownSeconds;
    }

    // Setters / Modifiers
    public void setStatus(Status status) {
        this.status = status;
    }

    public void setEndTime(Instant endTime) {
        this.endTime = endTime;
    }

    public void setSuccessRate(double successRate) {
        this.successRate = Math.max(0, Math.min(1, successRate));
    }

    /**
     * 根据天气条件调整成功率
     */
    public void adjustForWeather(dev.starcore.starcore.module.weather.model.WeatherType weather) {
        var effectiveTypes = tacticType.getEffectiveWeather();
        boolean isEffective = false;

        for (var type : effectiveTypes) {
            if (type == weather) {
                isEffective = true;
                break;
            }
        }

        if (isEffective) {
            this.successRate = Math.min(1.0, this.successRate * 1.3);
        } else {
            this.successRate = Math.max(0, this.successRate * 0.6);
        }
    }

    /**
     * 根据敌军状态调整成功率
     */
    public void adjustForEnemy(double enemyMorale, double enemyHealth) {
        // 敌军士气低更容易成功
        if (enemyMorale < 30) {
            this.successRate = Math.min(1.0, this.successRate * 1.2);
        }

        // 敌军生命值低更容易成功
        if (enemyHealth < 50) {
            this.successRate = Math.min(1.0, this.successRate * 1.1);
        }
    }

    /**
     * 激活战术
     */
    public void activate() {
        this.status = Status.ACTIVE;
    }

    /**
     * 完成战术
     */
    public void complete() {
        this.status = Status.COMPLETED;
        this.endTime = Instant.now();
    }

    /**
     * 标记战术失败
     */
    public void fail() {
        this.status = Status.FAILED;
        this.endTime = Instant.now();
    }

    /**
     * 开始冷却
     */
    public void startCooldown() {
        this.status = Status.COOLDOWN;
    }

    /**
     * 检查战术是否在冷却中
     */
    public boolean isOnCooldown() {
        if (status != Status.COOLDOWN) {
            return false;
        }
        long elapsed = (System.currentTimeMillis() - (endTime != null ? endTime.toEpochMilli() : startTime.toEpochMilli())) / 1000;
        return elapsed < cooldownSeconds;
    }

    /**
     * 获取剩余冷却时间（秒）
     */
    public int getRemainingCooldown() {
        if (status != Status.COOLDOWN && status != Status.COMPLETED) {
            return 0;
        }
        long elapsed = (System.currentTimeMillis() - (endTime != null ? endTime.toEpochMilli() : startTime.toEpochMilli())) / 1000;
        return Math.max(0, cooldownSeconds - (int) elapsed);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WeatherTacticState other)) return false;
        return tacticId.equals(other.tacticId);
    }

    @Override
    public int hashCode() {
        return tacticId.hashCode();
    }

    @Override
    public String toString() {
        return String.format("WeatherTacticState{id=%s, type=%s, status=%s, successRate=%.1f%%}",
            tacticId, tacticType, status, successRate * 100);
    }
}
