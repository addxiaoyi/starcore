package dev.starcore.starcore.module.territory.upgrade.model;

import java.util.Map;
import java.util.Objects;

/**
 * Represents an upgrade benefit for a nation.
 * 领地升级收益
 */
public record UpgradeBenefit(
    String pathId,
    int level,
    Map<String, Object> benefits
) {
    public UpgradeBenefit {
        Objects.requireNonNull(pathId, "pathId cannot be null");
        if (level < 0) {
            throw new IllegalArgumentException("level cannot be negative");
        }
        benefits = benefits != null ? Map.copyOf(benefits) : Map.of();
    }

    /**
     * Get benefit as integer.
     */
    public int getInt(String key, int defaultValue) {
        Object value = benefits.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }

    /**
     * Get benefit as double.
     */
    public double getDouble(String key, double defaultValue) {
        Object value = benefits.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return defaultValue;
    }

    /**
     * Get benefit as boolean.
     */
    public boolean getBoolean(String key, boolean defaultValue) {
        Object value = benefits.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return defaultValue;
    }

    /**
     * Check if has a specific benefit.
     */
    public boolean has(String key) {
        return benefits.containsKey(key);
    }

    /**
     * Get claim limit bonus from this benefit.
     */
    public int getClaimLimitBonus() {
        return getInt("claim_limit", 0);
    }

    /**
     * Get tax rate modifier from this benefit.
     */
    public double getTaxRateModifier() {
        return getDouble("tax_rate_modifier", 1.0);
    }

    /**
     * Get resource bonus multiplier.
     */
    public double getResourceBonus() {
        return getDouble("resource_bonus", 1.0);
    }

    /**
     * Get defense bonus multiplier.
     */
    public double getDefenseBonus() {
        return getDouble("defense_bonus", 1.0);
    }

    /**
     * Get army capacity bonus.
     */
    public int getArmyCapacityBonus() {
        return getInt("army_capacity", 0);
    }
}
