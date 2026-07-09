package dev.starcore.starcore.module.nation;

import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.config.ConfigurationService;
import dev.starcore.starcore.core.module.ModuleLayer;
import dev.starcore.starcore.core.module.ModuleMetadata;
import dev.starcore.starcore.core.module.StarCoreModule;
import dev.starcore.starcore.core.persistence.PersistenceService;
import dev.starcore.starcore.foundation.economy.InternalEconomyService;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.foundation.player.OnlinePlayerDirectory;
import dev.starcore.starcore.foundation.player.PlayerProfileService;
import dev.starcore.starcore.foundation.protection.ExternalProtectionService;
import dev.starcore.starcore.foundation.protection.ProtectionConflict;
import dev.starcore.starcore.foundation.territory.TerritoryService;
import dev.starcore.starcore.module.diplomacy.DiplomacyService;
import dev.starcore.starcore.module.technology.TechnologyService;
import dev.starcore.starcore.foundation.territory.model.ChunkClaimSelection;
import dev.starcore.starcore.foundation.territory.model.ChunkCoordinate;
import dev.starcore.starcore.foundation.territory.model.TerritoryClaim;
import dev.starcore.starcore.module.nation.claimtool.ClaimToolListener;
import dev.starcore.starcore.module.nation.claimtool.ClaimToolService;
import dev.starcore.starcore.module.nation.claimtool.NativeClaimToolService;
import dev.starcore.starcore.module.nation.model.ClaimChunkPrice;
import dev.starcore.starcore.module.nation.model.ClaimSelectionExplanation;
import dev.starcore.starcore.module.nation.model.ClaimSelectionPreview;
import dev.starcore.starcore.module.nation.model.ClaimSelectionResult;
import dev.starcore.starcore.module.nation.model.ClaimPriceBreakdown;
import dev.starcore.starcore.module.nation.model.ClaimSelectionReason;
import dev.starcore.starcore.module.government.model.GovernmentType;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.nation.model.NationKind;
import dev.starcore.starcore.module.nation.model.NationMember;
import dev.starcore.starcore.module.nation.protection.NativeTerritoryProtectionListener;
import dev.starcore.starcore.module.nation.resource.NationResourceDistrictService;
import dev.starcore.starcore.module.nation.resource.NativeNationResourceDistrictService;
import dev.starcore.starcore.module.nation.statusbar.NationStatusBarService;
import dev.starcore.starcore.module.nation.statusbar.NationStatusBarListener;
import dev.starcore.starcore.module.nation.statusbar.NationStatusBarCommand;
import dev.starcore.starcore.module.nation.tutorial.NationTutorialBubble;
import dev.starcore.starcore.module.nation.tutorial.NationTutorialConfig;
import dev.starcore.starcore.module.nation.tutorial.NationTutorialService;
import dev.starcore.starcore.module.nation.tutorial.NationTutorialListener;
import dev.starcore.starcore.module.nation.tutorial.NationTutorialCommand;
import dev.starcore.starcore.module.treasury.TreasuryService;
import dev.starcore.starcore.module.war.WarService;
import dev.starcore.starcore.module.army.ArmyService;
import dev.starcore.starcore.mechanics.ReputationService;
import dev.starcore.starcore.quest.DailyQuestService;
import dev.starcore.starcore.quest.QuestService;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class NationModule implements StarCoreModule, NationService {
    private static final String FILE_NAME = "nations.properties";
    private static final long CACHE_REFRESH_INTERVAL_TICKS = 6000L; // 5分钟刷新一次 (300秒)

    private static final ModuleMetadata METADATA = new ModuleMetadata(
        "nation",
        "国家核心",
        ModuleLayer.MODULE,
        List.of(),
        List.of(NationService.class, ClaimToolService.class),
        "Owns nation membership, identity, and strategic state boundaries."
    );

    private final Map<NationId, Nation> nationsById = new LinkedHashMap<>();
    private final Map<String, NationId> names = new ConcurrentHashMap<>();
    private final Map<UUID, NationId> membership = new ConcurrentHashMap<>();
    private TerritoryService territoryService;
    private PlayerProfileService playerProfiles;
    private PersistenceService persistenceService;
    private ConfigurationService configuration;
    private InternalEconomyService economyService;
    private MessageService messages;
    private ExternalProtectionService externalProtectionService;
    private NationStateStorage stateStorage;
    private NativeTerritoryProtectionListener protectionListener;
    private ClaimToolListener claimToolListener;
    private ClaimToolService claimToolService;
    private ClaimPricingService claimPricingService;
    private NationResourceDistrictService resourceDistrictService;
    private NationStatusBarService statusBarService;
    private dev.starcore.starcore.module.nation.gui.NationMenuProvider menuProvider;
    private NationTutorialBubble tutorialBubble;
    private NationTutorialConfig tutorialConfig;
    private NationTutorialService tutorialService;
    private NationTutorialListener tutorialListener;
    private NationTutorialCommand tutorialCommand;
    private StarCoreContext context;
    private int cacheRefreshTaskId = -1;

    @Override
    public ModuleMetadata metadata() {
        return METADATA;
    }

    @Override
    public void enable(StarCoreContext context) {
        this.context = context; // 保存 context 供 refreshNationCache 使用
        context.persistenceService().ensureNamespace(metadata().id());
        this.persistenceService = context.persistenceService();
        this.configuration = context.configuration();
        this.economyService = context.economyService();
        this.stateStorage = new DatabaseAwareNationStateStorage(metadata().id(), context.databaseService(), context.persistenceService(), context.plugin().getLogger());
        this.territoryService = context.serviceRegistry().require(TerritoryService.class);
        this.externalProtectionService = context.serviceRegistry().require(ExternalProtectionService.class);
        this.playerProfiles = context.serviceRegistry().require(PlayerProfileService.class);
        OnlinePlayerDirectory onlinePlayerDirectory = context.serviceRegistry().require(OnlinePlayerDirectory.class);
        MessageService messageService = context.serviceRegistry().require(MessageService.class);
        this.messages = messageService;
        this.claimPricingService = new ClaimPricingService(configuration);
        // 初始化战争状态感知型领地保护监听器
        WarService warService = context.serviceRegistry().find(WarService.class).orElse(null);
        this.protectionListener = new NativeTerritoryProtectionListener(configuration, territoryService, this, messageService, warService);
        context.plugin().getServer().getPluginManager().registerEvents(protectionListener, context.plugin());
        context.plugin().getLogger().info("STARCORE native territory protection registered." + (warService != null ? " War-aware mode enabled." : ""));
        this.claimToolService = new NativeClaimToolService(context.plugin(), configuration, this, messageService);
        context.serviceRegistry().register(ClaimToolService.class, claimToolService);
        this.claimToolListener = new ClaimToolListener(configuration, claimToolService, messageService);
        context.plugin().getServer().getPluginManager().registerEvents(claimToolListener, context.plugin());
        context.plugin().getLogger().info("STARCORE claim tool registered.");

        // 初始化菜单系统（支持多种提供者）
        TreasuryService treasuryService = context.serviceRegistry().find(TreasuryService.class).orElse(null);
        DiplomacyService diplomacyService = context.serviceRegistry().find(DiplomacyService.class).orElse(null);
        TechnologyService technologyService = context.serviceRegistry().find(TechnologyService.class).orElse(null);
        dev.starcore.starcore.module.government.GovernmentService governmentService = context.serviceRegistry().find(dev.starcore.starcore.module.government.GovernmentService.class).orElse(null);
        dev.starcore.starcore.module.policy.PolicyService policyService = context.serviceRegistry().find(dev.starcore.starcore.module.policy.PolicyService.class).orElse(null);
        String menuProviderType = context.plugin().getConfig().getString("menu.provider", "auto");
        dev.starcore.starcore.module.nation.gui.NationMenuFactory menuFactory =
            new dev.starcore.starcore.module.nation.gui.NationMenuFactory(this, messageService, context.plugin(), treasuryService, diplomacyService, technologyService, governmentService, economyService, policyService);
        this.menuProvider = menuFactory.createProvider(menuProviderType);
        context.plugin().getLogger().info("STARCORE Menu provider: " + this.menuProvider.getProviderType());

        // 初始化国家状态栏服务
        ArmyService armyService = context.serviceRegistry().find(ArmyService.class).orElse(null);
        ReputationService reputationService = context.serviceRegistry().find(ReputationService.class).orElse(null);
        this.statusBarService = new NationStatusBarService(
            context.plugin(),
            this,
            treasuryService,
            armyService,
            reputationService
        );
        // 注册状态栏监听器
        new NationStatusBarListener(statusBarService, this, context.plugin());
        // 注册状态栏命令
        new NationStatusBarCommand(statusBarService, this, messageService, context.plugin());
        context.plugin().getLogger().info("STARCORE Nation StatusBar initialized.");

        // 初始化教程系统
        initTutorialSystem();

        loadState();

        // 刷新所有国家的缓存数据
        refreshAllNationCaches();

        NativeNationResourceDistrictService nativeResourceDistrictService = new NativeNationResourceDistrictService(
            context.plugin(),
            configuration,
            context.databaseService(),
            persistenceService,
            territoryService,
            economyService,
            messageService,
            this,
            onlinePlayerDirectory,
            context.plugin().getLogger(),
            context.serviceRegistry()
        );
        nativeResourceDistrictService.start();
        this.resourceDistrictService = nativeResourceDistrictService;
        context.serviceRegistry().register(NationResourceDistrictService.class, resourceDistrictService);
        context.plugin().getLogger().info("STARCORE nation resource districts registered.");

        // 启动定时缓存刷新任务（每5分钟刷新一次）
        startCacheRefreshTask();
    }

    /**
     * 启动定时缓存刷新任务
     */
    private void startCacheRefreshTask() {
        if (context != null && context.plugin() != null) {
            cacheRefreshTaskId = context.plugin().getServer().getScheduler().runTaskTimer(
                context.plugin(),
                () -> refreshAllNationCaches(),
                CACHE_REFRESH_INTERVAL_TICKS,
                CACHE_REFRESH_INTERVAL_TICKS
            ).getTaskId();
            context.plugin().getLogger().info("STARCORE nation cache refresh task started (interval: 5 minutes).");
        }
    }

    /**
     * 停止定时缓存刷新任务
     */
    private void stopCacheRefreshTask() {
        if (cacheRefreshTaskId != -1 && context != null && context.plugin() != null) {
            context.plugin().getServer().getScheduler().cancelTask(cacheRefreshTaskId);
            cacheRefreshTaskId = -1;
        }
    }

    /**
     * 初始化教程系统
     */
    private void initTutorialSystem() {
        // 初始化教程配置
        tutorialConfig = new NationTutorialConfig(context.plugin());

        // 初始化教程气泡
        tutorialBubble = new NationTutorialBubble(context.plugin());

        // 初始化教程服务
        tutorialService = new NationTutorialService(context.plugin(), this, tutorialBubble, tutorialConfig);

        // 获取 TriumphNationMenu 实例（用于教程监听器）
        dev.starcore.starcore.module.nation.gui.NationMenuProvider provider = this.menuProvider;
        dev.starcore.starcore.module.nation.gui.TriumphNationMenu triumphMenu = null;
        if (provider instanceof dev.starcore.starcore.module.nation.gui.TriumphNationMenu) {
            triumphMenu = (dev.starcore.starcore.module.nation.gui.TriumphNationMenu) provider;
        }

        // 初始化教程监听器
        tutorialListener = new NationTutorialListener(
            context.plugin(),
            this,
            tutorialService,
            tutorialBubble,
            triumphMenu
        );
        context.plugin().getServer().getPluginManager().registerEvents(tutorialListener, context.plugin());

        // 初始化教程命令
        tutorialCommand = new NationTutorialCommand(
            context.plugin(),
            tutorialService,
            tutorialConfig,
            messages
        );

        // 注册 /tutorial 命令
        org.bukkit.command.Command tutorialCmd = new org.bukkit.command.Command("tutorial") {
            @Override
            public boolean execute(org.bukkit.command.CommandSender sender, String label, String[] args) {
                if (sender instanceof Player player) {
                    return tutorialCommand.onCommand(player, this, label, args);
                }
                sender.sendMessage(net.kyori.adventure.text.Component.text("此命令只能由玩家执行"));
                return true;
            }

            @Override
            public java.util.List<String> tabComplete(org.bukkit.command.CommandSender sender, String alias, String[] args) {
                if (sender instanceof Player player) {
                    return tutorialCommand.onTabComplete(player, this, alias, args);
                }
                return Collections.emptyList();
            }
        };
        tutorialCmd.setUsage("/tutorial [start|next|prev|skip|close|info|reset|list|beginner|visitor|admin]");
        context.plugin().getServer().getCommandMap().register("starcore", tutorialCmd);

        // 启动气泡跟随任务
        tutorialService.startBubbleFollowTask();

        context.plugin().getLogger().info("STARCORE Nation Tutorial system initialized.");
    }

    @Override
    public void disable(StarCoreContext context) {
        // 停止定时缓存刷新任务
        stopCacheRefreshTask();

        // 关闭教程系统
        if (tutorialService != null) {
            tutorialService.shutdown();
            tutorialService = null;
        }
        if (tutorialBubble != null) {
            tutorialBubble.hideAllBubbles();
            tutorialBubble = null;
        }
        if (tutorialListener != null) {
            HandlerList.unregisterAll(tutorialListener);
            tutorialListener = null;
        }
        tutorialConfig = null;
        tutorialCommand = null;

        // 关闭状态栏服务
        if (statusBarService != null) {
            statusBarService.shutdown();
            statusBarService = null;
        }

        if (protectionListener != null) {
            HandlerList.unregisterAll(protectionListener);
            protectionListener = null;
        }
        if (claimToolListener != null) {
            HandlerList.unregisterAll(claimToolListener);
            claimToolListener = null;
        }
        if (resourceDistrictService instanceof NativeNationResourceDistrictService nativeResourceDistrictService) {
            nativeResourceDistrictService.stop();
            resourceDistrictService = null;
        }
        flushState();
    }

    @Override
    public Nation createNation(UUID founderId, String founderName, String nationName) {
        playerProfiles.recordSeen(founderId, founderName);
        if (membership.containsKey(founderId)) {
            throw new IllegalStateException(msg("nation.service.already-in-nation-create"));
        }
        if (foundedCount(founderId, NationKind.NATION) >= configuration.maxNationsPerPlayer()) {
            throw new IllegalStateException(msg("nation.service.max-nations", configuration.maxNationsPerPlayer()));
        }
        String normalized = normalizeNationName(nationName);
        if (names.containsKey(normalized)) {
            throw new IllegalStateException(msg("nation.service.duplicate-name"));
        }
        charge(founderId, configuration.nationCreateCost(), msg("nation.service.action-create-nation"));

        Nation nation = new Nation(NationId.random(), nationName, founderId, founderName, NationKind.NATION, null);
        nationsById.put(nation.id(), nation);
        names.put(normalized, nation.id());
        membership.put(founderId, nation.id());
        saveState();
        return nation;
    }

    @Override
    public Nation createCityState(UUID founderId, String founderName, String cityStateName) {
        playerProfiles.recordSeen(founderId, founderName);
        Nation parentNation = nationOf(founderId).orElseThrow(() -> new IllegalStateException(msg("nation.service.city-parent-required")));
        if (!parentNation.founderId().equals(founderId)) {
            throw new IllegalStateException(msg("nation.service.leader-city-only"));
        }
        if (foundedCount(founderId, NationKind.CITY_STATE) >= configuration.maxCityStatesPerPlayer()) {
            throw new IllegalStateException(msg("nation.service.max-city-states-player", configuration.maxCityStatesPerPlayer()));
        }
        if (cityStatesOf(parentNation.id()).size() >= configuration.maxCityStatesPerNation()) {
            throw new IllegalStateException(msg("nation.service.max-city-states-nation", configuration.maxCityStatesPerNation()));
        }
        String normalized = normalizeNationName(cityStateName);
        if (names.containsKey(normalized)) {
            throw new IllegalStateException(msg("nation.service.duplicate-name"));
        }
        charge(founderId, configuration.cityStateCreateCost(), msg("nation.service.action-create-city-state"));

        Nation cityState = new Nation(NationId.random(), cityStateName, founderId, founderName, NationKind.CITY_STATE, parentNation.id());
        cityState.setGovernmentType(GovernmentType.REPUBLIC);
        nationsById.put(cityState.id(), cityState);
        names.put(normalized, cityState.id());
        saveState();
        return cityState;
    }

    @Override
    public Optional<Nation> nationById(NationId nationId) {
        return Optional.ofNullable(nationsById.get(nationId));
    }

    @Override
    public Optional<Nation> nationByName(String nationName) {
        NationId id = names.get(normalizeNationName(nationName));
        return id == null ? Optional.empty() : Optional.ofNullable(nationsById.get(id));
    }

    @Override
    public Optional<Nation> nationOf(UUID playerId) {
        NationId id = membership.get(playerId);
        return id == null ? Optional.empty() : Optional.ofNullable(nationsById.get(id));
    }

    @Override
    public boolean claimCurrentChunk(UUID playerId, String world, int x, int z) {
        ChunkClaimSelection selection = new ChunkClaimSelection(world, x, x, z, z);
        ClaimSelectionPreview preview = previewClaimSelection(playerId, selection);
        if (preview.overlapCount() > 0) {
            return false;
        }
        if (!preview.canSubmit()) {
            throw new IllegalStateException(preview.message());
        }
        claimSelection(playerId, selection);
        return true;
    }

    @Override
    public boolean unclaimCurrentChunk(UUID playerId, String world, int x, int z) {
        Nation nation = nationOf(playerId).orElseThrow(() -> new IllegalStateException(msg("nation.service.no-nation")));
        if (configuration.leaderOnlyClaim() && !nation.founderId().equals(playerId)) {
            throw new IllegalStateException(msg("nation.service.leader-unclaim-only"));
        }
        ChunkCoordinate coordinate = new ChunkCoordinate(world, x, z);
        TerritoryClaim claim = territoryService.claimAt(coordinate).orElse(null);
        if (claim == null) {
            return false;
        }
        String ownerId = nation.id().toString();
        if (!ownerId.equals(claim.ownerId())) {
            throw new IllegalStateException(msg("nation.service.not-own-current"));
        }
        if (resourceDistrictService != null && resourceDistrictService.districtAt(coordinate).isPresent()) {
            throw new IllegalStateException(msg("nation.service.resource-district-unclaim-blocked"));
        }
        boolean result = territoryService.unclaim(ownerId, coordinate);
        if (result) {
            // 刷新国家缓存数据（更新领土数量）
            refreshNationCache(nation.id());
        }
        return result;
    }

    @Override
    public Optional<TerritoryClaim> claimAt(String world, int x, int z) {
        return territoryService.claimAt(new ChunkCoordinate(world, x, z));
    }

    @Override
    public Collection<TerritoryClaim> claimsOf(NationId nationId) {
        return territoryService.claimsByOwner(nationId.toString());
    }

    @Override
    public ClaimSelectionPreview previewClaimSelection(UUID playerId, ChunkClaimSelection selection) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(selection, "selection");
        int chunkCount = selection.chunkCount();
        ClaimPriceBreakdown pricing = claimPricingService.price(selection);
        BigDecimal price = pricing.totalPrice();
        BigDecimal balance = economyService.balance(playerId);
        Nation nation = nationOf(playerId).orElse(null);
        if (nation == null) {
            String message = msg("nation.service.no-nation");
            return preview(null, "", selection, chunkCount, 0, 0, configuration.maxClaimsPerNation(), price, balance, pricing, false, message,
                claimExplanation("no-nation", "error", message, List.of(
                    reason("no-nation", msg("nation.service.explain.no-nation"), Map.of())
                ), pricing, 0, configuration.maxClaimsPerNation(), chunkCount, 0, price, balance));
        }
        int currentClaimCount = claimCount(nation.id());
        int maxClaims = maxClaimsFor(nation);
        if (configuration.leaderOnlyClaim() && !nation.founderId().equals(playerId)) {
            String message = msg("nation.service.leader-claim-only");
            return preview(nation, selection, chunkCount, 0, currentClaimCount, maxClaims, price, balance, pricing, false, message,
                claimExplanation("leader-only", "error", message, List.of(
                    reason("leader-only", msg("nation.service.explain.leader-claim-only"), Map.of(
                        "founderId", nation.founderId().toString(),
                        "playerId", playerId.toString()
                    ))
                ), pricing, currentClaimCount, maxClaims, chunkCount, 0, price, balance));
        }
        int overlapCount = overlapCount(selection);
        if (overlapCount > 0) {
            String message = msg("nation.service.overlap", overlapCount);
            return preview(nation, selection, chunkCount, overlapCount, currentClaimCount, maxClaims, price, balance, pricing, false, message,
                claimExplanation("overlap", "error", message, List.of(
                    reason("overlap", msg("nation.service.explain.overlap", overlapCount), Map.of(
                        "overlapCount", Integer.toString(overlapCount),
                        "selectionChunks", Integer.toString(chunkCount)
                    ))
                ), pricing, currentClaimCount, maxClaims, chunkCount, overlapCount, price, balance));
        }
        Optional<ProtectionConflict> externalConflict = externalProtectionService == null ? Optional.empty() : externalProtectionService.findClaimConflict(selection);
        if (externalConflict.isPresent()) {
            ProtectionConflict conflict = externalConflict.get();
            String message = msg("nation.service.external-protection-conflict", conflict.providerName(), conflict.displayLabel(), conflict.coordinate().toString());
            return preview(nation, selection, chunkCount, overlapCount, currentClaimCount, maxClaims, price, balance, pricing, false, message,
                claimExplanation("external-protection-conflict", "error", message, List.of(
                    reason("external-protection-conflict", msg("nation.service.explain.external-protection-conflict", conflict.providerName(), conflict.displayLabel(), conflict.coordinate().toString()), Map.of(
                        "provider", conflict.providerName(),
                        "label", conflict.displayLabel(),
                        "coordinate", conflict.coordinate().toString()
                    ))
                ), pricing, currentClaimCount, maxClaims, chunkCount, overlapCount, price, balance));
        }
        if (maxClaims >= 0 && currentClaimCount + chunkCount > maxClaims) {
            String message = msg("nation.service.claim-limit", maxClaims);
            return preview(nation, selection, chunkCount, overlapCount, currentClaimCount, maxClaims, price, balance, pricing, false, message,
                claimExplanation("claim-limit", "error", message, List.of(
                    reason("claim-limit", msg("nation.service.explain.claim-limit", currentClaimCount, chunkCount, maxClaims), Map.of(
                        "currentClaims", Integer.toString(currentClaimCount),
                        "selectionChunks", Integer.toString(chunkCount),
                        "projectedClaims", Integer.toString(currentClaimCount + chunkCount),
                        "maxClaims", Integer.toString(maxClaims)
                    ))
                ), pricing, currentClaimCount, maxClaims, chunkCount, overlapCount, price, balance));
        }
        if (price.signum() > 0 && balance.compareTo(price) < 0) {
            BigDecimal shortfall = price.subtract(balance).max(BigDecimal.ZERO).setScale(2, RoundingMode.DOWN);
            String message = msg("nation.service.insufficient-claim-balance", price.toPlainString());
            return preview(nation, selection, chunkCount, overlapCount, currentClaimCount, maxClaims, price, balance, pricing, false, message,
                claimExplanation("insufficient-balance", "error", message, List.of(
                    reason("insufficient-balance", msg("nation.service.explain.insufficient-claim-balance", price.toPlainString(), balance.toPlainString(), shortfall.toPlainString()), Map.of(
                        "price", price.toPlainString(),
                        "balance", balance.toPlainString(),
                        "shortfall", shortfall.toPlainString()
                    ))
                ), pricing, currentClaimCount, maxClaims, chunkCount, overlapCount, price, balance));
        }
        String message = msg("nation.service.can-claim");
        return preview(nation, selection, chunkCount, overlapCount, currentClaimCount, maxClaims, price, balance, pricing, true, message,
            claimExplanation("ready", "success", message, List.of(), pricing, currentClaimCount, maxClaims, chunkCount, overlapCount, price, balance));
    }

    @Override
    public ClaimSelectionResult claimSelection(UUID playerId, ChunkClaimSelection selection) {
        ClaimSelectionPreview preview = previewClaimSelection(playerId, selection);
        if (!preview.canSubmit()) {
            throw new IllegalStateException(preview.message());
        }
        Nation nation = nationsById.get(preview.nationId());
        if (nation == null) {
            throw new IllegalStateException(msg("nation.service.state-changed"));
        }
        charge(playerId, preview.price(), msg("nation.service.action-claim"));
        List<ChunkCoordinate> claimed = new java.util.ArrayList<>(preview.chunkCount());
        String ownerId = preview.nationId().toString();
        try {
            for (ChunkCoordinate coordinate : selection.coordinates()) {
                if (!territoryService.claim(ownerId, coordinate)) {
                    throw new IllegalStateException(msg("nation.service.overlap-during-claim"));
                }
                claimed.add(coordinate);
            }
        } catch (RuntimeException exception) {
            for (ChunkCoordinate coordinate : claimed) {
                territoryService.unclaim(ownerId, coordinate);
            }
            if (preview.price().signum() > 0) {
                economyService.deposit(playerId, preview.price());
            }
            throw exception;
        }
        if (resourceDistrictService != null) {
            resourceDistrictService.ensureDistricts(nation);
        }
        // 刷新国家缓存数据
        refreshNationCache(nation.id());
        return new ClaimSelectionResult(preview.nationId(), preview.nationName(), selection, claimed.size(), preview.price());
    }

    @Override
    public boolean isFounder(UUID playerId, NationId nationId) {
        Nation nation = nationsById.get(nationId);
        return nation != null && nation.founderId().equals(playerId);
    }

    @Override
    public boolean addMember(NationId nationId, UUID playerId, String playerName) {
        Nation nation = nationsById.get(nationId);
        if (nation == null || membership.containsKey(playerId)) {
            return false;
        }
        int maxMembers = configuration.maxMembersPerNation();
        if (maxMembers >= 0 && nation.members().size() >= maxMembers) {
            return false;
        }
        nation.addMember(playerId, playerName);
        membership.put(playerId, nationId);
        playerProfiles.recordSeen(playerId, playerName);
        saveState();
        return true;
    }

    @Override
    public boolean removeMember(NationId nationId, UUID playerId) {
        Nation nation = nationsById.get(nationId);
        if (nation == null) {
            return false;
        }
        if (nation.founderId().equals(playerId)) {
            return false;
        }
        nation.removeMember(playerId);
        membership.remove(playerId);
        saveState();
        return true;
    }

    @Override
    public boolean setMemberRank(NationId nationId, UUID playerId, String rank) {
        Nation nation = nationsById.get(nationId);
        if (nation == null || !nation.hasMember(playerId)) {
            return false;
        }
        nation.setMemberRank(playerId, rank);
        saveState();
        return true;
    }

    @Override
    public boolean renameNation(NationId nationId, String newName) {
        Nation nation = nationsById.get(nationId);
        if (nation == null) {
            return false;
        }
        String normalized = normalizeNationName(newName);
        NationId existing = names.get(normalized);
        if (existing != null && !existing.equals(nationId)) {
            return false;
        }
        names.remove(normalizeNationName(nation.name()));
        nation.rename(newName);
        names.put(normalized, nationId);
        saveState();
        return true;
    }

    @Override
    public int claimCount(NationId nationId) {
        return territoryService.claimsByOwner(nationId.toString()).size();
    }

    @Override
    public int levelOf(NationId nationId) {
        Nation nation = nationsById.get(nationId);
        return nation == null ? 1 : configuration.nationLevelForExperience(nation.experience());
    }

    @Override
    public long experienceOf(NationId nationId) {
        Nation nation = nationsById.get(nationId);
        return nation == null ? 0L : nation.experience();
    }

    @Override
    public int maxClaimsOf(NationId nationId) {
        Nation nation = nationsById.get(nationId);
        return nation == null ? configuration.maxClaimsPerNation() : maxClaimsFor(nation);
    }

    @Override
    public boolean addExperience(NationId nationId, long amount) {
        if (amount <= 0L) {
            return false;
        }
        Nation nation = nationsById.get(nationId);
        if (nation == null) {
            return false;
        }
        int previousLevel = levelOf(nationId);
        nation.addExperience(amount);
        saveState();
        if (resourceDistrictService != null && levelOf(nationId) > previousLevel) {
            resourceDistrictService.ensureDistricts(nation);
        }
        return true;
    }

    @Override
    public int foundedCount(UUID playerId, NationKind kind) {
        return (int) nationsById.values().stream()
            .filter(nation -> nation.founderId().equals(playerId))
            .filter(nation -> nation.kind() == kind)
            .count();
    }

    @Override
    public Collection<Nation> cityStatesOf(NationId parentNationId) {
        return nationsById.values().stream()
            .filter(nation -> nation.kind() == NationKind.CITY_STATE)
            .filter(nation -> parentNationId.equals(nation.parentNationId()))
            .toList();
    }

    @Override
    public Collection<Nation> nations() {
        return List.copyOf(nationsById.values());
    }

    /**
     * 检查两个国家是否处于战争状态
     * @param nationId 国家ID
     * @param otherNationId 另一个国家ID
     * @return 如果处于战争状态返回 true
     */
    @Override
    public boolean atWar(NationId nationId, NationId otherNationId) {
        if (nationId == null || otherNationId == null) {
            return false;
        }
        WarService warService = context.serviceRegistry().find(WarService.class).orElse(null);
        if (warService == null) {
            return false;
        }
        return warService.atWar(nationId, otherNationId);
    }

    /**
     * 检查国家是否处于战争状态
     * @param nationId 国家ID
     * @return 如果处于战争状态返回 true
     */
    @Override
    public boolean atWar(NationId nationId) {
        if (nationId == null) {
            return false;
        }
        WarService warService = context.serviceRegistry().find(WarService.class).orElse(null);
        if (warService == null) {
            return false;
        }
        return !warService.activeWarsOf(nationId).isEmpty();
    }

    @Override
    public void saveState() {
        if (stateStorage == null) {
            return;
        }
        stateStorage.saveAsync(stateProperties());
    }

    private void flushState() {
        if (stateStorage == null) {
            return;
        }
        stateStorage.save(stateProperties());
    }

    private Properties stateProperties() {
        return NationStateCodec.toProperties(nationsById.values());
    }

    @Override
    public String summary() {
        return nationsById.size() + " nation(s), " + membership.size() + " member binding(s)";
    }

    private void loadState() {
        Properties properties = stateStorage == null ? new Properties() : stateStorage.load();
        nationsById.clear();
        names.clear();
        membership.clear();
        for (Nation nation : NationStateCodec.fromProperties(properties)) {
            if (nation == null || names.containsKey(normalizeNationName(nation.name()))) {
                continue;
            }
            nationsById.put(nation.id(), nation);
            names.put(normalizeNationName(nation.name()), nation.id());
            for (NationMember member : nation.members()) {
                if (nation.kind() == NationKind.NATION) {
                    membership.put(member.playerId(), nation.id());
                }
                playerProfiles.recordSeen(member.playerId(), member.lastKnownName());
            }
        }
    }

    private static int parseInt(String value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static String normalizeNationName(String nationName) {
        String normalized = nationName.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("nation name cannot be empty");
        }
        return normalized;
    }

    private void charge(UUID playerId, BigDecimal cost, String actionName) {
        if (cost == null || cost.signum() <= 0) {
            return;
        }
        if (!economyService.withdraw(playerId, cost)) {
            throw new IllegalStateException(msg("nation.service.insufficient-action-balance", actionName, cost));
        }
    }

    private String msg(String key, Object... args) {
        return messages.format(key, args);
    }

    private int maxClaimsFor(Nation nation) {
        if (configuration.nationResourceLevelClaimLimitEnabled()) {
            return configuration.nationClaimLimitForExperience(nation.experience());
        }
        return configuration.maxClaimsPerNation();
    }

    private int overlapCount(ChunkClaimSelection selection) {
        int overlaps = 0;
        for (ChunkCoordinate coordinate : selection.coordinates()) {
            if (territoryService.isClaimed(coordinate)) {
                overlaps++;
            }
        }
        return overlaps;
    }

    private ClaimSelectionPreview preview(Nation nation, ChunkClaimSelection selection, int chunkCount, int overlapCount,
                                          int currentClaimCount, int maxClaims, BigDecimal price, BigDecimal balance,
                                          ClaimPriceBreakdown pricing, boolean canSubmit, String message) {
        return preview(nation.id(), nation.name(), selection, chunkCount, overlapCount, currentClaimCount, maxClaims, price, balance, pricing, canSubmit, message,
            ClaimSelectionExplanation.basic(canSubmit, message));
    }

    private ClaimSelectionPreview preview(Nation nation, ChunkClaimSelection selection, int chunkCount, int overlapCount,
                                          int currentClaimCount, int maxClaims, BigDecimal price, BigDecimal balance,
                                          ClaimPriceBreakdown pricing, boolean canSubmit, String message,
                                          ClaimSelectionExplanation explanation) {
        return preview(nation.id(), nation.name(), selection, chunkCount, overlapCount, currentClaimCount, maxClaims, price, balance, pricing, canSubmit, message, explanation);
    }

    private ClaimSelectionPreview preview(NationId nationId, String nationName, ChunkClaimSelection selection, int chunkCount, int overlapCount,
                                          int currentClaimCount, int maxClaims, BigDecimal price, BigDecimal balance,
                                          ClaimPriceBreakdown pricing, boolean canSubmit, String message) {
        return preview(nationId, nationName, selection, chunkCount, overlapCount, currentClaimCount, maxClaims, price, balance, pricing, canSubmit, message,
            ClaimSelectionExplanation.basic(canSubmit, message));
    }

    private ClaimSelectionPreview preview(NationId nationId, String nationName, ChunkClaimSelection selection, int chunkCount, int overlapCount,
                                          int currentClaimCount, int maxClaims, BigDecimal price, BigDecimal balance,
                                          ClaimPriceBreakdown pricing, boolean canSubmit, String message,
                                          ClaimSelectionExplanation explanation) {
        return new ClaimSelectionPreview(nationId, nationName, selection, chunkCount, overlapCount, currentClaimCount, maxClaims, price, balance, pricing, canSubmit, message, explanation);
    }

    private ClaimSelectionExplanation claimExplanation(
        String state,
        String severity,
        String summary,
        List<ClaimSelectionReason> leadingReasons,
        ClaimPriceBreakdown pricing,
        int currentClaimCount,
        int maxClaims,
        int chunkCount,
        int overlapCount,
        BigDecimal price,
        BigDecimal balance
    ) {
        List<ClaimSelectionReason> reasons = new ArrayList<>();
        if (leadingReasons != null) {
            reasons.addAll(leadingReasons);
        }
        reasons.add(capacityReason(currentClaimCount, maxClaims, chunkCount, overlapCount));
        reasons.addAll(priceReasons(pricing, chunkCount, price, balance));
        return ClaimSelectionExplanation.of(state, severity, summary, reasons);
    }

    private ClaimSelectionReason capacityReason(int currentClaimCount, int maxClaims, int chunkCount, int overlapCount) {
        int newClaims = Math.max(0, chunkCount - overlapCount);
        int projectedClaims = currentClaimCount + newClaims;
        String maxClaimsText = maxClaims < 0 ? msg("nation.service.explain.unlimited") : Integer.toString(maxClaims);
        Map<String, String> details = new LinkedHashMap<>();
        details.put("currentClaims", Integer.toString(currentClaimCount));
        details.put("selectionChunks", Integer.toString(chunkCount));
        details.put("overlapCount", Integer.toString(overlapCount));
        details.put("newClaims", Integer.toString(newClaims));
        details.put("projectedClaims", Integer.toString(projectedClaims));
        details.put("maxClaims", maxClaimsText);
        return reason("claim-capacity", msg("nation.service.explain.capacity", currentClaimCount, projectedClaims, maxClaimsText), details);
    }

    private List<ClaimSelectionReason> priceReasons(ClaimPriceBreakdown pricing, int chunkCount, BigDecimal price, BigDecimal balance) {
        List<ClaimSelectionReason> reasons = new ArrayList<>();
        ClaimPriceBreakdown safePricing = pricing == null
            ? ClaimPriceBreakdown.empty(price, price, chunkCount)
            : pricing;
        BigDecimal total = price == null ? BigDecimal.ZERO : price;
        BigDecimal base = safePricing.baseChunkPrice();
        BigDecimal average = chunkCount <= 0
            ? BigDecimal.ZERO
            : total.divide(BigDecimal.valueOf(chunkCount), 2, RoundingMode.DOWN);
        Map<String, String> summaryDetails = new LinkedHashMap<>();
        summaryDetails.put("baseChunkPrice", base.toPlainString());
        summaryDetails.put("totalPrice", total.toPlainString());
        summaryDetails.put("averageChunkPrice", average.toPlainString());
        summaryDetails.put("chunkCount", Integer.toString(chunkCount));
        summaryDetails.put("balance", balance == null ? "0" : balance.toPlainString());
        reasons.add(reason("claim-price-summary", msg("nation.service.explain.price-summary", total.toPlainString(), base.toPlainString(), average.toPlainString()), summaryDetails));

        safePricing.chunks().stream()
            .max((left, right) -> left.price().compareTo(right.price()))
            .ifPresent(chunk -> {
                Map<String, String> chunkDetails = new LinkedHashMap<>();
                chunkDetails.put("world", chunk.world());
                chunkDetails.put("chunkX", Integer.toString(chunk.chunkX()));
                chunkDetails.put("chunkZ", Integer.toString(chunk.chunkZ()));
                chunkDetails.put("biome", chunk.biome());
                chunkDetails.put("price", chunk.price().toPlainString());
                chunkDetails.put("distanceBlocks", Long.toString(chunk.distanceBlocks()));
                chunkDetails.put("distanceMultiplier", Double.toString(chunk.distanceMultiplier()));
                chunkDetails.put("biomeMultiplier", Double.toString(chunk.biomeMultiplier()));
                reasons.add(reason("claim-highest-priced-chunk", msg("nation.service.explain.highest-priced-chunk", chunk.world(), chunk.chunkX(), chunk.chunkZ(), chunk.price().toPlainString()), chunkDetails));
                if (chunk.distanceMultiplier() > 1.0D) {
                    reasons.add(reason("claim-distance-price-driver", msg("nation.service.explain.distance-price-driver", chunk.distanceBlocks(), Double.toString(chunk.distanceMultiplier())), Map.of(
                        "distanceBlocks", Long.toString(chunk.distanceBlocks()),
                        "distanceMultiplier", Double.toString(chunk.distanceMultiplier())
                    )));
                }
                if (chunk.biomeMultiplier() > 1.0D) {
                    reasons.add(reason("claim-biome-price-driver", msg("nation.service.explain.biome-price-driver", chunk.biome(), Double.toString(chunk.biomeMultiplier())), Map.of(
                        "biome", chunk.biome(),
                        "biomeRichness", Double.toString(chunk.biomeRichness()),
                        "biomeMultiplier", Double.toString(chunk.biomeMultiplier())
                    )));
                }
            });
        return reasons;
    }

    private ClaimSelectionReason reason(String code, String message, Map<String, String> details) {
        return ClaimSelectionReason.of(code, message, details);
    }

    /**
     * 打开国家管理菜单
     *
     * @param player 玩家
     * @param nation 国家（可以为 null，表示访客模式）
     */
    public void openManagementMenu(org.bukkit.entity.Player player, Nation nation) {
        if (menuProvider != null) {
            menuProvider.openMainMenu(player);
        }
    }

    /**
     * 获取菜单提供者
     *
     * @return 菜单提供者实例
     */
    public dev.starcore.starcore.module.nation.gui.NationMenuProvider getMenuProvider() {
        return menuProvider;
    }

    /**
     * 刷新国家缓存数据（更新 GUI 显示所需的统计数据）
     * 此方法应在国家数据变更后调用，或在打开 GUI 菜单前调用
     *
     * @param nationId 国家ID
     */
    @Override
    public void refreshNationCache(NationId nationId) {
        Nation nation = nationsById.get(nationId);
        if (nation == null) {
            return;
        }

        // 更新领土数量
        int territoryCount = claimCount(nationId);
        nation.setTerritoryCount(territoryCount);

        // 更新国库余额
        TreasuryService treasuryService = context.serviceRegistry().find(TreasuryService.class).orElse(null);
        BigDecimal treasuryBalance = BigDecimal.ZERO;
        if (treasuryService != null) {
            treasuryBalance = treasuryService.balance(nationId);
            nation.setTreasuryBalance(treasuryBalance);
        }

        // 更新政策数量
        dev.starcore.starcore.module.policy.PolicyService policyService = context.serviceRegistry().find(dev.starcore.starcore.module.policy.PolicyService.class).orElse(null);
        if (policyService != null) {
            nation.setPolicyCount(policyService.unlockedPolicies(nationId).size());
        }

        // 更新科技数量
        dev.starcore.starcore.module.technology.TechnologyService techService = context.serviceRegistry().find(dev.starcore.starcore.module.technology.TechnologyService.class).orElse(null);
        if (techService != null) {
            nation.setTechnologyCount(techService.unlockedTechnologies(nationId).size());
        }

        // 更新外交关系（盟友数量）
        DiplomacyService diplomacyService = context.serviceRegistry().find(DiplomacyService.class).orElse(null);
        int allyCount = 0;
        int warCount = 0;
        if (diplomacyService != null) {
            allyCount = (int) diplomacyService.relationsOf(nationId).stream()
                .filter(r -> r.relation() == dev.starcore.starcore.module.diplomacy.DiplomacyRelation.ALLIED)
                .count();
            nation.setAllyCount(allyCount);
        }

        // 更新战争数量
        WarService warService = context.serviceRegistry().find(WarService.class).orElse(null);
        if (warService != null) {
            warCount = warService.activeWarsOf(nationId).size();
            nation.setWarCount(warCount);
        }

        // 更新城镇数据（子城邦和首都位置）
        refreshTownCache(nationId, nation);

        // 标记缓存已更新
        nation.markCacheUpdated();
    }

    /**
     * 刷新国家城镇数据缓存
     */
    private void refreshTownCache(NationId nationId, Nation nation) {
        // 获取子城邦（CityState）列表
        List<String> townNames = new ArrayList<>();
        Map<String, org.bukkit.Location> townLocations = new LinkedHashMap<>();
        org.bukkit.Location capitalLoc = nation.capitalLocation();

        // 通过 CityService 获取城市数据（如果可用）
        dev.starcore.starcore.module.city.CityService cityService =
            context.serviceRegistry().find(dev.starcore.starcore.module.city.CityService.class).orElse(null);

        if (cityService != null) {
            Collection<dev.starcore.starcore.module.city.model.City> cities =
                cityService.getNationCities(nationId);
            for (dev.starcore.starcore.module.city.model.City city : cities) {
                townNames.add(city.name());
                if (city.spawnChunk() != null) {
                    townLocations.put(city.name(), city.spawnChunk());
                }
            }
        }

        // 同时获取子国家（城邦国家）
        Collection<Nation> cityStates = cityStatesOf(nationId);
        for (Nation cityState : cityStates) {
            if (!townNames.contains(cityState.name())) {
                townNames.add(cityState.name());
            }
            // 如果城市没有单独的 spawn 位置，使用首都位置
            if (cityState.capitalLocation() != null && !townLocations.containsKey(cityState.name())) {
                townLocations.put(cityState.name(), cityState.capitalLocation());
            }
        }

        // 如果没有首都位置但有领土，设置第一个领土块为中心
        if (capitalLoc == null) {
            Collection<dev.starcore.starcore.foundation.territory.model.TerritoryClaim> claims = claimsOf(nationId);
            if (!claims.isEmpty()) {
                var firstClaim = claims.iterator().next();
                org.bukkit.World world = org.bukkit.Bukkit.getWorld(firstClaim.coordinate().world());
                if (world != null) {
                    capitalLoc = new org.bukkit.Location(world,
                        firstClaim.coordinate().x() * 16 + 8,
                        64,
                        firstClaim.coordinate().z() * 16 + 8);
                }
            }
        }

        // 更新 Nation 的城镇缓存
        nation.refreshTownCache(townNames, townLocations, capitalLoc);
    }

    /**
     * 刷新所有国家的缓存数据
     * 通常在服务器启动或模块加载完成后调用
     */
    public void refreshAllNationCaches() {
        for (NationId nationId : nationsById.keySet()) {
            refreshNationCache(nationId);
        }
    }

    /**
     * 解散国家
     * 只有国家创始人可以解散国家
     * 解散国家会：
     * 1. 清除所有领土
     * 2. 清除所有成员关系
     * 3. 清除外交关系
     * 4. 清除国库资金
     * 5. 触发国家解散事件
     *
     * @param nationId 要解散的国家ID
     * @param disbanderId 执行解散操作的玩家ID
     * @return 解散成功返回 true
     */
    public boolean disbandNation(NationId nationId, UUID disbanderId) {
        Nation nation = nationsById.get(nationId);
        if (nation == null) {
            return false;
        }

        // 检查是否是创始人
        if (!nation.founderId().equals(disbanderId)) {
            return false;
        }

        // 检查是否在战争中（战争中不能解散）
        if (atWar(nationId)) {
            return false;
        }

        // 1. 清除所有领土
        Collection<TerritoryClaim> claims = claimsOf(nationId);
        for (TerritoryClaim claim : claims) {
            territoryService.unclaim(nationId.toString(), claim.coordinate());
        }

        // 2. 清除成员关系
        for (UUID playerId : nation.members().stream().map(m -> m.playerId()).toList()) {
            membership.remove(playerId);
        }

        // 3. 清除外交关系
        DiplomacyService diplomacyService = context.serviceRegistry().find(DiplomacyService.class).orElse(null);
        if (diplomacyService != null) {
            for (var rel : diplomacyService.relationsOf(nationId)) {
                diplomacyService.setRelation(nationId, rel.target(), dev.starcore.starcore.module.diplomacy.DiplomacyRelation.NEUTRAL);
            }
        }

        // 4. 清除国库资金（清空所有余额）
        TreasuryService treasuryService = context.serviceRegistry().find(TreasuryService.class).orElse(null);
        if (treasuryService != null) {
            BigDecimal balance = treasuryService.balance(nationId);
            if (balance.signum() > 0) {
                treasuryService.withdraw(nationId, balance);
            }
        }

        // 5. 解散所有子城邦
        Collection<Nation> cityStates = cityStatesOf(nationId);
        for (Nation cityState : cityStates) {
            disbandNation(cityState.id(), disbanderId);
        }

        // 6. 清除名称索引
        names.remove(nation.name().toLowerCase(Locale.ROOT));

        // 7. 从国家列表中移除
        nationsById.remove(nationId);

        // 8. 保存状态
        saveState();

        return true;
    }

    /**
     * 管理员解散国家（简化版，不需要 disbanderId 检查）
     * @param nationId 要解散的国家ID
     * @return 解散成功返回 true
     */
    public boolean disbandNation(NationId nationId) {
        return disbandNation(nationId, null);
    }

    /**
     * 设置国家税率
     * @param nationId 国家ID
     * @param rate 税率 (0.0 - 1.0)
     */
    public void setTaxRate(NationId nationId, double rate) {
        Nation nation = nationsById.get(nationId);
        if (nation != null) {
            double clampedRate = Math.max(0.0, Math.min(1.0, rate));
            nation.setTaxRate(clampedRate);
            saveState();
        }
    }

    /**
     * 设置国家政体类型
     * @param nationId 国家ID
     * @param type 政体类型
     */
    public void setGovernmentType(NationId nationId, GovernmentType type) {
        Nation nation = nationsById.get(nationId);
        if (nation != null && type != null) {
            nation.setGovernmentType(type);
            saveState();
        }
    }

    /**
     * 获取国家领土价格信息
     * @param nationId 国家ID
     * @return 价格详情，如果国家不存在返回默认值
     */
    public ClaimPriceBreakdown getClaimPriceBreakdown(NationId nationId) {
        if (nationId == null) {
            return new ClaimPriceBreakdown(BigDecimal.ZERO, BigDecimal.ZERO, 0, List.of());
        }
        // 使用 ClaimPricingService 计算默认价格
        return new ClaimPriceBreakdown(
            configuration.claimCost(),
            configuration.claimCost(),
            1,
            List.of()
        );
    }

    /**
     * 获取 ClaimToolService
     */
    public ClaimToolService getClaimToolService() {
        return claimToolService;
    }

    /**
     * 获取 QuestService（从 context 获取）
     */
    public QuestService getQuestService() {
        if (context != null && context.serviceRegistry() != null) {
            return context.serviceRegistry().find(QuestService.class).orElse(null);
        }
        return null;
    }

    /**
     * 获取 DailyQuestService（从 context 获取）
     */
    public DailyQuestService getDailyQuestService() {
        if (context != null && context.serviceRegistry() != null) {
            return context.serviceRegistry().find(DailyQuestService.class).orElse(null);
        }
        return null;
    }

    /**
     * 获取国家状态栏服务
     */
    public NationStatusBarService getStatusBarService() {
        return statusBarService;
    }
}
