package dev.starcore.starcore.module.vassal;
import java.util.Optional;

import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.event.StarCoreEventBus;
import dev.starcore.starcore.core.scheduler.StarCoreScheduler;
import dev.starcore.starcore.foundation.economy.InternalEconomyService;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.vassal.event.VassalEstablishedEvent;
import dev.starcore.starcore.module.vassal.event.VassalIndependenceDeclaredEvent;
import dev.starcore.starcore.module.vassal.event.VassalReleasedEvent;
import dev.starcore.starcore.module.vassal.event.VassalTributePaidEvent;
import dev.starcore.starcore.module.vassal.model.VassalInviteInfo;
import dev.starcore.starcore.module.vassal.model.VassalRelation;
import dev.starcore.starcore.module.vassal.model.VassalRelationKey;
import dev.starcore.starcore.module.vassal.model.VassalRelationSnapshot;
import dev.starcore.starcore.module.vassal.model.VassalType;
import dev.starcore.starcore.module.vassal.storage.VassalStorage;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.logging.Logger;

/**
 * 宗藩系统服务实现
 * Implementation of VassalService
 */
public class VassalServiceImpl implements VassalService {

    // 宗藩邀请有效期（毫秒）- 默认 7 天
    private static final long INVITE_EXPIRY_MS = 7 * 24 * 60 * 60 * 1000L;

    // 独立战争冷却时间（毫秒）- 默认 30 天
    private static final long INDEPENDENCE_COOLDOWN_MS = 30 * 24 * 60 * 60 * 1000L;

    // 最小贡金
    private static final BigDecimal MIN_TRIBUTE = new BigDecimal("100.00");

    private final Map<VassalRelationKey, VassalRelation> relations = new ConcurrentHashMap<>();
    private final Map<VassalRelationKey, Instant> invites = new ConcurrentHashMap<>();
    private final Map<NationId, Instant> independenceCooldowns = new ConcurrentHashMap<>();
    private final Map<VassalRelationKey, VassalType> inviteTypes = new ConcurrentHashMap<>();

    private final NationService nationService;
    private final InternalEconomyService economyService;
    private final StarCoreEventBus eventBus;
    private final StarCoreScheduler scheduler;
    private final VassalStorage storage;
    private final org.slf4j.Logger logger;

    public VassalServiceImpl(
            StarCoreContext context,
            NationService nationService,
            InternalEconomyService economyService,
            VassalStorage storage) {
        this.nationService = nationService;
        this.economyService = economyService;
        this.eventBus = context.eventBus();
        this.scheduler = context.scheduler();
        this.storage = storage;
        this.logger = org.slf4j.LoggerFactory.getLogger(VassalServiceImpl.class);

        // 加载持久化数据
        loadState();
    }

    /**
     * 初始化表格
     */
    public void initializeTables() {
        storage.initializeTables();
    }

    // ==================== 宗主国视角 ====================

    @Override
    public Collection<VassalRelationSnapshot> vassalsOf(NationId suzerainId) {
        return relations.values().stream()
            .filter(r -> r.suzerainId().equals(suzerainId))
            .map(this::toSnapshot)
            .toList();
    }

    @Override
    public Optional<VassalRelation> relationById(NationId suzerainId, NationId vassalId) {
        return Optional.ofNullable(relations.get(VassalRelationKey.of(suzerainId, vassalId)));
    }

    // ==================== 藩属国视角 ====================

    @Override
    public Optional<VassalRelationSnapshot> suzerainOf(NationId vassalId) {
        return relations.values().stream()
            .filter(r -> r.vassalId().equals(vassalId))
            .findFirst()
            .map(this::toSnapshot);
    }

    // ==================== 宗藩关系管理 ====================

    @Override
    public VassalInviteResult sendInvite(NationId suzerainId, NationId vassalId, VassalType type) {
        // 验证双方国家存在
        Optional<Nation> suzerainOpt = nationService.nationById(suzerainId);
        Optional<Nation> vassalOpt = nationService.nationById(vassalId);

        if (suzerainOpt.isEmpty()) {
            return new VassalInviteResult(false, "宗主国不存在");
        }
        if (vassalOpt.isEmpty()) {
            return new VassalInviteResult(false, "藩属国不存在");
        }

        // 不能是自己
        if (suzerainId.equals(vassalId)) {
            return new VassalInviteResult(false, "不能向自己的国家发送宗藩邀请");
        }

        // 检查是否已经是宗藩关系
        if (hasVassalRelation(suzerainId, vassalId)) {
            return new VassalInviteResult(false, "已经是宗藩关系");
        }

        // 检查藩属国是否已有宗主国
        if (suzerainOf(vassalId).isPresent()) {
            return new VassalInviteResult(false, "该国家已有宗主国");
        }

        // 检查邀请是否已存在且未过期
        VassalRelationKey key = VassalRelationKey.of(suzerainId, vassalId);
        Instant existingInvite = invites.get(key);
        if (existingInvite != null && !isInviteExpired(existingInvite)) {
            return new VassalInviteResult(false, "已有未处理的宗藩邀请");
        }

        // 创建邀请
        invites.put(key, Instant.now());
        inviteTypes.put(key, type);
        saveState();

        logger.info("Vassal invite sent: {} -> {} (type: {})",
            suzerainOpt.get().name(), vassalOpt.get().name(), type.displayName());

        return new VassalInviteResult(true, "宗藩邀请已发送，等待对方接受");
    }

