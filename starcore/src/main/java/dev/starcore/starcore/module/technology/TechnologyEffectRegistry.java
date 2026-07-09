package dev.starcore.starcore.module.technology;

import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.technology.model.TechnologyEffect;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.Map;

/**
 * Registry that maps technology effect types to actual game mechanics.
 * Applies effects to players based on their nation's unlocked technologies.
 */
public final class TechnologyEffectRegistry {
    // NamespacedKey-based modifiers for Paper 1.21.11+
    private static final NamespacedKey ATTACK_KEY = new NamespacedKey("starcore", "tech_attack_damage");
    private static final NamespacedKey DEFENSE_KEY = new NamespacedKey("starcore", "tech_defense_bonus");
    private static final NamespacedKey SPEED_KEY = new NamespacedKey("starcore", "tech_movement_speed");
    private static final NamespacedKey MINING_KEY = new NamespacedKey("starcore", "tech_mining_speed");
    private static final NamespacedKey ARMOR_KEY = new NamespacedKey("starcore", "tech_armor");
    private static final NamespacedKey ARMOR_TOUGHNESS_KEY = new NamespacedKey("starcore", "tech_armor_toughness");

    private static final String XP_MODIFIER_KEY = "tech_xp_modifier";
    private static final String INVENTORY_BONUS_KEY = "tech_inventory_bonus";

    private final JavaPlugin plugin;
    private final TechnologyDefinitionLoader definitionLoader;

    public TechnologyEffectRegistry(JavaPlugin plugin, TechnologyDefinitionLoader definitionLoader) {
        this.plugin = plugin;
        this.definitionLoader = definitionLoader;
    }

    /**
     * Applies all active technology effects for a nation to a player.
     */
    public void applyEffectsToPlayer(Player player, NationId nationId, TechnologyService technologyService) {
        if (player == null || nationId == null || technologyService == null) {
            return;
        }

        // First remove existing effects
        removeEffectsFromPlayer(player);

        // Get all unlocked technologies for the nation
        for (String techKey : technologyService.unlockedTechnologies(nationId)) {
            var techDef = definitionLoader.load(techKey);
            if (techDef != null) {
                for (var effect : techDef.effects()) {
                    applyEffect(player, effect);
                }
            }
        }
    }

    /**
     * Removes all technology effect modifiers from a player.
     */
    public void removeEffectsFromPlayer(Player player) {
        if (player == null) {
            return;
        }

        // Paper 1.21+: Use NamespacedKey-based removal
        player.getAttribute(Attribute.ATTACK_DAMAGE).removeModifier(ATTACK_KEY);
        player.getAttribute(Attribute.ARMOR).removeModifier(DEFENSE_KEY);
        player.getAttribute(Attribute.ARMOR_TOUGHNESS).removeModifier(ARMOR_KEY);
        player.getAttribute(Attribute.MOVEMENT_SPEED).removeModifier(SPEED_KEY);
        player.getAttribute(Attribute.MINING_EFFICIENCY).removeModifier(MINING_KEY);

        // Remove custom potion effects from tech
        player.removePotionEffect(PotionEffectType.HASTE);
        player.removePotionEffect(PotionEffectType.MINING_FATIGUE);
        player.removePotionEffect(PotionEffectType.RESISTANCE);
        player.removePotionEffect(PotionEffectType.STRENGTH);
        player.removePotionEffect(PotionEffectType.SPEED);

        // Remove metadata
        player.removeMetadata(XP_MODIFIER_KEY, plugin);
        player.removeMetadata(INVENTORY_BONUS_KEY, plugin);
    }

