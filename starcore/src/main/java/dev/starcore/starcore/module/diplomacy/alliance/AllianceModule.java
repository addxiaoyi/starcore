package dev.starcore.starcore.module.diplomacy.alliance;

import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.event.StarCoreEventBus;
import dev.starcore.starcore.core.module.ModuleLayer;
import dev.starcore.starcore.core.module.ModuleMetadata;
import dev.starcore.starcore.core.module.StarCoreModule;
import dev.starcore.starcore.core.persistence.PersistenceService;
import dev.starcore.starcore.foundation.player.OnlinePlayerDirectory;
import dev.starcore.starcore.module.diplomacy.DiplomacyService;
import dev.starcore.starcore.module.diplomacy.alliance.command.AllianceCommand;
import dev.starcore.starcore.module.diplomacy.alliance.gui.AllianceMenu;
import dev.starcore.starcore.module.diplomacy.alliance.gui.AllianceMenuListener;
import dev.starcore.starcore.module.diplomacy.alliance.storage.AllianceStorage;
import dev.starcore.starcore.module.nation.NationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * 联盟外交系统模块
 *
 * 提供完整的联盟外交管理功能：
 * - 联盟邀请与接受
 * - 联盟关系管理
 * - 联盟数据持久化
 * - 联盟 GUI 界面
 * - 联盟命令系统
 */
public final class AllianceModule implements StarCoreModule {

    private static final ModuleMetadata METADATA = new ModuleMetadata(
        "alliance",
        "联盟外交系统",
        ModuleLayer.MODULE,
        List.of("nation", "diplomacy"),
        List.of(AllianceService.class),
        "Provides alliance diplomacy system with invites, management, and GUI."
    );

    private static final Logger LOGGER = LoggerFactory.getLogger(AllianceModule.class);

    private AllianceService allianceService;
    private AllianceCommand allianceCommand;
    private AllianceMenuListener menuListener;

    @Override
    public ModuleMetadata metadata() {
        return METADATA;
    }

    @Override
    public void enable(StarCoreContext context) {
        JavaPlugin plugin = context.plugin();

        // 确保命名空间目录存在
        context.persistenceService().ensureNamespace(metadata().id());

        // 获取依赖服务
        NationService nationService = context.serviceRegistry().require(NationService.class);
        DiplomacyService diplomacyService = context.serviceRegistry().find(DiplomacyService.class).orElse(null);
        OnlinePlayerDirectory onlinePlayerDirectory = context.serviceRegistry()
            .require(OnlinePlayerDirectory.class);

        // 创建存储服务
        AllianceStorage storage = new AllianceStorage(
            context.databaseService(),
            context.persistenceService(),
            LOGGER
        );

        // 创建并初始化联盟服务
        this.allianceService = new AllianceServiceImpl(
            plugin,
            nationService,
            diplomacyService,
            context.scheduler(),
            context.eventBus(),
            storage
        );
        this.allianceService.initialize();

        // 注册服务到 ServiceRegistry
        context.serviceRegistry().register(AllianceService.class, allianceService);

        // 注册命令
        registerCommands(context);

        // 注册 GUI 监听器
        registerGui(context);

        plugin.getLogger().info("AllianceModule enabled successfully.");
    }

    /**
     * 注册联盟命令
     */
    private void registerCommands(StarCoreContext context) {
        JavaPlugin plugin = context.plugin();

        this.allianceCommand = new AllianceCommand(
            allianceService,
            context.serviceRegistry().require(NationService.class),
            context.serviceRegistry().require(OnlinePlayerDirectory.class)
        );

        // 尝试注册 /alliance 命令
        var command = plugin.getCommand("alliance");
        if (command != null) {
            command.setExecutor(allianceCommand);
            command.setTabCompleter(allianceCommand);
            LOGGER.info("Alliance command registered.");
        } else {
            LOGGER.warn("Command 'alliance' not found in plugin.yml");
        }
    }

    /**
     * 注册 GUI 监听器
     */
    private void registerGui(StarCoreContext context) {
        JavaPlugin plugin = context.plugin();

        NationService nationService = context.serviceRegistry().require(NationService.class);

        // 创建并注册 GUI 监听器
        this.menuListener = new AllianceMenuListener(allianceService, nationService);
        plugin.getServer().getPluginManager().registerEvents(menuListener, plugin);

        LOGGER.info("Alliance GUI listeners registered.");
    }

    @Override
    public void disable(StarCoreContext context) {
        // 关闭服务并保存数据
        if (allianceService instanceof AllianceServiceImpl service) {
            service.shutdown();
        }

        // 清理静态引用
        menuListener = null;
        allianceCommand = null;
        allianceService = null;

        LOGGER.info("AllianceModule disabled.");
    }

    /**
     * 获取联盟服务实例
     */
    public AllianceService getAllianceService() {
        return allianceService;
    }
}
