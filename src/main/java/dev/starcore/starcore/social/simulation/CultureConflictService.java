package dev.starcore.starcore.social.simulation;
import java.util.Optional;

import dev.starcore.starcore.core.service.ServiceRegistry;
import dev.starcore.starcore.module.diplomacy.DiplomacyRelation;
import dev.starcore.starcore.module.diplomacy.DiplomacyService;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.social.simulation.events.CultureConflictEvent;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 文化冲突服务
 *
 * 管理国家间的文化冲突检测、等级判定、影响应用与和解机制
 */
public class CultureConflictService {

    // 与其他服务的依赖
    private final JavaPlugin plugin;
    private final CultureService cultureService;
    private final NationService nationService;
    private final ServiceRegistry serviceRegistry;
    private DiplomacyService diplomacyService;

    // 冲突数据缓存: nation1Id:nation2Id (规范化) -> ConflictData
    private final Map<String, CultureConflictData> conflicts = new ConcurrentHashMap<>();

    // 文化冲突兼容性矩阵 (文化类别之间的冲突权重)
    private static final Map<CultureService.CultureCategory, Map<CultureService.CultureCategory, Integer>> CONFLICT_MATRIX;

    static {
        CONFLICT_MATRIX = new EnumMap<>(CultureService.CultureCategory.class);
        for (CultureService.CultureCategory cat : CultureService.CultureCategory.values()) {
            CONFLICT_MATRIX.put(cat, new EnumMap<>(CultureService.CultureCategory.class));
        }

        // 军事 vs 宗教 = 强烈冲突
        CONFLICT_MATRIX.get(CultureService.CultureCategory.MILITARY)
            .put(CultureService.CultureCategory.RELIGION, 30);
        CONFLICT_MATRIX.get(CultureService.CultureCategory.RELIGION)
            .put(CultureService.CultureCategory.MILITARY, 30);

        // 军事 vs 艺术 = 中等冲突
        CONFLICT_MATRIX.get(CultureService.CultureCategory.MILITARY)
            .put(CultureService.CultureCategory.ART, 15);
        CONFLICT_MATRIX.get(CultureService.CultureCategory.ART)
            .put(CultureService.CultureCategory.MILITARY, 15);

        // 科学 vs 宗教 = 轻微冲突
        CONFLICT_MATRIX.get(CultureService.CultureCategory.SCIENCE)
            .put(CultureService.CultureCategory.RELIGION, 10);
        CONFLICT_MATRIX.get(CultureService.CultureCategory.RELIGION)
            .put(CultureService.CultureCategory.SCIENCE, 10);

        // 商业 vs 军事 = 轻微冲突
        CONFLICT_MATRIX.get(CultureService.CultureCategory.TRADE)
            .put(CultureService.CultureCategory.MILITARY, 10);
        CONFLICT_MATRIX.get(CultureService.CultureCategory.MILITARY)
            .put(CultureService.CultureCategory.TRADE, 10);

        // 政治 vs 宗教 = 中等冲突
        CONFLICT_MATRIX.get(CultureService.CultureCategory.POLITICS)
            .put(CultureService.CultureCategory.RELIGION, 15);
        CONFLICT_MATRIX.get(CultureService.CultureCategory.RELIGION)
            .put(CultureService.CultureCategory.POLITICS, 15);

        // 科学 vs 政治 = 轻微冲突
        CONFLICT_MATRIX.get(CultureService.CultureCategory.SCIENCE)
            .put(CultureService.CultureCategory.POLITICS, 5);
        CONFLICT_MATRIX.get(CultureService.CultureCategory.POLITICS)
            .put(CultureService.CultureCategory.SCIENCE, 5);
    }

    public CultureConflictService(JavaPlugin plugin, CultureService cultureService,
            NationService nationService, ServiceRegistry serviceRegistry) {
        this.plugin = plugin;
        this.cultureService = cultureService;
        this.nationService = nationService;
        this.serviceRegistry = serviceRegistry;
        this.diplomacyService = null; // 延迟初始化
        initializeConflicts();
    }

    /**
     * 简化构造函数（无 NationService）
     */
    public CultureConflictService(JavaPlugin plugin, CultureService cultureService) {
        this.plugin = plugin;
        this.cultureService = cultureService;
        this.nationService = null;
        this.serviceRegistry = null;
        this.diplomacyService = null;
        // 不初始化冲突，因为没有 nationService
    }

