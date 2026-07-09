package dev.starcore.starcore.social;

import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.database.DatabaseService;
import dev.starcore.starcore.core.module.ModuleLayer;
import dev.starcore.starcore.core.module.ModuleMetadata;
import dev.starcore.starcore.core.module.StarCoreModule;
import dev.starcore.starcore.core.persistence.PersistenceService;
import dev.starcore.starcore.core.service.ServiceRegistry;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.chat.ChatFormatterService;
import dev.starcore.starcore.chat.BubbleChatVisualizer;
import dev.starcore.starcore.chat.EnhancedChatListener;
import dev.starcore.starcore.chat.ChatCommand;
import dev.starcore.starcore.moderation.mute.MuteService;
import dev.starcore.starcore.social.chat.PrivateMessageService;
import dev.starcore.starcore.social.command.FriendCommand;
import dev.starcore.starcore.social.command.GuildCommand;
import dev.starcore.starcore.social.command.MessageCommand;
import dev.starcore.starcore.social.command.PartyCommand;
import dev.starcore.starcore.social.emote.EmoteService;
import dev.starcore.starcore.social.emote.EmoteAnimationHandler;
import dev.starcore.starcore.social.emote.EmoteChatListener;
import dev.starcore.starcore.social.emote.CustomEmoteManager;
import dev.starcore.starcore.social.emote.EmoteCooldownManager;
import dev.starcore.starcore.social.emote.command.EmoteCommand;
import dev.starcore.starcore.social.emote.gui.EmoteMenu;
import dev.starcore.starcore.social.friend.FriendService;
import dev.starcore.starcore.social.gui.SocialMenuListener;
import dev.starcore.starcore.social.gui.ActivityRecommendationListener;
import dev.starcore.starcore.social.gui.SocialLeaderboardListener;
import dev.starcore.starcore.social.simulation.SocialActivityService;
import dev.starcore.starcore.social.simulation.SocialInfluenceService;
import dev.starcore.starcore.social.simulation.InfluenceLeaderboardService;
import dev.starcore.starcore.social.simulation.RelationshipNetwork;
import dev.starcore.starcore.social.simulation.RelationshipNetworkImpl;
import dev.starcore.starcore.social.simulation.RelationshipNetworkWrapper;
import dev.starcore.starcore.social.simulation.FriendRecommendationService;
import dev.starcore.starcore.social.simulation.SocialInfluenceServiceImpl;
import dev.starcore.starcore.social.guild.GuildService;
import dev.starcore.starcore.social.party.PartyService;
import dev.starcore.starcore.social.event.GuildCreateEvent;
import dev.starcore.starcore.social.event.GuildInviteEvent;
import dev.starcore.starcore.social.event.FriendRequestEvent;
import dev.starcore.starcore.social.event.PartyInviteEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 社交模块：统一持有好友/公会/派对的共享服务实例，
 * 注册命令，并对好友关系和公会做 YAML 持久化（重启不丢）。
 *
 * 支持双模式持久化：
 * 1. 数据库持久化（优先，当数据库可用时）
 * 2. YAML 文件持久化（降级方案）
 */
public final class SocialModule implements StarCoreModule {
    private JavaPlugin plugin;
    private PersistenceService persistenceService;
    private DatabaseService databaseService;
    private File friendsFile;
    private File guildsFile;

    private final FriendService friendService = new FriendService();
    private final GuildService guildService = new GuildService();
    private final PartyService partyService = new PartyService();
    private final PrivateMessageService messageService = new PrivateMessageService();
    private final EmoteService emoteService = new EmoteService();
    private final EmoteCooldownManager emoteCooldownManager = new EmoteCooldownManager();
    private final CustomEmoteManager customEmoteManager = new CustomEmoteManager(emoteService);

    private SocialMenuListener socialMenuListener;
    private SqlSocialStateStorage sqlStorage;
    private SocialImportExportService importExportService;
    private EmoteChatListener emoteChatListener;
    private EmoteAnimationHandler emoteAnimationHandler;
    private EmoteMenu emoteMenu;
    private dev.starcore.starcore.foundation.message.MessageService messageServiceCore;

