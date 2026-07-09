package dev.starcore.starcore.technology;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Manages the technology tree structure and relationships.
 * <p>
 * The TechnologyTree is responsible for loading technology definitions from YAML configuration,
 * maintaining the relationships between technologies (prerequisites, mutual exclusions, combos),
 * and providing query methods for the technology system.
 * </p>
 * <p>
 * The technology tree is loaded once during initialization and remains immutable during runtime.
 * </p>
 *
 * @author StarCore Development Team
 * @since 1.0.0
 */
public class TechnologyTree {
    private static final String CONFIG_FILE_NAME = "technologies.yml";

    private final Map<String, Technology> technologies;
    private final Map<TechnologyEra, List<Technology>> technologiesByEra;
    private final Map<TechnologyBranch, List<Technology>> technologiesByBranch;
    private final Logger logger;

    /**
     * Constructs a new TechnologyTree.
     *
     * @param technologies the map of technology IDs to Technology objects
     * @param logger       the logger for error reporting
     */
    private TechnologyTree(Map<String, Technology> technologies, Logger logger) {
        this.technologies = Map.copyOf(technologies);
        this.logger = logger;

        // Build era index
        Map<TechnologyEra, List<Technology>> eraMap = new HashMap<>();
        for (TechnologyEra era : TechnologyEra.values()) {
            eraMap.put(era, new ArrayList<>());
        }
        for (Technology tech : technologies.values()) {
            eraMap.get(tech.era()).add(tech);
        }
        this.technologiesByEra = Map.copyOf(eraMap);

        // Build branch index
        Map<TechnologyBranch, List<Technology>> branchMap = new HashMap<>();
        for (TechnologyBranch branch : TechnologyBranch.values()) {
            branchMap.put(branch, new ArrayList<>());
        }
        for (Technology tech : technologies.values()) {
            branchMap.get(tech.branch()).add(tech);
        }
        this.technologiesByBranch = Map.copyOf(branchMap);
    }

    /**
     * Gets a technology by its ID.
     *
     * @param techId the technology ID
     * @return the Technology object, or null if not found
     */
    public Technology getTechnology(String techId) {
        return technologies.get(techId);
    }

    /**
     * Gets all technologies in the tree.
     *
     * @return an unmodifiable collection of all technologies
     */
    public List<Technology> getAllTechnologies() {
        return new ArrayList<>(technologies.values());
    }

    /**
     * Gets all technologies in a specific era.
     *
     * @param era the technology era
     * @return list of technologies in that era
     */
    public List<Technology> getTechnologiesInEra(TechnologyEra era) {
        return new ArrayList<>(technologiesByEra.getOrDefault(era, List.of()));
    }

    /**
     * Gets all technologies in a specific branch.
     *
     * @param branch the technology branch
     * @return list of technologies in that branch
     */
    public List<Technology> getTechnologiesInBranch(TechnologyBranch branch) {
        return new ArrayList<>(technologiesByBranch.getOrDefault(branch, List.of()));
    }

    /**
     * Gets all technologies that have no prerequisites (starting technologies).
     *
     * @return list of starting technologies
     */
    public List<Technology> getStartingTechnologies() {
        return technologies.values().stream()
            .filter(tech -> !tech.hasPrerequisites())
            .collect(Collectors.toList());
    }

    /**
     * Gets all technologies that can be researched given current progress.
     *
     * @param unlockedTechnologies set of already unlocked technology IDs
     * @return list of researchable technologies
     */
    public List<Technology> getAvailableTechnologies(Set<String> unlockedTechnologies) {
        return technologies.values().stream()
            .filter(tech -> !unlockedTechnologies.contains(tech.id()))
            .filter(tech -> tech.canResearch(unlockedTechnologies))
            .collect(Collectors.toList());
    }

    /**
     * Checks if a technology exists in the tree.
     *
     * @param techId the technology ID
     * @return true if the technology exists
     */
    public boolean containsTechnology(String techId) {
        return technologies.containsKey(techId);
    }

    /**
     * Validates if all prerequisites for a technology are met.
     *
     * @param techId               the technology ID to check
     * @param unlockedTechnologies set of unlocked technology IDs
     * @return true if all prerequisites are met
     */
    public boolean canResearch(String techId, Set<String> unlockedTechnologies) {
        Technology tech = technologies.get(techId);
        if (tech == null) {
            return false;
        }
        return tech.canResearch(unlockedTechnologies);
    }

    /**
     * Gets all prerequisite technologies for a given technology (recursive).
     *
     * @param techId the technology ID
     * @return set of all prerequisite technology IDs
     */
    public Set<String> getAllPrerequisites(String techId) {
        Technology tech = technologies.get(techId);
        if (tech == null) {
            return Set.of();
        }

        Set<String> allPrereqs = new HashSet<>();
        collectPrerequisites(tech, allPrereqs);
        return allPrereqs;
    }

