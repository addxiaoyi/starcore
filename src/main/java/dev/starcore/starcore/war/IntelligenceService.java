package dev.starcore.starcore.war;
import java.util.concurrent.ThreadLocalRandom;
import java.util.Optional;

import dev.starcore.starcore.module.nation.model.NationId;
import org.bukkit.plugin.Plugin;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * 情报服务
 * 管理间谍、情报收集和反间谍活动
 */
public final class IntelligenceService {
    private final Plugin plugin;
    private final WarServiceImpl warService;
    private final Logger logger;
    private final IntelligenceConfig config;

    // 间谍
    private final ConcurrentHashMap<UUID, Spy> spies = new ConcurrentHashMap<>();
    // 国家的间谍索引
    private final ConcurrentHashMap<NationId, Set<UUID>> nationSpies = new ConcurrentHashMap<>();
    // 情报
    private final ConcurrentHashMap<UUID, Intelligence> intelligence = new ConcurrentHashMap<>();
    // 国家的情报索引
    private final ConcurrentHashMap<NationId, Set<UUID>> nationIntelligence = new ConcurrentHashMap<>();
    // 活跃任务
    private final ConcurrentHashMap<UUID, SpyMissionExecution> activeMissions = new ConcurrentHashMap<>();

    public IntelligenceService(
        Plugin plugin,
        WarServiceImpl warService,
        IntelligenceConfig config
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.warService = Objects.requireNonNull(warService, "warService");
        this.config = Objects.requireNonNull(config, "config");
        this.logger = plugin.getLogger();

        startPeriodicTasks();
    }

    /**
     * 招募间谍
     */
    public Spy recruitSpy(NationId ownerNation, NationId targetNation, String codeName) {
        Objects.requireNonNull(ownerNation, "ownerNation");
        Objects.requireNonNull(targetNation, "targetNation");
        Objects.requireNonNull(codeName, "codeName");

        if (ownerNation.equals(targetNation)) {
            throw new IllegalArgumentException("Cannot spy on own nation");
        }

        // 检查间谍数量限制
        Set<UUID> existingSpies = nationSpies.getOrDefault(ownerNation, Collections.emptySet());
        if (existingSpies.size() >= config.maxSpiesPerNation()) {
            throw new IllegalStateException("Max spy limit reached");
        }

        // 创建间谍
        int initialSkill = 3 + new Random().nextInt(3); // 3-5
        Spy spy = new Spy(
            UUID.randomUUID(),
            ownerNation,
            targetNation,
            codeName,
            initialSkill,
            Instant.now()
        );

        spies.put(spy.id(), spy);
        nationSpies.computeIfAbsent(ownerNation, k -> ConcurrentHashMap.newKeySet())
            .add(spy.id());

        logger.info(String.format("Spy recruited: %s (Nation: %s -> %s, Skill: %d)",
            codeName, ownerNation, targetNation, initialSkill));

        return spy;
    }

    /**
     * 解散间谍
     */
    public void dismissSpy(UUID spyId) {
        Spy spy = spies.remove(spyId);
        if (spy != null) {
            Set<UUID> spySet = nationSpies.get(spy.ownerNation());
            if (spySet != null) {
                spySet.remove(spyId);
            }

            // 取消活跃任务
            activeMissions.remove(spyId);

            logger.info(String.format("Spy dismissed: %s", spy.codeName()));
        }
    }

    /**
     * 获取间谍
     */
    public Optional<Spy> getSpy(UUID spyId) {
        return Optional.ofNullable(spies.get(spyId));
    }

