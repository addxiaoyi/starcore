package dev.starcore.starcore.module.prosperity;

import dev.starcore.starcore.core.event.StarCoreEventBus;
import dev.starcore.starcore.core.persistence.PersistenceService;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.prosperity.event.ProsperityChangedEvent;
import org.bukkit.plugin.Plugin;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 繁荣度服务实现
 * 管理国家的繁荣度数据、计算和修改
 */
public class ProsperityServiceImpl implements ProsperityService {
    private final Plugin plugin;
    private final NationService nationService;
    private final PersistenceService persistenceService;
    private final StarCoreEventBus eventBus;
    private final ProsperityConfig config;

    // 内存中的繁荣度数据: nationId -> NationProsperity
    private final Map<NationId, NationProsperity> prosperityMap = new ConcurrentHashMap<>();

    // 区块贡献数据: "world:x:z" -> contribution
    private final Map<String, Double> chunkContributions = new ConcurrentHashMap<>();

    // 活跃度数据: nationId -> activityScore
    private final Map<NationId, AtomicInteger> activityScores = new ConcurrentHashMap<>();

    // 活跃度记录: nationId -> playerId -> lastActivity
    private final Map<NationId, Map<UUID, Instant>> activityLog = new ConcurrentHashMap<>();

    // 事件历史: nationId -> events
    private final Map<NationId, List<ProsperityEvent>> eventHistory = new ConcurrentHashMap<>();

    private static final int MAX_EVENT_HISTORY = 50;
    private static final int MAX_CHUNK_CONTRIBUTIONS = 10000;

    public ProsperityServiceImpl(
            Plugin plugin,
            NationService nationService,
            PersistenceService persistenceService,
            StarCoreEventBus eventBus,
            ProsperityConfig config
    ) {
        this.plugin = plugin;
        this.nationService = nationService;
        this.persistenceService = persistenceService;
        this.eventBus = eventBus;
        this.config = config;
    }

    @Override
    public NationProsperity getProsperity(NationId nationId) {
        return prosperityMap.computeIfAbsent(nationId, id -> NationProsperity.defaultFor(id));
    }

    @Override
    public double getProsperityValue(NationId nationId) {
        return getProsperity(nationId).prosperity();
    }

    @Override
    public int getProsperityLevel(NationId nationId) {
        double prosperity = getProsperityValue(nationId);
        return config.calculateLevel(prosperity);
    }

    @Override
    public double modifyProsperity(NationId nationId, double amount, String reason) {
        // 使用 compute 原子更新繁荣度
        NationProsperity current = prosperityMap.get(nationId);
        if (current == null) {
            current = new NationProsperity(nationId, config.initialProsperity(), 1, 0.0, Instant.now(), Instant.now(), Map.of());
        }
        double oldValue = current.prosperity();
        double newValue = Math.max(config.minProsperity(),
                Math.min(config.maxProsperity(), oldValue + amount));

        NationProsperity updated = new NationProsperity(
                nationId,
                newValue,
                config.calculateLevel(newValue),
                current.activityScore(),
                current.lastActivity(),
                Instant.now(),
                current.chunkContributions()
        );

        prosperityMap.put(nationId, updated);

        // 记录事件
        recordEvent(nationId, determineEventType(amount), reason, amount);

        // 触发事件
        if (eventBus != null && Math.abs(newValue - oldValue) >= 0.1) {
            eventBus.publish(new ProsperityChangedEvent(nationId, oldValue, newValue, reason, java.time.Instant.now()));
        }

        // 异步保存
        saveAsync();

        return newValue;
    }

    private String determineEventType(double amount) {
        if (amount > 0) {
            return "positive";
        } else if (amount < 0) {
            return "negative";
        }
        return "neutral";
    }

    @Override
    public void setProsperity(NationId nationId, double value) {
        double clampedValue = Math.max(config.minProsperity(),
                Math.min(config.maxProsperity(), value));
        NationProsperity current = getProsperity(nationId);

        NationProsperity updated = new NationProsperity(
                nationId,
                clampedValue,
                config.calculateLevel(clampedValue),
                current.activityScore(),
                current.lastActivity(),
                Instant.now(),
                current.chunkContributions()
        );

        prosperityMap.put(nationId, updated);
        recordEvent(nationId, "admin_set", "管理员设置繁荣度", clampedValue - current.prosperity());
        saveAsync();
    }

