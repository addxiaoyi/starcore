package dev.starcore.starcore.module.merge;
import java.util.Optional;

import dev.starcore.starcore.core.database.DatabaseService;
import dev.starcore.starcore.core.persistence.PersistenceService;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.foundation.player.PlayerProfileService;
import dev.starcore.starcore.module.merge.model.MergeReferendum;
import dev.starcore.starcore.module.merge.model.MergeReferendumState;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.treasury.TreasuryService;
import dev.starcore.starcore.nation.permission.NationPermission;
import dev.starcore.starcore.util.PermissionUtil;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 合并公投服务实现
 */
public final class MergeReferendumServiceImpl implements MergeReferendumService {

    private static final String FILE_NAME = "merge-referendums.properties";
    private static final Duration DEFAULT_DURATION = Duration.ofDays(3);

    private final Plugin plugin;
    private final NationService nationService;
    private final TreasuryService treasuryService;
    private final PlayerProfileService playerProfiles;
    private final MessageService messages;
    private final MergeStateStorage stateStorage;

    private final Map<UUID, MergeReferendum> referendums = new ConcurrentHashMap<>();

    public MergeReferendumServiceImpl(
            Plugin plugin,
            NationService nationService,
            TreasuryService treasuryService,
            PlayerProfileService playerProfiles,
            MessageService messages,
            DatabaseService databaseService,
            PersistenceService persistenceService
    ) {
        this.plugin = plugin;
        this.nationService = nationService;
        this.treasuryService = treasuryService;
        this.playerProfiles = playerProfiles;
        this.messages = messages;
        this.stateStorage = new MergeStateStorage(
            "merge",
            databaseService,
            persistenceService,
            plugin.getLogger()
        );
        loadState();
    }

    @Override
    public MergeReferendum propose(UUID proposerId, String proposerName, UUID nation1Id, UUID nation2Id, String targetName) {
        NationId proposerNationId = NationId.of(nation1Id);
        NationId targetNationId = NationId.of(nation2Id);

        // 验证发起者属于发起国
        Nation proposerNation = nationService.nationById(proposerNationId)
            .orElseThrow(() -> new IllegalStateException("Nation not found: " + proposerNationId));
        if (!proposerNation.hasMember(proposerId)) {
            throw new IllegalStateException("You are not a member of this nation");
        }

        // 审计 A-094: 使用权限系统检查 MERGE_PROPOSE 权限（founder 或 TRUSTED+ 级别）
        if (!proposerNation.founderId().equals(proposerId) &&
            !PermissionUtil.hasNationPermission(proposerId, proposerNationId.value(), NationPermission.MERGE_PROPOSE)) {
            throw new IllegalStateException("你没有权限发起合并公投，需要创始人或受信任级别权限");
        }

        // 验证目标国家存在
        Nation targetNation = nationService.nationById(targetNationId)
            .orElseThrow(() -> new IllegalStateException("Target nation not found: " + targetNationId));

        // 检查是否已有进行中的公投
        for (MergeReferendum existing : referendums.values()) {
            if (existing.isPending() && existing.involvesNation(proposerNationId)) {
                throw new IllegalStateException("This nation already has a pending referendum");
            }
            if (existing.isPending() && existing.involvesNation(targetNationId)) {
                throw new IllegalStateException("Target nation already has a pending referendum");
            }
        }

        // 检查是否已有同名国家
        if (nationService.nationByName(targetName).isPresent()) {
            throw new IllegalStateException("Nation name already exists: " + targetName);
        }

        Instant now = Instant.now();
        MergeReferendum referendum = new MergeReferendum(
            proposerNationId,
            proposerId,
            proposerName,
            targetNationId,
            targetNation.name(),
            targetName,
            now,
            now.plus(DEFAULT_DURATION)
        );

        referendums.put(referendum.id(), referendum);
        playerProfiles.recordSeen(proposerId, proposerName);
        saveState();

        return referendum;
    }

