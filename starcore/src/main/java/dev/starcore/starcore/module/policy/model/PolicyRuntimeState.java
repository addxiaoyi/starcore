package dev.starcore.starcore.module.policy.model;

import java.time.Instant;
import java.util.Objects;

public record PolicyRuntimeState(
    String policyKey,
    Instant activatedAt,
    Instant expiresAt,
    Instant cooldownEndsAt
) {
    public PolicyRuntimeState {
        policyKey = Objects.requireNonNull(policyKey, "policyKey").trim().toLowerCase();
        if (policyKey.isEmpty()) {
            throw new IllegalArgumentException("policyKey cannot be empty");
        }
        Objects.requireNonNull(activatedAt, "activatedAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(cooldownEndsAt, "cooldownEndsAt");
    }

    public boolean isExpiredAt(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public boolean isCoolingDownAt(Instant now) {
        return cooldownEndsAt.isAfter(now);
    }
}
