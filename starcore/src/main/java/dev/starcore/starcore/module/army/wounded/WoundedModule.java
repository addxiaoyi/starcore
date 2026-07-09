package dev.starcore.starcore.module.army.wounded;

import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.module.ModuleLayer;
import dev.starcore.starcore.core.module.ModuleMetadata;
import dev.starcore.starcore.core.module.StarCoreModule;
import dev.starcore.starcore.core.persistence.PersistenceService;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.army.wounded.command.WoundedCommand;
import dev.starcore.starcore.module.army.wounded.listener.WoundedListener;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.army.wounded.WoundedService.WoundedConfig;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Optional;

/**
 * 伤兵模块
 * 提供伤兵管理、治疗、康复等功能
 */
public final class WoundedModule implements StarCoreModule {
    private static final ModuleMetadata METADATA = new ModuleMetadata(
        "wounded",
        "伤兵系统",
        ModuleLayer.MODULE,
        List.of("nation", "army"),  // 依赖国家模块和军队模块
        List.of(WoundedService.class),
        "Manages wounded soldiers from battle, healing, and recovery."
    );

    private Plugin plugin;
    private NationService nationService;
    private MessageService messages;
    private PersistenceService persistenceService;

    private WoundedService woundedService;
    private WoundedCommand woundedCommand;
    private WoundedListener woundedListener;

    @Override
    public ModuleMetadata metadata() {
        return METADATA;
    }

    @Override
    public void enable(StarCoreContext context) {
        this.plugin = context.plugin();
        this.nationService = context.serviceRegistry().require(NationService.class);
        this.messages = context.serviceRegistry().require(MessageService.class);
        this.persistenceService = context.serviceRegistry().find(PersistenceService.class).orElse(null);

        // 从配置读取伤兵配置
        org.bukkit.configuration.ConfigurationSection config =
            context.plugin().getConfig().getConfigurationSection("army.wounded");
        WoundedConfig woundedConfig = WoundedConfig.fromConfig(config);

        // 初始化伤兵服务
        woundedService = new WoundedServiceImpl(
            plugin,
            nationService,
            messages,
            woundedConfig,
            persistenceService
        );

        // 注册服务
        context.serviceRegistry().register(WoundedService.class, woundedService);

        // 注册命令
        woundedCommand = new WoundedCommand(woundedService, nationService, messages);
        var cmd = plugin.getServer().getPluginCommand("wounded");
        if (cmd != null) {
            cmd.setExecutor(woundedCommand);
            cmd.setTabCompleter(woundedCommand);
        }

        // 注册别名命令
        var healCmd = plugin.getServer().getPluginCommand("heal");
        if (healCmd != null) {
            healCmd.setExecutor(woundedCommand);
            healCmd.setTabCompleter(woundedCommand);
        }

        // 注册事件监听器
        woundedListener = new WoundedListener(woundedService, nationService, messages, plugin);
        plugin.getServer().getPluginManager().registerEvents(woundedListener, plugin);

        plugin.getLogger().info("Wounded module enabled: wounded limit=" + woundedConfig.baseWoundedLimit());
    }

    @Override
    public void disable(StarCoreContext context) {
        // 停止伤兵服务
        if (woundedService != null) {
            woundedService.shutdown();
            woundedService = null;
        }

        woundedCommand = null;
        woundedListener = null;

        plugin.getLogger().info("Wounded module disabled");
    }

    public WoundedService getWoundedService() {
        return woundedService;
    }
}