    /**
     * 获取外交服务（延迟加载）
     */
    private DiplomacyService getDiplomacyService() {
        if (diplomacyService == null && serviceRegistry != null) {
            diplomacyService = serviceRegistry.find(DiplomacyService.class).orElse(null);
        }
        return diplomacyService;
    }

    /**
     * 初始化现有国家间的冲突
     */
    private void initializeConflicts() {
        if (nationService == null) {
            return; // 没有 NationService，跳过初始化
        }

        List<NationId> nations = new ArrayList<>(nationService.nations().stream()
            .map(n -> n.id())
            .toList());

        for (int i = 0; i < nations.size(); i++) {
            for (int j = i + 1; j < nations.size(); j++) {
                NationId n1 = nations.get(i);
                NationId n2 = nations.get(j);
                String key = makeKey(n1, n2);
                if (!conflicts.containsKey(key)) {
                    CultureConflictData data = detectConflict(n1, n2);
                    if (data != null) {
                        conflicts.put(key, data);
                    }
                }
            }
        }
    }

    /**
     * 创建规范化键（确保 nation1 < nation2 避免重复）
     */
    private String makeKey(NationId n1, NationId n2) {
        return n1.value().compareTo(n2.value()) < 0
            ? n1.toString() + ":" + n2.toString()
            : n2.toString() + ":" + n1.toString();
    }

    // ==================== 冲突检测 ====================

    /**
     * 检测两个国家间的文化冲突
     *
     * @param nation1 第一个国家
     * @param nation2 第二个国家
     * @return 冲突数据（无冲突时返回null）
     */
    public CultureConflictData detectConflict(NationId nation1, NationId nation2) {
        List<CultureService.CultureTrait> traits1 = cultureService.getTraits(nation1);
        List<CultureService.CultureTrait> traits2 = cultureService.getTraits(nation2);

        if (traits1.isEmpty() && traits2.isEmpty()) {
            return null; // 两国都无文化特质，无冲突
        }

        // 计算冲突紧张度
        int totalTension = 0;
        List<CultureService.CultureCategory> conflictingCategories = new ArrayList<>();
        List<String> conflictReasons = new ArrayList<>();

        // 分析特质之间的冲突
        for (CultureService.CultureTrait t1 : traits1) {
            for (CultureService.CultureTrait t2 : traits2) {
                int conflictWeight = getConflictWeight(t1.category(), t2.category());
                if (conflictWeight > 0) {
                    totalTension += conflictWeight;
                    if (!conflictingCategories.contains(t1.category())) {
                        conflictingCategories.add(t1.category());
                    }
                    if (!conflictingCategories.contains(t2.category())) {
                        conflictingCategories.add(t2.category());
                    }
                    conflictReasons.add(t1.name() + " vs " + t2.name() + " 冲突 (权重: " + conflictWeight + ")");
                }
            }
        }

        // 考虑文化差距引发的冲突
        int points1 = cultureService.getCulturePoints(nation1);
        int points2 = cultureService.getCulturePoints(nation2);
        int cultureGap = Math.abs(points1 - points2);

        // 文化差距过大可能导致紧张
        if (cultureGap > 1000) {
            totalTension += 10;
            conflictReasons.add("文化差距过大 (差距: " + cultureGap + ")");
        }

        // 文化水平差异悬殊
        CultureService.CultureLevel level1 = cultureService.getLevel(nation1);
        CultureService.CultureLevel level2 = cultureService.getLevel(nation2);
        if (level1.ordinal() - level2.ordinal() >= 3 || level2.ordinal() - level1.ordinal() >= 3) {
            totalTension += 15;
            conflictReasons.add("文化发展水平悬殊 (" + level1.name() + " vs " + level2.name() + ")");
        }

        if (totalTension == 0) {
            return null; // 无冲突
        }

        // 封顶紧张度为100
        totalTension = Math.min(100, totalTension);
        ConflictLevel level = ConflictLevel.fromTension(totalTension);

        return new CultureConflictData(
            UUID.randomUUID(),
            nation1,
            nation2,
            level,
            totalTension,
            totalTension,
            conflictReasons,
            conflictingCategories,
            Instant.now(),
            Instant.now(),
            0,
            false
        );
    }

