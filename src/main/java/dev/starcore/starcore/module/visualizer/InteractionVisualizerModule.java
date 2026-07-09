package dev.starcore.starcore.module.visualizer;

import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.module.ModuleLayer;
import dev.starcore.starcore.core.module.ModuleMetadata;
import dev.starcore.starcore.core.module.StarCoreModule;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Optional;

public final class InteractionVisualizerModule implements StarCoreModule, VisualizerService {
    private static final ModuleMetadata METADATA = new ModuleMetadata(
        "interaction_visualizer",
        "交互可视化",
        ModuleLayer.FEATURE,
        List.of(),
        List.of(VisualizerService.class),
        "Native clean-room InteractionVisualizer-compatible display module."
    );

    private Plugin plugin;
    private VisualizerConfig config;
    private VisualizerPreferenceStore preferences;
    private VisualizerRenderer renderer;
    private VisualizerDisplayManager displays;
    private VisualizerListener listener;
    private BukkitTask scanTask;
    private BukkitTask cleanupTask;

    @Override
    public ModuleMetadata metadata() {
        return METADATA;
    }

    @Override
    public void enable(StarCoreContext context) {
        this.plugin = context.plugin();
        VisualizerConfig.ensureDefaults(plugin);
        this.config = VisualizerConfig.load(plugin);
        this.preferences = new VisualizerPreferenceStore(plugin);
        this.preferences.load(config.defaultDisableAll());
        this.renderer = new NativeVisualizerRenderer();
        this.displays = new VisualizerDisplayManager(plugin);
        this.listener = new VisualizerListener(this);
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);

