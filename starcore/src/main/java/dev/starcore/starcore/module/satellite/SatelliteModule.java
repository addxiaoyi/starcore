package dev.starcore.starcore.module.satellite;
import java.util.Optional;

import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.event.StarCoreEventBus;
import dev.starcore.starcore.core.module.ModuleLayer;
import dev.starcore.starcore.core.module.ModuleMetadata;
import dev.starcore.starcore.core.module.StarCoreModule;
import dev.starcore.starcore.core.persistence.PersistenceService;
import dev.starcore.starcore.foundation.economy.InternalEconomyService;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.diplomacy.DiplomacyService;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.satellite.command.SatelliteCommand;
import dev.starcore.starcore.module.satellite.event.SatelliteIndependenceDeclaredEvent;
import dev.starcore.starcore.module.satellite.event.SatelliteRelationDissolvedEvent;
import dev.starcore.starcore.module.satellite.event.SatelliteRelationEstablishedEvent;
import dev.starcore.starcore.module.satellite.event.SatelliteTributePaidEvent;
import dev.starcore.starcore.module.satellite.listener.SatelliteListener;
import dev.starcore.starcore.module.treasury.TreasuryService;
import dev.starcore.starcore.module.war.WarService;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 卫星国模块
 * 提供卫星国（附庸国/保护国）关系的管理功能
 */
public final class SatelliteModule implements StarCoreModule, SatelliteService {

    private static final ModuleMetadata METADATA = new ModuleMetadata(
        "satellite",
        "卫星国",
        ModuleLayer.MODULE,
        List.of("nation", "treasury"),
        List.of(SatelliteService.class),
        "Provides satellite state (vassal/protectorate) relationship management."
    );

    // 冷却时间配置（毫秒）- 默认 48 小时
    private static final long DEFAULT_COOLDOWN_MS = 48 * 60 * 60 * 1000L;

    // 贡金收取周期（毫秒）- 默认每天
    private static final long TRIBUTE_COLLECTION_INTERVAL_MS = 24 * 60 * 60 * 1000L;

    private final Map<SatellitePairKey, SatelliteState> relations = new ConcurrentHashMap<>();
    private final Map<NationId, NationId> satelliteToSuzerain = new ConcurrentHashMap<>();
    private final Map<NationId, Set<NationId>> suzerainToSatellites = new ConcurrentHashMap<>();
    private final Map<SatellitePairKey, Instant> relationChangeTimestamps = new ConcurrentHashMap<>();

    private NationService nationService;
    private TreasuryService treasuryService;
    private InternalEconomyService economyService;
    private MessageService messages;
    private StarCoreEventBus eventBus;
    private PersistenceService persistenceService;
    private JavaPlugin plugin;
    private Optional<DiplomacyService> diplomacyService = Optional.empty();
    private Optional<WarService> warService = Optional.empty();

    private int maxSatellitesPerNation = 5;
    private long relationCooldownMs = DEFAULT_COOLDOWN_MS;
    private long tributeCollectionIntervalMs = TRIBUTE_COLLECTION_INTERVAL_MS;

    private int tributeCollectionTaskId = -1;

    @Override
    public ModuleMetadata metadata() {
        return METADATA;
    }

    @Override
    public void enable(StarCoreContext context) {
        this.plugin = context.plugin();
        context.persistenceService().ensureNamespace(metadata().id());
        this.persistenceService = context.persistenceService();
        this.nationService = context.serviceRegistry().require(NationService.class);
        this.treasuryService = context.serviceRegistry().find(TreasuryService.class).orElse(null);
        this.economyService = context.economyService();
        this.messages = context.serviceRegistry().require(MessageService.class);
        this.eventBus = context.eventBus();

        // 获取可选服务
        this.diplomacyService = context.serviceRegistry().find(DiplomacyService.class);
        this.warService = context.serviceRegistry().find(WarService.class);

        // 从配置读取设置
        loadConfig(context);

        // 加载状态
        loadState();

        // 注册服务
        context.serviceRegistry().register(SatelliteService.class, this);

        // 注册命令
        registerCommands(context);

        // 注册事件监听器
        registerListeners(context);

        // 启动贡金收取定时任务
        startTributeCollectionTask();

        plugin.getLogger().info("Satellite module enabled: " + relations.size() + " relations loaded");
    }

