package dev.starcore.starcore.module.resolution;

import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.module.ModuleLayer;
import dev.starcore.starcore.core.module.ModuleMetadata;
import dev.starcore.starcore.core.module.StarCoreModule;
import dev.starcore.starcore.module.diplomacy.DiplomacyRelation;
import dev.starcore.starcore.module.diplomacy.DiplomacyService;
import dev.starcore.starcore.module.government.GovernmentService;
import dev.starcore.starcore.module.government.model.GovernmentType;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.resolution.model.ChangeDiplomacyRelationAction;
import dev.starcore.starcore.module.resolution.model.ChangeGovernmentAction;
import dev.starcore.starcore.module.resolution.model.JoinNationRequestAction;
import dev.starcore.starcore.module.resolution.model.RenameNationAction;
import dev.starcore.starcore.module.resolution.model.Resolution;
import dev.starcore.starcore.module.resolution.model.ResolutionAction;
import dev.starcore.starcore.module.resolution.model.ResolutionKind;
import dev.starcore.starcore.module.resolution.model.ResolutionState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.plugin.java.JavaPlugin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class ResolutionModule implements StarCoreModule, ResolutionService {
    private static final String FILE_NAME = "resolutions.properties";

    private static final ModuleMetadata METADATA = new ModuleMetadata(
        "resolution",
        "决议引擎",
        ModuleLayer.MODULE,
        List.of("nation", "government"),
        List.of(ResolutionService.class),
        "Cross-module governance workflow for proposals, voting, and enactment."
    );

    private static final Logger LOGGER = LoggerFactory.getLogger(ResolutionModule.class);

    private final Map<UUID, Resolution> resolutions = new LinkedHashMap<>();
    // audit B-167/B-168/B-183: Resolution 不是线程安全；用全表锁保证 sign 内的 sign/markEnacted/execute 原子
    private final Object resolutionsLock = new Object();
    private StarCoreContext context;
    private ResolutionStateStorage stateStorage;
    private NationService nationService;
    private GovernmentService governmentService;
    private ResolutionGuiListener guiListener;
    private ResolutionChatListener chatListener;

    @Override
    public ModuleMetadata metadata() {
        return METADATA;
    }

    @Override
    public void enable(StarCoreContext context) {
        context.persistenceService().ensureNamespace(metadata().id());
        this.context = context;
        this.stateStorage = new DatabaseAwareResolutionStateStorage(
            metadata().id(),
            context.databaseService(),
            context.persistenceService(),
            context.plugin().getLogger()
        );
        this.nationService = context.serviceRegistry().require(NationService.class);
        this.governmentService = context.serviceRegistry().require(GovernmentService.class);
        loadState();

        // Register GUI listener
        registerGuiListener(context);

        LOGGER.info("ResolutionModule enabled successfully with {} resolution(s).", resolutions.size());
    }

    /**
     * Get the GUI listener for chat input processing
     */
    public ResolutionGuiListener getGuiListener() {
        return guiListener;
    }

    /**
     * Register GUI event listeners
     */
    private void registerGuiListener(StarCoreContext context) {
        JavaPlugin plugin = context.plugin();

        // Create and register GUI listener
        this.guiListener = new ResolutionGuiListener(this, nationService, governmentService);
        plugin.getServer().getPluginManager().registerEvents(guiListener, plugin);

        // Create and register chat listener for input handling
        ResolutionInputHandler inputHandler = guiListener.getInputHandler();
        this.chatListener = new ResolutionChatListener(inputHandler);
        plugin.getServer().getPluginManager().registerEvents(chatListener, plugin);

        LOGGER.info("Resolution GUI and chat listeners registered.");
    }

    @Override
    public void disable(StarCoreContext context) {
        flushState();

        // audit B-181: 通过 HandlerList.unregisterAll 解除 GUI/chat 监听器，避免热重载插件时重复注册
        if (guiListener != null) {
            org.bukkit.event.HandlerList.unregisterAll(guiListener);
            guiListener = null;
        }
        if (chatListener != null) {
            org.bukkit.event.HandlerList.unregisterAll(chatListener);
            chatListener = null;
        }

        LOGGER.info("ResolutionModule disabled.");
    }

    @Override
    public Resolution proposeJoin(Nation nation, UUID proposerId, String proposerName, UUID applicantId, String applicantName) {
        return createResolution(nation, proposerId, proposerName, new JoinNationRequestAction(nation.id(), applicantId, applicantName));
    }

    @Override
    public Resolution proposeRename(Nation nation, UUID proposerId, String proposerName, String newName) {
        return createResolution(nation, proposerId, proposerName, new RenameNationAction(nation.id(), nation.name(), newName));
    }

    @Override
    public Resolution proposeGovernmentChange(Nation nation, UUID proposerId, String proposerName, GovernmentType targetType) {
        return createResolution(nation, proposerId, proposerName, new ChangeGovernmentAction(nation.id(), nation.governmentType(), targetType));
    }

    @Override
    public Resolution proposeDiplomacyChange(Nation nation, UUID proposerId, String proposerName, Nation targetNation, DiplomacyRelation relation) {
        return createResolution(nation, proposerId, proposerName, new ChangeDiplomacyRelationAction(nation.id(), targetNation.id(), targetNation.name(), relation));
    }

    @Override
    public Optional<Resolution> find(UUID resolutionId) {
        expireIfNeeded();
        return Optional.ofNullable(resolutions.get(resolutionId));
    }

    @Override
    public Collection<Resolution> openResolutions(Nation nation) {
        expireIfNeeded();
        return List.copyOf(resolutions.values().stream()
            .filter(Resolution::isOpen)
            .filter(resolution -> resolution.nationId().equals(nation.id()))
            .toList());
    }

    @Override
    public boolean sign(UUID signerId, UUID resolutionId) {
        // audit B-167/B-168/B-183: 全表同步避免并发签名导致重复执行同一动作
        synchronized (resolutionsLock) {
            expireIfNeeded();
            Resolution resolution = resolutions.get(resolutionId);
            if (resolution == null || !resolution.isOpen()) {
                return false;
            }
            Nation nation = nationService.nationById(resolution.nationId()).orElse(null);
            if (nation == null || !governmentService.maySign(nation, signerId, resolution)) {
                return false;
            }
            if (!resolution.sign(signerId)) {
                return false;
            }
            if (!governmentService.resolutionPasses(nation, resolution)) {
                saveState();
                return true;
            }
            // audit B-167: 进入 markEnacted 前 double-check isOpen 防止另一线程已置为非 OPEN
            if (!resolution.isOpen()) {
                saveState();
                return true;
            }
            DiplomacyService diplomacyService = context.serviceRegistry().find(DiplomacyService.class).orElse(null);
            boolean executed;
            try {
                executed = resolution.action().execute(nationService, governmentService, diplomacyService);
            } catch (RuntimeException e) {
                // audit B-168: 保留异常信息并通过广播通知；标记 FAILED
                resolution.markFailed();
                saveState();
                String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                broadcastResolutionFailed(resolution, nation, "执行异常: " + reason);
                LOGGER.warn("Resolution {} execute failed", resolutionId, e);
                return false;
            }
            if (executed) {
                resolution.markEnacted();
                saveState();
                broadcastResolutionEnacted(resolution, nation);
                return true;
            }
            // audit B-168: 执行失败需向国家成员广播 FAILED 与失败原因
            resolution.markFailed();
            saveState();
            broadcastResolutionFailed(resolution, nation, "执行返回 false");
            return false;
        }
    }

    /**
     * audit B-168: 决议执行失败时向国家成员广播 FAILED 与失败原因
     */
    private void broadcastResolutionFailed(Resolution resolution, Nation nation, String reason) {
        if (context == null || context.plugin() == null) {
            return;
        }
        String message = String.format(
            "[Resolution] %s %s FAILED for %s: %s",
            resolution.action().kind().name(),
            resolution.action().summary(),
            nation.name(),
            reason
        );
        // audit B-172: 仅向国家成员广播而非全服，避免敏感决议泄露给非成员
        sendToNationMembers(nation, Component.text(message, NamedTextColor.RED));
    }

    /**
     * audit B-172: 仅向国家成员广播消息（外交/敏感决议不外泄）
     */
    private void sendToNationMembers(Nation nation, Component message) {
        for (var member : nation.members()) {
            org.bukkit.entity.Player p = context.plugin().getServer().getPlayer(member.playerId());
            if (p != null) {
                p.sendMessage(message);
            }
        }
    }

    @Override
    public boolean cancel(UUID resolutionId, UUID cancellerId) {
        synchronized (resolutionsLock) {
            expireIfNeeded();
            Resolution resolution = resolutions.get(resolutionId);
            if (resolution == null || !resolution.isOpen()) {
                return false;
            }
            boolean isProposer = resolution.proposerId().equals(cancellerId);
            // audit B-170: 允许国家 admin 等级成员或 starcore.admin 取消任意决议，避免提议人退出/降级后无法清理恶意提案
            boolean canManage = false;
            org.bukkit.entity.Player canceller = context.plugin().getServer().getPlayer(cancellerId);
            if (canceller != null && canceller.hasPermission("starcore.admin")) {
                canManage = true;
            } else if (!isProposer) {
                Nation nation = nationService.nationById(resolution.nationId()).orElse(null);
                if (nation != null) {
                    canManage = nation.members().stream()
                        .filter(m -> m.playerId().equals(cancellerId))
                        .anyMatch(m -> "admin".equals(m.rank()) || nation.founderId().equals(cancellerId));
                }
            }
            if (!isProposer && !canManage) {
                return false;
            }
            resolution.markCancelled();
            saveState();
            return true;
        }
    }

    @Override
    public List<Resolution> history(Nation nation) {
        return resolutions.values().stream()
            .filter(res -> !res.isOpen())
            .filter(res -> res.nationId().equals(nation.id()))
            .sorted((a, b) -> b.createdAt().compareTo(a.createdAt()))
            .toList();
    }

    @Override
    public List<Resolution> proposedBy(UUID playerId) {
        return resolutions.values().stream()
            .filter(res -> res.proposerId().equals(playerId))
            .sorted((a, b) -> b.createdAt().compareTo(a.createdAt()))
            .toList();
    }

    @Override
    public List<Resolution> signedBy(UUID playerId) {
        return resolutions.values().stream()
            .filter(res -> res.signatures().contains(playerId))
            .sorted((a, b) -> b.createdAt().compareTo(a.createdAt()))
            .toList();
    }

    @Override
    public String details(UUID resolutionId) {
        Resolution resolution = resolutions.get(resolutionId);
        if (resolution == null) {
            return "Resolution not found";
        }
        Nation nation = nationService.nationById(resolution.nationId()).orElse(null);
        String nationName = nation == null ? resolution.nationId().toString() : nation.name();
        return String.format(
            "Resolution %s [ %s ]%nNation: %s%nProposer: %s%nType: %s%n%s%nSignatures: %d/%d%nStatus: %s%nCreated: %s%nExpires: %s",
            resolution.id(),
            resolution.action().kind().name(),
            nationName,
            resolution.proposerName(),
            resolution.action().summary(),
            resolution.state() == ResolutionState.ENACTED ? "(ENACTED)" : "",
            resolution.signatures().size(),
            governmentService.requiredSignatures(nation, resolution),
            resolution.state().name(),
            resolution.createdAt(),
            resolution.expiresAt()
        );
    }

    private void broadcastResolutionEnacted(Resolution resolution, Nation nation) {
        if (context == null || context.plugin() == null) {
            return;
        }
        String message = String.format(
            "[Resolution] %s %s has been enacted for %s",
            resolution.action().kind().name(),
            resolution.action().summary(),
            nation.name()
        );
        context.plugin().getServer().broadcast(Component.text(message, NamedTextColor.GOLD));
    }

    @Override
    public String summary() {
        expireIfNeeded();
        long open = resolutions.values().stream().filter(Resolution::isOpen).count();
        return open + " open resolution(s), " + resolutions.size() + " total";
    }

    private Resolution createResolution(Nation nation, UUID proposerId, String proposerName, ResolutionAction action) {
        if (!governmentService.mayPropose(nation, proposerId, action)) {
            throw new IllegalStateException("current government does not allow this proposer for " + action.kind());
        }
        // audit B-169: 限制每国家每类型同时一个 OPEN 提案，防止恶意刷屏待签列表
        ResolutionKind kind = action.kind();
        for (Resolution existing : resolutions.values()) {
            if (existing.isOpen()
                && existing.nationId().equals(nation.id())
                && existing.action().kind() == kind) {
                throw new IllegalStateException("已经存在同类型的开放决议，请等其结束或取消后再创建: " + existing.id());
            }
        }
        Resolution resolution = new Resolution(
            nation.id(),
            proposerId,
            proposerName,
            action,
            Instant.now(),
            Instant.now().plus(Duration.ofDays(3))
        );
        synchronized (resolutionsLock) {
            resolutions.put(resolution.id(), resolution);
            saveState();
        }
        return resolution;
    }

    private void expireIfNeeded() {
        Instant now = Instant.now();
        boolean changed = false;
        for (Resolution resolution : resolutions.values()) {
            if (resolution.isOpen() && resolution.isExpired(now)) {
                resolution.markExpired();
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
        stateStorage.saveAsync(ResolutionStateCodec.toProperties(resolutions.values()));
    }

    private void flushState() {
        if (stateStorage == null) {
            return;
        }
        stateStorage.save(ResolutionStateCodec.toProperties(resolutions.values()));
    }

    private void loadState() {
        resolutions.clear();
        ResolutionStateCodec.fromProperties(stateStorage == null ? new java.util.Properties() : stateStorage.load())
            .forEach(resolution -> resolutions.put(resolution.id(), resolution));
        expireIfNeeded();
    }
}
