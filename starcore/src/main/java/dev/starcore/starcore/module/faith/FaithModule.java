package dev.starcore.starcore.module.faith;

import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.module.ModuleLayer;
import dev.starcore.starcore.core.module.ModuleMetadata;
import dev.starcore.starcore.core.module.StarCoreModule;
import dev.starcore.starcore.core.persistence.PersistenceService;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.foundation.territory.TerritoryService;
import dev.starcore.starcore.module.faith.command.FaithCommand;
import dev.starcore.starcore.module.faith.listener.FaithListener;
import dev.starcore.starcore.module.nation.NationService;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 领土信仰模块
 * 提供国家信仰值系统，支持祈祷、祈福、信仰等级等功能
 */
public final class FaithModule implements StarCoreModule, FaithService {
    private static final ModuleMetadata METADATA = new ModuleMetadata(
        "faith",
        "领土信仰",
        ModuleLayer.MODULE,
        List.of("nation"),  // 依赖国家模块
        List.of(FaithService.class),
        "Provides territory faith system with prayer, blessing and faith levels."
    );

    private Plugin plugin;
    private NationService nationService;
    private TerritoryService territoryService;
    private MessageService messages;
    private PersistenceService persistenceService;

    private FaithServiceImpl faithService;
    private FaithListener listener;
    private StarCoreContext context;

    // 跟踪已初始化的国家，避免重复初始化
    private final java.util.Set<String> initializedNations = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean initializing = new AtomicBoolean(false);

    @Override
    public ModuleMetadata metadata() {
        return METADATA;
    }

    @Override
    public void enable(StarCoreContext context) {
        this.context = context;
        this.plugin = context.plugin();
        this.nationService = context.serviceRegistry().require(NationService.class);
        this.territoryService = context.serviceRegistry().find(TerritoryService.class).orElse(null);
        this.messages = context.serviceRegistry().require(MessageService.class);
        this.persistenceService = context.serviceRegistry().find(PersistenceService.class).orElse(null);

        context.persistenceService().ensureNamespace(metadata().id());

        // 初始化信仰服务
        ConfigurationSection configSection = context.plugin().getConfig().getConfigurationSection("faith");
        this.faithService = new FaithServiceImpl(plugin, persistenceService, configSection);
        this.faithService.initialize();

        // 注册服务
        context.serviceRegistry().register(FaithService.class, this);

        // 注册命令
        FaithCommand command = new FaithCommand(this, nationService, messages);
        var faithCmd = plugin.getServer().getPluginCommand("faith");
        if (faithCmd != null) {
            faithCmd.setExecutor(command);
            faithCmd.setTabCompleter(command);
        } else {
            // 尝试注册为 /sc faith
            var scCmd = plugin.getServer().getPluginCommand("sc");
            if (scCmd != null) {
                // 添加子命令处理
                plugin.getLogger().info("Faith command registered under /sc faith");
            }
        }

        // 注册事件监听器
        if (territoryService != null) {
            this.listener = new FaithListener(this, nationService, territoryService, messages);
            plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        }

        // 初始化已有国家的信仰数据
        initializeExistingNations();

        plugin.getLogger().info("Faith module enabled - Territory Faith System initialized");
    }

    @Override
    public void disable(StarCoreContext context) {
        // 保存所有信仰数据
        if (faithService != null) {
            faithService.saveAll();
        }

        // 清理监听器
        this.listener = null;

        context.plugin().getLogger().info("Faith module disabled");
    }

    /**
     * 初始化已有国家的信仰数据
     */
    private void initializeExistingNations() {
        if (!initializing.compareAndSet(false, true)) {
            return; // 防止重复初始化
        }

        try {
            for (var nation : nationService.nations()) {
                String nationKey = nation.id().toString();
                if (!initializedNations.contains(nationKey)) {
                    faithService.initializeFaith(nation.id(), nation.founderId());
                    initializedNations.add(nationKey);
                }
            }
        } finally {
            initializing.set(false);
        }
    }

