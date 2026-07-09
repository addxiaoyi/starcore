package dev.starcore.starcore.module.territory.upgrade;

import dev.starcore.starcore.module.territory.upgrade.model.TerritoryUpgradeLevel;
import dev.starcore.starcore.module.territory.upgrade.model.UpgradeTierDefinition;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Loads and manages upgrade tier definitions from configuration.
 * 从配置文件加载升级层级定义
 */
public class UpgradeDefinitionLoader {
    private static final String FILE_NAME = "territory_upgrades.yml";

    private final JavaPlugin plugin;
    private final Logger logger;
    private final Map<String, UpgradeTierDefinition> paths;

    public UpgradeDefinitionLoader(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.paths = new HashMap<>();
        loadDefinitions();
    }

    /**
     * Load definitions from config file.
     */
    public void loadDefinitions() {
        paths.clear();

        // 保存默认配置
        plugin.saveResource(FILE_NAME, false);

        // 加载配置文件
        File configFile = new File(plugin.getDataFolder(), FILE_NAME);
        FileConfiguration config;

        if (configFile.exists()) {
            config = YamlConfiguration.loadConfiguration(configFile);
        } else {
            // 从 JAR 内置资源加载
            InputStream defaultStream = plugin.getResource(FILE_NAME);
            if (defaultStream != null) {
                config = YamlConfiguration.loadConfiguration(new InputStreamReader(
                    defaultStream, StandardCharsets.UTF_8));
            } else {
                logger.warning("Cannot find " + FILE_NAME + " or bundled resource");
                return;
            }
        }

        // 加载升级路径
        if (config.contains("upgrade-paths")) {
            Map<String, Object> upgradePaths = config.getConfigurationSection("upgrade-paths").getValues(false);

            for (Map.Entry<String, Object> entry : upgradePaths.entrySet()) {
                String pathId = entry.getKey();
                Object pathData = entry.getValue();

                if (pathData instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> pathMap = (Map<String, Object>) pathData;
                    UpgradeTierDefinition definition = parsePathDefinition(pathId, pathMap);
                    if (definition != null) {
                        paths.put(definition.pathId(), definition);
                    }
                }
            }
        }

        logger.info("Loaded " + paths.size() + " upgrade paths");
    }

    private UpgradeTierDefinition parsePathDefinition(String pathId, Map<String, Object> pathMap) {
        String name = pathMap.getOrDefault("name", pathId).toString();
        String description = pathMap.getOrDefault("description", "").toString();
        String color = pathMap.getOrDefault("color", "&f").toString();

        List<TerritoryUpgradeLevel> tiers = List.of();

        Object tiersObj = pathMap.get("tiers");
        if (tiersObj instanceof List) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> tiersList = (List<Map<String, Object>>) tiersObj;
            tiers = tiersList.stream()
                .map(this::parseTier)
                .filter(Objects::nonNull)
                .toList();
        }

        return new UpgradeTierDefinition(pathId, name, description, color, tiers);
    }

    private TerritoryUpgradeLevel parseTier(Map<String, Object> tierMap) {
        try {
            int level = getInt(tierMap, "level", 0);
            String name = tierMap.getOrDefault("name", "Level " + level).toString();
            int expRequired = getInt(tierMap, "exp_required", 0);
            String description = tierMap.getOrDefault("description", "").toString();

            // 解析收益
            Map<String, Object> benefits = new HashMap<>();
            Object benefitsObj = tierMap.get("benefits");
            if (benefitsObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> benefitsMap = (Map<String, Object>) benefitsObj;
                benefits.putAll(benefitsMap);
            }

            // 解析解锁权限
            List<String> permissions = List.of();
            Object permissionsObj = tierMap.get("unlock_permissions");
            if (permissionsObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> permsList = (List<String>) permissionsObj;
                permissions = permsList;
            }

            // 解析前置条件
            List<String> prerequisites = List.of();
            Object prereqObj = tierMap.get("prerequisites");
            if (prereqObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> prereqList = (List<String>) prereqObj;
                prerequisites = prereqList;
            }

            return new TerritoryUpgradeLevel(
                level, name, expRequired, benefits, permissions, prerequisites, description
            );
        } catch (Exception e) {
            logger.warning("Failed to parse tier: " + e.getMessage());
            return null;
        }
    }

    private int getInt(Map<String, Object> map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }

    /**
     * Get all loaded paths.
     */
    public Map<String, UpgradeTierDefinition> getAllPaths() {
        return Map.copyOf(paths);
    }

    /**
     * Get a specific path definition.
     */
    public UpgradeTierDefinition getPath(String pathId) {
        return paths.get(pathId.toLowerCase());
    }

    /**
     * Check if a path exists.
     */
    public boolean hasPath(String pathId) {
        return paths.containsKey(pathId.toLowerCase());
    }

    /**
     * Reload definitions from disk.
     */
    public void reload() {
        loadDefinitions();
    }
}
