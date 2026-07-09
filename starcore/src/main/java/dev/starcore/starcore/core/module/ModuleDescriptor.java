package dev.starcore.starcore.core.module;

import java.time.Instant;

public record ModuleDescriptor(
    ModuleMetadata metadata,
    ModuleStatus status,
    Instant registeredAt,
    Instant lastChangedAt,
    String failureReason
) {
}
