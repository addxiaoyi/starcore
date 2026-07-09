package dev.starcore.starcore.module.technology;

import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.technology.model.TechnologyDefinition;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Extended technology service interface with enhanced functionality.
 */
public interface TechnologyService {
    /**
     * Gets all available technology keys.
     */
    Collection<String> availableTechnologies();

    /**
     * Gets the cost of a technology.
     */
    Optional<TechnologyCost> costOf(String technologyKey);

    /**
     * Gets all unlocked technologies for a nation.
     */
    Collection<String> unlockedTechnologies(NationId nationId);

    /**
     * Checks if a nation has a specific technology unlocked.
     */
    boolean hasTechnology(NationId nationId, String technologyKey);

    /**
     * Unlocks a technology for a nation (immediate, no research time).
     */
    boolean unlock(NationId nationId, String technologyKey);

    /**
     * Revokes a technology from a nation.
     */
    boolean revoke(NationId nationId, String technologyKey);

    /**
     * Gets a summary of the technology system state.
     */
    String summary();
}