    /**
     * 获取两个文化类别之间的冲突权重
     */
    private int getConflictWeight(CultureService.CultureCategory cat1, CultureService.CultureCategory cat2) {
        Map<CultureService.CultureCategory, Integer> row = CONFLICT_MATRIX.get(cat1);
        return row != null ? row.getOrDefault(cat2, 0) : 0;
    }

    /**
     * 检查两个国家是否有文化冲突
     */
    public boolean hasConflict(NationId nation1, NationId nation2) {
        String key = makeKey(nation1, nation2);
        CultureConflictData data = conflicts.get(key);
        return data != null && data.isActive();
    }

    /**
     * 获取两个国家间的文化冲突数据
     */
    public Optional<CultureConflictData> getConflict(NationId nation1, NationId nation2) {
        String key = makeKey(nation1, nation2);
        return Optional.ofNullable(conflicts.get(key));
    }

    /**
     * 获取某个国家参与的所有活跃冲突
     */
    public List<CultureConflictData> getActiveConflicts(NationId nationId) {
        return conflicts.values().stream()
            .filter(c -> c.involves(nationId) && c.isActive())
            .collect(Collectors.toList());
    }

    /**
     * 扫描并更新所有文化冲突
     * 应定期调用
     */
    public void scanAndUpdateConflicts() {
        nationService.nations().forEach(nation -> {
            // 检查与邻国的冲突
            nationService.nations().forEach(other -> {
                if (nation.id().equals(other.id())) return;

                String key = makeKey(nation.id(), other.id());
                CultureConflictData existing = conflicts.get(key);

                if (existing == null) {
                    // 新检测
                    CultureConflictData newConflict = detectConflict(nation.id(), other.id());
                    if (newConflict != null) {
                        conflicts.put(key, newConflict);
                        fireConflictEvent(newConflict, CultureConflictEvent.EventType.DETECTED);
                    }
                } else if (existing.isActive()) {
                    // 已有冲突，检查是否升级或缓和
                    CultureConflictData updated = checkConflictEvolution(existing);
                    if (updated != null && updated != existing) {
                        conflicts.put(key, updated);
                        if (updated.tension() > existing.tension()) {
                            fireConflictEvent(updated, CultureConflictEvent.EventType.ESCALATED);
                        }
                    }
                }
            });
        });

        // 检查无冲突的邻国是否有文化融合可能
        checkCulturalFusion();
    }

    /**
     * 检查冲突演化（升级或缓和）
     */
    private CultureConflictData checkConflictEvolution(CultureConflictData conflict) {
        // 文化传播可能导致紧张度变化
        int points1 = cultureService.getCulturePoints(conflict.nation1());
        int points2 = cultureService.getCulturePoints(conflict.nation2());

        // 如果一方文化增强，可能加剧冲突
        int tensionChange = 0;

        // 检查是否存在引发冲突的特质变化
        List<CultureService.CultureTrait> traits1 = cultureService.getTraits(conflict.nation1());
        List<CultureService.CultureTrait> traits2 = cultureService.getTraits(conflict.nation2());

        for (CultureService.CultureTrait t1 : traits1) {
            for (CultureService.CultureTrait t2 : traits2) {
                int weight = getConflictWeight(t1.category(), t2.category());
                tensionChange += weight / 10; // 缓慢增加紧张度
            }
        }

        if (tensionChange > 0) {
            return conflict.escalate(tensionChange);
        }

        // 自然缓和：每分钟-1紧张度
        if (conflict.getMinutesSinceLastEscalation() > 5 && conflict.tension() > 0) {
            return conflict.reconcile(1);
        }

        return null;
    }

    // ==================== 冲突影响 ====================

    /**
     * 获取冲突对贸易的影响
     *
     * @param nation1 国家1
     * @param nation2 国家2
     * @return 贸易惩罚倍数（负数表示惩罚，正数表示正常）
     */
    public double getTradePenalty(NationId nation1, NationId nation2) {
        String key = makeKey(nation1, nation2);
        CultureConflictData conflict = conflicts.get(key);

        if (conflict == null || !conflict.isActive()) {
            return 1.0; // 无影响
        }

        return 1.0 + conflict.level().getTradePenalty();
    }

