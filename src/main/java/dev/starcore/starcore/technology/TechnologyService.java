package dev.starcore.starcore.technology;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Core service for managing technology research and progression.
 * <p>
 * This service handles all technology-related operations including:
 * <ul>
 *   <li>Loading and maintaining the technology tree structure</li>
 *   <li>Validating technology prerequisites and mutual exclusions</li>
 *   <li>Tracking which technologies each nation has researched</li>
 *   <li>Applying and removing technology effects</li>
 *   <li>Managing technology combinations and bonus unlocks</li>
 * </ul>
 * </p>
 * <p>
 * Thread-safe for concurrent access by multiple nations.
 * </p>
 *
 * @author StarCore Development Team
 * @since 1.0.0
 */
public class TechnologyService {
    private final JavaPlugin plugin;
    private final Logger logger;
    private final TechnologyTree technologyTree;
    private final Map<String, Set<String>> nationTechnologies; // nationId -> set of tech IDs
    private final Map<String, Map<Set<String>, Boolean>> comboTracker; // nationId -> combo -> unlocked

    /**
     * Constructs a new TechnologyService.
     *
     * @param plugin the plugin instance
     * @throws IOException if the technology tree cannot be loaded
     */
    public TechnologyService(JavaPlugin plugin) throws IOException {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.technologyTree = TechnologyTree.load(plugin);
        this.nationTechnologies = new ConcurrentHashMap<>();
        this.comboTracker = new ConcurrentHashMap<>();

        logger.info("TechnologyService initialized with " + technologyTree.size() + " technologies");
    }

    /**
     * Gets the technology tree.
     *
     * @return the technology tree
     */
    public TechnologyTree getTechnologyTree() {
        return technologyTree;
    }

    /**
     * Gets all technologies that a nation has researched.
     *
     * @param nationId the nation ID
     * @return set of researched technology IDs
     */
    public Set<String> getResearchedTechnologies(String nationId) {
        return new HashSet<>(nationTechnologies.getOrDefault(nationId, Set.of()));
    }

    /**
     * Checks if a nation has researched a specific technology.
     *
     * @param nationId the nation ID
     * @param techId   the technology ID
     * @return true if the technology is researched
     */
    public boolean hasTechnology(String nationId, String techId) {
        Set<String> techs = nationTechnologies.get(nationId);
        return techs != null && techs.contains(techId);
    }

    /**
     * Gets all technologies that a nation can currently research.
     * <p>
     * A technology is available if:
     * <ul>
     *   <li>It exists in the technology tree</li>
     *   <li>It has not already been researched</li>
     *   <li>All prerequisite technologies have been researched</li>
     *   <li>No mutually exclusive technologies have been researched</li>
     * </ul>
     * </p>
     *
     * @param nationId the nation ID
     * @return list of available technologies
     */
    public List<Technology> getAvailableTechnologies(String nationId) {
        Set<String> researched = nationTechnologies.getOrDefault(nationId, Set.of());
        return technologyTree.getAvailableTechnologies(researched);
    }

    /**
     * Attempts to research a technology for a nation.
     * <p>
     * This method validates prerequisites and mutual exclusions but does NOT
     * check or deduct costs. Cost validation should be done by the caller.
     * </p>
     *
     * @param nationId the nation ID
     * @param techId   the technology ID to research
     * @return result object containing success status and details
     */
    public ResearchResult researchTechnology(String nationId, String techId) {
        Technology tech = technologyTree.getTechnology(techId);
        if (tech == null) {
            return ResearchResult.failure("Technology not found: " + techId);
        }

        Set<String> researched = nationTechnologies.computeIfAbsent(nationId, k -> ConcurrentHashMap.newKeySet());

        // Check if already researched
        if (researched.contains(techId)) {
            return ResearchResult.failure("Technology already researched");
        }

        // Validate prerequisites
        for (String prereqId : tech.prerequisites()) {
            if (!researched.contains(prereqId)) {
                Technology prereq = technologyTree.getTechnology(prereqId);
                String prereqName = prereq != null ? prereq.name() : prereqId;
                return ResearchResult.failure("Missing prerequisite: " + prereqName);
            }
        }

        // Check mutual exclusions
        for (String exclusiveId : tech.mutuallyExclusive()) {
            if (researched.contains(exclusiveId)) {
                Technology exclusive = technologyTree.getTechnology(exclusiveId);
                String exclusiveName = exclusive != null ? exclusive.name() : exclusiveId;
                return ResearchResult.failure("Conflicts with already researched technology: " + exclusiveName);
            }
        }

        // Add technology
        researched.add(techId);

        // Apply effects
        try {
            applyEffects(nationId, tech);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to apply effects for technology " + techId, e);
            // Don't rollback - effects are applied best-effort
        }

        // Check for combo unlocks
        List<Set<String>> newCombos = checkComboUnlocks(nationId, tech);

        logger.info("Nation " + nationId + " researched technology: " + tech.name() + " (" + techId + ")");

        return ResearchResult.success(tech, newCombos);
    }

