package dev.starcore.starcore.module.war;

import dev.starcore.starcore.module.nation.model.NationId;

import java.time.Instant;

public record WarSnapshot(
    NationId left,
    NationId right,
    Instant declaredAt
) {
}