    @Override
    public VassalResult acceptInvite(NationId vassalId, NationId suzerainId) {
        VassalRelationKey key = VassalRelationKey.of(suzerainId, vassalId);
        Instant inviteTime = invites.remove(key);
        VassalType type = inviteTypes.remove(key);

        if (inviteTime == null) {
            return new VassalResult(false, "没有收到来自该国家的宗藩邀请");
        }

        if (isInviteExpired(inviteTime)) {
            return new VassalResult(false, "宗藩邀请已过期");
        }

        // 如果没有记录类型，使用默认值
        if (type == null) {
            type = VassalType.TRIBUTARY;
        }

        // 创建宗藩关系
        VassalRelation relation = VassalRelation.create(suzerainId, vassalId, type);
        relations.put(key, relation);
        saveState();

        // 发布事件
        publishEvent(new VassalEstablishedEvent(suzerainId, vassalId, type));

        logger.info("Vassal relation established: {} -> {}", suzerainId, vassalId);

        return new VassalResult(true, "已接受宗藩关系，成为 " + getNationName(suzerainId) + " 的藩属国");
    }

    @Override
    public void rejectInvite(NationId vassalId, NationId suzerainId) {
        VassalRelationKey key = VassalRelationKey.of(suzerainId, vassalId);
        invites.remove(key);
        inviteTypes.remove(key);
        saveState();
    }

    @Override
    public void cancelInvite(NationId suzerainId, NationId vassalId) {
        VassalRelationKey key = VassalRelationKey.of(suzerainId, vassalId);
        invites.remove(key);
        inviteTypes.remove(key);
        saveState();
    }

    @Override
    public boolean releaseVassal(NationId suzerainId, NationId vassalId) {
        VassalRelationKey key = VassalRelationKey.of(suzerainId, vassalId);
        VassalRelation removed = relations.remove(key);

        if (removed == null) {
            return false;
        }

        // 移除藩属国的冷却时间记录
        independenceCooldowns.remove(vassalId);
        saveState();

        // 发布事件
        publishEvent(new VassalReleasedEvent(suzerainId, vassalId, VassalReleasedEvent.ReleaseReason.SUZERAIN_LIBERATES));

        logger.info("Vassal released: {} freed from {}", vassalId, suzerainId);

        return true;
    }

    @Override
    public VassalIndependenceResult declareIndependence(NationId vassalId) {
        Optional<VassalRelationSnapshot> suzerainOpt = suzerainOf(vassalId);

        if (suzerainOpt.isEmpty()) {
            return new VassalIndependenceResult(false, "你的国家没有宗主国", false);
        }

        NationId suzerainId = suzerainOpt.get().suzerainId();

        // 检查冷却时间
        Instant lastIndependence = independenceCooldowns.get(vassalId);
        if (lastIndependence != null) {
            Duration elapsed = Duration.between(lastIndependence, Instant.now());
            if (elapsed.toMillis() < INDEPENDENCE_COOLDOWN_MS) {
                long remaining = INDEPENDENCE_COOLDOWN_MS - elapsed.toMillis();
                long days = remaining / (24 * 60 * 60 * 1000);
                return new VassalIndependenceResult(false,
                    "独立冷却中，还需 " + days + " 天才能再次宣布独立", false);
            }
        }

        // 解除宗藩关系
        VassalRelationKey key = VassalRelationKey.of(suzerainId, vassalId);
        VassalRelation removed = relations.remove(key);

        if (removed == null) {
            return new VassalIndependenceResult(false, "宗藩关系不存在", false);
        }

        // 设置冷却时间
        independenceCooldowns.put(vassalId, Instant.now());
        saveState();

        // 发布事件
        publishEvent(new VassalIndependenceDeclaredEvent(vassalId, suzerainId));

        logger.info("Independence declared: {} from {}", vassalId, suzerainId);

        // 对于完全藩属，宣布独立会引发战争
        boolean warStarted = removed.type().requireFullSubmission();

        return new VassalIndependenceResult(true, "已宣布独立！", warStarted);
    }

    // ==================== 贡金管理 ====================

    @Override
    public boolean setTribute(NationId suzerainId, NationId vassalId, BigDecimal amount) {
        VassalRelationKey key = VassalRelationKey.of(suzerainId, vassalId);
        VassalRelation relation = relations.get(key);

        if (relation == null) {
            return false;
        }

        if (amount.compareTo(MIN_TRIBUTE) < 0) {
            return false;
        }

        VassalRelation updated = new VassalRelation(
            relation.suzerainId(),
            relation.vassalId(),
            relation.type(),
            relation.formedAt(),
            amount,
            relation.lastTributeAt(),
            relation.protectionEnabled()
        );

        relations.put(key, updated);
        saveState();

        return true;
    }