    /**
     * Revokes a technology from a nation (admin function).
     * <p>
     * This removes the technology and all its effects. Note that this does NOT
     * automatically revoke technologies that depend on this one.
     * </p>
     *
     * @param nationId the nation ID
     * @param techId   the technology ID to revoke
     * @return true if the technology was revoked, false if it wasn't researched
     */
    public boolean revokeTechnology(String nationId, String techId) {
        Set<String> researched = nationTechnologies.get(nationId);
        if (researched == null || !researched.remove(techId)) {
            return false;
        }

        Technology tech = technologyTree.getTechnology(techId);
        if (tech != null) {
            try {
                removeEffects(nationId, tech);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to remove effects for technology " + techId, e);
            }
        }

        // Remove combo tracking for this nation
        Map<Set<String>, Boolean> combos = comboTracker.get(nationId);
        if (combos != null) {
            combos.clear();
        }

        logger.info("Nation " + nationId + " had technology revoked: " + techId);
        return true;
    }

    /**
     * Gets the research progress percentage for a nation.
     *
     * @param nationId the nation ID
     * @return percentage of total technologies researched (0-100)
     */
    public double getResearchProgress(String nationId) {
        int total = technologyTree.size();
        if (total == 0) {
            return 100.0;
        }
        int researched = nationTechnologies.getOrDefault(nationId, Set.of()).size();
        return (researched * 100.0) / total;
    }

    /**
     * Gets research progress in a specific era.
     *
     * @param nationId the nation ID
     * @param era      the technology era
     * @return percentage of era technologies researched (0-100)
     */
    public double getEraProgress(String nationId, TechnologyEra era) {
        List<Technology> eraTechs = technologyTree.getTechnologiesInEra(era);
        if (eraTechs.isEmpty()) {
            return 100.0;
        }

        Set<String> researched = nationTechnologies.getOrDefault(nationId, Set.of());
        long researchedCount = eraTechs.stream()
            .filter(tech -> researched.contains(tech.id()))
            .count();

        return (researchedCount * 100.0) / eraTechs.size();
    }

    /**
     * Gets research progress in a specific branch.
     *
     * @param nationId the nation ID
     * @param branch   the technology branch
     * @return percentage of branch technologies researched (0-100)
     */
    public double getBranchProgress(String nationId, TechnologyBranch branch) {
        List<Technology> branchTechs = technologyTree.getTechnologiesInBranch(branch);
        if (branchTechs.isEmpty()) {
            return 100.0;
        }

        Set<String> researched = nationTechnologies.getOrDefault(nationId, Set.of());
        long researchedCount = branchTechs.stream()
            .filter(tech -> researched.contains(tech.id()))
            .count();

        return (researchedCount * 100.0) / branchTechs.size();
    }

    /**
     * Gets all active combo bonuses for a nation.
     *
     * @param nationId the nation ID
     * @return list of technology sets that form active combos
     */
    public List<Set<String>> getActiveCombos(String nationId) {
        Set<String> researched = nationTechnologies.getOrDefault(nationId, Set.of());
        List<Set<String>> activeCombos = new ArrayList<>();

        for (String techId : researched) {
            Technology tech = technologyTree.getTechnology(techId);
            if (tech != null && tech.hasCombos()) {
                activeCombos.addAll(tech.getUnlockedCombos(researched));
            }
        }

        return activeCombos;
    }

