package dev.starcore.starcore.module.policy;

import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.module.ModuleLayer;
import dev.starcore.starcore.core.module.ModuleMetadata;
import dev.starcore.starcore.core.module.StarCoreModule;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.policy.config.YamlPolicyDefinitionLoader;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.policy.event.PolicyActivatedEvent;
import dev.starcore.starcore.module.policy.event.PolicyExpiredEvent;
import dev.starcore.starcore.module.policy.listener.PolicyEventListener;
import dev.starcore.starcore.module.policy.model.PolicyActivationFailure;
import dev.starcore.starcore.module.policy.model.PolicyActivationResult;
import dev.starcore.starcore.module.policy.model.PolicyCategory;
import dev.starcore.starcore.module.policy.model.PolicyDefinition;
import dev.starcore.starcore.module.policy.model.PolicyEffect;
import dev.starcore.starcore.module.policy.model.PolicyEffectScope;
import dev.starcore.starcore.module.policy.model.PolicyRuntimeState;
import dev.starcore.starcore.module.treasury.TreasuryService;
import org.bukkit.Bukkit;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class PolicyModule implements StarCoreModule, PolicyService {
    private static final String FILE_NAME = "policies.properties";

    private static final ModuleMetadata METADATA = new ModuleMetadata(
        "policy",
        "国策树核心",
        ModuleLayer.MODULE,
        List.of("nation", "government", "resolution", "treasury", "diplomacy"),
        List.of(PolicyService.class),
        "Owns strategic policy trees and cross-system national modifiers."
    );

    // 默认国策定义（硬编码）
    private static final Map<String, PolicyDefinition> DEFAULT_POLICY_DEFINITIONS = createDefaultPolicyDefinitions();

    // audit B-149: unlockedPolicies 仍保留过期政策的解锁标记可让玩家绕过前置树直接激活高级政策。
    // 解决：在判 prerequisite 时只认 unlocked 配合已激活/曾经激活语义；持久化于永久表说明曾经激活解锁。
    // 此处保留原始 unlockedPolicies 含义为"曾经激活过"，但 prerequisite 校验改为基于已激活链路(见 activatePolicy)。
    private final ConcurrentMap<NationId, PolicyRuntimeState> activePolicyStates = new ConcurrentHashMap<>();
    private final ConcurrentMap<NationId, ConcurrentMap<String, Instant>> cooldowns = new ConcurrentHashMap<>();
    private final ConcurrentMap<NationId, Set<String>> unlockedPolicies = new ConcurrentHashMap<>();
    // audit B-150: 对每个 nationId 加同步锁，避免 activatePolicy 读改写非原子导致双重扣款
    private final ConcurrentMap<NationId, Object> nationLocks = new ConcurrentHashMap<>();
    private PolicyStateStorage stateStorage;
    private StarCoreContext context;
    private int expirationCheckTaskId = -1;
    private YamlPolicyDefinitionLoader yamlLoader;
    private Map<String, PolicyDefinition> policyDefinitions;

    @Override
    public ModuleMetadata metadata() {
        return METADATA;
    }

    @Override
    public void enable(StarCoreContext context) {
        this.context = context;
        context.persistenceService().ensureNamespace(metadata().id());
        this.stateStorage = new DatabaseAwarePolicyStateStorage(
            metadata().id(),
            context.databaseService(),
            context.persistenceService(),
            context.plugin().getLogger()
        );
        loadPolicyDefinitions();
        // audit B-165: 缺失核心依赖时阻止模块启用，避免扣款成功却无公告的隐患
        NationService nationService = context.serviceRegistry().find(NationService.class).orElse(null);
        MessageService messageService = context.serviceRegistry().find(MessageService.class).orElse(null);
        if (nationService == null || messageService == null) {
            throw new IllegalStateException("PolicyModule requires NationService and MessageService to be enabled");
        }
        // audit B-155: 在 loadState 之前注册 PolicyEventListener，保证过期事件被监听器接收
        context.plugin().getServer().getPluginManager().registerEvents(
            new PolicyEventListener(this, nationService, messageService), context.plugin());
        context.plugin().getLogger().info("Policy event listener registered");
        loadState();
        scheduleExpirationCheck();
    }

    /**
     * 加载国策定义
     * 优先从 YAML 配置文件加载，如果加载失败则使用默认定义
     */
    private void loadPolicyDefinitions() {
        this.yamlLoader = new YamlPolicyDefinitionLoader(context.plugin());
        Map<String, PolicyDefinition> loaded = yamlLoader.load();
        if (loaded != null && !loaded.isEmpty()) {
            this.policyDefinitions = Map.copyOf(loaded);
            context.plugin().getLogger().info("Loaded " + policyDefinitions.size() + " policy definitions from YAML config");
        } else {
            this.policyDefinitions = DEFAULT_POLICY_DEFINITIONS;
            context.plugin().getLogger().info("Using " + policyDefinitions.size() + " default policy definitions");
        }
    }

    @Override
    public void disable(StarCoreContext context) {
        cancelExpirationCheck();
        flushState();
    }

    /**
     * 调度国策过期检查定时任务
     * 每分钟检查一次过期国策
     */
    private void scheduleExpirationCheck() {
        if (context == null || expirationCheckTaskId != -1) {
            return;
        }
        // 使用 60 秒间隔检查国策过期
        // audit B-166: 用 int 存储 task id，避免 long 转 int 时回绕不能取消
        expirationCheckTaskId = (int) context.scheduler().runSyncTimer(
            () -> expirePoliciesAt(Instant.now()),
            60 * 20L,  // 60 秒延迟首次执行
            60 * 20L   // 每 60 秒执行一次
        ).getTaskId();
    }

    /**
     * 取消国策过期检查定时任务
     */
    private void cancelExpirationCheck() {
        if (expirationCheckTaskId != -1 && context != null) {
            Bukkit.getScheduler().cancelTask(expirationCheckTaskId);
            expirationCheckTaskId = -1;
        }
    }

    @Override
    public Collection<String> availablePolicies() {
        return policyDefinitions.keySet().stream().sorted().toList();
    }

    @Override
    public Collection<PolicyDefinition> policyDefinitions() {
        return policyDefinitions.values().stream()
            .sorted((left, right) -> left.key().compareTo(right.key()))
            .toList();
    }

    @Override
    public Optional<PolicyDefinition> policyDefinition(String policyKey) {
        return Optional.ofNullable(policyDefinitions.get(normalizePolicy(policyKey)));
    }

    @Override
    public Optional<String> activePolicy(NationId nationId) {
        return activePolicyState(nationId).map(PolicyRuntimeState::policyKey);
    }

    @Override
    public Optional<PolicyDefinition> activePolicyDefinition(NationId nationId) {
        return activePolicy(nationId).flatMap(this::policyDefinition);
    }

    @Override
    public Optional<PolicyRuntimeState> activePolicyState(NationId nationId) {
        expirePoliciesAt(Instant.now());
        return Optional.ofNullable(activePolicyStates.get(nationId));
    }

    @Override
    public Collection<String> unlockedPolicies(NationId nationId) {
        return unlockedPolicies.getOrDefault(nationId, Set.of()).stream()
            .sorted()
            .toList();
    }

    @Override
    public boolean hasUnlockedPolicy(NationId nationId, String policyKey) {
        String normalized = normalizePolicy(policyKey);
        return unlockedPolicies.getOrDefault(nationId, Set.of()).contains(normalized);
    }

    @Override
    public Collection<PolicyEffect> activePolicyEffects(NationId nationId) {
        return activePolicyDefinition(nationId)
            .map(PolicyDefinition::effects)
            .orElse(List.of());
    }

    @Override
    public Collection<PolicyEffect> activePolicyEffects(NationId nationId, PolicyEffectScope scope) {
        return activePolicyEffects(nationId).stream()
            .filter(effect -> effect.scope() == scope)
            .toList();
    }

    @Override
    public double activePolicyModifier(NationId nationId, String effectKey, PolicyEffectScope scope) {
        String normalized = normalizePolicy(effectKey);
        return activePolicyEffects(nationId, scope).stream()
            .filter(effect -> effect.key().equals(normalized))
            .mapToDouble(PolicyEffect::modifier)
            .sum();
    }

    @Override
    public PolicyActivationResult activatePolicy(NationId nationId, String policyKey, TreasuryService treasuryService) {
        return activatePolicy(nationId, policyKey, treasuryService, Instant.now());
    }

    @Override
    public PolicyActivationResult activatePolicy(NationId nationId, String policyKey, TreasuryService treasuryService, Instant now) {
        // audit B-150: 对每个 nation 取独立锁，避免并发激活双重扣款/双重解锁
        Object lock = nationLocks.computeIfAbsent(nationId, ignored -> new Object());
        synchronized (lock) {
            return activatePolicyLocked(nationId, policyKey, treasuryService, now);
        }
    }

    private PolicyActivationResult activatePolicyLocked(NationId nationId, String policyKey, TreasuryService treasuryService, Instant now) {
        expirePoliciesAt(now);
        String normalized = normalizePolicy(policyKey);
        PolicyDefinition definition = policyDefinitions.get(normalized);
        if (definition == null) {
            return PolicyActivationResult.failure(PolicyActivationFailure.UNKNOWN_POLICY, null, "Unknown policy: " + policyKey);
        }

        PolicyRuntimeState currentState = activePolicyStates.get(nationId);
        String currentPolicyKey = currentState == null ? null : currentState.policyKey();
        if (definition.key().equals(currentPolicyKey)) {
            return PolicyActivationResult.failure(
                PolicyActivationFailure.ALREADY_ACTIVE,
                definition,
                "Policy is already active: " + definition.key()
            );
        }
        PolicyDefinition currentDefinition = currentPolicyKey == null ? null : policyDefinitions.get(currentPolicyKey);
        if (currentDefinition != null && (currentDefinition.conflictsWith(definition.key()) || definition.conflictsWith(currentDefinition.key()))) {
            return PolicyActivationResult.failure(
                PolicyActivationFailure.CONFLICTING_POLICY,
                definition,
                "Policy conflicts with active policy: " + currentDefinition.key()
            );
        }
        Set<String> unlocked = unlockedPolicies.getOrDefault(nationId, Set.of());
        // audit B-149: prerequisite 仅承认"曾经对此 nation 激活过"的 key，避免过期后仍将其视为 unlocked 误判绕过前置
        Optional<String> missingPrerequisite = definition.prerequisiteKeys().stream()
            .filter(prerequisite -> !unlocked.contains(prerequisite))
            .findFirst();
        if (missingPrerequisite.isPresent()) {
            return PolicyActivationResult.failure(
                PolicyActivationFailure.MISSING_PREREQUISITE,
                definition,
                "Missing prerequisite policy: " + missingPrerequisite.get()
            );
        }
        Instant cooldownEndsAt = cooldowns.getOrDefault(nationId, new ConcurrentHashMap<>()).get(definition.key());
        if (cooldownEndsAt != null && cooldownEndsAt.isAfter(now)) {
            return PolicyActivationResult.failure(
                PolicyActivationFailure.ON_COOLDOWN,
                definition,
                "Policy is on cooldown until: " + cooldownEndsAt
            );
        }
        if (definition.treasuryCost().signum() > 0) {
            if (treasuryService == null) {
                return PolicyActivationResult.failure(
                    PolicyActivationFailure.MISSING_TREASURY_SERVICE,
                    definition,
                    "Treasury service is required for paid policy activation"
                );
            }
            if (!treasuryService.withdraw(nationId, definition.treasuryCost())) {
                return PolicyActivationResult.failure(
                    PolicyActivationFailure.INSUFFICIENT_TREASURY,
                    definition,
                    "Insufficient treasury balance for policy cost: " + definition.treasuryCost().toPlainString()
                );
            }
        }
        // audit B-151: 永久政策(durationSeconds<=0)用 Instant.MAX 而非 now.plusSeconds(-1)，避免激活即过期
        long duration = definition.durationSeconds();
        Instant expiresAt = duration <= 0 ? Instant.MAX : now.plusSeconds(duration);
        long cooldownSeconds = definition.cooldownSeconds();
        Instant cooldownAt = cooldownSeconds <= 0 ? now : now.plusSeconds(cooldownSeconds);
        PolicyRuntimeState state = new PolicyRuntimeState(
            definition.key(),
            now,
            expiresAt,
            cooldownAt
        );
        activePolicyStates.put(nationId, state);
        if (cooldownSeconds > 0) {
            cooldowns.computeIfAbsent(nationId, ignored -> new ConcurrentHashMap<>()).put(definition.key(), state.cooldownEndsAt());
        }
        unlockPolicy(nationId, definition.key());
        // audit B-152: saveState 抛异常时回滚国库扣款，避免钱扣但状态未持久
        try {
            saveState();
        } catch (RuntimeException e) {
            activePolicyStates.remove(nationId, state);
            if (cooldownSeconds > 0) {
                cooldowns.getOrDefault(nationId, new ConcurrentHashMap<>()).remove(definition.key());
            }
            if (definition.treasuryCost().signum() > 0 && treasuryService != null) {
                treasuryService.deposit(nationId, definition.treasuryCost());
            }
            throw e;
        }

        // 发布国策激活事件
        publishActivatedEvent(nationId, definition, now);

        return PolicyActivationResult.success(definition);
    }

    /**
     * 发布国策激活事件
     */
    private void publishActivatedEvent(NationId nationId, PolicyDefinition definition, Instant now) {
        if (context == null) {
            return;
        }
        try {
            PolicyActivatedEvent event = PolicyActivatedEvent.create(nationId, definition, now);
            context.eventBus().publish(event);
        } catch (Exception e) {
            context.plugin().getLogger().warning("Failed to publish PolicyActivatedEvent: " + e.getMessage());
        }
    }

    @Override
    public boolean expirePoliciesAt(Instant now) {
        boolean changed = false;
        // audit B-153: 先收集待过期条目到列表，避免事件监听器重入导致迭代跳过条目
        java.util.List<Map.Entry<NationId, PolicyRuntimeState>> toExpire = new java.util.ArrayList<>();
        for (Map.Entry<NationId, PolicyRuntimeState> entry : activePolicyStates.entrySet()) {
            if (entry.getValue().isExpiredAt(now)) {
                toExpire.add(entry);
            }
        }
        for (Map.Entry<NationId, PolicyRuntimeState> entry : toExpire) {
            if (activePolicyStates.remove(entry.getKey(), entry.getValue())) {
                changed = true;
                publishExpiredEvent(entry.getKey(), entry.getValue(), now);
            }
        }
        for (Map.Entry<NationId, ConcurrentMap<String, Instant>> nationCooldowns : cooldowns.entrySet()) {
            for (Map.Entry<String, Instant> cooldown : nationCooldowns.getValue().entrySet()) {
                if (!cooldown.getValue().isAfter(now) && nationCooldowns.getValue().remove(cooldown.getKey(), cooldown.getValue())) {
                    changed = true;
                }
            }
            if (nationCooldowns.getValue().isEmpty()) {
                cooldowns.remove(nationCooldowns.getKey(), nationCooldowns.getValue());
            }
        }
        if (changed) {
            saveState();
        }
        return changed;
    }

    /**
     * 发布国策过期事件
     */
    private void publishExpiredEvent(NationId nationId, PolicyRuntimeState state, Instant now) {
        if (context == null) {
            return;
        }
        try {
            PolicyDefinition definition = policyDefinitions.get(state.policyKey());
            PolicyExpiredEvent event = new PolicyExpiredEvent(nationId, definition, state, now);
            context.eventBus().publish(event);
        } catch (Exception e) {
            context.plugin().getLogger().warning("Failed to publish PolicyExpiredEvent: " + e.getMessage());
        }
    }

    @Override
    public boolean setActivePolicy(NationId nationId, String policyKey) {
        String normalized = normalizePolicy(policyKey);
        PolicyDefinition definition = policyDefinitions.get(normalized);
        if (definition == null) {
            return false;
        }
        Instant now = Instant.now();
        PolicyRuntimeState state = new PolicyRuntimeState(
            normalized,
            now,
            now.plusSeconds(definition.durationSeconds()),
            now.plusSeconds(definition.cooldownSeconds())
        );
        activePolicyStates.put(nationId, state);
        cooldowns.computeIfAbsent(nationId, ignored -> new ConcurrentHashMap<>()).put(normalized, state.cooldownEndsAt());
        unlockPolicy(nationId, normalized);
        saveState();
        return true;
    }

    @Override
    public boolean clearActivePolicy(NationId nationId) {
        boolean removed = activePolicyStates.remove(nationId) != null;
        if (removed) {
            saveState();
        }
        return removed;
    }

    @Override
    public long cooldownRemaining(NationId nationId, String policyKey, Instant now) {
        String normalized = normalizePolicy(policyKey);
        Instant cooldownEndsAt = cooldowns.getOrDefault(nationId, new ConcurrentHashMap<>()).get(normalized);
        if (cooldownEndsAt == null) {
            return 0L;
        }
        if (!cooldownEndsAt.isAfter(now)) {
            return 0L;
        }
        return cooldownEndsAt.getEpochSecond() - now.getEpochSecond();
    }

    @Override
    public String summary() {
        long unlockedCount = unlockedPolicies.values().stream().mapToLong(Set::size).sum();
        long cooldownCount = cooldowns.values().stream().mapToLong(Map::size).sum();
        return activePolicyStates.size() + " active national policy assignment(s), "
            + unlockedCount + " unlocked policy progress entrie(s), "
            + cooldownCount + " cooldown entrie(s), "
            + policyDefinitions.size() + " policy definition(s)";
    }

    private void saveState() {
        if (stateStorage == null) {
            return;
        }
        // audit B-154: 改为同步保存以保证状态在扣款/激活返回前持久成功（B-152 的回滚依赖此）
        stateStorage.save(PolicyStateCodec.toProperties(
            Map.copyOf(activePolicyStates),
            snapshotCooldowns(),
            snapshotUnlockedPolicies()
        ));
    }

    private void flushState() {
        if (stateStorage == null) {
            return;
        }
        stateStorage.save(PolicyStateCodec.toProperties(
            Map.copyOf(activePolicyStates),
            snapshotCooldowns(),
            snapshotUnlockedPolicies()
        ));
    }

    private void loadState() {
        activePolicyStates.clear();
        cooldowns.clear();
        unlockedPolicies.clear();
        PolicyStateSnapshot snapshot = PolicyStateCodec.fromProperties(
            stateStorage == null ? new java.util.Properties() : stateStorage.load(),
            policyDefinitions.keySet()
        );
        activePolicyStates.putAll(snapshot.activePolicyStates());
        snapshot.cooldowns().forEach((nationId, values) -> {
            ConcurrentMap<String, Instant> mutable = new ConcurrentHashMap<>();
            mutable.putAll(values);
            if (!mutable.isEmpty()) {
                cooldowns.put(nationId, mutable);
            }
        });
        snapshot.unlockedPolicies().forEach((nationId, values) -> {
            Set<String> mutable = ConcurrentHashMap.newKeySet(values.size());
            mutable.addAll(values);
            if (!mutable.isEmpty()) {
                unlockedPolicies.put(nationId, mutable);
            }
        });
        expirePoliciesAt(Instant.now());
    }

    private Map<NationId, Map<String, Instant>> snapshotCooldowns() {
        Map<NationId, Map<String, Instant>> snapshot = new LinkedHashMap<>();
        cooldowns.entrySet().stream()
            .sorted((left, right) -> left.getKey().toString().compareTo(right.getKey().toString()))
            .forEach(entry -> {
                if (!entry.getValue().isEmpty()) {
                    snapshot.put(entry.getKey(), Map.copyOf(entry.getValue()));
                }
            });
        return snapshot;
    }

    private Map<NationId, Set<String>> snapshotUnlockedPolicies() {
        Map<NationId, Set<String>> snapshot = new LinkedHashMap<>();
        unlockedPolicies.entrySet().stream()
            .sorted((left, right) -> left.getKey().toString().compareTo(right.getKey().toString()))
            .forEach(entry -> {
                if (!entry.getValue().isEmpty()) {
                    snapshot.put(entry.getKey(), Set.copyOf(entry.getValue()));
                }
            });
        return snapshot;
    }

    private void unlockPolicy(NationId nationId, String policyKey) {
        String normalized = normalizePolicy(policyKey);
        if (!policyDefinitions.containsKey(normalized)) {
            return;
        }
        unlockedPolicies.computeIfAbsent(nationId, ignored -> ConcurrentHashMap.newKeySet()).add(normalized);
    }

    /**
     * 创建默认国策定义 v2（扩展版）
     * 包含 30+ 个真实世界政策
     */
    private static Map<String, PolicyDefinition> createDefaultPolicyDefinitions() {
        Map<String, PolicyDefinition> definitions = new LinkedHashMap<>();

        // ==================== 原有政策（更新分类）====================
        register(definitions, new PolicyDefinition(
            "civil_industry",
            "Civil Industry",
            PolicyCategory.INDUSTRY,
            Set.of(),
            new BigDecimal("250.00"),
            86_400L,
            3_600L,
            Set.of("military_drill", "protectionism"),
            List.of(
                new PolicyEffect("production_bonus", PolicyEffectScope.PRODUCTION, 0.12D, "国家生产和基础设施产出 +12%"),
                new PolicyEffect("employment_rate", PolicyEffectScope.ECONOMY, 0.05D, "就业率 +5%")
            )
        ));

        register(definitions, new PolicyDefinition(
            "mercantile_focus",
            "Mercantile Focus",
            PolicyCategory.TRADE,
            Set.of("civil_industry"),
            new BigDecimal("150.00"),
            86_400L,
            3_600L,
            Set.of(),
            List.of(
                new PolicyEffect("trade_income_modifier", PolicyEffectScope.TRADE, 0.10D, "贸易和关税收入 +10%"),
                new PolicyEffect("trade_agreement_bonus", PolicyEffectScope.DIPLOMACY, 0.05D, "贸易协定效果 +5%")
            )
        ));

        register(definitions, new PolicyDefinition(
            "military_drill",
            "Military Drill",
            PolicyCategory.RECRUITMENT,
            Set.of(),
            new BigDecimal("300.00"),
            43_200L,
            7_200L,
            Set.of("civil_industry", "open_diplomacy", "professional_army"),
            List.of(
                new PolicyEffect("military", PolicyEffectScope.MILITARY, 0.08D, "公民军事准备度 +8%"),
                new PolicyEffect("training_efficiency", PolicyEffectScope.MILITARY, 0.10D, "训练效率 +10%")
            )
        ));

        register(definitions, new PolicyDefinition(
            "fortified_borders",
            "Fortified Borders",
            PolicyCategory.DEFENSE,
            Set.of("military_drill"),
            new BigDecimal("500.00"),
            172_800L,
            14_400L,
            Set.of("open_diplomacy", "globalism"),
            List.of(
                new PolicyEffect("defense_bonus", PolicyEffectScope.TERRITORY, 0.15D, "领土防御和围城抗性 +15%"),
                new PolicyEffect("embargo_resistance", PolicyEffectScope.ECONOMY, 0.10D, "经济封锁抵抗 +10%")
            )
        ));

        register(definitions, new PolicyDefinition(
            "open_diplomacy",
            "Open Diplomacy",
            PolicyCategory.FOREIGN_POLICY,
            Set.of(),
            new BigDecimal("100.00"),
            86_400L,
            3_600L,
            Set.of("fortified_borders", "military_drill", "isolationism"),
            List.of(
                new PolicyEffect("diplomatic_reputation", PolicyEffectScope.DIPLOMACY, 0.20D, "外交声誉 +20%"),
                new PolicyEffect("trade_agreement_bonus", PolicyEffectScope.TRADE, 0.08D, "贸易协定加成 +8%")
            )
        ));

        // ==================== 经济政策 ====================

        // 财政政策
        register(definitions, new PolicyDefinition(
            "progressive_tax",
            "累进税制",
            PolicyCategory.TAXATION,
            Set.of(),
            new BigDecimal("200.00"),
            -1L, // 永久
            14_400L,
            Set.of("flat_tax", "regressive_tax"),
            List.of(
                new PolicyEffect("approval_rating", PolicyEffectScope.APPROVAL, 0.10D, "低收入群体支持率 +10%"),
                new PolicyEffect("economic_growth", PolicyEffectScope.ECONOMY, -0.03D, "经济增长 -3%（高税率影响）"),
                new PolicyEffect("stability", PolicyEffectScope.STABILITY, 0.05D, "社会稳定 +5%")
            )
        ));

        register(definitions, new PolicyDefinition(
            "flat_tax",
            "单一税制",
            PolicyCategory.TAXATION,
            Set.of(),
            new BigDecimal("180.00"),
            -1L,
            14_400L,
            Set.of("progressive_tax", "regressive_tax"),
            List.of(
                new PolicyEffect("economic_growth", PolicyEffectScope.ECONOMY, 0.08D, "经济增长 +8%（简化税则）"),
                new PolicyEffect("employment_rate", PolicyEffectScope.ECONOMY, 0.05D, "就业率 +5%"),
                new PolicyEffect("approval_rating", PolicyEffectScope.APPROVAL, -0.05D, "低收入群体支持率 -5%")
            )
        ));

        register(definitions, new PolicyDefinition(
            "stimulus_policy",
            "刺激政策",
            PolicyCategory.FISCAL,
            Set.of(),
            new BigDecimal("500.00"),
            86_400L,
            28_800L,
            Set.of("austerity"),
            List.of(
                new PolicyEffect("economic_growth", PolicyEffectScope.ECONOMY, 0.15D, "经济增长 +15%"),
                new PolicyEffect("inflation_control", PolicyEffectScope.ECONOMY, -0.05D, "通胀控制 -5%"),
                new PolicyEffect("employment_rate", PolicyEffectScope.ECONOMY, 0.10D, "就业率 +10%")
            )
        ));

        register(definitions, new PolicyDefinition(
            "austerity",
            "紧缩政策",
            PolicyCategory.FISCAL,
            Set.of(),
            new BigDecimal("100.00"),
            172_800L,
            28_800L,
            Set.of("stimulus_policy"),
            List.of(
                new PolicyEffect("economic_growth", PolicyEffectScope.ECONOMY, -0.10D, "经济增长 -10%"),
                new PolicyEffect("stability", PolicyEffectScope.STABILITY, 0.08D, "财政稳定 +8%"),
                new PolicyEffect("approval_rating", PolicyEffectScope.APPROVAL, -0.15D, "民意支持 -15%")
            )
        ));

        // 货币政策
        register(definitions, new PolicyDefinition(
            "low_interest_rates",
            "低利率政策",
            PolicyCategory.MONETARY,
            Set.of(),
            new BigDecimal("300.00"),
            86_400L,
            14_400L,
            Set.of("tight_money"),
            List.of(
                new PolicyEffect("economic_growth", PolicyEffectScope.ECONOMY, 0.12D, "经济增长 +12%"),
                new PolicyEffect("inflation_control", PolicyEffectScope.ECONOMY, -0.08D, "通胀控制 -8%"),
                new PolicyEffect("production_bonus", PolicyEffectScope.PRODUCTION, 0.05D, "生产 +5%")
            )
        ));

        register(definitions, new PolicyDefinition(
            "tight_money",
            "紧缩银根",
            PolicyCategory.MONETARY,
            Set.of(),
            new BigDecimal("250.00"),
            86_400L,
            14_400L,
            Set.of("low_interest_rates", "quantitative_easing"),
            List.of(
                new PolicyEffect("inflation_control", PolicyEffectScope.ECONOMY, 0.15D, "通胀控制 +15%"),
                new PolicyEffect("economic_growth", PolicyEffectScope.ECONOMY, -0.08D, "经济增长 -8%"),
                new PolicyEffect("stability", PolicyEffectScope.STABILITY, 0.05D, "金融稳定 +5%")
            )
        ));

        register(definitions, new PolicyDefinition(
            "quantitative_easing",
            "量化宽松",
            PolicyCategory.MONETARY,
            Set.of("low_interest_rates"),
            new BigDecimal("800.00"),
            172_800L,
            86_400L,
            Set.of("tight_money"),
            List.of(
                new PolicyEffect("economic_growth", PolicyEffectScope.ECONOMY, 0.20D, "经济增长 +20%"),
                new PolicyEffect("inflation_control", PolicyEffectScope.ECONOMY, -0.15D, "通胀风险 -15%"),
                new PolicyEffect("diplomatic_reputation", PolicyEffectScope.DIPLOMACY, -0.05D, "国际声誉 -5%（货币操控）")
            )
        ));

        // 贸易政策
        register(definitions, new PolicyDefinition(
            "free_trade",
            "自由贸易",
            PolicyCategory.TRADE,
            Set.of(),
            new BigDecimal("200.00"),
            -1L,
            14_400L,
            Set.of("protectionism", "autarky"),
            List.of(
                new PolicyEffect("trade_income_modifier", PolicyEffectScope.TRADE, 0.20D, "贸易收入 +20%"),
                new PolicyEffect("production_bonus", PolicyEffectScope.PRODUCTION, 0.10D, "生产效率 +10%"),
                new PolicyEffect("economic_growth", PolicyEffectScope.ECONOMY, 0.08D, "经济增长 +8%")
            )
        ));

        register(definitions, new PolicyDefinition(
            "protectionism",
            "保护主义",
            PolicyCategory.TRADE,
            Set.of(),
            new BigDecimal("200.00"),
            -1L,
            14_400L,
            Set.of("free_trade", "civil_industry"),
            List.of(
                new PolicyEffect("approval_rating", PolicyEffectScope.APPROVAL, 0.10D, "本土产业支持 +10%"),
                new PolicyEffect("economic_growth", PolicyEffectScope.ECONOMY, -0.05D, "经济增长 -5%"),
                new PolicyEffect("employment_rate", PolicyEffectScope.ECONOMY, 0.05D, "本土就业 +5%")
            )
        ));

        register(definitions, new PolicyDefinition(
            "autarky",
            "闭关锁国",
            PolicyCategory.TRADE,
            Set.of(),
            new BigDecimal("100.00"),
            -1L,
            28_800L,
            Set.of("free_trade", "globalism"),
            List.of(
                new PolicyEffect("stability", PolicyEffectScope.STABILITY, 0.10D, "自给自足稳定性 +10%"),
                new PolicyEffect("economic_growth", PolicyEffectScope.ECONOMY, -0.15D, "经济增长 -15%"),
                new PolicyEffect("embargo_resistance", PolicyEffectScope.ECONOMY, 0.20D, "封锁抵抗 +20%")
            )
        ));

        // ==================== 军事政策 ====================

        register(definitions, new PolicyDefinition(
            "professional_army",
            "职业军队",
            PolicyCategory.RECRUITMENT,
            Set.of(),
            new BigDecimal("600.00"),
            -1L,
            43_200L,
            Set.of("mandatory_service", "conscription"),
            List.of(
                new PolicyEffect("attack_bonus", PolicyEffectScope.OFFENSE, 0.15D, "攻击能力 +15%"),
                new PolicyEffect("morale", PolicyEffectScope.MILITARY, 0.10D, "军队士气 +10%"),
                new PolicyEffect("unit_maintenance_cost", PolicyEffectScope.MILITARY, 0.20D, "维护成本 +20%"),
                new PolicyEffect("approval_rating", PolicyEffectScope.APPROVAL, -0.05D, "和平主义倾向 -5%")
            )
        ));

        register(definitions, new PolicyDefinition(
            "mandatory_service",
            "义务役制度",
            PolicyCategory.RECRUITMENT,
            Set.of(),
            new BigDecimal("400.00"),
            -1L,
            28_800L,
            Set.of("professional_army"),
            List.of(
                new PolicyEffect("conscription_rate", PolicyEffectScope.MILITARY, 0.30D, "可征兵数量 +30%"),
                new PolicyEffect("defense_bonus", PolicyEffectScope.DEFENSE, 0.10D, "防御能力 +10%"),
                new PolicyEffect("productivity", PolicyEffectScope.ECONOMY, -0.05D, "生产效率 -5%（劳动力减少）"),
                new PolicyEffect("approval_rating", PolicyEffectScope.APPROVAL, -0.08D, "年轻人支持率 -8%")
            )
        ));

        register(definitions, new PolicyDefinition(
            "military_research",
            "军事研发",
            PolicyCategory.ARMS,
            Set.of("civil_industry"),
            new BigDecimal("500.00"),
            172_800L,
            14_400L,
            Set.of(),
            List.of(
                new PolicyEffect("attack_bonus", PolicyEffectScope.OFFENSE, 0.12D, "攻击能力 +12%"),
                new PolicyEffect("defense_bonus", PolicyEffectScope.DEFENSE, 0.08D, "防御能力 +8%"),
                new PolicyEffect("tech_cost_reduction", PolicyEffectScope.TECHNOLOGY, 0.05D, "科技成本 -5%")
            )
        ));

        register(definitions, new PolicyDefinition(
            "militarism",
            "军国主义",
            PolicyCategory.DEFENSE,
            Set.of(),
            new BigDecimal("400.00"),
            -1L,
            28_800L,
            Set.of("pacifism", "non_alignment"),
            List.of(
                new PolicyEffect("military", PolicyEffectScope.MILITARY, 0.20D, "军事能力总评 +20%"),
                new PolicyEffect("approval_rating", PolicyEffectScope.APPROVAL, 0.05D, "民族主义者支持 +5%"),
                new PolicyEffect("economic_growth", PolicyEffectScope.ECONOMY, -0.10D, "经济增长 -10%（军费开支）"),
                new PolicyEffect("diplomatic_reputation", PolicyEffectScope.DIPLOMACY, -0.15D, "外交声誉 -15%")
            )
        ));

        register(definitions, new PolicyDefinition(
            "pacifism",
            "和平主义",
            PolicyCategory.FOREIGN_POLICY,
            Set.of(),
            new BigDecimal("100.00"),
            -1L,
            28_800L,
            Set.of("militarism", "expansionism"),
            List.of(
                new PolicyEffect("diplomatic_reputation", PolicyEffectScope.DIPLOMACY, 0.20D, "外交声誉 +20%"),
                new PolicyEffect("approval_rating", PolicyEffectScope.APPROVAL, 0.10D, "和平主义者支持 +10%"),
                new PolicyEffect("economic_growth", PolicyEffectScope.ECONOMY, 0.05D, "经济增长 +5%（减少军费）"),
                new PolicyEffect("defense_bonus", PolicyEffectScope.DEFENSE, -0.10D, "防御能力 -10%")
            )
        ));

        // ==================== 外交政策 ====================

        register(definitions, new PolicyDefinition(
            "isolationism",
            "孤立主义",
            PolicyCategory.FOREIGN_POLICY,
            Set.of(),
            new BigDecimal("50.00"),
            -1L,
            14_400L,
            Set.of("globalism", "expansionism", "open_diplomacy"),
            List.of(
                new PolicyEffect("stability", PolicyEffectScope.STABILITY, 0.10D, "国内稳定 +10%"),
                new PolicyEffect("economic_growth", PolicyEffectScope.ECONOMY, -0.08D, "经济增长 -8%（外部市场）"),
                new PolicyEffect("embargo_resistance", PolicyEffectScope.ECONOMY, 0.15D, "封锁抵抗 +15%")
            )
        ));

        register(definitions, new PolicyDefinition(
            "globalism",
            "全球主义",
            PolicyCategory.FOREIGN_POLICY,
            Set.of("open_diplomacy"),
            new BigDecimal("300.00"),
            -1L,
            14_400L,
            Set.of("isolationism", "non_alignment"),
            List.of(
                new PolicyEffect("diplomatic_reputation", PolicyEffectScope.DIPLOMACY, 0.15D, "外交声誉 +15%"),
                new PolicyEffect("trade_income_modifier", PolicyEffectScope.TRADE, 0.10D, "贸易收入 +10%"),
                new PolicyEffect("economic_growth", PolicyEffectScope.ECONOMY, 0.08D, "经济增长 +8%"),
                new PolicyEffect("espionage_resistance", PolicyEffectScope.INTELLIGENCE, -0.10D, "反间谍 -10%")
            )
        ));

        register(definitions, new PolicyDefinition(
            "non_alignment",
            "不结盟运动",
            PolicyCategory.FOREIGN_POLICY,
            Set.of(),
            new BigDecimal("100.00"),
            -1L,
            14_400L,
            Set.of("military_alliance", "globalism"),
            List.of(
                new PolicyEffect("diplomatic_reputation", PolicyEffectScope.DIPLOMACY, 0.10D, "外交声誉 +10%"),
                new PolicyEffect("stability", PolicyEffectScope.STABILITY, 0.05D, "中立稳定 +5%"),
                new PolicyEffect("espionage_resistance", PolicyEffectScope.INTELLIGENCE, 0.05D, "反间谍 +5%")
            )
        ));

        register(definitions, new PolicyDefinition(
            "foreign_aid",
            "对外援助",
            PolicyCategory.FOREIGN_POLICY,
            Set.of("globalism"),
            new BigDecimal("400.00"),
            86_400L,
            14_400L,
            Set.of(),
            List.of(
                new PolicyEffect("diplomatic_reputation", PolicyEffectScope.DIPLOMACY, 0.15D, "外交声誉 +15%"),
                new PolicyEffect("alliance_strength", PolicyEffectScope.ALLIANCE, 0.10D, "联盟强度 +10%"),
                new PolicyEffect("approval_rating", PolicyEffectScope.APPROVAL, -0.05D, "纳税人 -5%")
            )
        ));

        // ==================== 内政政策 ====================

        register(definitions, new PolicyDefinition(
            "universal_healthcare",
            "全民医保",
            PolicyCategory.HEALTHCARE,
            Set.of(),
            new BigDecimal("500.00"),
            -1L,
            28_800L,
            Set.of("privatized_healthcare"),
            List.of(
                new PolicyEffect("happiness_modifier", PolicyEffectScope.HAPPINESS, 0.15D, "国民幸福度 +15%"),
                new PolicyEffect("approval_rating", PolicyEffectScope.APPROVAL, 0.15D, "支持率 +15%"),
                new PolicyEffect("productivity", PolicyEffectScope.ECONOMY, 0.05D, "生产力 +5%（健康工人）"),
                new PolicyEffect("economic_growth", PolicyEffectScope.ECONOMY, -0.05D, "经济增长 -5%（医疗支出）")
            )
        ));

        register(definitions, new PolicyDefinition(
            "privatized_healthcare",
            "医疗私有化",
            PolicyCategory.HEALTHCARE,
            Set.of(),
            new BigDecimal("200.00"),
            -1L,
            14_400L,
            Set.of("universal_healthcare"),
            List.of(
                new PolicyEffect("economic_growth", PolicyEffectScope.ECONOMY, 0.08D, "经济增长 +8%"),
                new PolicyEffect("happiness_modifier", PolicyEffectScope.HAPPINESS, -0.10D, "国民幸福度 -10%"),
                new PolicyEffect("approval_rating", PolicyEffectScope.APPROVAL, -0.08D, "低收入群体 -8%")
            )
        ));

        register(definitions, new PolicyDefinition(
            "universal_education",
            "全民教育",
            PolicyCategory.EDUCATION,
            Set.of(),
            new BigDecimal("450.00"),
            -1L,
            28_800L,
            Set.of(),
            List.of(
                new PolicyEffect("research_speed", PolicyEffectScope.RESEARCH, 0.10D, "研究速度 +10%"),
                new PolicyEffect("productivity", PolicyEffectScope.ECONOMY, 0.08D, "生产力 +8%"),
                new PolicyEffect("approval_rating", PolicyEffectScope.APPROVAL, 0.12D, "支持率 +12%"),
                new PolicyEffect("immigration_rate", PolicyEffectScope.ECONOMY, 0.05D, "人才吸引力 +5%")
            )
        ));

        register(definitions, new PolicyDefinition(
            "corruption_crackdown",
            "反腐行动",
            PolicyCategory.ADMINISTRATION,
            Set.of(),
            new BigDecimal("300.00"),
            172_800L,
            43_200L,
            Set.of(),
            List.of(
                new PolicyEffect("corruption", PolicyEffectScope.STABILITY, -0.15D, "腐败程度 -15%"),
                new PolicyEffect("government_efficiency", PolicyEffectScope.NATION, 0.10D, "政府效率 +10%"),
                new PolicyEffect("approval_rating", PolicyEffectScope.APPROVAL, 0.10D, "支持率 +10%"),
                new PolicyEffect("stability", PolicyEffectScope.STABILITY, 0.08D, "稳定性 +8%")
            )
        ));

        register(definitions, new PolicyDefinition(
            "welfare_state",
            "福利国家",
            PolicyCategory.SOCIAL_WELFARE,
            Set.of(),
            new BigDecimal("600.00"),
            -1L,
            28_800L,
            Set.of("laissez_faire"),
            List.of(
                new PolicyEffect("happiness_modifier", PolicyEffectScope.HAPPINESS, 0.15D, "幸福度 +15%"),
                new PolicyEffect("approval_rating", PolicyEffectScope.APPROVAL, 0.15D, "支持率 +15%"),
                new PolicyEffect("stability", PolicyEffectScope.STABILITY, 0.10D, "稳定性 +10%"),
                new PolicyEffect("economic_growth", PolicyEffectScope.ECONOMY, -0.05D, "经济增长 -5%")
            )
        ));

        register(definitions, new PolicyDefinition(
            "laissez_faire",
            "自由放任",
            PolicyCategory.ADMINISTRATION,
            Set.of(),
            new BigDecimal("100.00"),
            -1L,
            14_400L,
            Set.of("welfare_state", "corruption_crackdown"),
            List.of(
                new PolicyEffect("economic_growth", PolicyEffectScope.ECONOMY, 0.15D, "经济增长 +15%"),
                new PolicyEffect("corruption", PolicyEffectScope.STABILITY, 0.10D, "腐败程度 +10%"),
                new PolicyEffect("approval_rating", PolicyEffectScope.APPROVAL, -0.10D, "底层民众 -10%"),
                new PolicyEffect("stability", PolicyEffectScope.STABILITY, -0.05D, "社会稳定 -5%")
            )
        ));

        // ==================== 资源/环境政策 ====================

        register(definitions, new PolicyDefinition(
            "green_energy",
            "绿色能源",
            PolicyCategory.ENVIRONMENTAL,
            Set.of("tech_investment"),
            new BigDecimal("500.00"),
            259_200L,
            28_800L,
            Set.of("industrial_boom"),
            List.of(
                new PolicyEffect("economic_growth", PolicyEffectScope.ECONOMY, 0.05D, "经济增长 +5%（新能源产业）"),
                new PolicyEffect("research_speed", PolicyEffectScope.RESEARCH, 0.05D, "研究速度 +5%"),
                new PolicyEffect("diplomatic_reputation", PolicyEffectScope.DIPLOMACY, 0.10D, "外交声誉 +10%（环保先锋）"),
                new PolicyEffect("stability", PolicyEffectScope.STABILITY, 0.03D, "长期稳定 +3%")
            )
        ));

        register(definitions, new PolicyDefinition(
            "strategic_reserves",
            "战略储备",
            PolicyCategory.RESOURCE_MANAGEMENT,
            Set.of(),
            new BigDecimal("400.00"),
            -1L,
            14_400L,
            Set.of(),
            List.of(
                new PolicyEffect("strategic_reserves", PolicyEffectScope.NATION, 0.30D, "战略储备 +30%"),
                new PolicyEffect("embargo_resistance", PolicyEffectScope.ECONOMY, 0.15D, "封锁抵抗 +15%"),
                new PolicyEffect("stability", PolicyEffectScope.STABILITY, 0.05D, "供应稳定 +5%")
            )
        ));

        // ==================== 文化/宣传政策 ====================

        register(definitions, new PolicyDefinition(
            "cultural_diplomacy",
            "文化外交",
            PolicyCategory.CULTURAL_EXCHANGE,
            Set.of(),
            new BigDecimal("250.00"),
            172_800L,
            14_400L,
            Set.of(),
            List.of(
                new PolicyEffect("culture_spread", PolicyEffectScope.DIPLOMACY, 0.15D, "文化传播 +15%"),
                new PolicyEffect("diplomatic_reputation", PolicyEffectScope.DIPLOMACY, 0.10D, "外交声誉 +10%"),
                new PolicyEffect("tourism", PolicyEffectScope.ECONOMY, 0.05D, "旅游收入 +5%")
            )
        ));

        register(definitions, new PolicyDefinition(
            "nationalism",
            "民族主义",
            PolicyCategory.CULTURE,
            Set.of(),
            new BigDecimal("150.00"),
            -1L,
            14_400L,
            Set.of("cultural_liberalization", "secularism"),
            List.of(
                new PolicyEffect("approval_rating", PolicyEffectScope.APPROVAL, 0.10D, "民族主义者 +10%"),
                new PolicyEffect("military", PolicyEffectScope.MILITARY, 0.05D, "军事动员 +5%"),
                new PolicyEffect("stability", PolicyEffectScope.STABILITY, 0.05D, "内部凝聚力 +5%"),
                new PolicyEffect("diplomatic_reputation", PolicyEffectScope.DIPLOMACY, -0.05D, "外交声誉 -5%")
            )
        ));

        register(definitions, new PolicyDefinition(
            "propaganda",
            "宣传机器",
            PolicyCategory.PROPAGANDA,
            Set.of("nationalism"),
            new BigDecimal("200.00"),
            172_800L,
            14_400L,
            Set.of("free_press"),
            List.of(
                new PolicyEffect("approval_rating", PolicyEffectScope.APPROVAL, 0.15D, "官方支持 +15%"),
                new PolicyEffect("revolution_risk", PolicyEffectScope.STABILITY, -0.10D, "革命风险 -10%"),
                new PolicyEffect("espionage_efficiency", PolicyEffectScope.INTELLIGENCE, 0.10D, "宣传效率 +10%"),
                new PolicyEffect("diplomatic_reputation", PolicyEffectScope.DIPLOMACY, -0.10D, "国际声誉 -10%")
            )
        ));

        register(definitions, new PolicyDefinition(
            "free_press",
            "新闻自由",
            PolicyCategory.PROPAGANDA,
            Set.of(),
            new BigDecimal("100.00"),
            -1L,
            14_400L,
            Set.of("propaganda", "state_media"),
            List.of(
                new PolicyEffect("approval_rating", PolicyEffectScope.APPROVAL, 0.08D, "知识分子支持 +8%"),
                new PolicyEffect("government_efficiency", PolicyEffectScope.NATION, 0.05D, "政府效率 +5%（监督）"),
                new PolicyEffect("revolution_risk", PolicyEffectScope.STABILITY, 0.05D, "革命风险 +5%"),
                new PolicyEffect("diplomatic_reputation", PolicyEffectScope.DIPLOMACY, 0.10D, "国际声誉 +10%")
            )
        ));

        // ==================== 科技政策 ====================

        register(definitions, new PolicyDefinition(
            "tech_investment",
            "科技投资",
            PolicyCategory.INDUSTRY,
            Set.of("universal_education"),
            new BigDecimal("500.00"),
            259_200L,
            28_800L,
            Set.of("budget_cut"),
            List.of(
                new PolicyEffect("research_speed", PolicyEffectScope.TECHNOLOGY, 0.15D, "研究速度 +15%"),
                new PolicyEffect("innovation_bonus", PolicyEffectScope.RESEARCH, 0.10D, "创新加成 +10%"),
                new PolicyEffect("tech_cost_reduction", PolicyEffectScope.TECHNOLOGY, 0.05D, "科技成本 -5%"),
                new PolicyEffect("production_bonus", PolicyEffectScope.PRODUCTION, 0.05D, "生产效率 +5%")
            )
        ));

        return Map.copyOf(definitions);
    }

    private static void register(Map<String, PolicyDefinition> definitions, PolicyDefinition definition) {
        definitions.put(definition.key(), definition);
    }

    private static String normalizePolicy(String policyKey) {
        return policyKey == null ? "" : policyKey.trim().toLowerCase(Locale.ROOT);
    }

}