    /**
     * 获取国家的所有间谍
     */
    public List<Spy> getSpiesOfNation(NationId nationId) {
        Set<UUID> spyIds = nationSpies.getOrDefault(nationId, Collections.emptySet());
        return spyIds.stream()
            .map(spies::get)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    /**
     * 获取针对特定国家的间谍
     */
    public List<Spy> getSpiesAgainst(NationId targetNation) {
        return spies.values().stream()
            .filter(spy -> spy.targetNation().equals(targetNation))
            .collect(Collectors.toList());
    }

    /**
     * 派遣间谍执行任务
     */
    public SpyMissionExecution assignMission(UUID spyId, SpyMission mission) {
        Spy spy = spies.get(spyId);
        if (spy == null) {
            throw new IllegalArgumentException("Spy not found");
        }

        if (!spy.isAvailable()) {
            throw new IllegalStateException("Spy is not available");
        }

        // 检查是否在战争中
        if (!warService.atWar(spy.ownerNation(), spy.targetNation())) {
            throw new IllegalStateException("Nations are not at war");
        }

        // 分配任务
        spy.assignMission(mission);

        // 创建任务执行记录
        Instant startTime = Instant.now();
        Instant completionTime = mission.calculateCompletionTime(startTime);
        double successRate = spy.calculateSuccessRate(mission);

        SpyMissionExecution execution = new SpyMissionExecution(
            spyId,
            mission,
            startTime,
            completionTime,
            successRate
        );

        activeMissions.put(spyId, execution);

        logger.info(String.format("Spy mission assigned: %s -> %s (Success rate: %.1f%%)",
            spy.codeName(), mission.displayName(), successRate * 100));

        return execution;
    }

    /**
     * 获取情报
     */
    public Optional<Intelligence> getIntelligence(UUID intelligenceId) {
        return Optional.ofNullable(intelligence.get(intelligenceId));
    }

    /**
     * 获取国家的情报
     */
    public List<Intelligence> getIntelligenceOfNation(NationId nationId) {
        Set<UUID> intelIds = nationIntelligence.getOrDefault(nationId, Collections.emptySet());
        Instant now = Instant.now();

        return intelIds.stream()
            .map(intelligence::get)
            .filter(Objects::nonNull)
            .filter(intel -> !intel.isExpired(now))
            .collect(Collectors.toList());
    }

    /**
     * 获取关于特定国家的情报
     */
    public List<Intelligence> getIntelligenceAbout(NationId targetNation) {
        Instant now = Instant.now();

        return intelligence.values().stream()
            .filter(intel -> intel.targetNation().equals(targetNation))
            .filter(intel -> !intel.isExpired(now))
            .collect(Collectors.toList());
    }

    /**
     * 反间谍：抓捕敌方间谍
     */
    public List<Spy> counterIntelligence(NationId nationId) {
        List<Spy> enemySpies = getSpiesAgainst(nationId);
        List<Spy> captured = new ArrayList<>();

        for (Spy spy : enemySpies) {
            if (spy.isCaptured() || !spy.isAvailable()) {
                continue;
            }

            // 抓捕概率 = 0.1 + (反间谍能力 * 0.01)
            double captureChance = config.baseCounterIntelligenceRate();
            if (ThreadLocalRandom.current().nextDouble() < captureChance) {
                spy.capture();
                captured.add(spy);

                logger.info(String.format("Enemy spy captured: %s (Owner: %s)",
                    spy.codeName(), spy.ownerNation()));
            }
        }

        return captured;
    }

    /**
     * 处决间谍
     */
    public void executeSpy(UUID spyId) {
        Spy spy = spies.get(spyId);
        if (spy == null) {
            throw new IllegalArgumentException("Spy not found");
        }

        if (!spy.isCaptured()) {
            throw new IllegalStateException("Spy is not captured");
        }

        spy.execute();
        logger.warning(String.format("Spy executed: %s (Owner: %s)",
            spy.codeName(), spy.ownerNation()));
    }

    /**
     * 释放/交换间谍
     */
    public void releaseSpy(UUID spyId) {
        Spy spy = spies.get(spyId);
        if (spy == null) {
            throw new IllegalArgumentException("Spy not found");
        }

        if (!spy.isCaptured()) {
            throw new IllegalStateException("Spy is not captured");
        }

        spy.release();
        logger.info(String.format("Spy released: %s", spy.codeName()));
    }

    /**
     * 启动定时任务
     */
    private void startPeriodicTasks() {
        // 每小时检查一次任务
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            checkMissions();
            cleanupExpiredIntelligence();
        }, 20L * 60 * 60, 20L * 60 * 60);
    }

    /**
     * 检查任务完成情况
     */
    private void checkMissions() {
        Instant now = Instant.now();

        for (Map.Entry<UUID, SpyMissionExecution> entry : activeMissions.entrySet()) {
            UUID spyId = entry.getKey();
            SpyMissionExecution execution = entry.getValue();

            if (now.isAfter(execution.completionTime())) {
                // 任务时间到，判定结果
                processMissionCompletion(spyId, execution);
            }
        }
    }

    /**
     * 处理任务完成
     */
    private void processMissionCompletion(UUID spyId, SpyMissionExecution execution) {
        Spy spy = spies.get(spyId);
        if (spy == null) {
            activeMissions.remove(spyId);
            return;
        }

        boolean success = ThreadLocalRandom.current().nextDouble() < execution.successRate();

        if (success) {
            // 任务成功，生成情报
            Intelligence intel = generateIntelligence(spy, execution.mission());
            intelligence.put(intel.id(), intel);
            nationIntelligence.computeIfAbsent(spy.ownerNation(), k -> ConcurrentHashMap.newKeySet())
                .add(intel.id());

            spy.completeMission(true);

            logger.info(String.format("Spy mission succeeded: %s completed %s",
                spy.codeName(), execution.mission().displayName()));
        } else {
            // 任务失败，可能被抓获
            if (ThreadLocalRandom.current().nextDouble() < config.captureProbabilityOnFailure()) {
                spy.capture();
                logger.warning(String.format("Spy captured on failed mission: %s",
                    spy.codeName()));
            } else {
                spy.completeMission(false);
                logger.info(String.format("Spy mission failed: %s failed %s",
                    spy.codeName(), execution.mission().displayName()));
            }
        }

        activeMissions.remove(spyId);
    }

    /**
     * 生成情报
     */
    private Intelligence generateIntelligence(Spy spy, SpyMission mission) {
        IntelligenceType type = mission.type();
        String content = generateIntelligenceContent(spy, mission);
        int reliability = calculateReliability(spy, mission);

        Instant collectedAt = Instant.now();
        Instant expiresAt = collectedAt.plusSeconds(type.validityHours() * 3600L);

        return new Intelligence(
            UUID.randomUUID(),
            spy.id(),
            spy.ownerNation(),
            spy.targetNation(),
            type,
            content,
            reliability,
            collectedAt,
            expiresAt
        );
    }

    /**
     * 生成情报内容
     */
    private String generateIntelligenceContent(Spy spy, SpyMission mission) {
        return String.format("%s: Intelligence gathered by %s on mission %s",
            mission.type().displayName(), spy.codeName(), mission.displayName());
    }

    /**
     * 计算情报可靠度
     */
    private int calculateReliability(Spy spy, SpyMission mission) {
        int base = 50;
        base += spy.skill() * 5;          // 技能加成
        base += spy.experience() / 20;    // 经验加成
        base -= mission.difficulty() * 3; // 难度减成
        return Math.max(0, Math.min(100, base));
    }

    /**
     * 清理过期情报
     */
    private void cleanupExpiredIntelligence() {
        Instant now = Instant.now();
        List<UUID> expired = intelligence.values().stream()
            .filter(intel -> intel.isExpired(now))
            .map(Intelligence::id)
            .collect(Collectors.toList());

        for (UUID id : expired) {
            Intelligence intel = intelligence.remove(id);
            if (intel != null) {
                Set<UUID> intelSet = nationIntelligence.get(intel.sourceNation());
                if (intelSet != null) {
                    intelSet.remove(id);
                }
            }
        }

        if (!expired.isEmpty()) {
            logger.fine(String.format("Cleaned up %d expired intelligence reports", expired.size()));
        }
    }

    /**
     * 间谍任务执行记录
     */
    public record SpyMissionExecution(
        UUID spyId,
        SpyMission mission,
        Instant startTime,
        Instant completionTime,
        double successRate
    ) {
        public Duration remainingTime(Instant now) {
            if (now.isAfter(completionTime)) {
                return Duration.ZERO;
            }
            return Duration.between(now, completionTime);
        }

        public double progressPercentage(Instant now) {
            if (now.isAfter(completionTime)) {
                return 100.0;
            }

            long total = Duration.between(startTime, completionTime).toSeconds();
            long elapsed = Duration.between(startTime, now).toSeconds();

            return (double) elapsed / total * 100.0;
        }
    }

    /**
     * 情报配置
     */
    public record IntelligenceConfig(
        int maxSpiesPerNation,
        double baseCounterIntelligenceRate,
        double captureProbabilityOnFailure,
        BigDecimal spyRecruitmentCost
    ) {
        public static IntelligenceConfig defaults() {
            return new IntelligenceConfig(
                10,                         // 每国最多10个间谍
                0.15,                       // 15%基础反间谍成功率
                0.3,                        // 失败时30%被抓概率
                new BigDecimal("5000")      // 招募成本
            );
        }
    }
}
