package dev.starcore.starcore.module.diplomacy.military;
import java.util.Optional;

import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.database.DatabaseService;
import dev.starcore.starcore.core.event.StarCoreEventBus;
import dev.starcore.starcore.core.persistence.PersistenceService;
import dev.starcore.starcore.core.scheduler.StarCoreScheduler;
import dev.starcore.starcore.module.diplomacy.DiplomacyRelation;
import dev.starcore.starcore.module.diplomacy.DiplomacyService;
import dev.starcore.starcore.module.diplomacy.military.event.MilitaryAllianceBrokenEvent;
import dev.starcore.starcore.module.diplomacy.military.event.MilitaryAllianceFormedEvent;
import dev.starcore.starcore.module.diplomacy.military.event.MilitaryPactUpgradedEvent;
import dev.starcore.starcore.module.diplomacy.military.storage.MilitaryAllianceStorage;
import dev.starcore.starcore.module.diplomacy.military.storage.MilitaryAllianceStorage.PactInviteRecord;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.war.WarService;
import org.bukkit.plugin.java.JavaPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 军事联盟服务实现
 *
 * 实现完整的军事联盟管理逻辑：
 * - 军事联盟邀请与超时处理
 * - 军事联盟关系持久化
 * - 互助防御条约
 * - 联合军事协议
 */
public class MilitaryAllianceServiceImpl implements MilitaryAllianceService {

    // 默认配置常量
    private static final long DEFAULT_COOLDOWN_MS = 24 * 60 * 60 * 1000L; // 24小时
    private static final long DEFAULT_INVITE_EXPIRY_MS = 7 * 24 * 60 * 60 * 1000L; // 7天

    // 军事联盟关系映射: key = (nationId1, nationId2) 标准化
    private final Map<MilitaryPactKey, MilitaryPactData> pacts = new ConcurrentHashMap<>();

    // 军事联盟邀请映射: key = (inviter, invited), value = (邀请时间, 条约类型)
    private final Map<MilitaryPactKey, PactInviteData> pendingInvites = new ConcurrentHashMap<>();

    // 冷却时间映射: key = (nation1, nation2) 标准化
    private final Map<MilitaryPactKey, Instant> cooldowns = new ConcurrentHashMap<>();

    // 依赖服务
    private final JavaPlugin plugin;
    private final NationService nationService;
    private final DiplomacyService diplomacyService;
    private final WarService warService;
    private final StarCoreScheduler scheduler;
    private final StarCoreEventBus eventBus;
    private final MilitaryAllianceStorage storage;

    // 配置
    private long cooldownMs = DEFAULT_COOLDOWN_MS;
    private long inviteExpiryMs = DEFAULT_INVITE_EXPIRY_MS;

    // 防御加成配置
    private static final double BASE_DEFENSE_BONUS = 0.1;
    private static final double FULL_ALLIANCE_BONUS = 0.25;
    private static final double INTEGRATED_BONUS = 0.5;

    /**
     * 构造函数 - 用于模块初始化
     */
    public MilitaryAllianceServiceImpl(
            JavaPlugin plugin,
            NationService nationService,
            DiplomacyService diplomacyService,
            WarService warService,
            StarCoreScheduler scheduler,
            StarCoreEventBus eventBus,
            MilitaryAllianceStorage storage
    ) {
        this.plugin = plugin;
        this.nationService = nationService;
        this.diplomacyService = diplomacyService;
        this.warService = warService;
        this.scheduler = scheduler;
        this.eventBus = eventBus;
        this.storage = storage;
    }

    /**
     * 构造函数 - 用于 StarCoreContext
     */
    public MilitaryAllianceServiceImpl(StarCoreContext context) {
        this.plugin = context.plugin();
        this.nationService = context.serviceRegistry().require(NationService.class);
        this.diplomacyService = context.serviceRegistry().find(DiplomacyService.class).orElse(null);
        this.warService = context.serviceRegistry().find(WarService.class).orElse(null);
        this.scheduler = context.scheduler();
        this.eventBus = context.eventBus();
        this.storage = new MilitaryAllianceStorage(
            context.databaseService(),
            context.persistenceService(),
            LoggerFactory.getLogger(MilitaryAllianceStorage.class)
        );
    }

    /**
     * 初始化服务
     */
    @Override
    public void initialize() {
        loadState();
    }

    /**
     * 关闭服务
     */
    public void shutdown() {
        saveState();
    }

    // ==================== 军事联盟邀请系统实现 ====================

