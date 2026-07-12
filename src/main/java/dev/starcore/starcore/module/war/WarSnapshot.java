package dev.starcore.starcore.module.war;

import dev.starcore.starcore.module.nation.model.NationId;

import java.time.Duration;
import java.time.Instant;

public record WarSnapshot(
    NationId left,
    NationId right,
    Instant declaredAt,
    Instant endedAt
) {
    public boolean ended() {
        return endedAt != null;
    }

    public Duration duration() {
        Instant end = endedAt != null ? endedAt : Instant.now();
        return Duration.between(declaredAt, end);
    }
}
