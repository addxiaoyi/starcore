package dev.starcore.starcore.module.army.espionage;

import java.util.concurrent.ThreadLocalRandom;
import dev.starcore.starcore.core.persistence.PersistenceService;
import dev.starcore.starcore.foundation.economy.EconomyService;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.army.espionage.model.*;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.treasury.TreasuryService;
import dev.starcore.starcore.module.nation.model.NationId;
import org.bukkit.plugin.Plugin;

import java.time.Instant;
import java.util.*;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * 间谍核心服务实现
 */
public final class EspionageServiceImpl implements EspionageService {
    private static final String PERSISTENCE_NAMESPACE = "espionage";
    private static final String SPY_STATE_FILE = "spies.dat";
    private static final String OPERATION_HISTORY_FILE = "operations.dat";

    private final Plugin plugin;
    private final NationService nationService;
    private final TreasuryService treasuryService;
    private final EconomyService economyService;
    private final MessageService messages;
    private final PersistenceService persistenceService;
    private final EspionageConfig config;

    // 所有间谍
    private final ConcurrentHashMap<UUID, Spy> spies = new ConcurrentHashMap<>();
    // 国家间谍索引
    private final ConcurrentHashMap<UUID, Set<UUID>> nationSpies = new ConcurrentHashMap<>();
    // 进行中的行动
    private final ConcurrentHashMap<UUID, EspionageOperation> activeOperations = new ConcurrentHashMap<>();
    // 行动历史 - 使用 CopyOnWriteArrayList 支持并发读写
    private final List<EspionageOperation> operationHistory = new CopyOnWriteArrayList<>();
    // 行动冷却记录
    private final ConcurrentHashMap<UUID, Long> operationCooldowns = new ConcurrentHashMap<>();
    // 国家反间谍等级
    private final ConcurrentHashMap<UUID, Integer> counterIntelligenceLevels = new ConcurrentHashMap<>();

    public EspionageServiceImpl(
            Plugin plugin,
            NationService nationService,
            TreasuryService treasuryService,
            EconomyService economyService,
            MessageService messages,
            EspionageConfig config,
            PersistenceService persistenceService
    ) {
        this.plugin = plugin;
        this.nationService = nationService;
        this.treasuryService = treasuryService;
        this.economyService = economyService;
        this.messages = messages;
        this.config = config;
        this.persistenceService = persistenceService;

        // 加载已有数据
        if (persistenceService != null) {
            loadSpies();
            loadOperations();
        }

        // 初始化反间谍等级
        initializeCounterIntelligence();

        // 启动定时任务
        startPeriodicTasks();
    }

    private void initializeCounterIntelligence() {
        // 从国家服务获取所有国家，初始化反间谍等级
        for (var nation : nationService.nations()) {
            int level = calculateCounterIntelligenceLevel(nation.id().value());
            counterIntelligenceLevels.put(nation.id().value(), level);
        }
    }

    private int calculateCounterIntelligenceLevel(UUID nationId) {
        // 基于国家等级和科技计算反间谍等级
        int level = nationService.levelOf(new NationId(nationId));
        // 可以扩展：从科技服务获取额外加成
        return Math.min(level, 10); // 最高10级
    }

    // ==================== 间谍管理实现 ====================

    @Override
    public Spy trainSpy(UUID nationId, String nationName, UUID trainerId, SpyType type) {
        // 检查限制
        Set<UUID> nationSpyIds = nationSpies.getOrDefault(nationId, Collections.emptySet());
        if (nationSpyIds.size() >= config.maxSpiesPerNation()) {
            throw new IllegalStateException("espionage.error.max-spies-reached");
        }

        // 计算训练成本
        double cost = type.trainingCost();
        NationId nationIdObj = new NationId(nationId);

        if (treasuryService.balance(nationIdObj).doubleValue() < cost) {
            throw new IllegalStateException("espionage.error.insufficient-funds");
        }

        if (!treasuryService.withdraw(nationIdObj, java.math.BigDecimal.valueOf(cost))) {
            throw new IllegalStateException("espionage.error.withdraw-failed");
        }

        // 创建间谍
        Spy spy = Spy.create(nationId, nationName, trainerId, type);

        // 注册
        spies.put(spy.id(), spy);
        nationSpies.computeIfAbsent(nationId, k -> ConcurrentHashMap.newKeySet()).add(spy.id());

        // 持久化
        persistSpy(spy);

        return spy;
    }