    /**
     * 获取冲突对外交关系的影响
     *
     * @param nation1 国家1
     * @param nation2 国家2
     * @return 关系惩罚值
     */
    public double getRelationPenalty(NationId nation1, NationId nation2) {
        String key = makeKey(nation1, nation2);
        CultureConflictData conflict = conflicts.get(key);

        if (conflict == null || !conflict.isActive()) {
            return 0.0;
        }

        // 中等及以上冲突影响关系
        if (conflict.level().ordinalValue() >= ConflictLevel.MODERATE.ordinalValue()) {
            return conflict.level().getTradePenalty(); // 复用贸易惩罚作为关系惩罚
        }

        return 0.0;
    }

    /**
     * 检查文化冲突是否可能引发战争
     *
     * @param nation1 国家1
     * @param nation2 国家2
     * @return 是否可能引发战争
     */
    public boolean canConflictTriggerWar(NationId nation1, NationId nation2) {
        String key = makeKey(nation1, nation2);
        CultureConflictData conflict = conflicts.get(key);

        if (conflict == null) {
            return false;
        }

        // 严重冲突且紧张度>=50可能引发战争
        if (conflict.level() == ConflictLevel.SEVERE && conflict.tension() >= 50) {
            // 检查当前外交关系
            DiplomacyService ds = getDiplomacyService();
            if (ds == null) return true; // 如果服务不可用，默认可能引发战争

            DiplomacyRelation relation = ds.relationBetween(nation1, nation2);
            // 如果已经处于敌对状态，更容易开战
            return relation != DiplomacyRelation.ALLIED && relation != DiplomacyRelation.FRIENDLY;
        }

        return false;
    }

    /**
     * 强制升级冲突（由特定事件触发）
     */
    public void forceEscalate(NationId nation1, NationId nation2, int amount, String reason) {
        String key = makeKey(nation1, nation2);
        CultureConflictData existing = conflicts.get(key);

        if (existing == null) {
            CultureConflictData newConflict = detectConflict(nation1, nation2);
            if (newConflict == null) {
                // 人工创建冲突
                newConflict = new CultureConflictData(
                    UUID.randomUUID(),
                    nation1,
                    nation2,
                    ConflictLevel.MINOR,
                    ConflictLevel.MINOR.getTensionThreshold(),
                    ConflictLevel.MINOR.getTensionThreshold(),
                    new ArrayList<>(List.of("外部事件: " + reason)),
                    new ArrayList<>(),
                    Instant.now(),
                    Instant.now(),
                    0,
                    false
                );
            }
            existing = newConflict;
        }

        CultureConflictData escalated = existing.escalate(amount);
        List<String> reasons = new ArrayList<>(escalated.conflictReasons());
        reasons.add("事件触发: " + reason);
        conflicts.put(key, new CultureConflictData(
            escalated.conflictId(),
            escalated.nation1(),
            escalated.nation2(),
            escalated.level(),
            escalated.tension(),
            escalated.maxTension(),
            reasons,
            escalated.conflictingCategories(),
            escalated.startTime(),
            escalated.lastEscalation(),
            escalated.reconciliationAttempts(),
            escalated.isResolved()
        ));

        fireConflictEvent(conflicts.get(key), CultureConflictEvent.EventType.ESCALATED);
    }

    // ==================== 和解机制 ====================

    /**
     * 尝试和解冲突
     *
     * @param mediator 和解调解方（可以是第三个国家或null表示自动和解）
     * @return 是否和解成功
     */
    public boolean attemptReconciliation(NationId nation1, NationId nation2, NationId mediator) {
        String key = makeKey(nation1, nation2);
        CultureConflictData conflict = conflicts.get(key);

        if (conflict == null || !conflict.isActive()) {
            return false;
        }

        // 和解难度取决于冲突等级
        int difficulty = switch (conflict.level()) {
            case MINOR -> 10;
            case MODERATE -> 25;
            case SEVERE -> 50;
            default -> 0;
        };

        // 有调解方更容易成功
        if (mediator != null) {
            difficulty -= 10;
            // 调解方的文化水平影响和解效果
            difficulty -= cultureService.getLevel(mediator).ordinal() * 2;
        }

        // 尝试次数越多越容易成功
        int successThreshold = Math.max(5, difficulty - (conflict.reconciliationAttempts() * 5));

        // 随机判定
        int roll = new Random().nextInt(100);
        boolean success = roll < successThreshold;

        if (success) {
            CultureConflictData reconciled = conflict.reconcile(difficulty / 2 + 10);
            if (reconciled.isResolved()) {
                conflicts.put(key, reconciled);
                fireConflictEvent(reconciled, CultureConflictEvent.EventType.RESOLVED);
                return true;
            } else {
                // 部分和解
                conflicts.put(key, reconciled);
                fireConflictEvent(reconciled, CultureConflictEvent.EventType.TENSION_REDUCED);
            }
        }

        return false;
    }

