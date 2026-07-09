package dev.starcore.starcore.module.visualizer;

import org.bukkit.entity.Player;

public interface VisualizerService {
    void reload();

    void cleanup();

    String summary();

    boolean setMode(Player player, VisualizerDisplayMode mode, boolean enabled);

    boolean setEntry(Player player, VisualizerEntry entry, boolean enabled);
}

