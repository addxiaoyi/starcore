package dev.starcore.starcore.title;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * 称号配置加载器
 * 从配置文件加载称号和徽章定义
 */
public class TitleConfigLoader {
    private final Plugin plugin;
    private final Logger logger;
    private final TitleService titleService;

    public TitleConfigLoader(Plugin plugin, TitleService titleService) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.titleService = titleService;
    }

    /**
     * 加载所有配置
     */
    public void loadAll() {
        loadTitles();
        loadBadges();
    }

    /**
     * 加载称号配置
     */
    public void loadTitles() {
        File titlesFile = new File(plugin.getDataFolder(), "titles.yml");
        if (!titlesFile.exists()) {
            plugin.saveResource("titles.yml", false);
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(titlesFile);
        ConfigurationSection titlesSection = config.getConfigurationSection("titles");

        if (titlesSection == null) {
            logger.warning("No titles section found in titles.yml");
            return;
        }

        int loaded = 0;
        for (String key : titlesSection.getKeys(false)) {
            try {
                Title title = loadTitle(key, titlesSection.getConfigurationSection(key));
                titleService.registerTitle(title);
                loaded++;
            } catch (Exception e) {
                logger.severe("Failed to load title: " + key + " - " + e.getMessage());
            }
        }

        logger.info("Loaded " + loaded + " titles from configuration");
    }

    /**
     * 加载单个称号
     */
    private Title loadTitle(String id, ConfigurationSection section) {
        if (section == null) {
            throw new IllegalArgumentException("Section is null for title: " + id);
        }

        String nameStr = section.getString("name", id);
        Component name = LegacyComponentSerializer.legacyAmpersand().deserialize(nameStr);

        String descStr = section.getString("description", "");
        Component description = LegacyComponentSerializer.legacyAmpersand().deserialize(descStr);

        String color = section.getString("color", "§f");
        int priority = section.getInt("priority", 0);

        Title.TitleType type;
        try {
            type = Title.TitleType.valueOf(section.getString("type", "NOVICE").toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.warning("Invalid title type for " + id + ", using NOVICE");
            type = Title.TitleType.NOVICE;
        }

        Material icon;
        try {
            icon = Material.valueOf(section.getString("icon", "NAME_TAG").toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.warning("Invalid icon material for " + id + ", using NAME_TAG");
            icon = Material.NAME_TAG;
        }

        List<String> unlockConditions = section.getStringList("unlock_conditions");
        List<String> rewards = section.getStringList("rewards");
        boolean hidden = section.getBoolean("hidden", false);

        return Title.builder(id)
            .name(name)
            .description(description)
            .color(color)
            .priority(priority)
            .type(type)
            .icon(icon)
            .unlockConditions(unlockConditions)
            .rewards(rewards)
            .hidden(hidden)
            .build();
    }

    /**
     * 加载徽章配置
     */
    public void loadBadges() {
        File badgesFile = new File(plugin.getDataFolder(), "badges.yml");
        if (!badgesFile.exists()) {
            plugin.saveResource("badges.yml", false);
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(badgesFile);
        ConfigurationSection badgesSection = config.getConfigurationSection("badges");

        if (badgesSection == null) {
            logger.warning("No badges section found in badges.yml");
            return;
        }

        int loaded = 0;
        for (String key : badgesSection.getKeys(false)) {
            try {
                Badge badge = loadBadge(key, badgesSection.getConfigurationSection(key));
                titleService.registerBadge(badge);
                loaded++;
            } catch (Exception e) {
                logger.severe("Failed to load badge: " + key + " - " + e.getMessage());
            }
        }

        logger.info("Loaded " + loaded + " badges from configuration");
    }

    /**
     * 加载单个徽章
     */
    private Badge loadBadge(String id, ConfigurationSection section) {
        if (section == null) {
            throw new IllegalArgumentException("Section is null for badge: " + id);
        }

        String nameStr = section.getString("name", id);
        Component name = LegacyComponentSerializer.legacyAmpersand().deserialize(nameStr);

        String descStr = section.getString("description", "");
        Component description = LegacyComponentSerializer.legacyAmpersand().deserialize(descStr);

        String icon = section.getString("icon", "★");

        Badge.Rarity rarity;
        try {
            rarity = Badge.Rarity.valueOf(section.getString("rarity", "COMMON").toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.warning("Invalid badge rarity for " + id + ", using COMMON");
            rarity = Badge.Rarity.COMMON;
        }

        List<String> unlockConditions = section.getStringList("unlock_conditions");

        return Badge.builder(id)
            .name(name)
            .icon(icon)
            .rarity(rarity)
            .description(description)
            .unlockConditions(unlockConditions)
            .build();
    }

    /**
     * 加载显示配置
     */
    public TitleDisplayConfig loadDisplayConfig() {
        File displayFile = new File(plugin.getDataFolder(), "display.yml");
        if (!displayFile.exists()) {
            plugin.saveResource("display.yml", false);
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(displayFile);
        TitleDisplayConfig displayConfig = new TitleDisplayConfig();

        // 加载Tab配置
        ConfigurationSection tabSection = config.getConfigurationSection("display.tab");
        if (tabSection != null) {
            displayConfig.setTabConfig(new TitleDisplayConfig.TabDisplayConfig(
                tabSection.getBoolean("enabled", true),
                tabSection.getString("prefix_format", "[{title}] "),
                tabSection.getString("suffix_format", ""),
                tabSection.getInt("priority", 100)
            ));
        }

        // 加载名字前缀配置
        ConfigurationSection nameSection = config.getConfigurationSection("display.name_prefix");
        if (nameSection != null) {
            displayConfig.setNamePrefixConfig(new TitleDisplayConfig.NamePrefixConfig(
                nameSection.getBoolean("enabled", true),
                nameSection.getString("format", "[{badge}]")
            ));
        }

        // 加载全息投影配置
        ConfigurationSection holoSection = config.getConfigurationSection("display.hologram");
        if (holoSection != null) {
            displayConfig.setHologramConfig(new TitleDisplayConfig.HologramConfig(
                holoSection.getBoolean("enabled", true),
                holoSection.getStringList("lines"),
                holoSection.getDouble("offset", -0.3),
                holoSection.getBoolean("follow_player", true)
            ));
        }

        // 加载聊天配置
        ConfigurationSection chatSection = config.getConfigurationSection("display.chat");
        if (chatSection != null) {
            displayConfig.setChatConfig(new TitleDisplayConfig.ChatPrefixConfig(
                chatSection.getBoolean("enabled", true),
                chatSection.getString("format", "[{title}] "),
                chatSection.getInt("priority", 100)
            ));
        }

        // 加载记分板配置
        ConfigurationSection scoreboardSection = config.getConfigurationSection("display.scoreboard");
        if (scoreboardSection != null) {
            displayConfig.setScoreboardConfig(new TitleDisplayConfig.ScoreboardConfig(
                scoreboardSection.getBoolean("enabled", true),
                scoreboardSection.getString("team_format", "{title}"),
                scoreboardSection.getInt("priority", 100)
            ));
        }

        logger.info("Loaded display configuration");
        return displayConfig;
    }

    /**
     * 重载所有配置
     */
    public void reload() {
        logger.info("Reloading title configurations...");
        loadAll();
    }
}
