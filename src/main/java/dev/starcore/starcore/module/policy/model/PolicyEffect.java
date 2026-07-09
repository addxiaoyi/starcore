package dev.starcore.starcore.module.policy.model;

import java.util.Objects;

public record PolicyEffect(
    String key,
    PolicyEffectScope scope,
    double modifier,
    String description
) {
    public PolicyEffect {
        key = normalizeKey(key);
        Objects.requireNonNull(scope, "scope");
        description = Objects.requireNonNull(description, "description").trim();
        if (description.isEmpty()) {
            throw new IllegalArgumentException("description cannot be empty");
        }
    }

    private static String normalizeKey(String key) {
        String normalized = Objects.requireNonNull(key, "key").trim().toLowerCase();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("key cannot be empty");
        }
        return normalized;
    }
}
