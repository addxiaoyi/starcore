package dev.starcore.starcore.region;

import dev.starcore.starcore.core.service.ServiceRegistry;
import dev.starcore.starcore.foundation.territory.TerritoryService;
import dev.starcore.starcore.territory.SubRegionService;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;

/**
 * 区域模块
 * 统一管理区域检测和标题显示功能
 *
 * 整合说明:
 * - 使用 Foundation 的 Chunk 级别 TerritoryService 作为王国/国家检测来源
 * - Chunk 级别的 claimAt() 用于快速判断玩家所在国家/领土
 * - 坐标级别 TerritoryService 用于子区域和精细领土检测
 * - SubRegionService 用于更精细的子区域检测
 *
 * 架构原则:
 * - 所有区域检测都基于 Chunk 级别，与 TerritoryProtectionListener 保持一致
 * - 确保进入区域的标题显示与实际保护规则匹配
 */
public class RegionModule {
    private final Plugin plugin;
    private final RegionIntegrationService integrationService;
    private final RegionTitleService titleService;
    private final RegionDetectionListener detectionListener;
    private final RegionCommand regionCommand;

    public RegionModule(Plugin plugin,
                       TerritoryService foundationTerritoryService,
                       dev.starcore.starcore.territory.TerritoryService coordinateTerritoryService,
                       SubRegionService subRegionService,
                       ServiceRegistry serviceRegistry) {
        this.plugin = plugin;

        // 初始化服务（传递两个服务）
        // - foundationTerritoryService: Chunk 级别，用于王国/国家检测
        // - coordinateTerritoryService: 坐标级别，用于子区域检测
        this.integrationService = new RegionIntegrationService(
            plugin,
            foundationTerritoryService,
            coordinateTerritoryService,
            subRegionService,
            serviceRegistry
        );

        this.titleService = new RegionTitleService(plugin, integrationService);

        // 注册监听器
        this.detectionListener = new RegionDetectionListener(plugin, titleService);
        plugin.getServer().getPluginManager().registerEvents(detectionListener, plugin);

        // 注册命令
        this.regionCommand = new RegionCommand(this);
        registerCommand();

        plugin.getLogger().info("区域模块已启用 - 基于 Foundation Chunk 级别领土系统");
    }

    /**
     * 注册命令
     */
    private void registerCommand() {
        PluginCommand command = plugin.getServer().getPluginCommand("region");
        if (command != null) {
            command.setExecutor(regionCommand);
            command.setTabCompleter(regionCommand);
        } else {
            plugin.getLogger().warning("无法注册 /region 命令 - 请检查 plugin.yml");
        }
    }

    /**
     * 获取集成服务
     */
    public RegionIntegrationService getIntegrationService() {
        return integrationService;
    }

    /**
     * 获取标题服务
     */
    public RegionTitleService getTitleService() {
        return titleService;
    }

    /**
     * 获取检测监听器
     */
    public RegionDetectionListener getDetectionListener() {
        return detectionListener;
    }

    /**
     * 关闭模块
     */
    public void shutdown() {
        detectionListener.stopCheckTask();
        plugin.getLogger().info("区域模块已关闭");
    }
}