    @Override
    public PactInviteResult sendPactInvite(NationId inviter, NationId invited, PactType pactType) {
        if (inviter.equals(invited)) {
            return new PactInviteResult(false, "不能邀请自己的国家");
        }

        if (pactType == null || pactType == PactType.NONE) {
            return new PactInviteResult(false, "必须选择有效的条约类型");
        }

        // 检查邀请方是否存在
        Optional<Nation> inviterNation = nationService.nationById(inviter);
        if (inviterNation.isEmpty()) {
            return new PactInviteResult(false, "邀请方国家不存在");
        }

        // 检查被邀请方是否存在
        Optional<Nation> invitedNation = nationService.nationById(invited);
        if (invitedNation.isEmpty()) {
            return new PactInviteResult(false, "被邀请的国家不存在");
        }

        // 检查是否已经是军事联盟（更高级别）
        Optional<MilitaryPactData> existingPact = Optional.ofNullable(pacts.get(MilitaryPactKey.of(inviter, invited)));
        if (existingPact.isPresent() && existingPact.get().pactType().isAtLeast(pactType)) {
            return new PactInviteResult(false, "已有相同或更高级别的军事联盟");
        }

        // 检查是否在敌对状态
        if (diplomacyService != null) {
            var relation = diplomacyService.relationBetween(inviter, invited);
            if (relation == DiplomacyRelation.WAR || relation == DiplomacyRelation.HOSTILE) {
                return new PactInviteResult(false, "无法与敌对国家建立军事联盟");
            }
        }

        // 检查冷却时间
        if (isInCooldown(inviter, invited)) {
            long remaining = getRemainingCooldownMs(inviter, invited);
            long hours = remaining / (60 * 60 * 1000);
            long minutes = (remaining % (60 * 60 * 1000)) / (60 * 1000);
            return new PactInviteResult(false, "外交冷却中，还需 " + hours + "小时" + minutes + "分钟");
        }

        // 标准化键（inviter < invited）
        MilitaryPactKey key = MilitaryPactKey.of(inviter, invited);

        // 检查是否已有未过期的邀请
        PactInviteData existingInvite = pendingInvites.get(key);
        if (existingInvite != null && !isInviteExpired(existingInvite.invitedAt())) {
            long remaining = getInviteRemainingMs(existingInvite.invitedAt());
            long hours = remaining / (60 * 60 * 1000);
            return new PactInviteResult(false, "已有未处理的邀请，还剩 " + hours + " 小时");
        }

        // 创建邀请
        pendingInvites.put(key, new PactInviteData(Instant.now(), pactType));
        saveState();

        return new PactInviteResult(true, "军事联盟邀请已发送，等待 " + invitedNation.get().name() + " 接受");
    }

    @Override
    public PactResult acceptPactInvite(NationId acceptor, String inviterName) {
        // 查找邀请方国家
        Optional<Nation> inviterNationOpt = nationService.nationByName(inviterName);
        if (inviterNationOpt.isEmpty()) {
            return new PactResult(false, "找不到邀请方国家: " + inviterName);
        }

        Nation acceptorNation = nationService.nationById(acceptor).orElse(null);
        if (acceptorNation == null) {
            return new PactResult(false, "你的国家不存在");
        }

        NationId inviterId = inviterNationOpt.get().id();
        MilitaryPactKey key = MilitaryPactKey.of(inviterId, acceptor);

        // 检查是否有邀请
        PactInviteData inviteData = pendingInvites.get(key);
        if (inviteData == null) {
            // 尝试反向查找
            key = MilitaryPactKey.of(acceptor, inviterId);
            inviteData = pendingInvites.get(key);
        }

        if (inviteData == null) {
            return new PactResult(false, "没有收到来自该国家的军事联盟邀请");
        }

        // 检查邀请是否过期
        if (isInviteExpired(inviteData.invitedAt())) {
            pendingInvites.remove(key);
            return new PactResult(false, "军事联盟邀请已过期");
        }

        // 检查冷却时间
        if (isInCooldown(acceptor, inviterId)) {
            long remaining = getRemainingCooldownMs(acceptor, inviterId);
            long hours = remaining / (60 * 60 * 1000);
            long minutes = (remaining % (60 * 60 * 1000)) / (60 * 1000);
            return new PactResult(false, "外交冷却中，还需 " + hours + "小时" + minutes + "分钟");
        }

        // 移除邀请记录
        pendingInvites.remove(key);

        // 创建军事联盟
        return createPactInternal(inviterId, acceptor, inviteData.pactType());
    }

