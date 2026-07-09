package dev.starcore.starcore.module.policy.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public record PolicyDefinition(
    String key,
    String displayName,
    PolicyCategory category,
    Set<String> prerequisiteKeys,
    BigDecimal treasuryCost,
    long durationSeconds,
    long cooldownSeconds,
    Set<String> conflictKeys,
    List<PolicyEffect> effects
) {
    public PolicyDefinition {
        key = normalizeKey(key);
        displayName = Objects.requireNonNull(displayName, "displayName").trim();
        if (displayName.isEmpty()) {
            throw new IllegalArgumentException("displayName cannot be empty");
        }
        Objects.requireNonNull(category, "category");
        prerequisiteKeys = Set.copyOf(Objects.requireNonNull(prerequisiteKeys, "prerequisiteKeys").stream()
            .map(PolicyDefinition::normalizeKey)
            .toList());
        treasuryCost = Objects.requireNonNull(treasuryCost, "treasuryCost");
        if (treasuryCost.signum() < 0) {
            throw new IllegalArgumentException("treasuryCost cannot be negative");
        }
        // -1 表示永久政策
        if (durationSeconds < 0 && durationSeconds != -1) {
            throw new IllegalArgumentException("durationSeconds cannot be negative (use -1 for permanent policies)");
        }
        if (cooldownSeconds < 0) {
            throw new IllegalArgumentException("cooldownSeconds cannot be negative");
        }
        conflictKeys = Set.copyOf(Objects.requireNonNull(conflictKeys, "conflictKeys").stream()
            .map(PolicyDefinition::normalizeKey)
            .toList());
        effects = List.copyOf(Objects.requireNonNull(effects, "effects"));
    }

    public boolean conflictsWith(String otherPolicyKey) {
        return conflictKeys.contains(normalizeKey(otherPolicyKey));
    }

    private static String normalizeKey(String key) {
        String normalized = Objects.requireNonNull(key, "key").trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("key cannot be empty");
        }
        return normalized;
    }
}
