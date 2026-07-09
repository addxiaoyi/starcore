package dev.starcore.starcore.technology;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Represents a single technology in the technology tree.
 * <p>
 * A technology defines a researchable advancement that provides gameplay benefits
 * when unlocked. Technologies are organized by era and branch, may have prerequisites,
 * and can be mutually exclusive with other technologies.
 * </p>
 * <p>
 * This is an immutable record representing technology metadata. The actual research
 * state (whether a nation has unlocked it) is tracked separately.
 * </p>
 *
 * @param id               the unique identifier for this technology
 * @param name             the display name of this technology
 * @param description      a detailed description of what this technology does
 * @param era              the technological era this belongs to
 * @param branch           the development branch (military/economic/culture)
 * @param prerequisites    list of technology IDs that must be researched first
 * @param mutuallyExclusive list of technology IDs that cannot coexist with this one
 * @param treasuryCost     the monetary cost to research this technology
 * @param resourceCosts    map of resource types to quantities required for research
 * @param researchTime     the time in seconds required to research this technology
 * @param effects          list of effects applied when this technology is researched
 * @param combos           list of technology sets that unlock special bonuses
 *
 * @author StarCore Development Team
 * @since 1.0.0
 */
public record Technology(
    String id,
    String name,
    String description,
    TechnologyEra era,
    TechnologyBranch branch,
    List<String> prerequisites,
    List<String> mutuallyExclusive,
    BigDecimal treasuryCost,
    Map<String, Long> resourceCosts,
    int researchTime,
    List<TechnologyEffect> effects,
    List<Set<String>> combos
) {
    /**
     * Compact constructor with validation and immutability enforcement.
     */
    public Technology {
        Objects.requireNonNull(id, "Technology ID cannot be null");
        Objects.requireNonNull(name, "Technology name cannot be null");
        Objects.requireNonNull(era, "Technology era cannot be null");
        Objects.requireNonNull(branch, "Technology branch cannot be null");

        if (id.isBlank()) {
            throw new IllegalArgumentException("Technology ID cannot be blank");
        }
        if (name.isBlank()) {
            throw new IllegalArgumentException("Technology name cannot be blank");
        }

        // Make collections immutable
        prerequisites = prerequisites == null ? List.of() : List.copyOf(prerequisites);
        mutuallyExclusive = mutuallyExclusive == null ? List.of() : List.copyOf(mutuallyExclusive);
        resourceCosts = resourceCosts == null ? Map.of() : Map.copyOf(resourceCosts);
        effects = effects == null ? List.of() : List.copyOf(effects);
        combos = combos == null ? List.of() : combos.stream()
            .map(set -> Set.copyOf(set))
            .toList();

        // Validate costs
        if (treasuryCost != null && treasuryCost.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Treasury cost cannot be negative");
        }
        for (Map.Entry<String, Long> entry : resourceCosts.entrySet()) {
            if (entry.getValue() < 0) {
                throw new IllegalArgumentException("Resource cost cannot be negative: " + entry.getKey());
            }
        }

        // Validate research time
        if (researchTime < 0) {
            throw new IllegalArgumentException("Research time cannot be negative");
        }

        // Ensure description is never null
        if (description == null) {
            description = "";
        }
    }

    /**
     * Checks if this technology has any prerequisites.
     *
     * @return true if prerequisites exist
     */
    public boolean hasPrerequisites() {
        return !prerequisites.isEmpty();
    }

    /**
     * Checks if this technology is mutually exclusive with others.
     *
     * @return true if mutually exclusive technologies exist
     */
    public boolean hasMutuallyExclusive() {
        return !mutuallyExclusive.isEmpty();
    }

    /**
     * Checks if this technology conflicts with another technology.
     *
     * @param otherTechId the ID of the other technology
     * @return true if the technologies are mutually exclusive
     */
    public boolean conflictsWith(String otherTechId) {
        return mutuallyExclusive.contains(otherTechId);
    }

    /**
     * Checks if this technology has any resource costs.
     *
     * @return true if resource costs exist
     */
    public boolean hasResourceCosts() {
        return !resourceCosts.isEmpty();
    }

    /**
     * Checks if this technology has a treasury cost.
     *
     * @return true if a treasury cost exists
     */
    public boolean hasTreasuryCost() {
        return treasuryCost != null && treasuryCost.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Checks if this technology has any effects.
     *
     * @return true if effects exist
     */
    public boolean hasEffects() {
        return !effects.isEmpty();
    }

    /**
     * Checks if this technology participates in any technology combos.
     *
     * @return true if combo sets exist
     */
    public boolean hasCombos() {
        return !combos.isEmpty();
    }

    /**
     * Gets the total number of prerequisite technologies.
     *
     * @return the prerequisite count
     */
    public int getPrerequisiteCount() {
        return prerequisites.size();
    }

    /**
     * Checks if all prerequisites are satisfied given a set of unlocked technologies.
     *
     * @param unlockedTechnologies the set of technology IDs that have been researched
     * @return true if all prerequisites are met
     */
    public boolean canResearch(Set<String> unlockedTechnologies) {
        if (unlockedTechnologies == null) {
            unlockedTechnologies = Set.of();
        }

        // Check if all prerequisites are unlocked
        for (String prereq : prerequisites) {
            if (!unlockedTechnologies.contains(prereq)) {
                return false;
            }
        }

        // Check if any mutually exclusive technologies are unlocked
        for (String exclusive : mutuallyExclusive) {
            if (unlockedTechnologies.contains(exclusive)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Checks if any combo bonuses are unlocked with the given technology set.
     *
     * @param unlockedTechnologies the set of technology IDs that have been researched
     * @return list of combo sets that are fully unlocked
     */
    public List<Set<String>> getUnlockedCombos(Set<String> unlockedTechnologies) {
        if (unlockedTechnologies == null || !unlockedTechnologies.contains(this.id)) {
            return List.of();
        }

        List<Set<String>> unlocked = new ArrayList<>();
        for (Set<String> combo : combos) {
            if (unlockedTechnologies.containsAll(combo)) {
                unlocked.add(combo);
            }
        }
        return unlocked;
    }

    /**
     * Creates a builder for constructing Technology instances.
     *
     * @param id the technology ID
     * @return a new builder
     */
    public static Builder builder(String id) {
        return new Builder(id);
    }

    /**
     * Builder class for creating Technology instances with a fluent API.
     */
    public static class Builder {
        private final String id;
        private String name;
        private String description = "";
        private TechnologyEra era;
        private TechnologyBranch branch;
        private final List<String> prerequisites = new ArrayList<>();
        private final List<String> mutuallyExclusive = new ArrayList<>();
        private BigDecimal treasuryCost = BigDecimal.ZERO;
        private final Map<String, Long> resourceCosts = new java.util.HashMap<>();
        private int researchTime = 0;
        private final List<TechnologyEffect> effects = new ArrayList<>();
        private final List<Set<String>> combos = new ArrayList<>();

        private Builder(String id) {
            this.id = id;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder era(TechnologyEra era) {
            this.era = era;
            return this;
        }

        public Builder branch(TechnologyBranch branch) {
            this.branch = branch;
            return this;
        }

        public Builder addPrerequisite(String techId) {
            this.prerequisites.add(techId);
            return this;
        }

        public Builder addMutuallyExclusive(String techId) {
            this.mutuallyExclusive.add(techId);
            return this;
        }

        public Builder treasuryCost(BigDecimal cost) {
            this.treasuryCost = cost;
            return this;
        }

        public Builder addResourceCost(String resourceType, long amount) {
            this.resourceCosts.put(resourceType, amount);
            return this;
        }

        public Builder researchTime(int seconds) {
            this.researchTime = seconds;
            return this;
        }

        public Builder addEffect(TechnologyEffect effect) {
            this.effects.add(effect);
            return this;
        }

        public Builder addCombo(Set<String> techIds) {
            this.combos.add(new HashSet<>(techIds));
            return this;
        }

        public Technology build() {
            return new Technology(
                id, name, description, era, branch,
                prerequisites, mutuallyExclusive,
                treasuryCost, resourceCosts, researchTime,
                effects, combos
            );
        }
    }
}
