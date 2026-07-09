package dev.starcore.starcore.social;

import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.database.DatabaseService;
import dev.starcore.starcore.core.module.ModuleLayer;
import dev.starcore.starcore.core.module.ModuleMetadata;
import dev.starcore.starcore.core.module.StarCoreModule;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.social.simulation.*;
import dev.starcore.starcore.social.simulation.command.InfluenceLeaderboardCommand;
import dev.starcore.starcore.social.simulation.command.SocialSimCommand;
import dev.starcore.starcore.social.simulation.events.*;
import dev.starcore.starcore.event.player.*;
import dev.starcore.starcore.title.TitleService;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * 社会模拟模块
 *
 * 提供真实的社会互动模拟，包括:
 * - 声望系统: 玩家通过行为积累声望
 * - 关系网络: 追踪玩家间的复杂关系
 * - 社会影响力: 玩家的社交影响力
 * - 文化系统: 国家文化发展
 * - 新闻传播: 信息在社会中的流动
 * - 社会事件: 节日、庆典、抗议等
 * - 社会阶层: 玩家社会地位系统
 * - 社交联盟: 玩家联盟组织
 * - 八卦传播: 流言蜚语系统
 * - 社交活动: 聚会、庆典、比赛等
 */
public final class SocialSimulationModule implements StarCoreModule {

    private static final ModuleMetadata METADATA = new ModuleMetadata(
        "social-simulation",
        "社会模拟",
        ModuleLayer.MODULE,
        List.of("nation", "social"),
        List.of(ReputationService.class, RelationshipNetwork.class, SocialInfluenceService.class,
                CultureService.class, NewsPropagationService.class, SocialEventScheduler.class,
                SocialClassService.class, SocialAllianceService.class, GossipService.class,
                SocialActivityService.class, GossipVerificationService.class,
                InfluenceLeaderboardService.class),
        "Realistic social simulation: reputation, relationships, influence, culture, news, class, alliance, gossip, activities, leaderboard."
    );

    private JavaPlugin plugin;
    private ReputationService reputationService;
    private RelationshipNetwork relationshipNetwork;
    private SocialInfluenceService influenceService;
    private CultureService cultureService;
    private NewsPropagationService newsService;
    private SocialEventScheduler eventScheduler;
    private SocialSimulationListener listener;

    // 新增服务
    private SocialClassService classService;
    private SocialAllianceService allianceService;
    private GossipService gossipService;
    private SocialActivityService activityService;
    private ActivityRewardService activityRewardService;
    private CultureConflictService cultureConflictService;
    private TitleService titleService;
    private GossipVerificationService gossipVerificationService;

    // 影响力排行榜
    private InfluenceLeaderboardService leaderboardService;
    private InfluenceLeaderboardGui leaderboardGui;

    @Override
    public ModuleMetadata metadata() {
        return METADATA;
    }

    @Override
    public void enable(StarCoreContext context) {
        this.plugin = (JavaPlugin) context.plugin();

        // 获取可选服务
        NationService nationService = context.serviceRegistry().find(NationService.class).orElse(null);
        DatabaseService databaseService = context.databaseService();

        try {
            // 初始化核心服务
            this.reputationService = new ReputationServiceImpl(plugin, databaseService);
            this.relationshipNetwork = new RelationshipNetworkImpl(plugin, databaseService);
            this.influenceService = new SocialInfluenceServiceImpl(plugin, reputationService, relationshipNetwork, databaseService);
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to initialize social simulation core services: " + e.getMessage());
            plugin.getLogger().warning("Social simulation module will have limited functionality");
            // 使用最小化配置继续
            this.reputationService = new ReputationServiceImpl(plugin, null);
            this.relationshipNetwork = new RelationshipNetworkImpl(plugin, null);
            this.influenceService = null;
        }

        // CultureService 需要 NationService，如果不可用则使用简化版本
        if (nationService != null) {
            this.cultureService = new CultureServiceImpl(plugin, nationService, databaseService);
        } else {
            this.cultureService = new CultureServiceImpl(plugin);
            plugin.getLogger().warning("NationService not available, CultureService will have limited functionality");
        }

        this.newsService = new NewsPropagationService(plugin, relationshipNetwork);
        this.eventScheduler = new SocialEventScheduler(plugin, reputationService, relationshipNetwork, cultureService);
        // 初始化事件调度器的持久化
        this.eventScheduler.initializePersistence(plugin, databaseService);

        // 初始化新服务
        this.classService = new SocialClassService(plugin);
        // 初始化联盟服务（带数据库持久化）
        this.allianceService = new SocialAllianceService(plugin, databaseService, context.scheduler());
        this.gossipService = new GossipService(plugin, relationshipNetwork, reputationService);
        this.activityService = new SocialActivityService();
        // 初始化活动服务的持久化
        this.activityService.initialize(plugin, databaseService);

        // 初始化活动奖励服务
        this.titleService = context.serviceRegistry().find(TitleService.class).orElse(null);
        if (titleService != null && reputationService != null && influenceService != null) {
            this.activityRewardService = new ActivityRewardService(
                plugin,
                reputationService,
                influenceService,
                context.economyService(),
                titleService,
                activityService
            );
            // 加载参与数据
            this.activityRewardService.loadParticipationData();
        } else {
            this.activityRewardService = null;
            plugin.getLogger().warning("TitleService/ReputationService not available, ActivityRewardService will have limited functionality");
        }

        // 初始化文化冲突服务（如果 nationService 可用）
        if (nationService != null && context.serviceRegistry() != null) {
            this.cultureConflictService = new CultureConflictService(
                plugin,
                cultureService,
                nationService,
                context.serviceRegistry()
            );
        } else {
            this.cultureConflictService = new CultureConflictService(plugin, cultureService);
        }

        // 初始化八卦验证服务
        this.gossipVerificationService = new GossipVerificationService(
            gossipService,
            reputationService,
            relationshipNetwork
        );

        // 初始化影响力排行榜服务
        this.leaderboardService = new InfluenceLeaderboardService(plugin, databaseService, influenceService);
        this.leaderboardGui = new InfluenceLeaderboardGui(plugin, leaderboardService);

        // 初始化监听器
        this.listener = new SocialSimulationListener(
            reputationService,
            relationshipNetwork,
            influenceService,
            newsService,
            eventScheduler,
            cultureService,
            activityService,
            gossipService
        );
        listener.register(plugin);

        // 启动定时任务
        eventScheduler.start();

        // 启动新闻传播
        newsService.start();

        // 启动影响力衰减
        if (influenceService != null) {
            influenceService.startDecayTask();
        }

        // 注册社会模拟服务到服务注册表
        registerSocialSimulationServices(context);

        // 注册命令
        registerCommands(context);

        plugin.getLogger().info("社会模拟模块已启用: 声望/关系/影响力/文化/新闻/阶层/联盟/八卦/活动");
    }

