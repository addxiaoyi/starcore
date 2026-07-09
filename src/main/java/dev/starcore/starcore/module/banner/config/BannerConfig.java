package dev.starcore.starcore.module.banner.config;

import dev.starcore.starcore.core.config.ConfigurationService;
import dev.starcore.starcore.module.banner.model.BannerPattern;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Configuration for the banner module
 */
public class BannerConfig {
    private final ConfigurationService configService;
    private final Map<String, Integer> patternCosts = new ConcurrentHashMap<>();

    public BannerConfig(ConfigurationService configService) {
        this.configService = configService;

        // Initialize default pattern costs
        for (BannerPattern pattern : BannerPattern.values()) {
            patternCosts.put(pattern.key(), pattern.cost());
        }

        // Load custom costs from config
        loadCustomCosts();
    }

    private void loadCustomCosts() {
        try {
            var customCosts = configService.getConfig().getConfigurationSection("banner.pattern-costs");
            if (customCosts != null) {
                for (String key : customCosts.getKeys(false)) {
                    int cost = customCosts.getInt(key, 0);
                    patternCosts.put(key.toLowerCase(), cost);
                }
            }
        } catch (Exception e) {
            // Config not loaded yet, use defaults
        }
    }

    public boolean isEnabled() {
        try {
            return configService.getConfig().getBoolean("enabled", true);
        } catch (Exception e) {
            return true;
        }
    }

    public int getPatternCost(String pattern) {
        return patternCosts.getOrDefault(pattern.toLowerCase(), 0);
    }

    public int getColorChangeCost() {
        try {
            return configService.getConfig().getInt("banner.color-change-cost", 50);
        } catch (Exception e) {
            return 50;
        }
    }

    public int getResetCost() {
        try {
            return configService.getConfig().getInt("banner.reset-cost", 0);
        } catch (Exception e) {
            return 0;
        }
    }

    public String getDefaultPattern() {
        try {
            return configService.getConfig().getString("banner.default.pattern", "plain");
        } catch (Exception e) {
            return "plain";
        }
    }

    public String getDefaultBaseColor() {
        try {
            return configService.getConfig().getString("banner.default.base-color", "WHITE");
        } catch (Exception e) {
            return "WHITE";
        }
    }

    public String getDefaultPatternColor() {
        try {
            return configService.getConfig().getString("banner.default.pattern-color", "BLACK");
        } catch (Exception e) {
            return "BLACK";
        }
    }

    public boolean isShowPreview() {
        try {
            return configService.getConfig().getBoolean("display.show-preview", true);
        } catch (Exception e) {
            return true;
        }
    }

    public boolean isShowActionbarInfo() {
        try {
            return configService.getConfig().getBoolean("display.show-actionbar-info", true);
        } catch (Exception e) {
            return true;
        }
    }

    public boolean isFeedbackEnabled() {
        try {
            return configService.getConfig().getBoolean("feedback.enabled", true);
        } catch (Exception e) {
            return true;
        }
    }

    public String getUpdateSound() {
        try {
            return configService.getConfig().getString("feedback.updated.sound", "UI_BUTTON_CLICK");
        } catch (Exception e) {
            return "UI_BUTTON_CLICK";
        }
    }

    public double getUpdateSoundVolume() {
        try {
            return configService.getConfig().getDouble("feedback.updated.sound-volume", 0.6);
        } catch (Exception e) {
            return 0.6;
        }
    }

    public boolean isShowOnMap() {
        try {
            return configService.getConfig().getBoolean("integration.show-on-map", true);
        } catch (Exception e) {
            return true;
        }
    }

    public boolean isShowInNationGui() {
        try {
            return configService.getConfig().getBoolean("integration.show-in-nation-gui", true);
        } catch (Exception e) {
            return true;
        }
    }

    public String getChangeBannerPermission() {
        try {
            return configService.getConfig().getString("permissions.change-banner", "starcore.banner.change");
        } catch (Exception e) {
            return "starcore.banner.change";
        }
    }

    public String getViewOtherBannersPermission() {
        try {
            return configService.getConfig().getString("permissions.view-other-banners", "starcore.banner.viewother");
        } catch (Exception e) {
            return "starcore.banner.viewother";
        }
    }

    public String getBypassCostPermission() {
        try {
            return configService.getConfig().getString("permissions.bypass-cost", "starcore.banner.bypass");
        } catch (Exception e) {
            return "starcore.banner.bypass";
        }
    }
}
