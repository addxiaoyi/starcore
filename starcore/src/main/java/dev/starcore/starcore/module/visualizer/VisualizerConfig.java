package dev.starcore.starcore.module.visualizer;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class VisualizerConfig {
    private final boolean enabled;
    private final int scanRadius;
    private final int scanVerticalRadius;
    private final int scanPeriodTicks;
    private final int cleanupPeriodTicks;
    private final int renderDistance;
    private final int maxDisplaysPerPlayer;
    private final int maxDisplaysGlobal;
    private final boolean defaultDisableAll;
    private final boolean hideIfViewObstructed;
    private final Set<String> disabledWorlds;
    private final EnumMap<VisualizerDisplayMode, Boolean> modes;
    private final EnumMap<VisualizerEntry, Boolean> entries;

    private VisualizerConfig(
        boolean enabled,
        int scanRadius,
        int scanVerticalRadius,
        int scanPeriodTicks,
        int cleanupPeriodTicks,
        int renderDistance,
        int maxDisplaysPerPlayer,
        int maxDisplaysGlobal,
        boolean defaultDisableAll,
        boolean hideIfViewObstructed,
        Set<String> disabledWorlds,
        EnumMap<VisualizerDisplayMode, Boolean> modes,
        EnumMap<VisualizerEntry, Boolean> entries
    ) {
        this.enabled = enabled;
        this.scanRadius = scanRadius;
        this.scanVerticalRadius = scanVerticalRadius;
        this.scanPeriodTicks = scanPeriodTicks;
        this.cleanupPeriodTicks = cleanupPeriodTicks;
        this.renderDistance = renderDistance;
        this.maxDisplaysPerPlayer = maxDisplaysPerPlayer;
        this.maxDisplaysGlobal = maxDisplaysGlobal;
        this.defaultDisableAll = defaultDisableAll;
        this.hideIfViewObstructed = hideIfViewObstructed;
        this.disabledWorlds = Set.copyOf(disabledWorlds);
        this.modes = new EnumMap<>(modes);
        this.entries = new EnumMap<>(entries);
    }

    public static VisualizerConfig load(Plugin plugin) {
        FileConfiguration config = plugin.getConfig();
        String root = "interaction-visualizer";
        EnumMap<VisualizerDisplayMode, Boolean> modes = new EnumMap<>(VisualizerDisplayMode.class);
        for (VisualizerDisplayMode mode : VisualizerDisplayMode.values()) {
            modes.put(mode, config.getBoolean(root + ".modules." + mode.key() + ".enabled", true));
        }
        EnumMap<VisualizerEntry, Boolean> entries = new EnumMap<>(VisualizerEntry.class);
        for (VisualizerEntry entry : VisualizerEntry.values()) {
            entries.put(entry, config.getBoolean(root + ".entries." + entry.key() + ".enabled", true));
        }

        Set<String> disabledWorlds = new HashSet<>();
        for (String world : config.getStringList(root + ".settings.disabled-worlds")) {
            if (world != null && !world.isBlank()) {
                disabledWorlds.add(world.trim().toLowerCase(Locale.ROOT));
            }
        }

        return new VisualizerConfig(
            config.getBoolean(root + ".enabled", true),
            Math.clamp(config.getInt(root + ".scan.radius", 8), 1, 32),
            Math.clamp(config.getInt(root + ".scan.vertical-radius", 5), 1, 16),
            Math.clamp(config.getInt(root + ".scan.period-ticks", 20), 5, 20 * 60),
            Math.clamp(config.getInt(root + ".cleanup.period-ticks", 600), 100, 20 * 60 * 10),
            Math.clamp(config.getInt(root + ".display.render-distance", 48), 8, 128),
            Math.clamp(config.getInt(root + ".display.max-displays-per-player", 48), 4, 256),
            Math.clamp(config.getInt(root + ".display.max-displays-global", 512), 32, 4096),
            config.getBoolean(root + ".settings.default-disable-all", false),
            config.getBoolean(root + ".settings.hide-if-view-obstructed", false),
            disabledWorlds,
            modes,
            entries
        );
    }

    public static void ensureDefaults(Plugin plugin) {
        FileConfiguration config = plugin.getConfig();
        setDefault(config, "modules.interaction_visualizer", true);
        String root = "interaction-visualizer";
        setDefault(config, root + ".enabled", true);
        setDefault(config, root + ".scan.radius", 8);
        setDefault(config, root + ".scan.vertical-radius", 5);
        setDefault(config, root + ".scan.period-ticks", 20);
        setDefault(config, root + ".cleanup.period-ticks", 600);
        setDefault(config, root + ".display.render-distance", 48);
        setDefault(config, root + ".display.max-displays-per-player", 48);
        setDefault(config, root + ".display.max-displays-global", 512);
        setDefault(config, root + ".settings.default-disable-all", false);
        setDefault(config, root + ".settings.hide-if-view-obstructed", false);
        setDefault(config, root + ".settings.disabled-worlds", java.util.List.of());
        for (VisualizerDisplayMode mode : VisualizerDisplayMode.values()) {
            setDefault(config, root + ".modules." + mode.key() + ".enabled", true);
        }
        for (VisualizerEntry entry : VisualizerEntry.values()) {
            setDefault(config, root + ".entries." + entry.key() + ".enabled", true);
            setDefault(config, root + ".entries." + entry.key() + ".display-name", entry.displayName());
        }
        plugin.saveConfig();
    }

    private static void setDefault(FileConfiguration config, String path, Object value) {
        if (!config.contains(path)) {
            config.set(path, value);
        }
    }

    public boolean enabled() {
        return enabled;
    }

    public int scanRadius() {
        return scanRadius;
    }

    public int scanVerticalRadius() {
        return scanVerticalRadius;
    }

    public int scanPeriodTicks() {
        return scanPeriodTicks;
    }

    public int cleanupPeriodTicks() {
        return cleanupPeriodTicks;
    }

    public int renderDistance() {
        return renderDistance;
    }

    public int maxDisplaysPerPlayer() {
        return maxDisplaysPerPlayer;
    }

    public int maxDisplaysGlobal() {
        return maxDisplaysGlobal;
    }

    public boolean defaultDisableAll() {
        return defaultDisableAll;
    }

    public boolean hideIfViewObstructed() {
        return hideIfViewObstructed;
    }

    public boolean worldEnabled(String worldName) {
        return worldName != null && !disabledWorlds.contains(worldName.toLowerCase(Locale.ROOT));
    }

    public boolean modeEnabled(VisualizerDisplayMode mode) {
        return modes.getOrDefault(mode, true);
    }

    public boolean entryEnabled(VisualizerEntry entry) {
        return entries.getOrDefault(entry, true);
    }

    public String summary() {
        long enabledEntries = entries.values().stream().filter(Boolean::booleanValue).count();
        return enabledEntries + " entry/entries enabled, scan radius " + scanRadius + ", period " + scanPeriodTicks + " ticks";
    }
}

