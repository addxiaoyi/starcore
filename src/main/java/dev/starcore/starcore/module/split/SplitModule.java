package dev.starcore.starcore.module.split;

import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.event.StarCoreEventBus;
import dev.starcore.starcore.core.module.ModuleLayer;
import dev.starcore.starcore.core.module.ModuleMetadata;
import dev.starcore.starcore.core.module.StarCoreModule;
import dev.starcore.starcore.foundation.economy.EconomyService;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.foundation.player.PlayerProfileService;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.split.command.SplitCommand;
import dev.starcore.starcore.module.split.listener.SplitListener;
import dev.starcore.starcore.module.split.model.SplitRequest;
import dev.starcore.starcore.module.split.model.SplitRegion;
import dev.starcore.starcore.module.split.model.SplitResult;
import dev.starcore.starcore.module.treasury.TreasuryService;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 国家分裂模块
 * 允许国家分裂成两个独立的国家，支持领土、成员、国库的分割
 */
public final class SplitModule implements StarCoreModule {
    private static final ModuleMetadata METADATA = new ModuleMetadata(
        "split",
        "国家分裂系统",
        ModuleLayer.MODULE,
        List.of("nation", "treasury"),
        List.of(SplitService.class),
        "Allows nations to split into two independent nations with territory and member partition."
    );

    private SplitService splitService;
    private NationService nationService;
    private TreasuryService treasuryService;
    private EconomyService economyService;
    private MessageService messages;
    private PlayerProfileService playerProfiles;
    private StarCoreEventBus eventBus;
    private SplitConfig config;
    private SplitServiceImpl splitServiceImpl;
    private SplitListener listener;

    @Override
    public ModuleMetadata metadata() {
        return METADATA;
    }

    @Override
    public void enable(StarCoreContext context) {
        context.persistenceService().ensureNamespace(metadata().id());

        this.nationService = context.serviceRegistry().require(NationService.class);
        this.treasuryService = context.serviceRegistry().find(TreasuryService.class).orElse(null);
        this.economyService = context.economyService();
        this.messages = context.serviceRegistry().require(MessageService.class);
        this.playerProfiles = context.serviceRegistry().require(PlayerProfileService.class);
        this.eventBus = context.eventBus();

        // 初始化配置
        this.config = new SplitConfig(context.plugin());

        // 初始化分裂服务
        this.splitServiceImpl = new SplitServiceImpl(
            context.plugin(),
            nationService,
            treasuryService,
            economyService,
            messages,
            playerProfiles,
            eventBus,
            config,
            context.serviceRegistry()
        );
        this.splitService = splitServiceImpl;

        // 注册服务
        context.serviceRegistry().register(SplitService.class, splitService);

        // 注册命令
        SplitCommand command = new SplitCommand(splitService, nationService, messages);
        var splitCmd = context.plugin().getCommand("split");
        if (splitCmd != null) {
            splitCmd.setExecutor(command);
            splitCmd.setTabCompleter(command);
        }

        // 注册事件监听器
        this.listener = new SplitListener(splitService, nationService, messages);
        context.plugin().getServer().getPluginManager().registerEvents(listener, context.plugin());

        context.plugin().getLogger().info("Split module enabled: nation splitting system ready.");
    }

    @Override
    public void disable(StarCoreContext context) {
        this.nationService = null;
        this.treasuryService = null;
        this.economyService = null;
        this.messages = null;
        this.playerProfiles = null;
        this.eventBus = null;
        this.listener = null;

        context.plugin().getLogger().info("Split module disabled.");
    }

    /**
     * 获取分裂服务
     */
    public SplitService getSplitService() {
        return splitService;
    }
}