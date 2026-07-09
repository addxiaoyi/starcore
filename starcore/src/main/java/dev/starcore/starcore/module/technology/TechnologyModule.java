package dev.starcore.starcore.module.technology;

import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.module.ModuleLayer;
import dev.starcore.starcore.core.module.ModuleMetadata;
import dev.starcore.starcore.core.module.StarCoreModule;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.policy.PolicyService;
import dev.starcore.starcore.module.policy.model.PolicyEffectScope;
import dev.starcore.starcore.module.resource.ResourceService;
import dev.starcore.starcore.module.technology.listener.TechnologyEventListener;
import dev.starcore.starcore.module.technology.command.TechnologyCommand;
import dev.starcore.starcore.module.technology.gui.TechnologyGuiListener;
import dev.starcore.starcore.module.technology.model.TechnologyDefinition;
import dev.starcore.starcore.module.technology.model.TechnologyEffect;
import dev.starcore.starcore.module.treasury.TreasuryService;
import dev.starcore.starcore.module.nation.NationService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class TechnologyModule implements StarCoreModule, TechnologyService, Listener {
    private static final String FILE_NAME = "technologies.properties";

    private static final ModuleMetadata METADATA = new ModuleMetadata(
        "technology",
        "科技核心",
        ModuleLayer.MODULE,
        List.of("nation", "resource"),
        List.of(TechnologyService.class),
        "Owns national technology unlocks for strategic progression."
    );

    private final ConcurrentMap<NationId, Set<String>> unlocked = new ConcurrentHashMap<>();
    private TechnologyStateStorage stateStorage;
    private StarCoreContext currentContext;

    // New components for enhanced functionality
    private TechnologyDefinitionLoader definitionLoader;
    private TechnologyEffectRegistry effectRegistry;
    private ResearchScheduler researchScheduler;
    private TechnologyValidator validator;

    // Policy integration
    private PolicyService policyService;

    // GUI
    private TechnologyGuiListener guiListener;

    @Override
    public ModuleMetadata metadata() {
        return METADATA;
    }

    @Override
    public void enable(StarCoreContext context) {
        this.currentContext = context;
        context.persistenceService().ensureNamespace(metadata().id());
        this.stateStorage = new DatabaseAwareTechnologyStateStorage(
            metadata().id(),
            context.databaseService(),
            context.persistenceService(),
            context.plugin().getLogger()
        );

        // Initialize new components
        this.definitionLoader = new TechnologyDefinitionLoader(context.plugin());
        this.effectRegistry = new TechnologyEffectRegistry(context.plugin(), definitionLoader);
        this.researchScheduler = new ResearchScheduler(this, context.plugin(), this::onResearchComplete);
        this.researchScheduler.initializePersistence(context);

        loadState();
        this.researchScheduler.loadState();
        this.validator = new TechnologyValidator(
            this,
            () -> context.serviceRegistry().find(TreasuryService.class).orElse(null),
            () -> context.serviceRegistry().find(ResourceService.class).orElse(null),
            definitionLoader
        );

        // Get policy service if available
        this.policyService = context.serviceRegistry().find(PolicyService.class).orElse(null);

        // Register this module as TechnologyService
        context.serviceRegistry().register(TechnologyService.class, this);

        // Register player event listeners
        context.plugin().getServer().getPluginManager().registerEvents(this, context.plugin());

        // Register technology event listener
        NationService nationService = context.serviceRegistry().find(NationService.class).orElse(null);
        if (nationService != null) {
            context.plugin().getServer().getPluginManager().registerEvents(
                new TechnologyEventListener(this, nationService), context.plugin());

            // Register technology command
            TechnologyCommand techCommand = new TechnologyCommand(this, nationService, context.plugin());

            // Register GUI listener and wire it to command
            TreasuryService treasuryService = context.serviceRegistry().find(TreasuryService.class).orElse(null);
            guiListener = new TechnologyGuiListener(this, nationService, treasuryService, context.plugin());
            context.plugin().getServer().getPluginManager().registerEvents(guiListener, context.plugin());

            // Set GUI instance in command
            techCommand.setTreeGui(guiListener.getTreeGui());

            org.bukkit.command.PluginCommand command = context.plugin().getCommand("tech");
            if (command != null) {
                command.setExecutor(techCommand);
                command.setTabCompleter(techCommand);
            }
        }
    }

    @Override
    public void disable(StarCoreContext context) {
        // Unregister from service registry
        context.serviceRegistry().unregister(TechnologyService.class);

        // Clear all active research
        if (researchScheduler != null) {
            for (NationId nationId : unlocked.keySet()) {
                researchScheduler.clearNationResearch(nationId);
            }
            // Flush research state before shutdown
            researchScheduler.flushState();
        }

        // Remove effects from all online players
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (effectRegistry != null) {
                effectRegistry.removeEffectsFromPlayer(player);
            }
        }

        flushState();
        this.currentContext = null;
    }

    // ========== TechnologyService Implementation ==========

    @Override
    public Collection<String> availableTechnologies() {
        return definitionLoader.getAll().keySet().stream().sorted().toList();
    }

    @Override
    public Optional<TechnologyCost> costOf(String technologyKey) {
        String normalized = normalizeTechnology(technologyKey);
        TechnologyDefinition def = definitionLoader.load(normalized);
        if (def == null) {
            return Optional.empty();
        }
        return Optional.of(new TechnologyCost(def.treasuryCost(), def.resourceCosts()));
    }

    @Override
    public Collection<String> unlockedTechnologies(NationId nationId) {
        return unlocked.getOrDefault(nationId, Set.of()).stream().sorted().toList();
    }

    @Override
    public boolean hasTechnology(NationId nationId, String technologyKey) {
        return unlocked.getOrDefault(nationId, Set.of()).contains(normalizeTechnology(technologyKey));
    }

    @Override
    public boolean unlock(NationId nationId, String technologyKey) {
        String normalized = normalizeTechnology(technologyKey);
        if (definitionLoader.load(normalized) == null) {
            return false;
        }
        // TODO audit B-103: unlock 不在此处扣成本/资源。当前调用路径
        //   startResearchTechnology -> deductCosts -> scheduler.startResearch -> ... -> completeResearch -> unlock
        //   已在 startResearchTechnology 中扣过一次。但 forceComplete -> completeResearch -> unlock
        //   走的 path 未扣额外成本（也确实不应重复扣）。若未来有直接调用 unlock 的入口，需自行保证扣成本。
        //   整合扣成本到 unlock 内部并配套回滚是后续架构改造项。
        // audit B-104: 重复 unlock 仍返回 true，调用方无法区分"已存在"与"新解锁"。
        //   在 JavaDoc 中明确：true 既表示解锁成功也表示"早已存在"。如需区分请用 hasTechnology 预判。
        Set<String> unlockedSet = unlocked.computeIfAbsent(nationId, ignored -> ConcurrentHashMap.newKeySet());
        boolean alreadyUnlocked = unlockedSet.contains(normalized);
        unlockedSet.add(normalized);
        saveState();

        // Apply effects to all online nation members
        applyEffectsToNationMembers(nationId);

        // 返回 true：定义存在且解锁成功/已解锁
        // 调用方可先调用 hasTechnology(nationId, technologyKey) 区分是否新增解锁
        return true;
    }

    @Override
    public boolean revoke(NationId nationId, String technologyKey) {
        String normalized = normalizeTechnology(technologyKey);
        if (definitionLoader.load(normalized) == null) {
            return false;
        }
        Set<String> values = unlocked.get(nationId);
        if (values == null || !values.remove(normalized)) {
            return false;
        }
        saveState();

        // Remove effects from all online nation members
        applyEffectsToNationMembers(nationId);

        return true;
    }

    @Override
    public String summary() {
        long total = unlocked.values().stream().mapToLong(Set::size).sum();
        return unlocked.size() + " nation technology record(s), " + total + " unlock(s)";
    }

    // ========== Enhanced Technology Methods ==========

    /**
     * Gets the technology definition for a key.
     */
    public Optional<TechnologyDefinition> getDefinition(String technologyKey) {
        return Optional.ofNullable(definitionLoader.load(technologyKey));
    }

    /**
     * Gets all technology definitions.
     */
    public Map<String, TechnologyDefinition> getAllDefinitions() {
        return definitionLoader.getAll();
    }

    /**
     * Validates if a technology can be researched.
     */
    public TechnologyValidator.ValidationResult validateResearch(NationId nationId, String technologyKey) {
        return validator.validate(nationId, technologyKey);
    }

    /**
     * Checks if a nation can afford a technology.
     */
    public boolean canAfford(NationId nationId, String technologyKey) {
        return validator.canAfford(nationId, technologyKey);
    }

    /**
     * Gets missing prerequisites for a technology.
     */
    public List<String> getMissingPrerequisites(NationId nationId, String technologyKey) {
        return validator.getMissingPrerequisitesInOrder(nationId, technologyKey);
    }

    /**
     * Calculates the total cost breakdown for a technology.
     */
    public TechnologyValidator.CostBreakdown calculateCost(NationId nationId, String technologyKey) {
        return validator.calculateTotalCost(nationId, technologyKey);
    }

    /**
     * Starts an asynchronous research operation.
     *
     * @return true if research started, false if already researching or invalid
     */
    public boolean startResearch(NationId nationId, String technologyKey, java.util.function.Consumer<ResearchProgress> onProgress) {
        String normalized = normalizeTechnology(technologyKey);

        // Validate first
        TechnologyValidator.ValidationResult result = validator.validate(nationId, normalized);
        if (!result.valid()) {
            return false;
        }

        // Get research time from definition
        TechnologyDefinition definition = definitionLoader.load(normalized);
        if (definition == null) {
            return false;
        }

        int researchTime = definition.researchTimeSeconds();

        // Apply research speed modifier from policy/tech
        researchTime = (int) (researchTime * getResearchSpeedModifier(nationId));

        // Deduct costs
        deductCosts(nationId, normalized);

        // Start the research
        return researchScheduler.startResearch(nationId, normalized, researchTime, onProgress);
    }

    /**
     * Cancels an ongoing research operation.
     */
    public boolean cancelResearch(NationId nationId, String technologyKey) {
        return researchScheduler.cancelResearch(nationId, normalizeTechnology(technologyKey));
    }

    /**
     * Gets the current research progress for a nation's technology.
     */
    public ResearchProgress getResearchProgress(NationId nationId, String technologyKey) {
        return researchScheduler.getProgress(nationId, normalizeTechnology(technologyKey));
    }

    /**
     * Checks if a nation is currently researching a technology.
     */
    public boolean isResearching(NationId nationId, String technologyKey) {
        return researchScheduler.isResearching(nationId, normalizeTechnology(technologyKey));
    }

    /**
     * Gets all ongoing research for a nation.
     */
    public Map<String, ResearchProgress> getNationResearch(NationId nationId) {
        return researchScheduler.getNationResearch(nationId);
    }

    /**
     * Forces completion of research (admin command).
     */
    public boolean forceCompleteResearch(NationId nationId, String technologyKey) {
        return researchScheduler.forceComplete(nationId, normalizeTechnology(technologyKey));
    }

    // ========== Effect Application Methods ==========

    /**
     * Applies all technology effects for a nation to a player.
     */
    public void applyEffectsToPlayer(Player player, NationId nationId) {
        effectRegistry.applyEffectsToPlayer(player, nationId, this);
    }

    /**
     * Removes all technology effects from a player.
     */
    public void removeEffectsFromPlayer(Player player) {
        effectRegistry.removeEffectsFromPlayer(player);
    }

    /**
     * Gets the total modifier for a specific effect type.
     */
    public double getTotalModifier(NationId nationId, String effectType) {
        return effectRegistry.calculateTotalModifier(nationId, effectType, this);
    }

    /**
     * Checks if a nation has a specific unlock.
     */
    public boolean hasUnlock(NationId nationId, String unlockType, String unlockValue) {
        return effectRegistry.hasUnlock(nationId, unlockType, unlockValue, this);
    }

    /**
     * Gets all unlocked features for a nation.
     */
    public Map<String, Double> getUnlockedFeatures(NationId nationId) {
        return effectRegistry.getUnlockedFeatures(nationId, this);
    }

    // ========== Policy Integration ==========

    /**
     * Gets the research speed modifier from policy effects.
     */
    public double getResearchSpeedModifier(NationId nationId) {
        double modifier = 1.0;

        // Apply policy modifiers
        if (policyService != null) {
            double policyBonus = policyService.activePolicyModifier(nationId, "research_speed", PolicyEffectScope.NATION);
            if (policyBonus != 0) {
                modifier *= (1 - policyBonus); // Negative modifier reduces time
            }

            double techBonus = policyService.activePolicyModifier(nationId, "technology_speed", PolicyEffectScope.NATION);
            if (techBonus != 0) {
                modifier *= (1 - techBonus);
            }
        }

        // Apply technology modifiers from already unlocked techs
        for (String techKey : unlockedTechnologies(nationId)) {
            TechnologyDefinition def = definitionLoader.load(techKey);
            if (def != null) {
                for (TechnologyEffect effect : def.effects()) {
                    if ("research_speed".equals(effect.type())) {
                        modifier *= (1 - effect.value());
                    }
                }
            }
        }

        return Math.max(0.1, modifier); // Minimum 10% of base time
    }

    // ========== Player Event Listeners ==========

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Apply technology effects when player joins
        currentContext.serviceRegistry().find(dev.starcore.starcore.module.nation.NationService.class)
            .ifPresent(nationService -> {
                nationService.nationOf(player.getUniqueId()).ifPresent(nation -> {
                    applyEffectsToPlayer(player, nation.id());
                });
            });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Clean up player effects on quit
        removeEffectsFromPlayer(event.getPlayer());
    }

    // ========== Internal Methods ==========

    private void onResearchComplete(NationId nationId, String technologyKey, boolean success, String message) {
        if (success && currentContext != null) {
            currentContext.plugin().getLogger().info("Nation " + nationId + " completed research: " + technologyKey);

            // Notify nation members
            currentContext.serviceRegistry().find(dev.starcore.starcore.module.nation.NationService.class)
                .ifPresent(nationService -> {
                    nationService.nations().stream()
                        .filter(n -> n.id().equals(nationId))
                        .findFirst()
                        .ifPresent(nation -> {
                            nation.members().forEach(member -> {
                                Player player = Bukkit.getPlayer(member.playerId());
                                if (player != null) {
                                    player.sendMessage("§a[科技] §f研究完成: §e" + technologyKey);
                                    // Refresh effects
                                    applyEffectsToPlayer(player, nationId);
                                }
                            });
                        });
                });
        }
    }

    private void applyEffectsToNationMembers(NationId nationId) {
        if (currentContext == null) return;

        currentContext.serviceRegistry().find(dev.starcore.starcore.module.nation.NationService.class)
            .ifPresent(nationService -> {
                nationService.nations().stream()
                    .filter(n -> n.id().equals(nationId))
                    .findFirst()
                    .ifPresent(nation -> {
                        nation.members().forEach(member -> {
                            Player player = Bukkit.getPlayer(member.playerId());
                            if (player != null) {
                                // Remove all effects first
                                effectRegistry.removeEffectsFromPlayer(player);
                                // Reapply all effects
                                effectRegistry.applyEffectsToPlayer(player, nationId, this);
                            }
                        });
                    });
            });
    }

    private void deductCosts(NationId nationId, String technologyKey) {
        // Deduct treasury cost
        TechnologyDefinition definition = definitionLoader.load(technologyKey);
        if (definition != null && definition.treasuryCost().signum() > 0) {
            currentContext.serviceRegistry().find(TreasuryService.class)
                .ifPresent(treasury -> treasury.withdraw(nationId, definition.treasuryCost()));
        }

        // Deduct resource costs
        var costOpt = costOf(technologyKey);
        if (costOpt.isPresent()) {
            currentContext.serviceRegistry().find(ResourceService.class)
                .ifPresent(resources -> {
                    for (Map.Entry<String, Long> entry : costOpt.get().resources().entrySet()) {
                        resources.consume(nationId, entry.getKey(), entry.getValue());
                    }
                });
        }
    }

    private void saveState() {
        if (stateStorage == null) {
            return;
        }
        stateStorage.saveAsync(TechnologyStateCodec.toProperties(unlocked));
    }

    private void flushState() {
        if (stateStorage == null) {
            return;
        }
        stateStorage.save(TechnologyStateCodec.toProperties(unlocked));
    }

    private void loadState() {
        unlocked.clear();
        java.util.Set<String> allowed = java.util.Set.copyOf(availableTechnologies());
        TechnologyStateCodec.fromProperties(stateStorage == null ? new java.util.Properties() : stateStorage.load(), allowed)
            .forEach((nationId, values) -> {
                if (values != null && !values.isEmpty()) {
                    unlocked.put(nationId, java.util.Set.copyOf(values));
                }
            });
    }

    private static String normalizeTechnology(String technologyKey) {
        return technologyKey == null ? "" : technologyKey.trim().toLowerCase(Locale.ROOT);
    }
}
