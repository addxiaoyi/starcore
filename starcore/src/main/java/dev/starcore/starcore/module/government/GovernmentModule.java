package dev.starcore.starcore.module.government;

import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.module.ModuleLayer;
import dev.starcore.starcore.core.module.ModuleMetadata;
import dev.starcore.starcore.core.module.StarCoreModule;
import dev.starcore.starcore.core.database.DatabaseService;
import dev.starcore.starcore.module.government.model.GovernmentType;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.resolution.model.Resolution;
import dev.starcore.starcore.module.resolution.model.ResolutionAction;
import dev.starcore.starcore.foundation.player.OnlinePlayerDirectory;
import dev.starcore.starcore.government.ParliamentService;
import dev.starcore.starcore.government.PartyService;
import dev.starcore.starcore.government.CourtService;
import dev.starcore.starcore.government.CourtExecutionService;
import dev.starcore.starcore.government.command.ParliamentCommand;
import dev.starcore.starcore.government.command.PoliticalPartyCommand;
import dev.starcore.starcore.government.command.CourtCommand;
import dev.starcore.starcore.government.listener.CourtExecutionListener;
import dev.starcore.starcore.moderation.jail.JailService;

import java.util.List;
import java.util.UUID;

public final class GovernmentModule implements StarCoreModule, GovernmentService {
    private static final ModuleMetadata METADATA = new ModuleMetadata(
        "government",
        "政体核心",
        ModuleLayer.MODULE,
        List.of("nation"),
        List.of(GovernmentService.class),
        "Models state forms, offices, and constitutional rules."
    );

    private NationService nationService;
    private DatabaseService databaseService;
    private ParliamentService parliamentService;
    private PartyService partyService;
    private CourtService courtService;
    private CourtExecutionService executionService;
    private OnlinePlayerDirectory onlinePlayerDirectory;
    private StarCoreContext context;

    @Override
    public ModuleMetadata metadata() {
        return METADATA;
    }

    @Override
    public void enable(StarCoreContext context) {
        this.context = context;
        context.persistenceService().ensureNamespace(metadata().id());
        this.nationService = context.serviceRegistry().require(NationService.class);
        this.databaseService = context.serviceRegistry().require(DatabaseService.class);
        this.onlinePlayerDirectory = context.serviceRegistry().require(OnlinePlayerDirectory.class);

        // 初始化服务并注册命令
        initializeServices(context);
        registerCommands(context);
        registerListeners(context);
    }

    private void registerListeners(StarCoreContext context) {
        // 注册法庭执行监听器
        if (executionService != null) {
            var jailService = context.serviceRegistry().find(JailService.class).orElse(null);
            CourtExecutionListener listener = new CourtExecutionListener(
                courtService,
                executionService,
                jailService,
                nationService
            );
            context.plugin().getServer().getPluginManager().registerEvents(listener, context.plugin());
            context.plugin().getLogger().info("Government listeners registered: CourtExecutionListener");
        }
    }

    private void initializeServices(StarCoreContext context) {
        // 创建议会服务
        context.databaseService().dataSource().ifPresentOrElse(ds -> {
            this.parliamentService = new ParliamentService(context.plugin(), ds);
            parliamentService.initializeTables();
            context.serviceRegistry().register(ParliamentService.class, parliamentService);

            this.partyService = new PartyService(context.plugin(), ds);
            partyService.initializeTables();
            context.serviceRegistry().register(PartyService.class, partyService);

            this.courtService = new CourtService(context.plugin(), ds);
            courtService.initializeTables();
            context.serviceRegistry().register(CourtService.class, courtService);

            // 初始化判决执行服务
            var jailService = context.serviceRegistry().find(dev.starcore.starcore.moderation.jail.JailService.class).orElse(null);
            var economyService = context.economyService();
            this.executionService = new CourtExecutionService(courtService, jailService, economyService, nationService, onlinePlayerDirectory, context.plugin(), databaseService);
            executionService.initializeAllTables();
            context.serviceRegistry().register(CourtExecutionService.class, executionService);

            context.plugin().getLogger().info("Government services initialized: Parliament, Party, Court with execution");
        }, () -> {
            context.plugin().getLogger().warning("Database not available, government services not initialized");
            this.parliamentService = null;
            this.partyService = null;
            this.courtService = null;
            this.executionService = null;
        });
    }

    private void registerCommands(StarCoreContext context) {
        // 创建议会命令
        ParliamentCommand parliamentCommand = new ParliamentCommand(
            parliamentService,
            partyService,
            nationService,
            onlinePlayerDirectory
        );
        registerCommand("parliament", parliamentCommand);

        // 创建政党命令
        PoliticalPartyCommand partyCommand = new PoliticalPartyCommand(
            partyService,
            parliamentService,
            nationService,
            onlinePlayerDirectory
        );
        registerCommand("politicalparty", partyCommand);

        // 创建法庭命令
        CourtCommand courtCommand = new CourtCommand(
            courtService,
            nationService,
            onlinePlayerDirectory,
            executionService
        );
        registerCommand("court", courtCommand);

        context.plugin().getLogger().info("Government commands registered: /parliament, /politicalparty, /court");
    }

    private void registerCommand(String name, org.bukkit.command.CommandExecutor executor) {
        var cmd = context.plugin().getCommand(name);
        if (cmd != null) {
            cmd.setExecutor(executor);
            if (executor instanceof org.bukkit.command.TabCompleter tabCompleter) {
                cmd.setTabCompleter(tabCompleter);
            }
        }
    }

    @Override
    public GovernmentType governmentOf(Nation nation) {
        return nation.governmentType();
    }

    @Override
    public boolean setGovernment(NationId nationId, GovernmentType governmentType) {
        Nation nation = nationService.nationById(nationId).orElse(null);
        if (nation == null) {
            return false;
        }
        nation.setGovernmentType(governmentType);
        nationService.saveState();
        return true;
    }

    @Override
    public boolean mayPropose(Nation nation, UUID proposerId, ResolutionAction action) {
        return nation.governmentType().mayPropose(nation, proposerId, action);
    }

    @Override
    public boolean maySign(Nation nation, UUID signerId, Resolution resolution) {
        return nation.governmentType().maySign(nation, signerId, resolution);
    }

    @Override
    public boolean resolutionPasses(Nation nation, Resolution resolution) {
        return nation.governmentType().passes(nation, resolution);
    }

    @Override
    public int requiredSignatures(Nation nation, Resolution resolution) {
        return nation.governmentType().requiredSignatures(nation, resolution);
    }

    @Override
    public String summary() {
        return "Government rules active via nation-attached government types";
    }

    @Override
    public void disable(StarCoreContext context) {
        // 清理服务引用
        this.parliamentService = null;
        this.partyService = null;
        this.courtService = null;
        this.executionService = null;
        this.context = null;

        context.plugin().getLogger().info("Government module disabled");
    }
}