    @Override
    public MergeReferendum proposeAnnexation(UUID proposerId, String proposerName, UUID suzerainId, UUID vassalId) {
        // 吞并公投的实现逻辑
        NationId suzerainNationId = NationId.of(suzerainId);
        NationId vassalNationId = NationId.of(vassalId);

        Nation suzerainNation = nationService.nationById(suzerainNationId)
            .orElseThrow(() -> new IllegalStateException("Suzerain nation not found"));
        Nation vassalNation = nationService.nationById(vassalNationId)
            .orElseThrow(() -> new IllegalStateException("Vassal nation not found"));

        if (!suzerainNation.hasMember(proposerId)) {
            throw new IllegalStateException("You are not a member of the suzerain nation");
        }

        // 吞并后使用宗主国名称
        return propose(proposerId, proposerName, suzerainId, vassalId, suzerainNation.name());
    }

    @Override
    public boolean vote(UUID voterId, UUID referendumId, boolean approve) {
        MergeReferendum referendum = referendums.get(referendumId);
        if (referendum == null || !referendum.isPending()) {
            return false;
        }

        // 检查投票者是否在公投涉及的任一国家中
        if (!isParticipant(voterId, referendumId)) {
            return false;
        }

        if (hasVoted(voterId, referendumId)) {
            return false;
        }

        if (approve) {
            referendum.vote(voterId);
        } else {
            referendum.voteAgainst(voterId);
        }

        playerProfiles.recordSeen(voterId, voterId.toString());
        saveState();

        return true;
    }

    @Override
    public boolean cancel(UUID referendumId, UUID cancellerId) {
        MergeReferendum referendum = referendums.get(referendumId);
        if (referendum == null || !referendum.isPending()) {
            return false;
        }

        if (!referendum.proposerId().equals(cancellerId)) {
            return false;
        }

        referendum.markCancelled();
        saveState();
        return true;
    }

    @Override
    public Optional<MergeReferendum> get(UUID referendumId) {
        expireIfNeeded();
        return Optional.ofNullable(referendums.get(referendumId));
    }

    @Override
    public Collection<MergeReferendum> getNationReferendums(UUID nationId) {
        expireIfNeeded();
        NationId nationIdObj = NationId.of(nationId);
        return referendums.values().stream()
            .filter(MergeReferendum::isPending)
            .filter(r -> r.involvesNation(nationIdObj))
            .collect(Collectors.toList());
    }

    @Override
    public List<MergeReferendum> getPlayerReferendums(UUID playerId) {
        expireIfNeeded();
        return referendums.values().stream()
            .filter(r -> r.isPending())
            .filter(r -> {
                Nation proposerNation = nationService.nationById(r.proposerNationId()).orElse(null);
                Nation targetNation = nationService.nationById(r.targetNationId()).orElse(null);
                if (proposerNation != null && proposerNation.hasMember(playerId)) return true;
                if (targetNation != null && targetNation.hasMember(playerId)) return true;
                return false;
            })
            .sorted((a, b) -> b.createdAt().compareTo(a.createdAt()))
            .collect(Collectors.toList());
    }

    @Override
    public boolean hasVoted(UUID voterId, UUID referendumId) {
        MergeReferendum referendum = referendums.get(referendumId);
        return referendum != null && referendum.hasVoted(voterId);
    }

    @Override
    public boolean isParticipant(UUID playerId, UUID referendumId) {
        MergeReferendum referendum = referendums.get(referendumId);
        if (referendum == null) return false;

        Nation proposerNation = nationService.nationById(referendum.proposerNationId()).orElse(null);
        Nation targetNation = nationService.nationById(referendum.targetNationId()).orElse(null);

        if (proposerNation != null && proposerNation.hasMember(playerId)) return true;
        if (targetNation != null && targetNation.hasMember(playerId)) return true;
        return false;
    }

    @Override
    public int[] getVoteStats(UUID referendumId) {
        MergeReferendum referendum = referendums.get(referendumId);
        if (referendum == null) {
            return new int[]{0, 0, 0};
        }
        return new int[]{
            referendum.approveCount(),
            referendum.rejectCount(),
            referendum.totalVotes()
        };
    }

    @Override
    public boolean isApproved(UUID referendumId) {
        MergeReferendum referendum = referendums.get(referendumId);
        return referendum != null && referendum.isApproved();
    }