    private void collectPrerequisites(Technology tech, Set<String> collected) {
        for (String prereqId : tech.prerequisites()) {
            if (collected.add(prereqId)) {
                Technology prereq = technologies.get(prereqId);
                if (prereq != null) {
                    collectPrerequisites(prereq, collected);
                }
            }
        }
    }

    /**
     * Gets all technologies that depend on a given technology (reverse lookup).
     *
     * @param techId the technology ID
     * @return set of technology IDs that require this technology
     */
    public Set<String> getDependentTechnologies(String techId) {
        return technologies.values().stream()
            .filter(tech -> tech.prerequisites().contains(techId))
            .map(Technology::id)
            .collect(Collectors.toSet());
    }

    /**
     * Gets the total number of technologies in the tree.
     *
     * @return the technology count
     */
    public int size() {
        return technologies.size();
    }

    /**
     * Loads the technology tree from the plugin's configuration file.
     *
     * @param plugin the plugin instance
     * @return the loaded TechnologyTree
     * @throws IOException if the configuration file cannot be loaded
     */
    public static TechnologyTree load(JavaPlugin plugin) throws IOException {
        Logger logger = plugin.getLogger();
        File configFile = new File(plugin.getDataFolder(), CONFIG_FILE_NAME);

        // Create default config if it doesn't exist
        if (!configFile.exists()) {
            plugin.getDataFolder().mkdirs();
            try (InputStream in = plugin.getResource(CONFIG_FILE_NAME)) {
                if (in != null) {
                    Files.copy(in, configFile.toPath());
                    logger.info("Created default " + CONFIG_FILE_NAME);
                } else {
                    logger.warning("Default " + CONFIG_FILE_NAME + " not found in plugin resources");
                }
            }
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        Map<String, Technology> technologies = new HashMap<>();

        ConfigurationSection techSection = config.getConfigurationSection("technologies");
        if (techSection == null) {
            logger.warning("No 'technologies' section found in " + CONFIG_FILE_NAME);
            return new TechnologyTree(Map.of(), logger);
        }

        for (String techId : techSection.getKeys(false)) {
            try {
                Technology tech = loadTechnology(techId, techSection.getConfigurationSection(techId), logger);
                technologies.put(techId, tech);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to load technology: " + techId, e);
            }
        }

        logger.info("Loaded " + technologies.size() + " technologies from " + CONFIG_FILE_NAME);
        return new TechnologyTree(technologies, logger);
    }

    private static Technology loadTechnology(String id, ConfigurationSection section, Logger logger) {
        String name = section.getString("name", id);
        String description = section.getString("description", "");

        TechnologyEra era = TechnologyEra.fromId(section.getString("era", "stone_age"));
        if (era == null) {
            era = TechnologyEra.STONE_AGE;
            logger.warning("Invalid era for technology " + id + ", defaulting to STONE_AGE");
        }

        TechnologyBranch branch = TechnologyBranch.fromId(section.getString("branch", "economic"));
        if (branch == null) {
            branch = TechnologyBranch.ECONOMIC;
            logger.warning("Invalid branch for technology " + id + ", defaulting to ECONOMIC");
        }

        List<String> prerequisites = section.getStringList("prerequisites");
        List<String> mutuallyExclusive = section.getStringList("mutually_exclusive");

        BigDecimal treasuryCost = new BigDecimal(section.getString("cost", "0"));
        int researchTime = section.getInt("research_time", 0);

        Map<String, Long> resourceCosts = new HashMap<>();
        ConfigurationSection resourceSection = section.getConfigurationSection("resource_costs");
        if (resourceSection != null) {
            for (String resource : resourceSection.getKeys(false)) {
                resourceCosts.put(resource, resourceSection.getLong(resource));
            }
        }

        // Load effects (simplified for now - can be extended)
        List<TechnologyEffect> effects = new ArrayList<>();
        List<?> effectsList = section.getList("effects");
        if (effectsList != null) {
            // Effects will be loaded by effect factory in full implementation
            // For now, we just track that they exist
        }

        // Load combos
        List<Set<String>> combos = new ArrayList<>();
        List<?> combosList = section.getList("combos");
        if (combosList != null) {
            for (Object combo : combosList) {
                if (combo instanceof List<?> comboList) {
                    Set<String> comboSet = new HashSet<>();
                    for (Object techIdObj : comboList) {
                        if (techIdObj instanceof String techIdStr) {
                            comboSet.add(techIdStr);
                        }
                    }
                    if (!comboSet.isEmpty()) {
                        combos.add(comboSet);
                    }
                }
            }
        }

        return new Technology(
            id, name, description, era, branch,
            prerequisites, mutuallyExclusive,
            treasuryCost, resourceCosts, researchTime,
            effects, combos
        );
    }
}
