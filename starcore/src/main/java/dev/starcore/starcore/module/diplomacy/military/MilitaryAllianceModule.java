package dev.starcore.starcore.module.diplomacy.military;

import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.event.StarCoreEventBus;
import dev.starcore.starcore.core.module.ModuleLayer;
import dev.starcore.starcore.core.module.ModuleMetadata;
import dev.starcore.starcore.core.module.StarCoreModule;
import dev.starcore.starcore.core.persistence.PersistenceService;
import dev.starcore.starcore.foundation.player.OnlinePlayerDirectory;
import dev.starcore.starcore.module.diplomacy.DiplomacyService;
import dev.starcore.starcore.module.diplomacy.military.command.MilitaryAllianceCommand;
import dev.starcore.starcore.module.diplomacy.military.gui.MilitaryAllianceMenuListener;
import dev.starcore.starcore.module.diplomacy.military.storage.MilitaryAllianceStorage;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.war.WarService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * 军事联盟外交系统模块
 *
 * 提供完整的军事联盟管理功能：
 * - 军事联盟邀请与接受
 * - 军事联盟关系管理
 * - 互助防御条约
 * - 联合军事协议
 * - 军事联盟 GUI 界面
 * - 军事联盟命令系统
 */
public final class MilitaryAllianceModule implements StarCoreModule {

    private static final ModuleMetadata METADATA = new ModuleMetadata(
        "military-alliance",
        "军事联盟系统",
        ModuleLayer.MODULE,
        List.of("nation", "diplomacy", "war"),
        List.of(MilitaryAllianceService.class),
        "Provides military alliance system with defense pacts, joint defense, and protection mechanics."
    );

    private static final Logger LOGGER = LoggerFactory.getLogger(MilitaryAllianceModule.class);

    private MilitaryAllianceService militaryAllianceService;
    private MilitaryAllianceCommand allianceCommand;
    private MilitaryAllianceMenuListener menuListener;

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
        WarService warService = context.serviceRegistry().find(WarService.class).orElse(null);
        OnlinePlayerDirectory onlinePlayerDirectory = context.serviceRegistry()
            .require(OnlinePlayerDirectory.class);

        // 创建存储服务
        MilitaryAllianceStorage storage = new MilitaryAllianceStorage(
            context.databaseService(),
            context.persistenceService(),
            LOGGER
        );

        // 创建并初始化军事联盟服务
        this.militaryAllianceService = new MilitaryAllianceServiceImpl(
            plugin,
            nationService,
            diplomacyService,
            warService,
            context.scheduler(),
            context.eventBus(),
            storage
        );
        this.militaryAllianceService.initialize();

        // 注册服务到 ServiceRegistry
        context.serviceRegistry().register(MilitaryAllianceService.class, militaryAllianceService);

        // 注册命令
        registerCommands(context);

        // 注册 GUI 监听器
        registerGui(context);

        plugin.getLogger().info("MilitaryAllianceModule enabled successfully.");
    }

    /**
     * 注册军事联盟命令
     */
    private void registerCommands(StarCoreContext context) {
        JavaPlugin plugin = context.plugin();

        this.allianceCommand = new MilitaryAllianceCommand(
            militaryAllianceService,
            context.serviceRegistry().require(NationService.class),
            context.serviceRegistry().require(OnlinePlayerDirectory.class)
        );

        // 尝试注册 /militaryalliance 命令
        var command = plugin.getCommand("militaryalliance");
        if (command != null) {
            command.setExecutor(allianceCommand);
            command.setTabCompleter(allianceCommand);
            LOGGER.info("Military Alliance command registered.");
        } else {
            LOGGER.warn("Command 'militaryalliance' not found in plugin.yml");
        }

        // 也尝试注册 /ma 别名
        var aliasCommand = plugin.getCommand("ma");
        if (aliasCommand != null) {
            aliasCommand.setExecutor(allianceCommand);
            aliasCommand.setTabCompleter(allianceCommand);
            LOGGER.info("Military Alliance alias command (/ma) registered.");
        }
    }

    /**
     * 注册 GUI 监听器
     */
    private void registerGui(StarCoreContext context) {
        JavaPlugin plugin = context.plugin();

        NationService nationService = context.serviceRegistry().require(NationService.class);

        // 创建并注册 GUI 监听器
        this.menuListener = new MilitaryAllianceMenuListener(militaryAllianceService, nationService);
        plugin.getServer().getPluginManager().registerEvents(menuListener, plugin);

        LOGGER.info("Military Alliance GUI listeners registered.");
    }

    @Override
    public void disable(StarCoreContext context) {
        // 关闭服务并保存数据
        if (militaryAllianceService instanceof MilitaryAllianceServiceImpl service) {
            service.shutdown();
        }

        // 清理静态引用
        menuListener = null;
        allianceCommand = null;
        militaryAllianceService = null;

        LOGGER.info("MilitaryAllianceModule disabled.");
    }

    /**
     * 获取军事联盟服务实例
     */
    public MilitaryAllianceService getMilitaryAllianceService() {
        return militaryAllianceService;
    }
}