    // 活动推荐和排行榜
    private ActivityRecommendationListener activityRecommendationListener;
    private SocialLeaderboardListener socialLeaderboardListener;
    private SocialActivityService activityService;
    private RelationshipNetwork relationshipNetwork;
    private FriendRecommendationService friendRecommendationService;

    // 聊天系统
    private ChatFormatterService chatFormatterService;
    private BubbleChatVisualizer bubbleChatVisualizer;
    private EnhancedChatListener enhancedChatListener;
    private ChatCommand chatCommand;

    @Override
    public ModuleMetadata metadata() {
        return new ModuleMetadata(
            "social", "社交系统", ModuleLayer.FEATURE,
            List.of(), List.of(),
            "好友/公会/派对系统（双模式持久化 + 导入导出）"
        );
    }

    public FriendService friendService() { return friendService; }
    public GuildService guildService() { return guildService; }
    public PartyService partyService() { return partyService; }
    public PrivateMessageService messageService() { return messageService; }
    public SocialMenuListener socialMenuListener() { return socialMenuListener; }
    public SqlSocialStateStorage sqlStorage() { return sqlStorage; }
    public SocialImportExportService importExportService() { return importExportService; }
    public EmoteService emoteService() { return emoteService; }
    public EmoteCooldownManager emoteCooldownManager() { return emoteCooldownManager; }
    public CustomEmoteManager customEmoteManager() { return customEmoteManager; }
    public EmoteChatListener emoteChatListener() { return emoteChatListener; }
    public EmoteAnimationHandler emoteAnimationHandler() { return emoteAnimationHandler; }
    public EmoteMenu emoteMenu() { return emoteMenu; }

    // 聊天系统 Getter
    public ChatFormatterService chatFormatterService() { return chatFormatterService; }
    public BubbleChatVisualizer bubbleChatVisualizer() { return bubbleChatVisualizer; }

    // 兼容旧代码 - 提供 PartyService 和 GuildService 的直接访问
    public PartyService getPartyService() { return partyService; }
    public GuildService getGuildService() { return guildService; }

    @Override
    public void enable(StarCoreContext context) {
        this.plugin = (JavaPlugin) context.plugin();
        this.persistenceService = context.persistenceService();
        this.databaseService = context.databaseService();
        this.messageServiceCore = context.serviceRegistry().require(MessageService.class);

        // 创建社交数据目录
        File dir = new File(plugin.getDataFolder(), "social");
        if (!dir.exists()) dir.mkdirs();

        this.friendsFile = new File(dir, "friends.yml");
        this.guildsFile = new File(dir, "guilds.yml");

        // 初始化数据库存储
        initializeSqlStorage();

        // 初始化导入导出服务
        initializeImportExport();

        // 初始化服务
        guildService.initialize(plugin);
        partyService.initialize(plugin);
        messageService.initialize(plugin);

        // 加载持久化数据
        loadFriends();
        loadGuilds();
        loadParties();
        // messageService 的 loadData 在 initialize 中已调用

        // 先初始化监听器（用于命令集成）
        initializeListeners();

        // 注册命令
        registerCommands();

        // 注册事件监听器
        registerListeners();

        // 注册社交事件监听器
        registerSocialEvents();

        // 初始化表情/动作系统
        initializeEmoteSystem();

        // 注册定时保存任务
        registerPeriodicSave();

        // 初始化聊天系统
        initializeChatSystem();

        // 初始化活动推荐和排行榜系统
        initializeActivityAndLeaderboardSystem();

        // 注册社交服务到服务注册表
        registerSocialServices(context);

        plugin.getLogger().info("社交模块已启用（好友/公会/派对/活动/排行榜）- " +
            (sqlStorage != null && sqlStorage.isDatabaseAvailable() ? "数据库模式" : "YAML模式"));
    }

    @Override
    public void disable(StarCoreContext context) {
        // 保存数据
        saveFriends();
        saveGuilds();
        saveParties();
        messageService.saveData();

        // 取消动画任务
        if (emoteAnimationHandler != null) {
            emoteAnimationHandler.cancelAllAnimations();
        }

        // 关闭气泡聊天
        if (bubbleChatVisualizer != null) {
            bubbleChatVisualizer.shutdown();
        }

        plugin.getLogger().info("社交模块已禁用，数据已保存");
    }

