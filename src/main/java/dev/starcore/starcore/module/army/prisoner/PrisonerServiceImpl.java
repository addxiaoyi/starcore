package dev.starcore.starcore.module.army.prisoner;
import java.util.Optional;

import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.module.army.prisoner.event.PrisonerCapturedEvent;
import dev.starcore.starcore.module.army.prisoner.event.PrisonerEscapedEvent;
import dev.starcore.starcore.module.army.prisoner.event.PrisonerExecutedEvent;
import dev.starcore.starcore.module.army.prisoner.event.PrisonerExchangedEvent;
import dev.starcore.starcore.module.army.prisoner.event.PrisonerReleasedEvent;
import dev.starcore.starcore.module.army.prisoner.model.PrisonerOfWar;
import dev.starcore.starcore.module.army.prisoner.model.PrisonerStatus;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.war.WarService;
import dev.starcore.starcore.nation.permission.NationPermission;
import dev.starcore.starcore.nation.permission.NationPermissionChecker;
import dev.starcore.starcore.nation.permission.PermissionLevel;
import dev.starcore.starcore.nation.rank.NationRank;
import dev.starcore.starcore.nation.rank.NationRankManager;
import dev.starcore.starcore.util.PermissionUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 俘虏服务实现
 */
public final class PrisonerServiceImpl implements PrisonerService {

    private final Plugin plugin;
    private final MessageService messages;
    private final WarService warService;
    private final NationService nationService;
    private final NationRankManager rankManager;
    private final NationPermissionChecker permissionChecker;
    private final PrisonerService.PrisonerConfig config;

    private final Map<UUID, PrisonerOfWar> prisonersById = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> playerToPrisoner = new ConcurrentHashMap<>(); // playerId -> prisonerId
    private final Map<UUID, Set<UUID>> nationPrisoners = new ConcurrentHashMap<>(); // nationId -> Set<prisonerId>
    private final Map<UUID, Set<UUID>> nationCaptives = new ConcurrentHashMap<>(); // nationId -> Set<prisonerId>

    private final AtomicInteger totalPrisoners = new AtomicInteger(0);

    public PrisonerServiceImpl(
            Plugin plugin,
            MessageService messages,
            WarService warService,
            NationService nationService,
            NationRankManager rankManager,
            NationPermissionChecker permissionChecker,
            PrisonerService.PrisonerConfig config
    ) {
        this.plugin = plugin;
        this.messages = messages;
        this.warService = warService;
        this.nationService = nationService;
        this.rankManager = rankManager;
        this.permissionChecker = permissionChecker;
        this.config = config;
        loadAll();
    }

    @Override
    public PrisonerOfWar capturePrisoner(
        UUID captorPlayerId,
        String captorPlayerName,
        UUID targetPlayerId,
        String targetPlayerName,
        UUID targetNationId,
        String targetNationName,
        UUID captorNationId,
        String captorNationName,
        int ransom
    ) {
        return capturePrisonerWithBattle(
            captorPlayerId, captorPlayerName,
            targetPlayerId, targetPlayerName,
            targetNationId, targetNationName,
            captorNationId, captorNationName,
            ransom, null
        );
    }

    @Override
    public PrisonerOfWar capturePrisonerWithBattle(
        UUID captorPlayerId,
        String captorPlayerName,
        UUID targetPlayerId,
        String targetPlayerName,
        UUID targetNationId,
        String targetNationName,
        UUID captorNationId,
        String captorNationName,
        int ransom,
        UUID battleId
    ) {
        // 检查是否已经是被俘虏状态
        if (isPrisoner(targetPlayerId)) {
            PrisonerOfWar existing = getPrisonerByPlayer(targetPlayerId).orElse(null);
            if (existing != null && existing.status().isActive()) {
                // 俘虏国变更
                PrisonerOfWar transferred = existing;
                updatePrisoner(transferred);
                return transferred;
            }
        }

        // 检查俘虏数量限制
        if (nationCaptives.computeIfAbsent(captorNationId, k -> ConcurrentHashMap.newKeySet()).size() >= config.maxPrisonersPerNation()) {
            throw new IllegalStateException("俘虏数量已达上限 (" + config.maxPrisonersPerNation() + ")");
        }

        // 计算赎金
        double finalRansom = ransom > 0 ? ransom : config.baseRansom();

        // 创建俘虏记录
        PrisonerOfWar prisoner = PrisonerOfWar.create(
            targetPlayerId, targetPlayerName,
            captorPlayerId, captorPlayerName,
            captorNationId, targetNationId
        );

        // 保存到内存
        prisonersById.put(prisoner.id(), prisoner);
        playerToPrisoner.put(targetPlayerId, prisoner.id());
        nationCaptives.computeIfAbsent(captorNationId, k -> ConcurrentHashMap.newKeySet()).add(prisoner.id());
        nationPrisoners.computeIfAbsent(targetNationId, k -> ConcurrentHashMap.newKeySet()).add(prisoner.id());
        totalPrisoners.incrementAndGet();

        // 触发事件
        Bukkit.getPluginManager().callEvent(new PrisonerCapturedEvent(prisoner));

        return prisoner;
    }