    @Override
    public TributeResult payTribute(NationId vassalId, NationId suzerainId, BigDecimal amount) {
        VassalRelationKey key = VassalRelationKey.of(suzerainId, vassalId);
        VassalRelation relation = relations.get(key);

        if (relation == null) {
            return new TributeResult(false, "没有宗藩关系", BigDecimal.ZERO);
        }

        // 检查余额
        BigDecimal balance = economyService.balance(vassalId.value());
        if (balance.compareTo(amount) < 0) {
            return new TributeResult(false, "余额不足，需要 " + amount + " 金币", BigDecimal.ZERO);
        }

        // 转账
        economyService.withdraw(vassalId.value(), amount);
        economyService.deposit(suzerainId.value(), amount);

        // 更新记录
        VassalRelation updated = relation.withTribute(amount);
        relations.put(key, updated);
        saveState();

        // 发布事件
        publishEvent(new VassalTributePaidEvent(vassalId, suzerainId, amount));

        return new TributeResult(true, "已缴纳 " + amount + " 金币贡金", amount);
    }

    @Override
    public Collection<VassalInviteInfo> getPendingInvites(NationId nationId) {
        List<VassalInviteInfo> result = new ArrayList<>();

        for (Map.Entry<VassalRelationKey, Instant> entry : invites.entrySet()) {
            VassalRelationKey key = entry.getKey();
            Instant inviteTime = entry.getValue();

            // 检查是发给nationId的邀请
            if (key.vassalId().equals(nationId) && !isInviteExpired(inviteTime)) {
                String suzerainName = getNationName(key.suzerainId());
                VassalType type = inviteTypes.getOrDefault(key, VassalType.TRIBUTARY);
                long remaining = getInviteRemainingMs(inviteTime);
                result.add(new VassalInviteInfo(key.suzerainId(), suzerainName, type, remaining));
            }
        }

        return result;
    }

    // ==================== 宗主保护 ====================

    @Override
    public boolean isUnderProtection(NationId nationId) {
        Optional<VassalRelationSnapshot> suzerain = suzerainOf(nationId);
        return suzerain.isPresent() && suzerain.get().protectionEnabled();
    }

    @Override
    public Optional<NationId> getProtector(NationId nationId) {
        return suzerainOf(nationId)
            .filter(VassalRelationSnapshot::protectionEnabled)
            .map(VassalRelationSnapshot::suzerainId);
    }

    // ==================== 工具方法 ====================

    @Override
    public boolean hasVassalRelation(NationId nation1, NationId nation2) {
        return relations.containsKey(VassalRelationKey.of(nation1, nation2)) ||
               relations.containsKey(VassalRelationKey.of(nation2, nation1));
    }

    @Override
    public Optional<VassalRelationSnapshot> getSnapshot(NationId nation1, NationId nation2) {
        VassalRelationKey key1 = VassalRelationKey.of(nation1, nation2);
        VassalRelationKey key2 = VassalRelationKey.of(nation2, nation1);

        VassalRelation relation = relations.get(key1);
        if (relation == null) {
            relation = relations.get(key2);
        }

        if (relation == null) {
            return Optional.empty();
        }

        return Optional.of(toSnapshot(relation));
    }

    @Override
    public VassalStats getStats() {
        int totalVassals = (int) relations.values().stream()
            .filter(r -> r.vassalId() != null)
            .count();
        int totalSuzerains = (int) relations.values().stream()
            .filter(r -> r.suzerainId() != null)
            .count();
        int pendingInvites = (int) invites.entrySet().stream()
            .filter(e -> !isInviteExpired(e.getValue()))
            .count();

        return new VassalStats(totalVassals, totalSuzerains, pendingInvites, 0);
    }

    @Override
    public String summary() {
        VassalStats stats = getStats();
        return String.format("VassalSystem: %d vassals, %d suzerains, %d pending invites",
            stats.totalVassals(), stats.totalSuzerains(), stats.pendingInvites());
    }

    // ==================== 辅助方法 ====================

    private VassalRelationSnapshot toSnapshot(VassalRelation relation) {
        String suzerainName = getNationName(relation.suzerainId());
        String vassalName = getNationName(relation.vassalId());
        return VassalRelationSnapshot.from(relation, suzerainName, vassalName);
    }

    private String getNationName(NationId nationId) {
        return nationService.nationById(nationId)
            .map(Nation::name)
            .orElse("未知");
    }

    private boolean isInviteExpired(Instant inviteTime) {
        return Duration.between(inviteTime, Instant.now()).toMillis() >= INVITE_EXPIRY_MS;
    }

    private long getInviteRemainingMs(Instant inviteTime) {
        long elapsed = Duration.between(inviteTime, Instant.now()).toMillis();
        return Math.max(0, INVITE_EXPIRY_MS - elapsed);
    }

    private void publishEvent(Object event) {
        if (eventBus != null) {
            eventBus.publish(event);
        }
    }

    private void saveState() {
        storage.saveRelations(relations);
        storage.saveInvites(invites);
    }

    private void loadState() {
        storage.loadRelations().ifPresent(relations::putAll);
        storage.loadInvites().ifPresent(invites::putAll);
    }
}