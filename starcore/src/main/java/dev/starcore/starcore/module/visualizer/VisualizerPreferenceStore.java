package dev.starcore.starcore.module.visualizer;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class VisualizerPreferenceStore {
    private final Plugin plugin;
    private final Map<UUID, VisualizerPreferences> preferences = new ConcurrentHashMap<>();
    private File file;
    private YamlConfiguration yaml;
    private boolean defaultDisableAll;

    public VisualizerPreferenceStore(Plugin plugin) {
        this.plugin = plugin;
    }

    public void load(boolean defaultDisableAll) {
        this.defaultDisableAll = defaultDisableAll;
        this.file = new File(plugin.getDataFolder(), "interaction-visualizer-preferences.yml");
        this.yaml = YamlConfiguration.loadConfiguration(file);
        preferences.clear();
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players == null) {
            return;
        }
        for (String key : players.getKeys(false)) {
            try {
                UUID playerId = UUID.fromString(key);
                VisualizerPreferences prefs = new VisualizerPreferences(defaultDisableAll);
                ConfigurationSection section = players.getConfigurationSection(key);
                if (section == null) {
                    continue;
                }
                ConfigurationSection modes = section.getConfigurationSection("modes");
                if (modes != null) {
                    for (String modeKey : modes.getKeys(false)) {
                        VisualizerDisplayMode.from(modeKey).ifPresent(mode -> prefs.setMode(mode, modes.getBoolean(modeKey)));
                    }
                }
                for (String entryKey : section.getStringList("disabled-entries")) {
                    VisualizerEntry.fromKey(entryKey).ifPresent(entry -> prefs.setEntry(entry, false));
                }
                for (String entryKey : section.getStringList("enabled-entries")) {
                    VisualizerEntry.fromKey(entryKey).ifPresent(entry -> prefs.setEntry(entry, true));
                }
                preferences.put(playerId, prefs);
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Ignoring invalid interaction visualizer preference key: " + key);
            }
        }
    }

    public VisualizerPreferences preferences(UUID playerId) {
        return preferences.computeIfAbsent(playerId, ignored -> new VisualizerPreferences(defaultDisableAll));
    }

    public void save() {
        if (yaml == null || file == null) {
            return;
        }
        yaml.set("players", null);
        for (Map.Entry<UUID, VisualizerPreferences> entry : preferences.entrySet()) {
            String path = "players." + entry.getKey();
            VisualizerPreferences prefs = entry.getValue();
            for (Map.Entry<VisualizerDisplayMode, Boolean> mode : prefs.modes().entrySet()) {
                yaml.set(path + ".modes." + mode.getKey().key(), mode.getValue());
            }
            yaml.set(path + ".disabled-entries", prefs.disabledEntries().stream().map(VisualizerEntry::key).toList());
            yaml.set(path + ".enabled-entries", prefs.enabledEntries().stream().map(VisualizerEntry::key).toList());
        }
        try {
            yaml.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("Failed to save interaction visualizer preferences: " + exception.getMessage());
        }
    }
}
