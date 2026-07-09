package dev.starcore.starcore.module.merge;

import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.event.StarCoreEventBus;
import dev.starcore.starcore.core.module.ModuleLayer;
import dev.starcore.starcore.core.module.ModuleMetadata;
import dev.starcore.starcore.core.module.StarCoreModule;
import dev.starcore.starcore.foundation.economy.EconomyService;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.foundation.player.PlayerProfileService;
import dev.starcore.starcore.module.merge.command.MergeCommand;
import dev.starcore.starcore.module.merge.listener.MergeListener;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.treasury.TreasuryService;

import java.util.List;

/**
 * 合并公投模块
 * 允许国家通过公投方式合并成一个国家
 */
public final class MergeModule implements StarCoreModule {
    private static final ModuleMetadata METADATA = new ModuleMetadata(
        "merge",
        "合并公投",
        ModuleLayer.MODULE,
        List.of("nation", "treasury"),
        List.of(MergeReferendumService.class),
        "Allows nations to merge through referendum voting system."
    );

    private MergeReferendumService mergeService;
    private MergeReferendumServiceImpl mergeServiceImpl;
    private MergeListener listener;
    private NationService nationService;
    private TreasuryService treasuryService;
    private EconomyService economyService;
    private MessageService messages;
    private PlayerProfileService playerProfiles;
    private StarCoreEventBus eventBus;

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

        // 初始化合并公投服务
        this.mergeServiceImpl = new MergeReferendumServiceImpl(
            context.plugin(),
            nationService,
            treasuryService,
            playerProfiles,
            messages,
            context.databaseService(),
            context.persistenceService()
        );
        this.mergeService = mergeServiceImpl;

        // 注册服务
        context.serviceRegistry().register(MergeReferendumService.class, mergeService);

        // 注册命令
        MergeCommand command = new MergeCommand(mergeService, nationService, messages);
        var mergeCmd = context.plugin().getCommand("merge");
        if (mergeCmd != null) {
            mergeCmd.setExecutor(command);
            mergeCmd.setTabCompleter(command);
        }

        // 注册事件监听器
        this.listener = new MergeListener(mergeService, nationService, messages);
        context.plugin().getServer().getPluginManager().registerEvents(listener, context.plugin());

        context.plugin().getLogger().info("Merge referendum module enabled: nation merge referendum system ready.");
    }

    @Override
    public void disable(StarCoreContext context) {
        if (mergeServiceImpl != null) {
            mergeServiceImpl.shutdown();
        }
        this.nationService = null;
        this.treasuryService = null;
        this.economyService = null;
        this.messages = null;
        this.playerProfiles = null;
        this.eventBus = null;
        this.listener = null;

        context.plugin().getLogger().info("Merge referendum module disabled.");
    }

    /**
     * 获取合并公投服务
     */
    public MergeReferendumService getMergeService() {
        return mergeService;
    }
}