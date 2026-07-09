package dev.starcore.starcore.module.policy.config;

import dev.starcore.starcore.module.policy.model.PolicyCategory;
import dev.starcore.starcore.module.policy.model.PolicyDefinition;
import dev.starcore.starcore.module.policy.model.PolicyEffect;
import dev.starcore.starcore.module.policy.model.PolicyEffectScope;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * 从 YAML 配置文件加载国策定义
 */
public class YamlPolicyDefinitionLoader {

    private final Plugin plugin;
    private final Logger logger;
    private static final String DEFAULT_FILE_NAME = "policies.yml";

    public YamlPolicyDefinitionLoader(Plugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    /**
     * 从默认文件加载国策定义
     * @return 国策定义映射，如果加载失败则返回空映射
     */
    public Map<String, PolicyDefinition> load() {
        return load(DEFAULT_FILE_NAME);
    }

    /**
     * 从指定文件加载国策定义
     * @param fileName 文件名
     * @return 国策定义映射
     */
    public Map<String, PolicyDefinition> load(String fileName) {
        File file = new File(plugin.getDataFolder(), fileName);

        // 如果文件不存在，创建默认配置
        if (!file.exists()) {
            createDefaultConfig(file);
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        Map<String, PolicyDefinition> definitions = new LinkedHashMap<>();

        ConfigurationSection policiesSection = config.getConfigurationSection("policies");
        if (policiesSection == null) {
            logger.warning("No 'policies' section found in " + fileName);
            return definitions;
        }

        for (String key : policiesSection.getKeys(false)) {
            try {
                ConfigurationSection policySection = policiesSection.getConfigurationSection(key);
                if (policySection == null) {
                    logger.warning("Invalid policy section: " + key);
                    continue;
                }
                PolicyDefinition definition = parsePolicy(key, policySection);
                definitions.put(definition.key(), definition);
                logger.info("Loaded policy definition: " + definition.key());
            } catch (Exception e) {
                logger.warning("Failed to parse policy '" + key + "': " + e.getMessage());
            }
        }

        return definitions;
    }

    /**
     * 保存国策定义到配置文件
     * @param definitions 国策定义映射
     */
    public void save(Map<String, PolicyDefinition> definitions) {
        save(definitions, DEFAULT_FILE_NAME);
    }

    /**
     * 保存国策定义到指定配置文件
     * @param definitions 国策定义映射
     * @param fileName 文件名
     */
    public void save(Map<String, PolicyDefinition> definitions, String fileName) {
        File file = new File(plugin.getDataFolder(), fileName);
        FileConfiguration config = new YamlConfiguration();

        for (PolicyDefinition definition : definitions.values()) {
            String path = "policies." + definition.key();
            config.set(path + ".display-name", definition.displayName());
            config.set(path + ".category", definition.category().name());
            config.set(path + ".treasury-cost", definition.treasuryCost().toPlainString());
            config.set(path + ".duration-seconds", definition.durationSeconds());
            config.set(path + ".cooldown-seconds", definition.cooldownSeconds());
            config.set(path + ".prerequisite-keys", definition.prerequisiteKeys().stream().toList());
            config.set(path + ".conflict-keys", definition.conflictKeys().stream().toList());

            // 保存效果列表
            List<Map<String, Object>> effects = definition.effects().stream()
                .map(effect -> {
                    Map<String, Object> effectMap = new LinkedHashMap<>();
                    effectMap.put("key", effect.key());
                    effectMap.put("scope", effect.scope().name());
                    effectMap.put("modifier", effect.modifier());
                    effectMap.put("description", effect.description());
                    return effectMap;
                })
                .toList();
            config.set(path + ".effects", effects);
        }

        try {
            config.save(file);
            logger.info("Saved policy definitions to " + fileName);
        } catch (IOException e) {
            logger.warning("Failed to save policy definitions: " + e.getMessage());
        }
    }

    private PolicyDefinition parsePolicy(String key, ConfigurationSection section) {
        String displayName = section.getString("display-name", key);
        PolicyCategory category = parseCategory(section.getString("category", "INTERNAL"));
        BigDecimal treasuryCost = new BigDecimal(section.getString("treasury-cost", "0"));
        long durationSeconds = section.getLong("duration-seconds", 86400L);
        long cooldownSeconds = section.getLong("cooldown-seconds", 3600L);

        List<String> prerequisites = section.getStringList("prerequisite-keys");
        List<String> conflicts = section.getStringList("conflict-keys");

        List<?> effectsRaw = section.getList("effects", List.of());
        List<PolicyEffect> effects = effectsRaw.stream()
            .filter(e -> e instanceof Map)
            .map(e -> parseEffect((Map<?, ?>) e))
            .toList();

        return new PolicyDefinition(
            key,
            displayName,
            category,
            Set.copyOf(prerequisites),
            treasuryCost,
            durationSeconds,
            cooldownSeconds,
            Set.copyOf(conflicts),
            effects
        );
    }

    @SuppressWarnings("unchecked")
    private PolicyEffect parseEffect(Map<?, ?> effectMap) {
        String key = String.valueOf(effectMap.get("key"));
        String scopeStr = String.valueOf(effectMap.get("scope"));
        PolicyEffectScope scope = parseScope(scopeStr == null ? "GLOBAL" : scopeStr);
        Map<String, Object> typedMap = (Map<String, Object>) effectMap;
        double modifier = typedMap.containsKey("modifier") ? ((Number) typedMap.get("modifier")).doubleValue() : 0.0;
        String description = typedMap.containsKey("description") ? String.valueOf(typedMap.get("description")) : "";

        return new PolicyEffect(key, scope, modifier, description);
    }

    private PolicyCategory parseCategory(String category) {
        String normalized = category.toUpperCase();
        // Preserve legacy broad category mapping even though compatibility enum constants exist.
        switch (normalized) {
            case "ECONOMY" -> { return PolicyCategory.INDUSTRY; }
            case "MILITARY" -> { return PolicyCategory.DEFENSE; }
            case "INTERNAL" -> { return PolicyCategory.ADMINISTRATION; }
            case "DIPLOMACY" -> { return PolicyCategory.FOREIGN_POLICY; }
            default -> { }
        }
        try {
            return PolicyCategory.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            return PolicyCategory.ADMINISTRATION;
        }
    }

    private PolicyEffectScope parseScope(String scope) {
        try {
            return PolicyEffectScope.valueOf(scope.toUpperCase());
        } catch (IllegalArgumentException e) {
            return PolicyEffectScope.GLOBAL;
        }
    }

    private void createDefaultConfig(File file) {
        FileConfiguration config = new YamlConfiguration();

        // 创建示例国策
        String basePath = "policies.";

        // 经济国策：民事工业
        config.set(basePath + "civil_industry.display-name", "Civil Industry");
        config.set(basePath + "civil_industry.category", "ECONOMY");
        config.set(basePath + "civil_industry.treasury-cost", "250.00");
        config.set(basePath + "civil_industry.duration-seconds", 86400L);
        config.set(basePath + "civil_industry.cooldown-seconds", 3600L);
        config.set(basePath + "civil_industry.prerequisite-keys", List.of());
        config.set(basePath + "civil_industry.conflict-keys", List.of("military_drill"));
        config.set(basePath + "civil_industry.effects", List.of(
            Map.of("key", "production_bonus", "scope", "GLOBAL", "modifier", 0.12, "description", "National production and infrastructure output +12%")
        ));

        // 经济国策：商业聚焦
        config.set(basePath + "mercantile_focus.display-name", "Mercantile Focus");
        config.set(basePath + "mercantile_focus.category", "ECONOMY");
        config.set(basePath + "mercantile_focus.treasury-cost", "150.00");
        config.set(basePath + "mercantile_focus.duration-seconds", 86400L);
        config.set(basePath + "mercantile_focus.cooldown-seconds", 3600L);
        config.set(basePath + "mercantile_focus.prerequisite-keys", List.of("civil_industry"));
        config.set(basePath + "mercantile_focus.conflict-keys", List.of());
        config.set(basePath + "mercantile_focus.effects", List.of(
            Map.of("key", "trade_income_bonus", "scope", "GLOBAL", "modifier", 0.10, "description", "Trade and tariff income +10%")
        ));

        // 军事国策：军事训练
        config.set(basePath + "military_drill.display-name", "Military Drill");
        config.set(basePath + "military_drill.category", "MILITARY");
        config.set(basePath + "military_drill.treasury-cost", "300.00");
        config.set(basePath + "military_drill.duration-seconds", 43200L);
        config.set(basePath + "military_drill.cooldown-seconds", 7200L);
        config.set(basePath + "military_drill.prerequisite-keys", List.of());
        config.set(basePath + "military_drill.conflict-keys", List.of("civil_industry", "open_diplomacy"));
        config.set(basePath + "military_drill.effects", List.of(
            Map.of("key", "combat_readiness", "scope", "PLAYER", "modifier", 0.08, "description", "Citizen combat readiness +8% inside national operations")
        ));

        // 内战国策：巩固边防
        config.set(basePath + "fortified_borders.display-name", "Fortified Borders");
        config.set(basePath + "fortified_borders.category", "INTERNAL");
        config.set(basePath + "fortified_borders.treasury-cost", "500.00");
        config.set(basePath + "fortified_borders.duration-seconds", 172800L);
        config.set(basePath + "fortified_borders.cooldown-seconds", 14400L);
        config.set(basePath + "fortified_borders.prerequisite-keys", List.of("military_drill"));
        config.set(basePath + "fortified_borders.conflict-keys", List.of("open_diplomacy"));
        config.set(basePath + "fortified_borders.effects", List.of(
            Map.of("key", "claim_defense_bonus", "scope", "TERRITORY", "modifier", 0.15, "description", "Claim defense and siege resistance +15%")
        ));

        // 外交国策：开放外交
        config.set(basePath + "open_diplomacy.display-name", "Open Diplomacy");
        config.set(basePath + "open_diplomacy.category", "DIPLOMACY");
        config.set(basePath + "open_diplomacy.treasury-cost", "100.00");
        config.set(basePath + "open_diplomacy.duration-seconds", 86400L);
        config.set(basePath + "open_diplomacy.cooldown-seconds", 3600L);
        config.set(basePath + "open_diplomacy.prerequisite-keys", List.of());
        config.set(basePath + "open_diplomacy.conflict-keys", List.of("fortified_borders", "military_drill"));
        config.set(basePath + "open_diplomacy.effects", List.of(
            Map.of("key", "diplomacy_point_bonus", "scope", "GLOBAL", "modifier", 0.20, "description", "Diplomacy point generation +20%")
        ));

        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            config.save(file);
            logger.info("Created default policy configuration: " + file.getName());
        } catch (IOException e) {
            logger.warning("Failed to create default policy configuration: " + e.getMessage());
        }
    }
}
