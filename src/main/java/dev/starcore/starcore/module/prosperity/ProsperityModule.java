package dev.starcore.starcore.module.prosperity;

import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.module.ModuleLayer;
import dev.starcore.starcore.core.module.ModuleMetadata;
import dev.starcore.starcore.core.module.StarCoreModule;
import dev.starcore.starcore.core.persistence.PersistenceService;
import dev.starcore.starcore.core.event.StarCoreEventBus;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.prosperity.command.ProsperityCommand;
import dev.starcore.starcore.module.prosperity.listener.ProsperityListener;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 繁荣度模块
 * 提供国家繁荣度系统，包括：
 * - 繁荣度计算和等级系统
 * - 活跃度追踪
 * - 每日衰减机制
 * - 繁荣度加成（税收、资源产出）
 * - 危机和峰值事件
 */
public final class ProsperityModule implements StarCoreModule, ProsperityService {

    private static final ModuleMetadata METADATA = new ModuleMetadata(
        "prosperity",
        "繁荣度系统",
        ModuleLayer.MODULE,
        List.of("nation"),  // 依赖国家模块
        List.of(ProsperityService.class),
        "Provides nation prosperity system with activity tracking, decay, and bonuses."
    );

    private Plugin plugin;
    private NationService nationService;
    private PersistenceService persistenceService;
    private StarCoreEventBus eventBus;
    private MessageService messages;
    private ProsperityConfig config;

    private ProsperityServiceImpl prosperityService;
    private ProsperityCommand command;
    private ProsperityListener listener;

    // 定时任务ID
    private int decayTaskId = -1;
    private int saveTaskId = -1;

    @Override
    public ModuleMetadata metadata() {
        return METADATA;
    }

    @Override
    public void enable(StarCoreContext context) {
        this.plugin = context.plugin();
        this.nationService = context.serviceRegistry().require(NationService.class);
        this.persistenceService = context.serviceRegistry().find(PersistenceService.class).orElse(null);
        this.eventBus = context.serviceRegistry().find(StarCoreEventBus.class).orElse(null);
        this.messages = context.serviceRegistry().require(MessageService.class);

        // 确保命名空间存在
        context.persistenceService().ensureNamespace(metadata().id());

        // 加载配置
        loadConfig(context);

        // 初始化服务
        initializeService();

        // 注册命令
        registerCommands(context);

        // 注册监听器
        registerListeners(context);

        // 启动定时任务
        startScheduledTasks(context);

        // 注册服务
        context.serviceRegistry().register(ProsperityService.class, this);

        plugin.getLogger().info("STARCORE prosperity module enabled.");
    }

    @Override
    public void disable(StarCoreContext context) {
        // 停止定时任务
        stopScheduledTasks();

        // 保存所有数据
        if (prosperityService != null) {
            prosperityService.saveAll();
        }

        // 清理监听器
        listener = null;
        command = null;
        prosperityService = null;

        plugin.getLogger().info("STARCORE prosperity module disabled.");
    }

    private void loadConfig(StarCoreContext context) {
        // 从主配置文件中读取繁荣度配置
        var configSection = context.plugin().getConfig().getConfigurationSection("prosperity");
        if (configSection == null) {
            // 尝试从 prosperity.yml 读取
            config = loadFromProsperityYaml(context);
        } else {
            config = ProsperityConfig.fromConfig(configSection);
        }

        if (config == null) {
            config = ProsperityConfig.defaultConfig();
            plugin.getLogger().warning("Using default prosperity config.");
        }
    }