    /**
     * Applies a single technology effect to a player.
     */
    @SuppressWarnings("deprecation")
    public void applyEffect(Player player, TechnologyEffect effect) {
        if (player == null || effect == null) {
            return;
        }

        switch (effect.type()) {
            case "mining_speed" -> applyMiningSpeed(player, effect.value());
            case "attack_damage" -> applyAttackDamage(player, effect.value());
            case "defense", "defense_bonus" -> applyDefenseBonus(player, effect.value());
            case "melee_damage" -> applyMeleeDamage(player, effect.value());
            case "ranged_damage" -> applyRangedDamage(player, effect.value());
            case "bow_damage" -> applyBowDamage(player, effect.value());
            case "food_efficiency" -> applyFoodEfficiency(player, effect.value());
            case "movement_speed" -> applyMovementSpeed(player, effect.value());
            case "xp_gain" -> applyXpGain(player, effect.value());
            case "inventory_capacity" -> applyInventoryCapacity(player, (int) effect.value());
            case "crop_growth" -> applyCropGrowth(player, effect.value());
            case "production_speed" -> applyProductionSpeed(player, effect.value());
            case "resource_yield" -> applyResourceYield(player, effect.value());
            case "research_speed" -> applyResearchSpeed(player, effect.value());
            case "trade_income" -> applyTradeIncome(player, effect.value());
            case "team_damage_bonus" -> applyTeamDamageBonus(player, effect.value());
            case "transport_speed" -> applyTransportSpeed(player, effect.value());
            case "mining_xp" -> applyMiningXp(player, effect.value());
            case "fishing_bonus" -> applyFishingBonus(player, effect.value());
            case "growth_bonus" -> applyGrowthBonus(player, effect.value());
            case "population_cap" -> applyPopulationCap(player, (int) effect.value());
            case "production_multiplier" -> applyProductionMultiplier(player, effect.value());
            case "research_cost_reduction" -> applyResearchCostReduction(player, effect.value());
            case "automation", "production_automation" -> applyAutomation(player, effect.value());
            case "economic_output" -> applyEconomicOutput(player, effect.value());
            case "military_production" -> applyMilitaryProduction(player, effect.value());
            case "research_breakthrough" -> applyResearchBreakthrough(player, effect.value());
            case "skill_learning" -> applySkillLearning(player, effect.value());
            case "power_generation" -> applyPowerGeneration(player, effect.value());
            case "arrow_speed" -> applyArrowSpeed(player, effect.value());
            case "communication_range" -> applyCommunicationRange(player, effect.value());
            // unlock* types are handled via unlockEffects() and hasUnlock() - no direct apply needed
            case "unlock_crafting", "unlock_equipment", "unlock_feature",
                 "unlock_building", "unlock_machine", "unlock_weapon",
                 "unlock_material", "unlock_transport", "unlock_formation",
                 "unlock_system", "unlock_entity", "unlock_reactor",
                 "unlock_unit" -> {
                // Unlock effects are processed through hasUnlock() check
                // Stored in unlockEffects() for feature gating
            }
            default -> {
                // Unknown effect type - log or ignore
            }
        }
    }

    /**
     * Calculates the total modifier for a specific effect type across all unlocked technologies.
     */
    public double calculateTotalModifier(NationId nationId, String effectType, TechnologyService technologyService) {
        if (nationId == null || effectType == null || technologyService == null) {
            return 0.0;
        }

        double total = 0.0;
        String normalizedType = effectType.toLowerCase(Locale.ROOT);

        // Define effect type aliases for combined bonuses
        String[] effectTypesToCheck;
        if ("defense".equals(normalizedType)) {
            effectTypesToCheck = new String[]{"defense_bonus", "defense"};
        } else if ("damage".equals(normalizedType)) {
            effectTypesToCheck = new String[]{"attack_damage", "melee_damage", "ranged_damage", "bow_damage"};
        } else if ("speed".equals(normalizedType)) {
            effectTypesToCheck = new String[]{"movement_speed", "transport_speed"};
        } else if ("production".equals(normalizedType)) {
            effectTypesToCheck = new String[]{"production_multiplier", "production_speed"};
        } else {
            effectTypesToCheck = new String[]{normalizedType};
        }

        for (String techKey : technologyService.unlockedTechnologies(nationId)) {
            var techDef = definitionLoader.load(techKey);
            if (techDef != null) {
                for (var effect : techDef.effects()) {
                    String effectTypeStr = effect.type();
                    for (String checkType : effectTypesToCheck) {
                        if (checkType.equals(effectTypeStr)) {
                            if (effect.isMultiplier()) {
                                total = (total == 0.0) ? effect.value() : total * (1 + effect.value());
                            } else {
                                total += effect.value();
                            }
                            break;
                        }
                    }
                }
            }
        }
        return total;
    }

