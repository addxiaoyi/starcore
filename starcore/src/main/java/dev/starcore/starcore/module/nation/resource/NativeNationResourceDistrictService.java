package dev.starcore.starcore.module.nation.resource;

import dev.starcore.starcore.core.config.ConfigurationService;
import dev.starcore.starcore.core.database.DatabaseService;
import dev.starcore.starcore.core.persistence.PersistenceService;
import dev.starcore.starcore.core.service.ServiceRegistry;
import dev.starcore.starcore.foundation.economy.InternalEconomyService;
import dev.starcore.starcore.foundation.feedback.BukkitInGameFeedbackService;
import dev.starcore.starcore.foundation.feedback.InGameFeedbackService;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.foundation.player.OnlinePlayerDirectory;
import dev.starcore.starcore.foundation.territory.TerritoryService;
import dev.starcore.starcore.foundation.territory.model.ChunkClaimSelection;
import dev.starcore.starcore.foundation.territory.model.ChunkCoordinate;
import dev.starcore.starcore.foundation.territory.model.TerritoryClaim;
import dev.starcore.starcore.module.event.EventService;
import dev.starcore.starcore.module.nation.ClaimPricingService;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.nation.model.NationKind;
import dev.starcore.starcore.module.officer.OfficerService;
import dev.starcore.starcore.module.treasury.TreasuryService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MenuType;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

public final class NativeNationResourceDistrictService implements NationResourceDistrictService, Listener {
    private static final String NAMESPACE = "nation";
    private static final int CHUNK_CENTER_OFFSET = 8;
    private static final List<Material> NATURAL_REPLACEABLES = List.of(
        Material.STONE,
        Material.DEEPSLATE,
        Material.TUFF,
        Material.ANDESITE,
        Material.DIORITE,
        Material.GRANITE,
        Material.CALCITE,
        Material.NETHERRACK,
        Material.BASALT,
        Material.BLACKSTONE,
        Material.END_STONE
    );

    private final Plugin plugin;
    private final ConfigurationService configuration;
    private final PersistenceService persistenceService;
    private final TerritoryService territoryService;
    private final InternalEconomyService economyService;
    private final MessageService messages;
    private final NationService nationService;
    private final OnlinePlayerDirectory onlinePlayerDirectory;
    private final NationResourceDistrictStateStorage stateStorage;
    private final MigrationCoreSupport migrationCoreSupport;
    private final InGameFeedbackService feedbackSupport;
    private final ServiceRegistry serviceRegistry;
    private final NamespacedKey migrationItemKey;
    private final NamespacedKey displayKey;
    private final Map<UUID, NationResourceDistrict> districtsById = new LinkedHashMap<>();
    private final Map<String, UUID> districtByChunk = new ConcurrentHashMap<>();
    private final Map<Inventory, ResourceDistrictMenuHolder> resourceMenus = new WeakHashMap<>();
    private int refreshTaskId = -1;
    private EventService eventService;
    private TreasuryService treasuryService;
    private OfficerService officerService;

    public NativeNationResourceDistrictService(
        Plugin plugin,
        ConfigurationService configuration,
        DatabaseService databaseService,
        PersistenceService persistenceService,
        TerritoryService territoryService,
        InternalEconomyService economyService,
        MessageService messages,
        NationService nationService,
        OnlinePlayerDirectory onlinePlayerDirectory,
        Logger logger,
        ServiceRegistry serviceRegistry
    ) {
        this(
            plugin,
            configuration,
            databaseService,
            persistenceService,
            territoryService,
            economyService,
            messages,
            nationService,
            onlinePlayerDirectory,
            logger,
            new BukkitMigrationCoreSupport(),
            new BukkitInGameFeedbackService(plugin, configuration::nationResourceFeedbackProfile),
            serviceRegistry
        );
    }

    NativeNationResourceDistrictService(
        Plugin plugin,
        ConfigurationService configuration,
        DatabaseService databaseService,
        PersistenceService persistenceService,
        TerritoryService territoryService,
        InternalEconomyService economyService,
        MessageService messages,
        NationService nationService,
        OnlinePlayerDirectory onlinePlayerDirectory,
        Logger logger,
        MigrationCoreSupport migrationCoreSupport,
        ServiceRegistry serviceRegistry
    ) {
        this(
            plugin,
            configuration,
            databaseService,
            persistenceService,
            territoryService,
            economyService,
            messages,
            nationService,
            onlinePlayerDirectory,
            logger,
            migrationCoreSupport,
            new BukkitInGameFeedbackService(plugin, configuration::nationResourceFeedbackProfile),
            serviceRegistry
        );
    }

    NativeNationResourceDistrictService(
        Plugin plugin,
        ConfigurationService configuration,
        DatabaseService databaseService,
        PersistenceService persistenceService,
        TerritoryService territoryService,
        InternalEconomyService economyService,
        MessageService messages,
        NationService nationService,
        OnlinePlayerDirectory onlinePlayerDirectory,
        Logger logger,
        MigrationCoreSupport migrationCoreSupport,
        InGameFeedbackService feedbackSupport,
        ServiceRegistry serviceRegistry
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.persistenceService = Objects.requireNonNull(persistenceService, "persistenceService");
        this.territoryService = Objects.requireNonNull(territoryService, "territoryService");
        this.economyService = Objects.requireNonNull(economyService, "economyService");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.nationService = Objects.requireNonNull(nationService, "nationService");
        this.onlinePlayerDirectory = Objects.requireNonNull(onlinePlayerDirectory, "onlinePlayerDirectory");
        this.migrationCoreSupport = Objects.requireNonNull(migrationCoreSupport, "migrationCoreSupport");
        this.feedbackSupport = Objects.requireNonNull(feedbackSupport, "feedbackSupport");
        this.serviceRegistry = Objects.requireNonNull(serviceRegistry, "serviceRegistry");
        this.stateStorage = new DatabaseAwareNationResourceDistrictStateStorage(
            NAMESPACE,
            Objects.requireNonNull(databaseService, "databaseService"),
            persistenceService,
            Objects.requireNonNull(logger, "logger")
        );
        this.migrationItemKey = new NamespacedKey(plugin, "resource_district_migration");
        this.displayKey = new NamespacedKey(plugin, "resource_district_display");
    }

    public void start() {
        persistenceService.ensureNamespace(NAMESPACE);
        loadState();
        warmBeaconBlockData();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTask(plugin, this::placeKnownDistricts);
        startRefreshTask();
    }

    public void stop() {
        stopRefreshTask();
        HandlerList.unregisterAll(this);
        flushState();
    }

