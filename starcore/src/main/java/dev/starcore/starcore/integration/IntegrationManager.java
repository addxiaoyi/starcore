package dev.starcore.starcore.integration;

import dev.starcore.starcore.foundation.economy.EconomyService;
import dev.starcore.starcore.foundation.i18n.I18nManager;
import dev.starcore.starcore.integration.citizens.CitizensIntegration;
import dev.starcore.starcore.integration.craftengine.CraftEngineIntegration;
import dev.starcore.starcore.integration.customitems.CustomItemIntegration;
import dev.starcore.starcore.integration.geyser.GeyserIntegration;
import dev.starcore.starcore.integration.mobstack.MobStackIntegration;
// import dev.starcore.starcore.integration.mythicmobs.MythicMobsIntegration; // Disabled - missing dependency
import dev.starcore.starcore.integration.papi.StarcorePlaceholder;
import dev.starcore.starcore.integration.typewriter.TypewriterIntegration;
import dev.starcore.starcore.integration.vault.VaultIntegration;
import dev.starcore.starcore.pvp.stats.PvPStatsService;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 统一集成管理器
 * 管理所有第三方插件集成
 */
public final class IntegrationManager {
    private final Plugin plugin;
    private final EconomyService economyService;
    private final PvPStatsService statsService;
    private final I18nManager i18nManager;

    // 集成实例
    private VaultIntegration vault;
    private StarcorePlaceholder placeholderAPI;
    private MobStackIntegration mobStack;
    private CustomItemIntegration customItems;
    // private MythicMobsIntegration mythicMobs; // Disabled - missing dependency
    private CraftEngineIntegration craftEngine;
    private CitizensIntegration citizens;
    private TypewriterIntegration typewriter;
    private GeyserIntegration geyser;

    // 集成状态
    private final Map<String, Boolean> integrationStatus = new ConcurrentHashMap<>();

    public IntegrationManager(
        Plugin plugin,
        EconomyService economyService,
        PvPStatsService statsService,
        I18nManager i18nManager
    ) {
        this.plugin = plugin;
        this.economyService = economyService;
        this.statsService = statsService;
        this.i18nManager = i18nManager;
    }

    /**
     * 初始化所有集成
     */
    public void initAll() {
        plugin.getLogger().info("========================================");
        plugin.getLogger().info("  初始化插件集成...");
        plugin.getLogger().info("========================================");

        // 经济系统
        vault = new VaultIntegration(plugin, economyService, i18nManager);
        integrationStatus.put("Vault", vault.register());

        // 占位符
        placeholderAPI = new StarcorePlaceholder(plugin, economyService, statsService);
        integrationStatus.put("PlaceholderAPI", placeholderAPI.register());

        // 生物堆叠
        mobStack = new MobStackIntegration(plugin);
        integrationStatus.put("MobStack", mobStack.init());

        // 自定义物品
        customItems = new CustomItemIntegration(plugin);
        integrationStatus.put("CustomItems", customItems.init());

        // 自定义生物 - 已禁用
        // mythicMobs = new MythicMobsIntegration(plugin);
        // integrationStatus.put("MythicMobs", mythicMobs.init());

        // 自定义合成
        craftEngine = new CraftEngineIntegration(plugin);
        integrationStatus.put("CraftEngine", craftEngine.init());

        // NPC 系统
        citizens = new CitizensIntegration(plugin);
        integrationStatus.put("Citizens", citizens.init());

        // 任务对话系统
        typewriter = new TypewriterIntegration(plugin);
        integrationStatus.put("Typewriter", typewriter.init());

        // Geyser/Floodgate 基岩版支持
        geyser = new GeyserIntegration(plugin);
        integrationStatus.put("Geyser", geyser.isGeyserAvailable() || geyser.isFloodgateAvailable());

        // 输出集成报告
        printIntegrationReport();
    }

