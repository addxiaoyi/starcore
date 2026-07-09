package dev.starcore.starcore.module.diplomacy;
import java.util.Optional;

import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.event.StarCoreEventBus;
import dev.starcore.starcore.core.module.ModuleLayer;
import dev.starcore.starcore.core.module.ModuleMetadata;
import dev.starcore.starcore.core.module.StarCoreModule;
import dev.starcore.starcore.core.persistence.PersistenceService;
import dev.starcore.starcore.foundation.economy.InternalEconomyService;
import dev.starcore.starcore.module.diplomacy.command.NationDiplomacyCommand;
import dev.starcore.starcore.module.diplomacy.event.AllianceBrokenEvent;
import dev.starcore.starcore.module.diplomacy.event.AllianceFormedEvent;
import dev.starcore.starcore.module.diplomacy.event.DiplomacyRelationChangedEvent;
import dev.starcore.starcore.module.diplomacy.gui.DiplomacyMenu;
import dev.starcore.starcore.module.diplomacy.gui.DiplomacyMenuListener;
import dev.starcore.starcore.module.diplomacy.gui.DiplomacyNetworkMenu;
import dev.starcore.starcore.module.diplomacy.gui.DiplomacyNetworkListener;
import dev.starcore.starcore.module.diplomacy.network.NetworkVisualizationService;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.nation.relation.NationRelationManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class DiplomacyModule implements StarCoreModule, DiplomacyService {
    private static final ModuleMetadata METADATA = new ModuleMetadata(
        "diplomacy",
        "外交核心",
        ModuleLayer.MODULE,
        List.of("nation", "resolution"),
        List.of(DiplomacyService.class),
        "Owns relations, treaties, sanctions, and international agreements."
    );

    // 外交冷却时间配置（毫秒）- 默认 24 小时
    private static final long DEFAULT_COOLDOWN_MS = 24 * 60 * 60 * 1000L;

    // 联盟邀请有效期（毫秒）- 默认 7 天
    private static final long ALLIANCE_INVITE_EXPIRY_MS = 7 * 24 * 60 * 60 * 1000L;

    // 宣战费用配置
    private static final BigDecimal DEFAULT_WAR_DECLARATION_COST = new BigDecimal("1000.00");

    private final Map<DiplomacyPairKey, DiplomacyRelation> relations = new ConcurrentHashMap<>();
    private final Map<DiplomacyPairKey, WarRecord> warRecords = new ConcurrentHashMap<>();
    // 记录关系变更时间，用于冷却验证
    private final Map<DiplomacyPairKey, Instant> relationChangeTimestamps = new ConcurrentHashMap<>();
    // 联盟邀请记录: key = (inviter, invited), value = 邀请时间
    private final Map<DiplomacyPairKey, Instant> allianceInvitations = new ConcurrentHashMap<>();

    private NationService nationService;
    private DiplomacyStateStorage stateStorage;
    private StarCoreEventBus eventBus;
    private InternalEconomyService economyService;
    private long allianceCooldownMs = DEFAULT_COOLDOWN_MS;
    private long warCooldownMs = DEFAULT_COOLDOWN_MS;
    private BigDecimal warDeclarationCost = DEFAULT_WAR_DECLARATION_COST;
    private NationRelationManager relationManager;
    private JavaPlugin plugin;

    @Override
    public ModuleMetadata metadata() {
        return METADATA;
    }

    @Override
    public void enable(StarCoreContext context) {
        this.plugin = context.plugin();
        context.persistenceService().ensureNamespace(metadata().id());
        this.nationService = context.serviceRegistry().require(NationService.class);
        this.stateStorage = new DatabaseAwareDiplomacyStateStorage(
            metadata().id(),
            context.databaseService(),
            context.persistenceService(),
            context.plugin().getLogger()
        );
        this.eventBus = context.eventBus();

        // 初始化经济服务（用于宣战费用检查）
        try {
            this.economyService = context.serviceRegistry().find(InternalEconomyService.class).orElse(null);
        } catch (Exception e) {
            this.economyService = null;
        }

        // 初始化关系管理器并设置同步
        this.relationManager = new NationRelationManager();
        this.relationManager.setDiplomacyService(this);
        this.relationManager.setEventBus(eventBus);

        // 从配置读取冷却时间和费用配置
        loadCooldownConfig(context);
        loadState();

        // 注册外交服务到 ServiceRegistry（供其他模块和 API 使用）
        context.serviceRegistry().register(DiplomacyService.class, this);

        // 注册外交菜单监听器
        DiplomacyMenu diplomacyMenu = new DiplomacyMenu(this, nationService, null);

        // 尝试获取并连接子模块的菜单
        try {
            // 获取 AllianceService
            var allianceServiceOpt = context.serviceRegistry().find(dev.starcore.starcore.module.diplomacy.alliance.AllianceService.class);
            allianceServiceOpt.ifPresent(allianceService -> {
                dev.starcore.starcore.module.diplomacy.alliance.gui.AllianceMenu allianceMenu =
                    new dev.starcore.starcore.module.diplomacy.alliance.gui.AllianceMenu(
                        allianceService, nationService, null
                    );
                diplomacyMenu.setAllianceMenu(allianceMenu);
            });

            // 获取 MilitaryAllianceService
            var militaryServiceOpt = context.serviceRegistry().find(dev.starcore.starcore.module.diplomacy.military.MilitaryAllianceService.class);
            militaryServiceOpt.ifPresent(militaryService -> {
                dev.starcore.starcore.module.diplomacy.military.gui.MilitaryAllianceMenu militaryMenu =
                    new dev.starcore.starcore.module.diplomacy.military.gui.MilitaryAllianceMenu(
                        militaryService, nationService
                    );
                diplomacyMenu.setMilitaryMenu(militaryMenu);
            });

            // 初始化并连接关系网络可视化菜单
            allianceServiceOpt.ifPresent(allianceService -> {
                NetworkVisualizationService networkService = new NetworkVisualizationService(
                    nationService, this, allianceService
                );
                DiplomacyNetworkMenu networkMenu = new DiplomacyNetworkMenu(networkService, nationService);
                diplomacyMenu.setNetworkMenu(networkMenu);

                // 注册网络菜单监听器
                context.plugin().getServer().getPluginManager().registerEvents(
                    new DiplomacyNetworkListener(networkMenu, networkService, nationService),
                    context.plugin()
                );
            });
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to connect sub-module menus: " + e.getMessage());
        }

        context.plugin().getServer().getPluginManager().registerEvents(
            new DiplomacyMenuListener(diplomacyMenu), context.plugin()
        );

        // 注册外交命令
        registerCommands(context);
    }

    /**
     * 注册外交命令
     */
    private void registerCommands(StarCoreContext context) {
        if (plugin == null) {
            return;
        }

        var command = plugin.getCommand("diplomacy");
        if (command != null) {
            var diplomacyCommand = new NationDiplomacyCommand(
                relationManager,
                nationService,
                context.serviceRegistry().require(dev.starcore.starcore.foundation.player.OnlinePlayerDirectory.class),
                this
            );
            command.setExecutor(diplomacyCommand);
            command.setTabCompleter(diplomacyCommand);
            plugin.getLogger().info("Diplomacy command registered.");
        } else {
            plugin.getLogger().warning("Command 'diplomacy' not found in plugin.yml");
        }
    }

    /**
     * 获取关系管理器（供外部使用）
     */
    public NationRelationManager getRelationManager() {
        return relationManager;
    }

    @Override
    public void disable(StarCoreContext context) {
        flushState();
    }

    @Override
    public DiplomacyRelation relationBetween(NationId left, NationId right) {
        if (left.equals(right)) {
            return DiplomacyRelation.ALLIED;
        }
        return relations.getOrDefault(DiplomacyPairKey.of(left, right), DiplomacyRelation.NEUTRAL);
    }

    @Override
    public DiplomacyRelation setRelation(NationId left, NationId right, DiplomacyRelation relation) {
        if (left.equals(right)) {
            return DiplomacyRelation.ALLIED;
        }

        // 冷却时间验证（仅对联盟和战争生效）
        if (relation == DiplomacyRelation.ALLIED || relation == DiplomacyRelation.WAR) {
            if (isInCooldown(left, right)) {
                throw new IllegalStateException("Diplomatic action is in cooldown period");
            }
        }

        DiplomacyPairKey key = DiplomacyPairKey.of(left, right);
        DiplomacyRelation previousRelation = relations.getOrDefault(key, DiplomacyRelation.NEUTRAL);

        if (relation == DiplomacyRelation.NEUTRAL) {
            // 发布联盟破裂事件
            if (previousRelation == DiplomacyRelation.ALLIED) {
                publishEvent(new AllianceBrokenEvent(left, right, AllianceBrokenEvent.AllianceBreakReason.UNILATERAL_BREAK));
            }
            // 保存 NEUTRAL 关系到数据库，以便持久化冷却时间等信息
            relations.put(key, DiplomacyRelation.NEUTRAL);
            warRecords.remove(key);
            relationChangeTimestamps.put(key, Instant.now());
            saveState();
            return DiplomacyRelation.NEUTRAL;
        }

        relations.put(key, relation);
        relationChangeTimestamps.put(key, Instant.now());

        // 发布关系变更事件
        publishRelationChangedEvent(left, right, previousRelation, relation);

        saveState();
        return relation;
    }

    /**
     * 检查外交行动是否在冷却期内
     */
    public boolean isInCooldown(NationId left, NationId right) {
        DiplomacyPairKey key = DiplomacyPairKey.of(left, right);
        Instant lastChange = relationChangeTimestamps.get(key);
        if (lastChange == null) {
            return false;
        }
        return Duration.between(lastChange, Instant.now()).toMillis() < allianceCooldownMs;
    }

    /**
     * 获取剩余冷却时间（毫秒）
     */
    public long getRemainingCooldownMs(NationId left, NationId right) {
        DiplomacyPairKey key = DiplomacyPairKey.of(left, right);
        Instant lastChange = relationChangeTimestamps.get(key);
        if (lastChange == null) {
            return 0;
        }
        long elapsed = Duration.between(lastChange, Instant.now()).toMillis();
        return Math.max(0, allianceCooldownMs - elapsed);
    }

    /**
     * 设置联盟冷却时间
     */
    public void setAllianceCooldown(Duration cooldown) {
        this.allianceCooldownMs = cooldown.toMillis();
    }

    /**
     * 设置战争冷却时间
     */
    public void setWarCooldown(Duration cooldown) {
        this.warCooldownMs = cooldown.toMillis();
    }

    /**
     * 发布关系变更事件，并派生出联盟相关事件
     */
    private void publishRelationChangedEvent(NationId left, NationId right, DiplomacyRelation previous, DiplomacyRelation current) {
        // 发布基础关系变更事件
        DiplomacyRelationChangedEvent event = new DiplomacyRelationChangedEvent(left, right, previous, current);
        publishEvent(event);

        // 如果关系变为联盟，发布联盟建立事件
        if (current == DiplomacyRelation.ALLIED && previous != DiplomacyRelation.ALLIED) {
            publishEvent(new AllianceFormedEvent(left, right));
        }

        // 如果之前是联盟现在不是，发布联盟破裂事件
        if (previous == DiplomacyRelation.ALLIED && current != DiplomacyRelation.ALLIED) {
            AllianceBrokenEvent.AllianceBreakReason reason = (current == DiplomacyRelation.WAR)
                ? AllianceBrokenEvent.AllianceBreakReason.WAR_DECLARED
                : AllianceBrokenEvent.AllianceBreakReason.UNILATERAL_BREAK;
            publishEvent(new AllianceBrokenEvent(left, right, reason));
        }
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
     * 从配置加载冷却时间和费用设置
     */
    private void loadCooldownConfig(StarCoreContext context) {
        try {
            var config = context.plugin().getConfig();
            this.allianceCooldownMs = config.getLong("diplomacy.cooldown.alliance-ms", DEFAULT_COOLDOWN_MS);
            this.warCooldownMs = config.getLong("diplomacy.cooldown.war-ms", DEFAULT_COOLDOWN_MS);
            // 加载宣战费用配置
            String costStr = config.getString("diplomacy.war.declaration-cost", "1000.00");
            try {
                this.warDeclarationCost = new BigDecimal(costStr);
            } catch (NumberFormatException e) {
                this.warDeclarationCost = DEFAULT_WAR_DECLARATION_COST;
            }
        } catch (Exception e) {
            // 配置读取失败，使用默认值
            this.allianceCooldownMs = DEFAULT_COOLDOWN_MS;
            this.warCooldownMs = DEFAULT_COOLDOWN_MS;
            this.warDeclarationCost = DEFAULT_WAR_DECLARATION_COST;
        }
    }

    @Override
    public Collection<DiplomacyRelationSnapshot> relationsOf(NationId nationId) {
        return relations.entrySet().stream()
            .filter(entry -> entry.getKey().contains(nationId))
            .map(entry -> new DiplomacyRelationSnapshot(nationId, entry.getKey().other(nationId), entry.getValue()))
            .toList();
    }

    @Override
    public Optional<DiplomacyRelationSnapshot> relationSnapshot(NationId left, NationId right) {
        if (left.equals(right)) {
            return Optional.of(new DiplomacyRelationSnapshot(left, right, DiplomacyRelation.ALLIED));
        }
        DiplomacyRelation relation = relations.get(DiplomacyPairKey.of(left, right));
        if (relation == null) {
            return Optional.empty();
        }
        return Optional.of(new DiplomacyRelationSnapshot(left, right, relation));
    }

    @Override
    public Optional<WarRecord> getWarRecord(NationId left, NationId right) {
        if (left.equals(right)) {
            return Optional.empty();
        }
        return Optional.ofNullable(warRecords.get(DiplomacyPairKey.of(left, right)));
    }

    @Override
    public Collection<WarRecord> activeWarsOf(NationId nationId) {
        return warRecords.values().stream()
            .filter(wr -> wr.declarer().equals(nationId) || wr.target().equals(nationId))
            .toList();
    }

    @Override
    public WarRecord declareWar(NationId declarer, NationId target) {
        if (declarer.equals(target)) {
            throw new IllegalArgumentException("Cannot declare war on yourself");
        }

        // 检查冷却时间
        if (isInCooldown(declarer, target)) {
            throw new IllegalStateException("War declaration is in cooldown period");
        }

        // 检查宣战费用
        if (economyService != null) {
            BigDecimal balance = economyService.balance(declarer.value());
            if (balance.compareTo(warDeclarationCost) < 0) {
                throw new IllegalStateException("Insufficient funds for war declaration. Required: " + warDeclarationCost + ", Available: " + balance);
            }
            // 扣除宣战费用
            economyService.withdraw(declarer.value(), warDeclarationCost, true);
        }

        DiplomacyPairKey key = DiplomacyPairKey.of(declarer, target);
        WarRecord record = new WarRecord(declarer, target, Instant.now());
        warRecords.put(key, record);
        setRelation(declarer, target, DiplomacyRelation.WAR);
        return record;
    }

    /**
     * 获取宣战费用
     */
    public BigDecimal getWarDeclarationCost() {
        return warDeclarationCost;
    }

    /**
     * 设置宣战费用
     */
    public void setWarDeclarationCost(BigDecimal cost) {
        this.warDeclarationCost = cost;
    }

    @Override
    public boolean endWar(NationId left, NationId right) {
        if (left.equals(right)) {
            return false;
        }
        DiplomacyPairKey key = DiplomacyPairKey.of(left, right);
        // 检查是否存在真实的战争记录
        if (!warRecords.containsKey(key)) {
            return false;
        }
        warRecords.remove(key);
        setRelation(left, right, DiplomacyRelation.CEASE_FIRE);
        return true;
    }

    // ==================== 联盟邀请系统 ====================

    @Override
    public AllianceInviteResult sendAllianceInvite(NationId inviter, NationId invited) {
        if (inviter.equals(invited)) {
            return new AllianceInviteResult(false, "不能邀请自己的国家");
        }

        // 检查是否已有关系
        DiplomacyRelation currentRelation = relationBetween(inviter, invited);
        if (currentRelation == DiplomacyRelation.ALLIED) {
            return new AllianceInviteResult(false, "已经是联盟关系");
        }
        if (currentRelation == DiplomacyRelation.WAR) {
            return new AllianceInviteResult(false, "无法与敌对国家建立联盟");
        }

        // 检查冷却时间
        if (isInCooldown(inviter, invited)) {
            long remaining = getRemainingCooldownMs(inviter, invited);
            long hours = remaining / (60 * 60 * 1000);
            long minutes = (remaining % (60 * 60 * 1000)) / (60 * 1000);
            return new AllianceInviteResult(false, "外交冷却中，还需 " + hours + "小时" + minutes + "分钟");
        }

        // 检查是否已有未过期的邀请
        DiplomacyPairKey key = DiplomacyPairKey.of(inviter, invited);
        Instant existingInvite = allianceInvitations.get(key);
        if (existingInvite != null && !isInviteExpired(existingInvite)) {
            long remaining = getInviteRemainingMs(existingInvite);
            long hours = remaining / (60 * 60 * 1000);
            return new AllianceInviteResult(false, "已有未处理的邀请，还剩 " + hours + "小时");
        }

        // 创建邀请
        allianceInvitations.put(key, Instant.now());
        return new AllianceInviteResult(true, "联盟邀请已发送，等待对方接受");
    }

    @Override
    public AllianceResult acceptAllianceInvite(NationId acceptor, NationId inviter) {
        if (acceptor.equals(inviter)) {
            return new AllianceResult(false, "无效的操作");
        }

        DiplomacyPairKey key = DiplomacyPairKey.of(inviter, acceptor);
        Instant inviteTime = allianceInvitations.get(key);

        if (inviteTime == null) {
            return new AllianceResult(false, "没有收到来自该国家的联盟邀请");
        }

        if (isInviteExpired(inviteTime)) {
            allianceInvitations.remove(key);
            return new AllianceResult(false, "联盟邀请已过期");
        }

        // 检查是否已是敌对
        if (relationBetween(acceptor, inviter) == DiplomacyRelation.WAR) {
            allianceInvitations.remove(key);
            return new AllianceResult(false, "无法与敌对国家建立联盟");
        }

        // 移除邀请记录
        allianceInvitations.remove(key);

        // 建立联盟
        return createAllianceInternal(inviter, acceptor);
    }

    @Override
    public void rejectAllianceInvite(NationId rejector, NationId inviter) {
        DiplomacyPairKey key = DiplomacyPairKey.of(inviter, rejector);
        allianceInvitations.remove(key);
    }

    @Override
    public void cancelAllianceInvite(NationId canceller, NationId invited) {
        DiplomacyPairKey key = DiplomacyPairKey.of(canceller, invited);
        allianceInvitations.remove(key);
    }

    @Override
    public boolean hasPendingInvite(NationId nationId, NationId fromInviter) {
        if (fromInviter != null) {
            DiplomacyPairKey key = DiplomacyPairKey.of(fromInviter, nationId);
            Instant inviteTime = allianceInvitations.get(key);
            return inviteTime != null && !isInviteExpired(inviteTime);
        }
        // 检查所有发给该国家的邀请
        for (Map.Entry<DiplomacyPairKey, Instant> entry : allianceInvitations.entrySet()) {
            if (entry.getKey().right().equals(nationId) && !isInviteExpired(entry.getValue())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public List<NationId> getPendingInvites(NationId nationId) {
        List<NationId> invites = new ArrayList<>();
        for (Map.Entry<DiplomacyPairKey, Instant> entry : allianceInvitations.entrySet()) {
            if (entry.getKey().right().equals(nationId) && !isInviteExpired(entry.getValue())) {
                invites.add(entry.getKey().left());
            }
        }
        return invites;
    }

    /**
     * 内部方法：创建联盟
     */
    private AllianceResult createAllianceInternal(NationId nation1, NationId nation2) {
        // 冷却时间验证
        if (isInCooldown(nation1, nation2)) {
            long remaining = getRemainingCooldownMs(nation1, nation2);
            long hours = remaining / (60 * 60 * 1000);
            long minutes = (remaining % (60 * 60 * 1000)) / (60 * 1000);
            return new AllianceResult(false, "外交冷却中，还需 " + hours + "小时" + minutes + "分钟");
        }

        // 设置联盟关系
        setRelation(nation1, nation2, DiplomacyRelation.ALLIED);

        // 发布联盟建立事件
        publishEvent(new AllianceFormedEvent(nation1, nation2));

        return new AllianceResult(true, "联盟建立成功");
    }

    /**
     * 检查邀请是否过期
     */
    private boolean isInviteExpired(Instant inviteTime) {
        return Duration.between(inviteTime, Instant.now()).toMillis() >= ALLIANCE_INVITE_EXPIRY_MS;
    }

    /**
     * 获取邀请剩余时间（毫秒）
     */
    private long getInviteRemainingMs(Instant inviteTime) {
        long elapsed = Duration.between(inviteTime, Instant.now()).toMillis();
        return Math.max(0, ALLIANCE_INVITE_EXPIRY_MS - elapsed);
    }

    @Override
    public String summary() {
        return nationService.nations().size() + " nation(s), " + relations.size() + " diplomatic relation pair(s), " + warRecords.size() + " active war(s)";
    }

    private void saveState() {
        if (stateStorage == null) {
            return;
        }
        stateStorage.saveAsync(DiplomacyStateCodec.toProperties(relations));
        stateStorage.saveAsync(DiplomacyWarCodec.toProperties(warRecords));
    }

    private void flushState() {
        if (stateStorage == null) {
            return;
        }
        stateStorage.save(DiplomacyStateCodec.toProperties(relations));
        stateStorage.save(DiplomacyWarCodec.toProperties(warRecords));
    }

    private void loadState() {
        Properties properties = stateStorage == null ? new Properties() : stateStorage.load();
        relations.clear();
        relations.putAll(DiplomacyStateCodec.fromProperties(properties));
        warRecords.clear();
        warRecords.putAll(DiplomacyWarCodec.fromProperties(properties));
    }
}
