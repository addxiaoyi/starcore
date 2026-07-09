package dev.starcore.starcore.technology;

import org.bukkit.entity.Player;

/**
 * Represents the effect that a technology applies when researched.
 * <p>
 * Technology effects can modify various game mechanics such as resource production,
 * combat statistics, research speed, or unlock new capabilities. Effects are applied
 * when a technology is researched and removed if the technology is revoked.
 * </p>
 * <p>
 * Implementations should be stateless and reusable across multiple nations.
 * </p>
 *
 * @author StarCore Development Team
 * @since 1.0.0
 */
public interface TechnologyEffect {

    /**
     * Gets the type identifier for this effect.
     * <p>
     * This is used for serialization and effect identification. Common types include:
     * <ul>
     *   <li>mining_speed - Increases resource gathering rate</li>
     *   <li>attack_damage - Increases combat damage</li>
     *   <li>defense_bonus - Increases defensive capabilities</li>
     *   <li>research_speed - Reduces research time</li>
     *   <li>production_efficiency - Increases production output</li>
     * </ul>
     * </p>
     *
     * @return the effect type identifier
     */
    String getType();

    /**
     * Gets the magnitude of this effect.
     * <p>
     * The interpretation of this value depends on the effect type:
     * <ul>
     *   <li>Percentage bonuses: 0.1 = +10%, 0.25 = +25%</li>
     *   <li>Flat bonuses: actual value to add</li>
     *   <li>Multipliers: 1.5 = 150% of base value</li>
     * </ul>
     * </p>
     *
     * @return the effect value
     */
    double getValue();

    /**
     * Applies this effect to a nation.
     * <p>
     * This is called when a nation successfully researches the technology.
     * Implementations should register any necessary listeners, modify persistent
     * data, or update game state as needed.
     * </p>
     *
     * @param nationId the ID of the nation that researched the technology
     */
    void apply(String nationId);

    /**
     * Removes this effect from a nation.
     * <p>
     * This is called when a technology is revoked (e.g., through admin commands
     * or game mechanics). Implementations should unregister listeners and revert
     * any changes made by {@link #apply(String)}.
     * </p>
     *
     * @param nationId the ID of the nation to remove the effect from
     */
    void remove(String nationId);

    /**
     * Checks if this effect applies to a specific player.
     * <p>
     * Used for per-player effects like combat bonuses or gathering speed.
     * Returns true if the player's nation has researched the technology
     * and the effect should be active for them.
     * </p>
     *
     * @param player the player to check
     * @return true if the effect applies to this player
     */
    default boolean appliesTo(Player player) {
        return false;
    }

    /**
     * Gets a human-readable description of this effect.
     * <p>
     * This should be localized and formatted for display to players,
     * e.g., "+10% Mining Speed" or "Unlock: Iron Weapons"
     * </p>
     *
     * @return the effect description
     */
    String getDescription();

    /**
     * Checks if this effect conflicts with another effect.
     * <p>
     * Some effects may be mutually exclusive or require special handling
     * when combined. For example, two different weapon upgrade paths might
     * conflict with each other.
     * </p>
     *
     * @param other the other effect to check
     * @return true if the effects conflict
     */
    default boolean conflictsWith(TechnologyEffect other) {
        return false;
    }
}
