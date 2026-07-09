package dev.starcore.starcore.module.technology.model;

import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

/**
 * Represents a single technology effect with its type, value, and description.
 */
public record TechnologyEffect(
    String type,
    double value,
    String description
) {
    public TechnologyEffect {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("effect type cannot be null or blank");
        }
        type = type.trim().toLowerCase();
        description = description != null ? description.trim() : "";
    }

    /**
     * Check if this effect applies to a specific player (for player-specific effects).
     */
    public boolean appliesToPlayer() {
        return switch (type) {
            case "mining_speed", "attack_damage", "defense_bonus", "defense", "melee_damage",
                 "ranged_damage", "bow_damage", "food_efficiency", "movement_speed",
                 "xp_gain", "inventory_capacity", "crop_growth", "production_speed",
                 "resource_yield", "research_speed", "trade_income", "team_damage_bonus",
                 "transport_speed", "mining_xp", "fishing_bonus", "growth_bonus",
                 "population_cap", "production_multiplier", "research_cost_reduction",
                 "automation", "production_automation", "economic_output",
                 "military_production", "research_breakthrough", "skill_learning",
                 "power_generation", "arrow_speed", "communication_range" -> true;
            default -> false;
        };
    }

    /**
     * Check if this is a bonus multiplier effect (value should be multiplied, not added).
     */
    public boolean isMultiplier() {
        return switch (type) {
            case "production_multiplier", "research_breakthrough", "economic_output",
                 "military_production", "production_automation", "automation",
                 "resource_yield", "production_speed" -> true;
            default -> false;
        };
    }

    /**
     * Check if this effect unlocks something (not a numeric bonus).
     */
    public boolean isUnlockEffect() {
        return switch (type) {
            case "unlock_crafting", "unlock_equipment", "unlock_feature",
                 "unlock_building", "unlock_machine", "unlock_weapon",
                 "unlock_material", "unlock_transport", "unlock_formation",
                 "unlock_system", "unlock_entity", "unlock_reactor",
                 "power_generation" -> true;
            default -> false;
        };
    }
}
