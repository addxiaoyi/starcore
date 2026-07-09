package dev.starcore.starcore.module.army.espionage.model;

import java.time.Instant;
import java.util.UUID;

/**
 * 间谍行动数据模型
 */
public final class EspionageOperation {
    private final UUID id;
    private final UUID spyId;
    private final UUID sourceNationId;       // 发起国
    private final String sourceNationName;
    private final UUID targetNationId;        // 目标国
    private final String targetNationName;
    private final OperationType type;
    private final int difficulty;
    private final double cost;
    private final Instant startTime;
    private final long durationTicks;
    private final boolean detected;

    // 行动结果
    private OperationStatus status;
    private Instant endTime;
    private boolean success;
    private String report;
    private Object reward;  // 行动收益

    public EspionageOperation(
            UUID id, UUID spyId,
            UUID sourceNationId, String sourceNationName,
            UUID targetNationId, String targetNationName,
            OperationType type, int difficulty, double cost,
            Instant startTime, long durationTicks, boolean detected,
            OperationStatus status, Instant endTime, boolean success,
            String report, Object reward) {
        this.id = id;
        this.spyId = spyId;
        this.sourceNationId = sourceNationId;
        this.sourceNationName = sourceNationName;
        this.targetNationId = targetNationId;
        this.targetNationName = targetNationName;
        this.type = type;
        this.difficulty = difficulty;
        this.cost = cost;
        this.startTime = startTime;
        this.durationTicks = durationTicks;
        this.detected = detected;
        this.status = status;
        this.endTime = endTime;
        this.success = success;
        this.report = report;
        this.reward = reward;
    }

    public static EspionageOperation start(
            UUID spyId,
            UUID sourceNationId, String sourceNationName,
            UUID targetNationId, String targetNationName,
            OperationType type, double cost, long durationTicks) {
        return new EspionageOperation(
            UUID.randomUUID(),
            spyId,
            sourceNationId, sourceNationName,
            targetNationId, targetNationName,
            type, type.difficulty(), cost,
            Instant.now(), durationTicks, false,
            OperationStatus.IN_PROGRESS, null, false, null, null
        );
    }

    public UUID id() {
        return id;
    }

    public UUID spyId() {
        return spyId;
    }

    public UUID sourceNationId() {
        return sourceNationId;
    }

    public String sourceNationName() {
        return sourceNationName;
    }

    public UUID targetNationId() {
        return targetNationId;
    }

    public String targetNationName() {
        return targetNationName;
    }

    public OperationType type() {
        return type;
    }

    public int difficulty() {
        return difficulty;
    }

    public double cost() {
        return cost;
    }

    public Instant startTime() {
        return startTime;
    }

    public long durationTicks() {
        return durationTicks;
    }

    public boolean detected() {
        return detected;
    }

    public OperationStatus status() {
        return status;
    }

    public void setStatus(OperationStatus status) {
        this.status = status;
    }

    public Instant endTime() {
        return endTime;
    }

    public void setEndTime(Instant endTime) {
        this.endTime = endTime;
    }

    public boolean success() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String report() {
        return report;
    }

    public void setReport(String report) {
        this.report = report;
    }

    public Object reward() {
        return reward;
    }

    public void setReward(Object reward) {
        this.reward = reward;
    }

    /**
     * 完成行动
     */
    public void complete(boolean success, String report, Object reward) {
        this.status = OperationStatus.COMPLETED;
        this.endTime = Instant.now();
        this.success = success;
        this.report = report;
        this.reward = reward;
    }

    /**
     * 行动失败/暴露
     */
    public void fail(String reason) {
        this.status = OperationStatus.FAILED;
        this.endTime = Instant.now();
        this.success = false;
        this.report = reason;
    }

    /**
     * 是否已完成
     */
    public boolean isCompleted() {
        return status == OperationStatus.COMPLETED || status == OperationStatus.FAILED;
    }

    /**
     * 获取剩余时间（刻）
     */
    public long getRemainingTicks(Instant now) {
        if (isCompleted()) {
            return 0;
        }
        long elapsed = (now.toEpochMilli() - startTime.toEpochMilli()) / 50; // 毫秒转刻
        return Math.max(0, durationTicks - elapsed);
    }
}