    @Override
    public void rejectPactInvite(NationId rejector, String inviterName) {
        Optional<Nation> inviterNationOpt = nationService.nationByName(inviterName);
        if (inviterNationOpt.isEmpty()) {
            return;
        }

        NationId inviterId = inviterNationOpt.get().id();
        MilitaryPactKey key = MilitaryPactKey.of(inviterId, rejector);
        pendingInvites.remove(key);
    }

    @Override
    public void cancelPactInvite(NationId canceller, String invited) {
        Optional<Nation> invitedNationOpt = nationService.nationByName(invited);
        if (invitedNationOpt.isEmpty()) {
            return;
        }

        NationId invitedId = invitedNationOpt.get().id();
        MilitaryPactKey key = MilitaryPactKey.of(canceller, invitedId);
        pendingInvites.remove(key);
    }

    @Override
    public List<PactInviteInfo> getPendingInvites(NationId nationId) {
        List<PactInviteInfo> invites = new ArrayList<>();

        for (Map.Entry<MilitaryPactKey, PactInviteData> entry : pendingInvites.entrySet()) {
            MilitaryPactKey key = entry.getKey();
            PactInviteData data = entry.getValue();
            if (!isInviteExpired(data.invitedAt())) {
                // 检查是否是发送给 nationId 的邀请
                if (key.right().equals(nationId)) {
                    NationId inviterId = key.left();
                    Optional<Nation> inviterOpt = nationService.nationById(inviterId);
                    String inviterName = inviterOpt.map(Nation::name).orElse("未知");
                    invites.add(new PactInviteInfo(
                        inviterId,
                        inviterName,
                        data.pactType(),
                        data.invitedAt(),
                        getInviteRemainingMs(data.invitedAt())
                    ));
                }
            }
        }

        return invites;
    }

    @Override
    public boolean hasPendingInvite(NationId nationId) {
        for (Map.Entry<MilitaryPactKey, PactInviteData> entry : pendingInvites.entrySet()) {
            if (entry.getKey().right().equals(nationId) && !isInviteExpired(entry.getValue().invitedAt())) {
                return true;
            }
        }
        return false;
    }

    // ==================== 军事联盟关系管理实现 ====================

    @Override
    public Collection<NationId> getMilitaryAllies(NationId nationId, PactType minLevel) {
        return pacts.values().stream()
            .filter(data -> data.nation1().equals(nationId) || data.nation2().equals(nationId))
            .filter(data -> data.pactType().isAtLeast(minLevel))
            .map(data -> data.nation1().equals(nationId) ? data.nation2() : data.nation1())
            .collect(Collectors.toList());
    }

    @Override
    public Optional<MilitaryPactInfo> getPactInfo(NationId nation1, NationId nation2) {
        MilitaryPactKey key = MilitaryPactKey.of(nation1, nation2);
        MilitaryPactData data = pacts.get(key);
        if (data == null) {
            return Optional.empty();
        }

        return Optional.of(new MilitaryPactInfo(
            data.nation1(),
            data.nation2(),
            data.nation1Name(),
            data.nation2Name(),
            data.pactType(),
            data.formedAt(),
            data.upgradedAt(),
            Duration.between(data.formedAt(), Instant.now()).toDays()
        ));
    }

    @Override
    public boolean hasMilitaryAlliance(NationId nation1, NationId nation2, PactType minLevel) {
        MilitaryPactKey key = MilitaryPactKey.of(nation1, nation2);
        MilitaryPactData data = pacts.get(key);
        return data != null && data.pactType().isAtLeast(minLevel);
    }

    @Override
    public boolean breakPact(NationId nation1, NationId nation2, NationId brokenBy) {
        MilitaryPactKey key = MilitaryPactKey.of(nation1, nation2);
        MilitaryPactData data = pacts.remove(key);
        if (data == null) {
            return false;
        }

        // 设置冷却时间
        cooldowns.put(key, Instant.now());

        // 发布军事联盟破裂事件
        publishEvent(new MilitaryAllianceBrokenEvent(nation1, nation2, brokenBy, data.pactType()));

        saveState();
        return true;
    }

