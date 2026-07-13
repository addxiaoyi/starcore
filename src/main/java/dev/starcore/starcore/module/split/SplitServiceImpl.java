package dev.starcore.starcore.module.split;
import java.util.Optional;

import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.event.StarCoreEventBus;
import dev.starcore.starcore.core.service.ServiceRegistry;
import dev.starcore.starcore.foundation.economy.EconomyService;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.foundation.player.PlayerProfileService;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.split.event.NationSplitEvent;
import dev.starcore.starcore.module.split.event.SplitRequestApprovedEvent;
import dev.starcore.starcore.module.split.event.SplitRequestCreatedEvent;
import dev.starcore.starcore.module.split.model.SplitRequest;
import dev.starcore.starcore.module.split.model.SplitRegion;
import dev.starcore.starcore.module.split.model.SplitResult;
import dev.starcore.starcore.module.treasury.TreasuryService;
import dev.starcore.starcore.module.war.WarService;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 国家分裂服务实现
 */
public class SplitServiceImpl implements SplitService {
    private final Plugin plugin;
    private final NationService nationService;
    private final TreasuryService treasuryService;
    private final EconomyService economyService;
    private final MessageService messages;
    private final PlayerProfileService playerProfiles;
    private final StarCoreEventBus eventBus;
    private final SplitConfig config;
    private final ServiceRegistry serviceRegistry;

    private final ConcurrentMap<UUID, SplitRequest> requests = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    public SplitServiceImpl(
        Plugin plugin,
        NationService nationService,
        TreasuryService treasuryService,
        EconomyService economyService,
        MessageService messages,
        PlayerProfileService playerProfiles,
        StarCoreEventBus eventBus,
        SplitConfig config,
        ServiceRegistry serviceRegistry
    ) {
        this.plugin = plugin;
        this.nationService = nationService;
        this.treasuryService = treasuryService;
        this.economyService = economyService;
        this.messages = messages;
        this.playerProfiles = playerProfiles;
        this.eventBus = eventBus;
        this.config = config;
        this.serviceRegistry = serviceRegistry;
    }

    @Override
    public SplitConfig getConfig() {
        return config;
    }

    @Override
    public SplitResult createSplitRequest(UUID requesterId, NationId nationId,
                                          String newNationName, SplitRegion region) {
        // 检查是否可以发起分裂
        String checkResult = canInitiateSplit(requesterId, nationId);
        if (checkResult != null) {
            return SplitResult.failure(checkResult);
        }

        // 检查新国家名称
        if (newNationName == null || newNationName.trim().isEmpty()) {
            return SplitResult.failure("新国家名称不能为空");
        }
        if (nationService.nationByName(newNationName).isPresent()) {
            return SplitResult.failure("新国家名称已被使用");
        }

        // 检查区域
        if (region == null || region.chunks().isEmpty()) {
            return SplitResult.failure("分离区域不能为空");
        }

        // 检查是否有待处理的请求
        Optional<SplitRequest> existingRequest = getPendingRequestsForNation(nationId).stream().findFirst();
        if (existingRequest.isPresent()) {
            return SplitResult.failure("该国家已有待处理的分裂请求");
        }

        // 获取国家信息
        Nation sourceNation = nationService.nationById(nationId).orElse(null);
        if (sourceNation == null) {
            return SplitResult.failure("国家不存在");
        }

        // 检查区域是否属于该国家
        int validChunks = 0;
        for (var chunk : region.chunks()) {
            var claim = nationService.claimAt(chunk.world(), chunk.x(), chunk.z()).orElse(null);
            if (claim != null && claim.ownerId().equals(nationId.toString())) {
                validChunks++;
            }
        }
        if (validChunks == 0) {
            return SplitResult.failure("所选区域不属于你的国家");
        }

        // 检查分裂后是否保留足够领土
        int currentChunks = nationService.claimCount(nationId);
        int minChunks = config.getMinChunksRemaining();
        if (currentChunks - region.chunkCount() < minChunks) {
            return SplitResult.failure("分裂后原国家至少需要保留 " + minChunks + " 个区块");
        }

        // 计算费用
        java.math.BigDecimal cost = calculateSplitCost(nationId, region);

        // 创建请求
        String requesterName = playerProfiles.lastKnownName(requesterId).orElse("Unknown");
        SplitRequest request = SplitRequest.pending(
            requesterId,
            requesterName,
            nationId,
            sourceNation.name(),
            newNationName.trim(),
            region
        );

        requests.put(request.requestId(), request);

        // 发布事件
        SplitRequestCreatedEvent event = new SplitRequestCreatedEvent(
            request.requestId(),
            requesterId,
            requesterName,
            sourceNation,
            newNationName,
            region.chunkCount()
        );
        Bukkit.getServer().getPluginManager().callEvent(event);

        return SplitResult.requestCreated(request, cost.doubleValue());
    }

