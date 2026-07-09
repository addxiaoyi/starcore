package dev.starcore.starcore.module.dynasty;

import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.database.DatabaseService;
import dev.starcore.starcore.core.module.ModuleLayer;
import dev.starcore.starcore.core.module.ModuleMetadata;
import dev.starcore.starcore.core.module.StarCoreModule;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.foundation.player.OnlinePlayerDirectory;
import dev.starcore.starcore.foundation.player.PlayerProfileService;
import dev.starcore.starcore.module.dynasty.command.DynastyCommand;
import dev.starcore.starcore.module.dynasty.listener.DynastyListener;
import dev.starcore.starcore.module.dynasty.model.Dynasty;
import dev.starcore.starcore.module.dynasty.model.SuccessionType;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 王朝模块
 * 提供王位继承、禅让、继承顺序等功能
 */
public final class DynastyModule implements StarCoreModule, DynastyService {
    private static final ModuleMetadata METADATA = new ModuleMetadata(
        "dynasty",
        "王位继承",
        ModuleLayer.MODULE,
        List.of("nation"),  // 依赖国家模块
        List.of(DynastyService.class),
        "Provides succession system for monarchies."
    );

    private DynastyServiceImpl service;
    private DynastyCommand command;
    private DynastyListener listener;

    @Override
    public ModuleMetadata metadata() {
        return METADATA;
    }

    @Override
    public void enable(StarCoreContext context) {
        context.persistenceService().ensureNamespace(metadata().id());

        DatabaseService databaseService = context.serviceRegistry().require(DatabaseService.class);
        NationService nationService = context.serviceRegistry().require(NationService.class);
        PlayerProfileService playerProfiles = context.serviceRegistry().require(PlayerProfileService.class);
        OnlinePlayerDirectory onlinePlayerDirectory = context.serviceRegistry().require(OnlinePlayerDirectory.class);
        MessageService messages = context.serviceRegistry().require(MessageService.class);

        // 创建并初始化服务
        service = new DynastyServiceImpl(
            context.plugin(),
            databaseService,
            nationService,
            playerProfiles,
            context.eventBus()
        );

        // 初始化数据库表
        service.initializeTables();

        // 加载已有数据
        service.loadAll();

        // 为没有王朝的国家创建王朝
        initializeDynasties(nationService);

        // 注册服务
        context.serviceRegistry().register(DynastyService.class, service);

        // 创建并注册命令
        command = new DynastyCommand(service, nationService, messages);
        command.setPlugin(context.plugin());
        var dynastyCmd = context.plugin().getCommand("dynasty");
        if (dynastyCmd != null) {
            dynastyCmd.setExecutor(command);
            dynastyCmd.setTabCompleter(command);
        }

        // 注册事件监听器
        listener = new DynastyListener(service, nationService, onlinePlayerDirectory, context.plugin());
        context.plugin().getServer().getPluginManager().registerEvents(listener, context.plugin());

        context.plugin().getLogger().info("Dynasty module enabled: " + service.summary());
    }

    /**
     * 为已有国家初始化王朝
     */
    private void initializeDynasties(NationService nationService) {
        for (Nation nation : nationService.nations()) {
            if (service.getDynasty(nation.id()).isEmpty()) {
                service.createDynasty(nation.id(), nation.founderId(), nation.founderId().toString());
            }
        }
    }

    @Override
    public void disable(StarCoreContext context) {
        if (service != null) {
            service.saveState();
        }
        context.plugin().getLogger().info("Dynasty module disabled");
    }

    // ===== DynastyService 接口方法 =====
    // 委托给内部服务实现

    @Override
    public Optional<Dynasty> getDynasty(NationId nationId) {
        return service != null ? service.getDynasty(nationId) : Optional.empty();
    }

    @Override
    public Dynasty createDynasty(NationId nationId, UUID founderId, String founderName) {
        return service != null ? service.createDynasty(nationId, founderId, founderName) : null;
    }

    @Override
    public SuccessionResult abdicate(NationId nationId, UUID currentMonarch, UUID newMonarch, String reason) {
        return service != null ? service.abdicate(nationId, currentMonarch, newMonarch, reason) : SuccessionResult.fail("Service not initialized");
    }

    @Override
    public SuccessionResult inherit(NationId nationId, UUID inheritorId) {
        return service != null ? service.inherit(nationId, inheritorId) : SuccessionResult.fail("Service not initialized");
    }

    @Override
    public SuccessionResult addHeir(NationId nationId, UUID monarchId, UUID heirId) {
        return service != null ? service.addHeir(nationId, monarchId, heirId) : SuccessionResult.fail("Service not initialized");
    }

    @Override
    public SuccessionResult removeHeir(NationId nationId, UUID monarchId, UUID heirId) {
        return service != null ? service.removeHeir(nationId, monarchId, heirId) : SuccessionResult.fail("Service not initialized");
    }

    @Override
    public List<HeirRecord> getHeirs(NationId nationId) {
        return service != null ? service.getHeirs(nationId) : List.of();
    }

    @Override
    public SuccessionResult setSuccessionType(NationId nationId, UUID monarchId, SuccessionType type) {
        return service != null ? service.setSuccessionType(nationId, monarchId, type) : SuccessionResult.fail("Service not initialized");
    }

    @Override
    public boolean isInterregnum(NationId nationId) {
        return service != null && service.isInterregnum(nationId);
    }

    @Override
    public Optional<Instant> getInterregnumStart(NationId nationId) {
        return service != null ? service.getInterregnumStart(nationId) : Optional.empty();
    }

    @Override
    public SuccessionResult claimCrown(NationId nationId, UUID claimantId) {
        return service != null ? service.claimCrown(nationId, claimantId) : SuccessionResult.fail("Service not initialized");
    }

    @Override
    public void renounceClaim(NationId nationId, UUID claimantId) {
        if (service != null) {
            service.renounceClaim(nationId, claimantId);
        }
    }

    @Override
    public Collection<NationId> getInterregnumNations() {
        return service != null ? service.getInterregnumNations() : List.of();
    }

    @Override
    public void saveState() {
        if (service != null) {
            service.saveState();
        }
    }

    @Override
    public String summary() {
        return service != null ? service.summary() : "Dynasty module not initialized";
    }

    /**
     * 获取内部服务实例（供测试用）
     */
    public DynastyServiceImpl getService() {
        return service;
    }
}