package dev.starcore.starcore.module.alliance;

import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.event.StarCoreEventBus;
import dev.starcore.starcore.core.module.ModuleLayer;
import dev.starcore.starcore.core.module.ModuleMetadata;
import dev.starcore.starcore.core.module.StarCoreModule;
import dev.starcore.starcore.core.persistence.PersistenceService;
import dev.starcore.starcore.foundation.player.OnlinePlayerDirectory;
import dev.starcore.starcore.module.alliance.gui.AllianceGuiListener;
import dev.starcore.starcore.module.nation.NationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * 联盟外交系统模块
 *
 * 提供多国联盟（联邦）管理功能：
 * - 联盟创建、解散、重命名
 * - 成员邀请、加入、离开、移除
 * - 角色管理和领导权转移
 * - 联盟外交关系设置
 * - 联盟公告系统
 * - GUI 界面管理
 * - 命令行交互
 */
public final class AllianceModule implements StarCoreModule {

    private static final ModuleMetadata METADATA = new ModuleMetadata(
        "alliance",
        "联盟外交系统",
        ModuleLayer.MODULE,
        List.of("nation"),
        List.of(AllianceService.class),
        "Provides multi-nation alliance (federation) system with member management, diplomacy, and GUI."
    );

    private static final Logger LOGGER = LoggerFactory.getLogger(AllianceModule.class);

    private AllianceService allianceService;
    private AllianceCommand allianceCommand;
    private AllianceGuiListener guiListener;

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

        LOGGER.info("AllianceModule enabled successfully with {} alliance(s).", allianceService.getAllianceCount());
    }

    /**
     * 注册联盟命令
     * 注意：使用 /fed 作为命令别名以区分于旧的两国联盟系统
     */
    private void registerCommands(StarCoreContext context) {
        JavaPlugin plugin = context.plugin();

        this.allianceCommand = new AllianceCommand(
            allianceService,
            context.serviceRegistry().require(NationService.class),
            context.serviceRegistry().require(OnlinePlayerDirectory.class)
        );

        // 尝试注册 /fed 命令（联盟/联邦命令）
        var fedCommand = plugin.getCommand("fed");
        if (fedCommand != null) {
            fedCommand.setExecutor(allianceCommand);
            fedCommand.setTabCompleter(allianceCommand);
            LOGGER.info("Alliance command registered as /fed.");
        } else {
            LOGGER.warn("Command 'fed' not found in plugin.yml");
        }

        // 尝试注册 /alliance2 命令（用于区分旧的两国联盟）
        var alliance2Command = plugin.getCommand("alliance2");
        if (alliance2Command != null) {
            alliance2Command.setExecutor(allianceCommand);
            alliance2Command.setTabCompleter(allianceCommand);
            LOGGER.info("Alliance command registered as /alliance2.");
        }
    }

    /**
     * 注册 GUI 监听器
     */
    private void registerGui(StarCoreContext context) {
        JavaPlugin plugin = context.plugin();

        NationService nationService = context.serviceRegistry().require(NationService.class);

        // 创建并注册 GUI 监听器
        this.guiListener = new AllianceGuiListener(allianceService, nationService);
        plugin.getServer().getPluginManager().registerEvents(guiListener, plugin);

        LOGGER.info("Alliance GUI listeners registered.");
    }

    @Override
    public void disable(StarCoreContext context) {
        // 关闭服务并保存数据
        if (allianceService instanceof AllianceServiceImpl service) {
            service.shutdown();
        }

        // 清理静态引用
        guiListener = null;
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