    /**
     * 初始化聊天系统
     */
    private void initializeChatSystem() {
        // 创建聊天格式化服务
        chatFormatterService = new ChatFormatterService(plugin);

        // 创建气泡聊天可视化
        bubbleChatVisualizer = new BubbleChatVisualizer(plugin, chatFormatterService);

        // 创建增强聊天监听器
        enhancedChatListener = new EnhancedChatListener(
            muteService(),
            chatFormatterService,
            null, // NationService 将在后续集成
            partyService,
            guildService
        );

        // 注册聊天监听器
        plugin.getServer().getPluginManager().registerEvents(enhancedChatListener, plugin);

        // 注册聊天命令
        chatCommand = new ChatCommand(chatFormatterService, bubbleChatVisualizer);
        bind("chat", chatCommand);

        plugin.getLogger().info("聊天系统已初始化（气泡/高亮/多频道）");
    }

    /**
     * 初始化活动推荐和排行榜系统
     */
    private void initializeActivityAndLeaderboardSystem() {
        // 创建活动服务
        activityService = new SocialActivityService();
        // 初始化活动服务的持久化
        if (databaseService != null) {
            activityService.initialize(plugin, databaseService);
        }

        // 创建关系网络
        RelationshipNetwork relNetwork;
        if (databaseService != null && databaseService.isRunning()) {
            relNetwork = new RelationshipNetworkImpl(plugin, databaseService);
        } else {
            relNetwork = new RelationshipNetworkWrapper(friendService);
        }

        // 创建好友推荐服务
        friendRecommendationService = new FriendRecommendationService(
            plugin, relNetwork, friendService
        );

        // 创建影响力服务和排行榜（需要数据库和 ReputationService）
        SocialInfluenceService influenceService = null;
        InfluenceLeaderboardService leaderboardService = null;

        // 注意：完整的影响力服务需要 ReputationService，这里使用降级方案
        // 如果需要完整功能，可以从 StarCoreContext 获取 ReputationService

        // 创建排行榜GUI监听器（即使没有完整的影响力服务也能显示基础排行榜）
        socialLeaderboardListener = new SocialLeaderboardListener(
            influenceService, leaderboardService, friendRecommendationService
        );
        plugin.getServer().getPluginManager().registerEvents(socialLeaderboardListener, plugin);

        // 创建活动推荐GUI监听器
        activityRecommendationListener = new ActivityRecommendationListener(activityService);
        plugin.getServer().getPluginManager().registerEvents(activityRecommendationListener, plugin);

        // 将监听器注入到社交菜单监听器
        if (socialMenuListener != null) {
            socialMenuListener.setActivityListener(activityRecommendationListener);
            socialMenuListener.setLeaderboardListener(socialLeaderboardListener);
        }

        plugin.getLogger().info("活动推荐和排行榜系统已初始化");
    }

    /**
     * 注册社交服务到服务注册表
     */
    private void registerSocialServices(StarCoreContext context) {
        var registry = context.serviceRegistry();
        registry.register(FriendService.class, friendService);
        registry.register(GuildService.class, guildService);
        registry.register(PartyService.class, partyService);
        registry.register(PrivateMessageService.class, messageService);
        registry.register(EmoteService.class, emoteService);
        plugin.getLogger().info("社交服务已注册到服务注册表");
    }

    /**
     * 获取禁言服务
     */
    private MuteService muteService() {
        return MuteService.getInstance();
    }

    /**
     * 初始化数据库存储
     */
    private void initializeSqlStorage() {
        if (databaseService != null && databaseService.isRunning()) {
            sqlStorage = new SqlSocialStateStorage(databaseService, plugin.getLogger());
            sqlStorage.ensureTables();

            // 注入到各个服务
            friendService.setSqlStorage(sqlStorage);
            guildService.setSqlStorage(sqlStorage);
            partyService.setSqlStorage(sqlStorage);

            plugin.getLogger().info("社交系统数据库存储已初始化");
        } else {
            plugin.getLogger().info("社交系统使用YAML存储（数据库不可用）");
        }
    }