    @Override
    public PrisonerOfWar releasePrisoner(UUID prisonerId, UUID releaserId) {
        PrisonerOfWar prisoner = getPrisoner(prisonerId).orElse(null);
        if (prisoner == null || !prisoner.status().isActive()) {
            throw new IllegalStateException("俘虏不存在或状态不正确");
        }

        // 权限校验：必须是俘虏国成员才可释放
        if (!hasPrisonerPermission(releaserId, prisoner.captorNationId(), NationPermission.PRISONER_RELEASE)) {
            throw new SecurityException("你没有权限释放俘虏");
        }

        // 实际改变状态
        PrisonerOfWar released = prisoner
                .withStatus(PrisonerStatus.RELEASED)
                .withReleaseTime(java.time.Instant.now());
        updatePrisoner(released);

        // 触发事件
        Bukkit.getPluginManager().callEvent(new PrisonerReleasedEvent(released, PrisonerReleasedEvent.ReleaseReason.MERCY, releaserId));

        return released;
    }

    @Override
    public PrisonerOfWar executePrisoner(UUID prisonerId, UUID executorId) {
        if (!config.allowExecution()) {
            throw new IllegalStateException("系统不允许处决俘虏");
        }

        PrisonerOfWar prisoner = getPrisoner(prisonerId).orElse(null);
        if (prisoner == null || !prisoner.status().isActive()) {
            throw new IllegalStateException("俘虏不存在或状态不正确");
        }

        // 权限校验：必须是俘虏国成员且有 LEADER 级别
        if (!hasPrisonerPermission(executorId, prisoner.captorNationId(), NationPermission.PRISONER_EXECUTE)) {
            throw new SecurityException("你没有权限处决俘虏");
        }

        // 实际改变状态
        PrisonerOfWar executed = prisoner.withStatus(PrisonerStatus.DECEASED);
        updatePrisoner(executed);

        // 触发事件
        Bukkit.getPluginManager().callEvent(new PrisonerExecutedEvent(executed, executorId, ""));

        return executed;
    }

    @Override
    public PrisonerOfWar releaseOnRansom(UUID prisonerId, UUID payerId) {
        PrisonerOfWar prisoner = getPrisoner(prisonerId).orElse(null);
        if (prisoner == null || !prisoner.status().isActive()) {
            throw new IllegalStateException("俘虏不存在或状态不正确");
        }

        PrisonerOfWar released = prisoner
                .withStatus(PrisonerStatus.RELEASED)
                .withReleaseTime(java.time.Instant.now());
        updatePrisoner(released);

        // 触发事件
        Bukkit.getPluginManager().callEvent(new PrisonerReleasedEvent(released, PrisonerReleasedEvent.ReleaseReason.RANSOM_PAID, payerId));

        return released;
    }