    @Override
    public PactResult upgradePact(NationId nation1, NationId nation2, PactType newType) {
        if (newType == null || newType == PactType.NONE) {
            return new PactResult(false, "必须选择有效的条约类型");
        }

        MilitaryPactKey key = MilitaryPactKey.of(nation1, nation2);
        MilitaryPactData data = pacts.get(key);
        if (data == null) {
            return new PactResult(false, "不存在军事联盟关系");
        }

        if (!newType.isHigherThan(data.pactType())) {
            return new PactResult(false, "新条约类型必须高于当前类型");
        }

        // 检查冷却时间
        if (isInCooldown(nation1, nation2)) {
            long remaining = getRemainingCooldownMs(nation1, nation2);
            long hours = remaining / (60 * 60 * 1000);
            return new PactResult(false, "外交冷却中，还需 " + hours + " 小时");
        }

        // 更新条约
        MilitaryPactData newData = new MilitaryPactData(
            data.nation1(),
            data.nation2(),
            data.nation1Name(),
            data.nation2Name(),
            newType,
            data.formedAt(),
            Instant.now()
        );
        pacts.put(key, newData);

        // 设置冷却时间
        cooldowns.put(key, Instant.now());

        // 发布升级事件
        publishEvent(new MilitaryPactUpgradedEvent(nation1, nation2, data.pactType(), newType));

        saveState();
        return new PactResult(true, "军事联盟条约已升级为 " + newType.displayName());
    }

    @Override
    public int getPactCount() {
        return pacts.size();
    }

    // ==================== 军事联盟效果实现 ====================

