package dev.starcore.starcore.module.arbitration;

import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.module.ModuleLayer;
import dev.starcore.starcore.core.module.ModuleMetadata;
import dev.starcore.starcore.core.module.StarCoreModule;
import dev.starcore.starcore.core.persistence.PersistenceService;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.foundation.player.OnlinePlayerDirectory;
import dev.starcore.starcore.module.arbitration.command.ArbitrationCommand;
import dev.starcore.starcore.module.arbitration.listener.ArbitrationListener;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.treasury.TreasuryService;
import dev.starcore.starcore.foundation.territory.TerritoryService;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Optional;

/**
 * 领土仲裁模块
 * 提供领土纠纷仲裁功能
 */
public final class ArbitrationModule implements StarCoreModule {
    private static final ModuleMetadata METADATA = new ModuleMetadata(
        "arbitration",
        "领土仲裁",
        ModuleLayer.MODULE,
        List.of("nation", "treasury"),
        List.of(ArbitrationService.class),
        "Handles territory dispute arbitration between nations."
    );

    private Plugin plugin;
    private NationService nationService;
    private TreasuryService treasuryService;
    private TerritoryService territoryService;
    private MessageService messages;
    private PersistenceService persistenceService;
    private OnlinePlayerDirectory onlinePlayerDirectory;

    private ArbitrationService arbitrationService;
    private ArbitrationCommand command;
    private ArbitrationListener listener;

    @Override
    public ModuleMetadata metadata() {
        return METADATA;
    }

    @Override
    public void enable(StarCoreContext context) {
        this.plugin = context.plugin();
        this.nationService = context.serviceRegistry().require(NationService.class);
        this.treasuryService = context.serviceRegistry().require(TreasuryService.class);
        this.territoryService = context.serviceRegistry().require(TerritoryService.class);
        this.messages = context.serviceRegistry().require(MessageService.class);
        this.persistenceService = context.serviceRegistry().find(PersistenceService.class).orElse(null);
        this.onlinePlayerDirectory = context.serviceRegistry().require(OnlinePlayerDirectory.class);

        // 确保命名空间存在
        context.persistenceService().ensureNamespace(metadata().id());

        // 从配置读取仲裁配置
        ConfigurationSection config = context.plugin().getConfig().getConfigurationSection("arbitration");
        ArbitrationServiceImpl.ArbitrationConfig arbitrationConfig = ArbitrationServiceImpl.ArbitrationConfig.fromConfig(config);

        // 初始化仲裁服务
        this.arbitrationService = new ArbitrationServiceImpl(
            plugin,
            nationService,
            treasuryService,
            territoryService,
            persistenceService,
            arbitrationConfig
        );

        // 注册服务
        context.serviceRegistry().register(ArbitrationService.class, arbitrationService);

        // 注册命令
        this.command = new ArbitrationCommand(
            arbitrationService,
            nationService,
            messages,
            onlinePlayerDirectory
        );
        var arbCmd = plugin.getServer().getPluginCommand("arbitration");
        if (arbCmd != null) {
            arbCmd.setExecutor(command);
            arbCmd.setTabCompleter(command);
        }
        var arbCmd2 = plugin.getServer().getPluginCommand("arb");
        if (arbCmd2 != null) {
            arbCmd2.setExecutor(command);
            arbCmd2.setTabCompleter(command);
        }

        // 注册事件监听器
        this.listener = new ArbitrationListener(
            arbitrationService,
            nationService,
            messages
        );
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);

        plugin.getLogger().info("Arbitration module enabled with config: filingFee=" + arbitrationConfig.filingFee() + ", min=" + arbitrationConfig.minimumClaimFee() + ", max=" + arbitrationConfig.maximumClaimFee());
    }

    @Override
    public void disable(StarCoreContext context) {
        // 保存状态
        if (arbitrationService != null) {
            arbitrationService.saveState();
        }

        // 清理引用
        this.arbitrationService = null;
        this.command = null;
        this.listener = null;
        this.plugin = null;
        this.nationService = null;
        this.treasuryService = null;
        this.territoryService = null;
        this.messages = null;
        this.persistenceService = null;
        this.onlinePlayerDirectory = null;

        context.plugin().getLogger().info("Arbitration module disabled");
    }

    public ArbitrationService getArbitrationService() {
        return arbitrationService;
    }
}