    @Override
    public SplitResult approveSplitRequest(UUID approverId, UUID requestId) {
        SplitRequest request = requests.get(requestId);
        if (request == null) {
            return SplitResult.failure("分裂请求不存在");
        }
        if (!request.isPending()) {
            return SplitResult.failure("分裂请求已处理");
        }

        // 检查审批者权限
        Nation sourceNation = nationService.nationById(request.sourceNationId()).orElse(null);
        if (sourceNation == null) {
            return SplitResult.failure("源国家不存在");
        }

        // 只有国家领导人可以批准
        if (!sourceNation.founderId().equals(approverId)) {
            return SplitResult.failure("只有国家领导人可以批准分裂请求");
        }

        // 检查冷却
        if (config.isCooldownEnabled()) {
            Long lastSplit = cooldowns.get(approverId);
            if (lastSplit != null) {
                long elapsed = System.currentTimeMillis() - lastSplit;
                if (elapsed < config.getCooldownMillis()) {
                    long remaining = config.getCooldownMillis() - elapsed;
                    return SplitResult.failure("分裂冷却中，请等待 " + formatDuration(remaining));
                }
            }
        }

        // 检查国家是否在战争中
        WarService warService = serviceRegistry.find(WarService.class).orElse(null);
        if (warService != null) {
            // 检查与所有其他国家的战争状态
            boolean atWar = nationService.nationIds().stream()
                .filter(id -> !id.equals(request.sourceNationId()))
                .anyMatch(otherId -> warService.atWar(request.sourceNationId(), otherId));
            if (atWar) {
                return SplitResult.failure("国家处于战争中，无法分裂");
            }
        }

        // 计算费用
        java.math.BigDecimal cost = calculateSplitCost(request.sourceNationId(), request.region());

        // 检查国库余额
        if (treasuryService != null) {
            java.math.BigDecimal balance = treasuryService.balance(request.sourceNationId());
            if (balance.compareTo(cost) < 0) {
                return SplitResult.failure("国库余额不足，分裂需要 " + cost + " 金币");
            }
        }

        // 审计 A-117: 先扣款再执行分裂，确保原子性
        if (treasuryService != null && cost.compareTo(java.math.BigDecimal.ZERO) > 0) {
            if (!treasuryService.withdraw(request.sourceNationId(), cost)) {
                return SplitResult.failure("扣款失败，分裂需要 " + cost + " 金币");
            }
        }

        // 触发分裂前事件
        NationSplitEvent splitEvent = new NationSplitEvent(
            sourceNation,
            request.requesterId(),
            request.newNationName(),
            request.region(),
            cost.doubleValue()
        );
        Bukkit.getServer().getPluginManager().callEvent(splitEvent);
        if (splitEvent.isCancelled()) {
            // 如果事件取消，退款
            if (treasuryService != null && cost.compareTo(java.math.BigDecimal.ZERO) > 0) {
                treasuryService.deposit(request.sourceNationId(), cost);
            }
            return SplitResult.failure(splitEvent.getCancelReason() != null ?
                splitEvent.getCancelReason() : "分裂被取消");
        }

        // 执行分裂
        SplitResult result = executeSplit(request);

        if (result.isSuccess()) {
            // 更新冷却
            cooldowns.put(approverId, System.currentTimeMillis());

            // 更新请求状态
            requests.put(requestId, request.approved(approverId));

            // 发布审批完成事件
            SplitRequestApprovedEvent approvedEvent = new SplitRequestApprovedEvent(
                requestId,
                approverId,
                sourceNation,
                result.newNationId() != null ?
                    nationService.nationById(result.newNationId()).orElse(null) : null,
                result.transferredChunks()
            );
            if (approvedEvent.getNewNation() != null) {
                Bukkit.getServer().getPluginManager().callEvent(approvedEvent);
            }
        } else {
            // 分裂失败，退款
            if (treasuryService != null && cost.compareTo(java.math.BigDecimal.ZERO) > 0) {
                treasuryService.deposit(request.sourceNationId(), cost);
            }
        }

        return result;
    }