    /**
     * 注册社会模拟服务到服务注册表
     */
    private void registerSocialSimulationServices(StarCoreContext context) {
        var registry = context.serviceRegistry();
        if (reputationService != null) {
            registry.register(ReputationService.class, reputationService);
        }
        if (gossipService != null) {
            registry.register(GossipService.class, gossipService);
        }
        if (influenceService != null) {
            registry.register(SocialInfluenceService.class, influenceService);
        }
        if (cultureService != null) {
            registry.register(CultureService.class, cultureService);
        }
        if (relationshipNetwork != null) {
            registry.register(RelationshipNetwork.class, relationshipNetwork);
        }
        if (gossipVerificationService != null) {
            registry.register(GossipVerificationService.class, gossipVerificationService);
        }
        plugin.getLogger().info("社会模拟服务已注册到服务注册表");
    }

    @Override
    public void disable(StarCoreContext context) {
        // 保存所有数据
        if (classService != null) classService.saveData();
        if (gossipService != null) gossipService.saveData();
        if (cultureService instanceof CultureServiceImpl cultureServiceImpl) {
            cultureServiceImpl.saveCultures();
        }
        if (activityRewardService != null) activityRewardService.saveParticipationData();

        if (eventScheduler != null) eventScheduler.stop();
        if (newsService != null) newsService.stop();
        if (influenceService != null) influenceService.stopDecayTask();
        if (leaderboardService != null) leaderboardService.shutdown();
        if (listener != null) listener.unregister();

        plugin.getLogger().info("社会模拟模块已关闭并保存数据");
    }

    /**
     * 注册命令
     */
    private void registerCommands(StarCoreContext context) {
        SocialSimCommand command = new SocialSimCommand(
            this,
            reputationService,
            relationshipNetwork,
            influenceService,
            classService,
            allianceService,
            gossipService,
            activityService,
            newsService
        );

        org.bukkit.command.CommandExecutor executor = (sender, cmd, label, args) -> {
            command.onCommand(sender, cmd, label, args);
            return true;
        };

        org.bukkit.command.TabCompleter completer = (sender, cmd, alias, args) ->
            command.onTabComplete(sender, cmd, alias, args);

        var socialsimCmd = plugin.getServer().getPluginCommand("socialsim");
        if (socialsimCmd != null) {
            socialsimCmd.setExecutor(executor);
            socialsimCmd.setTabCompleter(completer);
        }

        // 注册影响力排行榜命令
        InfluenceLeaderboardCommand leaderboardCmd = new InfluenceLeaderboardCommand(leaderboardGui);
        var influencelbCmd = plugin.getServer().getPluginCommand("influencelb");
        if (influencelbCmd != null) {
            influencelbCmd.setExecutor(leaderboardCmd);
            influencelbCmd.setTabCompleter(leaderboardCmd);
        }
    }

    // 服务获取方法
    public ReputationService reputationService() { return reputationService; }
    public RelationshipNetwork relationshipNetwork() { return relationshipNetwork; }
    public SocialInfluenceService influenceService() { return influenceService; }
    public CultureService cultureService() { return cultureService; }
    public CultureConflictService cultureConflictService() { return cultureConflictService; }
    public NewsPropagationService newsService() { return newsService; }
    public SocialEventScheduler eventScheduler() { return eventScheduler; }
    public SocialClassService classService() { return classService; }
    public SocialAllianceService allianceService() { return allianceService; }
    public GossipService gossipService() { return gossipService; }
    public SocialActivityService activityService() { return activityService; }
    public ActivityRewardService activityRewardService() { return activityRewardService; }
    public GossipVerificationService gossipVerificationService() { return gossipVerificationService; }
    public InfluenceLeaderboardService leaderboardService() { return leaderboardService; }
    public InfluenceLeaderboardGui leaderboardGui() { return leaderboardGui; }
}