    private ProsperityConfig loadFromProsperityYaml(StarCoreContext context) {
        try {
            var prosperityFile = new java.io.File(context.plugin().getDataFolder(), "prosperity.yml");
            if (prosperityFile.exists()) {
                org.bukkit.configuration.file.YamlConfiguration yamlConfig =
                    org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(prosperityFile);
                var configSection = yamlConfig.getConfigurationSection("prosperity");
                if (configSection != null) {
                    return ProsperityConfig.fromConfig(configSection);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load prosperity.yml: " + e.getMessage());
        }
        return null;
    }

    private void initializeService() {
        prosperityService = new ProsperityServiceImpl(
            plugin,
            nationService,
            persistenceService,
            eventBus,
            config
        );

        // 初始化服务（加载数据并为现有国家创建繁荣度）
        prosperityService.initialize();
    }

    private void registerCommands(StarCoreContext context) {
        command = new ProsperityCommand(prosperityService, nationService);

        // 尝试获取已注册的命令
        var prosperityCmd = context.plugin().getCommand("prosperity");
        if (prosperityCmd != null) {
            prosperityCmd.setExecutor(command);
            prosperityCmd.setTabCompleter(command);
        } else {
            plugin.getLogger().warning("Command 'prosperity' not found in plugin.yml");
        }
    }

    private void registerListeners(StarCoreContext context) {
        listener = new ProsperityListener(
            prosperityService,
            nationService,
            messages,
            config
        );
        context.plugin().getServer().getPluginManager().registerEvents(listener, context.plugin());
    }

    private void startScheduledTasks(StarCoreContext context) {
        // 繁荣度衰减任务（每小时检查一次）
        long decayInterval = config.decayCheckIntervalTicks();
        if (decayInterval > 0) {
            decayTaskId = context.plugin().getServer().getScheduler().runTaskTimer(
                context.plugin(),
                () -> prosperityService.processAllDecay(),
                decayInterval,
                decayInterval
            ).getTaskId();
        }

        // 数据保存任务（每5分钟保存一次）
        saveTaskId = context.plugin().getServer().getScheduler().runTaskTimerAsynchronously(
            context.plugin(),
            () -> prosperityService.saveAll(),
            20L * 60 * 5,  // 5分钟后开始
            20L * 60 * 5   // 每5分钟
        ).getTaskId();
    }

    private void stopScheduledTasks() {
        if (decayTaskId != -1) {
            plugin.getServer().getScheduler().cancelTask(decayTaskId);
            decayTaskId = -1;
        }
        if (saveTaskId != -1) {
            plugin.getServer().getScheduler().cancelTask(saveTaskId);
            saveTaskId = -1;
        }
    }

    // ==================== ProsperityService 实现 ====================

    @Override
    public NationProsperity getProsperity(dev.starcore.starcore.module.nation.model.NationId nationId) {
        return prosperityService.getProsperity(nationId);
    }

    @Override
    public double getProsperityValue(dev.starcore.starcore.module.nation.model.NationId nationId) {
        return prosperityService.getProsperityValue(nationId);
    }

    @Override
    public int getProsperityLevel(dev.starcore.starcore.module.nation.model.NationId nationId) {
        return prosperityService.getProsperityLevel(nationId);
    }

    @Override
    public double modifyProsperity(dev.starcore.starcore.module.nation.model.NationId nationId, double amount, String reason) {
        return prosperityService.modifyProsperity(nationId, amount, reason);
    }

    @Override
    public void setProsperity(dev.starcore.starcore.module.nation.model.NationId nationId, double value) {
        prosperityService.setProsperity(nationId, value);
    }

    @Override
    public void addChunkContribution(java.util.UUID nationId, String chunkWorld, int chunkX, int chunkZ, double amount) {
        prosperityService.addChunkContribution(nationId, chunkWorld, chunkX, chunkZ, amount);
    }

    @Override
    public double getChunkContribution(String chunkWorld, int chunkX, int chunkZ) {
        return prosperityService.getChunkContribution(chunkWorld, chunkX, chunkZ);
    }

    @Override
    public void recordEvent(dev.starcore.starcore.module.nation.model.NationId nationId, String eventType, String description, double amount) {
        prosperityService.recordEvent(nationId, eventType, description, amount);
    }

    @Override
    public java.util.List<ProsperityEvent> getRecentEvents(dev.starcore.starcore.module.nation.model.NationId nationId, int limit) {
        return prosperityService.getRecentEvents(nationId, limit);
    }

    @Override
    public double getBonusMultiplier(dev.starcore.starcore.module.nation.model.NationId nationId) {
        return prosperityService.getBonusMultiplier(nationId);
    }

    @Override
    public double getTaxBonus(dev.starcore.starcore.module.nation.model.NationId nationId) {
        return prosperityService.getTaxBonus(nationId);
    }

    @Override
    public double getResourceBonus(dev.starcore.starcore.module.nation.model.NationId nationId) {
        return prosperityService.getResourceBonus(nationId);
    }

    @Override
    public void processDecay(dev.starcore.starcore.module.nation.model.NationId nationId) {
        prosperityService.processDecay(nationId);
    }

    @Override
    public void processAllDecay() {
        prosperityService.processAllDecay();
    }

    @Override
    public void refreshProsperity(dev.starcore.starcore.module.nation.model.NationId nationId) {
        prosperityService.refreshProsperity(nationId);
    }

    @Override
    public void refreshAllProsperity() {
        prosperityService.refreshAllProsperity();
    }

    @Override
    public void recordActivity(dev.starcore.starcore.module.nation.model.NationId nationId, java.util.UUID playerId, String activityType) {
        prosperityService.recordActivity(nationId, playerId, activityType);
    }

    @Override
    public int getActivityScore(dev.starcore.starcore.module.nation.model.NationId nationId) {
        return prosperityService.getActivityScore(nationId);
    }

    @Override
    public void saveAll() {
        prosperityService.saveAll();
    }

    @Override
    public java.util.List<java.util.Map.Entry<dev.starcore.starcore.module.nation.model.NationId, Double>> getRanking() {
        return prosperityService.getRanking();
    }

    /**
     * 获取配置
     */
    public ProsperityConfig getConfig() {
        return config;
    }

    /**
     * 获取内部服务实例
     */
    public ProsperityServiceImpl getServiceImpl() {
        return prosperityService;
    }

    public String summary() {
        return "Prosperity system active with " + prosperityService.getRanking().size() + " nations tracked.";
    }
}
