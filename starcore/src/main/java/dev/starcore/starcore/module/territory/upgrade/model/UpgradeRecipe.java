package dev.starcore.starcore.module.territory.upgrade.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Represents an upgrade recipe (cost to upgrade).
 * 升级配方（升级所需消耗）
 */
public record UpgradeRecipe(
    String pathId,
    int targetLevel,
    BigDecimal treasuryCost,
    Map<String, Long> resourceCosts,
    List<String> requiredPermissions
) {
    public UpgradeRecipe {
        pathId = pathId != null ? pathId.trim().toLowerCase(Locale.ROOT) : "";
        treasuryCost = treasuryCost != null ? treasuryCost : BigDecimal.ZERO;
        resourceCosts = resourceCosts != null ? Map.copyOf(resourceCosts) : Map.of();
        requiredPermissions = requiredPermissions != null ? List.copyOf(requiredPermissions) : List.of();
    }

    /**
     * Check if this recipe has treasury cost.
     */
    public boolean hasTreasuryCost() {
        return treasuryCost.signum() > 0;
    }

    /**
     * Check if this recipe has resource costs.
     */
    public boolean hasResourceCosts() {
        return !resourceCosts.isEmpty();
    }

    /**
     * Check if this recipe has permission requirements.
     */
    public boolean hasPermissionRequirements() {
        return !requiredPermissions.isEmpty();
    }

    /**
     * Get total resource cost count.
     */
    public int resourceCostCount() {
        return resourceCosts.size();
    }
}