    @Override
    public synchronized Collection<NationResourceDistrictSnapshot> districts() {
        return districtsById.values().stream()
            .map(NationResourceDistrict::snapshot)
            .toList();
    }

    @Override
    public synchronized Collection<NationResourceDistrictSnapshot> districtsOf(NationId nationId) {
        return districtsById.values().stream()
            .filter(district -> district.nationId().equals(nationId))
            .map(NationResourceDistrict::snapshot)
            .toList();
    }

    @Override
    public synchronized Optional<NationResourceDistrictSnapshot> districtAt(ChunkCoordinate coordinate) {
        UUID id = districtByChunk.get(chunkKey(coordinate));
        NationResourceDistrict district = id == null ? null : districtsById.get(id);
        return district == null ? Optional.empty() : Optional.of(district.snapshot());
    }

    @Override
    public int districtLimitFor(Nation nation) {
        if (!configuration.nationResourcesEnabled() || nation == null || nation.kind() != NationKind.NATION) {
            return 0;
        }
        int level = configuration.nationLevelForExperience(nation.experience());
        return configuration.nationResourceDistrictLimitForLevel(level);
    }

    @Override
    public NationResourceDistrictMigrationResult beginMigration(Player player, UUID districtId) {
        if (player == null) {
            return NationResourceDistrictMigrationResult.failure(
                "player-offline",
                msg("resource.district.migration.player-offline"),
                null
            );
        }
        if (districtId == null) {
            return NationResourceDistrictMigrationResult.failure(
                "district-not-found",
                msg("resource.district.migration.not-found"),
                null
            );
        }
        NationResourceDistrict district = synchronizedDistrict(districtId).orElse(null);
        if (district == null) {
            return NationResourceDistrictMigrationResult.failure(
                "district-not-found",
                msg("resource.district.migration.not-found"),
                null
            );
        }
        return beginMigration(player, district);
    }

    @Override
    public void ensureDistricts(Nation nation) {
        if (!configuration.nationResourcesEnabled() || nation == null || nation.kind() != NationKind.NATION) {
            return;
        }
        List<NationResourceDistrict> created = new ArrayList<>();
        synchronized (this) {
            int allowed = districtLimitFor(nation);
            if (allowed <= 0) {
                return;
            }
            while (countDistricts(nation.id()) < allowed) {
                Optional<ChunkCoordinate> selected = selectResourceChunk(nation);
                if (selected.isEmpty()) {
                    break;
                }
                BiomeProfile profile = biomeProfile(selected.get());
                NationResourceDistrict district = new NationResourceDistrict(UUID.randomUUID(), nation.id(), selected.get(), profile.name(), profile.richness());
                districtsById.put(district.id(), district);
                districtByChunk.put(chunkKey(district.coordinate()), district.id());
                created.add(district);
            }
        }
        long now = System.currentTimeMillis();
        boolean changed = !created.isEmpty();
        for (NationResourceDistrict district : created) {
            placeBeacon(district);
            changed |= refreshDistrictIfDue(district, now, true);
        }
        if (changed) {
            saveState();
        }
    }

