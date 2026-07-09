package dev.starcore.starcore.technology;

import org.bukkit.entity.Player;

/**
 * Basic implementation of a simple technology effect.
 * <p>
 * This is a simple effect implementation for common effect types. For more complex
 * effects that require event listeners or persistent state, create custom implementations
 * of the TechnologyEffect interface.
 * </p>
 *
 * @author StarCore Development Team
 * @since 1.0.0
 */
public class SimpleTechnologyEffect implements TechnologyEffect {
    private final String type;
    private final double value;
    private final String description;

    /**
     * Constructs a new SimpleTechnologyEffect.
     *
     * @param type        the effect type identifier
     * @param value       the effect magnitude
     * @param description the human-readable description
     */
    public SimpleTechnologyEffect(String type, double value, String description) {
        this.type = type;
        this.value = value;
        this.description = description;
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public double getValue() {
        return value;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public void apply(String nationId) {
        // Base implementation does nothing - override in subclasses
        // or use effect registry system to handle specific effect types
    }

    @Override
    public void remove(String nationId) {
        // Base implementation does nothing - override in subclasses
    }

    @Override
    public boolean appliesTo(Player player) {
        // Base implementation - check if player belongs to the nation
        // This would require integration with nation system
        return false;
    }

    @Override
    public String toString() {
        return "SimpleTechnologyEffect{" +
               "type='" + type + '\'' +
               ", value=" + value +
               ", description='" + description + '\'' +
               '}';
    }
}
