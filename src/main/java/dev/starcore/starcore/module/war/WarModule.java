package dev.starcore.starcore.module.war;

import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.event.StarCoreEventBus;
import dev.starcore.starcore.core.module.ModuleLayer;
import dev.starcore.starcore.core.module.ModuleMetadata;
import dev.starcore.starcore.core.module.StarCoreModule;
import dev.starcore.starcore.foundation.animation.GuiAnimationManager;
import dev.starcore.starcore.foundation.animation.SoundFeedbackManager;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.diplomacy.DiplomacyRelation;
import dev.starcore.starcore.module.diplomacy.DiplomacyService;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.war.command.WarCommand;
import dev.starcore.starcore.module.war.gui.WarMenu;
import dev.starcore.starcore.module.war.gui.WarMenuListener;
import dev.starcore.starcore.module.war.gui.WarSituationMenu;
import dev.starcore.starcore.module.war.gui.WarSituationListener;
import dev.starcore.starcore.module.war.situation.WarSituationService;
import dev.starcore.starcore.module.war.listener.WarEventListener;
import dev.starcore.starcore.module.war.listener.WarTerritoryIntegrationListener;
import dev.starcore.starcore.war.War;
import dev.starcore.starcore.war.WarConfig;
import dev.starcore.starcore.war.WarGoal;
import dev.starcore.starcore.war.WarServiceImpl;
import dev.starcore.starcore.war.WarStateStorage;
import dev.starcore.starcore.war.BattlefieldService;
import dev.starcore.starcore.module.army.ArmyService;
import dev.starcore.starcore.foundation.territory.TerritoryService;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class WarModule implements StarCoreModule, WarService {
    private static final String FILE_NAME = "wars.properties";

    private static final ModuleMetadata METADATA = new ModuleMetadata(
        "war",
        "战争核心",
        ModuleLayer.MODULE,
        List.of("nation", "diplomacy"),
        List.of(WarService.class),
        "Owns active war declarations and the base wartime state machine."
    );

    private final ConcurrentMap<WarPairKey, Instant> activeWars = new ConcurrentHashMap<>();
    private dev.starcore.starcore.module.war.WarStateStorage moduleStateStorage;
    private WarStateStorage warStateStorage;
    private WarConfig warConfig;
    private DiplomacyService diplomacyService;
    private NationService nationService;
    private WarServiceImpl warServiceImpl;
    private StarCoreEventBus eventBus;
    private MessageService messages;
    private Plugin plugin;
    private WarTerritoryIntegrationListener territoryIntegrationListener;
    private TerritoryService territoryService;
    private WarMenu warMenu;
    private WarSituationMenu warSituationMenu;
    private WarSituationService warSituationService;
    private GuiAnimationManager animationManager;
    private SoundFeedbackManager soundManager;
    private BattlefieldService battlefieldService;
    private ArmyService armyService;

    @Override
    public ModuleMetadata metadata() {
        return METADATA;
    }

    @Override
    public void enable(StarCoreContext context) {
        context.persistenceService().ensureNamespace(metadata().id());

        // 保存插件引用
        this.plugin = context.plugin();

        // 模块级状态存储（用于简单的 WarPairKey 存储）
        this.moduleStateStorage = new DatabaseAwareWarStateStorage(
            metadata().id(),
            context.databaseService(),
            context.persistenceService(),
            context.plugin().getLogger()
        );

        // 完整战争状态存储（用于详细的 War 对象）
        this.warStateStorage = new WarStateStorage(context.plugin(), context.databaseService());
        this.warStateStorage.ensureDatabaseTable();

        this.diplomacyService = context.serviceRegistry().require(DiplomacyService.class);
        this.nationService = context.serviceRegistry().require(NationService.class);
        this.eventBus = context.eventBus();
        this.messages = context.serviceRegistry().require(MessageService.class);

        // 获取可选服务
        try {
            this.animationManager = context.serviceRegistry().find(GuiAnimationManager.class).orElse(null);
            this.soundManager = context.serviceRegistry().find(SoundFeedbackManager.class).orElse(null);
            this.battlefieldService = context.serviceRegistry().find(BattlefieldService.class).orElse(null);
            this.armyService = context.serviceRegistry().find(ArmyService.class).orElse(null);
        } catch (Exception e) {
            this.animationManager = null;
            this.soundManager = null;
            this.battlefieldService = null;
            this.armyService = null;
        }

        // 注册战争服务到 ServiceRegistry
        context.serviceRegistry().register(WarService.class, this);

        // 从配置文件加载战争配置
        this.warConfig = new WarConfig(context.plugin());

        // 从 WarConfig 转换到 WarServiceImpl.WarConfig
        WarServiceImpl.WarConfig serviceConfig = new WarServiceImpl.WarConfig(
            java.time.Duration.ofHours(warConfig.getPreparationHours()),
            java.time.Duration.ofHours(warConfig.getDeclarationCooldownHours()),
            java.time.Duration.ofDays(warConfig.getMaxWarDurationDays()),
            100 // minWarScore 默认值
        );
        this.warServiceImpl = new WarServiceImpl(
            context.plugin(),
            diplomacyService,
            warStateStorage,
            serviceConfig,
            eventBus
        );

        // 注册战争命令
        registerCommands(context);

        // 注册战争事件监听器
        registerEventListener(context);

        // 注册战争菜单 GUI（在事件监听器之后，确保 warMenu 已初始化）
        registerWarMenu(context);

        // 设置命令的 GUI 引用
        setCommandWarMenu();
        setCommandWarSituationMenu();

        loadState();
    }

    /**
     * 设置命令的 GUI 引用
     */
    private void setCommandWarMenu() {
        // 使用 Server 获取插件命令
        if (plugin instanceof org.bukkit.plugin.java.JavaPlugin javaPlugin) {
            org.bukkit.command.PluginCommand warCommand = javaPlugin.getCommand("war");
            if (warCommand != null && warCommand.getExecutor() instanceof WarCommand warCmd) {
                warCmd.setWarMenu(warMenu);
            }
        }
    }

    /**
     * 设置命令的战况菜单引用
     */
    private void setCommandWarSituationMenu() {
        if (plugin instanceof org.bukkit.plugin.java.JavaPlugin javaPlugin) {
            org.bukkit.command.PluginCommand warCommand = javaPlugin.getCommand("war");
            if (warCommand != null && warCommand.getExecutor() instanceof WarCommand warCmd) {
                warCmd.setWarSituationMenu(warSituationMenu);
            }
        }
    }

    @Override
    public void disable(StarCoreContext context) {
        flushState();
    }

    @Override
    public boolean declareWar(NationId left, NationId right) {
        if (left.equals(right)) {
            return false;
        }

        // 使用 WarServiceImpl 创建完整 War 对象（会检查冷却、创建 War 记录、发布事件）
        try {
            warServiceImpl.declareWar(left, right, WarGoal.TERRITORIAL);
        } catch (IllegalStateException e) {
            // 已经在战争中或冷却未结束
            return false;
        }

        // WarServiceImpl.declareWar 已保存 War 到 warStateStorage
        // 同时维护外交关系
        diplomacyService.setRelation(left, right, DiplomacyRelation.WAR);
        saveState();

        return true;
    }

    @Override
    public boolean endWar(NationId left, NationId right) {
        if (left.equals(right)) {
            return false;
        }

        // 获取完整的 War 对象用于清理盟友
        Optional<War> warOpt = warServiceImpl.findActiveWar(left, right);
        if (warOpt.isEmpty()) {
            return false;
        }
        War war = warOpt.get();

        // 使用 WarServiceImpl 结束完整 War 对象（会发布事件、清理数据）
        boolean warEnded = warServiceImpl.endWar(left, right);
        if (!warEnded) {
            return false;
        }

        // 清理外交关系
        WarPairKey key = WarPairKey.of(left, right);
        activeWars.remove(key);
        diplomacyService.setRelation(left, right, DiplomacyRelation.NEUTRAL);

        // 清理与盟友的关系
        for (NationId ally : war.aggressorAllies()) {
            if (!ally.equals(left) && !ally.equals(right)) {
                diplomacyService.setRelation(left, ally, DiplomacyRelation.NEUTRAL);
                diplomacyService.setRelation(right, ally, DiplomacyRelation.NEUTRAL);
            }
        }
        for (NationId ally : war.defenderAllies()) {
            if (!ally.equals(left) && !ally.equals(right)) {
                diplomacyService.setRelation(left, ally, DiplomacyRelation.NEUTRAL);
                diplomacyService.setRelation(right, ally, DiplomacyRelation.NEUTRAL);
            }
        }

        // 同步状态到 warStateStorage - WarServiceImpl.endWar 已更新内存中的 War 对象
        // 但需要确保外交关系变更也被持久化
        warStateStorage.saveWar(war);
        saveState();

        return true;
    }

    @Override
    public boolean atWar(NationId left, NationId right) {
        if (left.equals(right)) {
            return false;
        }
        // 委托给 WarServiceImpl 查询完整 War 对象
        return warServiceImpl.findActiveWar(left, right).isPresent();
    }

    @Override
    public Collection<WarSnapshot> activeWars() {
        // 委托给 WarServiceImpl 获取完整的 War 快照
        return warServiceImpl.activeWars();
    }

    @Override
    public Collection<WarSnapshot> activeWarsOf(NationId nationId) {
        // 委托给 WarServiceImpl 获取国家参与的战争快照
        return warServiceImpl.activeWarsOf(nationId);
    }

    @Override
    public Collection<WarSnapshot> warHistory(NationId nationId) {
        // 委托给 WarServiceImpl 获取国家参与的战争历史
        return warServiceImpl.warHistory(nationId);
    }

    @Override
    public String summary() {
        return warServiceImpl.summary();
    }

    /**
     * 获取战争服务实现（用于订阅战争事件）
     */
    public WarServiceImpl getWarServiceImpl() {
        return warServiceImpl;
    }

    /**
     * 获取战争与领地集成监听器
     */
    public WarTerritoryIntegrationListener getTerritoryIntegrationListener() {
        return territoryIntegrationListener;
    }

    /**
     * 发布事件到事件总线
     */
    private void publishEvent(Object event) {
        if (eventBus != null) {
            eventBus.publish(event);
        }
    }

    /**
     * 注册战争命令
     */
    private void registerCommands(StarCoreContext context) {
        PluginCommand warCommand = context.plugin().getCommand("war");
        if (warCommand == null) {
            context.plugin().getLogger().warning("Command 'war' not found in plugin.yml");
            return;
        }

        WarCommand commandExecutor = new WarCommand(
            this,                    // WarService (WarModule implements WarService)
            nationService,
            diplomacyService,
            messages
        );

        warCommand.setExecutor(commandExecutor);
        warCommand.setTabCompleter(commandExecutor);
        context.plugin().getLogger().info("War command registered: /war");
    }

    /**
     * 注册战争事件监听器
     */
    private void registerEventListener(StarCoreContext context) {
        // 基础战争事件监听器（广播消息）
        WarEventListener listener = new WarEventListener(nationService, messages);
        context.plugin().getServer().getPluginManager().registerEvents(listener, context.plugin());
        context.plugin().getLogger().info("War event listener registered.");

        // 战争与领地集成监听器
        this.territoryService = context.serviceRegistry().require(TerritoryService.class);
        this.territoryIntegrationListener = new WarTerritoryIntegrationListener(
            context.plugin(),
            this, // WarService
            nationService,
            territoryService,
            eventBus
        );
        context.plugin().getServer().getPluginManager().registerEvents(territoryIntegrationListener, context.plugin());
        context.plugin().getLogger().info("War-Territory integration listener registered.");

        // 注册战争菜单 GUI
        registerWarMenu(context);
    }

    /**
     * 注册战争菜单 GUI
     */
    private void registerWarMenu(StarCoreContext context) {
        this.warMenu = new WarMenu(
            this,                    // WarService (this class implements WarService)
            nationService,
            diplomacyService,
            animationManager,
            soundManager
        );

        // 注册菜单事件监听器
        WarMenuListener menuListener = new WarMenuListener(warMenu, warSituationMenu, nationService);
        context.plugin().getServer().getPluginManager().registerEvents(menuListener, context.plugin());
        context.plugin().getLogger().info("War menu GUI registered.");

        // 注册战况预览菜单
        registerWarSituationMenu(context);
    }

    /**
     * 注册战况预览菜单
     */
    private void registerWarSituationMenu(StarCoreContext context) {
        if (battlefieldService == null || armyService == null) {
            context.plugin().getLogger().warning("BattlefieldService or ArmyService not available, skipping WarSituationMenu");
            return;
        }

        // 创建战况服务
        this.warSituationService = new WarSituationService(
            context.plugin(),
            this,                     // WarService
            armyService,
            nationService,
            battlefieldService
        );

        // 创建战况菜单
        this.warSituationMenu = new WarSituationMenu(
            this,                     // WarService
            warSituationService,
            armyService,
            nationService,
            animationManager,
            soundManager
        );

        // 注册战况菜单事件监听器
        WarSituationListener situationListener = new WarSituationListener(
            warSituationMenu,
            this,                     // WarService
            armyService,
            nationService,
            warSituationService
        );
        context.plugin().getServer().getPluginManager().registerEvents(situationListener, context.plugin());
        context.plugin().getLogger().info("War situation menu GUI registered.");
    }

    /**
     * 获取战争菜单实例（供其他模块调用）
     */
    public WarMenu getWarMenu() {
        return warMenu;
    }

    /**
     * 获取战况预览菜单实例
     */
    public WarSituationMenu getWarSituationMenu() {
        return warSituationMenu;
    }

    /**
     * 获取战况服务实例
     */
    public WarSituationService getWarSituationService() {
        return warSituationService;
    }

    private Collection<WarSnapshot> snapshots(Map<WarPairKey, Instant> source) {
        return source.entrySet().stream()
            .sorted(Comparator.comparing(entry -> entry.getValue()))
            .map(entry -> new WarSnapshot(entry.getKey().left(), entry.getKey().right(), entry.getValue(), null))
            .toList();
    }

    private void saveState() {
        if (moduleStateStorage == null) {
            return;
        }
        moduleStateStorage.saveAsync(WarStateCodec.toProperties(activeWars));
    }

    private void flushState() {
        if (moduleStateStorage == null) {
            return;
        }
        moduleStateStorage.save(WarStateCodec.toProperties(activeWars));
    }

    private void loadState() {
        // WarServiceImpl 已在构造函数中从 warStateStorage 加载了完整 War 对象
        // 这里只需同步外交关系到 DiplomacyService
        for (dev.starcore.starcore.war.War war : warStateStorage.loadAllWars()) {
            if (war.isActive()) {
                // 确保外交关系正确
                diplomacyService.setRelation(war.aggressor(), war.defender(), DiplomacyRelation.WAR);
            }
        }

        // 同步简单状态到 moduleStateStorage（用于兼容旧格式）
        warServiceImpl.activeWars().forEach(snapshot -> {
            WarPairKey key = WarPairKey.of(snapshot.left(), snapshot.right());
            activeWars.put(key, snapshot.declaredAt());
        });
    }
}
