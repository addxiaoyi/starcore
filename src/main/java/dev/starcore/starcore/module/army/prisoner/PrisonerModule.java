package dev.starcore.starcore.module.army.prisoner;

import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.module.ModuleLayer;
import dev.starcore.starcore.core.module.ModuleMetadata;
import dev.starcore.starcore.core.module.StarCoreModule;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.war.WarService;
import dev.starcore.starcore.nation.permission.NationPermissionChecker;
import dev.starcore.starcore.nation.rank.NationRankManager;

import java.util.List;

/**
 * 俘虏系统模块
 */
public final class PrisonerModule implements StarCoreModule {

    private static final ModuleMetadata METADATA = new ModuleMetadata(
        "prisoner",
        "俘虏系统",
        ModuleLayer.MODULE,
        List.of("army", "war", "nation"),
        List.of(PrisonerService.class),
        "Prisoner of war management system"
    );

    private PrisonerServiceImpl prisonerService;

    @Override
    public ModuleMetadata metadata() {
        return METADATA;
    }

    @Override
    public void enable(StarCoreContext context) {
        MessageService messages = context.serviceRegistry().require(MessageService.class);
        WarService warService = context.serviceRegistry().find(WarService.class).orElse(null);
        NationService nationService = context.serviceRegistry().require(NationService.class);
        NationRankManager rankManager = context.serviceRegistry().require(NationRankManager.class);
        NationPermissionChecker permissionChecker = new NationPermissionChecker();

        // 加载配置
        PrisonerService.PrisonerConfig config = PrisonerService.PrisonerConfig.fromConfig(
            context.plugin().getConfig().getConfigurationSection("prisoner")
        );

        // 初始化服务
        this.prisonerService = new PrisonerServiceImpl(
            context.plugin(),
            messages,
            warService,
            nationService,
            rankManager,
            permissionChecker,
            config
        );

        // 注册服务
        context.serviceRegistry().register(PrisonerService.class, prisonerService);

        // 注册命令（如果存在）
        if (context.plugin().getCommand("prisoner") != null) {
            context.plugin().getCommand("prisoner").setExecutor(new PrisonerCommand(prisonerService, nationService, messages));
            context.plugin().getCommand("prisoner").setTabCompleter(new PrisonerCommand(prisonerService, nationService, messages));
        }

        // 注册监听器
        context.plugin().getServer().getPluginManager().registerEvents(
            new PrisonerListener(prisonerService, messages),
            context.plugin()
        );

        context.plugin().getLogger().info("俘虏系统模块已启用");
    }

    @Override
    public void disable(StarCoreContext context) {
        if (prisonerService != null) {
            prisonerService.saveAll();
        }
    }
}