    @Override
    public synchronized String summary() {
        int waiting = (int) districtsById.values().stream()
            .filter(district -> district.migrationState() != NationResourceDistrict.MigrationState.NONE)
            .count();
        return districtsById.size() + " resource district(s), " + waiting + " migration(s) waiting";
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!configuration.nationResourcesEnabled() || event.getHand() != EquipmentSlot.HAND || event.getClickedBlock() == null) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (isMigrationItem(item)) {
            event.setCancelled(true);
            handleMigrationTarget(event.getPlayer(), event.getClickedBlock(), item, event.getHand());
            return;
        }
        NationResourceDistrict district = districtByBeacon(event.getClickedBlock()).orElse(null);
        if (district == null) {
            return;
        }
        event.setCancelled(true);
        if (!canOperateBeacon(event.getPlayer(), district)) {
            event.getPlayer().sendMessage(Component.text(msg("resource.district.beacon-open-denied"), NamedTextColor.RED));
            emitFeedback("migration-blocked", event.getPlayer(), district);
            return;
        }
        openDistrictMenu(event.getPlayer(), district);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        NationResourceDistrict beaconDistrict = districtByBeacon(block).orElse(null);
        if (beaconDistrict != null) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text(msg("resource.district.beacon-protected"), NamedTextColor.RED));
            return;
        }
        NationResourceDistrict resourceDistrict = removeResourceBlock(block).orElse(null);
        if (resourceDistrict == null) {
            return;
        }
        event.getPlayer().sendMessage(Component.text(msg("resource.district.mined", resourceDistrict.resourceBlocks().size()), NamedTextColor.GREEN));
        emitFeedback("resource-mined", event.getPlayer(), block);
        updateDisplay(resourceDistrict);
        maybeCompleteMigration(resourceDistrict, false);
        saveState();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        handleExplosionBlocks(event.blockList());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        handleExplosionBlocks(event.blockList());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (event.getBlocks().stream().anyMatch(this::isBeaconBlock)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (event.getBlocks().stream().anyMatch(this::isBeaconBlock)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        ResourceDistrictMenuHolder holder = resourceMenus.get(topInventory);
        if (holder == null && topInventory.getHolder() instanceof ResourceDistrictMenuHolder legacyHolder) {
            holder = legacyHolder;
        }
        if (holder == null) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(topInventory)) {
            return;
        }
        ItemStack clicked = event.getCurrentItem();
        NationResourceDistrictRules.ResourceDistrictMenuAction action = NationResourceDistrictRules.menuAction(
            holder.confirmation(),
            event.getSlot(),
            clicked == null ? null : clicked.getType()
        );
        if (action == NationResourceDistrictRules.ResourceDistrictMenuAction.NONE) {
            return;
        }
        NationResourceDistrict district = synchronizedDistrict(holder.districtId()).orElse(null);
        if (district == null) {
            player.closeInventory();
            player.sendMessage(Component.text(msg("resource.district.stale-menu"), NamedTextColor.YELLOW));
            return;
        }
        if (holder.confirmation()) {
            if (action == NationResourceDistrictRules.ResourceDistrictMenuAction.BEGIN_MIGRATION) {
                NationResourceDistrictMigrationResult result = beginMigration(player, district);
                reopenDistrictMenu(player, holder.districtId(), feedbackLore(result));
                return;
            }
            if (action == NationResourceDistrictRules.ResourceDistrictMenuAction.RETURN_TO_STATUS) {
                openDistrictMenu(player, district);
            }
            return;
        }
        if (action == NationResourceDistrictRules.ResourceDistrictMenuAction.OPEN_MIGRATION_CONFIRMATION) {
            openMigrationConfirmation(player, district);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        resourceMenus.remove(event.getView().getTopInventory());
    }

    private void handleExplosionBlocks(List<Block> blocks) {
        boolean changed = false;
        for (Block block : List.copyOf(blocks)) {
            if (isBeaconBlock(block)) {
                blocks.remove(block);
                continue;
            }
            NationResourceDistrict district = removeResourceBlock(block).orElse(null);
            if (district != null) {
                changed = true;
                updateDisplay(district);
                maybeCompleteMigration(district, false);
            }
        }
        if (changed) {
            saveState();
        }
    }

    private void startRefreshTask() {
        if (refreshTaskId != -1 || !configuration.nationResourcesEnabled()) {
            return;
        }
        refreshTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(
            plugin,
            this::refreshAllDistricts,
            configuration.nationResourceRefreshIntervalTicks(),
            configuration.nationResourceRefreshIntervalTicks()
        );
    }

    private void stopRefreshTask() {
        if (refreshTaskId != -1) {
            Bukkit.getScheduler().cancelTask(refreshTaskId);
            refreshTaskId = -1;
        }
    }

    private void refreshAllDistricts() {
        if (!configuration.nationResourcesEnabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        boolean changed = false;
        List<NationResourceDistrict> snapshot;
        synchronized (this) {
            snapshot = List.copyOf(districtsById.values());
        }
        for (NationResourceDistrict district : snapshot) {
            changed |= maybeCompleteMigration(district, false);
            if (NationResourceDistrictRules.forceMigrationDue(district, now)) {
                changed |= maybeCompleteMigration(district, true);
                continue;
            }
            changed |= refreshDistrictIfDue(district, now, false);
        }
        if (changed) {
            saveState();
        }
    }

    private boolean refreshDistrictIfDue(NationResourceDistrict district, long now, boolean force) {
        if (district.migrationState() == NationResourceDistrict.MigrationState.WAITING_DEPLETION) {
            return false;
        }
        synchronized (this) {
            if (!force && (district.nextRefreshAtMillis() > now || !district.resourceBlocks().isEmpty())) {
                return false;
            }
        }
        int targetAmount = resourceAmount(district);
        int generated = generateResourceBlocks(district, targetAmount);
        long nextRefresh = now + Duration.ofMinutes(refreshCooldownMinutes(district)).toMillis();
        long experience = NationResourceDistrictRules.refreshExperience(configuration.nationResourceRefreshBaseExperience(), district.biomeRichness());
        synchronized (this) {
            district.setRefreshTimes(now, nextRefresh);
            if (generated > 0) {
                district.addExperience(experience);
            }
        }
        if (generated > 0) {
            nationService.addExperience(district.nationId(), experience);
            ResourceIncomeSettlement income = settleResourceIncome(district, generated);
            recordDistrictEvent(
                district.nationId(),
                "resource.refresh",
                district.id().toString(),
                msg(
                    "resource.district.event.refresh",
                    district.coordinate().toString(),
                    generated,
                    experience,
                    income.amount().toPlainString()
                )
            );
            emitFeedback("resource-refreshed", null, district);
        }
        updateDisplay(district);
        return true;
    }

    private ResourceIncomeSettlement settleResourceIncome(NationResourceDistrict district, int generatedBlocks) {
        if (!configuration.nationResourceRefreshTreasuryIncomeEnabled()) {
            return new ResourceIncomeSettlement(BigDecimal.ZERO, BigDecimal.ZERO);
        }
        TreasuryService service = treasuryService();
        if (service == null) {
            return new ResourceIncomeSettlement(BigDecimal.ZERO, BigDecimal.ZERO);
        }
        BigDecimal amount = NationResourceDistrictRules.treasuryIncome(
            configuration.nationResourceRefreshTreasuryBaseIncome(),
            configuration.nationResourceRefreshTreasuryIncomePerBlock(),
            generatedBlocks,
            district.biomeRichness(),
            configuration.nationResourceRefreshTreasuryRichnessMultiplier()
        );
        if (amount.signum() <= 0) {
            return new ResourceIncomeSettlement(BigDecimal.ZERO, service.balance(district.nationId()));
        }
        service.deposit(district.nationId(), amount);
        BigDecimal balance = service.balance(district.nationId());
        recordDistrictEvent(
            district.nationId(),
            "treasury.resource-income",
            district.id().toString(),
            msg(
                "resource.district.event.treasury-income",
                district.coordinate().toString(),
                amount.toPlainString(),
                balance.toPlainString()
            )
        );
        return new ResourceIncomeSettlement(amount, balance);
    }

    private int generateResourceBlocks(NationResourceDistrict district, int targetAmount) {
        World world = Bukkit.getWorld(district.coordinate().world());
        if (world == null || targetAmount <= 0) {
            return 0;
        }
        int amount = Math.min(targetAmount, configuration.nationResourceRefreshMaxBlocksPerCycle());
        int minY = Math.max(world.getMinHeight() + 8, -48);
        int maxY = Math.min(world.getMaxHeight() - 12, 48);
        if (maxY <= minY) {
            minY = world.getMinHeight() + 4;
            maxY = Math.max(minY + 1, world.getSeaLevel());
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int generated = 0;
        int attempts = Math.max(64, amount * 32);
        for (int attempt = 0; attempt < attempts && generated < amount; attempt++) {
            int x = district.coordinate().x() * ChunkClaimSelection.CHUNK_SIZE + random.nextInt(ChunkClaimSelection.CHUNK_SIZE);
            int z = district.coordinate().z() * ChunkClaimSelection.CHUNK_SIZE + random.nextInt(ChunkClaimSelection.CHUNK_SIZE);
            int y = random.nextInt(minY, maxY + 1);
            Block block = world.getBlockAt(x, y, z);
            if (!NATURAL_REPLACEABLES.contains(block.getType())) {
                continue;
            }
            Material material = oreFor(block.getType(), district.biomeRichness(), random);
            block.setType(material, false);
            synchronized (this) {
                district.resourceBlocks().add(new ResourceBlockLocation(world.getName(), x, y, z, material));
            }
            generated++;
        }
        return generated;
    }

    private Material oreFor(Material replaced, double richness, ThreadLocalRandom random) {
        List<Material> candidates;
        if (richness < 0.75D) {
            candidates = List.of(Material.COAL_ORE, Material.COPPER_ORE);
        } else if (richness < 1.05D) {
            candidates = List.of(Material.COAL_ORE, Material.COPPER_ORE, Material.IRON_ORE);
        } else if (richness < 1.25D) {
            candidates = List.of(Material.COPPER_ORE, Material.IRON_ORE, Material.REDSTONE_ORE, Material.LAPIS_ORE);
        } else {
            candidates = List.of(Material.IRON_ORE, Material.GOLD_ORE, Material.REDSTONE_ORE, Material.LAPIS_ORE, Material.DIAMOND_ORE, Material.EMERALD_ORE);
        }
        Material selected = candidates.get(random.nextInt(candidates.size()));
        if (replaced == Material.DEEPSLATE) {
            return deepslateVariant(selected);
        }
        if (replaced == Material.NETHERRACK) {
            return random.nextBoolean() ? Material.NETHER_QUARTZ_ORE : Material.NETHER_GOLD_ORE;
        }
        return selected;
    }

    private Material deepslateVariant(Material material) {
        return switch (material) {
            case COAL_ORE -> Material.DEEPSLATE_COAL_ORE;
            case COPPER_ORE -> Material.DEEPSLATE_COPPER_ORE;
            case IRON_ORE -> Material.DEEPSLATE_IRON_ORE;
            case GOLD_ORE -> Material.DEEPSLATE_GOLD_ORE;
            case REDSTONE_ORE -> Material.DEEPSLATE_REDSTONE_ORE;
            case LAPIS_ORE -> Material.DEEPSLATE_LAPIS_ORE;
            case DIAMOND_ORE -> Material.DEEPSLATE_DIAMOND_ORE;
            case EMERALD_ORE -> Material.DEEPSLATE_EMERALD_ORE;
            default -> material;
        };
    }

    private int resourceAmount(NationResourceDistrict district) {
        return NationResourceDistrictRules.resourceAmount(
            configuration.nationResourceRefreshBaseAmount(),
            district.biomeRichness(),
            configuration.nationResourceRefreshRichnessAmountMultiplier()
        );
    }

    private long refreshCooldownMinutes(NationResourceDistrict district) {
        return NationResourceDistrictRules.refreshCooldownMinutes(
            configuration.nationResourceRefreshBaseCooldownMinutes(),
            configuration.nationResourceRefreshMinCooldownMinutes(),
            configuration.nationResourceRefreshMaxCooldownMinutes(),
            district.biomeRichness()
        );
    }

    private Optional<ChunkCoordinate> selectResourceChunk(Nation nation) {
        List<ChunkCoordinate> candidates = territoryService.claimsByOwner(nation.id().toString()).stream()
            .map(TerritoryClaim::coordinate)
            .filter(coordinate -> !districtByChunk.containsKey(chunkKey(coordinate)))
            .toList();
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        return NationResourceDistrictRules.selectWeightedChunk(candidates, coordinate -> biomeProfile(coordinate).richness(), ThreadLocalRandom.current()::nextDouble);
    }

    private BiomeProfile biomeProfile(ChunkCoordinate coordinate) {
        World world = Bukkit.getWorld(coordinate.world());
        if (world == null) {
            double richness = configuration.claimPricingBiomeRichness("unknown", configuration.claimPricingUnknownBiomeRichness());
            return new BiomeProfile("unknown", richness);
        }
        int centerX = coordinate.x() * ChunkClaimSelection.CHUNK_SIZE + CHUNK_CENTER_OFFSET;
        int centerZ = coordinate.z() * ChunkClaimSelection.CHUNK_SIZE + CHUNK_CENTER_OFFSET;
        Biome biome = world.getBiome(centerX, world.getSeaLevel(), centerZ);
        String biomeName = biome.getKey().getKey().toLowerCase(Locale.ROOT);
        double fallback = ClaimPricingService.estimatedBiomeRichness(biomeName, configuration.claimPricingUnknownBiomeRichness());
        return new BiomeProfile(biomeName, configuration.claimPricingBiomeRichness(biomeName, fallback));
    }

    private void placeKnownDistricts() {
        List<NationResourceDistrict> snapshot;
        synchronized (this) {
            snapshot = List.copyOf(districtsById.values());
        }
        snapshot.forEach(this::placeBeacon);
    }

    private void warmBeaconBlockData() {
        try {
            configuration.nationResourceBeaconMaterial().createBlockData();
        } catch (RuntimeException | LinkageError exception) {
            if (configuration.debug()) {
                plugin.getLogger().fine("STARCORE resource district beacon block data prewarm skipped: " + exception.getMessage());
            }
        }
    }

    private void placeBeacon(NationResourceDistrict district) {
        World world = Bukkit.getWorld(district.coordinate().world());
        if (world == null) {
            return;
        }
        Material marker = configuration.nationResourceBeaconMaterial();
        if (district.beaconY() > world.getMinHeight()) {
            Block existing = world.getBlockAt(district.beaconX(), district.beaconY(), district.beaconZ());
            if (existing.getType() == marker) {
                updateDisplay(district);
                return;
            }
        }
        int x = district.coordinate().x() * ChunkClaimSelection.CHUNK_SIZE + CHUNK_CENTER_OFFSET;
        int z = district.coordinate().z() * ChunkClaimSelection.CHUNK_SIZE + CHUNK_CENTER_OFFSET;
        int y = Math.clamp(world.getHighestBlockYAt(x, z) + 1, world.getMinHeight() + 1, world.getMaxHeight() - 1);
        Block beacon = world.getBlockAt(x, y, z);
        beacon.setType(marker, false);
        synchronized (this) {
            district.setBeacon(x, y, z);
        }
        updateDisplay(district);
    }

    private void updateDisplay(NationResourceDistrict district) {
        if (!configuration.nationResourceBeaconDisplayEnabled()) {
            return;
        }
        World world = Bukkit.getWorld(district.coordinate().world());
        if (world == null || district.beaconY() <= world.getMinHeight()) {
            return;
        }
        Location location = new Location(
            world,
            district.beaconX() + 0.5D,
            district.beaconY() + configuration.nationResourceBeaconDisplayYOffset(),
            district.beaconZ() + 0.5D
        );
        TextDisplay display = existingDisplay(location, district.id()).orElseGet(() -> {
            TextDisplay spawned = world.spawn(location, TextDisplay.class);
            spawned.getPersistentDataContainer().set(displayKey, PersistentDataType.STRING, district.id().toString());
            spawned.setPersistent(false);
            spawned.setBillboard(Display.Billboard.CENTER);
            spawned.setViewRange(48.0F);
            return spawned;
        });
        display.teleport(location);
        display.text(Component.text(displayText(district), NamedTextColor.AQUA));
    }

    private Optional<TextDisplay> existingDisplay(Location location, UUID districtId) {
        return location.getWorld().getNearbyEntities(location, 2.0D, 4.0D, 2.0D).stream()
            .filter(TextDisplay.class::isInstance)
            .map(TextDisplay.class::cast)
            .filter(display -> districtId.toString().equals(display.getPersistentDataContainer().get(displayKey, PersistentDataType.STRING)))
            .findFirst();
    }

    private String displayText(NationResourceDistrict district) {
        String nationName = nationService.nationById(district.nationId()).map(Nation::name).orElse(msg("resource.district.unknown-nation"));
        return NationResourceDistrictViewSupport.displayText(
            messages,
            nationName,
            district,
            operationalOverview(district)
        );
    }

    private void removeBeaconAndDisplay(NationResourceDistrict district) {
        World world = Bukkit.getWorld(district.coordinate().world());
        if (world == null) {
            return;
        }
        if (district.beaconY() > world.getMinHeight()) {
            Block beacon = world.getBlockAt(district.beaconX(), district.beaconY(), district.beaconZ());
            if (beacon.getType() == configuration.nationResourceBeaconMaterial()) {
                beacon.setType(Material.AIR, false);
            }
            Location location = new Location(world, district.beaconX() + 0.5D, district.beaconY() + configuration.nationResourceBeaconDisplayYOffset(), district.beaconZ() + 0.5D);
            for (Entity entity : world.getNearbyEntities(location, 2.0D, 4.0D, 2.0D)) {
                if (entity instanceof TextDisplay display
                    && district.id().toString().equals(display.getPersistentDataContainer().get(displayKey, PersistentDataType.STRING))) {
                    display.remove();
                }
            }
        }
    }

    private Optional<NationResourceDistrict> districtByBeacon(Block block) {
        if (block == null) {
            return Optional.empty();
        }
        synchronized (this) {
            return districtsById.values().stream()
                .filter(district -> district.coordinate().world().equals(block.getWorld().getName()))
                .filter(district -> district.beaconX() == block.getX() && district.beaconY() == block.getY() && district.beaconZ() == block.getZ())
                .findFirst();
        }
    }

    private boolean isBeaconBlock(Block block) {
        return districtByBeacon(block).isPresent();
    }

    private Optional<NationResourceDistrict> removeResourceBlock(Block block) {
        if (block == null) {
            return Optional.empty();
        }
        String key = blockKey(block);
        synchronized (this) {
            for (NationResourceDistrict district : districtsById.values()) {
                boolean removed = district.resourceBlocks().removeIf(location -> location.key().equals(key));
                if (removed) {
                    return Optional.of(district);
                }
            }
        }
        return Optional.empty();
    }

    private void openDistrictMenu(Player player, NationResourceDistrict district) {
        openDistrictMenu(player, district, List.of());
    }

    private void openDistrictMenu(Player player, NationResourceDistrict district, List<String> actionFeedbackLore) {
        ResourceDistrictMenuHolder holder = new ResourceDistrictMenuHolder(district.id(), false);
        ResourceDistrictMenu menu = createResourceDistrictMenu(player, holder, msg("resource.district.menu.title"));
        Inventory inventory = menu.inventory();
        NationResourceDistrictOperationalOverview overview = operationalOverview(district);
        NationResourceDistrictSnapshot snapshot = district.snapshot();
        NationResourceDistrictCommandSupport.CommandState commandState = resourceDistrictCommandState(player, snapshot, "ready");
        NationResourceDistrictCommandSupport.CommandPresentation commandPresentation = NationResourceDistrictCommandSupport.presentation(messages, commandState);
        long nowMillis = System.currentTimeMillis();
        NationResourceDistrictMenuSupport.MenuPaneSpec statusPane = NationResourceDistrictMenuSupport.statusPane(
            messages,
            nationService.nationById(district.nationId()).map(Nation::name).orElse(msg("resource.district.unknown-nation")),
            district,
            overview,
            nowMillis,
            null
        );
        inventory.setItem(10, item(Material.BEACON, statusPane.name(), statusPane.lore()));
        NationResourceDistrictMenuSupport.MenuPaneSpec actionPane =
            NationResourceDistrictMenuSupport.actionPane(messages, commandState, commandPresentation, actionFeedbackLore);
        inventory.setItem(13, item(actionMaterial(commandState), actionPane.name(), actionPane.lore()));
        NationResourceDistrictMenuSupport.MenuPaneSpec migrationPane = NationResourceDistrictMenuSupport.migrationStatusPane(
            messages,
            district,
            overview,
            commandPresentation,
            nowMillis,
            null
        );
        inventory.setItem(16, item(Material.CLOCK, migrationPane.name(), migrationPane.lore()));
        menu.open();
    }

    private boolean canOperateBeacon(Player player, NationResourceDistrict district) {
        return canManageMigration(player, district);
    }

    private void openMigrationConfirmation(Player player, NationResourceDistrict district) {
        if (!canManageMigration(player, district)) {
            player.sendMessage(Component.text(msg("resource.district.migration.leader-only"), NamedTextColor.RED));
            return;
        }
        ResourceDistrictMenuHolder holder = new ResourceDistrictMenuHolder(district.id(), true);
        ResourceDistrictMenu menu = createResourceDistrictMenu(player, holder, msg("resource.district.menu.confirm-title"));
        Inventory inventory = menu.inventory();
        NationResourceDistrictOperationalOverview overview = operationalOverview(district);
        NationResourceDistrictSnapshot snapshot = district.snapshot();
        NationResourceDistrictCommandSupport.CommandState commandState = resourceDistrictCommandState(player, snapshot, "ready");
        NationResourceDistrictCommandSupport.CommandPresentation commandPresentation = NationResourceDistrictCommandSupport.presentation(messages, commandState);
        long nowMillis = System.currentTimeMillis();
        NationResourceDistrictMenuSupport.MenuPaneSpec statusPane =
            NationResourceDistrictMenuSupport.confirmationStatusPane(messages, district, nowMillis, null);
        NationResourceDistrictMenuSupport.MenuPaneSpec overviewPane = NationResourceDistrictMenuSupport.confirmationOverviewPane(
            messages,
            district,
            overview,
            nowMillis,
            null
        );
        inventory.setItem(10, item(Material.BEACON, statusPane.name(), statusPane.lore()));
        inventory.setItem(13, item(Material.CLOCK, overviewPane.name(), overviewPane.lore()));
        NationResourceDistrictMenuSupport.MenuPaneSpec confirmationActionPane =
            NationResourceDistrictMenuSupport.confirmationActionPane(messages, commandState, commandPresentation);
        inventory.setItem(11, item(Material.LIME_CONCRETE, confirmationActionPane.name(), confirmationActionPane.lore()));
        inventory.setItem(15, item(Material.BARRIER, msg("resource.district.menu.cancel-name"), List.of(msg("resource.district.menu.cancel-lore"))));
        menu.open();
    }

    private NationResourceDistrictCommandSupport.CommandState resourceDistrictCommandState(
        Player viewer,
        NationResourceDistrictSnapshot snapshot,
        String fallbackActionState
    ) {
        if (viewer == null) {
            return NationResourceDistrictCommandSupport.resolve(
                configuration,
                economyService,
                nationService,
                onlinePlayerDirectory,
                null,
                snapshot,
                fallbackActionState
            );
        }
        Nation viewerNation = nationService.nationOf(viewer.getUniqueId()).orElse(null);
        return NationResourceDistrictCommandSupport.evaluate(
            economyService.balance(viewer.getUniqueId()),
            configuration.nationResourceMigrationCost(),
            viewerNation == null ? null : viewerNation.id().toString(),
            viewerNation != null && viewerNation.founderId().equals(viewer.getUniqueId()),
            canManageMigration(viewer, snapshot == null || snapshot.nationId() == null ? null : snapshot.nationId()),
            authorizedMigrationOfficerRole(viewer.getUniqueId(), snapshot == null ? null : snapshot.nationId()).orElse(""),
            true,
            snapshot == null || snapshot.nationId() == null ? null : snapshot.nationId().toString(),
            snapshot == null ? null : snapshot.migrationState(),
            fallbackActionState
        );
    }

    private boolean canManageMigration(Player player, NationResourceDistrict district) {
        return district != null && canManageMigration(player, district.nationId());
    }

    private boolean canManageMigration(Player player, NationId nationId) {
        if (player == null || nationId == null) {
            return false;
        }
        if (player.hasPermission("starcore.admin")) {
            return true;
        }
        UUID playerId = player.getUniqueId();
        return nationService.isFounder(playerId, nationId)
            || authorizedMigrationOfficerRole(playerId, nationId).isPresent();
    }

    private Optional<String> authorizedMigrationOfficerRole(UUID playerId, NationId nationId) {
        if (playerId == null || nationId == null) {
            return Optional.empty();
        }
        OfficerService service = officerService();
        if (service == null) {
            return Optional.empty();
        }
        return configuration.nationResourceMigrationOfficerRoles().stream()
            .map(role -> role == null ? "" : role.trim().toLowerCase(Locale.ROOT).replace('_', '-'))
            .filter(role -> !role.isBlank())
            .filter(role -> service.officer(nationId, role)
                .map(appointment -> playerId.equals(appointment.playerId()))
                .orElse(false))
            .findFirst();
    }

    private ResourceDistrictMenu createResourceDistrictMenu(Player player, ResourceDistrictMenuHolder holder, String title) {
        try {
            InventoryView view = MenuType.GENERIC_9X3.builder()
                .title(Component.text(title))
                .build(player);
            Inventory inventory = view.getTopInventory();
            holder.setInventory(inventory);
            resourceMenus.put(inventory, holder);
            return new ResourceDistrictMenu(inventory, view::open);
        } catch (RuntimeException | LinkageError exception) {
            Inventory inventory = Bukkit.createInventory(holder, 27, title);
            holder.setInventory(inventory);
            resourceMenus.put(inventory, holder);
            return new ResourceDistrictMenu(inventory, () -> player.openInventory(inventory));
        }
    }

    private NationResourceDistrictMigrationResult beginMigration(Player player, NationResourceDistrict district) {
        if (!canManageMigration(player, district)) {
            String message = msg("resource.district.migration.leader-only");
            player.sendMessage(Component.text(message, NamedTextColor.RED));
            emitFeedback("migration-blocked", player, district);
            return NationResourceDistrictMigrationResult.failure("leader-only", message, snapshotOf(district));
        }
        synchronized (this) {
            if (!NationResourceDistrictRules.canIssueMigrationCore(district)) {
                String message = msg("resource.district.migration.already-waiting");
                player.sendMessage(Component.text(message, NamedTextColor.YELLOW));
                emitFeedback("migration-blocked", player, district);
                return NationResourceDistrictMigrationResult.failure("already-waiting", message, district.snapshot());
            }
        }
        if (NationResourceDistrictRules.shouldChargeMigrationCost(district)) {
            BigDecimal cost = configuration.nationResourceMigrationCost();
            if (cost.signum() > 0 && !economyService.withdraw(player.getUniqueId(), cost)) {
                String message = msg("resource.district.migration.insufficient-balance", cost.toPlainString());
                player.sendMessage(Component.text(message, NamedTextColor.RED));
                emitFeedback("migration-blocked", player, district);
                return NationResourceDistrictMigrationResult.failure("insufficient-balance", message, snapshotOf(district));
            }
        }
        long now = System.currentTimeMillis();
        synchronized (this) {
            district.setMigration(NationResourceDistrict.MigrationState.AWAITING_TARGET, null, now, 0L);
        }
        migrationCoreSupport.giveToPlayer(player, createMigrationItem(district));
        String message = msg("resource.district.migration.core-received");
        player.sendMessage(Component.text(message, NamedTextColor.GREEN));
        recordDistrictEvent(
            district.nationId(),
            "resource.migration.requested",
            district.id().toString(),
            msg(
                "resource.district.event.migration-requested",
                player.getName(),
                district.coordinate().toString(),
                configuration.nationResourceMigrationCost().toPlainString()
            )
        );
        updateDisplay(district);
        emitFeedback("migration-started", player, district);
        saveState();
        return NationResourceDistrictMigrationResult.success("migration-started", message, snapshotOf(district));
    }

    private void handleMigrationTarget(Player player, Block clickedBlock, ItemStack item, EquipmentSlot hand) {
        UUID districtId = migrationItemDistrictId(item).orElse(null);
        NationResourceDistrict district = districtId == null ? null : synchronizedDistrict(districtId).orElse(null);
        if (district == null) {
            player.sendMessage(Component.text(msg("resource.district.migration.core-invalid"), NamedTextColor.RED));
            emitFeedback("migration-blocked", player, clickedBlock);
            return;
        }
        if (!canManageMigration(player, district)) {
            player.sendMessage(Component.text(msg("resource.district.migration.target-leader-only"), NamedTextColor.RED));
            emitFeedback("migration-blocked", player, district);
            return;
        }
        synchronized (this) {
            if (!NationResourceDistrictRules.canAcceptMigrationTarget(district)) {
                player.sendMessage(Component.text(msg("resource.district.migration.target-not-awaiting"), NamedTextColor.RED));
                emitFeedback("migration-blocked", player, district);
                return;
            }
        }
        ChunkCoordinate target = new ChunkCoordinate(clickedBlock.getWorld().getName(), clickedBlock.getChunk().getX(), clickedBlock.getChunk().getZ());
        TerritoryClaim claim = territoryService.claimAt(target).orElse(null);
        if (claim == null || !district.nationId().toString().equals(claim.ownerId())) {
            player.sendMessage(Component.text(msg("resource.district.migration.target-must-own"), NamedTextColor.RED));
            emitFeedback("migration-blocked", player, clickedBlock);
            return;
        }
        if (districtAt(target).isPresent()) {
            player.sendMessage(Component.text(msg("resource.district.migration.target-already-district"), NamedTextColor.RED));
            emitFeedback("migration-blocked", player, clickedBlock);
            return;
        }
        long now = System.currentTimeMillis();
        long forceAt = NationResourceDistrictRules.forceMigrationAtMillis(now, configuration.nationResourceForceMigrationHours());
        synchronized (this) {
            district.setMigration(NationResourceDistrict.MigrationState.WAITING_DEPLETION, target, now, forceAt);
        }
        migrationCoreSupport.consumeOne(player, hand, item);
        player.sendMessage(Component.text(msg(
            "resource.district.migration.target-set",
            district.resourceBlocks().size(),
            configuration.nationResourceForceMigrationHours()
        ), NamedTextColor.GREEN));
        recordDistrictEvent(
            district.nationId(),
            "resource.migration.target-selected",
            district.id().toString(),
            msg(
                "resource.district.event.migration-target-selected",
                player.getName(),
                district.coordinate().toString(),
                target.toString()
            )
        );
        maybeCompleteMigration(district, false);
        updateDisplay(district);
        emitFeedback("migration-target-selected", player, clickedBlock);
        saveState();
    }

    private boolean maybeCompleteMigration(NationResourceDistrict district, boolean force) {
        synchronized (this) {
            if (!NationResourceDistrictRules.canCompleteMigration(district, force)) {
                return false;
            }
        }
        ChunkCoordinate source = district.coordinate();
        ChunkCoordinate target = district.pendingTarget();
        if (force) {
            clearGeneratedResourceBlocks(district);
        }
        removeBeaconAndDisplay(district);
        BiomeProfile profile = biomeProfile(target);
        synchronized (this) {
            districtByChunk.remove(chunkKey(district.coordinate()));
            district.setCoordinate(target, profile.name(), profile.richness());
            district.setMigration(NationResourceDistrict.MigrationState.NONE, null, 0L, 0L);
            district.resourceBlocks().clear();
            district.setRefreshTimes(System.currentTimeMillis(), 0L);
            districtByChunk.put(chunkKey(district.coordinate()), district.id());
        }
        placeBeacon(district);
        refreshDistrictIfDue(district, System.currentTimeMillis(), true);
        emitFeedback(force ? "migration-completed-forced" : "migration-completed", null, district);
        recordDistrictEvent(
            district.nationId(),
            force ? "resource.migration.completed-forced" : "resource.migration.completed",
            district.id().toString(),
            msg(
                force
                    ? "resource.district.event.migration-completed-forced"
                    : "resource.district.event.migration-completed",
                source.toString(),
                target.toString()
            )
        );
        return true;
    }

    private void clearGeneratedResourceBlocks(NationResourceDistrict district) {
        List<ResourceBlockLocation> blocks;
        synchronized (this) {
            blocks = List.copyOf(district.resourceBlocks());
            district.resourceBlocks().clear();
        }
        for (ResourceBlockLocation location : blocks) {
            World world = Bukkit.getWorld(location.world());
            if (world == null) {
                continue;
            }
            Block block = world.getBlockAt(location.x(), location.y(), location.z());
            if (block.getType() == location.material()) {
                block.setType(Material.AIR, false);
            }
        }
    }

    private ItemStack createMigrationItem(NationResourceDistrict district) {
        List<Component> lore = new ArrayList<>(configuration.nationResourceMigrationItemLore().stream()
            .map(line -> (Component) Component.text(line, NamedTextColor.GRAY))
            .toList());
        lore.add(Component.text(msg("resource.district.migration.item-district", district.id()), NamedTextColor.DARK_GRAY));
        return migrationCoreSupport.createMigrationCore(
            district.id(),
            configuration.nationResourceMigrationItemName(),
            lore,
            migrationItemKey
        );
    }

    private boolean isMigrationItem(ItemStack item) {
        return migrationItemDistrictId(item).isPresent();
    }

    private synchronized NationResourceDistrictSnapshot snapshotOf(NationResourceDistrict district) {
        return district.snapshot();
    }

    private Optional<UUID> migrationItemDistrictId(ItemStack item) {
        return migrationCoreSupport.resolveDistrictId(item, migrationItemKey);
    }

    private ItemStack item(Material material, String name, List<String> loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.GOLD));
        meta.lore(loreLines.stream().map(line -> (Component) Component.text(line, NamedTextColor.GRAY)).toList());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private NationResourceDistrictOperationalOverview operationalOverview(NationResourceDistrict district) {
        return NationResourceDistrictOperationalSupport.overview(configuration, district);
    }

    private void reopenDistrictMenu(Player player, UUID districtId, List<String> actionFeedbackLore) {
        if (player == null || !player.isOnline()) {
            return;
        }
        NationResourceDistrict district = synchronizedDistrict(districtId).orElse(null);
        if (district == null) {
            player.closeInventory();
            player.sendMessage(Component.text(msg("resource.district.stale-menu"), NamedTextColor.YELLOW));
            return;
        }
        openDistrictMenu(player, district, actionFeedbackLore);
    }

    private Material actionMaterial(NationResourceDistrictCommandSupport.CommandState commandState) {
        return switch (String.valueOf(commandState.migrationActionState())) {
            case "ready" -> Material.EMERALD;
            case "awaiting-target" -> Material.NETHER_STAR;
            case "waiting-depletion" -> Material.CLOCK;
            case "insufficient-balance" -> Material.REDSTONE;
            default -> Material.BARRIER;
        };
    }

    private void emitFeedback(String eventKey, Player player, NationResourceDistrict district) {
        feedbackSupport.emit(eventKey, player, districtEffectLocation(district).orElse(null));
    }

    private void emitFeedback(String eventKey, Player player, Block block) {
        feedbackSupport.emit(eventKey, player, blockEffectLocation(block).orElse(null));
    }

    private Optional<Location> districtEffectLocation(NationResourceDistrict district) {
        if (district == null) {
            return Optional.empty();
        }
        World world = Bukkit.getWorld(district.coordinate().world());
        if (world == null) {
            return Optional.empty();
        }
        if (district.beaconY() > world.getMinHeight()) {
            return Optional.of(new Location(
                world,
                district.beaconX() + 0.5D,
                district.beaconY() + 0.5D,
                district.beaconZ() + 0.5D
            ));
        }
        return Optional.of(new Location(
            world,
            district.coordinate().x() * ChunkClaimSelection.CHUNK_SIZE + CHUNK_CENTER_OFFSET + 0.5D,
            world.getSeaLevel() + 1.0D,
            district.coordinate().z() * ChunkClaimSelection.CHUNK_SIZE + CHUNK_CENTER_OFFSET + 0.5D
        ));
    }

    private Optional<Location> blockEffectLocation(Block block) {
        if (block == null || block.getWorld() == null) {
            return Optional.empty();
        }
        return Optional.of(new Location(block.getWorld(), block.getX() + 0.5D, block.getY() + 0.5D, block.getZ() + 0.5D));
    }

    private List<String> feedbackLore(NationResourceDistrictMigrationResult result) {
        return NationResourceDistrictMenuSupport.resultFeedbackLore(messages, result, System.currentTimeMillis(), null);
    }

    private String msg(String key, Object... args) {
        return messages.format(key, args);
    }

    private EventService eventService() {
        if (eventService == null) {
            eventService = serviceRegistry.find(EventService.class).orElse(null);
        }
        return eventService;
    }

    private TreasuryService treasuryService() {
        if (treasuryService == null) {
            treasuryService = serviceRegistry.find(TreasuryService.class).orElse(null);
        }
        return treasuryService;
    }

    private OfficerService officerService() {
        if (officerService == null) {
            officerService = serviceRegistry.find(OfficerService.class).orElse(null);
        }
        return officerService;
    }

    private void recordDistrictEvent(NationId nationId, String type, String context, String message) {
        EventService service = eventService();
        if (service == null || nationId == null || type == null || type.isBlank() || message == null || message.isBlank()) {
            return;
        }
        try {
            service.record(nationId, type, message, context);
        } catch (RuntimeException ignored) {
        }
    }

    private synchronized Optional<NationResourceDistrict> synchronizedDistrict(UUID districtId) {
        return Optional.ofNullable(districtsById.get(districtId));
    }

    private synchronized int countDistricts(NationId nationId) {
        return (int) districtsById.values().stream()
            .filter(district -> district.nationId().equals(nationId))
            .count();
    }

    private void saveState() {
        stateStorage.saveAsync(stateProperties());
    }

    private void flushState() {
        stateStorage.save(stateProperties());
    }

    private Properties stateProperties() {
        List<NationResourceDistrict> districts;
        synchronized (this) {
            districts = districtsById.values().stream()
                .sorted(Comparator.comparing(district -> district.id().toString()))
                .toList();
        }
        return NationResourceDistrictStateCodec.toProperties(districts);
    }

    private void loadState() {
        Properties properties = stateStorage.load();
        synchronized (this) {
            districtsById.clear();
            districtByChunk.clear();
            for (NationResourceDistrict district : NationResourceDistrictStateCodec.fromProperties(properties)) {
                if (district == null || districtByChunk.containsKey(chunkKey(district.coordinate()))) {
                    continue;
                }
                districtsById.put(district.id(), district);
                districtByChunk.put(chunkKey(district.coordinate()), district.id());
            }
        }
    }

    private String chunkKey(ChunkCoordinate coordinate) {
        return coordinate.world() + ':' + coordinate.x() + ':' + coordinate.z();
    }

    private String blockKey(Block block) {
        return block.getWorld().getName() + ':' + block.getX() + ':' + block.getY() + ':' + block.getZ();
    }

    private record BiomeProfile(String name, double richness) {
    }

    private record ResourceDistrictMenu(Inventory inventory, Runnable open) {
    }

    private record ResourceIncomeSettlement(BigDecimal amount, BigDecimal balance) {
    }

    interface MigrationCoreSupport {
        ItemStack createMigrationCore(UUID districtId, String name, List<Component> lore, NamespacedKey migrationItemKey);

        Optional<UUID> resolveDistrictId(ItemStack item, NamespacedKey migrationItemKey);

        void giveToPlayer(Player player, ItemStack item);

        void consumeOne(Player player, EquipmentSlot hand, ItemStack item);
    }

    private static final class BukkitMigrationCoreSupport implements MigrationCoreSupport {
        @Override
        public ItemStack createMigrationCore(UUID districtId, String name, List<Component> lore, NamespacedKey migrationItemKey) {
            ItemStack item = new ItemStack(Material.NETHER_STAR);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text(name, NamedTextColor.AQUA));
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.getPersistentDataContainer().set(migrationItemKey, PersistentDataType.STRING, districtId.toString());
            item.setItemMeta(meta);
            return item;
        }

        @Override
        public Optional<UUID> resolveDistrictId(ItemStack item, NamespacedKey migrationItemKey) {
            if (item == null || item.getType() != Material.NETHER_STAR || !item.hasItemMeta()) {
                return Optional.empty();
            }
            String raw = item.getItemMeta().getPersistentDataContainer().get(migrationItemKey, PersistentDataType.STRING);
            if (raw == null) {
                return Optional.empty();
            }
            try {
                return Optional.of(UUID.fromString(raw));
            } catch (RuntimeException exception) {
                return Optional.empty();
            }
        }

        @Override
        public void giveToPlayer(Player player, ItemStack item) {
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
            leftovers.values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        }

        @Override
        public void consumeOne(Player player, EquipmentSlot hand, ItemStack item) {
            if (item.getAmount() <= 1) {
                player.getInventory().setItem(hand, null);
                return;
            }
            item.setAmount(item.getAmount() - 1);
        }
    }

    private static final class ResourceDistrictMenuHolder implements InventoryHolder {
        private final UUID districtId;
        private final boolean confirmation;
        private Inventory inventory;

        private ResourceDistrictMenuHolder(UUID districtId, boolean confirmation) {
            this.districtId = districtId;
            this.confirmation = confirmation;
        }

        private UUID districtId() {
            return districtId;
        }

        private boolean confirmation() {
            return confirmation;
        }

        private void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
