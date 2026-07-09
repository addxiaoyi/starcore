package dev.starcore.starcore.region;

import net.kyori.adventure.text.format.TextColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * 区域配置加载器
 * 从 region-config.yml 加载区域显示配置
 */
public class RegionConfigLoader {
    private final Plugin plugin;
    private final Logger logger;
    private final File configFile;
    private FileConfiguration config;

    public RegionConfigLoader(Plugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.configFile = new File(plugin.getDataFolder(), "region-config.yml");
        loadConfig();
    }

    /**
     * 加载配置文件
     */
    private void loadConfig() {
        // 如果配置文件不存在，从资源中复制
        if (!configFile.exists()) {
            plugin.getDataFolder().mkdirs();
            try (InputStream in = plugin.getResource("region-config.yml")) {
                if (in != null) {
                    Files.copy(in, configFile.toPath());
                    logger.info("已创建默认区域配置文件: region-config.yml");
                }
            } catch (IOException e) {
                logger.warning("无法创建默认区域配置文件: " + e.getMessage());
            }
        }

        // 加载配置
        this.config = YamlConfiguration.loadConfiguration(configFile);
    }

    /**
     * 重新加载配置
     */
    public void reload() {
        this.config = YamlConfiguration.loadConfiguration(configFile);
        logger.info("已重新加载区域配置");
    }

    /**
     * 检查是否启用
     */
    public boolean isEnabled() {
        return config.getBoolean("enabled", true);
    }

    /**
     * 获取默认配置
     */
    public RegionTitleService.RegionDisplayConfig getDefaultConfig() {
        ConfigurationSection section = config.getConfigurationSection("default");
        if (section == null) {
            return createFallbackConfig();
        }

        return parseConfig(section);
    }

    /**
     * 获取自定义区域配置
     */
    public Map<String, RegionTitleService.RegionDisplayConfig> getCustomRegionConfigs() {
        Map<String, RegionTitleService.RegionDisplayConfig> configs = new HashMap<>();

        ConfigurationSection section = config.getConfigurationSection("custom-regions");
        if (section == null) {
            return configs;
        }

        for (String key : section.getKeys(false)) {
            ConfigurationSection regionSection = section.getConfigurationSection(key);
            if (regionSection != null) {
                configs.put(key, parseConfig(regionSection));
            }
        }

        return configs;
    }

    /**
     * 获取区域类型默认配置
     */
    public Map<RegionEnterEvent.RegionType, RegionTitleService.RegionDisplayConfig> getTypeDefaults() {
        Map<RegionEnterEvent.RegionType, RegionTitleService.RegionDisplayConfig> defaults = new HashMap<>();

        ConfigurationSection section = config.getConfigurationSection("type-defaults");
        if (section == null) {
            return defaults;
        }

        for (String typeKey : section.getKeys(false)) {
            RegionEnterEvent.RegionType type = parseRegionType(typeKey);
            if (type != null) {
                ConfigurationSection typeSection = section.getConfigurationSection(typeKey);
                if (typeSection != null) {
                    // 合并默认配置和类型特定配置
                    RegionTitleService.RegionDisplayConfig defaultConfig = getDefaultConfig();
                    RegionTitleService.RegionDisplayConfig typeConfig = parseConfig(typeSection, defaultConfig);
                    defaults.put(type, typeConfig);
                }
            }
        }

        return defaults;
    }

    /**
     * 解析配置节
     */
    private RegionTitleService.RegionDisplayConfig parseConfig(ConfigurationSection section) {
        return parseConfig(section, null);
    }

    /**
     * 解析配置节（带默认值）
     */
    private RegionTitleService.RegionDisplayConfig parseConfig(ConfigurationSection section,
                                                              RegionTitleService.RegionDisplayConfig defaultConfig) {
        Duration fadeIn = defaultConfig != null ? defaultConfig.getFadeIn()
            : Duration.ofMillis(section.getLong("fade-in", 500));
        Duration stay = defaultConfig != null ? defaultConfig.getStay()
            : Duration.ofMillis(section.getLong("stay", 3000));
        Duration fadeOut = defaultConfig != null ? defaultConfig.getFadeOut()
            : Duration.ofMillis(section.getLong("fade-out", 500));

        // 解析颜色
        String colorStr = section.getString("title-color");
        TextColor titleColor;
        if (colorStr != null && !colorStr.isEmpty()) {
            titleColor = parseColor(colorStr);
        } else if (defaultConfig != null) {
            titleColor = defaultConfig.getTitleColor();
        } else {
            titleColor = TextColor.color(255, 215, 0); // 默认金色
        }

        boolean showTitle = section.getBoolean("show-title",
            defaultConfig != null ? defaultConfig.isShowTitle() : true);
        boolean showSubtitle = section.getBoolean("show-subtitle",
            defaultConfig != null ? defaultConfig.isShowSubtitle() : true);

        String soundEffect = section.getString("sound-effect", "");
        if ((soundEffect == null || soundEffect.isEmpty()) && defaultConfig != null) {
            soundEffect = defaultConfig.getSoundEffect();
        }

        return new RegionTitleService.RegionDisplayConfig(
            fadeIn, stay, fadeOut, titleColor, showTitle, showSubtitle,
            (soundEffect == null || soundEffect.isEmpty()) ? null : soundEffect
        );
    }

    /**
     * 解析颜色字符串
     */
    private TextColor parseColor(String colorStr) {
        if (colorStr.startsWith("#")) {
            try {
                int rgb = Integer.parseInt(colorStr.substring(1), 16);
                return TextColor.color(rgb);
            } catch (NumberFormatException e) {
                logger.warning("无效的颜色格式: " + colorStr);
                return TextColor.color(255, 215, 0);
            }
        }
        return TextColor.color(255, 215, 0);
    }

    /**
     * 解析区域类型
     */
    private RegionEnterEvent.RegionType parseRegionType(String typeKey) {
        return switch (typeKey.toLowerCase()) {
            case "kingdom" -> RegionEnterEvent.RegionType.KINGDOM;
            case "territory" -> RegionEnterEvent.RegionType.TERRITORY;
            case "sub-region", "subregion" -> RegionEnterEvent.RegionType.SUB_REGION;
            case "biome" -> RegionEnterEvent.RegionType.BIOME;
            case "custom" -> RegionEnterEvent.RegionType.CUSTOM;
            default -> null;
        };
    }

    /**
     * 创建默认的回退配置
     */
    private RegionTitleService.RegionDisplayConfig createFallbackConfig() {
        return new RegionTitleService.RegionDisplayConfig(
            Duration.ofMillis(500),
            Duration.ofSeconds(3),
            Duration.ofMillis(500),
            TextColor.color(255, 215, 0),
            true,
            true,
            null
        );
    }
}
