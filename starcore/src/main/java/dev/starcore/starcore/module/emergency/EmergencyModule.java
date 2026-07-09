package dev.starcore.starcore.module.emergency;

import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.module.ModuleLayer;
import dev.starcore.starcore.core.module.ModuleMetadata;
import dev.starcore.starcore.core.module.StarCoreModule;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.emergency.command.EmergencyCommand;
import dev.starcore.starcore.module.emergency.listener.EmergencyListener;
import dev.starcore.starcore.module.emergency.model.EmergencyState;
import dev.starcore.starcore.module.nation.NationService;

import java.util.List;

/**
 * 紧急状态模块
 * 提供国家紧急状态的声明、取消和管理功能
 */
public final class EmergencyModule implements StarCoreModule, EmergencyService {

    private static final ModuleMetadata METADATA = new ModuleMetadata(
        "emergency",
        "紧急状态系统",
        ModuleLayer.MODULE,
        List.of("nation"),  // 依赖国家模块
        List.of(EmergencyService.class),
        "Provides national emergency state declaration and management."
    );

    private EmergencyServiceImpl emergencyService;
    private MessageService messages;
    private NationService nationService;

    @Override
    public ModuleMetadata metadata() {
        return METADATA;
    }

    @Override
    public void enable(StarCoreContext context) {
        // 确保命名空间存在
        context.persistenceService().ensureNamespace(metadata().id());

        // 获取服务依赖
        this.messages = context.serviceRegistry().require(MessageService.class);
        this.nationService = context.serviceRegistry().require(NationService.class);

        // 初始化紧急状态服务
        this.emergencyService = new EmergencyServiceImpl(
            context.plugin(),
            nationService,
            messages,
            context.eventBus(),
            context.persistenceService(),
            context.scheduler()
        );

        // 注册服务
        context.serviceRegistry().register(EmergencyService.class, emergencyService);

        // 加载持久化状态
        emergencyService.loadState();

        // 启动定时清理任务
        emergencyService.startCleanupTask();

        // 注册命令
        registerCommands(context);

        // 注册事件监听器
        registerEventListeners(context);

        context.plugin().getLogger().info("Emergency module enabled: emergency state system ready.");
    }

    @Override
    public void disable(StarCoreContext context) {
        // 停止定时清理任务
        if (emergencyService != null) {
            emergencyService.stopCleanupTask();
            emergencyService.saveState();
        }

        context.plugin().getLogger().info("Emergency module disabled.");
    }

    /**
     * 注册命令
     */
    private void registerCommands(StarCoreContext context) {
        var cmd = context.plugin().getCommand("emergency");
        if (cmd != null) {
            EmergencyCommand executor = new EmergencyCommand(emergencyService, nationService, messages);
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
            context.plugin().getLogger().info("Emergency command registered: /emergency");
        } else {
            context.plugin().getLogger().warning("Command 'emergency' not found in plugin.yml");
        }
    }

    /**
     * 注册事件监听器
     */
    private void registerEventListeners(StarCoreContext context) {
        EmergencyListener listener = new EmergencyListener(emergencyService, nationService, messages);
        context.plugin().getServer().getPluginManager().registerEvents(listener, context.plugin());
        context.plugin().getLogger().info("Emergency event listener registered.");
    }

    // EmergencyService 接口实现

    @Override
    public boolean declareEmergency(
        dev.starcore.starcore.module.nation.model.NationId nationId,
        EmergencyState.EmergencyType type,
        String reason,
        int durationMinutes
    ) {
        return emergencyService.declareEmergency(nationId, type, reason, durationMinutes);
    }

    @Override
    public boolean cancelEmergency(dev.starcore.starcore.module.nation.model.NationId nationId) {
        return emergencyService.cancelEmergency(nationId);
    }

    @Override
    public java.util.Optional<EmergencyState> getEmergencyState(dev.starcore.starcore.module.nation.model.NationId nationId) {
        return emergencyService.getEmergencyState(nationId);
    }

    @Override
    public boolean isInEmergency(dev.starcore.starcore.module.nation.model.NationId nationId) {
        return emergencyService.isInEmergency(nationId);
    }

    @Override
    public java.util.Collection<EmergencyState> getAllEmergencies() {
        return emergencyService.getAllEmergencies();
    }

    @Override
    public boolean extendEmergency(dev.starcore.starcore.module.nation.model.NationId nationId, int additionalMinutes) {
        return emergencyService.extendEmergency(nationId, additionalMinutes);
    }

    @Override
    public String summary() {
        return emergencyService.summary();
    }

    /**
     * 获取紧急状态服务实现
     */
    public EmergencyServiceImpl getEmergencyServiceImpl() {
        return emergencyService;
    }
}