    @Override
    public void addChunkContribution(UUID nationId, String chunkWorld, int chunkX, int chunkZ, double amount) {
        String key = chunkKey(chunkWorld, chunkX, chunkZ);
        // 原子更新贡献值（使用 compute 避免竞态）
        double newValue = chunkContributions.compute(key, (k, current) -> {
            double val = current != null ? current : 0.0;
            double updated = val + amount;
            return Math.max(0, Math.min(10.0, updated));
        });

        // 限制总贡献数（使用 compute 原子清理）
        if (chunkContributions.size() > MAX_CHUNK_CONTRIBUTIONS) {
            // 找到贡献值最低的条目移除（确定性清理策略）
            String toRemove = chunkContributions.entrySet().stream()
                    .min(Comparator.comparingDouble(Map.Entry::getValue))
                    .map(Map.Entry::getKey)
                    .orElse(null);
            if (toRemove != null) {
                chunkContributions.remove(toRemove);
            }
        }

        // 更新国家繁荣度
        NationId nid = NationId.of(nationId);
        if (nationService.nationById(nid).isPresent()) {
            modifyProsperity(nid, amount * config.buildingBoostAmount() * 0.1, "建筑贡献: " + key);
        }
    }

    @Override
    public double getChunkContribution(String chunkWorld, int chunkX, int chunkZ) {
        return chunkContributions.getOrDefault(chunkKey(chunkWorld, chunkX, chunkZ), 0.0);
    }

    @Override
    public void recordEvent(NationId nationId, String eventType, String description, double amount) {
        ProsperityEvent event = new ProsperityEvent(
                UUID.randomUUID(),
                nationId,
                eventType,
                description,
                amount,
                Instant.now()
        );

        List<ProsperityEvent> events = eventHistory.computeIfAbsent(nationId, k -> new ArrayList<>());
        synchronized (events) {
            events.add(event);
            // 限制历史记录数量
            while (events.size() > MAX_EVENT_HISTORY) {
                events.remove(0);
            }
        }
    }

    @Override
    public List<ProsperityEvent> getRecentEvents(NationId nationId, int limit) {
        List<ProsperityEvent> events = eventHistory.getOrDefault(nationId, List.of());
        synchronized (events) {
            int size = events.size();
            if (limit >= size) {
                return new ArrayList<>(events);
            }
            return new ArrayList<>(events.subList(size - limit, size));
        }
    }

    @Override
    public double getBonusMultiplier(NationId nationId) {
        int level = getProsperityLevel(nationId);
        // 每级增加5%加成
        return 1.0 + (level - 1) * 0.05;
    }

    @Override
    public double getTaxBonus(NationId nationId) {
        int level = getProsperityLevel(nationId);
        // 每级增加2%税收
        return 1.0 + (level - 1) * config.taxBonusPerLevel();
    }

    @Override
    public double getResourceBonus(NationId nationId) {
        int level = getProsperityLevel(nationId);
        // 每级增加5%资源产出
        return 1.0 + (level - 1) * config.resourceBonusPerLevel();
    }

    @Override
    public void processDecay(NationId nationId) {
        NationProsperity current = getProsperity(nationId);

        // 如果繁荣度低于阈值，不衰减
        if (current.prosperity() < config.decayThreshold()) {
            return;
        }

        // 计算活跃度衰减惩罚
        double inactivityMultiplier = 1.0;
        if (current.lastActivity() != null) {
            long hoursSinceActivity = java.time.Duration.between(current.lastActivity(), Instant.now()).toHours();
            if (hoursSinceActivity > 24) {
                inactivityMultiplier = config.inactivityPenaltyMultiplier();
            }
        }

        // 计算衰减量
        double decayAmount = config.dailyDecayRate() * inactivityMultiplier;

        modifyProsperity(nationId, -decayAmount, "每日衰减");
    }

    @Override
    public void processAllDecay() {
        for (NationId nationId : nationService.nations().stream()
                .map(n -> n.id())
                .collect(Collectors.toList())) {
            processDecay(nationId);
        }
    }

    @Override
    public void refreshProsperity(NationId nationId) {
        NationProsperity current = getProsperity(nationId);

        // 重新计算活跃度分数
        AtomicInteger score = activityScores.get(nationId);
        int activityScore = score != null ? score.get() : 0;

        NationProsperity refreshed = new NationProsperity(
                nationId,
                current.prosperity(),
                config.calculateLevel(current.prosperity()),
                activityScore,
                current.lastActivity(),
                Instant.now(),
                current.chunkContributions()
        );

        prosperityMap.put(nationId, refreshed);
    }

    @Override
    public void refreshAllProsperity() {
        for (NationId nationId : prosperityMap.keySet()) {
            refreshProsperity(nationId);
        }
    }