        registerCommand();
        startTasks();
        plugin.getLogger().info("STARCORE Interaction Visualizer enabled: " + config.summary());
    }

    @Override
    public void disable(StarCoreContext context) {
        stopTasks();
        if (listener != null) {
            HandlerList.unregisterAll(listener);
        }
        if (preferences != null) {
            preferences.save();
        }
        if (displays != null) {
            displays.removeAll();
        }
        plugin.getLogger().info("STARCORE Interaction Visualizer disabled.");
    }

    @Override
    public void reload() {
        plugin.reloadConfig();
        this.config = VisualizerConfig.load(plugin);
        this.preferences.load(config.defaultDisableAll());
        stopTasks();
        startTasks();
        cleanup();
    }

    @Override
    public void cleanup() {
        if (displays != null) {
            displays.removeAll();
        }
    }

    @Override
    public String summary() {
        int active = displays == null ? 0 : displays.activeDisplayCount();
        String configSummary = config == null ? "not loaded" : config.summary();
        return "Interaction Visualizer: " + configSummary + ", active displays " + active;
    }

    @Override
    public boolean setMode(Player player, VisualizerDisplayMode mode, boolean enabled) {
        if (player == null || mode == null || preferences == null) {
            return false;
        }
        preferences.preferences(player.getUniqueId()).setMode(mode, enabled);
        preferences.save();
        return true;
    }

    @Override
    public boolean setEntry(Player player, VisualizerEntry entry, boolean enabled) {
        if (player == null || entry == null || preferences == null) {
            return false;
        }
        preferences.preferences(player.getUniqueId()).setEntry(entry, enabled);
        preferences.save();
        return true;
    }

    void renderNearby(Player player) {
        if (player == null || !player.isOnline() || config == null || !config.enabled() || displays == null) {
            return;
        }
        World world = player.getWorld();
        if (!config.worldEnabled(world.getName())) {
            return;
        }
        VisualizerPreferences playerPrefs = preferences.preferences(player.getUniqueId());
        int rendered = 0;
        rendered += renderBlocks(player, playerPrefs, config.maxDisplaysPerPlayer());
        if (rendered < config.maxDisplaysPerPlayer()) {
            renderEntities(player, playerPrefs, config.maxDisplaysPerPlayer() - rendered);
        }
    }

    void renderBlockFor(Player player, Block block) {
        if (player == null || block == null || config == null || !config.enabled()) {
            return;
        }
        VisualizerEntry.fromBlock(block).ifPresent(entry -> renderBlock(player, preferences.preferences(player.getUniqueId()), block, entry));
    }

    void renderBlockNextTick(Player player, Block block) {
        if (plugin == null || player == null || block == null) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> renderBlockFor(player, block), 1L);
    }

    void removeBlock(Block block) {
        if (displays != null && block != null) {
            displays.remove(VisualizerBlockKey.block(block));
        }
    }

    private int renderBlocks(Player player, VisualizerPreferences playerPrefs, int max) {
        Location origin = player.getLocation();
        Block center = origin.getBlock();
        int rendered = 0;
        int radius = config.scanRadius();
        int vertical = config.scanVerticalRadius();
        for (int y = -vertical; y <= vertical && rendered < max; y++) {
            for (int x = -radius; x <= radius && rendered < max; x++) {
                for (int z = -radius; z <= radius && rendered < max; z++) {
                    Block block = center.getRelative(x, y, z);
                    Optional<VisualizerEntry> entry = VisualizerEntry.fromBlock(block);
                    if (entry.isEmpty()) {
                        continue;
                    }
                    if (renderBlock(player, playerPrefs, block, entry.get())) {
                        rendered++;
                    }
                }
            }
        }
        return rendered;
    }

    private boolean renderBlock(Player player, VisualizerPreferences playerPrefs, Block block, VisualizerEntry entry) {
        if (!canRenderEntry(playerPrefs, entry)) {
            return false;
        }
        boolean renderHologram = config.modeEnabled(VisualizerDisplayMode.HOLOGRAM) && playerPrefs.modeEnabled(VisualizerDisplayMode.HOLOGRAM);
        boolean renderItemStand = config.modeEnabled(VisualizerDisplayMode.ITEM_STAND) && playerPrefs.modeEnabled(VisualizerDisplayMode.ITEM_STAND);
        if (!renderHologram && !renderItemStand) {
            return false;
        }
        return renderer.renderBlock(block, entry, player, config)
            .map(snapshot -> {
                displays.render(VisualizerBlockKey.block(block), block.getLocation(), snapshot, config, renderHologram, renderItemStand);
                return true;
            })
            .orElse(false);
    }

    private int renderEntities(Player player, VisualizerPreferences playerPrefs, int max) {
        boolean hologram = config.modeEnabled(VisualizerDisplayMode.HOLOGRAM) && playerPrefs.modeEnabled(VisualizerDisplayMode.HOLOGRAM);
        boolean itemDrop = config.modeEnabled(VisualizerDisplayMode.ITEM_DROP) && playerPrefs.modeEnabled(VisualizerDisplayMode.ITEM_DROP);
        if (!hologram && !itemDrop) {
            return 0;
        }
        int rendered = 0;
        for (Entity entity : player.getWorld().getNearbyEntities(player.getLocation(), config.scanRadius(), config.scanVerticalRadius(), config.scanRadius())) {
            if (rendered >= max) {
                break;
            }
            VisualizerEntry entry = null;
            if (entity instanceof Item) {
                entry = VisualizerEntry.ITEM;
            } else if (entity instanceof Villager) {
                entry = VisualizerEntry.VILLAGER;
            }
            if (entry == null || !canRenderEntry(playerPrefs, entry)) {
                continue;
            }
            VisualizerEntry finalEntry = entry;
            boolean didRender = renderer.renderEntity(entity, finalEntry, player, config)
                .map(snapshot -> {
                    Location location = entity.getLocation().clone().add(-0.5D, 0.0D, -0.5D);
                    displays.render(VisualizerBlockKey.entity(entity), location, snapshot, config, hologram, itemDrop);
                    return true;
                })
                .orElse(false);
            if (didRender) {
                rendered++;
            }
        }
        return rendered;
    }

    private boolean canRenderEntry(VisualizerPreferences playerPrefs, VisualizerEntry entry) {
        return config.entryEnabled(entry) && playerPrefs.entryEnabled(entry);
    }

    private void startTasks() {
        if (config == null || !config.enabled()) {
            return;
        }
        this.scanTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            displays.nextTick();
            for (Player player : Bukkit.getOnlinePlayers()) {
                renderNearby(player);
            }
        }, 20L, config.scanPeriodTicks());
        this.cleanupTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> displays.cleanupStale(3), config.cleanupPeriodTicks(), config.cleanupPeriodTicks());
    }

    private void stopTasks() {
        if (scanTask != null) {
            scanTask.cancel();
            scanTask = null;
        }
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }
    }

    private void registerCommand() {
        var command = plugin.getServer().getPluginCommand("interactionvisualizer");
        if (command == null) {
            plugin.getLogger().warning("Command 'interactionvisualizer' not found in plugin.yml");
            return;
        }
        VisualizerCommand executor = new VisualizerCommand(this);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }
}
