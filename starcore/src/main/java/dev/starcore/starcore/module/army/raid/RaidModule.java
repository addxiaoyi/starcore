package dev.starcore.starcore.module.army.raid;

import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.module.ModuleLayer;
import dev.starcore.starcore.core.module.ModuleMetadata;
import dev.starcore.starcore.core.module.StarCoreModule;
import dev.starcore.starcore.core.persistence.PersistenceService;
import dev.starcore.starcore.foundation.economy.InternalEconomyService;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.foundation.player.OnlinePlayerDirectory;
import dev.starcore.starcore.module.army.raid.command.RaidCommand;
import dev.starcore.starcore.module.army.raid.listener.RaidListener;
import dev.starcore.starcore.module.nation.NationService;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * 夜间突袭模块
 * 提供夜间突袭功能，允许国家在特定时间段对敌对国家发起突袭
 */
public final class RaidModule implements StarCoreModule {
    private static final ModuleMetadata METADATA = new ModuleMetadata(
        "raid",
        "夜间突袭",
        ModuleLayer.MODULE,
        List.of("nation", "army"),  // 依赖国家和军队模块
        List.of(NightRaidService.class),
        "Provides night raid functionality for nations to raid enemy territories during specific time windows."
    );

    private JavaPlugin plugin;
    private NightRaidService raidService;
    private RaidListener raidListener;
    private RaidCommand raidCommand;

    @Override
    public ModuleMetadata metadata() {
        return METADATA;
    }

    @Override
    public void enable(StarCoreContext context) {
        context.persistenceService().ensureNamespace(metadata().id());

        this.plugin = context.plugin();
        NationService nationService = context.serviceRegistry().require(NationService.class);
        OnlinePlayerDirectory playerDirectory = context.serviceRegistry().require(OnlinePlayerDirectory.class);
        MessageService messages = context.serviceRegistry().require(MessageService.class);
        PersistenceService persistenceService = context.serviceRegistry().find(PersistenceService.class).orElse(null);

        // 从配置读取突袭配置
        var configSection = context.plugin().getConfig().getConfigurationSection("night-raid");
        if (configSection == null || !configSection.getBoolean("enabled", true)) {
            plugin.getLogger().info("Night raid module is disabled in config");
            return;
        }

        InternalEconomyService economyService = context.serviceRegistry().find(InternalEconomyService.class).orElse(null);

        // 初始化突袭服务
        raidService = new NightRaidServiceImpl(
            plugin,
            nationService,
            playerDirectory,
            messages,
            context.eventBus(),
            economyService,
            configSection,
            persistenceService
        );

        // 注册服务
        context.serviceRegistry().register(NightRaidService.class, raidService);

        // 注册命令
        raidCommand = new RaidCommand(raidService, nationService, messages, plugin);
        var raidCmd = plugin.getServer().getPluginCommand("raid");
        if (raidCmd != null) {
            raidCmd.setExecutor(raidCommand);
            raidCmd.setTabCompleter(raidCommand);
        } else {
            plugin.getLogger().warning("Command 'raid' not found in plugin.yml");
        }

        // 注册监听器
        raidListener = new RaidListener(raidService, nationService, messages);
        raidListener.setPlugin(plugin);
        plugin.getServer().getPluginManager().registerEvents(raidListener, plugin);

        plugin.getLogger().info("Night raid module enabled");
    }

    @Override
    public void disable(StarCoreContext context) {
        if (raidService != null) {
            raidService.saveAll();
            raidService.shutdown();
            raidService = null;
        }

        raidListener = null;
        raidCommand = null;

        context.plugin().getLogger().info("Night raid module disabled");
    }

    public NightRaidService getRaidService() {
        return raidService;
    }
}