    @Override
    public void recordActivity(NationId nationId, UUID playerId, String activityType) {
        // 更新活跃度分数
        AtomicInteger score = activityScores.computeIfAbsent(nationId, k -> new AtomicInteger(0));
        int increment = switch (activityType.toLowerCase()) {
            case "build" -> 3;
            case "trade" -> 2;
            case "combat" -> 2;
            case "interact" -> 1;
            default -> 1;
        };
        score.addAndGet(increment);

        // 限制活跃度分数
        if (score.get() > 1000) {
            score.set(1000);
        }

        // 记录活跃时间
        Map<UUID, Instant> playerActivities = activityLog.computeIfAbsent(nationId, k -> new ConcurrentHashMap<>());
        playerActivities.put(playerId, Instant.now());

        // 更新最后活跃时间
        NationProsperity current = getProsperity(nationId);
        NationProsperity updated = new NationProsperity(
                nationId,
                current.prosperity(),
                current.level(),
                score.get(),
                Instant.now(),
                Instant.now(),
                current.chunkContributions()
        );
        prosperityMap.put(nationId, updated);

        // 增加繁荣度
        double boost = increment * config.activityBoostAmount() * 0.1;
        modifyProsperity(nationId, boost, "活跃度提升: " + activityType);
    }

    @Override
    public int getActivityScore(NationId nationId) {
        AtomicInteger score = activityScores.get(nationId);
        return score != null ? score.get() : 0;
    }

    @Override
    public void saveAll() {
        if (persistenceService == null) {
            return;
        }

        // 保存繁荣度数据
        Properties prosperityProps = new Properties();
        for (Map.Entry<NationId, NationProsperity> entry : prosperityMap.entrySet()) {
            NationProsperity p = entry.getValue();
            String key = entry.getKey().toString();
            String value = String.format("%f,%d,%d,%d",
                    p.prosperity(),
                    p.activityScore(),
                    p.lastActivity().toEpochMilli(),
                    p.lastUpdate().toEpochMilli());
            prosperityProps.setProperty(key, value);
        }
        persistenceService.saveProperties("prosperity", "nation_prosperity.properties", prosperityProps);

        // 保存区块贡献
        Properties chunkProps = new Properties();
        chunkProps.putAll(chunkContributions);
        persistenceService.saveProperties("prosperity", "chunk_contributions.properties", chunkProps);
    }

    private void saveAsync() {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, this::saveAll);
    }

    @Override
    public List<Map.Entry<NationId, Double>> getRanking() {
        return prosperityMap.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue().prosperity(), a.getValue().prosperity()))
                .map(e -> Map.entry(e.getKey(), e.getValue().prosperity()))
                .toList();
    }

    /**
     * 初始化服务，加载持久化数据
     */
    public void initialize() {
        loadData();
        initializeExistingNations();
    }

    private void loadData() {
        if (persistenceService == null) {
            return;
        }

        // 加载繁荣度数据
        Properties prosperityProps = persistenceService.loadProperties("prosperity", "nation_prosperity.properties");
        for (String key : prosperityProps.stringPropertyNames()) {
            try {
                String value = prosperityProps.getProperty(key);
                String[] parts = value.split(",");
                if (parts.length >= 4) {
                    NationId nationId = NationId.of(UUID.fromString(key));
                    double prosperity = Double.parseDouble(parts[0]);
                    int activityScore = Integer.parseInt(parts[1]);
                    Instant lastActivity = Instant.ofEpochMilli(Long.parseLong(parts[2]));
                    Instant lastUpdate = Instant.ofEpochMilli(Long.parseLong(parts[3]));

                    NationProsperity p = new NationProsperity(
                            nationId, prosperity, config.calculateLevel(prosperity),
                            activityScore, lastActivity, lastUpdate, Map.of()
                    );
                    prosperityMap.put(nationId, p);
                    activityScores.put(nationId, new AtomicInteger(activityScore));
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to parse prosperity data for key: " + key);
            }
        }

        // 加载区块贡献
        Properties chunkProps = persistenceService.loadProperties("prosperity", "chunk_contributions.properties");
        for (String key : chunkProps.stringPropertyNames()) {
            try {
                chunkContributions.put(key, Double.parseDouble(chunkProps.getProperty(key)));
            } catch (Exception e) {
                // ignore
            }
        }
    }

    private void initializeExistingNations() {
        // 为所有现有国家初始化繁荣度数据
        for (var nation : nationService.nations()) {
            if (!prosperityMap.containsKey(nation.id())) {
                prosperityMap.put(nation.id(), NationProsperity.defaultFor(nation.id()));
                activityScores.put(nation.id(), new AtomicInteger(0));
            }
        }
    }

    /**
     * 关闭服务，保存所有数据
     */
    public void shutdown() {
        saveAll();
        prosperityMap.clear();
        chunkContributions.clear();
        activityScores.clear();
        activityLog.clear();
        eventHistory.clear();
    }

    private String chunkKey(String world, int x, int z) {
        return world + ":" + x + ":" + z;
    }

    /**
     * 获取配置
     */
    public ProsperityConfig getConfig() {
        return config;
    }
}
