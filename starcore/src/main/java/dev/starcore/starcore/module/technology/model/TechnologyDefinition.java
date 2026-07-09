package dev.starcore.starcore.module.technology.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a technology definition loaded from configuration.
 */
public record TechnologyDefinition(
    String key,
    String displayName,
    String description,
    String era,
    String branch,
    BigDecimal treasuryCost,
    int researchTimeSeconds,
    List<String> prerequisites,
    List<String> mutuallyExclusive,
    List<TechnologyEffect> effects,
    Map<String, Long> resourceCosts
) {
    public TechnologyDefinition {
        key = normalizeKey(key);
        displayName = Objects.requireNonNull(displayName, "displayName").trim();
        description = description != null ? description.trim() : "";
        era = era != null ? era.trim().toLowerCase(Locale.ROOT) : "";
        branch = branch != null ? branch.trim().toLowerCase(Locale.ROOT) : "";
        treasuryCost = treasuryCost != null ? treasuryCost : BigDecimal.ZERO;
        if (treasuryCost.signum() < 0) {
            throw new IllegalArgumentException("treasuryCost cannot be negative");
        }
        if (researchTimeSeconds < 0) {
            throw new IllegalArgumentException("researchTimeSeconds cannot be negative");
        }
        prerequisites = prerequisites != null ? List.copyOf(prerequisites) : List.of();
        prerequisites = prerequisites.stream().map(TechnologyDefinition::normalizeKey).toList();
        mutuallyExclusive = mutuallyExclusive != null ? List.copyOf(mutuallyExclusive) : List.of();
        mutuallyExclusive = mutuallyExclusive.stream().map(TechnologyDefinition::normalizeKey).toList();
        effects = effects != null ? List.copyOf(effects) : List.of();
        resourceCosts = resourceCosts != null ? Map.copyOf(resourceCosts) : Map.of();
    }

    /**
     * Secondary constructor with default empty resourceCosts for backward compatibility.
     */
    public TechnologyDefinition(
        String key,
        String displayName,
        String description,
        String era,
        String branch,
        BigDecimal treasuryCost,
        int researchTimeSeconds,
        List<String> prerequisites,
        List<String> mutuallyExclusive,
        List<TechnologyEffect> effects
    ) {
        this(key, displayName, description, era, branch, treasuryCost, researchTimeSeconds,
             prerequisites, mutuallyExclusive, effects, Map.of());
    }

    /**
     * Check if this technology has any prerequisites.
     */
    public boolean hasPrerequisites() {
        return !prerequisites.isEmpty();
    }

    /**
     * Check if this technology is mutually exclusive with another.
     */
    public boolean isMutuallyExclusiveWith(String otherKey) {
        return mutuallyExclusive.contains(normalizeKey(otherKey));
    }

    /**
     * Get all unlocked effects for this technology.
     */
    public List<TechnologyEffect> unlockedEffects() {
        return effects.stream()
            .filter(e -> !e.isUnlockEffect())
            .toList();
    }

    /**
     * Get all unlock effects (things this technology unlocks).
     */
    public List<TechnologyEffect> unlockEffects() {
        return effects.stream()
            .filter(dev.starcore.starcore.module.technology.model.TechnologyEffect::isUnlockEffect)
            .toList();
    }

    private static String normalizeKey(String key) {
        if (key == null) {
            return "";
        }
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? "" : normalized;
    }
}