    @Override
    public PrisonerOfWar[] exchangePrisoners(UUID prisonerId1, UUID prisonerId2, UUID exchangerId) {
        if (!config.allowExchange()) {
            throw new IllegalStateException("系统不允许交换俘虏");
        }

        PrisonerOfWar prisoner1 = getPrisoner(prisonerId1).orElse(null);
        PrisonerOfWar prisoner2 = getPrisoner(prisonerId2).orElse(null);

        if (prisoner1 == null || prisoner2 == null) {
            throw new IllegalStateException("俘虏不存在");
        }

        if (!prisoner1.status().isActive() || !prisoner2.status().isActive()) {
            throw new IllegalStateException("俘虏状态不正确");
        }

        // 权限校验：必须是俘虏国1或俘虏国2的成员
        if (!hasPrisonerPermission(exchangerId, prisoner1.captorNationId(), NationPermission.PRISONER_RELEASE)
                && !hasPrisonerPermission(exchangerId, prisoner2.captorNationId(), NationPermission.PRISONER_RELEASE)) {
            throw new SecurityException("你没有权限交换俘虏");
        }

        // 交换俘虏国
        UUID nation1Id = prisoner1.captorNationId();
        UUID nation2Id = prisoner2.captorNationId();

        // 先从旧俘虏国移除
        nationCaptives.getOrDefault(prisoner1.captorNationId(), Collections.emptySet()).remove(prisonerId1);
        nationCaptives.getOrDefault(prisoner2.captorNationId(), Collections.emptySet()).remove(prisonerId2);

        // 交换国家ID并更新记录
        PrisonerOfWar newPrisoner1 = prisoner1.withStatus(PrisonerStatus.AWAITING_EXCHANGE)
                .withCaptorNationId(nation2Id);
        PrisonerOfWar newPrisoner2 = prisoner2.withStatus(PrisonerStatus.AWAITING_EXCHANGE)
                .withCaptorNationId(nation1Id);

        updatePrisoner(newPrisoner1);
        updatePrisoner(newPrisoner2);

        // 添加到新俘虏国
        nationCaptives.computeIfAbsent(nation2Id, k -> ConcurrentHashMap.newKeySet()).add(prisonerId1);
        nationCaptives.computeIfAbsent(nation1Id, k -> ConcurrentHashMap.newKeySet()).add(prisonerId2);

        // 触发事件
        Bukkit.getPluginManager().callEvent(new PrisonerExchangedEvent(newPrisoner1, newPrisoner2, exchangerId));

        return new PrisonerOfWar[]{newPrisoner1, newPrisoner2};
    }

    @Override
    public PrisonerOfWar startEscape(UUID prisonerId) {
        PrisonerOfWar prisoner = getPrisoner(prisonerId).orElse(null);
        if (prisoner == null || !prisoner.status().isActive()) {
            throw new IllegalStateException("俘虏不存在或状态不正确");
        }

        PrisonerOfWar escaping = prisoner.withStatus(PrisonerStatus.ESCAPED);
        updatePrisoner(escaping);

        return escaping;
    }

    @Override
    public PrisonerOfWar completeEscape(UUID prisonerId) {
        PrisonerOfWar prisoner = getPrisoner(prisonerId).orElse(null);
        if (prisoner == null || prisoner.status() != PrisonerStatus.ESCAPED) {
            throw new IllegalStateException("俘虏不在逃跑状态");
        }

        // 逃跑完成，从所有俘虏记录中移除
        removePrisoner(prisonerId);

        // 触发事件
        Bukkit.getPluginManager().callEvent(new PrisonerEscapedEvent(prisoner, prisoner.captorNationId()));

        return prisoner;
    }

    @Override
    public Optional<PrisonerOfWar> getPrisoner(UUID prisonerId) {
        return Optional.ofNullable(prisonersById.get(prisonerId));
    }

    @Override
    public Optional<PrisonerOfWar> getPrisonerByPlayer(UUID playerId) {
        UUID prisonerId = playerToPrisoner.get(playerId);
        return prisonerId != null ? getPrisoner(prisonerId) : Optional.empty();
    }