    @Override
    public boolean isUnderProtection(NationId attacker, NationId defender) {
        // 检查是否有国家正在保护 defender
        for (MilitaryPactData data : pacts.values()) {
            if (data.nation2().equals(defender)) {
                // defender 是被保护方，检查 nation1 是否在攻击 defender
                if (data.nation1().equals(attacker)) {
                    return data.pactType().isAtLeast(PactType.DEFENSIVE);
                }
                // 检查 nation1 是否在与其他国家交战（可能间接保护）
                if (data.pactType().isAtLeast(PactType.FULL_ALLIANCE) && isAtWar(data.nation1(), attacker)) {
                    return true;
                }
            } else if (data.nation1().equals(defender)) {
                // defender 是被保护方，检查 nation2 是否在攻击 defender
                if (data.nation2().equals(attacker)) {
                    return data.pactType().isAtLeast(PactType.DEFENSIVE);
                }
                // 检查 nation2 是否在与其他国家交战（可能间接保护）
                if (data.pactType().isAtLeast(PactType.FULL_ALLIANCE) && isAtWar(data.nation2(), attacker)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean canJointDefense(NationId ally1, NationId ally2, NationId target) {
        // 检查 ally1 和 ally2 是否都有针对 target 的军事联盟
        MilitaryPactKey key1 = MilitaryPactKey.of(ally1, target);
        MilitaryPactKey key2 = MilitaryPactKey.of(ally2, target);

        MilitaryPactData data1 = pacts.get(key1);
        MilitaryPactData data2 = pacts.get(key2);

        return data1 != null && data2 != null &&
               data1.pactType().isAtLeast(PactType.DEFENSIVE) &&
               data2.pactType().isAtLeast(PactType.DEFENSIVE);
    }

    @Override
    public double getDefenseBonus(NationId defender, NationId attacker) {
        double totalBonus = 0.0;

        // 计算所有盟友提供的防御加成
        for (MilitaryPactData data : pacts.values()) {
            if (data.nation1().equals(defender)) {
                // defender 在左边，检查 nation2 是否与 attacker 交战
                if (isAtWar(data.nation2(), attacker)) {
                    totalBonus += data.pactType().defenseBonus();
                }
            } else if (data.nation2().equals(defender)) {
                // defender 在右边，检查 nation1 是否与 attacker 交战
                if (isAtWar(data.nation1(), attacker)) {
                    totalBonus += data.pactType().defenseBonus();
                }
            }
        }

        return totalBonus;
    }

    @Override
    public long getRemainingCooldown(NationId nation1, NationId nation2) {
        return getRemainingCooldownMs(nation1, nation2);
    }

    // ==================== 统计数据实现 ====================

    @Override
    public MilitaryAllianceStats getStats() {
        int totalInvites = (int) pendingInvites.entrySet().stream()
            .filter(e -> !isInviteExpired(e.getValue().invitedAt()))
            .count();

        // 计算最大联盟规模（按国家计算军事联盟数）
        Map<NationId, Long> allyCounts = new HashMap<>();
        for (MilitaryPactData data : pacts.values()) {
            allyCounts.merge(data.nation1(), 1L, Long::sum);
            allyCounts.merge(data.nation2(), 1L, Long::sum);
        }

        int strongestSize = allyCounts.values().stream()
            .mapToInt(Long::intValue)
            .max()
            .orElse(0);

        String mostAllied = allyCounts.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(e -> nationService.nationById(e.getKey()).map(Nation::name).orElse("未知"))
            .orElse("无");

        return new MilitaryAllianceStats(
            pacts.size(),
            totalInvites,
            strongestSize,
            mostAllied
        );
    }

    @Override
    public String summary() {
        return pacts.size() + " military pact(s), " + pendingInvites.size() + " pending invite(s)";
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 内部方法：创建军事联盟
     */
    private PactResult createPactInternal(NationId nation1, NationId nation2, PactType pactType) {
        // 创建军事联盟数据
        MilitaryPactData data = new MilitaryPactData(
            nation1,
            nation2,
            nationService.nationById(nation1).map(Nation::name).orElse("未知"),
            nationService.nationById(nation2).map(Nation::name).orElse("未知"),
            pactType,
            Instant.now(),
            Instant.now()
        );

        MilitaryPactKey key = MilitaryPactKey.of(nation1, nation2);
        pacts.put(key, data);

        // 发布军事联盟建立事件
        publishEvent(new MilitaryAllianceFormedEvent(nation1, nation2, pactType));

        saveState();
        return new PactResult(true, "军事联盟建立成功！当前条约类型: " + pactType.displayName());
    }

    /**
     * 检查邀请是否过期
     */
    private boolean isInviteExpired(Instant inviteTime) {
        return Duration.between(inviteTime, Instant.now()).toMillis() >= inviteExpiryMs;
    }

    /**
     * 获取邀请剩余时间（毫秒）
     */
    private long getInviteRemainingMs(Instant inviteTime) {
        long elapsed = Duration.between(inviteTime, Instant.now()).toMillis();
        return Math.max(0, inviteExpiryMs - elapsed);
    }

    /**
     * 检查是否在冷却中
     */
    private boolean isInCooldown(NationId nation1, NationId nation2) {
        MilitaryPactKey key = MilitaryPactKey.of(nation1, nation2);
        Instant lastChange = cooldowns.get(key);
        if (lastChange == null) {
            return false;
        }
        return Duration.between(lastChange, Instant.now()).toMillis() < cooldownMs;
    }

    /**
     * 获取剩余冷却时间（毫秒）
     */
    private long getRemainingCooldownMs(NationId nation1, NationId nation2) {
        MilitaryPactKey key = MilitaryPactKey.of(nation1, nation2);
        Instant lastChange = cooldowns.get(key);
        if (lastChange == null) {
            return 0;
        }
        long elapsed = Duration.between(lastChange, Instant.now()).toMillis();
        return Math.max(0, cooldownMs - elapsed);
    }

    /**
     * 检查两个国家是否处于战争状态
     */
    private boolean isAtWar(NationId nation1, NationId nation2) {
        if (warService == null) {
            return false;
        }
        return warService.activeWarsOf(nation1).stream()
            .anyMatch(w -> w.right().equals(nation2) || w.left().equals(nation2));
    }

    /**
     * 发布事件到事件总线
     */
    private void publishEvent(Object event) {
        if (eventBus != null) {
            eventBus.publish(event);
        }
    }

    // ==================== 持久化方法 ====================

    private void saveState() {
        if (storage != null) {
            scheduler.runAsync(() -> {
                try {
                    storage.savePacts(pacts);
                    // Convert PactInviteData to PactInviteRecord for storage
                    Map<MilitaryPactKey, PactInviteRecord> inviteRecords = new java.util.HashMap<>();
                    for (Map.Entry<MilitaryPactKey, PactInviteData> entry : pendingInvites.entrySet()) {
                        inviteRecords.put(entry.getKey(), new PactInviteRecord(entry.getValue().pactType(), entry.getValue().invitedAt()));
                    }
                    storage.saveInvites(inviteRecords);
                    storage.saveCooldowns(cooldowns);
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to save military alliance state: " + e.getMessage());
                }
            });
        }
    }

    private void loadState() {
        if (storage != null) {
            try {
                pacts.clear();
                pacts.putAll(storage.loadPacts());

                pendingInvites.clear();
                Map<MilitaryPactKey, PactInviteRecord> loadedInvites = storage.loadInvites();
                for (Map.Entry<MilitaryPactKey, PactInviteRecord> entry : loadedInvites.entrySet()) {
                    // Convert PactInviteRecord to PactInviteData
                    pendingInvites.put(entry.getKey(), new PactInviteData(entry.getValue().invitedAt(), entry.getValue().pactType()));
                }

                cooldowns.clear();
                cooldowns.putAll(storage.loadCooldowns());
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load military alliance state: " + e.getMessage());
            }
        }
    }

    // ==================== 内部数据类 ====================

    /**
     * 条约邀请数据
     */
    private record PactInviteData(Instant invitedAt, PactType pactType) {}
}