    @Override
    public Optional<Spy> getSpy(UUID spyId) {
        return Optional.ofNullable(spies.get(spyId));
    }

    @Override
    public List<Spy> getNationSpies(UUID nationId) {
        Set<UUID> spyIds = nationSpies.getOrDefault(nationId, Collections.emptySet());
        return spyIds.stream()
                .map(spies::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public void dismissSpy(UUID spyId) {
        Spy spy = spies.remove(spyId);
        if (spy != null) {
            Set<UUID> nationSpyIds = nationSpies.get(spy.ownerId());
            if (nationSpyIds != null) {
                nationSpyIds.remove(spyId);
            }
            removePersistedSpy(spyId);
        }
    }

    @Override
    public int getSpyCount(UUID nationId) {
        return nationSpies.getOrDefault(nationId, Collections.emptySet()).size();
    }

    // ==================== 行动管理实现 ====================

    @Override
    public EspionageOperation startOperation(UUID spyId, UUID targetNationId, String targetNationName, OperationType type) {
        Spy spy = spies.get(spyId);
        if (spy == null) {
            throw new IllegalArgumentException("espionage.error.spy-not-found");
        }

        if (!spy.canOperate()) {
            throw new IllegalStateException("espionage.error.spy-cannot-operate");
        }

        // 检查行动冷却
        UUID nationId = spy.ownerId();
        Long lastOperation = operationCooldowns.get(nationId);
        if (lastOperation != null) {
            long cooldownMs = config.operationCooldownMinutes() * 60 * 1000L;
            if (System.currentTimeMillis() - lastOperation < cooldownMs) {
                throw new IllegalStateException("espionage.error.operation-in-cooldown");
            }
        }

        // 检查活跃行动限制
        if (getActiveOperationCount(nationId) >= config.maxActiveOperationsPerNation()) {
            throw new IllegalStateException("espionage.error.max-operations-reached");
        }

        // 检查是否只允许战争期间
        if (config.allowWarOnly()) {
            boolean atWar = nationService.atWar(new NationId(nationId), new NationId(targetNationId));
            if (!atWar) {
                throw new IllegalStateException("espionage.error.war-only");
            }
        }

        // 检查经验要求
        if (spy.experience() < type.requiredExp()) {
            throw new IllegalStateException("espionage.error.insufficient-experience");
        }

        // 计算行动成本
        double cost = type.cost();
        NationId nationIdObj = new NationId(nationId);

        if (treasuryService.balance(nationIdObj).doubleValue() < cost) {
            throw new IllegalStateException("espionage.error.insufficient-funds");
        }

        if (!treasuryService.withdraw(nationIdObj, java.math.BigDecimal.valueOf(cost))) {
            throw new IllegalStateException("espionage.error.withdraw-failed");
        }

        // 创建行动
        long durationTicks = spy.missionDurationTicks();
        EspionageOperation operation = EspionageOperation.start(
                spyId,
                nationId, spy.ownerName(),
                targetNationId, targetNationName,
                type, cost, durationTicks
        );

        // 注册行动
        activeOperations.put(operation.id(), operation);

        // 记录冷却
        operationCooldowns.put(nationId, System.currentTimeMillis());

        return operation;
    }

    @Override
    public List<EspionageOperation> getActiveOperations() {
        return new ArrayList<>(activeOperations.values());
    }

    @Override
    public List<EspionageOperation> getNationOperationHistory(UUID nationId) {
        return operationHistory.stream()
                .filter(op -> op.sourceNationId().equals(nationId))
                .collect(Collectors.toList());
    }

    @Override
    public int getActiveOperationCount(UUID nationId) {
        return (int) activeOperations.values().stream()
                .filter(op -> op.sourceNationId().equals(nationId))
                .count();
    }

    // ==================== 反间谍实现 ====================

    @Override
    public boolean detectSpy(UUID spyId, UUID targetNationId) {
        Spy spy = spies.get(spyId);
        if (spy == null) {
            return true; // 间谍不存在，视为被发现
        }

        // 获取目标国家反间谍等级
        int targetLevel = getCounterIntelligenceLevel(targetNationId);

        // 计算发现概率
        double detectionChance = config.baseDetectionChance() / 100.0;
        double spyStealth = spy.effectiveStealth();
        double levelPenalty = targetLevel * 0.05;
        double operationRisk = spy.type().stealthBonus() * 0.1;

        double finalChance = detectionChance + levelPenalty + operationRisk - spyStealth;
        finalChance = Math.max(0.05, Math.min(0.95, finalChance));

        return ThreadLocalRandom.current().nextDouble() < finalChance;
    }

    @Override
    public int getCounterIntelligenceLevel(UUID nationId) {
        return counterIntelligenceLevels.getOrDefault(nationId, 1);
    }

    // ==================== 行动结果实现 ====================

    @Override
    public Optional<String> getOperationReport(UUID operationId) {
        EspionageOperation op = activeOperations.get(operationId);
        if (op == null) {
            // 查找历史
            return operationHistory.stream()
                    .filter(o -> o.id().equals(operationId))
                    .map(EspionageOperation::report)
                    .findFirst();
        }
        return Optional.ofNullable(op.report());
    }

    @Override
    public Optional<EspionageOperation> getOperation(UUID operationId) {
        EspionageOperation op = activeOperations.get(operationId);
        if (op != null) {
            return Optional.of(op);
        }
        return operationHistory.stream()
                .filter(o -> o.id().equals(operationId))
                .findFirst();
    }

    @Override
    public EspionageConfig getConfig() {
        return config;
    }

    // ==================== 定时任务 ====================

    private void startPeriodicTasks() {
        // 每分钟检查一次行动完成情况
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            checkOperationsComplete();
        }, 20L * 60, 20L * 60);

        // 每5分钟保存一次数据
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            saveAllSpies();
            saveOperations();
        }, 20L * 60 * 5, 20L * 60 * 5);

        // 每日检查：维护和士气
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            performDailyMaintenance();
        }, 20L * 60 * 60 * 24, 20L * 60 * 60 * 24);
    }

    private void checkOperationsComplete() {
        Instant now = Instant.now();
        List<EspionageOperation> toComplete = new ArrayList<>();

        for (EspionageOperation op : activeOperations.values()) {
            if (op.getRemainingTicks(now) <= 0) {
                toComplete.add(op);
            }
        }

        for (EspionageOperation op : toComplete) {
            completeOperation(op);
        }
    }

    private void completeOperation(EspionageOperation operation) {
        activeOperations.remove(operation.id());

        Spy spy = spies.get(operation.spyId());
        if (spy == null) {
            operation.fail("Spy no longer exists");
            operationHistory.add(operation);
            return;
        }

        // 检查是否被发现
        boolean detected = detectSpy(operation.spyId(), operation.targetNationId());

        if (detected) {
            // 行动暴露
            operation.fail("Your spy was detected by " + operation.targetNationName() + "!");
            operation.setStatus(OperationStatus.EXPOSED);
            spy.missionFailed();

            // 给目标国发送警报
            sendDetectionAlert(operation);
        } else {
            // 行动成功
            boolean success = ThreadLocalRandom.current().nextDouble() < spy.calculateSuccessChance(operation);
            if (success) {
                String report = generateSuccessReport(operation, spy);
                operation.complete(true, report, calculateReward(operation));
                spy.missionSucceeded();
                spy.addExperience(calculateExperienceGain(operation));
            } else {
                operation.fail("Operation failed - partial success only");
                operation.setSuccess(false);
                operation.setStatus(OperationStatus.COMPLETED);
                spy.missionFailed();
                spy.addExperience(5);
            }
        }

        operationHistory.add(operation);
        persistSpy(spy);
    }

    private String generateSuccessReport(EspionageOperation op, Spy spy) {
        StringBuilder report = new StringBuilder();
        report.append("Operation '").append(op.type().key()).append("' against ")
                .append(op.targetNationName()).append(" was successful!\n");

        switch (op.type()) {
            case RECON -> report.append("- Collected basic intelligence on target nation");
            case STEAL_RESOURCES -> {
                double stolen = (double) op.reward() * config.resourceStealRate();
                report.append(String.format("- Estimated treasury: %s coins", formatAmount(stolen)));
            }
            case SABOTAGE -> report.append("- Sabotaged target facilities");
            case ASSASSINATE -> report.append("- High-value target eliminated");
            case INCITE_DEFECTION -> report.append("- Successfully recruited defector");
            case STEAL_TECHNOLOGY -> report.append("- Acquired technology research data");
            case DIPLOMATIC_SABOTAGE -> report.append("- Damaged target diplomatic relations");
            case INFILTRATE -> report.append("- Spy network established in target nation");
        }

        return report.toString();
    }

    private Object calculateReward(EspionageOperation operation) {
        // 根据行动类型计算奖励
        return switch (operation.type()) {
            case STEAL_RESOURCES -> {
                var balance = treasuryService.balance(new NationId(operation.targetNationId()));
                yield balance.doubleValue() * config.resourceStealRate();
            }
            case STEAL_TECHNOLOGY -> "tech_data";
            case RECON -> Map.of(
                    "treasury", treasuryService.balance(new NationId(operation.targetNationId())),
                    "memberCount", nationService.nationById(new NationId(operation.targetNationId()))
                            .map(n -> n.members().size()).orElse(0),
                    "claims", nationService.claimCount(new NationId(operation.targetNationId()))
            );
            default -> "success";
        };
    }

    private int calculateExperienceGain(EspionageOperation operation) {
        return operation.type().difficulty() * 10;
    }

    private void sendDetectionAlert(EspionageOperation operation) {
        // 记录警告日志，实际通知通过事件系统处理
        plugin.getLogger().info("[Espionage] Spy detected! Source: " +
                operation.sourceNationName() + " -> Target: " + operation.targetNationName());
    }

    private void performDailyMaintenance() {
        // 每日士气维护
        for (Spy spy : spies.values()) {
            spy.dailyMaintenanceCheck();

            if (spy.isDead()) {
                dismissSpy(spy.id());
            } else {
                persistSpy(spy);
            }
        }

        // 更新反间谍等级
        for (UUID nationId : counterIntelligenceLevels.keySet()) {
            counterIntelligenceLevels.put(nationId, calculateCounterIntelligenceLevel(nationId));
        }
    }

    // ==================== 持久化 ====================

    private void loadSpies() {
        if (persistenceService == null) {
            return;
        }

        try {
            var props = persistenceService.loadProperties(PERSISTENCE_NAMESPACE, SPY_STATE_FILE);
            for (String key : props.stringPropertyNames()) {
                String json = props.getProperty(key);
                try {
                    Spy spy = decodeSpy(json);
                    if (spy != null) {
                        spies.put(spy.id(), spy);
                        nationSpies.computeIfAbsent(spy.ownerId(), k -> ConcurrentHashMap.newKeySet()).add(spy.id());
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to load spy from key " + key + ": " + e.getMessage());
                }
            }
            plugin.getLogger().info("Loaded " + spies.size() + " spies from persistence");
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load spies: " + e.getMessage());
        }
    }

    private void loadOperations() {
        if (persistenceService == null) {
            return;
        }

        try {
            var props = persistenceService.loadProperties(PERSISTENCE_NAMESPACE, OPERATION_HISTORY_FILE);
            for (String key : props.stringPropertyNames()) {
                String json = props.getProperty(key);
                try {
                    EspionageOperation op = decodeOperation(json);
                    if (op != null && !op.isCompleted()) {
                        activeOperations.put(op.id(), op);
                    }
                    operationHistory.add(op);
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to load operation from key " + key + ": " + e.getMessage());
                }
            }
            plugin.getLogger().info("Loaded " + operationHistory.size() + " operations, " + activeOperations.size() + " active");
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load operations: " + e.getMessage());
        }
    }

    private void saveAllSpies() {
        if (persistenceService == null) {
            return;
        }

        try {
            var props = new java.util.Properties();
            for (Map.Entry<UUID, Spy> entry : spies.entrySet()) {
                props.setProperty(entry.getKey().toString(), encodeSpy(entry.getValue()));
            }
            persistenceService.savePropertiesAsync(PERSISTENCE_NAMESPACE, SPY_STATE_FILE, props);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to save spies: " + e.getMessage());
        }
    }

    private void saveOperations() {
        if (persistenceService == null) {
            return;
        }

        try {
            var props = new java.util.Properties();
            int index = 0;
            for (EspionageOperation op : operationHistory) {
                props.setProperty(op.id().toString(), encodeOperation(op));
                index++;
            }
            persistenceService.savePropertiesAsync(PERSISTENCE_NAMESPACE, OPERATION_HISTORY_FILE, props);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to save operations: " + e.getMessage());
        }
    }

    private void persistSpy(Spy spy) {
        if (persistenceService == null) {
            return;
        }

        try {
            var props = persistenceService.loadProperties(PERSISTENCE_NAMESPACE, SPY_STATE_FILE);
            props.setProperty(spy.id().toString(), encodeSpy(spy));
            persistenceService.savePropertiesAsync(PERSISTENCE_NAMESPACE, SPY_STATE_FILE, props);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to persist spy " + spy.id() + ": " + e.getMessage());
        }
    }

    private void removePersistedSpy(UUID spyId) {
        if (persistenceService == null) {
            return;
        }

        try {
            var props = persistenceService.loadProperties(PERSISTENCE_NAMESPACE, SPY_STATE_FILE);
            props.remove(spyId.toString());
            persistenceService.savePropertiesAsync(PERSISTENCE_NAMESPACE, SPY_STATE_FILE, props);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to remove spy " + spyId + " from persistence: " + e.getMessage());
        }
    }

    // ==================== 编解码 ====================

    private String encodeSpy(Spy spy) {
        return String.format("%s|%s|%s|%s|%s|%d|%d|%d|%s|%s|%.2f",
                spy.id(),
                spy.ownerId(),
                spy.ownerName(),
                spy.trainerId(),
                spy.type().name(),
                spy.experience(),
                spy.missionsCompleted(),
                spy.missionsFailed(),
                spy.recruitedAt().toString(),
                spy.lastMissionAt() != null ? spy.lastMissionAt().toString() : "",
                spy.morale()
        );
    }

    private Spy decodeSpy(String data) {
        try {
            String[] parts = data.split("\\|");
            if (parts.length < 11) return null;

            return new Spy(
                    UUID.fromString(parts[0]),
                    UUID.fromString(parts[1]),
                    parts[2],
                    UUID.fromString(parts[3]),
                    SpyType.valueOf(parts[4]),
                    Integer.parseInt(parts[5]),
                    Integer.parseInt(parts[6]),
                    Integer.parseInt(parts[7]),
                    Instant.parse(parts[8]),
                    parts[9].isEmpty() ? null : Instant.parse(parts[9]),
                    Double.parseDouble(parts[10])
            );
        } catch (Exception e) {
            return null;
        }
    }

    private String encodeOperation(EspionageOperation op) {
        return String.format("%s|%s|%s|%s|%s|%s|%s|%d|%.2f|%s|%d|%b|%s|%s|%s|%b|%s|%s",
                op.id(),
                op.spyId(),
                op.sourceNationId(),
                op.sourceNationName(),
                op.targetNationId(),
                op.targetNationName(),
                op.type().name(),
                op.difficulty(),
                op.cost(),
                op.startTime().toString(),
                op.durationTicks(),
                op.detected(),
                op.status().name(),
                op.endTime() != null ? op.endTime().toString() : "",
                op.report() != null ? op.report() : "",
                op.success(),
                op.reward() != null ? op.reward().toString() : ""
        );
    }

    private EspionageOperation decodeOperation(String data) {
        try {
            String[] parts = data.split("\\|");
            if (parts.length < 17) return null;

            return new EspionageOperation(
                    UUID.fromString(parts[0]),
                    UUID.fromString(parts[1]),
                    UUID.fromString(parts[2]),
                    parts[3],
                    UUID.fromString(parts[4]),
                    parts[5],
                    OperationType.valueOf(parts[6]),
                    Integer.parseInt(parts[7]),
                    Double.parseDouble(parts[8]),
                    Instant.parse(parts[9]),
                    Long.parseLong(parts[10]),
                    Boolean.parseBoolean(parts[11]),
                    OperationStatus.valueOf(parts[12]),
                    parts[13].isEmpty() ? null : Instant.parse(parts[13]),
                    Boolean.parseBoolean(parts[15]),
                    parts[14],
                    parts.length > 16 && !parts[16].isEmpty() ? parts[16] : null
            );
        } catch (Exception e) {
            return null;
        }
    }

    private String formatAmount(double amount) {
        if (amount >= 1_000_000) {
            return String.format("%.1fM", amount / 1_000_000);
        } else if (amount >= 1_000) {
            return String.format("%.1fK", amount / 1_000);
        }
        return String.format("%.2f", amount);
    }
}