    /**
     * Gets all unlocked items/categories for a nation.
     */
    public Map<String, Double> getUnlockedFeatures(NationId nationId, TechnologyService technologyService) {
        Map<String, Double> unlocked = new java.util.LinkedHashMap<>();
        if (nationId == null || technologyService == null) {
            return unlocked;
        }

        for (String techKey : technologyService.unlockedTechnologies(nationId)) {
            var techDef = definitionLoader.load(techKey);
            if (techDef != null) {
                for (var effect : techDef.unlockEffects()) {
                    unlocked.put(effect.type() + ":" + (int) effect.value(), effect.value());
                }
            }
        }
        return unlocked;
    }

    /**
     * Checks if a specific unlock is available for a nation.
     */
    public boolean hasUnlock(NationId nationId, String unlockType, String unlockValue, TechnologyService technologyService) {
        if (nationId == null || unlockType == null || unlockValue == null) {
            return false;
        }

        for (String techKey : technologyService.unlockedTechnologies(nationId)) {
            var techDef = definitionLoader.load(techKey);
            if (techDef != null) {
                for (var effect : techDef.unlockEffects()) {
                    if (effect.type().equals(unlockType) &&
                        String.valueOf((int) effect.value()).equals(unlockValue)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // ========== Private effect application methods ==========

    private void applyMiningSpeed(Player player, double value) {
        var attribute = player.getAttribute(Attribute.MINING_EFFICIENCY);
        if (attribute != null) {
            attribute.removeModifier(MINING_KEY);
            double baseValue = attribute.getBaseValue();
            double newValue = baseValue * (1 + value);
            attribute.addModifier(new AttributeModifier(MINING_KEY,
                newValue - baseValue, AttributeModifier.Operation.ADD_NUMBER));
        }
    }

    private void applyAttackDamage(Player player, double value) {
        var attribute = player.getAttribute(Attribute.ATTACK_DAMAGE);
        if (attribute != null) {
            attribute.removeModifier(ATTACK_KEY);
            double baseValue = attribute.getBaseValue();
            double newValue = baseValue * (1 + value);
            attribute.addModifier(new AttributeModifier(ATTACK_KEY,
                newValue - baseValue, AttributeModifier.Operation.ADD_NUMBER));
        }
    }

    private void applyDefenseBonus(Player player, double value) {
        var armorAttr = player.getAttribute(Attribute.ARMOR);
        if (armorAttr != null) {
            armorAttr.removeModifier(DEFENSE_KEY);
            double baseValue = armorAttr.getBaseValue();
            double newValue = baseValue + (baseValue * value);
            armorAttr.addModifier(new AttributeModifier(DEFENSE_KEY,
                newValue - baseValue, AttributeModifier.Operation.ADD_NUMBER));
        }

        // Apply resistance potion effect
        int amplifier = (int) (value * 2);
        if (amplifier > 0) {
            player.addPotionEffect(new PotionEffect(
                PotionEffectType.RESISTANCE,
                Integer.MAX_VALUE,
                Math.min(amplifier - 1, 4),
                false,
                false
            ));
        }
    }

    private void applyMeleeDamage(Player player, double value) {
        applyAttackDamage(player, value);
    }

    private void applyRangedDamage(Player player, double value) {
        player.addPotionEffect(new PotionEffect(
            PotionEffectType.STRENGTH,
            Integer.MAX_VALUE,
            (int) (value * 4),
            false,
            false
        ));
    }

    private void applyBowDamage(Player player, double value) {
        applyRangedDamage(player, value);
    }

    private void applyFoodEfficiency(Player player, double value) {
        // Food efficiency could be tracked via hunger event listeners
        // Store the modifier in player metadata
        player.setMetadata(XP_MODIFIER_KEY + "_food", new FixedMetadataValue(plugin, value));
    }

    private void applyMovementSpeed(Player player, double value) {
        var attribute = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (attribute != null) {
            attribute.removeModifier(SPEED_KEY);
            double baseValue = attribute.getBaseValue();
            double newValue = baseValue * (1 + value);
            attribute.addModifier(new AttributeModifier(SPEED_KEY,
                newValue - baseValue, AttributeModifier.Operation.ADD_NUMBER));
        }
    }

    private void applyXpGain(Player player, double value) {
        player.setMetadata(XP_MODIFIER_KEY, new FixedMetadataValue(plugin, value));
    }

    private void applyInventoryCapacity(Player player, int slots) {
        player.setMetadata(INVENTORY_BONUS_KEY, new FixedMetadataValue(plugin, slots));
    }

    private void applyCropGrowth(Player player, double value) {
        // Crop growth is handled via BlockGrowEvent listener
        player.setMetadata("tech_crop_growth", new FixedMetadataValue(plugin, value));
    }

    private void applyProductionSpeed(Player player, double value) {
        // Production speed affects crafting via CraftItemEvent
        player.setMetadata("tech_production_speed", new FixedMetadataValue(plugin, value));
    }

    private void applyResourceYield(Player player, double value) {
        // Resource yield affects block drops via BlockBreakEvent
        player.setMetadata("tech_resource_yield", new FixedMetadataValue(plugin, value));
    }

    private void applyResearchSpeed(Player player, double value) {
        // Research speed affects research time calculations
        player.setMetadata("tech_research_speed", new FixedMetadataValue(plugin, value));
    }

    private void applyTradeIncome(Player player, double value) {
        // Trade income affects economic rewards
        player.setMetadata("tech_trade_income", new FixedMetadataValue(plugin, value));
    }

    private void applyTeamDamageBonus(Player player, double value) {
        // Team damage bonus applied via party/ally detection in combat
        player.setMetadata("tech_team_damage", new FixedMetadataValue(plugin, value));
    }

    private void applyTransportSpeed(Player player, double value) {
        // Transport speed affects entity movement when carrying items
        player.setMetadata("tech_transport_speed", new FixedMetadataValue(plugin, value));
    }

    private void applyMiningXp(Player player, double value) {
        // Mining XP bonus affects experience orbs from mining
        player.setMetadata("tech_mining_xp", new FixedMetadataValue(plugin, value));
    }

    private void applyFishingBonus(Player player, double value) {
        // Fishing bonus affects loot quality
        player.setMetadata("tech_fishing_bonus", new FixedMetadataValue(plugin, value));
    }

    private void applyGrowthBonus(Player player, double value) {
        // Growth bonus for crops and animals
        player.setMetadata("tech_growth_bonus", new FixedMetadataValue(plugin, value));
    }

    private void applyPopulationCap(Player player, int value) {
        // Population cap affects nation member limit
        player.setMetadata("tech_population_cap", new FixedMetadataValue(plugin, value));
    }

    private void applyProductionMultiplier(Player player, double value) {
        // Production multiplier for crafting
        player.setMetadata("tech_production_multiplier", new FixedMetadataValue(plugin, value));
    }

    private void applyResearchCostReduction(Player player, double value) {
        // Research cost reduction for technology costs
        player.setMetadata("tech_research_cost_reduction", new FixedMetadataValue(plugin, value));
    }

    private void applyAutomation(Player player, double value) {
        // Automation efficiency
        player.setMetadata("tech_automation", new FixedMetadataValue(plugin, value));
    }

    private void applyEconomicOutput(Player player, double value) {
        // Economic output bonus
        player.setMetadata("tech_economic_output", new FixedMetadataValue(plugin, value));
    }

    private void applyMilitaryProduction(Player player, double value) {
        // Military unit production speed
        player.setMetadata("tech_military_production", new FixedMetadataValue(plugin, value));
    }

    private void applyResearchBreakthrough(Player player, double value) {
        // Research breakthrough bonus (applies to all research)
        player.setMetadata("tech_research_breakthrough", new FixedMetadataValue(plugin, value));
    }

    private void applySkillLearning(Player player, double value) {
        // Skill learning speed
        player.setMetadata("tech_skill_learning", new FixedMetadataValue(plugin, value));
    }

    private void applyPowerGeneration(Player player, double value) {
        // Power generation for machines/reactors
        player.setMetadata("tech_power_generation", new FixedMetadataValue(plugin, value));
    }

    private void applyArrowSpeed(Player player, double value) {
        // Arrow speed affects projectile velocity
        // Stored as metadata for projectile listeners to use
        player.setMetadata("tech_arrow_speed", new FixedMetadataValue(plugin, value));
    }

    private void applyCommunicationRange(Player player, double value) {
        // Communication range for nation chat/messaging
        // Stored as metadata for chat listeners to check
        player.setMetadata("tech_communication_range", new FixedMetadataValue(plugin, value));
    }
}