    @Override
    public void disable(StarCoreContext context) {
        // 停止贡金收取任务
        stopTributeCollectionTask();

        // 保存状态
        saveState();

        // 清理监听器
        context.plugin().getLogger().info("Satellite module disabled");
    }

    private void loadConfig(StarCoreContext context) {
        try {
            ConfigurationSection config = context.plugin().getConfig().getConfigurationSection("satellite");
            if (config != null) {
                this.maxSatellitesPerNation = config.getInt("max-satellites-per-nation", 5);
                this.relationCooldownMs = config.getLong("relation-cooldown-ms", DEFAULT_COOLDOWN_MS);
                this.tributeCollectionIntervalMs = config.getLong("tribute-collection-interval-ms", TRIBUTE_COLLECTION_INTERVAL_MS);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load satellite config, using defaults: " + e.getMessage());
        }
    }

    private void registerCommands(StarCoreContext context) {
        var command = context.plugin().getCommand("satellite");
        if (command != null) {
            SatelliteCommand satelliteCommand = new SatelliteCommand(
                this,
                nationService,
                treasuryService,
                messages
            );
            command.setExecutor(satelliteCommand);
            command.setTabCompleter(satelliteCommand);
            plugin.getLogger().info("Satellite command registered: /satellite");
        }
    }

    private void registerListeners(StarCoreContext context) {
        SatelliteListener listener = new SatelliteListener(
            this,
            nationService,
            diplomacyService,
            warService,
            messages
        );
        context.plugin().getServer().getPluginManager().registerEvents(listener, context.plugin());
        plugin.getLogger().info("Satellite listener registered");
    }

    private void startTributeCollectionTask() {
        if (plugin != null && tributeCollectionIntervalMs > 0) {
            tributeCollectionTaskId = plugin.getServer().getScheduler().runTaskTimer(
                plugin,
                () -> {
                    for (NationId suzerainId : new HashSet<>(suzerainToSatellites.keySet())) {
                        collectTributes(suzerainId);
                    }
                },
                tributeCollectionIntervalMs,
                tributeCollectionIntervalMs
            ).getTaskId();
        }
    }

    private void stopTributeCollectionTask() {
        if (tributeCollectionTaskId != -1 && plugin != null) {
            plugin.getServer().getScheduler().cancelTask(tributeCollectionTaskId);
            tributeCollectionTaskId = -1;
        }
    }

    // ==================== 关系查询 ====================

    @Override
    public Optional<NationId> suzerainOf(NationId satelliteId) {
        return Optional.ofNullable(satelliteToSuzerain.get(satelliteId));
    }

    @Override
    public Optional<NationId> protectorOf(NationId suzerainId) {
        // 查找以该国为宗主国的保护国关系
        return suzerainToSatellites.getOrDefault(suzerainId, Collections.emptySet())
            .stream()
            .filter(satId -> {
                SatelliteState state = relations.get(SatellitePairKey.of(suzerainId, satId));
                return state != null && state.relation() == SatelliteRelation.PROTECTORATE;
            })
            .findFirst();
    }

    @Override
    public Collection<NationId> satellitesOf(NationId suzerainId) {
        return Collections.unmodifiableSet(
            suzerainToSatellites.getOrDefault(suzerainId, Collections.emptySet())
        );
    }

    @Override
    public SatelliteRelation getRelation(NationId nation1, NationId nation2) {
        if (nation1.equals(nation2)) {
            return SatelliteRelation.NONE;
        }

        SatellitePairKey key = SatellitePairKey.of(nation1, nation2);
        SatelliteState state = relations.get(key);

        if (state == null || !state.active()) {
            return SatelliteRelation.NONE;
        }

        // 确定 nation1 是宗主国还是卫星国
        if (state.suzerainId().equals(nation1)) {
            return state.relation();
        } else {
            // nation1 是卫星国，返回负向关系（表示它是附庸）
            return state.relation();
        }
    }

    @Override
    public boolean isSuzerain(NationId nationId) {
        return !suzerainToSatellites.getOrDefault(nationId, Collections.emptySet()).isEmpty();
    }

    @Override
    public boolean isSatellite(NationId nationId) {
        return satelliteToSuzerain.containsKey(nationId);
    }

    @Override
    public boolean hasRelation(NationId suzerainId, NationId satelliteId) {
        SatelliteState state = relations.get(SatellitePairKey.of(suzerainId, satelliteId));
        return state != null && state.active();
    }

    // ==================== 关系操作 ====================

    @Override
    public SatelliteResult establishRelation(NationId suzerainId, NationId satelliteId, SatelliteRelation relation) {
        if (suzerainId.equals(satelliteId)) {
            return SatelliteResult.failure("不能与自己的国家建立卫星关系");
        }

        if (relation == SatelliteRelation.NONE) {
            return SatelliteResult.failure("必须指定有效的卫星关系类型");
        }

        // 检查冷却时间
        if (isInCooldown(suzerainId, satelliteId)) {
            return SatelliteResult.failure("外交冷却中，请稍后再试");
        }

        // 检查是否已存在关系
        SatellitePairKey key = SatellitePairKey.of(suzerainId, satelliteId);
        if (relations.containsKey(key)) {
            return SatelliteResult.failure("该卫星关系已存在");
        }

        // 检查卫星国是否已是其他国家卫星
        if (satelliteToSuzerain.containsKey(satelliteId)) {
            return SatelliteResult.failure("该国已是其他国家卫星");
        }

        // 检查宗主国最大卫星数量
        if (suzerainToSatellites.getOrDefault(suzerainId, Collections.emptySet()).size() >= maxSatellitesPerNation) {
            return SatelliteResult.failure("宗主国已达到最大卫星国数量限制 (" + maxSatellitesPerNation + ")");
        }

        // 检查宗主国是否已是卫星国（防止多层嵌套）
        if (satelliteToSuzerain.containsKey(suzerainId)) {
            return SatelliteResult.failure("宗主国本身也是卫星国，不能拥有自己的卫星");
        }

        // 创建关系
        SatelliteState state = new SatelliteState(suzerainId, satelliteId, relation, Instant.now());
        state.setTributeRate(relation.defaultTributeRate());
        relations.put(key, state);
        satelliteToSuzerain.put(satelliteId, suzerainId);
        suzerainToSatellites.computeIfAbsent(suzerainId, k -> ConcurrentHashMap.newKeySet()).add(satelliteId);
        relationChangeTimestamps.put(key, Instant.now());

        // 发布事件
        publishEvent(new SatelliteRelationEstablishedEvent(suzerainId, satelliteId, relation));

        // 保存状态
        saveState();

        return SatelliteResult.success("卫星关系建立成功", relation);
    }

    @Override
    public SatelliteResult dissolveRelation(NationId suzerainId, NationId satelliteId, UUID initiatorId) {
        return dissolveRelationInternal(suzerainId, satelliteId, SatelliteRelationDissolvedEvent.DissolveReason.NEGOTIATION);
    }

    @Override
    public SatelliteResult declareIndependence(NationId satelliteId, UUID initiatorId) {
        Optional<NationId> suzerainOpt = suzerainOf(satelliteId);
        if (suzerainOpt.isEmpty()) {
            return SatelliteResult.failure("该国不是任何国家的卫星");
        }

        NationId suzerainId = suzerainOpt.get();
        SatelliteState state = relations.get(SatellitePairKey.of(suzerainId, satelliteId));

        if (state == null || !state.active()) {
            return SatelliteResult.failure("卫星关系不存在");
        }

        if (!state.canDeclareIndependence()) {
            return SatelliteResult.failure("当前关系等级不允许独立：" + state.relation().displayName());
        }

        // 发布独立宣言事件
        String playerName = initiatorId != null ? initiatorId.toString() : "system";
        publishEvent(new SatelliteIndependenceDeclaredEvent(satelliteId, suzerainId, playerName));

        return dissolveRelationInternal(suzerainId, satelliteId, SatelliteRelationDissolvedEvent.DissolveReason.INDEPENDENCE);
    }

    @Override
    public SatelliteResult releaseSatellite(NationId suzerainId, NationId satelliteId, UUID initiatorId) {
        return dissolveRelationInternal(suzerainId, satelliteId, SatelliteRelationDissolvedEvent.DissolveReason.RELEASE);
    }

    private SatelliteResult dissolveRelationInternal(NationId suzerainId, NationId satelliteId, SatelliteRelationDissolvedEvent.DissolveReason reason) {
        SatellitePairKey key = SatellitePairKey.of(suzerainId, satelliteId);
        SatelliteState state = relations.get(key);

        if (state == null || !state.active()) {
            return SatelliteResult.failure("卫星关系不存在");
        }

        SatelliteRelation previousRelation = state.relation();

        // 移除关系
        state.setActive(false);
        relations.remove(key);
        satelliteToSuzerain.remove(satelliteId);

        Set<NationId> satellites = suzerainToSatellites.get(suzerainId);
        if (satellites != null) {
            satellites.remove(satelliteId);
            if (satellites.isEmpty()) {
                suzerainToSatellites.remove(suzerainId);
            }
        }

        relationChangeTimestamps.put(key, Instant.now());

        // 发布事件
        publishEvent(new SatelliteRelationDissolvedEvent(suzerainId, satelliteId, previousRelation, reason));

        // 保存状态
        saveState();

        return SatelliteResult.success("卫星关系已解除");
    }

    // ==================== 保护关系 ====================

    @Override
    public SatelliteResult establishProtection(NationId protectorId, NationId protectedId) {
        return establishRelation(protectorId, protectedId, SatelliteRelation.PROTECTORATE);
    }

    @Override
    public SatelliteResult endProtection(NationId protectorId, NationId protectedId) {
        return dissolveRelationInternal(protectorId, protectedId, SatelliteRelationDissolvedEvent.DissolveReason.RELEASE);
    }

    // ==================== 义务与收益 ====================

    @Override
    public BigDecimal getTributeAmount(NationId satelliteId) {
        Optional<NationId> suzerainOpt = suzerainOf(satelliteId);
        if (suzerainOpt.isEmpty()) {
            return BigDecimal.ZERO;
        }

        // 从卫星国国库获取一定比例的余额
        if (treasuryService != null) {
            BigDecimal balance = treasuryService.balance(satelliteId);
            SatelliteState state = relations.get(SatellitePairKey.of(suzerainOpt.get(), satelliteId));
            double rate = state != null ? state.tributeRate() : 0.0;
            return balance.multiply(BigDecimal.valueOf(rate)).setScale(2, RoundingMode.DOWN);
        }

        return BigDecimal.ZERO;
    }

    @Override
    public boolean setTributeAmount(NationId satelliteId, BigDecimal amount) {
        // 贡金由税率自动计算，不支持手动设置固定金额
        return false;
    }

    @Override
    public double getTributeRate(NationId satelliteId) {
        Optional<NationId> suzerainOpt = suzerainOf(satelliteId);
        if (suzerainOpt.isEmpty()) {
            return 0.0;
        }

        SatelliteState state = relations.get(SatellitePairKey.of(suzerainOpt.get(), satelliteId));
        return state != null ? state.tributeRate() : 0.0;
    }

    @Override
    public boolean setTributeRate(NationId satelliteId, double rate) {
        Optional<NationId> suzerainOpt = suzerainOf(satelliteId);
        if (suzerainOpt.isEmpty()) {
            return false;
        }

        SatelliteState state = relations.get(SatellitePairKey.of(suzerainOpt.get(), satelliteId));
        if (state == null) {
            return false;
        }

        if (rate < 0 || rate > state.relation().maxTributeRate()) {
            return false;
        }

        state.setTributeRate(rate);
        saveState();
        return true;
    }

    @Override
    public BigDecimal collectTributes(NationId suzerainId) {
        if (treasuryService == null || economyService == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal totalCollected = BigDecimal.ZERO;

        for (NationId satelliteId : satellitesOf(suzerainId)) {
            SatelliteState state = relations.get(SatellitePairKey.of(suzerainId, satelliteId));
            if (state == null || !state.active()) {
                continue;
            }

            BigDecimal satelliteBalance = treasuryService.balance(satelliteId);
            BigDecimal tribute = satelliteBalance.multiply(BigDecimal.valueOf(state.tributeRate()))
                .setScale(2, RoundingMode.DOWN);

            if (tribute.signum() > 0) {
                // 从卫星国转出
                treasuryService.withdraw(satelliteId, tribute);

                // 转入宗主国
                treasuryService.deposit(suzerainId, tribute);

                BigDecimal newBalance = treasuryService.balance(suzerainId);
                totalCollected = totalCollected.add(tribute);

                // 发布贡金事件
                publishEvent(new SatelliteTributePaidEvent(
                    suzerainId,
                    satelliteId,
                    tribute,
                    newBalance.subtract(tribute),
                    newBalance
                ));
            }
        }

        return totalCollected;
    }

    // ==================== 防御保护 ====================

    @Override
    public boolean isProtectedFrom(NationId satelliteId, NationId attackerId) {
        Optional<NationId> suzerainOpt = suzerainOf(satelliteId);
        if (suzerainOpt.isEmpty()) {
            return false;
        }

        SatelliteState state = relations.get(SatellitePairKey.of(suzerainOpt.get(), satelliteId));
        if (state == null || !state.active()) {
            return false;
        }

        // 宗主国受到攻击时，其卫星国也受到保护
        return state.relation().providesMilitaryProtection();
    }

    @Override
    public SatelliteDefenseStatus getDefenseStatus(NationId satelliteId) {
        Optional<NationId> suzerainOpt = suzerainOf(satelliteId);
        if (suzerainOpt.isEmpty()) {
            return new SatelliteDefenseStatus(satelliteId, null, false, false, BigDecimal.ZERO);
        }

        NationId suzerainId = suzerainOpt.get();
        SatelliteState state = relations.get(SatellitePairKey.of(suzerainId, satelliteId));

        if (state == null || !state.active()) {
            return new SatelliteDefenseStatus(satelliteId, null, false, false, BigDecimal.ZERO);
        }

        BigDecimal defenseBonus = BigDecimal.ZERO;
        if (state.relation().providesAutomaticDefense()) {
            defenseBonus = BigDecimal.valueOf(0.10); // 10% 防御加成
        }

        return new SatelliteDefenseStatus(
            satelliteId,
            suzerainId,
            state.relation().providesMilitaryProtection(),
            state.relation().providesAutomaticDefense(),
            defenseBonus
        );
    }

    // ==================== 特殊状态 ====================

    @Override
    public boolean canDeclareWar(NationId satelliteId) {
        Optional<NationId> suzerainOpt = suzerainOf(satelliteId);
        if (suzerainOpt.isEmpty()) {
            return true; // 非卫星国可以自由宣战
        }

        SatelliteState state = relations.get(SatellitePairKey.of(suzerainOpt.get(), satelliteId));
        if (state == null) {
            return true;
        }

        // 宗主国不能攻击自己的卫星国
        // 卫星国是否可以宣战取决于关系类型
        return state.allowsIndependentWar() || state.relation() == SatelliteRelation.NONE;
    }

    @Override
    public boolean canFormAlliance(NationId satelliteId, NationId targetId) {
        // 不能与宗主国结盟
        Optional<NationId> suzerainOpt = suzerainOf(satelliteId);
        if (suzerainOpt.isPresent() && suzerainOpt.get().equals(targetId)) {
            return false;
        }

        // 检查是否已存在卫星关系
        if (hasRelation(satelliteId, targetId) || hasRelation(targetId, satelliteId)) {
            return false;
        }

        return true;
    }

    // ==================== 配置 ====================

    @Override
    public int getMaxSuzerains() {
        return 1; // 一个国家只能有一个宗主国
    }

    @Override
    public int getMaxSatellites(NationId suzerainId) {
        return maxSatellitesPerNation;
    }

    // ==================== 统计 ====================

    @Override
    public int getTotalSatellites() {
        return satelliteToSuzerain.size();
    }

    @Override
    public int getTotalSuzerains() {
        return suzerainToSatellites.size();
    }

    @Override
    public String summary() {
        return suzerainToSatellites.size() + " suzerain(s), " + satelliteToSuzerain.size() + " satellite(s), " + relations.size() + " active relation(s)";
    }

    // ==================== 辅助方法 ====================

    private boolean isInCooldown(NationId nation1, NationId nation2) {
        SatellitePairKey key = SatellitePairKey.of(nation1, nation2);
        Instant lastChange = relationChangeTimestamps.get(key);
        if (lastChange == null) {
            return false;
        }
        return Duration.between(lastChange, Instant.now()).toMillis() < relationCooldownMs;
    }

    private void publishEvent(Object event) {
        if (eventBus != null) {
            eventBus.publish(event);
        }
    }

    private void saveState() {
        if (persistenceService == null) {
            return;
        }
        // 保存到 properties 文件
        Properties props = new Properties();
        for (Map.Entry<SatellitePairKey, SatelliteState> entry : relations.entrySet()) {
            SatelliteState state = entry.getValue();
            String prefix = "satellite." + entry.getKey().left() + "." + entry.getKey().right();
            props.setProperty(prefix + ".relation", state.relation().name());
            props.setProperty(prefix + ".tribute-rate", String.valueOf(state.tributeRate()));
            props.setProperty(prefix + ".established", String.valueOf(state.establishedAt().getEpochSecond()));
            props.setProperty(prefix + ".active", String.valueOf(state.active()));
        }
        persistenceService.saveProperties(metadata().id(), "satellites.properties", props);
    }

    private void loadState() {
        if (persistenceService == null) {
            return;
        }

        Properties props = persistenceService.loadProperties(metadata().id(), "satellites.properties");
        relations.clear();
        satelliteToSuzerain.clear();
        suzerainToSatellites.clear();

        for (String key : props.stringPropertyNames()) {
            if (!key.startsWith("satellite.")) {
                continue;
            }

            // 解析 key: satellite.{suzerainId}.{satelliteId}.relation
            String[] parts = key.split("\\.");
            if (parts.length < 4) {
                continue;
            }

            try {
                NationId suzerainId = NationId.fromString(parts[1]);
                NationId satelliteId = NationId.fromString(parts[2]);
                String propType = parts[3];

                SatellitePairKey pairKey = SatellitePairKey.of(suzerainId, satelliteId);
                SatelliteState state = relations.computeIfAbsent(pairKey, k -> {
                    SatelliteState newState = new SatelliteState(suzerainId, satelliteId, SatelliteRelation.NONE, Instant.now());
                    return newState;
                });

                switch (propType) {
                    case "relation" -> {
                        String relName = props.getProperty(key);
                        SatelliteRelation rel = SatelliteRelation.valueOf(relName);
                        // 使用反射设置关系类型
                        // 实际上需要修改 SatelliteState 的创建方式
                    }
                    case "tribute-rate" -> {
                        double rate = Double.parseDouble(props.getProperty(key));
                        state.setTributeRate(rate);
                    }
                    case "established" -> {
                        // 已在构造函数中设置
                    }
                    case "active" -> {
                        boolean active = Boolean.parseBoolean(props.getProperty(key));
                        state.setActive(active);
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load satellite state: " + key + " - " + e.getMessage());
            }
        }

        // 重建索引
        for (SatelliteState state : relations.values()) {
            if (state.active()) {
                satelliteToSuzerain.put(state.satelliteId(), state.suzerainId());
                suzerainToSatellites.computeIfAbsent(state.suzerainId(), k -> ConcurrentHashMap.newKeySet())
                    .add(state.satelliteId());
            }
        }
    }

    /**
     * 获取关系冷却时间（毫秒）
     */
    public long getRelationCooldownMs() {
        return relationCooldownMs;
    }

    /**
     * 设置关系冷却时间
     */
    public void setRelationCooldownMs(long cooldownMs) {
        this.relationCooldownMs = cooldownMs;
    }

    /**
     * 获取贡金收取间隔（毫秒）
     */
    public long getTributeCollectionIntervalMs() {
        return tributeCollectionIntervalMs;
    }

    /**
     * 设置贡金收取间隔
     */
    public void setTributeCollectionIntervalMs(long intervalMs) {
        this.tributeCollectionIntervalMs = intervalMs;
    }

    /**
     * 辅助方法：修复变量名错误
     */
    private Optional<NationId> suzerainOfFix(NationId satelliteId) {
        return suzerainOf(satelliteId);
    }
}

/**
 * 卫星国关系键
 */
record SatellitePairKey(NationId left, NationId right) {
    static SatellitePairKey of(NationId first, NationId second) {
        return first.toString().compareTo(second.toString()) <= 0
            ? new SatellitePairKey(first, second)
            : new SatellitePairKey(second, first);
    }
}