    /**
     * 初始化导入导出服务
     */
    private void initializeImportExport() {
        importExportService = new SocialImportExportService(
            plugin, friendService, guildService, partyService
        );
    }

    /**
     * 初始化表情/动作系统
     */
    private void initializeEmoteSystem() {
        // 创建动画处理器并注入到服务
        emoteAnimationHandler = new EmoteAnimationHandler(emoteService);
        emoteService.setAnimationHandler(emoteAnimationHandler);
        // D-042: 统一冷却来源——让 EmoteCooldownManager 代理 EmoteState
        emoteCooldownManager.setEmoteService(emoteService);

        // 创建动作菜单 GUI
        emoteMenu = new EmoteMenu(emoteService, emoteCooldownManager, customEmoteManager);

        // 创建聊天监听器
        emoteChatListener = new EmoteChatListener(emoteService, emoteMenu);

        // 注册表情命令
        EmoteCommand emoteCommand = new EmoteCommand(emoteService, customEmoteManager, emoteCooldownManager);
        bind("emote", emoteCommand);
        bind("e", emoteCommand);

        // 注册表情监听器
        plugin.getServer().getPluginManager().registerEvents(emoteChatListener, plugin);

        // 注册动作菜单 GUI 监听器
        plugin.getServer().getPluginManager().registerEvents(new dev.starcore.starcore.social.emote.gui.EmoteMenuListener(emoteMenu, emoteService), plugin);

        // 启动定时清理任务
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            emoteService.update();
            emoteCooldownManager.cleanupExpiredCooldowns();
        }, 100L, 100L); // 每5秒清理一次

        // D-054: 自定义动作持久化（YAML 落盘 + 启动加载）
        java.io.File customEmoteFile = new java.io.File(plugin.getDataFolder(), "social/custom-emotes.yml");
        customEmoteManager.setOnChanged(() -> {
            try {
                org.bukkit.configuration.file.YamlConfiguration yml = new org.bukkit.configuration.file.YamlConfiguration();
                yml.set("customEmotes", customEmoteManager.serialize().get("customEmotes"));
                yml.save(customEmoteFile);
            } catch (java.io.IOException ex) {
                plugin.getLogger().warning("保存自定义动作失败: " + ex.getMessage());
            }
        });
        if (customEmoteFile.exists()) {
            try {
                org.bukkit.configuration.file.YamlConfiguration yml =
                    org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(customEmoteFile);
                Object list = yml.getList("customEmotes");
                if (list != null) {
                    customEmoteManager.deserialize(java.util.Map.of("customEmotes", list));
                }
            } catch (Exception ex) {
                plugin.getLogger().warning("加载自定义动作失败: " + ex.getMessage());
            }
        }

        plugin.getLogger().info("表情/动作系统已初始化 - " + emoteService.getAllEmotes().size() + " 个动作");
    }

    /**
     * 注册定时保存任务（每5分钟自动保存到数据库）
     */
    private void registerPeriodicSave() {
        if (sqlStorage != null && sqlStorage.isDatabaseAvailable()) {
            // 每5分钟保存一次
            Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
                try {
                    friendService.saveAllToDatabase();
                    guildService.saveGuilds();
                    partyService.saveData();
                } catch (Exception e) {
                    plugin.getLogger().warning("定时保存社交数据失败: " + e.getMessage());
                }
            }, 6000L, 6000L); // 5分钟后开始，每5分钟执行
        }
    }

    /**
     * 初始化监听器（用于命令集成）
     */
    private void initializeListeners() {
        if (socialMenuListener == null) {
            socialMenuListener = new SocialMenuListener(
                friendService, guildService, partyService, messageService
            );
        }
    }

    private void registerCommands() {
        // 创建 MessageCommand 实例并注入 SocialMenuListener
        MessageCommand messageCommand = new MessageCommand(messageService);
        messageCommand.setSocialMenuListener(socialMenuListener);

        bind("friend", new FriendCommand(friendService, messageServiceCore));
        bind("guild", new GuildCommand(guildService));
        bind("party", new PartyCommand(partyService));
        bind("msg", messageCommand);
        bind("tell", messageCommand);
        bind("whisper", messageCommand);
        bind("r", messageCommand);
        bind("reply", messageCommand);
        bind("socialgui", messageCommand);
        bind("social", new dev.starcore.starcore.social.command.SocialDataCommand(this));
    }

    private void registerListeners() {
        // socialMenuListener 已在 initializeListeners 中初始化
        plugin.getServer().getPluginManager().registerEvents(socialMenuListener, plugin);
    }

    /**
     * 注册社交系统事件
     */
    private void registerSocialEvents() {
        // 公会创建事件
        plugin.getServer().getPluginManager().registerEvents(
            new org.bukkit.event.Listener() {
                @org.bukkit.event.EventHandler
                public void onGuildCreate(GuildCreateEvent event) {
                    plugin.getLogger().info("公会创建事件: " + event.getGuildName());
                }
            }, plugin
        );

        // 好友请求事件
        plugin.getServer().getPluginManager().registerEvents(
            new org.bukkit.event.Listener() {
                @org.bukkit.event.EventHandler
                public void onFriendRequest(FriendRequestEvent event) {
                    plugin.getLogger().info("好友请求事件: " + event.getSenderName() + " -> " + event.getTargetName());
                }
            }, plugin
        );

        // 派对邀请事件
        plugin.getServer().getPluginManager().registerEvents(
            new org.bukkit.event.Listener() {
                @org.bukkit.event.EventHandler
                public void onPartyInvite(PartyInviteEvent event) {
                    plugin.getLogger().info("派对邀请事件: " + event.getInviterName() + " -> " + event.getTargetName());
                }
            }, plugin
        );

        // 公会邀请事件
        plugin.getServer().getPluginManager().registerEvents(
            new org.bukkit.event.Listener() {
                @org.bukkit.event.EventHandler
                public void onGuildInvite(GuildInviteEvent event) {
                    plugin.getLogger().info("公会邀请事件: " + event.getGuildName() + " -> " + event.getTargetName());
                }
            }, plugin
        );
    }

    private void bind(String name, CommandExecutor executor) {
        PluginCommand cmd = plugin.getServer().getPluginCommand(name);
        if (cmd != null) {
            cmd.setExecutor(executor);
            if (executor instanceof TabCompleter tc) cmd.setTabCompleter(tc);
        }
    }

    // ========== 好友持久化（双模式）==========

    private void loadFriends() {
        if (sqlStorage != null && sqlStorage.isDatabaseAvailable()) {
            // 从数据库加载
            friendService.loadFromDatabase();
            friendService.loadFriendRequestsFromDatabase();
            friendService.loadBlacklistFromDatabase();
            friendService.loadOnlineStatusFromDatabase();
            plugin.getLogger().info("已从数据库加载好友数据");
        } else {
            // 从YAML加载（兼容旧版本）
            loadFriendsFromYaml();
        }
    }

    private void loadFriendsFromYaml() {
        if (friendsFile == null || !friendsFile.exists()) return;
        try {
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(friendsFile);

            // 加载好友关系
            for (String key : yml.getKeys(false)) {
                UUID a;
                try { a = UUID.fromString(key); } catch (IllegalArgumentException ex) { continue; }
                for (String other : yml.getStringList(key + ".friends")) {
                    try {
                        friendService.loadFriendship(a, UUID.fromString(other));
                    } catch (IllegalArgumentException e) {
                        // 跳过无效的UUID
                    }
                }
                // 加载好友请求
                for (String other : yml.getStringList(key + ".requests")) {
                    try {
                        friendService.loadFriendRequest(a, UUID.fromString(other));
                    } catch (IllegalArgumentException e) {
                        // 跳过无效的UUID
                    }
                }
                // 加载黑名单
                for (String other : yml.getStringList(key + ".blacklist")) {
                    try {
                        friendService.loadBlacklistEntry(a, UUID.fromString(other));
                    } catch (IllegalArgumentException e) {
                        // 跳过无效的UUID
                    }
                }
                // 加载在线状态
                if (yml.contains(key + ".online")) {
                    friendService.loadOnlineStatus(a, yml.getBoolean(key + ".online", false));
                }
            }
            plugin.getLogger().info("已从YAML加载好友数据");
        } catch (Exception ex) {
            plugin.getLogger().warning("加载好友数据失败: " + ex.getMessage());
        }
    }

    private void saveFriends() {
        if (sqlStorage != null && sqlStorage.isDatabaseAvailable()) {
            // 保存到数据库
            friendService.saveAllToDatabase();
        } else {
            // 保存到YAML（兼容旧版本）
            saveFriendsToYaml();
        }
    }

    private void saveFriendsToYaml() {
        if (friendsFile == null) return;
        try {
            YamlConfiguration yml = new YamlConfiguration();

            // 收集所有涉及的玩家UUID（包括只有请求或黑名单的玩家）
            Set<UUID> allPlayers = new java.util.HashSet<>();
            allPlayers.addAll(friendService.exportFriendships().keySet());
            allPlayers.addAll(friendService.exportFriendRequests().keySet());
            allPlayers.addAll(friendService.exportBlacklist().keySet());
            allPlayers.addAll(friendService.getAllOnlineStatus().keySet());

            for (UUID playerId : allPlayers) {
                String path = playerId.toString();

                // 好友列表
                Set<UUID> friends = friendService.getFriends(playerId);
                if (!friends.isEmpty()) {
                    List<String> friendList = new java.util.ArrayList<>();
                    for (UUID f : friends) friendList.add(f.toString());
                    yml.set(path + ".friends", friendList);
                }

                // 好友请求
                Set<UUID> requests = friendService.getFriendRequests(playerId);
                if (!requests.isEmpty()) {
                    List<String> requestList = new java.util.ArrayList<>();
                    for (UUID r : requests) requestList.add(r.toString());
                    yml.set(path + ".requests", requestList);
                }

                // 黑名单
                Set<UUID> blocked = friendService.getBlacklist(playerId);
                if (!blocked.isEmpty()) {
                    List<String> blockList = new java.util.ArrayList<>();
                    for (UUID b : blocked) blockList.add(b.toString());
                    yml.set(path + ".blacklist", blockList);
                }

                // 在线状态
                yml.set(path + ".online", friendService.getOnlineStatus(playerId));
            }
            yml.save(friendsFile);
        } catch (Exception ex) {
            plugin.getLogger().warning("保存好友数据失败: " + ex.getMessage());
        }
    }

    // ========== 公会持久化（双模式）==========

    private void loadGuilds() {
        guildService.loadGuilds();
    }

    private void saveGuilds() {
        guildService.saveGuilds();
    }

    // ========== 派对持久化（双模式）==========

    private void loadParties() {
        partyService.loadData();
    }

    private void saveParties() {
        partyService.saveData();
    }

    /**
     * 发布好友请求事件
     */
    public void publishFriendRequest(UUID senderId, UUID targetId) {
        FriendRequestEvent event = new FriendRequestEvent(senderId, targetId);
        Bukkit.getPluginManager().callEvent(event);
        if (!event.isCancelled()) {
            messageService.sendMessage(senderId, targetId,
                "§a[星链] §f" + Bukkit.getOfflinePlayer(senderId).getName() + " 请求添加你为好友");
        }
    }

    /**
     * 发布公会创建事件
     */
    public void publishGuildCreate(org.bukkit.entity.Player creator, String name, String tag, dev.starcore.starcore.social.guild.Guild guild) {
        GuildCreateEvent event = new GuildCreateEvent(creator, name, tag, guild);
        Bukkit.getPluginManager().callEvent(event);
    }

    /**
     * 发布公会邀请事件
     */
    public void publishGuildInvite(UUID inviterId, UUID targetId, UUID guildId, String guildName) {
        GuildInviteEvent event = new GuildInviteEvent(inviterId, targetId, guildId, guildName);
        Bukkit.getPluginManager().callEvent(event);
    }

    /**
     * 发布派对邀请事件
     */
    public void publishPartyInvite(UUID inviterId, UUID targetId, dev.starcore.starcore.social.party.Party party) {
        PartyInviteEvent event = new PartyInviteEvent(inviterId, targetId, party);
        Bukkit.getPluginManager().callEvent(event);
    }
}
