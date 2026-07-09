package dev.starcore.starcore;

import dev.starcore.starcore.api.StarCoreApi;
import dev.starcore.starcore.api.StarCoreApiProvider;
import dev.starcore.starcore.command.StarCoreCommand;
import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.cache.CacheManager;
import dev.starcore.starcore.core.config.ConfigurationService;
import dev.starcore.starcore.core.config.RedisSettings;
import dev.starcore.starcore.core.database.DatabaseService;
import dev.starcore.starcore.core.event.StarCoreEventBus;
import dev.starcore.starcore.core.metrics.PerformanceMetricsService;
import dev.starcore.starcore.core.module.ModuleManager;
import dev.starcore.starcore.core.net.RedisCrossServerService;
import dev.starcore.starcore.crossserver.CrossServerService;
import dev.starcore.starcore.core.platform.PlatformAdapter;
import dev.starcore.starcore.core.platform.PlatformAdapterFactory;
import dev.starcore.starcore.core.persistence.PersistenceService;
import dev.starcore.starcore.core.scheduler.StarCoreScheduler;
import dev.starcore.starcore.core.service.ServiceRegistry;
import dev.starcore.starcore.foundation.animation.AnimationPlayer;
import dev.starcore.starcore.foundation.animation.ScreenShakeManager;
import dev.starcore.starcore.foundation.animation.ScreenShake;
import dev.starcore.starcore.foundation.animation.SoundFeedbackManager;
import dev.starcore.starcore.foundation.sound.SceneSoundService;
import dev.starcore.starcore.foundation.sound.SceneSoundConfig;
import dev.starcore.starcore.foundation.sound.SceneSoundCommand;
import dev.starcore.starcore.foundation.epoch.EpochService;
import dev.starcore.starcore.foundation.economy.InternalEconomyService;
import dev.starcore.starcore.foundation.economy.PlayerVaultEconomyService;
import dev.starcore.starcore.foundation.i18n.I18nManager;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.foundation.message.SimpleMessageService;
import dev.starcore.starcore.foundation.permission.InternalPermissionService;
import dev.starcore.starcore.foundation.player.BukkitOnlinePlayerDirectory;
import dev.starcore.starcore.foundation.player.OnlinePlayerDirectory;
import dev.starcore.starcore.foundation.player.InMemoryPlayerProfileService;
import dev.starcore.starcore.foundation.player.PlayerProfileService;
import dev.starcore.starcore.foundation.protection.ExternalProtectionService;
import dev.starcore.starcore.foundation.protection.ExternalProtectionServices;
import dev.starcore.starcore.territory.TerritoryStorage;
import dev.starcore.starcore.territory.TerritoryStorageFactory;
import dev.starcore.starcore.foundation.territory.DatabaseAwareTerritoryService;
import dev.starcore.starcore.foundation.territory.TerritoryService;
import dev.starcore.starcore.foundation.timesync.TimeSyncService;
import dev.starcore.starcore.territory.SubRegionService;
import dev.starcore.starcore.region.RegionModule;
import dev.starcore.starcore.foundation.hud.HudCommand;
import dev.starcore.starcore.foundation.hud.MainMenuHud;
import dev.starcore.starcore.foundation.hud.ModernHudListener;
import dev.starcore.starcore.module.diplomacy.DiplomacyModule;
import dev.starcore.starcore.module.diplomacy.alliance.AllianceModule;
import dev.starcore.starcore.module.event.EventModule;
import dev.starcore.starcore.module.government.GovernmentModule;
import dev.starcore.starcore.module.city.CityModule;
import dev.starcore.starcore.module.map.MapModule;
import dev.starcore.starcore.module.nation.NationModule;
import dev.starcore.starcore.module.officer.OfficerModule;
import dev.starcore.starcore.module.policy.PolicyModule;
import dev.starcore.starcore.module.resource.ResourceModule;
import dev.starcore.starcore.module.resolution.ResolutionModule;
import dev.starcore.starcore.module.technology.TechnologyModule;
import dev.starcore.starcore.module.treasury.TreasuryModule;
import dev.starcore.starcore.module.treasury.LoanServiceImpl;
import dev.starcore.starcore.module.treasury.BudgetServiceImpl;
import dev.starcore.starcore.module.treasury.BankruptcyServiceImpl;
import dev.starcore.starcore.module.war.WarModule;
import dev.starcore.starcore.module.weather.WeatherModule;
import dev.starcore.starcore.module.blueprint.BlueprintModule;
import dev.starcore.starcore.module.territory.upgrade.TerritoryUpgradeModule;
import dev.starcore.starcore.clan.ClanManager;
import dev.starcore.starcore.mechanics.ReputationService;
import dev.starcore.starcore.module.anniversary.AnniversaryModule;
import dev.starcore.starcore.module.army.ArmyModule;
import dev.starcore.starcore.module.army.exercise.ExerciseModule;
import dev.starcore.starcore.module.army.doctrine.DoctrineModule;
import dev.starcore.starcore.module.army.mercenary.MercenaryModule;
import dev.starcore.starcore.module.army.navy.NavyModule;
import dev.starcore.starcore.module.banner.BannerModule;
import dev.starcore.starcore.module.combat.CombatModule;
import dev.starcore.starcore.module.split.SplitModule;
import dev.starcore.starcore.module.merge.MergeModule;
import dev.starcore.starcore.essentials.EssentialsModule;
import dev.starcore.starcore.pvp.PvPModule;
import dev.starcore.starcore.storage.StorageModule;
import dev.starcore.starcore.title.TitleModule;
import dev.starcore.starcore.util.MessageUtil;
import dev.starcore.starcore.core.scheduler.FoliaCompatScheduler;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class StarCorePlugin extends JavaPlugin {
    private StarCoreContext context;
    private StarCoreApi api;
    private RegionModule regionModule;
    private TitleModule titleModule;
    private dev.starcore.starcore.social.SocialModule socialModule;
    private dev.starcore.starcore.quest.QuestModule questModule;
    private dev.starcore.starcore.event.random.RandomEventService randomEventService;
    private RedisCrossServerService redisService;
    private CrossServerService crossServerService;
    private ClanManager clanManager;
    private ScreenShakeManager screenShakeManager;
    private SceneSoundService sceneSoundService;
    private SceneSoundConfig sceneSoundConfig;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        ConfigurationService configurationService = new ConfigurationService(this);

        // 校验配置文件版本
        configurationService.validateConfigVersion();

        // 安全检查：MySQL空密码验证
        validateDatabaseSecurity(configurationService);

        if (configurationService.ensureMapWebAccessSecretConfigured()) {
            getLogger().info("Generated map.web.access-secret for STARCORE map personal links.");
        }
        StarCoreScheduler scheduler = new StarCoreScheduler(this, configurationService.asyncThreads());
        StarCoreEventBus eventBus = new StarCoreEventBus(getLogger());
        PersistenceService persistenceService = new PersistenceService(this, scheduler);
        DatabaseService databaseService = new DatabaseService(this, configurationService);
        InternalPermissionService permissionService = new InternalPermissionService();
        InternalEconomyService economyService = new InternalEconomyService(databaseService, persistenceService, getLogger());
        PlatformAdapter platformAdapter = PlatformAdapterFactory.detect(this);
        ServiceRegistry serviceRegistry = new ServiceRegistry();
        ModuleManager moduleManager = new ModuleManager(getLogger(), serviceRegistry);

        this.context = new StarCoreContext(this, platformAdapter, configurationService, scheduler, eventBus, persistenceService, databaseService, permissionService, economyService, moduleManager, serviceRegistry);

        context.persistenceService().start();
        context.databaseService().start();
        context.economyService().start();

        // 初始化跨服通信服务
        RedisSettings redisSettings = RedisSettings.from(configurationService);
        this.redisService = new RedisCrossServerService(this, getLogger(), redisSettings);
        this.redisService.start(context);
        context.serviceRegistry().register(RedisCrossServerService.class, redisService);

        registerFoundationServices();
        context.serviceRegistry().require(TimeSyncService.class).start();

        // 初始化屏幕震动管理器
        this.screenShakeManager = new ScreenShakeManager(this);
        context.serviceRegistry().register(ScreenShakeManager.class, screenShakeManager);
        ScreenShake.init(screenShakeManager);
        getLogger().info("Screen shake manager initialized.");

        // 初始化场景化音效系统
        this.sceneSoundConfig = new SceneSoundConfig(this);
        this.sceneSoundService = new SceneSoundService(this);
        context.serviceRegistry().register(SceneSoundService.class, sceneSoundService);

        // 注册音效命令
        SceneSoundCommand soundCommand = new SceneSoundCommand(this, sceneSoundService, sceneSoundConfig);
        soundCommand.register();
        getLogger().info("Scene sound system initialized.");

        // 初始化 GUI 动画系统 (在模块启用前初始化)
        dev.starcore.starcore.foundation.animation.GuiAnimationRegistry.initialize(this);
        getLogger().info("GUI animation system initialized.");

        registerModules();
        initializeCrossServerServices();
        registerCommands();

        // 初始化权限工具类（必须在模块注册之后）
        dev.starcore.starcore.util.PermissionUtil.init(this);

        context.moduleManager().enableAll(context);

        // 初始化 HUD 菜单系统
        ModernHudListener.initialize(this);
        getLogger().info("Modern HUD system initialized.");

        // 初始化随机事件服务（在所有模块启用之后）
        initializeRandomEventService();

        // 称号模块（非 StarCoreModule，手动装配）
        if (context.configuration().moduleEnabled("title", true)) {
            this.titleModule = new TitleModule(this, context.databaseService(), context.serviceRegistry());
            this.titleModule.enable();
        }

        // 注册需要模块支持的辅助命令（必须在模块启用后）
        registerModuleDependentCommands();

        // 注意：任务模块（任务/每日任务/委托）现在由 QuestModule 管理
        // 参见 registerModules() 中的 QuestModule 注册

        // 初始化 ClanManager 并注册命令
        this.clanManager = new ClanManager();
        this.clanManager.setPlugin(this);
        this.clanManager.loadData(); // 加载保存的数据
        bindCommand("clan", new dev.starcore.starcore.clan.command.ClanCommand(clanManager));

        // 注册 Clan PvP 监听器
        getServer().getPluginManager().registerEvents(
            new dev.starcore.starcore.clan.listener.ClanPvPListener(clanManager), this
        );

        // 注册 Clan 聊天监听器
        getServer().getPluginManager().registerEvents(
            new dev.starcore.starcore.clan.chat.ClanChatChannel(clanManager), this
        );

        // 初始化 MessageUtil（获取 NationService）
        dev.starcore.starcore.module.nation.NationModule nationModule =
            (dev.starcore.starcore.module.nation.NationModule) context.serviceRegistry()
                .find(dev.starcore.starcore.module.nation.NationService.class)
                .orElse(null);
        MessageUtil.init(nationModule, clanManager);

        // 注册 PlaceholderAPI 扩展
        registerPlaceholderAPI();

        registerApi();

        // 向 GUI 静态菜单注入共享服务，使统计/社交菜单显示真实数据
        // Nukkit GUI system removed - commented out
        /*
        dev.starcore.starcore.gui.GuiServices.init(
            context.economyService(),
            context.serviceRegistry().find(dev.starcore.starcore.pvp.stats.PvPStatsService.class).orElse(null),
            socialModule != null ? socialModule.friendService() : null,
            socialModule != null ? socialModule.guildService() : null,
            socialModule != null ? socialModule.partyService() : null
        );
        */

        getLogger().info("Platform adapter: " + platformAdapter.platformName() + " | " + platformAdapter.supportSummary());
        getLogger().info("STARCORE " + getPluginMeta().getVersion() + " enabled.");
    }

    @Override
    public void onDisable() {
        if (context == null) {
            return;
        }
        unregisterApi();

        // 保存领土数据到存储
        try {
            ModernHudListener.getInstance().shutdown();
        } catch (IllegalStateException ignored) {
            // HUD 未初始化，忽略
        }

        context.serviceRegistry().find(dev.starcore.starcore.territory.TerritoryService.class)
            .ifPresent(dev.starcore.starcore.territory.TerritoryService::save);
        context.serviceRegistry().find(SubRegionService.class)
            .ifPresent(SubRegionService::save);

        // 关闭区域模块
        if (regionModule != null) {
            regionModule.shutdown();
        }

        // 关闭称号模块
        if (titleModule != null) {
            titleModule.disable();
        }

        // 关闭随机事件服务
        if (randomEventService != null) {
            randomEventService.shutdown();
            getLogger().info("RandomEventService shut down.");
        }

        // 清理 MessageUtil 静态引用
        MessageUtil.shutdown();

        context.serviceRegistry().find(TimeSyncService.class).ifPresent(TimeSyncService::stop);
        context.moduleManager().disableAll(context);
        context.economyService().stop();
        if (redisService != null) {
            redisService.stop(context);
        }
        crossServerService = null;
        // 保存 ClanManager 数据
        if (clanManager != null) {
            clanManager.saveData();
        }
        // 注意：任务模块（QuestModule）禁用时会自动保存数据

        context.databaseService().stop();
        context.persistenceService().stop();
        context.scheduler().shutdown();
        getLogger().info("STARCORE disabled.");
    }

    public StarCoreContext context() {
        return context;
    }

    private void registerFoundationServices() {
        context.serviceRegistry().register(PerformanceMetricsService.class, new PerformanceMetricsService());
        context.serviceRegistry().register(CacheManager.class, new CacheManager());
        context.serviceRegistry().register(PlayerProfileService.class, new InMemoryPlayerProfileService());
        context.serviceRegistry().register(OnlinePlayerDirectory.class, new BukkitOnlinePlayerDirectory());

        // 注册国际化管理器
        I18nManager i18nManager = new I18nManager(this, context.platformAdapter());
        i18nManager.setDatabaseService(context.databaseService());
        i18nManager.setDefaultLocale(context.configuration().locale());
        context.serviceRegistry().register(I18nManager.class, i18nManager);

        // 注册消息服务（使用国际化管理器作为实现）
        context.serviceRegistry().register(MessageService.class, i18nManager);

        MessageService messageService = new SimpleMessageService(this, context.configuration());
        context.serviceRegistry().register(EpochService.class, new EpochService(context.configuration(), messageService));
        context.serviceRegistry().register(TimeSyncService.class, new TimeSyncService(this, context.configuration(), messageService));

        // 注册Foundation的TerritoryService（基于chunk的）
        dev.starcore.starcore.foundation.territory.TerritoryService foundationTerritoryService =
            new DatabaseAwareTerritoryService(context.databaseService(), context.persistenceService(), getLogger());
        context.serviceRegistry().register(dev.starcore.starcore.foundation.territory.TerritoryService.class, foundationTerritoryService);

        // 创建领土存储（支持 SQL/Properties 双模式持久化）
        TerritoryStorage territoryStorage = TerritoryStorageFactory.create(
            this, context.databaseService(), context.persistenceService()
        );
        context.serviceRegistry().register(TerritoryStorage.class, territoryStorage);

        // 注册Territory包的TerritoryService（基于坐标的）
        dev.starcore.starcore.territory.TerritoryService territoryService =
            new dev.starcore.starcore.territory.TerritoryService(this, territoryStorage);
        context.serviceRegistry().register(dev.starcore.starcore.territory.TerritoryService.class, territoryService);
        territoryService.load(); // 从存储加载

        // 注册子区域服务
        SubRegionService subRegionService = new SubRegionService(this, territoryService, territoryStorage);
        context.serviceRegistry().register(SubRegionService.class, subRegionService);
        subRegionService.load(); // 从存储加载

        // 注册区域模块（同时传递两个服务）
        // - foundationTerritoryService: Chunk 级别，用于王国/国家检测
        // - territoryService: 坐标级别，用于子区域检测
        this.regionModule = new RegionModule(
            this,
            foundationTerritoryService,
            territoryService,
            subRegionService,
            context.serviceRegistry()
        );

        context.serviceRegistry().register(ExternalProtectionService.class, createExternalProtectionService());
        context.serviceRegistry().register(DatabaseService.class, context.databaseService());
        context.serviceRegistry().register(InternalPermissionService.class, context.permissionService());
        context.serviceRegistry().register(InternalEconomyService.class, context.economyService());
        context.serviceRegistry().register(dev.starcore.starcore.foundation.economy.EconomyService.class, context.economyService());

        // 注册 PlayerVaultEconomyService（使用 Vault 经济插件，如 EssentialsX）
        PlayerVaultEconomyService playerVaultEconomyService = new PlayerVaultEconomyService(getLogger());
        context.serviceRegistry().register(PlayerVaultEconomyService.class, playerVaultEconomyService);
    }

    private ExternalProtectionService createExternalProtectionService() {
        return ExternalProtectionServices.create(this, context.configuration(), getLogger());
    }

    private void registerModules() {
        ModuleManager modules = context.moduleManager();
        if (context.configuration().moduleEnabled("nation")) modules.register(new NationModule());
        if (context.configuration().moduleEnabled("city", true)) modules.register(new CityModule());
        if (context.configuration().moduleEnabled("government")) modules.register(new GovernmentModule());
        if (context.configuration().moduleEnabled("resolution")) modules.register(new ResolutionModule());
        if (context.configuration().moduleEnabled("treasury")) modules.register(new TreasuryModule());
        if (context.configuration().moduleEnabled("treasury")) {
            // 注册财政子系统模块
            modules.register(new LoanServiceImpl());
            modules.register(new BudgetServiceImpl());
            modules.register(new BankruptcyServiceImpl());
        }
        if (context.configuration().moduleEnabled("diplomacy")) modules.register(new DiplomacyModule());
        // 联盟外交系统模块
        if (context.configuration().moduleEnabled("alliance", true)) modules.register(new AllianceModule());
        if (context.configuration().moduleEnabled("policy")) modules.register(new PolicyModule());
        if (context.configuration().moduleEnabled("resource")) modules.register(new ResourceModule());
        if (context.configuration().moduleEnabled("technology")) modules.register(new TechnologyModule());
        if (context.configuration().moduleEnabled("war")) modules.register(new WarModule());
        if (context.configuration().moduleEnabled("officer")) modules.register(new OfficerModule());
        if (context.configuration().moduleEnabled("event")) modules.register(new EventModule());
        if (context.configuration().moduleEnabled("map")) modules.register(new MapModule());
        // 功能模块（配置缺失时默认开启，兼容已部署服务器的旧 config.yml）
        if (context.configuration().moduleEnabled("essentials", true)) {
            modules.register(new EssentialsModule(this, new FoliaCompatScheduler(this), context.serviceRegistry().require(MessageService.class), context.economyService()));
        }
        if (context.configuration().moduleEnabled("pvp", true)) modules.register(new PvPModule());
        if (context.configuration().moduleEnabled("army", true)) modules.register(new ArmyModule());
        if (context.configuration().moduleEnabled("exercise", true)) modules.register(new ExerciseModule());
        if (context.configuration().moduleEnabled("wounded", true)) modules.register(new dev.starcore.starcore.module.army.wounded.WoundedModule());
        if (context.configuration().moduleEnabled("navy", true)) modules.register(new NavyModule());
        if (context.configuration().moduleEnabled("mercenary", true)) modules.register(new MercenaryModule());
        // 军事学说系统模块
        if (context.configuration().moduleEnabled("doctrine", true)) modules.register(new DoctrineModule());
        if (context.configuration().moduleEnabled("faith", true)) modules.register(new dev.starcore.starcore.module.faith.FaithModule());
        if (context.configuration().moduleEnabled("banner", true)) modules.register(new BannerModule());
        if (context.configuration().moduleEnabled("split", true)) modules.register(new SplitModule());
        if (context.configuration().moduleEnabled("storage", true)) modules.register(new StorageModule());
        if (context.configuration().moduleEnabled("social", true)) {
            this.socialModule = new dev.starcore.starcore.social.SocialModule();
            modules.register(this.socialModule);
        }
        // 任务模块（任务/每日任务/委托系统）
        if (context.configuration().moduleEnabled("quest", true)) {
            this.questModule = new dev.starcore.starcore.quest.QuestModule();
            modules.register(this.questModule);
        }
        // 邮件模块
        if (context.configuration().moduleEnabled("mail", true)) {
            modules.register(new dev.starcore.starcore.social.mail.MailModule());
        }
        // 成就模块
        if (context.configuration().moduleEnabled("achievement", true)) {
            modules.register(new dev.starcore.starcore.achievement.AchievementModule(this));
        }
        // 宠物/坐骑模块
        if (context.configuration().moduleEnabled("pet", true)) {
            modules.register(new dev.starcore.starcore.pet.PetModule());
        }
        // 经济区模块
        if (context.configuration().moduleEnabled("zone", true)) {
            modules.register(new dev.starcore.starcore.zone.ZoneModule());
        }
        // 锦标赛模块
        if (context.configuration().moduleEnabled("tournament", true)) {
            modules.register(new dev.starcore.starcore.module.tournament.TournamentModule());
        }
        // 天气控制模块
        if (context.configuration().moduleEnabled("weather", true)) {
            modules.register(new WeatherModule());
        }
        // 战斗系统模块
        if (context.configuration().moduleEnabled("combat", true)) {
            modules.register(new CombatModule());
        }
        // 商店系统模块
        if (context.configuration().moduleEnabled("shop", true)) {
            modules.register(new dev.starcore.starcore.module.shop.ShopModule());
        }
        // 领地升级模块
        if (context.configuration().moduleEnabled("territory_upgrade", true)) {
            modules.register(new dev.starcore.starcore.module.territory.upgrade.TerritoryUpgradeModule());
        }
        // 副本系统模块
        if (context.configuration().moduleEnabled("dungeon", true)) {
            modules.register(new dev.starcore.starcore.module.dungeon.DungeonModule());
        }
        // 蓝图系统模块
        if (context.configuration().moduleEnabled("blueprint", true)) {
            modules.register(new BlueprintModule());
        }
        // 商业系统模块
        if (context.configuration().moduleEnabled("business", true)) {
            modules.register(new dev.starcore.starcore.module.business.service.BusinessServiceImpl());
        }
        // 社会模拟模块（声望/关系/影响力/文化/新闻/事件/阶层/联盟/八卦/活动）
        if (context.configuration().moduleEnabled("social_simulation", true)) {
            modules.register(new dev.starcore.starcore.social.SocialSimulationModule());
        }
        // 纪念日系统模块
        if (context.configuration().moduleEnabled("anniversary", true)) {
            modules.register(new AnniversaryModule());
        }
        // 紧急状态系统模块
        if (context.configuration().moduleEnabled("emergency", true)) {
            modules.register(new dev.starcore.starcore.module.emergency.EmergencyModule());
        }
        // 契约制度/租约系统模块
        if (context.configuration().moduleEnabled("lease", true)) {
            modules.register(new dev.starcore.starcore.module.lease.LeaseModule());
        }
        // 领土租借系统模块
        if (context.configuration().moduleEnabled("territory_rent", true)) {
            modules.register(new dev.starcore.starcore.module.territory.rent.TerritoryRentModule());
        }
        // 领土仲裁系统模块
        if (context.configuration().moduleEnabled("arbitration", true)) {
            modules.register(new dev.starcore.starcore.module.arbitration.ArbitrationModule());
        }
        // 繁荣度系统模块
        if (context.configuration().moduleEnabled("prosperity", true)) {
            modules.register(new dev.starcore.starcore.module.prosperity.ProsperityModule());
        }
        // 合并公投系统模块
        if (context.configuration().moduleEnabled("merge", true)) {
            modules.register(new dev.starcore.starcore.module.merge.MergeModule());
        }
        // 王位继承系统模块
        if (context.configuration().moduleEnabled("dynasty", true)) {
            modules.register(new dev.starcore.starcore.module.dynasty.DynastyModule());
        }
        // 军事指挥中心模块（战况预览）
        if (context.configuration().moduleEnabled("military", true)) {
            modules.register(new dev.starcore.starcore.module.military.MilitaryModule());
        }
        // 邮件系统模块
        if (context.configuration().moduleEnabled("mail", true)) {
            modules.register(new dev.starcore.starcore.module.mail.MailModule());
        }
        // 领土升级模块
        if (context.configuration().moduleEnabled("territory_upgrade", true)) {
            modules.register(new dev.starcore.starcore.module.territory.upgrade.TerritoryUpgradeModule());
        }
        // 领土租借模块
        if (context.configuration().moduleEnabled("territory_rent", true)) {
            modules.register(new dev.starcore.starcore.module.territory.rent.TerritoryRentModule());
        }
    }

    private void registerCommands() {
        PluginCommand command = getCommand("starcore");
        if (command == null) {
            throw new IllegalStateException("Command 'starcore' is missing from plugin.yml");
        }
        StarCoreCommand executor = new StarCoreCommand(context);
        command.setExecutor(executor);
        command.setTabCompleter(executor);

        registerAuxiliaryCommands();
    }

    /**
     * 注册不属于 ModuleManager 的辅助命令（管理、经济、社交）
     * 注意：菜单命令需要在模块启用后注册，见 registerModuleDependentCommands()
     */
    private void registerAuxiliaryCommands() {
        // 管理命令（mute/unmute/kick/ban/unban/jail/unjail/vanish 共用一个执行器，按命令名分发）
        var moderationExecutor = new dev.starcore.starcore.moderation.command.ModerationCommand(
            new dev.starcore.starcore.moderation.ModerationService(),
            new dev.starcore.starcore.moderation.VanishService()
        );
        for (String name : new String[] {"mute", "unmute", "kick", "ban", "unban", "jail", "unjail", "vanish"}) {
            bindCommand(name, moderationExecutor);
        }

        // 经济命令（pay/balance）
        var economyExecutor = new dev.starcore.starcore.essentials.command.EconomyCommand(
            context.economyService(),
            new dev.starcore.starcore.essentials.baltop.BalTopService()
        );
        bindCommand("pay", economyExecutor);
        bindCommand("balance", economyExecutor);

        // 语言切换命令
        I18nManager i18nManager = context.serviceRegistry().find(I18nManager.class).orElse(null);
        if (i18nManager != null) {
            bindCommand("language", new dev.starcore.starcore.foundation.i18n.command.LanguageCommand(i18nManager));
        }

        // 压力测试命令
        var stressTestExecutor = new dev.starcore.starcore.monitoring.StressTestCommand(
            this,
            context.economyService(),
            context.serviceRegistry().find(dev.starcore.starcore.module.war.WarService.class).orElse(null)
        );
        bindCommand("stresstest", stressTestExecutor);

        // 好友/公会/派对命令由 SocialModule 用共享+持久化的服务实例注册
    }

    /**
     * 绑定命令执行器（同时设置 TabCompleter，若执行器支持）
     */
    private void bindCommand(String name, org.bukkit.command.CommandExecutor executor) {
        PluginCommand cmd = getCommand(name);
        if (cmd == null) {
            getLogger().warning("命令 '" + name + "' 未在 plugin.yml 声明，跳过注册");
            return;
        }
        cmd.setExecutor(executor);
        if (executor instanceof org.bukkit.command.TabCompleter completer) {
            cmd.setTabCompleter(completer);
        }
    }

    private void registerApi() {
        this.api = new StarCoreApiProvider(context);
        getServer().getServicesManager().register(StarCoreApi.class, api, this, ServicePriority.Normal);
        getLogger().info("Registered STARCORE public API.");
    }

    private void unregisterApi() {
        if (api == null) {
            return;
        }
        getServer().getServicesManager().unregister(StarCoreApi.class, api);
        api = null;
    }

    /**
     * 验证数据库配置安全性 - 防止空密码和弱配置
     */
    private void validateDatabaseSecurity(ConfigurationService configService) {
        String dbType = getConfig().getString("database.type", "sqlite");

        if ("mysql".equalsIgnoreCase(dbType)) {
            String password = getConfig().getString("database.mysql.password", "");

            // 检查空密码
            if (password.isEmpty()) {
                getLogger().severe("========================================");
                getLogger().severe("  ⚠️  SECURITY ALERT - 安全警告");
                getLogger().severe("========================================");
                getLogger().severe("检测到 MySQL 数据库配置使用空密码！");
                getLogger().severe("这是严重的安全漏洞，可能导致：");
                getLogger().severe("  - 数据库完全暴露");
                getLogger().severe("  - 玩家数据被窃取或篡改");
                getLogger().severe("  - 服务器被完全控制");
                getLogger().severe("");
                getLogger().severe("解决方案：");
                getLogger().severe("1. 为 MySQL 设置强密码（至少16字符）");
                getLogger().severe("2. 在 config.yml 中更新 database.mysql.password");
                getLogger().severe("3. 或切换到 SQLite: database.type: sqlite");
                getLogger().severe("========================================");
                getLogger().severe("插件拒绝启动以保护服务器安全！");
                getLogger().severe("========================================");

                // 抛出异常阻止插件启动
                throw new SecurityException("MySQL password cannot be empty! 数据库密码不能为空！");
            }

            // 检查弱密码
            if (password.length() < 8) {
                getLogger().warning("========================================");
                getLogger().warning("  ⚠️  SECURITY WARNING - 安全警告");
                getLogger().warning("========================================");
                getLogger().warning("MySQL 密码过短（少于8字符）！");
                getLogger().warning("建议使用至少16字符的强密码。");
                getLogger().warning("========================================");
            }

            // 检查默认密码
            if ("starcore".equalsIgnoreCase(password) || "password".equalsIgnoreCase(password) ||
                "123456".equals(password) || "admin".equalsIgnoreCase(password)) {
                getLogger().severe("========================================");
                getLogger().severe("  ⚠️  SECURITY ALERT - 安全警告");
                getLogger().severe("========================================");
                getLogger().severe("检测到使用默认或弱密码（长度：" + password.length() + " 字符）");
                getLogger().severe("这是严重的安全风险！");
                getLogger().severe("请立即更换为强随机密码！");
                getLogger().severe("========================================");
                throw new SecurityException("Default or weak MySQL password detected! 检测到默认或弱密码！");
            }

            getLogger().info("✅ 数据库安全验证通过");
        }
    }

    /**
     * 注册依赖模块的命令（必须在模块启用后调用）
     */
    private void registerModuleDependentCommands() {
        // 主菜单 - 使用 NationModule 菜单系统
        dev.starcore.starcore.module.nation.NationModule nationModule =
            (dev.starcore.starcore.module.nation.NationModule) context.serviceRegistry()
                .find(dev.starcore.starcore.module.nation.NationService.class)
                .orElse(null);

        if (nationModule != null && nationModule.getMenuProvider() != null) {
            bindCommand("menu", new dev.starcore.starcore.module.nation.command.MenuCommand(
                nationModule,
                context.serviceRegistry().require(MessageService.class)
            ));
            getLogger().info("STARCORE menu command registered.");

            // 注册新的 HUD 菜单命令
            bindCommand("hud", new HudCommand(
                this,
                nationModule,
                context.serviceRegistry().require(MessageService.class)
            ));
            getLogger().info("HUD menu command registered (/hud).");
        } else {
            getLogger().warning("Nation module or menu provider not available, /menu command not registered.");
        }

        // 成就命令
        if (context.configuration().moduleEnabled("achievement", true)) {
            bindCommand("achievements", new dev.starcore.starcore.achievement.command.AchievementCommand(
                this, context.serviceRegistry()));
            getLogger().info("Achievement command registered.");
        }
    }

    /**
     * 初始化随机事件服务
     * 必须在所有业务模块启用后调用，以确保服务引用可用
     */
    private void initializeRandomEventService() {
        // 从 ServiceRegistry 获取所需服务
        dev.starcore.starcore.module.nation.NationService nationService =
            context.serviceRegistry().find(dev.starcore.starcore.module.nation.NationService.class).orElse(null);
        dev.starcore.starcore.module.resource.ResourceService resourceService =
            context.serviceRegistry().find(dev.starcore.starcore.module.resource.ResourceService.class).orElse(null);
        dev.starcore.starcore.module.treasury.TreasuryService treasuryService =
            context.serviceRegistry().find(dev.starcore.starcore.module.treasury.TreasuryService.class).orElse(null);
        dev.starcore.starcore.module.technology.TechnologyService technologyService =
            context.serviceRegistry().find(dev.starcore.starcore.module.technology.TechnologyService.class).orElse(null);
        dev.starcore.starcore.module.diplomacy.DiplomacyService diplomacyService =
            context.serviceRegistry().find(dev.starcore.starcore.module.diplomacy.DiplomacyService.class).orElse(null);

        // 创建并初始化 RandomEventService
        this.randomEventService = new dev.starcore.starcore.event.random.RandomEventService(
            this,
            context.economyService(),
            nationService,
            resourceService,
            treasuryService,
            technologyService,
            diplomacyService
        );

        // 设置服务注册表（用于延迟解析）
        this.randomEventService.setServiceRegistry(context.serviceRegistry());

        // 执行初始化（注入所有静态服务引用）
        this.randomEventService.initialize();

        // 注册到 ServiceRegistry（供其他组件使用）
        context.serviceRegistry().register(
            dev.starcore.starcore.event.random.RandomEventService.class,
            this.randomEventService
        );

        getLogger().info("RandomEventService initialized successfully.");
    }

    /**
     * 注册 PlaceholderAPI 扩展
     */
    private void registerPlaceholderAPI() {
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                dev.starcore.starcore.pvp.stats.PvPStatsService statsService =
                    context.serviceRegistry().find(dev.starcore.starcore.pvp.stats.PvPStatsService.class)
                        .orElse(null);

                if (statsService != null) {
                    // 注册 RankingService（如果尚未注册）
                    if (context.serviceRegistry().find(dev.starcore.starcore.ranking.RankingService.class).isEmpty()) {
                        dev.starcore.starcore.ranking.RankingService rankingService =
                            new dev.starcore.starcore.ranking.RankingServiceImpl(this, statsService);
                        context.serviceRegistry().register(dev.starcore.starcore.ranking.RankingService.class, rankingService);
                        getLogger().info("RankingService registered.");
                    }

                    // 注册排行榜命令
                    dev.starcore.starcore.ranking.RankingService rankingService =
                        context.serviceRegistry().require(dev.starcore.starcore.ranking.RankingService.class);
                    dev.starcore.starcore.ranking.command.RankingCommand rankCommand =
                        new dev.starcore.starcore.ranking.command.RankingCommand(this, rankingService, statsService);

                    bindCommand("rank", rankCommand);
                    bindCommand("leaderboard", rankCommand);
                    bindCommand("top", rankCommand);
                    getLogger().info("Ranking commands registered.");

                    // 注册排行榜 GUI
                    new dev.starcore.starcore.ranking.gui.RankingGUI(this, rankingService, statsService);
                    getLogger().info("RankingGUI registered.");

                    new dev.starcore.starcore.integration.papi.StarcorePlaceholder(
                        this,
                        context.economyService(),
                        statsService
                    ).register();
                    getLogger().info("STARCORE PlaceholderAPI expansion registered.");
                } else {
                    getLogger().warning("PvP stats service not available, PlaceholderAPI expansion not registered.");
                }
            } catch (Exception e) {
                getLogger().warning("Failed to register PlaceholderAPI expansion: " + e.getMessage());
            }
        }

        // 数据库迁移命令
        bindCommand("dbmigrate", new dev.starcore.starcore.core.database.MigrationCommand(context));
        getLogger().info("Database migration command registered.");

        // 注册统计报告系统命令
        registerStatsCommands();
    }

    /**
     * 注册统计报告命令
     */
    private void registerStatsCommands() {
        try {
            dev.starcore.starcore.pvp.stats.PvPStatsService statsService =
                context.serviceRegistry().find(dev.starcore.starcore.pvp.stats.PvPStatsService.class)
                    .orElse(null);
            dev.starcore.starcore.ranking.RankingService rankingService =
                context.serviceRegistry().find(dev.starcore.starcore.ranking.RankingService.class)
                    .orElse(null);

            if (statsService != null && rankingService != null) {
                // 创建并初始化 StatsService
                dev.starcore.starcore.stats.StatsService statsReportService =
                    new dev.starcore.starcore.stats.StatsService(this, rankingService, statsService);
                statsReportService.initialize();

                // 注册到 ServiceRegistry
                context.serviceRegistry().register(dev.starcore.starcore.stats.StatsService.class, statsReportService);

                // 注册统计命令
                dev.starcore.starcore.stats.command.StatsCommand statsCommand =
                    new dev.starcore.starcore.stats.command.StatsCommand(statsReportService, rankingService);
                bindCommand("stats", statsCommand);
                getLogger().info("Stats command system registered.");
            }
        } catch (Exception e) {
            getLogger().warning("Failed to register stats command: " + e.getMessage());
        }
    }

    /**
     * 初始化跨服通信服务
     * 在所有模块启用后调用，确保所需服务都已注册
     */
    private void initializeCrossServerServices() {
        if (redisService == null || !redisService.isConnected()) {
            getLogger().info("[跨服] Redis未连接，跳过跨服服务初始化");
            return;
        }

        // 创建统一的跨服服务
        this.crossServerService = new CrossServerService(getConfig().getString("server.id", "server-1"), getLogger());

        // 获取所需服务
        dev.starcore.starcore.module.nation.NationService nationService =
            context.serviceRegistry().find(dev.starcore.starcore.module.nation.NationService.class).orElse(null);
        dev.starcore.starcore.module.treasury.TreasuryService treasuryService =
            context.serviceRegistry().find(dev.starcore.starcore.module.treasury.TreasuryService.class).orElse(null);
        dev.starcore.starcore.module.diplomacy.DiplomacyService diplomacyService =
            context.serviceRegistry().find(dev.starcore.starcore.module.diplomacy.DiplomacyService.class).orElse(null);
        dev.starcore.starcore.module.war.WarService warService =
            context.serviceRegistry().find(dev.starcore.starcore.module.war.WarService.class).orElse(null);
        dev.starcore.starcore.pvp.stats.PvPStatsService statsService =
            context.serviceRegistry().find(dev.starcore.starcore.pvp.stats.PvPStatsService.class).orElse(null);
        dev.starcore.starcore.foundation.territory.TerritoryService territoryService =
            context.serviceRegistry().find(dev.starcore.starcore.foundation.territory.TerritoryService.class).orElse(null);

        // 初始化聊天跨服
        crossServerService.initializeChatSync(redisService, this);

        // 初始化国家数据同步
        crossServerService.initializeNationSync(redisService, nationService, treasuryService, diplomacyService, this);

        // 初始化战争状态同步
        crossServerService.initializeWarSync(redisService, warService, this);

        // 初始化领土同步
        crossServerService.initializeTerritorySync(redisService, territoryService, warService, this);

        // 注册到 ServiceRegistry
        context.serviceRegistry().register(CrossServerService.class, crossServerService);

        getLogger().info("[跨服] 跨服通信服务已初始化");
        getLogger().info(redisService.getStatus());
    }
}
