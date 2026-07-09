package dev.starcore.starcore.module.alliance;

import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.database.DatabaseService;
import dev.starcore.starcore.core.event.StarCoreEventBus;
import dev.starcore.starcore.core.persistence.PersistenceService;
import dev.starcore.starcore.core.scheduler.StarCoreScheduler;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import org.bukkit.plugin.java.JavaPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 联盟外交系统服务实现
 *
 * 实现完整的多国联盟（联邦）管理逻辑：
 * - 联盟创建、解散、重命名
 * - 成员邀请、加入、离开、移除
 * - 角色管理和领导权转移
 * - 联盟外交关系
 * - 数据持久化
 * - 事件发布
 */
public class AllianceServiceImpl implements AllianceService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AllianceServiceImpl.class);

    // 默认配置常量
    private static final long DEFAULT_INVITE_EXPIRY_MS = 7 * 24 * 60 * 60 * 1000L; // 7天
    private static final int MAX_ALLIANCE_NAME_LENGTH = 32;
    private static final int MIN_ALLIANCE_NAME_LENGTH = 3;

    // 联盟数据映射: allianceId -> Alliance
    private final Map<UUID, Alliance> alliances = new ConcurrentHashMap<>();

    // 联盟成员映射: allianceId -> List<AllianceMember>
    private final Map<UUID, List<AllianceMember>> allianceMembers = new ConcurrentHashMap<>();

    // 联盟名称索引: name (lowercase) -> allianceId
    private final Map<String, UUID> allianceNames = new ConcurrentHashMap<>();

    // 国家所属联盟映射: nationId -> allianceId
    private final Map<NationId, UUID> nationAllianceMap = new ConcurrentHashMap<>();

    // 待处理邀请映射: nationId -> AllianceInviteInfo
    // D-123: 每次新建邀请前移除旧邀请（支持后续扩展为 List）
    private final Map<NationId, AllianceInviteInfo> pendingInvites = new ConcurrentHashMap<>();

    // 联盟关系映射: (allianceId1, allianceId2) -> AllianceRelation
    private final Map<AlliancePairKey, AllianceRelation> allianceRelations = new ConcurrentHashMap<>();

    // 联盟公告映射: allianceId -> AllianceAnnouncement
    private final Map<UUID, AllianceAnnouncement> announcements = new ConcurrentHashMap<>();

    // 依赖服务
    private final JavaPlugin plugin;
    private final NationService nationService;
    private final StarCoreScheduler scheduler;
    private final StarCoreEventBus eventBus;
    private final AllianceStorage storage;

    // 配置
    private long inviteExpiryMs = DEFAULT_INVITE_EXPIRY_MS;

    // D-124: 脏标记用于延迟批量保存，减少 I/O
    private volatile boolean dirty = false;

    // 构造方法 - 用于模块初始化
    public AllianceServiceImpl(
            JavaPlugin plugin,
            NationService nationService,
            StarCoreScheduler scheduler,
            StarCoreEventBus eventBus,
            AllianceStorage storage
    ) {
        this.plugin = plugin;
        this.nationService = nationService;
        this.scheduler = scheduler;
        this.eventBus = eventBus;
        this.storage = storage;
    }

    // 构造方法 - 用于 StarCoreContext
    public AllianceServiceImpl(StarCoreContext context) {
        this.plugin = context.plugin();
        this.nationService = context.serviceRegistry().require(NationService.class);
        this.scheduler = context.scheduler();
        this.eventBus = context.eventBus();
        this.storage = new AllianceStorage(
            context.databaseService(),
            context.persistenceService(),
            LoggerFactory.getLogger(AllianceStorage.class)
        );
    }

    @Override
    public void initialize() {
        loadState();
        // D-123: 启动定时清理过期邀请任务（每分钟检查一次）
        if (plugin != null && scheduler != null) {
            scheduler.runSyncTimer(() -> {
                cleanupExpiredInvites();
                // D-124: 脏标记模式下延迟批量保存（每5分钟检查一次）
                if (dirty) {
                    saveState();
                }
            }, 60 * 20L, 5 * 60 * 20L); // 延迟1分钟，之后每5分钟
        }
        LOGGER.info("AllianceService initialized with {} alliances", alliances.size());
    }

    /**
     * D-123: 清理所有过期邀请，避免过期邀请永久堆积
     */
    private void cleanupExpiredInvites() {
        pendingInvites.entrySet().removeIf(entry -> isInviteExpired(entry.getValue()));
    }

    @Override
    public void shutdown() {
        saveState();
        dirty = false;
        LOGGER.info("AllianceService shutdown - saved state");
    }

    // ==================== 联盟生命周期管理实现 ====================

    @Override
    public AllianceResult createAlliance(String name, NationId leaderId) {
        // 验证名称
        if (name == null || name.trim().isEmpty()) {
            return new AllianceResult(false, "联盟名称不能为空");
        }
        String trimmedName = name.trim();
        if (trimmedName.length() < MIN_ALLIANCE_NAME_LENGTH) {
            return new AllianceResult(false, "联盟名称至少需要 " + MIN_ALLIANCE_NAME_LENGTH + " 个字符");
        }
        if (trimmedName.length() > MAX_ALLIANCE_NAME_LENGTH) {
            return new AllianceResult(false, "联盟名称不能超过 " + MAX_ALLIANCE_NAME_LENGTH + " 个字符");
        }

        // 检查名称是否已存在
        String normalizedName = trimmedName.toLowerCase(Locale.ROOT);
        UUID existingId = allianceNames.get(normalizedName);
        if (existingId != null) {
            // D-124: 错误信息中包含已存在的联盟名，帮助玩家修正
            Alliance existing = alliances.get(existingId);
            String existingName = existing != null ? existing.name() : trimmedName;
            return new AllianceResult(false, "联盟名称「" + existingName + "」已被使用");
        }

        // 检查国家是否存在
        Optional<Nation> leaderNationOpt = nationService.nationById(leaderId);
        if (leaderNationOpt.isEmpty()) {
            return new AllianceResult(false, "国家不存在");
        }
        Nation leaderNation = leaderNationOpt.get();

        // 检查国家是否已在联盟中
        if (nationAllianceMap.containsKey(leaderId)) {
            return new AllianceResult(false, "你的国家已在联盟中，需要先离开当前联盟");
        }

        // 创建联盟
        UUID allianceId = UUID.randomUUID();
        Instant now = Instant.now();

        Alliance alliance = new Alliance(allianceId, trimmedName, leaderId, now, null);
        AllianceMember member = new AllianceMember(leaderId, AllianceMember.Role.LEADER, now);

        alliances.put(allianceId, alliance);
        allianceNames.put(normalizedName, allianceId);
        allianceMembers.put(allianceId, new ArrayList<>(List.of(member)));
        nationAllianceMap.put(leaderId, allianceId);

        // 发布事件
        publishEvent(new AllianceCreatedEvent(alliance, leaderNation.name()));

        // D-124: 标记脏，延迟批量保存
        markDirty();

        return new AllianceResult(true, "联盟 [" + trimmedName + "] 创建成功！");
    }

    @Override
    public AllianceResult disbandAlliance(UUID allianceId) {
        return disbandAlliance(null, allianceId);
    }

    /**
     * D-121: 带请求者校验的解散接口，内部使用
     */
    public AllianceResult disbandAlliance(NationId actor, UUID allianceId) {
        Alliance alliance = alliances.get(allianceId);
        if (alliance == null) {
            return new AllianceResult(false, "联盟不存在");
        }

        // D-121: 权限校验：只有盟主才能解散
        if (actor != null && !alliance.leaderId().equals(actor)) {
            return new AllianceResult(false, "只有盟主才能解散联盟");
        }

        // 获取所有成员
        List<AllianceMember> members = allianceMembers.get(allianceId);
        if (members != null) {
            // 移除所有成员与联盟的关联
            for (AllianceMember member : members) {
                nationAllianceMap.remove(member.nationId());
            }
        }

        // 清除所有联盟关系
        clearAllianceRelations(allianceId);

        // 清除公告
        announcements.remove(allianceId);

        // 清除所有待处理邀请
        clearPendingInvitesForAlliance(allianceId);

        // 移除联盟名称索引
        allianceNames.remove(alliance.name().toLowerCase(Locale.ROOT));

        // 移除联盟数据
        alliances.remove(allianceId);
        allianceMembers.remove(allianceId);

        // 发布事件
        publishEvent(new AllianceDisbandedEvent(allianceId, alliance.name()));

        // D-124: 标记脏，延迟批量保存
        markDirty();

        return new AllianceResult(true, "联盟 [" + alliance.name() + "] 已解散");
    }

    /**
     * D-122: 带请求者校验的重命名接口
     */
    @Override
    public AllianceResult renameAlliance(NationId actor, UUID allianceId, String newName) {
        Alliance alliance = alliances.get(allianceId);
        if (alliance == null) {
            return new AllianceResult(false, "联盟不存在");
        }

        // D-122: 权限校验：只有盟主才能重命名
        if (actor != null && !alliance.leaderId().equals(actor)) {
            return new AllianceResult(false, "只有盟主才能修改联盟名称");
        }

        // 验证新名称
        if (newName == null || newName.trim().isEmpty()) {
            return new AllianceResult(false, "联盟名称不能为空");
        }
        String trimmedName = newName.trim();
        if (trimmedName.length() < MIN_ALLIANCE_NAME_LENGTH) {
            return new AllianceResult(false, "联盟名称至少需要 " + MIN_ALLIANCE_NAME_LENGTH + " 个字符");
        }
        if (trimmedName.length() > MAX_ALLIANCE_NAME_LENGTH) {
            return new AllianceResult(false, "联盟名称不能超过 " + MAX_ALLIANCE_NAME_LENGTH + " 个字符");
        }

        // 检查新名称是否已存在
        String normalizedNewName = trimmedName.toLowerCase(Locale.ROOT);
        UUID existingId = allianceNames.get(normalizedNewName);
        if (existingId != null && !existingId.equals(allianceId)) {
            return new AllianceResult(false, "该联盟名称已被使用");
        }

        // 更新名称索引
        allianceNames.remove(alliance.name().toLowerCase(Locale.ROOT));
        allianceNames.put(normalizedNewName, allianceId);

        // 更新联盟数据（需要重新创建 record）
        Alliance newAlliance = new Alliance(
            alliance.id(),
            trimmedName,
            alliance.leaderId(),
            alliance.createdAt(),
            alliance.emblem()
        );
        alliances.put(allianceId, newAlliance);

        // D-127: 发布联盟重命名事件
        publishEvent(new AllianceRenamedEvent(allianceId, alliance.name(), trimmedName));

        // D-124: 标记脏，延迟批量保存
        markDirty();

        return new AllianceResult(true, "联盟已更名为 [" + trimmedName + "]");
    }

    @Override
    /**
     * D-122: 带请求者校验的徽章设置接口
     */
    public AllianceResult setEmblem(NationId actor, UUID allianceId, String emblem) {
        Alliance alliance = alliances.get(allianceId);
        if (alliance == null) {
            return new AllianceResult(false, "联盟不存在");
        }

        // D-122: 权限校验：只有盟主才能设置徽章
        if (actor != null && !alliance.leaderId().equals(actor)) {
            return new AllianceResult(false, "只有盟主才能设置联盟徽章");
        }

        // 更新联盟徽章
        Alliance newAlliance = new Alliance(
            alliance.id(),
            alliance.name(),
            alliance.leaderId(),
            alliance.createdAt(),
            emblem
        );
        alliances.put(allianceId, newAlliance);

        // D-127: 发布联盟徽章变更事件
        publishEvent(new AllianceEmblemChangedEvent(allianceId, emblem));

        // D-124: 标记脏，延迟批量保存
        markDirty();

        return new AllianceResult(true, "联盟徽章已更新");
    }

    @Override
    public Optional<Alliance> getAlliance(UUID allianceId) {
        return Optional.ofNullable(alliances.get(allianceId));
    }

    @Override
    public Optional<Alliance> getAllianceByName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return Optional.empty();
        }
        UUID allianceId = allianceNames.get(name.trim().toLowerCase(Locale.ROOT));
        return allianceId == null ? Optional.empty() : getAlliance(allianceId);
    }

    @Override
    public Collection<Alliance> getAllAlliances() {
        return List.copyOf(alliances.values());
    }

    @Override
    public int getAllianceCount() {
        return alliances.size();
    }

    // ==================== 成员管理实现 ====================

    @Override
    public InviteResult inviteNation(UUID allianceId, NationId nationId) {
        Alliance alliance = alliances.get(allianceId);
        if (alliance == null) {
            return new InviteResult(false, "联盟不存在", null);
        }

        // 检查被邀请国家是否存在
        Optional<Nation> invitedNationOpt = nationService.nationById(nationId);
        if (invitedNationOpt.isEmpty()) {
            return new InviteResult(false, "被邀请的国家不存在", null);
        }
        Nation invitedNation = invitedNationOpt.get();

        // 检查被邀请国家是否已在联盟中
        if (nationAllianceMap.containsKey(nationId)) {
            return new InviteResult(false, invitedNation.name() + " 已在联盟中", null);
        }

        // 检查是否已有待处理邀请
        if (pendingInvites.containsKey(nationId)) {
            return new InviteResult(false, "该国家已有待处理的邀请", null);
        }

        // 创建邀请
        Instant now = Instant.now();
        AllianceInviteInfo invite = new AllianceInviteInfo(
            allianceId,
            alliance.name(),
            nationId,
            now,
            alliance.leaderId(),
            now.plusMillis(inviteExpiryMs)
        );
        pendingInvites.put(nationId, invite);

        // 发布事件
        publishEvent(new AllianceInviteSentEvent(allianceId, alliance.name(), nationId, invitedNation.name()));

        // 保存状态
        // D-124: 标记脏，延迟批量保存
        markDirty();

        return new InviteResult(true, "已邀请 " + invitedNation.name() + " 加入联盟", allianceId);
    }

    @Override
    public AllianceResult acceptInvite(NationId nationId, UUID allianceId) {
        Alliance alliance = alliances.get(allianceId);
        if (alliance == null) {
            return new AllianceResult(false, "联盟不存在");
        }

        // 检查邀请是否存在且未过期
        AllianceInviteInfo invite = pendingInvites.get(nationId);
        if (invite == null || !invite.allianceId().equals(allianceId)) {
            return new AllianceResult(false, "没有收到该联盟的邀请");
        }

        if (isInviteExpired(invite)) {
            pendingInvites.remove(nationId);
            return new AllianceResult(false, "邀请已过期");
        }

        // 检查国家是否已在联盟中
        if (nationAllianceMap.containsKey(nationId)) {
            pendingInvites.remove(nationId);
            return new AllianceResult(false, "你的国家已在联盟中");
        }

        // 添加成员
        Instant now = Instant.now();
        AllianceMember member = new AllianceMember(nationId, AllianceMember.Role.MEMBER, now);

        List<AllianceMember> members = allianceMembers.computeIfAbsent(allianceId, k -> new ArrayList<>());
        members.add(member);
        nationAllianceMap.put(nationId, allianceId);

        // 移除邀请
        pendingInvites.remove(nationId);

        // 获取国家名称
        String nationName = nationService.nationById(nationId)
            .map(Nation::name)
            .orElse("未知");

        // 发布事件
        publishEvent(new AllianceMemberJoinedEvent(allianceId, nationId, nationName));

        // D-124: 标记脏，延迟批量保存
        markDirty();

        return new AllianceResult(true, "成功加入 [" + alliance.name() + "] 联盟！");
    }

    @Override
    public void rejectInvite(NationId nationId, UUID allianceId) {
        AllianceInviteInfo invite = pendingInvites.get(nationId);
        if (invite != null && invite.allianceId().equals(allianceId)) {
            pendingInvites.remove(nationId);

            String nationName = nationService.nationById(nationId)
                .map(Nation::name)
                .orElse("未知");

            publishEvent(new AllianceInviteRejectedEvent(allianceId, nationId, nationName));
            // D-124: 标记脏，延迟批量保存
            markDirty();
        }
    }

    @Override
    public AllianceResult leaveAlliance(NationId nationId) {
        UUID allianceId = nationAllianceMap.get(nationId);
        if (allianceId == null) {
            return new AllianceResult(false, "你的国家不在任何联盟中");
        }

        Alliance alliance = alliances.get(allianceId);
        if (alliance == null) {
            nationAllianceMap.remove(nationId);
            return new AllianceResult(false, "联盟不存在");
        }

        // 盟主不能直接离开，需要先转移领导权或解散联盟
        if (alliance.leaderId().equals(nationId)) {
            List<AllianceMember> members = allianceMembers.get(allianceId);
            if (members != null && members.size() > 1) {
                return new AllianceResult(false, "盟主不能直接离开联盟，请先使用 /alliance transferleadership 转移领导权");
            }
            // 如果只剩盟主一人，直接解散联盟
            return disbandAlliance(allianceId);
        }

        // 获取国家名称
        String nationName = nationService.nationById(nationId)
            .map(Nation::name)
            .orElse("未知");

        // 移除成员
        removeMemberFromAlliance(allianceId, nationId);

        // 发布事件
        publishEvent(new AllianceMemberLeftEvent(allianceId, nationId, nationName));

        // D-124: 标记脏，延迟批量保存
        markDirty();

        return new AllianceResult(true, "已离开 [" + alliance.name() + "] 联盟");
    }

    @Override
    public AllianceResult removeMember(UUID allianceId, NationId nationId, NationId removedBy) {
        Alliance alliance = alliances.get(allianceId);
        if (alliance == null) {
            return new AllianceResult(false, "联盟不存在");
        }

        // 只有盟主可以移除成员
        if (!alliance.leaderId().equals(removedBy)) {
            return new AllianceResult(false, "只有盟主可以移除成员");
        }

        // 不能移除自己
        if (nationId.equals(removedBy)) {
            return new AllianceResult(false, "不能移除自己，请使用解散联盟命令");
        }

        // 检查成员是否存在
        List<AllianceMember> members = allianceMembers.get(allianceId);
        if (members == null || members.stream().noneMatch(m -> m.nationId().equals(nationId))) {
            return new AllianceResult(false, "该国家不是联盟成员");
        }

        // 获取国家名称
        String nationName = nationService.nationById(nationId)
            .map(Nation::name)
            .orElse("未知");

        // 移除成员
        removeMemberFromAlliance(allianceId, nationId);

        // 发布事件
        publishEvent(new AllianceMemberRemovedEvent(allianceId, nationId, nationName, removedBy));

        // D-124: 标记脏，延迟批量保存
        markDirty();

        return new AllianceResult(true, "已将 " + nationName + " 从联盟中移除");
    }

    @Override
    public AllianceResult setMemberRole(UUID allianceId, NationId nationId, AllianceMember.Role role) {
        Alliance alliance = alliances.get(allianceId);
        if (alliance == null) {
            return new AllianceResult(false, "联盟不存在");
        }

        // 只有盟主可以设置角色
        if (!alliance.leaderId().equals(nationId)) {
            // 检查调用者是否是盟主（这里简化处理，实际需要传递调用者ID）
            return new AllianceResult(false, "只有盟主可以设置成员角色");
        }

        List<AllianceMember> members = allianceMembers.get(allianceId);
        if (members == null) {
            return new AllianceResult(false, "联盟成员列表为空");
        }

        // 查找并更新成员角色
        for (int i = 0; i < members.size(); i++) {
            AllianceMember member = members.get(i);
            if (member.nationId().equals(nationId)) {
                members.set(i, new AllianceMember(member.nationId(), role, member.joinedAt()));
                // D-124: 标记脏，延迟批量保存
                markDirty();
                return new AllianceResult(true, "已设置 " + nationService.nationById(nationId).map(Nation::name).orElse("未知") + " 的角色为 " + role.name());
            }
        }

        return new AllianceResult(false, "该国家不是联盟成员");
    }

    @Override
    public AllianceResult transferLeadership(UUID allianceId, NationId newLeaderId, NationId currentLeaderId) {
        Alliance alliance = alliances.get(allianceId);
        if (alliance == null) {
            return new AllianceResult(false, "联盟不存在");
        }

        // 只有现任盟主可以转移领导权
        if (!alliance.leaderId().equals(currentLeaderId)) {
            return new AllianceResult(false, "只有现任盟主可以转移领导权");
        }

        // 检查新盟主是否是成员
        List<AllianceMember> members = allianceMembers.get(allianceId);
        if (members == null || members.stream().noneMatch(m -> m.nationId().equals(newLeaderId))) {
            return new AllianceResult(false, "新盟主必须是联盟成员");
        }

        // 更新盟主
        Alliance newAlliance = new Alliance(
            alliance.id(),
            alliance.name(),
            newLeaderId,
            alliance.createdAt(),
            alliance.emblem()
        );
        alliances.put(allianceId, newAlliance);

        // 更新成员角色
        if (members != null) {
            for (int i = 0; i < members.size(); i++) {
                AllianceMember member = members.get(i);
                if (member.nationId().equals(newLeaderId)) {
                    members.set(i, new AllianceMember(member.nationId(), AllianceMember.Role.LEADER, member.joinedAt()));
                } else if (member.nationId().equals(currentLeaderId)) {
                    members.set(i, new AllianceMember(member.nationId(), AllianceMember.Role.MEMBER, member.joinedAt()));
                }
            }
        }

        String newLeaderName = nationService.nationById(newLeaderId).map(Nation::name).orElse("未知");

        // 发布事件
        publishEvent(new AllianceLeadershipChangedEvent(allianceId, currentLeaderId, newLeaderId, newLeaderName));

        // D-124: 标记脏，延迟批量保存
        markDirty();

        return new AllianceResult(true, "领导权已转移给 " + newLeaderName);
    }

    @Override
    public List<AllianceMember> getAllianceMembers(UUID allianceId) {
        List<AllianceMember> members = allianceMembers.get(allianceId);
        return members == null ? List.of() : List.copyOf(members);
    }

    @Override
    public Optional<Alliance> getNationAlliance(NationId nationId) {
        UUID allianceId = nationAllianceMap.get(nationId);
        return allianceId == null ? Optional.empty() : getAlliance(allianceId);
    }

    @Override
    public boolean isInAlliance(NationId nationId) {
        return nationAllianceMap.containsKey(nationId);
    }

    @Override
    public List<AllianceInviteInfo> getPendingInvites(NationId nationId) {
        AllianceInviteInfo invite = pendingInvites.get(nationId);
        if (invite == null) {
            return List.of();
        }
        if (isInviteExpired(invite)) {
            pendingInvites.remove(nationId);
            // D-124: 标记脏，延迟批量保存
            markDirty();
            return List.of();
        }
        return List.of(invite);
    }

    @Override
    public boolean hasPendingInvite(NationId nationId) {
        AllianceInviteInfo invite = pendingInvites.get(nationId);
        return invite != null && !isInviteExpired(invite);
    }

    // ==================== 联盟外交关系实现 ====================

    @Override
    public AllianceResult setAllianceRelation(UUID alliance1, UUID alliance2, AllianceRelationType relationType) {
        // 检查联盟是否存在
        if (!alliances.containsKey(alliance1)) {
            return new AllianceResult(false, "联盟1不存在");
        }
        if (!alliances.containsKey(alliance2)) {
            return new AllianceResult(false, "联盟2不存在");
        }

        if (alliance1.equals(alliance2)) {
            return new AllianceResult(false, "不能设置与自己的关系");
        }

        // 创建关系（标准化键）
        AlliancePairKey key = AlliancePairKey.of(alliance1, alliance2);
        AllianceRelation relation = new AllianceRelation(
            alliance1,
            alliance2,
            relationType,
            Instant.now(),
            null
        );
        allianceRelations.put(key, relation);

        // 发布事件
        publishEvent(new AllianceRelationChangedEvent(alliance1, alliance2, relationType));

        // D-124: 标记脏，延迟批量保存
        markDirty();

        return new AllianceResult(true, "联盟关系已设置");
    }

    @Override
    public Optional<AllianceRelation> getAllianceRelation(UUID alliance1, UUID alliance2) {
        AlliancePairKey key = AlliancePairKey.of(alliance1, alliance2);
        return Optional.ofNullable(allianceRelations.get(key));
    }

    @Override
    public Collection<UUID> getFriendlyAlliances(UUID allianceId) {
        return allianceRelations.entrySet().stream()
            .filter(e -> e.getKey().involves(allianceId))
            .filter(e -> e.getValue().type() == AllianceRelationType.FRIENDLY)
            .map(e -> e.getKey().other(allianceId))
            .collect(Collectors.toList());
    }

    @Override
    public Collection<UUID> getHostileAlliances(UUID allianceId) {
        return allianceRelations.entrySet().stream()
            .filter(e -> e.getKey().involves(allianceId))
            .filter(e -> e.getValue().type() == AllianceRelationType.HOSTILE || e.getValue().type() == AllianceRelationType.WAR)
            .map(e -> e.getKey().other(allianceId))
            .collect(Collectors.toList());
    }

    // ==================== 公告和外交实现 ====================

    @Override
    public AllianceResult setAnnouncement(UUID allianceId, String announcement, NationId announcerId) {
        Alliance alliance = alliances.get(allianceId);
        if (alliance == null) {
            return new AllianceResult(false, "联盟不存在");
        }

        // 只有盟主或官员可以发布公告（这里简化处理，实际需要检查角色）
        if (!alliance.leaderId().equals(announcerId)) {
            List<AllianceMember> members = allianceMembers.get(allianceId);
            if (members == null || members.stream().noneMatch(m -> m.nationId().equals(announcerId) && m.role() != AllianceMember.Role.MEMBER)) {
                return new AllianceResult(false, "只有盟主和官员可以发布公告");
            }
        }

        AllianceAnnouncement ann = new AllianceAnnouncement(
            allianceId,
            announcement,
            announcerId,
            Instant.now()
        );
        announcements.put(allianceId, ann);

        // D-124: 标记脏，延迟批量保存
        markDirty();

        return new AllianceResult(true, "公告已发布");
    }

    @Override
    public Optional<AllianceAnnouncement> getAnnouncement(UUID allianceId) {
        return Optional.ofNullable(announcements.get(allianceId));
    }

    // ==================== 摘要 ====================

    @Override
    public String summary() {
        int totalMembers = allianceMembers.values().stream().mapToInt(List::size).sum();
        return alliances.size() + " alliance(s), " + totalMembers + " member(s), " + pendingInvites.size() + " pending invite(s)";
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 移除成员（内部方法）
     */
    private void removeMemberFromAlliance(UUID allianceId, NationId nationId) {
        nationAllianceMap.remove(nationId);

        List<AllianceMember> members = allianceMembers.get(allianceId);
        if (members != null) {
            members.removeIf(m -> m.nationId().equals(nationId));

            // 如果联盟没有成员了，解散联盟
            if (members.isEmpty()) {
                announcements.remove(allianceId);
                clearAllianceRelations(allianceId);
                clearPendingInvitesForAlliance(allianceId);
                alliances.remove(allianceId);
                allianceMembers.remove(allianceId);
                Alliance alliance = alliances.get(allianceId);
                if (alliance != null) {
                    allianceNames.remove(alliance.name().toLowerCase(Locale.ROOT));
                }
            }
        }
    }

    /**
     * 清除联盟的所有关系
     */
    private void clearAllianceRelations(UUID allianceId) {
        allianceRelations.entrySet().removeIf(e -> e.getKey().involves(allianceId));
    }

    /**
     * 清除联盟的所有待处理邀请
     */
    private void clearPendingInvitesForAlliance(UUID allianceId) {
        pendingInvites.entrySet().removeIf(e -> e.getValue().allianceId().equals(allianceId));
    }

    /**
     * 检查邀请是否过期
     */
    private boolean isInviteExpired(AllianceInviteInfo invite) {
        return Duration.between(Instant.now(), invite.expiresAt()).isNegative();
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

    /**
     * D-124: 标记数据已修改，由定时任务触发批量保存
     */
    private void markDirty() {
        this.dirty = true;
    }

    private void saveState() {
        if (storage != null) {
            scheduler.runAsync(() -> {
                try {
                    storage.saveAlliances(alliances);
                    storage.saveMembers(allianceMembers);
                    storage.saveNationAllianceMap(nationAllianceMap);
                    storage.savePendingInvites(pendingInvites);
                    storage.saveRelations(allianceRelations);
                    storage.saveAnnouncements(announcements);
                    dirty = false; // 保存成功后清除脏标记
                } catch (Exception e) {
                    LOGGER.warn("Failed to save alliance state: {}", e.getMessage());
                }
            });
        }
    }

    private void loadState() {
        if (storage != null) {
            try {
                // 加载联盟数据
                Map<UUID, Alliance> loadedAlliances = storage.loadAlliances();
                alliances.clear();
                alliances.putAll(loadedAlliances);

                // 加载名称索引
                allianceNames.clear();
                for (Alliance alliance : loadedAlliances.values()) {
                    allianceNames.put(alliance.name().toLowerCase(Locale.ROOT), alliance.id());
                }

                // 加载成员数据
                Map<UUID, List<AllianceMember>> loadedMembers = storage.loadMembers();
                allianceMembers.clear();
                allianceMembers.putAll(loadedMembers);

                // 加载国家-联盟映射
                Map<NationId, UUID> loadedMap = storage.loadNationAllianceMap();
                nationAllianceMap.clear();
                nationAllianceMap.putAll(loadedMap);

                // 加载待处理邀请
                Map<NationId, AllianceInviteInfo> loadedInvites = storage.loadPendingInvites();
                pendingInvites.clear();
                pendingInvites.putAll(loadedInvites);

                // 清除过期邀请
                pendingInvites.entrySet().removeIf(e -> isInviteExpired(e.getValue()));

                // 加载关系数据
                Map<AlliancePairKey, AllianceRelation> loadedRelations = storage.loadRelations();
                allianceRelations.clear();
                allianceRelations.putAll(loadedRelations);

                // 加载公告数据
                Map<UUID, AllianceAnnouncement> loadedAnnouncements = storage.loadAnnouncements();
                announcements.clear();
                announcements.putAll(loadedAnnouncements);

                LOGGER.info("Loaded {} alliances, {} members, {} invites",
                    alliances.size(),
                    allianceMembers.values().stream().mapToInt(List::size).sum(),
                    pendingInvites.size());
            } catch (Exception e) {
                LOGGER.warn("Failed to load alliance state: {}", e.getMessage());
            }
        }
    }

    // ==================== 内部数据类 ====================

    /**
     * 联盟关系键（标准化两个联盟ID）
     */
    static class AlliancePairKey {
        private final UUID alliance1;
        private final UUID alliance2;

        AlliancePairKey(UUID alliance1, UUID alliance2) {
            // 标准化：较小的UUID在前
            if (alliance1.compareTo(alliance2) < 0) {
                this.alliance1 = alliance1;
                this.alliance2 = alliance2;
            } else {
                this.alliance1 = alliance2;
                this.alliance2 = alliance1;
            }
        }

        static AlliancePairKey of(UUID alliance1, UUID alliance2) {
            return new AlliancePairKey(alliance1, alliance2);
        }

        UUID alliance1() { return alliance1; }
        UUID alliance2() { return alliance2; }

        boolean involves(UUID allianceId) {
            return alliance1.equals(allianceId) || alliance2.equals(allianceId);
        }

        UUID other(UUID allianceId) {
            return alliance1.equals(allianceId) ? alliance2 : alliance1;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            AlliancePairKey that = (AlliancePairKey) o;
            return alliance1.equals(that.alliance1) && alliance2.equals(that.alliance2);
        }

        @Override
        public int hashCode() {
            return Objects.hash(alliance1, alliance2);
        }
    }

    // ==================== 事件类 ====================

    public record AllianceCreatedEvent(Alliance alliance, String leaderNationName) {}
    public record AllianceDisbandedEvent(UUID allianceId, String allianceName) {}
    public record AllianceInviteSentEvent(UUID allianceId, String allianceName, NationId nationId, String nationName) {}
    public record AllianceInviteAcceptedEvent(UUID allianceId, NationId nationId, String nationName) {}
    public record AllianceInviteRejectedEvent(UUID allianceId, NationId nationId, String nationName) {}
    public record AllianceMemberJoinedEvent(UUID allianceId, NationId nationId, String nationName) {}
    public record AllianceMemberLeftEvent(UUID allianceId, NationId nationId, String nationName) {}
    public record AllianceMemberRemovedEvent(UUID allianceId, NationId nationId, String nationName, NationId removedBy) {}
    public record AllianceLeadershipChangedEvent(UUID allianceId, NationId oldLeader, NationId newLeader, String newLeaderName) {}
    public record AllianceRenamedEvent(UUID allianceId, String oldName, String newName) {}
    public record AllianceEmblemChangedEvent(UUID allianceId, String newEmblem) {}
    public record AllianceRelationChangedEvent(UUID alliance1, UUID alliance2, AllianceRelationType relationType) {}
}