    @Override
    public SplitResult rejectSplitRequest(UUID rejecterId, UUID requestId) {
        SplitRequest request = requests.get(requestId);
        if (request == null) {
            return SplitResult.failure("分裂请求不存在");
        }
        if (!request.isPending()) {
            return SplitResult.failure("分裂请求已处理");
        }

        // 检查权限
        Nation sourceNation = nationService.nationById(request.sourceNationId()).orElse(null);
        if (sourceNation == null) {
            return SplitResult.failure("源国家不存在");
        }

        // 只有国家领导人可以拒绝
        if (!sourceNation.founderId().equals(rejecterId) && !rejecterId.equals(request.requesterId())) {
            return SplitResult.failure("只有国家领导人或请求者可以拒绝分裂请求");
        }

        // 更新请求状态
        requests.put(requestId, request.rejected(rejecterId, "被拒绝"));

        return SplitResult.success(
            SplitResult.SplitResultType.REQUEST_REJECTED,
            "分裂请求已被拒绝",
            request.sourceNationId(),
            request.sourceNationName()
        );
    }

    @Override
    public SplitResult cancelSplitRequest(UUID requesterId, UUID requestId) {
        SplitRequest request = requests.get(requestId);
        if (request == null) {
            return SplitResult.failure("分裂请求不存在");
        }
        if (!request.isPending()) {
            return SplitResult.failure("分裂请求已处理");
        }

        // 只有请求者可以取消
        if (!request.requesterId().equals(requesterId)) {
            return SplitResult.failure("只有请求者可以取消分裂请求");
        }

        // 更新请求状态
        requests.put(requestId, request.cancelled());

        return SplitResult.success(
            SplitResult.SplitResultType.REQUEST_CANCELLED,
            "分裂请求已取消",
            request.sourceNationId(),
            request.sourceNationName()
        );
    }

    @Override
    public SplitResult forceSplit(NationId nationId, String newNationName, SplitRegion region) {
        // 设计决策：forceSplit 是管理员操作，已抛出 UnsupportedOperationException
        // 需在 Command 层双重校验或传入 caller 参数
        throw new UnsupportedOperationException("forceSplit requires admin permission; use admin command layer instead");

        /*
        // 创建临时请求
        Nation sourceNation = nationService.nationById(nationId).orElse(null);
        if (sourceNation == null) {
            return SplitResult.failure("国家不存在");
        }

        SplitRequest tempRequest = SplitRequest.pending(
            sourceNation.founderId(),
            "Administrator",
            nationId,
            sourceNation.name(),
            newNationName,
            region
        );

        return executeSplit(tempRequest);
        */
    }

    @Override
    public Collection<SplitRequest> getPendingRequests(UUID playerId) {
        return requests.values().stream()
            .filter(r -> r.isPending() && r.requesterId().equals(playerId))
            .toList();
    }

    @Override
    public Collection<SplitRequest> getPendingRequestsForNation(NationId nationId) {
        return requests.values().stream()
            .filter(r -> r.isPending() && r.sourceNationId().equals(nationId))
            .toList();
    }

    @Override
    public Optional<SplitRequest> getRequest(UUID requestId) {
        return Optional.ofNullable(requests.get(requestId));
    }

