package dev.starcore.starcore.region;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 区域标题显示服务
 * 负责在玩家进入区域时显示标题
 */
public class RegionTitleService {
    private final Plugin plugin;
    private final RegionIntegrationService integrationService;
    private final RegionConfigLoader configLoader;

    // 玩家当前所在区域缓存
    private final Map<UUID, String> playerCurrentRegion = new ConcurrentHashMap<>();

    // 区域显示配置
    private final Map<String, RegionDisplayConfig> regionConfigs = new ConcurrentHashMap<>();

    // 类型默认配置
    private final Map<RegionEnterEvent.RegionType, RegionDisplayConfig> typeDefaults = new ConcurrentHashMap<>();

    // 默认配置
    private RegionDisplayConfig defaultConfig;

    public RegionTitleService(Plugin plugin, RegionIntegrationService integrationService) {
        this.plugin = plugin;
        this.integrationService = integrationService;
        this.configLoader = new RegionConfigLoader(plugin);
        loadConfigurations();
    }

    /**
     * 加载配置
     */
    private void loadConfigurations() {
        this.defaultConfig = configLoader.getDefaultConfig();
        this.regionConfigs.clear();
        this.regionConfigs.putAll(configLoader.getCustomRegionConfigs());
        this.typeDefaults.clear();
        this.typeDefaults.putAll(configLoader.getTypeDefaults());
    }

    /**
     * 重新加载配置
     */
    public void reloadConfig() {
        configLoader.reload();
        loadConfigurations();
        plugin.getLogger().info("区域标题配置已重新加载");
    }

    /**
     * 检查玩家区域并显示标题
     */
    public void checkPlayerRegion(Player player, Location location) {
        Optional<RegionIntegrationService.RegionInfo> regionInfoOpt =
            integrationService.detectRegion(player, location);

        if (regionInfoOpt.isEmpty()) {
            // 玩家离开所有区域
            playerCurrentRegion.remove(player.getUniqueId());
            return;
        }

        RegionIntegrationService.RegionInfo regionInfo = regionInfoOpt.get();
        String regionKey = regionInfo.getRegionKey();
        String lastRegion = playerCurrentRegion.get(player.getUniqueId());

        // 只有当区域变化时才显示
        if (!regionKey.equals(lastRegion)) {
            playerCurrentRegion.put(player.getUniqueId(), regionKey);
            showRegionTitle(player, regionInfo);
        }
    }

    /**
     * 显示区域标题
     */
    private void showRegionTitle(Player player, RegionIntegrationService.RegionInfo regionInfo) {
        String regionKey = regionInfo.getRegionKey();

        // 优先使用自定义区域配置
        RegionDisplayConfig config = regionConfigs.get(regionKey);

        // 如果没有自定义配置，使用类型默认配置
        if (config == null) {
            config = typeDefaults.get(regionInfo.getType());
        }

        // 如果还是没有，使用默认配置
        if (config == null) {
            config = defaultConfig;
        }

        if (config.showTitle) {
            Component titleComponent = Component.text(regionInfo.getDisplayName())
                .color(config.titleColor);

            Component subtitleComponent = regionInfo.getSubtitle() != null && config.showSubtitle
                ? Component.text(regionInfo.getSubtitle()).color(TextColor.color(200, 200, 200))
                : Component.empty();

            Title title = Title.title(
                titleComponent,
                subtitleComponent,
                Title.Times.times(config.fadeIn, config.stay, config.fadeOut)
            );

            player.showTitle(title);
        }

        // 播放音效
        if (config.soundEffect != null) {
            player.playSound(player.getLocation(), config.soundEffect, 1.0f, 1.0f);
        }

        // 触发事件
        RegionEnterEvent event = new RegionEnterEvent(
            player,
            regionInfo.getType(),
            regionKey,
            regionInfo.getDisplayName()
        );
        plugin.getServer().getPluginManager().callEvent(event);
    }

    /**
     * 设置区域显示配置
     */
    public void setRegionConfig(String regionKey, RegionDisplayConfig config) {
        regionConfigs.put(regionKey, config);
    }

    /**
     * 移除区域显示配置
     */
    public void removeRegionConfig(String regionKey) {
        regionConfigs.remove(regionKey);
    }

    /**
     * 清理玩家数据
     */
    public void clearPlayerData(UUID playerId) {
        playerCurrentRegion.remove(playerId);
    }

    /**
     * 区域显示配置类
     */
    public static class RegionDisplayConfig {
        private final Duration fadeIn;
        private final Duration stay;
        private final Duration fadeOut;
        private final TextColor titleColor;
        private final boolean showTitle;
        private final boolean showSubtitle;
        private final String soundEffect;

        public RegionDisplayConfig(Duration fadeIn, Duration stay, Duration fadeOut,
                                 TextColor titleColor, boolean showTitle,
                                 boolean showSubtitle, String soundEffect) {
            this.fadeIn = fadeIn;
            this.stay = stay;
            this.fadeOut = fadeOut;
            this.titleColor = titleColor;
            this.showTitle = showTitle;
            this.showSubtitle = showSubtitle;
            this.soundEffect = soundEffect;
        }

        // Getters
        public Duration getFadeIn() { return fadeIn; }
        public Duration getStay() { return stay; }
        public Duration getFadeOut() { return fadeOut; }
        public TextColor getTitleColor() { return titleColor; }
        public boolean isShowTitle() { return showTitle; }
        public boolean isShowSubtitle() { return showSubtitle; }
        public String getSoundEffect() { return soundEffect; }
    }
}
