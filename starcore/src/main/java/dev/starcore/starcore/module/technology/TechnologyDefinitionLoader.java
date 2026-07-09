package dev.starcore.starcore.module.technology;

import dev.starcore.starcore.module.technology.model.TechnologyDefinition;
import dev.starcore.starcore.module.technology.model.TechnologyEffect;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads and caches technology definitions from the technologies.yml configuration file.
 */
public final class TechnologyDefinitionLoader {
    private final JavaPlugin plugin;
    private final String configPath;
    private final Map<String, TechnologyDefinition> definitions = new ConcurrentHashMap<>();
    private final Map<String, List<String>> techByEra = new ConcurrentHashMap<>();
    private final Map<String, List<String>> techByBranch = new ConcurrentHashMap<>();

    private volatile long lastLoadTime = 0;
    private static final long CACHE_TTL_MS = 60_000; // 1 minute cache TTL

    public TechnologyDefinitionLoader(JavaPlugin plugin) {
        this(plugin, "technologies.yml");
    }

    public TechnologyDefinitionLoader(JavaPlugin plugin, String configPath) {
        this.plugin = plugin;
        this.configPath = configPath;
        load();
    }

    /**
     * Loads or reloads technology definitions from the configuration file.
     */
    public synchronized void load() {
        try {
            // First try to load from plugin's data folder (user-modified)
            var file = plugin.getDataFolder().toPath().resolve(configPath);
            YamlConfiguration config;
            if (file.toFile().exists()) {
                config = YamlConfiguration.loadConfiguration(file.toFile());
            } else {
                // Fall back to resources (default)
                try (InputStream is = plugin.getResource(configPath)) {
                    if (is != null) {
                        config = YamlConfiguration.loadConfiguration(new InputStreamReader(is, StandardCharsets.UTF_8));
                    } else {
                        plugin.getLogger().warning("Technology config not found: " + configPath);
                        loadHardcodedDefaults();
                        return;
                    }
                }
            }

            definitions.clear();
            techByEra.clear();
            techByBranch.clear();

            var techSection = config.getConfigurationSection("technologies");
            if (techSection == null) {
                plugin.getLogger().warning("No 'technologies' section found in " + configPath);
                loadHardcodedDefaults();
                return;
            }

            for (String key : techSection.getKeys(false)) {
                try {
                    TechnologyDefinition definition = parseDefinition(key, techSection.getConfigurationSection(key));
                    if (definition != null) {
                        definitions.put(key.toLowerCase(Locale.ROOT), definition);

                        // Index by era
                        techByEra.computeIfAbsent(definition.era(), k -> new ArrayList<>()).add(key);

                        // Index by branch
                        techByBranch.computeIfAbsent(definition.branch(), k -> new ArrayList<>()).add(key);
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to parse technology '" + key + "': " + e.getMessage());
                }
            }

            lastLoadTime = System.currentTimeMillis();
            plugin.getLogger().info("Loaded " + definitions.size() + " technology definitions");
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load technology definitions: " + e.getMessage());
            loadHardcodedDefaults();
        }
    }

    /**
     * Loads hardcoded default technologies as fallback.
     */
    private void loadHardcodedDefaults() {
        definitions.clear();

        // Add default technologies matching the hardcoded list in TechnologyModule
        TechnologyDefinition logistics = new TechnologyDefinition(
            "logistics",
            "后勤学",
            "提高资源采集和运输效率",
            "stone_age",
            "economic",
            java.math.BigDecimal.valueOf(120),
            300,
            List.of(),
            List.of(),
            List.of(
                new TechnologyEffect("mining_speed", 0.1, "+10% 采矿速度"),
                new TechnologyEffect("movement_speed", 0.05, "+5% 移动速度")
            ),
            Map.of("food", 80L, "timber", 40L)
        );

        TechnologyDefinition steelWorking = new TechnologyDefinition(
            "steel_working",
            "钢铁冶炼",
            "掌握高级金属加工技术",
            "iron_age",
            "economic",
            java.math.BigDecimal.valueOf(180),
            450,
            List.of("logistics"),
            List.of(),
            List.of(
                new TechnologyEffect("mining_speed", 0.2, "+20% 采矿速度"),
                new TechnologyEffect("attack_damage", 0.1, "+10% 攻击伤害")
            ),
            Map.of("ore", 120L, "timber", 30L)
        );

        TechnologyDefinition radioCommand = new TechnologyDefinition(
            "radio_command",
            "无线电指挥",
            "实现战场实时通信",
            "industrial_age",
            "military",
            java.math.BigDecimal.valueOf(240),
            600,
            List.of("steel_working"),
            List.of(),
            List.of(
                new TechnologyEffect("team_damage_bonus", 0.15, "+15% 团队作战伤害"),
                new TechnologyEffect("communication_range", 999999, "无限通信范围")
            ),
            Map.of("rare_metal", 20L, "oil", 30L)
        );

        TechnologyDefinition mechanizedWarfare = new TechnologyDefinition(
            "mechanized_warfare",
            "机械化战争",
            "解锁机械单位和载具",
            "information_age",
            "military",
            java.math.BigDecimal.valueOf(360),
            900,
            List.of("radio_command"),
            List.of(),
            List.of(
                new TechnologyEffect("unlock_unit", 0, "解锁机械单位"),
                new TechnologyEffect("military_production", 0.5, "+50% 军事单位生产速度")
            ),
            Map.of("oil", 120L, "ore", 180L, "rare_metal", 40L)
        );

        TechnologyDefinition industrialPlanning = new TechnologyDefinition(
            "industrial_planning",
            "工业规划",
            "提高工业生产效率",
            "information_age",
            "economic",
            java.math.BigDecimal.valueOf(300),
            720,
            List.of("steel_working"),
            List.of(),
            List.of(
                new TechnologyEffect("production_multiplier", 1.5, "生产效率 x1.5"),
                new TechnologyEffect("research_speed", 0.2, "+20% 科研速度")
            ),
            Map.of("timber", 120L, "ore", 90L)
        );

        definitions.put("logistics", logistics);
        definitions.put("steel_working", steelWorking);
        definitions.put("radio_command", radioCommand);
        definitions.put("mechanized_warfare", mechanizedWarfare);
        definitions.put("industrial_planning", industrialPlanning);

        // Index by era
        techByEra.put("stone_age", List.of("logistics"));
        techByEra.put("iron_age", List.of("steel_working"));
        techByEra.put("industrial_age", List.of("radio_command"));
        techByEra.put("information_age", List.of("mechanized_warfare", "industrial_planning"));

        // Index by branch
        techByBranch.put("economic", List.of("logistics", "steel_working", "industrial_planning"));
        techByBranch.put("military", List.of("radio_command", "mechanized_warfare"));

        lastLoadTime = System.currentTimeMillis();
        plugin.getLogger().info("Loaded " + definitions.size() + " hardcoded technology definitions");
    }

    private TechnologyDefinition parseDefinition(String key, org.bukkit.configuration.ConfigurationSection section) {
        if (section == null) {
            return null;
        }

        String name = section.getString("name", key);
        String description = section.getString("description", "");
        String era = section.getString("era", "");
        String branch = section.getString("branch", "");

        java.math.BigDecimal cost = java.math.BigDecimal.valueOf(section.getDouble("cost", 0));
        int researchTime = section.getInt("research_time", 300);

        List<String> prerequisites = section.getStringList("prerequisites");
        List<String> mutuallyExclusive = section.getStringList("mutually_exclusive");

        // Parse resource_costs
        Map<String, Long> resourceCosts = new HashMap<>();
        org.bukkit.configuration.ConfigurationSection resourceSection = section.getConfigurationSection("resource_costs");
        if (resourceSection != null) {
            for (String resourceKey : resourceSection.getKeys(false)) {
                long amount = resourceSection.getLong(resourceKey, 0);
                if (amount > 0) {
                    resourceCosts.put(resourceKey.trim().toLowerCase(Locale.ROOT), amount);
                }
            }
        }

        // Parse effects
        List<TechnologyEffect> effects = new ArrayList<>();
        List<?> effectList = section.getList("effects");
        if (effectList != null) {
            for (Object effectObj : effectList) {
                if (effectObj instanceof Map<?, ?> effectMap) {
                    String type = (String) effectMap.get("type");
                    Object valueObj = effectMap.get("value");
                    Object descObj = effectMap.get("description");
                    String desc = descObj != null ? descObj.toString() : "";

                    double value = 0;
                    if (valueObj instanceof Number num) {
                        value = num.doubleValue();
                    } else if (valueObj instanceof String str) {
                        try {
                            value = Double.parseDouble(str);
                        } catch (NumberFormatException ignored) {
                            // String value like "copper_tools"
                        }
                    }

                    if (type != null && !type.isBlank()) {
                        effects.add(new TechnologyEffect(type.trim().toLowerCase(Locale.ROOT), value, desc));
                    }
                }
            }
        }

        return new TechnologyDefinition(
            key,
            name,
            description,
            era,
            branch,
            cost,
            researchTime,
            prerequisites,
            mutuallyExclusive,
            effects,
            resourceCosts
        );
    }

    /**
     * Gets a technology definition by key.
     *
     * @param key The technology key
     * @return The definition, or null if not found
     */
    public TechnologyDefinition load(String key) {
        String normalized = key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
        return definitions.get(normalized);
    }

    /**
     * Gets all technology definitions.
     */
    public Map<String, TechnologyDefinition> getAll() {
        return Map.copyOf(definitions);
    }

    /**
     * Gets all technologies in a specific era.
     */
    public List<TechnologyDefinition> getByEra(String era) {
        String normalized = era == null ? "" : era.trim().toLowerCase(Locale.ROOT);
        return techByEra.getOrDefault(normalized, List.of()).stream()
            .map(definitions::get)
            .filter(d -> d != null)
            .toList();
    }

    /**
     * Gets all technologies in a specific branch.
     */
    public List<TechnologyDefinition> getByBranch(String branch) {
        String normalized = branch == null ? "" : branch.trim().toLowerCase(Locale.ROOT);
        return techByBranch.getOrDefault(normalized, List.of()).stream()
            .map(definitions::get)
            .filter(d -> d != null)
            .toList();
    }

    /**
     * Gets all available eras.
     */
    public List<String> getAvailableEras() {
        return List.copyOf(techByEra.keySet());
    }

    /**
     * Gets all available branches.
     */
    public List<String> getAvailableBranches() {
        return List.copyOf(techByBranch.keySet());
    }

    /**
     * Reloads if cache is stale.
     */
    public void reloadIfStale() {
        if (System.currentTimeMillis() - lastLoadTime > CACHE_TTL_MS) {
            load();
        }
    }

    /**
     * Forces a reload of all technology definitions.
     */
    public synchronized void forceReload() {
        load();
    }
}