    /**
     * 文化交流促进和解
     * 两国进行文化交流活动
     */
    public boolean promoteCulturalExchange(NationId nation1, NationId nation2, int culturePoints) {
        String key = makeKey(nation1, nation2);
        CultureConflictData conflict = conflicts.get(key);

        if (conflict == null || !conflict.isActive()) {
            return false;
        }

        // 每100点文化交流减少10点紧张度
        int tensionReduction = culturePoints / 10;
        if (tensionReduction > 0) {
            CultureConflictData updated = conflict.reconcile(tensionReduction);
            conflicts.put(key, updated);

            // 同时向两国添加文化值
            cultureService.addCulturePoints(nation1, culturePoints / 2, CultureService.CultureCategory.TRADE);
            cultureService.addCulturePoints(nation2, culturePoints / 2, CultureService.CultureCategory.TRADE);

            if (updated.isResolved()) {
                fireConflictEvent(updated, CultureConflictEvent.EventType.RESOLVED);
            } else {
                fireConflictEvent(updated, CultureConflictEvent.EventType.TENSION_REDUCED);
            }

            return updated.isResolved();
        }

        return false;
    }

    // ==================== 文化融合 ====================

    /**
     * 检查是否可以进行文化融合
     * 文化融合：长期和平导致文化趋同
     */
    private void checkCulturalFusion() {
        conflicts.values().stream()
            .filter(CultureConflictData::isActive)
            .filter(c -> c.getDurationMinutes() >= 60) // 至少持续1小时
            .filter(c -> c.tension() <= 10) // 紧张度很低
            .forEach(conflict -> {
                // 随机判定是否融合
                if (new Random().nextInt(100) < 10) {
                    performCulturalFusion(conflict);
                }
            });
    }

    /**
     * 执行文化融合
     */
    private void performCulturalFusion(CultureConflictData conflict) {
        // 文化融合：双方获得对方的特质
        List<CultureService.CultureTrait> traits1 = cultureService.getTraits(conflict.nation1());
        List<CultureService.CultureTrait> traits2 = cultureService.getTraits(conflict.nation2());

        // 随机选择一个对方的特质
        if (!traits2.isEmpty() && new Random().nextBoolean()) {
            CultureService.CultureTrait inherited = traits2.get(new Random().nextInt(traits2.size()));
            // 通过文化点数形式让特质可传递
            cultureService.addCulturePoints(conflict.nation1(), 50, inherited.category());
        }

        if (!traits1.isEmpty() && new Random().nextBoolean()) {
            CultureService.CultureTrait inherited = traits1.get(new Random().nextInt(traits1.size()));
            cultureService.addCulturePoints(conflict.nation2(), 50, inherited.category());
        }

        CultureConflictData fused = conflict.resolveAsFusion();
        conflicts.put(makeKey(conflict.nation1(), conflict.nation2()), fused);

        fireConflictEvent(fused, CultureConflictEvent.EventType.FUSION_COMPLETED);
    }

    /**
     * 获取文化融合进度
     */
    public double getFusionProgress(NationId nation1, NationId nation2) {
        String key = makeKey(nation1, nation2);
        CultureConflictData conflict = conflicts.get(key);

        if (conflict == null || !conflict.isActive()) {
            return 0.0;
        }

        // 根据持续时间和低紧张度计算融合进度
        double durationFactor = Math.min(1.0, conflict.getDurationMinutes() / 120.0); // 最多2小时
        double tensionFactor = 1.0 - (conflict.tension() / 100.0);

        return (durationFactor + tensionFactor) / 2.0;
    }

    // ==================== 事件触发 ====================

    private void fireConflictEvent(CultureConflictData conflict, CultureConflictEvent.EventType type) {
        CultureConflictEvent event = new CultureConflictEvent(conflict, type);
        Bukkit.getPluginManager().callEvent(event);
    }