    /**
     * Validates prerequisites for a technology.
     *
     * @param nationId the nation ID
     * @param techId   the technology ID
     * @return validation result with missing prerequisites
     */
    public PrerequisiteValidation validatePrerequisites(String nationId, String techId) {
        Technology tech = technologyTree.getTechnology(techId);
        if (tech == null) {
            return new PrerequisiteValidation(false, List.of(), "Technology not found");
        }

        Set<String> researched = nationTechnologies.getOrDefault(nationId, Set.of());

        List<String> missing = tech.prerequisites().stream()
            .filter(prereqId -> !researched.contains(prereqId))
            .collect(Collectors.toList());

        if (!missing.isEmpty()) {
            return new PrerequisiteValidation(false, missing, "Missing prerequisites");
        }

        // Check mutual exclusions
        for (String exclusiveId : tech.mutuallyExclusive()) {
            if (researched.contains(exclusiveId)) {
                return new PrerequisiteValidation(false, List.of(), "Conflicts with: " + exclusiveId);
            }
        }

        return new PrerequisiteValidation(true, List.of(), "All prerequisites met");
    }

    private void applyEffects(String nationId, Technology tech) {
        for (TechnologyEffect effect : tech.effects()) {
            try {
                effect.apply(nationId);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to apply effect " + effect.getType() + " for tech " + tech.id(), e);
            }
        }
    }

    private void removeEffects(String nationId, Technology tech) {
        for (TechnologyEffect effect : tech.effects()) {
            try {
                effect.remove(nationId);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to remove effect " + effect.getType() + " for tech " + tech.id(), e);
            }
        }
    }

    private List<Set<String>> checkComboUnlocks(String nationId, Technology tech) {
        if (!tech.hasCombos()) {
            return List.of();
        }

        Set<String> researched = nationTechnologies.get(nationId);
        Map<Set<String>, Boolean> combos = comboTracker.computeIfAbsent(nationId, k -> new HashMap<>());

        List<Set<String>> newlyUnlocked = new ArrayList<>();
        for (Set<String> combo : tech.combos()) {
            if (combos.getOrDefault(combo, false)) {
                continue; // Already unlocked
            }

            if (researched.containsAll(combo)) {
                combos.put(combo, true);
                newlyUnlocked.add(combo);
                logger.info("Nation " + nationId + " unlocked technology combo: " + combo);
            }
        }

        return newlyUnlocked;
    }

    /**
     * Result of a technology research attempt.
     */
    public static class ResearchResult {
        private final boolean success;
        private final String message;
        private final Technology technology;
        private final List<Set<String>> newCombos;

        private ResearchResult(boolean success, String message, Technology technology, List<Set<String>> newCombos) {
            this.success = success;
            this.message = message;
            this.technology = technology;
            this.newCombos = newCombos != null ? newCombos : List.of();
        }

        public static ResearchResult success(Technology tech, List<Set<String>> newCombos) {
            return new ResearchResult(true, "Research successful", tech, newCombos);
        }

        public static ResearchResult failure(String message) {
            return new ResearchResult(false, message, null, null);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public Technology getTechnology() {
            return technology;
        }

        public List<Set<String>> getNewCombos() {
            return newCombos;
        }

        public boolean hasNewCombos() {
            return !newCombos.isEmpty();
        }
    }

    /**
     * Result of prerequisite validation.
     */
    public static class PrerequisiteValidation {
        private final boolean valid;
        private final List<String> missingPrerequisites;
        private final String message;

        public PrerequisiteValidation(boolean valid, List<String> missingPrerequisites, String message) {
            this.valid = valid;
            this.missingPrerequisites = missingPrerequisites;
            this.message = message;
        }

        public boolean isValid() {
            return valid;
        }

        public List<String> getMissingPrerequisites() {
            return missingPrerequisites;
        }

        public String getMessage() {
            return message;
        }
    }
}
