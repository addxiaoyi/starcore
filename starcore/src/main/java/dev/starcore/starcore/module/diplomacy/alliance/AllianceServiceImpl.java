package dev.starcore.starcore.module.diplomacy.alliance;
import java.util.Optional;

import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.database.DatabaseService;
import dev.starcore.starcore.core.event.StarCoreEventBus;
import dev.starcore.starcore.core.persistence.PersistenceService;
import dev.starcore.starcore.core.scheduler.StarCoreScheduler;
import dev.starcore.starcore.module.diplomacy.DiplomacyRelation;
import dev.starcore.starcore.module.diplomacy.DiplomacyService;
import dev.starcore.starcore.module.diplomacy.alliance.event.AllianceBrokenEvent;
import dev.starcore.starcore.module.diplomacy.alliance.event.AllianceFormedEvent;
import dev.starcore.starcore.module.diplomacy.alliance.storage.AllianceStorage;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import org.bukkit.plugin.java.JavaPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 联盟外交系统服务实现
 *
 * 实现完整的联盟管理逻辑：
 * - 联盟邀请与超时处理
 * - 联盟关系持久化
 * - 外交冷却管理
 * - 事件发布
 */
public class AllianceServiceImpl implements AllianceService {

    // 默认配置常量
    private static final long DEFAULT_COOLDOWN_MS = 24 * 60 * 60 * 1000L; // 24小时
    private static final long DEFAULT_INVITE_EXPIRY_MS = 7 * 24 * 60 * 60 * 1000L; // 7天

    // 联盟关系映射: key = (nationId1, nationId2) 标准化
    private final Map<AlliancePairKey, AllianceInfoData> alliances = new ConcurrentHashMap<>();

    // 联盟邀请映射: key = (inviter, invited), value = 邀请时间
    private final Map<AlliancePairKey, Instant> pendingInvites = new ConcurrentHashMap<>();

    // 冷却时间映射: key = (nation1, nation2) 标准化
    private final Map<AlliancePairKey, Instant> cooldowns = new ConcurrentHashMap<>();

    // 依赖服务
    private final JavaPlugin plugin;
    private final NationService nationService;
    private final DiplomacyService diplomacyService;
    private final StarCoreScheduler scheduler;
    private final StarCoreEventBus eventBus;
    private final AllianceStorage storage;

    // 配置
    private long cooldownMs = DEFAULT_COOLDOWN_MS;
    private long inviteExpiryMs = DEFAULT_INVITE_EXPIRY_MS;

    /**
     * 构造函数 - 用于模块初始化
     */
    public AllianceServiceImpl(
            JavaPlugin plugin,
            NationService nationService,
            DiplomacyService diplomacyService,
            StarCoreScheduler scheduler,
            StarCoreEventBus eventBus,
            AllianceStorage storage
    ) {
        this.plugin = plugin;
        this.nationService = nationService;
        this.diplomacyService = diplomacyService;
        this.scheduler = scheduler;
        this.eventBus = eventBus;
        this.storage = storage;
    }