    // ==================== 统计和查询 ====================

    /**
     * 获取全局冲突统计
     */
    public ConflictStatistics getStatistics() {
        int total = conflicts.size();
        int active = (int) conflicts.values().stream().filter(CultureConflictData::isActive).count();
        int minor = (int) conflicts.values().stream()
            .filter(c -> c.isActive() && c.level() == ConflictLevel.MINOR).count();
        int moderate = (int) conflicts.values().stream()
            .filter(c -> c.isActive() && c.level() == ConflictLevel.MODERATE).count();
        int severe = (int) conflicts.values().stream()
            .filter(c -> c.isActive() && c.level() == ConflictLevel.SEVERE).count();

        return new ConflictStatistics(total, active, minor, moderate, severe);
    }

    /**
     * 获取可能引发战争的冲突数量
     */
    public int countWarRisks() {
        return (int) conflicts.values().stream()
            .filter(conflict -> this.canConflictTriggerWar(conflict.nation1(), conflict.nation2()))
            .count();
    }

    /**
     * 获取冲突记录
     */
    public Collection<CultureConflictData> getAllConflicts() {
        return conflicts.values();
    }

    /**
     * 清除已解决的冲突记录
     */
    public void clearResolvedConflicts() {
        conflicts.entrySet().removeIf(entry -> entry.getValue().isResolved());
    }

    /**
     * 冲突统计数据
     */
    public record ConflictStatistics(
        int totalConflicts,
        int activeConflicts,
        int minorConflicts,
        int moderateConflicts,
        int severeConflicts
    ) {}

    /**
     * 数据结构（兼容 record 的所有方法）
     */
    public record CultureConflictData(
        UUID conflictId,
        NationId nation1,
        NationId nation2,
        ConflictLevel level,
        int tension,
        int maxTension,
        List<String> conflictReasons,
        List<CultureService.CultureCategory> conflictingCategories,
        Instant startTime,
        Instant lastEscalation,
        int reconciliationAttempts,
        boolean isResolved
    ) {
        public boolean canTriggerWar() {
            return level.canTriggerWar() && tension >= 50;
        }

        public double getTensionPercent() {
            return tension / 100.0;
        }

        public boolean isActive() {
            return !isResolved && tension > 0;
        }

        public boolean involves(NationId nationId) {
            return nation1.equals(nationId) || nation2.equals(nationId);
        }

        public NationId getOtherParty(NationId nationId) {
            if (nation1.equals(nationId)) return nation2;
            if (nation2.equals(nationId)) return nation1;
            throw new IllegalArgumentException("Nation not part of this conflict");
        }

        public CultureConflictData escalate(int amount) {
            int newTension = Math.min(100, tension + amount);
            int newMax = Math.max(maxTension, newTension);
            List<String> newReasons = new ArrayList<>(conflictReasons);
            newReasons.add("紧张度升级: +" + amount);
            return new CultureConflictData(
                conflictId, nation1, nation2,
                ConflictLevel.fromTension(newTension),
                newTension, newMax, newReasons,
                conflictingCategories, startTime, Instant.now(),
                reconciliationAttempts, false
            );
        }

        public CultureConflictData reconcile(int amount) {
            int newTension = Math.max(0, tension - amount);
            List<String> newReasons = new ArrayList<>(conflictReasons);
            newReasons.add("和解努力: -" + amount);
            return new CultureConflictData(
                conflictId, nation1, nation2,
                ConflictLevel.fromTension(newTension),
                newTension, maxTension, newReasons,
                conflictingCategories, startTime, lastEscalation,
                reconciliationAttempts + 1, newTension == 0
            );
        }

        public CultureConflictData resolveAsFusion() {
            List<String> newReasons = new ArrayList<>(conflictReasons);
            newReasons.add("文化融合完成");
            return new CultureConflictData(
                conflictId, nation1, nation2,
                ConflictLevel.NONE, 0, maxTension, newReasons,
                List.of(), startTime, Instant.now(),
                reconciliationAttempts, true
            );
        }

        public long getDurationMinutes() {
            return java.time.Duration.between(startTime, Instant.now()).toMinutes();
        }

        public long getMinutesSinceLastEscalation() {
            return java.time.Duration.between(lastEscalation, Instant.now()).toMinutes();
        }
    }
}
