package dev.starcore.starcore.title;

import dev.starcore.starcore.achievement.AbstractAchievementService;
import dev.starcore.starcore.achievement.AchievementService;
import dev.starcore.starcore.core.database.DatabaseService;
import dev.starcore.starcore.core.service.ServiceRegistry;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.ranking.RankingService;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;

import java.util.logging.Logger;

/**
 * 称号系统模块
 * 统一管理称号系统的所有组件
 */
public class TitleModule {
    private final Plugin plugin;
    private final DatabaseService databaseService;
    private final dev.starcore.starcore.core.service.ServiceRegistry serviceRegistry;
    private final Logger logger;

    private TitleService titleService;
    private TitleDisplayService displayService;
    private TitleConfigLoader configLoader;
    private TitleListener listener;
    private StarCorePlaceholderExpansion placeholderExpansion;
    private AchievementService achievementService;

    public TitleModule(Plugin plugin, DatabaseService databaseService,
                       dev.starcore.starcore.core.service.ServiceRegistry serviceRegistry) {
        this.plugin = plugin;
        this.databaseService = databaseService;
        this.serviceRegistry = serviceRegistry;
        this.logger = plugin.getLogger();
    }

    /**
     * 启用模块
     */
    public void enable() {
        logger.info("Enabling Title System...");

        // 初始化成就服务（如果未注册）
        achievementService = serviceRegistry.find(AchievementService.class).orElseGet(() -> {
            AbstractAchievementService svc = new AbstractAchievementService(plugin) {};
            serviceRegistry.register(AchievementService.class, svc);
            return svc;
        });

        // 初始化核心服务
        titleService = new TitleService(plugin, databaseService);
        titleService.initialize();

        // 加载配置
        configLoader = new TitleConfigLoader(plugin, titleService);
        TitleDisplayConfig displayConfig = configLoader.loadDisplayConfig();
        configLoader.loadAll();

        // 初始化显示服务
        displayService = new TitleDisplayService(plugin, titleService, displayConfig);

        // 设置国家服务
        serviceRegistry.find(NationService.class).ifPresent(displayService::setNationService);

        // 设置排名服务
        serviceRegistry.find(RankingService.class).ifPresent(displayService::setRankingService);

        // 注册命令
        registerCommands();

        // 注册事件监听器
        listener = new TitleListener(plugin, titleService, displayService);
        Bukkit.getPluginManager().registerEvents(listener, plugin);

        // 注册PlaceholderAPI
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            placeholderExpansion = new StarCorePlaceholderExpansion(titleService, serviceRegistry);
            if (placeholderExpansion.register()) {
                logger.info("PlaceholderAPI expansion registered successfully");
            } else {
                logger.warning("Failed to register PlaceholderAPI expansion");
            }
        } else {
            logger.warning("PlaceholderAPI not found, placeholder support disabled");
        }

        // 为已在线的玩家更新显示
        Bukkit.getOnlinePlayers().forEach(displayService::updateAllDisplays);

        logger.info("Title System enabled successfully");
    }

    /**
     * 禁用模块
     */
    public void disable() {
        logger.info("Disabling Title System...");

        // 保存所有玩家数据
        Bukkit.getOnlinePlayers().forEach(player -> {
            titleService.getPlayerData(player.getUniqueId()).thenAccept(data -> {
                titleService.savePlayerData(data);
            });
        });

        // 注销PlaceholderAPI
        if (placeholderExpansion != null) {
            placeholderExpansion.unregister();
        }

        // 清理缓存
        titleService.invalidateAllCache();

        logger.info("Title System disabled");
    }

    /**
     * 注册命令
     */
    private void registerCommands() {
        PluginCommand titleCommand = plugin.getServer().getPluginCommand("title");
        if (titleCommand != null) {
            TitleCommand executor = new TitleCommand(titleService, displayService);
            titleCommand.setExecutor(executor);
            titleCommand.setTabCompleter(executor);
            logger.info("Registered /title command");
        } else {
            logger.warning("Title command not found in plugin.yml");
        }
    }

    /**
     * 重载模块
     */
    public void reload() {
        logger.info("Reloading Title System...");
        configLoader.reload();
        displayService.refreshAllPlayers();
        logger.info("Title System reloaded");
    }

    // Getters

    public TitleService getTitleService() {
        return titleService;
    }

    public TitleDisplayService getDisplayService() {
        return displayService;
    }

    public TitleConfigLoader getConfigLoader() {
        return configLoader;
    }
}