    /**
     * 打印集成报告
     */
    private void printIntegrationReport() {
        plugin.getLogger().info("========================================");
        plugin.getLogger().info("  插件集成报告");
        plugin.getLogger().info("========================================");

        int enabled = 0;
        int total = integrationStatus.size();

        for (Map.Entry<String, Boolean> entry : integrationStatus.entrySet()) {
            String status = entry.getValue() ? "§a✔ 已启用" : "§7✘ 未启用";
            plugin.getLogger().info(String.format("  %s: %s", entry.getKey(), status));
            if (entry.getValue()) enabled++;
        }

        plugin.getLogger().info("========================================");
        plugin.getLogger().info(String.format("  总计: %d/%d 已启用", enabled, total));
        plugin.getLogger().info("========================================");
    }

    /**
     * 获取集成状态
     */
    public Map<String, Boolean> getIntegrationStatus() {
        return new ConcurrentHashMap<>(integrationStatus);
    }

    /**
     * 检查集成是否启用
     */
    public boolean isIntegrationEnabled(String integrationName) {
        return integrationStatus.getOrDefault(integrationName, false);
    }

    // ==================== Vault ====================

    public Optional<VaultIntegration> getVault() {
        return vault != null && vault.isEnabled() ? Optional.of(vault) : Optional.empty();
    }

    public boolean hasVault() {
        return vault != null && vault.isEnabled();
    }

    // ==================== PlaceholderAPI ====================

    public Optional<StarcorePlaceholder> getPlaceholderAPI() {
        return placeholderAPI != null ? Optional.of(placeholderAPI) : Optional.empty();
    }

    public boolean hasPlaceholderAPI() {
        return placeholderAPI != null;
    }

    // ==================== MobStack ====================

    public Optional<MobStackIntegration> getMobStack() {
        return mobStack != null && mobStack.isEnabled() ? Optional.of(mobStack) : Optional.empty();
    }

    public boolean hasMobStack() {
        return mobStack != null && mobStack.isEnabled();
    }

    // ==================== CustomItems ====================

    public Optional<CustomItemIntegration> getCustomItems() {
        return customItems != null && customItems.isEnabled() ? Optional.of(customItems) : Optional.empty();
    }

    public boolean hasCustomItems() {
        return customItems != null && customItems.isEnabled();
    }

    // ==================== MythicMobs ====================
    // Disabled due to missing dependency
    /*
    public Optional<MythicMobsIntegration> getMythicMobs() {
        return mythicMobs != null && mythicMobs.isEnabled() ? Optional.of(mythicMobs) : Optional.empty();
    }

    public boolean hasMythicMobs() {
        return mythicMobs != null && mythicMobs.isEnabled();
    }
    */

    public Optional<?> getMythicMobs() {
        return Optional.empty();
    }

    public boolean hasMythicMobs() {
        return false;
    }

    // ==================== CraftEngine ====================

    public Optional<CraftEngineIntegration> getCraftEngine() {
        return craftEngine != null && craftEngine.isEnabled() ? Optional.of(craftEngine) : Optional.empty();
    }

    public boolean hasCraftEngine() {
        return craftEngine != null && craftEngine.isEnabled();
    }

    // ==================== Citizens ====================

    public Optional<CitizensIntegration> getCitizens() {
        return citizens != null && citizens.isEnabled() ? Optional.of(citizens) : Optional.empty();
    }

    public boolean hasCitizens() {
        return citizens != null && citizens.isEnabled();
    }

    // ==================== Typewriter ====================

    public Optional<TypewriterIntegration> getTypewriter() {
        return typewriter != null && typewriter.isEnabled() ? Optional.of(typewriter) : Optional.empty();
    }

    public boolean hasTypewriter() {
        return typewriter != null && typewriter.isEnabled();
    }

    // ==================== 便捷方法 ====================

    /**
     * 获取已启用的集成数量
     */
    public int getEnabledCount() {
        return (int) integrationStatus.values().stream().filter(b -> b).count();
    }

    /**
     * 获取总集成数量
     */
    public int getTotalCount() {
        return integrationStatus.size();
    }

    /**
     * 获取集成启用率
     */
    public double getEnabledPercentage() {
        if (integrationStatus.isEmpty()) {
            return 0.0;
        }
        return ((double) getEnabledCount() / getTotalCount()) * 100.0;
    }

    /**
     * 重载所有集成
     */
    public void reloadAll() {
        plugin.getLogger().info("正在重载所有集成...");
        initAll();
    }
}
