package dev.starcore.starcore.war;

import dev.starcore.starcore.module.nation.model.NationId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 情报记录
 * 记录间谍收集到的情报信息
 */
public final class Intelligence {
    private final UUID id;
    private final UUID spyId;
    private final NationId sourceNation;    // 情报来源国
    private final NationId targetNation;    // 情报目标国
    private final IntelligenceType type;
    private final String content;
    private final int reliability;          // 可靠度 (0-100)
    private final Instant collectedAt;
    private final Instant expiresAt;
    private boolean verified;               // 是否已验证

    public Intelligence(
        UUID id,
        UUID spyId,
        NationId sourceNation,
        NationId targetNation,
        IntelligenceType type,
        String content,
        int reliability,
        Instant collectedAt,
        Instant expiresAt
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.spyId = Objects.requireNonNull(spyId, "spyId");
        this.sourceNation = Objects.requireNonNull(sourceNation, "sourceNation");
        this.targetNation = Objects.requireNonNull(targetNation, "targetNation");
        this.type = Objects.requireNonNull(type, "type");
        this.content = Objects.requireNonNull(content, "content");
        this.reliability = Math.max(0, Math.min(100, reliability));
        this.collectedAt = Objects.requireNonNull(collectedAt, "collectedAt");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        this.verified = false;
    }

    public UUID id() {
        return id;
    }

    public UUID spyId() {
        return spyId;
    }

    public NationId sourceNation() {
        return sourceNation;
    }

    public NationId targetNation() {
        return targetNation;
    }

    public IntelligenceType type() {
        return type;
    }

    public String content() {
        return content;
    }

    public int reliability() {
        return reliability;
    }

    public Instant collectedAt() {
        return collectedAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public boolean isVerified() {
        return verified;
    }

    /**
     * 验证情报
     */
    public void verify() {
        this.verified = true;
    }

    /**
     * 是否过期
     */
    public boolean isExpired(Instant now) {
        return now.isAfter(expiresAt);
    }

    /**
     * 是否有效
     */
    public boolean isValid(Instant now) {
        return !isExpired(now) && reliability >= 50;
    }

    /**
     * 获取情报价值
     */
    public int strategicValue() {
        int value = type.baseValue();
        value = (int) (value * (reliability / 100.0));
        if (verified) {
            value = (int) (value * 1.5);
        }
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Intelligence other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return String.format("Intelligence{type=%s, reliability=%d%%, verified=%s, expires=%s}",
            type, reliability, verified, expiresAt);
    }
}