    @Override
    public String canInitiateSplit(UUID playerId, NationId nationId) {
        // 检查玩家是否属于该国家
        Optional<Nation> playerNation = nationService.nationOf(playerId);
        if (playerNation.isEmpty() || !playerNation.get().id().equals(nationId)) {
            return "你必须是该国家的成员才能发起分裂";
        }

        Nation nation = playerNation.get();

        // 检查是否是领导人或有分裂权限的官员
        boolean isLeader = nation.founderId().equals(playerId);
        boolean hasPermission = isLeader || hasSplitPermission(nationId, playerId);

        if (!hasPermission) {
            return "只有国家领导人或有分裂权限的官员才能发起分裂";
        }

        // 检查国家规模
        int chunks = nationService.claimCount(nationId);
        if (chunks < config.getMinChunksToSplit() * 2) {
            return "国家领土太少，无法分裂（至少需要 " + (config.getMinChunksToSplit() * 2) + " 个区块）";
        }

        // 检查冷却
        if (config.isCooldownEnabled()) {
            Long lastSplit = cooldowns.get(playerId);
            if (lastSplit != null) {
                long elapsed = System.currentTimeMillis() - lastSplit;
                if (elapsed < config.getCooldownMillis()) {
                    return "分裂冷却中，请等待 " + formatDuration(config.getCooldownMillis() - elapsed);
                }
            }
        }

        // 检查是否在战争中
        WarService warService = serviceRegistry.find(WarService.class).orElse(null);
        if (warService != null) {
            boolean atWar = nationService.nationIds().stream()
                .filter(id -> !id.equals(nationId))
                .anyMatch(otherId -> warService.atWar(nationId, otherId));
            if (atWar) {
                return "国家处于战争中，无法分裂";
            }
        }

        // 检查玩家是否已有待处理请求
        Collection<SplitRequest> pending = getPendingRequests(playerId);
        if (!pending.isEmpty()) {
            return "你已有待处理的分裂请求";
        }

        return null; // 可以发起分裂
    }

    @Override
    public java.math.BigDecimal calculateSplitCost(NationId nationId, SplitRegion region) {
        if (!config.isCostEnabled()) {
            return java.math.BigDecimal.ZERO;
        }

        // 基础费用 + 每区块费用
        double baseCost = config.getBaseCost();
        double perChunkCost = config.getPerChunkCost();
        return java.math.BigDecimal.valueOf(baseCost + (region.chunkCount() * perChunkCost));
    }

    @Override
    public void cleanupExpiredProposals() {
        long now = System.currentTimeMillis();
        long expirationMillis = config.getRequestExpirationMinutes() * 60 * 1000;

        requests.entrySet().removeIf(entry -> {
            SplitRequest request = entry.getValue();
            if (request.isPending()) {
                long elapsed = now - request.createdAt().toEpochMilli();
                return elapsed > expirationMillis;
            }
            return false;
        });
    }

    @Override
    public String summary() {
        long pending = requests.values().stream().filter(SplitRequest::isPending).count();
        return "Split system: " + pending + " pending requests";
    }

    /**
     * 执行国家分裂
     */
    private SplitResult executeSplit(SplitRequest request) {
        Nation sourceNation = nationService.nationById(request.sourceNationId()).orElse(null);
        if (sourceNation == null) {
            return SplitResult.failure("源国家不存在");
        }

        // 验证新国家名称
        String newName = request.newNationName().trim();
        if (nationService.nationByName(newName).isPresent()) {
            return SplitResult.failure("新国家名称已被使用");
        }

        // 创建新国家
        Nation newNation;
        try {
            newNation = nationService.createNation(
                request.requesterId(),
                request.requesterName(),
                newName
            );
        } catch (Exception e) {
            return SplitResult.failure("创建新国家失败: " + e.getMessage());
        }

        // 转移领土
        int transferred = 0;
        for (var chunk : request.region().chunks()) {
            var claim = nationService.claimAt(chunk.world(), chunk.x(), chunk.z()).orElse(null);
            if (claim != null && claim.ownerId().equals(request.sourceNationId().toString())) {
                // 解除原国家领地
                nationService.unclaimCurrentChunk(
                    request.requesterId(),
                    chunk.world(),
                    chunk.x(),
                    chunk.z()
                );
                // 尝试为新国家圈地
                try {
                    nationService.claimCurrentChunk(
                        request.requesterId(),
                        chunk.world(),
                        chunk.x(),
                        chunk.z()
                    );
                    transferred++;
                } catch (Exception e) {
                    // 忽略单个区块转移失败
                }
            }
        }

        // 计算实际费用
        java.math.BigDecimal cost = calculateSplitCost(request.sourceNationId(), request.region());

        return SplitResult.splitCompleted(sourceNation, newNation, transferred, cost.doubleValue());
    }

    /**
     * 检查玩家是否有分裂权限
     */
    private boolean hasSplitPermission(NationId nationId, UUID playerId) {
        // 可以扩展为检查官职权限
        return false;
    }

    /**
     * 格式化时间
     */
    private String formatDuration(long millis) {
        Duration duration = Duration.ofMillis(millis);
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        if (hours > 0) {
            return hours + "小时" + minutes + "分钟";
        }
        return minutes + "分钟";
    }
}