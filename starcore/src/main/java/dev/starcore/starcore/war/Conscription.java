package dev.starcore.starcore.war;

import dev.starcore.starcore.module.nation.model.NationId;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 征兵记录
 * 记录国家的征兵过程和进度
 */
public final class Conscription {
    private final UUID id;
    private final NationId nationId;
    private final UUID warId;
    private final int targetSoldiers;
    private int currentSoldiers;
    private final Instant startedAt;
    private final Instant completionAt;
    private boolean completed;

    public Conscription(
        UUID id,
        NationId nationId,
        UUID warId,
        int targetSoldiers,
        int currentSoldiers,
        Instant startedAt,
        Instant completionAt
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.nationId = Objects.requireNonNull(nationId, "nationId");
        this.warId = Objects.requireNonNull(warId, "warId");
        this.targetSoldiers = targetSoldiers;
        this.currentSoldiers = currentSoldiers;
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt");
        this.completionAt = Objects.requireNonNull(completionAt, "completionAt");
        this.completed = false;
    }

    public UUID id() {
        return id;
    }

    public NationId nationId() {
        return nationId;
    }

    public UUID warId() {
        return warId;
    }

    public int targetSoldiers() {
        return targetSoldiers;
    }

    public int currentSoldiers() {
        return currentSoldiers;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant completionAt() {
        return completionAt;
    }

    public boolean isCompleted() {
        return completed;
    }

    /**
     * 更新征兵进度
     */
    public void updateProgress(Instant now) {
        if (completed) {
            return;
        }

        // 计算进度
        Duration total = Duration.between(startedAt, completionAt);
        Duration elapsed = Duration.between(startedAt, now);

        if (elapsed.compareTo(total) >= 0) {
            // 完成
            this.currentSoldiers = targetSoldiers;
            this.completed = true;
        } else {
            // 线性增长
            double progress = (double) elapsed.toSeconds() / total.toSeconds();
            this.currentSoldiers = (int) (targetSoldiers * progress);
        }
    }

    /**
     * 获取完成百分比
     */
    public double progressPercentage() {
        if (targetSoldiers == 0) {
            return 100.0;
        }
        return (double) currentSoldiers / targetSoldiers * 100.0;
    }

    /**
     * 获取剩余时间
     */
    public Duration remainingTime(Instant now) {
        if (completed) {
            return Duration.ZERO;
        }

        Duration elapsed = Duration.between(startedAt, now);
        Duration total = Duration.between(startedAt, completionAt);

        Duration remaining = total.minus(elapsed);
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Conscription other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return String.format("Conscription{id=%s, nation=%s, progress=%d/%d (%.1f%%), completed=%s}",
            id, nationId, currentSoldiers, targetSoldiers, progressPercentage(), completed);
    }
}
