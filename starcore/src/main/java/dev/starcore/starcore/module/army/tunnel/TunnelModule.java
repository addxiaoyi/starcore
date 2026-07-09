package dev.starcore.starcore.module.army.tunnel;

import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.module.ModuleLayer;
import dev.starcore.starcore.core.module.ModuleMetadata;
import dev.starcore.starcore.core.module.StarCoreModule;
import dev.starcore.starcore.core.persistence.PersistenceService;
import dev.starcore.starcore.foundation.economy.EconomyService;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.army.tunnel.command.TunnelCommand;
import dev.starcore.starcore.module.army.tunnel.listener.TunnelListener;
import dev.starcore.starcore.module.nation.NationService;
import org.bukkit.plugin.Plugin;

import java.util.List;

/**
 * Tunnel Warfare Module
 * Provides underground tunnel network system for nations
 *
 * Features:
 * - Create different types of tunnels (Supply, Escape, Military, Secret)
 * - Multiple hidden entrances
 * - Trap system for military tunnels
 * - Ambush capabilities
 * - Tunnel collapse mechanics
 *
 * Dependencies: nation, economy
 */
public final class TunnelModule implements StarCoreModule {

    private static final ModuleMetadata METADATA = new ModuleMetadata(
        "tunnel",
        "地道战",
        ModuleLayer.MODULE,
        List.of("nation"),  // 依赖国家模块
        List.of(TunnelService.class),
        "Provides underground tunnel networks for nations - Tunnel Warfare system"
    );

    private Plugin plugin;
    private NationService nationService;
    private EconomyService economyService;
    private MessageService messages;
    private PersistenceService persistenceService;

    private TunnelService tunnelService;
    private TunnelCommand tunnelCommand;
    private TunnelListener tunnelListener;

    @Override
    public ModuleMetadata metadata() {
        return METADATA;
    }

    @Override
    public void enable(StarCoreContext context) {
        this.plugin = context.plugin();
        this.nationService = context.serviceRegistry().require(NationService.class);
        this.economyService = context.economyService();
        this.messages = context.serviceRegistry().require(MessageService.class);
        this.persistenceService = context.serviceRegistry().find(PersistenceService.class).orElse(null);

        // Initialize persistence namespace
        context.persistenceService().ensureNamespace(metadata().id());

        // Load configuration
        var configSection = context.plugin().getConfig().getConfigurationSection("tunnel");
        var tunnelConfig = TunnelService.TunnelConfig.fromConfig(configSection);

        // Initialize tunnel service
        tunnelService = new TunnelServiceImpl(
            plugin,
            nationService,
            economyService,
            messages,
            tunnelConfig
        );

        // Register service
        context.serviceRegistry().register(TunnelService.class, tunnelService);

        // Register command
        tunnelCommand = new TunnelCommand(tunnelService, nationService, messages);
        var tunnelCmd = plugin.getServer().getPluginCommand("tunnel");
        if (tunnelCmd != null) {
            tunnelCmd.setExecutor(tunnelCommand);
            tunnelCmd.setTabCompleter(tunnelCommand);
        }

        // Register listener
        tunnelListener = new TunnelListener(tunnelService, nationService, messages);
        plugin.getServer().getPluginManager().registerEvents(tunnelListener, plugin);

        // Load saved tunnel data
        tunnelService.loadAll();

        plugin.getLogger().info("Tunnel Warfare module enabled successfully");
        plugin.getLogger().info("Commands: /tunnel create, list, info, enter, exit, addentrance, discover, ambush, trap, collapse, destroy");
    }

    @Override
    public void disable(StarCoreContext context) {
        // Save tunnel data
        if (tunnelService != null) {
            tunnelService.saveAll();
        }

        // Clean up
        tunnelService = null;
        tunnelCommand = null;
        tunnelListener = null;

        plugin.getLogger().info("Tunnel Warfare module disabled");
    }

    public TunnelService getTunnelService() {
        return tunnelService;
    }

    public TunnelCommand getTunnelCommand() {
        return tunnelCommand;
    }

    public TunnelListener getTunnelListener() {
        return tunnelListener;
    }
}
