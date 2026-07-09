package dev.starcore.starcore.module.prosperity.event;

import dev.starcore.starcore.module.nation.model.NationId;

import java.time.Instant;
import java.util.Objects;

/**
 * 繁荣度达到峰值事件
 * 当国家繁荣度达到100%时触发
 */
public record ProsperityPeakEvent(
    NationId nationId,
    double prosperity,
    int level,
    Instant occurredAt
) {
    public ProsperityPeakEvent {
        Objects.requireNonNull(nationId, "nationId");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }

    public static ProsperityPeakEvent create(NationId nationId, double prosperity, int level) {
        return new ProsperityPeakEvent(nationId, prosperity, level, Instant.now());
    }
}