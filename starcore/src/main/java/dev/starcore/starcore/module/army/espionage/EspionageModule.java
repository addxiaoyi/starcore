package dev.starcore.starcore.module.army.espionage;

import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.module.ModuleLayer;
import dev.starcore.starcore.core.module.ModuleMetadata;
import dev.starcore.starcore.core.module.StarCoreModule;
import dev.starcore.starcore.core.persistence.PersistenceService;
import dev.starcore.starcore.foundation.economy.EconomyService;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.army.espionage.command.EspionageCommand;
import dev.starcore.starcore.module.army.espionage.listener.EspionageListener;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.treasury.TreasuryService;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

import java.util.List;

/**
 * 间谍模块
 * 提供间谍训练、间谍行动、反间谍等功能
 */
public final class EspionageModule implements StarCoreModule {
    private static final ModuleMetadata METADATA = new ModuleMetadata(
            "espionage",
            "间谍系统",
            ModuleLayer.MODULE,
            List.of("nation", "treasury"),  // 依赖国家模块和金库模块
            List.of(EspionageService.class),
            "Provides spy training, espionage operations, and counter-intelligence."
    );

    private Plugin plugin;
    private NationService nationService;
    private TreasuryService treasuryService;
    private EconomyService economyService;
    private MessageService messages;
    private PersistenceService persistenceService;

    private EspionageService espionageService;
    private EspionageListener listener;

    @Override
    public ModuleMetadata metadata() {
        return METADATA;
    }

    @Override
    public void enable(StarCoreContext context) {
        this.plugin = context.plugin();
        this.nationService = context.serviceRegistry().require(NationService.class);
        this.treasuryService = context.serviceRegistry().require(TreasuryService.class);
        this.economyService = context.economyService();
        this.messages = context.serviceRegistry().require(MessageService.class);
        this.persistenceService = context.serviceRegistry().find(PersistenceService.class).orElse(null);

        // 从配置读取间谍配置
        ConfigurationSection config = context.plugin().getConfig().getConfigurationSection("espionage");
        EspionageService.EspionageConfig espionageConfig = EspionageService.EspionageConfig.fromConfig(config);

        // 初始化间谍服务
        espionageService = new EspionageServiceImpl(
                plugin,
                nationService,
                treasuryService,
                economyService,
                messages,
                espionageConfig,
                persistenceService
        );

        // 注册服务
        context.serviceRegistry().register(EspionageService.class, espionageService);

        // 注册命令
        EspionageCommand command = new EspionageCommand(espionageService, nationService, messages);
        var spyCmd = plugin.getServer().getPluginCommand("spy");
        if (spyCmd != null) {
            spyCmd.setExecutor(command);
            spyCmd.setTabCompleter(command);
        }

        // 注册别名命令
        registerAliasCommand("espionage", command);

        // 注册事件监听器
        listener = new EspionageListener(espionageService, nationService, messages);
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);

        plugin.getLogger().info("Espionage module enabled successfully!");
    }

    @Override
    public void disable(StarCoreContext context) {
        // 清理资源
        if (espionageService instanceof EspionageServiceImpl impl) {
            // 保存所有状态
            plugin.getLogger().info("Espionage module shutting down...");
        }

        espionageService = null;
        listener = null;

        plugin.getLogger().info("Espionage module disabled");
    }

    public EspionageService getEspionageService() {
        return espionageService;
    }

    /**
     * 注册别名命令
     */
    private void registerAliasCommand(String name, EspionageCommand executor) {
        var cmd = plugin.getServer().getPluginCommand(name);
        if (cmd != null) {
            cmd.setExecutor(executor);
            if (executor instanceof org.bukkit.command.TabCompleter tabCompleter) {
                cmd.setTabCompleter(tabCompleter);
            }
        }
    }
}
