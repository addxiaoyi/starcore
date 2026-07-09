package dev.starcore.starcore.module.prosperity.event;

import dev.starcore.starcore.module.nation.model.NationId;

import java.time.Instant;
import java.util.Objects;

/**
 * 繁荣度危机事件
 * 当国家繁荣度过低时触发
 */
public record ProsperityCrisisEvent(
    NationId nationId,
    double prosperity,
    int level,
    Instant occurredAt
) {
    public ProsperityCrisisEvent {
        Objects.requireNonNull(nationId, "nationId");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }

    public static ProsperityCrisisEvent create(NationId nationId, double prosperity, int level) {
        return new ProsperityCrisisEvent(nationId, prosperity, level, Instant.now());
    }

    /**
     * 是否为严重危机（繁荣度低于10%）
     */
    public boolean isSevere() {
        return prosperity < 10.0;
    }
}