    @Override
    public List<MergeReferendum> getHistory(UUID nationId, int limit) {
        NationId nationIdObj = NationId.of(nationId);
        return referendums.values().stream()
            .filter(r -> !r.isPending())
            .filter(r -> r.involvesNation(nationIdObj))
            .sorted((a, b) -> b.createdAt().compareTo(a.createdAt()))
            .limit(limit)
            .collect(Collectors.toList());
    }

    @Override
    public void processExpiredReferendums() {
        Instant now = Instant.now();
        boolean changed = false;

        for (MergeReferendum referendum : referendums.values()) {
            if (referendum.isPending() && referendum.isExpired(now)) {
                if (referendum.passes()) {
                    referendum.markApproved();
                } else {
                    referendum.markRejected();
                }
                changed = true;
            }
        }

        if (changed) {
            saveState();
        }
    }

    @Override
    public Collection<MergeReferendum> getAllActive() {
        expireIfNeeded();
        return referendums.values().stream()
            .filter(MergeReferendum::isPending)
            .collect(Collectors.toList());
    }

    @Override
    public boolean forceExecute(UUID referendumId) {
        MergeReferendum referendum = referendums.get(referendumId);
        if (referendum == null) {
            return false;
        }

        return executeMerge(referendum);
    }

    private boolean executeMerge(MergeReferendum referendum) {
        try {
            Nation proposerNation = nationService.nationById(referendum.proposerNationId())
                .orElseThrow(() -> new IllegalStateException("Proposer nation not found"));
            Nation targetNation = nationService.nationById(referendum.targetNationId())
                .orElseThrow(() -> new IllegalStateException("Target nation not found"));

            // 获取两个国家的所有成员
            Set<UUID> allMembers = new LinkedHashSet<>();
            for (var member : proposerNation.members()) {
                allMembers.add(member.playerId());
            }
            for (var member : targetNation.members()) {
                allMembers.add(member.playerId());
            }

            // 创建新国家
            Nation newNation = nationService.createNation(
                referendum.proposerId(),
                referendum.proposerName(),
                referendum.newNationName()
            );

            // 转移成员
            for (UUID playerId : allMembers) {
                if (playerId.equals(referendum.proposerId())) continue;
                String playerName = playerId.toString();
                nationService.addMember(newNation.id(), playerId, playerName);
            }

            // 合并领土（通过 NationService 的 claim 方法）
            // 注意：实际领土合并逻辑需要在 NationService 中实现

            // 合并国库
            BigDecimal targetBalance = treasuryService.balance(targetNation.id());
            if (targetBalance.signum() > 0) {
                treasuryService.withdraw(targetNation.id(), targetBalance);
                treasuryService.deposit(newNation.id(), targetBalance);
            }

            // 标记公投为已执行
            referendum.markExecuted(newNation.id());
            referendum.setResultMessage("Successfully merged into " + newNation.name());
            saveState();

            return true;
        } catch (Exception e) {
            referendum.markFailed(e.getMessage());
            saveState();
            plugin.getLogger().warning("Failed to execute merge referendum: " + e.getMessage());
            return false;
        }
    }

    private void expireIfNeeded() {
        Instant now = Instant.now();
        boolean changed = false;

        for (MergeReferendum referendum : referendums.values()) {
            if (referendum.isPending() && referendum.isExpired(now)) {
                if (referendum.passes()) {
                    referendum.markApproved();
                } else {
                    referendum.markRejected();
                }
                changed = true;
            }
        }

        if (changed) {
            saveState();
        }
    }

    private void saveState() {
        if (stateStorage == null) {
            return;
        }
        stateStorage.saveAsync(MergeStateCodec.toProperties(referendums.values()));
    }

    private void loadState() {
        referendums.clear();
        MergeStateCodec.fromProperties(stateStorage == null ? new Properties() : stateStorage.load())
            .forEach(ref -> referendums.put(ref.id(), ref));
        expireIfNeeded();
    }

    @Override
    public String summary() {
        expireIfNeeded();
        long active = referendums.values().stream().filter(MergeReferendum::isPending).count();
        long approved = referendums.values().stream().filter(MergeReferendum::isApproved).count();
        return active + " active referendum(s), " + approved + " approved, " + referendums.size() + " total";
    }

    public void shutdown() {
        saveState();
    }
}