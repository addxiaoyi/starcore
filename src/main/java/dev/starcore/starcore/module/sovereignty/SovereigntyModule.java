package dev.starcore.starcore.module.sovereignty;

import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.module.ModuleLayer;
import dev.starcore.starcore.core.module.ModuleMetadata;
import dev.starcore.starcore.core.module.StarCoreModule;
import dev.starcore.starcore.core.persistence.PersistenceService;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.sovereignty.command.SovereigntyCommand;
import dev.starcore.starcore.module.sovereignty.listener.SovereigntyListener;
import org.bukkit.plugin.Plugin;

import java.util.List;

/**
 * 主权声明模块
 * 提供国家主权声明、领土宣称等功能
 */
public final class SovereigntyModule implements StarCoreModule {

    private static final ModuleMetadata METADATA = new ModuleMetadata(
            "sovereignty",
            "主权声明",
            ModuleLayer.MODULE,
            List.of("nation"),  // 依赖国家模块
            List.of(SovereigntyService.class),
            "Provides sovereignty declaration and territory claim management."
    );

    private StarCoreContext context;
    private SovereigntyService sovereigntyService;
    private SovereigntyListener listener;

    @Override
    public ModuleMetadata metadata() {
        return METADATA;
    }

    @Override
    public void enable(StarCoreContext context) {
        this.context = context;

        Plugin plugin = context.plugin();
        NationService nationService = context.serviceRegistry().require(NationService.class);
        MessageService messages = context.serviceRegistry().require(MessageService.class);
        PersistenceService persistenceService = context.serviceRegistry().find(PersistenceService.class).orElse(null);

        // 初始化服务
        this.sovereigntyService = new SovereigntyServiceImpl(
                plugin,
                nationService,
                context.databaseService(),
                persistenceService,
                messages
        );

        // 初始化数据库表
        if (this.sovereigntyService instanceof SovereigntyServiceImpl impl) {
            impl.initializeTables();
            // 加载已有数据
            impl.loadSovereignties();
        }

        // 注册服务
        context.serviceRegistry().register(SovereigntyService.class, sovereigntyService);

        // 创建并注册命令
        SovereigntyCommand command = new SovereigntyCommand(sovereigntyService, nationService, messages);
        var sovereigntyCmd = plugin.getServer().getPluginCommand("sovereignty");
        if (sovereigntyCmd != null) {
            sovereigntyCmd.setExecutor(command);
            sovereigntyCmd.setTabCompleter(command);
        } else {
            plugin.getLogger().warning("Sovereignty command not found in plugin.yml");
        }

        // 创建并注册事件监听器
        this.listener = new SovereigntyListener(sovereigntyService, nationService, messages);
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);

        plugin.getLogger().info("Sovereignty module enabled: " + sovereigntyService.summary());
    }

    @Override
    public void disable(StarCoreContext context) {
        // 保存状态
        if (sovereigntyService != null) {
            sovereigntyService.saveState();
        }

        // 清理引用
        this.sovereigntyService = null;
        this.listener = null;
        this.context = null;

        context.plugin().getLogger().info("Sovereignty module disabled");
    }

    /**
     * 获取主权服务
     */
    public SovereigntyService getSovereigntyService() {
        return sovereigntyService;
    }

    /**
     * 获取监听器
     */
    public SovereigntyListener getListener() {
        return listener;
    }
}