    /**
     * 确保国家信仰数据已初始化
     * 当需要访问某国家信仰数据时调用此方法
     */
    private void ensureFaithInitialized(dev.starcore.starcore.module.nation.model.NationId nationId, UUID playerId) {
        String nationKey = nationId.toString();
        if (!initializedNations.contains(nationKey)) {
            faithService.initializeFaith(nationId, playerId);
            initializedNations.add(nationKey);
        }
    }

    // ========== FaithService 接口实现 ==========

    @Override
    public java.util.Optional<dev.starcore.starcore.module.faith.model.FaithData> getFaithData(dev.starcore.starcore.module.nation.model.NationId nationId) {
        ensureFaithInitialized(nationId, null);
        return faithService.getFaithData(nationId);
    }

    @Override
    public int getFaith(dev.starcore.starcore.module.nation.model.NationId nationId) {
        ensureFaithInitialized(nationId, null);
        return faithService.getFaith(nationId);
    }

    @Override
    public boolean setFaith(dev.starcore.starcore.module.nation.model.NationId nationId, int faith) {
        ensureFaithInitialized(nationId, null);
        return faithService.setFaith(nationId, faith);
    }

    @Override
    public int addFaith(dev.starcore.starcore.module.nation.model.NationId nationId, int amount) {
        ensureFaithInitialized(nationId, null);
        return faithService.addFaith(nationId, amount);
    }

    @Override
    public int getFaithLevel(dev.starcore.starcore.module.nation.model.NationId nationId) {
        ensureFaithInitialized(nationId, null);
        return faithService.getFaithLevel(nationId);
    }

    @Override
    public String getFaithLevelName(int level) {
        return faithService.getFaithLevelName(level);
    }

    @Override
    public java.util.Map<String, Double> getFaithBonuses(dev.starcore.starcore.module.nation.model.NationId nationId) {
        ensureFaithInitialized(nationId, null);
        return faithService.getFaithBonuses(nationId);
    }

    @Override
    public void recordPrayer(UUID playerId, dev.starcore.starcore.module.nation.model.NationId nationId, int locationX, int locationY, int locationZ, String world) {
        ensureFaithInitialized(nationId, playerId);
        faithService.recordPrayer(playerId, nationId, locationX, locationY, locationZ, world);
    }

    @Override
    public void checkFaithEvents(dev.starcore.starcore.module.nation.model.NationId nationId) {
        ensureFaithInitialized(nationId, null);
        faithService.checkFaithEvents(nationId);
    }

    @Override
    public dev.starcore.starcore.module.faith.model.FaithStats getStats(dev.starcore.starcore.module.nation.model.NationId nationId) {
        ensureFaithInitialized(nationId, null);
        return faithService.getStats(nationId);
    }

    @Override
    public boolean useFaithBlessing(dev.starcore.starcore.module.nation.model.NationId nationId, String blessingType) {
        ensureFaithInitialized(nationId, null);
        return faithService.useFaithBlessing(nationId, blessingType);
    }

    @Override
    public dev.starcore.starcore.module.faith.model.FaithConfig getConfig() {
        return faithService.getConfig();
    }

    @Override
    public void saveAll() {
        faithService.saveAll();
    }

    @Override
    public void initializeFaith(dev.starcore.starcore.module.nation.model.NationId nationId, UUID founderId) {
        String nationKey = nationId.toString();
        if (!initializedNations.contains(nationKey)) {
            faithService.initializeFaith(nationId, founderId);
            initializedNations.add(nationKey);
        }
    }

    @Override
    public void removeFaith(dev.starcore.starcore.module.nation.model.NationId nationId) {
        faithService.removeFaith(nationId);
        initializedNations.remove(nationId.toString());
    }

    @Override
    public int getMaxFaith() {
        return faithService.getMaxFaith();
    }

    @Override
    public int getFaithThreshold(int level) {
        return faithService.getFaithThreshold(level);
    }

    @Override
    public void initialize() {
        // 信仰模块在 enable() 中已初始化
        // 此方法实现接口要求
    }
}
