package dev.starcore.starcore.module.territory.upgrade.model;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Represents an upgrade tier definition.
 * 升级层级定义
 */
public record UpgradeTierDefinition(
    String pathId,
    String pathName,
    String pathDescription,
    String color,
    List<TerritoryUpgradeLevel> tiers
) {
    public UpgradeTierDefinition {
        pathId = Objects.requireNonNull(pathId, "pathId").trim().toLowerCase(Locale.ROOT);
        pathName = Objects.requireNonNull(pathName, "pathName").trim();
        pathDescription = pathDescription != null ? pathDescription.trim() : "";
        color = color != null ? color.trim() : "&f";
        tiers = tiers != null ? List.copyOf(tiers) : List.of();
    }

    /**
     * Get the maximum level in this tier.
     */
    public int maxLevel() {
        return tiers.stream()
            .mapToInt(TerritoryUpgradeLevel::level)
            .max()
            .orElse(0);
    }

    /**
     * Get the level definition by level number.
     */
    public TerritoryUpgradeLevel getLevel(int level) {
        return tiers.stream()
            .filter(t -> t.level() == level)
            .findFirst()
            .orElse(null);
    }

    /**
     * Get the next level after current level.
     */
    public TerritoryUpgradeLevel getNextLevel(int currentLevel) {
        return tiers.stream()
            .filter(t -> t.level() > currentLevel)
            .min((a, b) -> Integer.compare(a.level(), b.level()))
            .orElse(null);
    }

    /**
     * Check if this path has a specific level.
     */
    public boolean hasLevel(int level) {
        return tiers.stream().anyMatch(t -> t.level() == level);
    }
}
