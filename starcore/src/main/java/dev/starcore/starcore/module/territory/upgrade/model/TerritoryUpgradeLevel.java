package dev.starcore.starcore.module.territory.upgrade.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a territory upgrade level definition.
 * 领地升级等级定义
 */
public record TerritoryUpgradeLevel(
    int level,
    String name,
    int expRequired,
    Map<String, Object> benefits,
    List<String> unlockPermissions,
    List<String> prerequisites,
    String description
) {
    public TerritoryUpgradeLevel {
        if (level < 0) {
            throw new IllegalArgumentException("level cannot be negative");
        }
        name = Objects.requireNonNull(name, "name").trim();
        description = description != null ? description.trim() : "";
        benefits = benefits != null ? Map.copyOf(benefits) : Map.of();
        unlockPermissions = unlockPermissions != null ? List.copyOf(unlockPermissions) : List.of();
        prerequisites = prerequisites != null ? List.copyOf(prerequisites) : List.of();
    }

    /**
     * Get benefit as integer.
     */
    public int getBenefitAsInt(String key, int defaultValue) {
        Object value = benefits.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }

    /**
     * Get benefit as double.
     */
    public double getBenefitAsDouble(String key, double defaultValue) {
        Object value = benefits.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return defaultValue;
    }

    /**
     * Get benefit as boolean.
     */
    public boolean getBenefitAsBoolean(String key, boolean defaultValue) {
        Object value = benefits.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return defaultValue;
    }

    /**
     * Check if this level has a specific benefit.
     */
    public boolean hasBenefit(String key) {
        return benefits.containsKey(key);
    }

    /**
     * Check if this level unlocks a specific permission.
     */
    public boolean unlocksPermission(String permission) {
        return unlockPermissions.stream()
            .anyMatch(p -> p.equalsIgnoreCase(permission));
    }
}
