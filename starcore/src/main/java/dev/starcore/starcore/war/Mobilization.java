package dev.starcore.starcore.war;

import dev.starcore.starcore.module.nation.model.NationId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 动员令
 * 代表国家为战争做的动员准备
 */
public final class Mobilization {
    private final UUID id;
    private final NationId nationId;
    private final UUID warId;
    private final MobilizationLevel level;
    private final Instant declaredAt;
    private boolean active;
    private Instant cancelledAt;

    public Mobilization(
        UUID id,
        NationId nationId,
        UUID warId,
        MobilizationLevel level,
        Instant declaredAt
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.nationId = Objects.requireNonNull(nationId, "nationId");
        this.warId = Objects.requireNonNull(warId, "warId");
        this.level = Objects.requireNonNull(level, "level");
        this.declaredAt = Objects.requireNonNull(declaredAt, "declaredAt");
        this.active = true;
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

    public MobilizationLevel level() {
        return level;
    }

    public Instant declaredAt() {
        return declaredAt;
    }

    public boolean isActive() {
        return active;
    }

    public Instant cancelledAt() {
        return cancelledAt;
    }

    /**
     * 取消动员令
     */
    public void cancel() {
        this.active = false;
        this.cancelledAt = Instant.now();
    }

    /**
     * 获取动员持续时间（秒）
     */
    public long durationSeconds() {
        Instant end = cancelledAt != null ? cancelledAt : Instant.now();
        return end.getEpochSecond() - declaredAt.getEpochSecond();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Mobilization other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return String.format("Mobilization{id=%s, nation=%s, level=%s, active=%s}",
            id, nationId, level, active);
    }
}