    /**
     * 构造函数 - 用于 StarCoreContext
     */
    public AllianceServiceImpl(StarCoreContext context) {
        this.plugin = context.plugin();
        this.nationService = context.serviceRegistry().require(NationService.class);
        this.diplomacyService = context.serviceRegistry().find(DiplomacyService.class).orElse(null);
        this.scheduler = context.scheduler();
        this.eventBus = context.eventBus();
        this.storage = new AllianceStorage(
            context.databaseService(),
            context.persistenceService(),
            LoggerFactory.getLogger(AllianceStorage.class)
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

    // ==================== 联盟邀请系统实现 ====================

    @Override
    public AllianceInviteResult sendInvite(NationId inviter, NationId invited) {
        if (inviter.equals(invited)) {
            return new AllianceInviteResult(false, "不能邀请自己的国家");
        }

        // 检查邀请方是否存在
        Optional<Nation> inviterNation = nationService.nationById(inviter);
        if (inviterNation.isEmpty()) {
            return new AllianceInviteResult(false, "邀请方国家不存在");
        }

        // 检查被邀请方是否存在
        Optional<Nation> invitedNation = nationService.nationById(invited);
        if (invitedNation.isEmpty()) {
            return new AllianceInviteResult(false, "被邀请的国家不存在");
        }

        // 检查是否已经是联盟
        if (areAllied(inviter, invited)) {
            return new AllianceInviteResult(false, "已经是联盟关系");
        }

        // 检查是否在敌对状态
        if (diplomacyService != null) {
            var relation = diplomacyService.relationBetween(inviter, invited);
            if (relation == DiplomacyRelation.WAR || relation == DiplomacyRelation.HOSTILE) {
                return new AllianceInviteResult(false, "无法与敌对国家建立联盟");
            }
        }

        // 检查冷却时间
        if (isInCooldown(inviter, invited)) {
            long remaining = getRemainingCooldownMs(inviter, invited);
            long hours = remaining / (60 * 60 * 1000);
            long minutes = (remaining % (60 * 60 * 1000)) / (60 * 1000);
            return new AllianceInviteResult(false, "外交冷却中，还需 " + hours + "小时" + minutes + "分钟");
        }

        // 标准化键（inviter < invited）
        AlliancePairKey key = AlliancePairKey.of(inviter, invited);

        // 检查是否已有未过期的邀请
        Instant existingInvite = pendingInvites.get(key);
        if (existingInvite != null && !isInviteExpired(existingInvite)) {
            long remaining = getInviteRemainingMs(existingInvite);
            long hours = remaining / (60 * 60 * 1000);
            return new AllianceInviteResult(false, "已有未处理的邀请，还剩 " + hours + " 小时");
        }

        // 创建邀请
        pendingInvites.put(key, Instant.now());
        saveState();

        return new AllianceInviteResult(true, "联盟邀请已发送，等待 " + invitedNation.get().name() + " 接受");
    }

    @Override
    public AllianceResult acceptInvite(NationId acceptor, String inviterNationName) {
        // 查找邀请方国家
        Optional<Nation> inviterNationOpt = nationService.nationByName(inviterNationName);
        if (inviterNationOpt.isEmpty()) {
            return new AllianceResult(false, "找不到邀请方国家: " + inviterNationName);
        }

        Nation acceptorNation = nationService.nationById(acceptor).orElse(null);
        if (acceptorNation == null) {
            return new AllianceResult(false, "你的国家不存在");
        }

        NationId inviterId = inviterNationOpt.get().id();
        AlliancePairKey key = AlliancePairKey.of(inviterId, acceptor);

        // 检查是否有邀请
        Instant inviteTime = pendingInvites.get(key);
        if (inviteTime == null) {
            // 尝试反向查找
            key = AlliancePairKey.of(acceptor, inviterId);
            inviteTime = pendingInvites.get(key);
        }

        if (inviteTime == null) {
            return new AllianceResult(false, "没有收到来自该国家的联盟邀请");
        }

        // 检查邀请是否过期
        if (isInviteExpired(inviteTime)) {
            pendingInvites.remove(key);
            return new AllianceResult(false, "联盟邀请已过期");
        }

        // 检查冷却时间
        if (isInCooldown(acceptor, inviterId)) {
            long remaining = getRemainingCooldownMs(acceptor, inviterId);
            long hours = remaining / (60 * 60 * 1000);
            long minutes = (remaining % (60 * 60 * 1000)) / (60 * 1000);
            return new AllianceResult(false, "外交冷却中，还需 " + hours + "小时" + minutes + "分钟");
        }

        // 移除邀请记录
        pendingInvites.remove(key);

        // 创建联盟
        return createAllianceInternal(inviterId, acceptor);
    }

    @Override
    public void rejectInvite(NationId rejector, String inviterNationName) {
        Optional<Nation> inviterNationOpt = nationService.nationByName(inviterNationName);
        if (inviterNationOpt.isEmpty()) {
            return;
        }

        NationId inviterId = inviterNationOpt.get().id();
        AlliancePairKey key = AlliancePairKey.of(inviterId, rejector);
        pendingInvites.remove(key);
    }

    @Override
    public void cancelInvite(NationId canceller, String invited) {
        Optional<Nation> invitedNationOpt = nationService.nationByName(invited);
        if (invitedNationOpt.isEmpty()) {
            return;
        }

        NationId invitedId = invitedNationOpt.get().id();
        AlliancePairKey key = AlliancePairKey.of(canceller, invitedId);
        pendingInvites.remove(key);
    }

    @Override
    public List<AllianceInviteInfo> getPendingInvites(NationId nationId) {
        List<AllianceInviteInfo> invites = new ArrayList<>();

        for (Map.Entry<AlliancePairKey, Instant> entry : pendingInvites.entrySet()) {
            AlliancePairKey key = entry.getKey();
            if (!isInviteExpired(entry.getValue())) {
                // 检查是否是发送给 nationId 的邀请
                if (key.right().equals(nationId)) {
                    NationId inviterId = key.left();
                    Optional<Nation> inviterOpt = nationService.nationById(inviterId);
                    String inviterName = inviterOpt.map(Nation::name).orElse("未知");
                    invites.add(new AllianceInviteInfo(
                        inviterId,
                        inviterName,
                        entry.getValue(),
                        getInviteRemainingMs(entry.getValue())
                    ));
                }
            }
        }

        return invites;
    }

    @Override
    public boolean hasPendingInvite(NationId nationId) {
        for (Map.Entry<AlliancePairKey, Instant> entry : pendingInvites.entrySet()) {
            if (entry.getKey().right().equals(nationId) && !isInviteExpired(entry.getValue())) {
                return true;
            }
        }
        return false;
    }

    // ==================== 联盟关系管理实现 ====================

    @Override
    public Collection<NationId> getAllies(NationId nationId) {
        return alliances.values().stream()
            .filter(info -> info.nation1().equals(nationId) || info.nation2().equals(nationId))
            .map(info -> info.nation1().equals(nationId) ? info.nation2() : info.nation1())
            .collect(Collectors.toList());
    }

    @Override
    public Optional<AllianceInfo> getAllianceInfo(NationId nation1, NationId nation2) {
        AlliancePairKey key = AlliancePairKey.of(nation1, nation2);
        AllianceInfoData data = alliances.get(key);
        if (data == null) {
            return Optional.empty();
        }

        return Optional.of(new AllianceInfo(
            data.nation1(),
            data.nation2(),
            data.nation1Name(),
            data.nation2Name(),
            data.formedAt(),
            Duration.between(data.formedAt(), Instant.now()).toDays()
        ));
    }

    @Override
    public boolean areAllied(NationId nation1, NationId nation2) {
        AlliancePairKey key = AlliancePairKey.of(nation1, nation2);
        return alliances.containsKey(key);
    }

    @Override
    public boolean breakAlliance(NationId nation1, NationId nation2, NationId brokenBy) {
        AlliancePairKey key = AlliancePairKey.of(nation1, nation2);
        AllianceInfoData data = alliances.remove(key);
        if (data == null) {
            return false;
        }

        // 设置冷却时间
        cooldowns.put(key, Instant.now());

        // 更新外交关系
        if (diplomacyService != null) {
            diplomacyService.setRelation(nation1, nation2, DiplomacyRelation.NEUTRAL);
        }

        // 发布联盟破裂事件
        publishEvent(new AllianceBrokenEvent(nation1, nation2, brokenBy, AllianceBrokenEvent.BreakReason.UNILATERAL));

        saveState();
        return true;
    }

    @Override
    public int getAllianceCount() {
        return alliances.size();
    }

    // ==================== 外交策略实现 ====================

    @Override
    public boolean isInCooldown(NationId nation1, NationId nation2) {
        AlliancePairKey key = AlliancePairKey.of(nation1, nation2);
        Instant lastChange = cooldowns.get(key);
        if (lastChange == null) {
            return false;
        }
        return Duration.between(lastChange, Instant.now()).toMillis() < cooldownMs;
    }

    @Override
    public long getRemainingCooldownMs(NationId nation1, NationId nation2) {
        AlliancePairKey key = AlliancePairKey.of(nation1, nation2);
        Instant lastChange = cooldowns.get(key);
        if (lastChange == null) {
            return 0;
        }
        long elapsed = Duration.between(lastChange, Instant.now()).toMillis();
        return Math.max(0, cooldownMs - elapsed);
    }

    // ==================== 统计数据实现 ====================

    @Override
    public AllianceStats getStats() {
        int totalInvites = (int) pendingInvites.entrySet().stream()
            .filter(e -> !isInviteExpired(e.getValue()))
            .count();

        // 计算最大联盟规模（按国家计算联盟数）
        Map<NationId, Long> allyCounts = new HashMap<>();
        for (AllianceInfoData info : alliances.values()) {
            allyCounts.merge(info.nation1(), 1L, Long::sum);
            allyCounts.merge(info.nation2(), 1L, Long::sum);
        }

        int largestSize = allyCounts.values().stream()
            .mapToInt(Long::intValue)
            .max()
            .orElse(0);

        String mostActive = allyCounts.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(e -> nationService.nationById(e.getKey()).map(Nation::name).orElse("未知"))
            .orElse("无");

        return new AllianceStats(
            alliances.size(),
            totalInvites,
            largestSize,
            mostActive
        );
    }

    @Override
    public String summary() {
        return alliances.size() + " alliance(s), " + pendingInvites.size() + " pending invite(s)";
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 内部方法：创建联盟
     */
    private AllianceResult createAllianceInternal(NationId nation1, NationId nation2) {
        // 创建联盟数据
        AllianceInfoData data = new AllianceInfoData(
            nation1,
            nation2,
            nationService.nationById(nation1).map(Nation::name).orElse("未知"),
            nationService.nationById(nation2).map(Nation::name).orElse("未知"),
            Instant.now()
        );

        AlliancePairKey key = AlliancePairKey.of(nation1, nation2);
        alliances.put(key, data);

        // 更新外交关系
        if (diplomacyService != null) {
            diplomacyService.setRelation(nation1, nation2, DiplomacyRelation.ALLIED);
        }

        // 发布联盟建立事件
        publishEvent(new AllianceFormedEvent(nation1, nation2));

        saveState();
        return new AllianceResult(true, "联盟建立成功！");
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
                    storage.saveAlliances(alliances);
                    storage.saveInvites(pendingInvites);
                    storage.saveCooldowns(cooldowns);
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to save alliance state: " + e.getMessage());
                }
            });
        }
    }

    private void loadState() {
        if (storage != null) {
            try {
                alliances.clear();
                alliances.putAll(storage.loadAlliances());

                pendingInvites.clear();
                pendingInvites.putAll(storage.loadInvites());

                cooldowns.clear();
                cooldowns.putAll(storage.loadCooldowns());
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load alliance state: " + e.getMessage());
            }
        }
    }

    // ==================== 内部数据类 ====================

    // 为了向后兼容，保留旧的类型别名
    // 新代码应直接使用 AllianceInfoData 和 AlliancePairKey
}