    @Override
    public Collection<PrisonerOfWar> getNationPrisoners(UUID nationId) {
        Set<UUID> ids = nationPrisoners.getOrDefault(nationId, Collections.emptySet());
        return ids.stream()
            .map(prisonersById::get)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    @Override
    public Collection<PrisonerOfWar> getNationCapturedMembers(UUID nationId) {
        Set<UUID> ids = nationCaptives.getOrDefault(nationId, Collections.emptySet());
        return ids.stream()
            .map(prisonersById::get)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    @Override
    public PrisonerOfWar transferPrisoner(
        UUID prisonerId,
        UUID newNationId,
        String newNationName,
        UUID newCaptorPlayerId,
        String newCaptorPlayerName
    ) {
        PrisonerOfWar prisoner = getPrisoner(prisonerId).orElse(null);
        if (prisoner == null || !prisoner.status().isActive()) {
            throw new IllegalStateException("俘虏不存在或状态不正确");
        }

        // 权限校验：仅俘虏国成员可转移
        if (!hasPrisonerPermission(newCaptorPlayerId, prisoner.captorNationId(), NationPermission.PRISONER_RELEASE)) {
            throw new SecurityException("你没有权限转移俘虏");
        }

        // 从旧俘虏国移除
        nationCaptives.getOrDefault(prisoner.captorNationId(), Collections.emptySet()).remove(prisonerId);

        // 构建新记录
        PrisonerOfWar transferred = prisoner
                .withCaptorNationId(newNationId)
                .withCaptorId(newCaptorPlayerId)
                .withCaptorPlayerName(newCaptorPlayerName);
        updatePrisoner(transferred);

        // 添加到新俘虏国
        nationCaptives.computeIfAbsent(newNationId, k -> ConcurrentHashMap.newKeySet()).add(prisonerId);

        return transferred;
    }

    @Override
    public PrisonerOfWar setPrisonerNotes(UUID prisonerId, String notes) {
        PrisonerOfWar prisoner = getPrisoner(prisonerId).orElse(null);
        if (prisoner == null) {
            throw new IllegalStateException("俘虏不存在");
        }

        PrisonerOfWar updated = prisoner.withNotes(notes);
        updatePrisoner(updated);

        return updated;
    }

    @Override
    public boolean isPrisoner(UUID playerId) {
        return getPrisonerByPlayer(playerId)
            .map(PrisonerOfWar::isActive)
            .orElse(false);
    }

    @Override
    public boolean hasPrisonerRelation(UUID nationId1, UUID nationId2) {
        // 检查 nationId1 是否俘虏了 nationId2 的人
        Collection<PrisonerOfWar> captives = getNationCapturedMembers(nationId1);
        for (PrisonerOfWar p : captives) {
            if (p.capturedNationId().equals(nationId2)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int getTotalPrisonerCount() {
        return totalPrisoners.get();
    }

    @Override
    public int getNationPrisonerCount(UUID nationId) {
        return nationCaptives.getOrDefault(nationId, Collections.emptySet()).size();
    }

    @Override
    public void removePrisoner(UUID prisonerId) {
        PrisonerOfWar prisoner = prisonersById.remove(prisonerId);
        if (prisoner != null) {
            playerToPrisoner.remove(prisoner.id());
            nationCaptives.getOrDefault(prisoner.captorNationId(), Collections.emptySet()).remove(prisonerId);
            nationPrisoners.getOrDefault(prisoner.capturedNationId(), Collections.emptySet()).remove(prisonerId);
            totalPrisoners.decrementAndGet();
        }
    }

    @Override
    public void saveAll() {
        Path dataPath = plugin.getDataFolder().toPath().resolve("prisoners");
        try {
            Files.createDirectories(dataPath);
            Path file = dataPath.resolve("prisoners.dat");

            try (ObjectOutputStream oos = new ObjectOutputStream(
                new BufferedOutputStream(Files.newOutputStream(file)))) {
                oos.writeObject(new ArrayList<>(prisonersById.values()));
            }
            plugin.getLogger().info("Saved " + prisonersById.size() + " prisoner records");
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save prisoner data: " + e.getMessage());
        }
    }

    /**
     * 安全的对象输入流 - 白名单验证
     * 防止反序列化漏洞 (CWE-502)
     */
    private static class SafeObjectInputStream extends ObjectInputStream {
        private static final Set<String> ALLOWED_CLASSES = Set.of(
            "dev.starcore.starcore.module.army.prisoner.model.PrisonerOfWar",
            "java.util.ArrayList",
            "java.util.HashSet",
            "java.util.LinkedHashSet",
            "java.util.concurrent.ConcurrentHashMap",
            "java.util.UUID",
            "java.time.Instant",
            "java.lang.String",
            "java.lang.Integer",
            "java.lang.Boolean",
            "java.lang.Double"
        );

        private SafeObjectInputStream(InputStream in) throws IOException {
            super(in);
        }

        @Override
        protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
            String className = desc.getName();
            if (!ALLOWED_CLASSES.contains(className)) {
                throw new IOException("不允许反序列化类: " + className + " - 可能存在安全风险");
            }
            return super.resolveClass(desc);
        }
    }

    @SuppressWarnings("unchecked")
    public void loadAll() {
        Path dataPath = plugin.getDataFolder().toPath().resolve("prisoners");
        Path file = dataPath.resolve("prisoners.dat");

        if (!Files.exists(file)) {
            return;
        }

        try (SafeObjectInputStream ois = new SafeObjectInputStream(
            new BufferedInputStream(Files.newInputStream(file)))) {
            List<PrisonerOfWar> loaded = (List<PrisonerOfWar>) ois.readObject();

            for (PrisonerOfWar prisoner : loaded) {
                // 验证数据完整性
                if (prisoner == null || prisoner.id() == null || prisoner.prisonerId() == null) {
                    plugin.getLogger().warning("跳过无效的俘虏数据记录");
                    continue;
                }
                prisonersById.put(prisoner.id(), prisoner);
                playerToPrisoner.put(prisoner.prisonerId(), prisoner.id());
                nationCaptives.computeIfAbsent(prisoner.captorNationId(), k -> ConcurrentHashMap.newKeySet()).add(prisoner.id());
                nationPrisoners.computeIfAbsent(prisoner.capturedNationId(), k -> ConcurrentHashMap.newKeySet()).add(prisoner.id());
                totalPrisoners.incrementAndGet();
            }

            plugin.getLogger().info("Loaded " + loaded.size() + " prisoner records");
        } catch (IOException | ClassNotFoundException e) {
            plugin.getLogger().severe("Failed to load prisoner data: " + e.getMessage());
        } catch (Exception e) {
            plugin.getLogger().severe("安全检查失败，无法加载俘虏数据: " + e.getMessage());
        }
    }

    @Override
    public PrisonerService.PrisonerConfig getConfig() {
        return config;
    }

    private void updatePrisoner(PrisonerOfWar prisoner) {
        prisonersById.put(prisoner.id(), prisoner);
    }

    @Override
    public String summary() {
        return String.format("Prisoners: %d total, %d active",
            totalPrisoners.get(),
            prisonersById.values().stream().filter(PrisonerOfWar::isActive).count()
        );
    }

    @Override
    public void shutdown() {
        saveAll();
        prisonersById.clear();
        playerToPrisoner.clear();
        nationPrisoners.clear();
        nationCaptives.clear();
    }

    /**
     * 战争结束时释放所有相关俘虏
     */
    public void releasePrisonersOnWarEnd(UUID nation1Id, UUID nation2Id) {
        if (!config.autoReleaseOnWarEnd()) {
            return;
        }

        // 释放 nation1 俘虏的 nation2 的俘虏
        Collection<PrisonerOfWar> captives = getNationCapturedMembers(nation1Id);
        for (PrisonerOfWar prisoner : captives) {
            if (prisoner.capturedNationId().equals(nation2Id)) {
                releasePrisoner(prisoner.id(), null);
            }
        }

        // 释放 nation2 俘虏的 nation1 的俘虏
        Collection<PrisonerOfWar> captives2 = getNationCapturedMembers(nation2Id);
        for (PrisonerOfWar prisoner : captives2) {
            if (prisoner.capturedNationId().equals(nation1Id)) {
                releasePrisoner(prisoner.id(), null);
            }
        }
    }

    /**
     * 权限校验：检查玩家是否在指定国家拥有指定权限
     *
     * @param playerId 玩家UUID（可以为 null，表示系统调用，跳过权限校验）
     * @param nationId 国家UUID
     * @param permission 需要的权限
     * @return 是否有权限
     */
    private boolean hasPrisonerPermission(UUID playerId, UUID nationId, NationPermission permission) {
        // 系统调用（war end 等）跳过权限校验
        if (playerId == null) {
            return true;
        }

        Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            // 离线玩家：手动推断 level，仅支持 FOUNDER 和 MEMBER
            var nationOpt = nationService.nationOf(playerId);
            if (nationOpt.isEmpty() || !nationOpt.get().id().value().equals(nationId)) {
                return false;
            }
            var nation = nationOpt.get();
            boolean isFounder = nation.founderId().equals(playerId);
            boolean isMember = nation.hasMember(playerId);
            if (!isMember && !isFounder) {
                return false;
            }
            PermissionLevel level = isFounder ? PermissionLevel.FOUNDER : PermissionLevel.MEMBER;
            NationRank rank = rankManager.getPlayerRank(playerId, nation.id().value());
            return permissionChecker.hasPermission(null, permission, level, rank);
        }

        // hasNationPermission 内部已检查玩家是否属于目标国家
        return PermissionUtil.hasNationPermission(player, nationId, permission, null);
    }
}