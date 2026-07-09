package dev.starcore.starcore.module.visualizer;

import org.bukkit.inventory.ItemStack;

import java.util.List;

record VisualizerRenderSnapshot(
    VisualizerEntry entry,
    List<String> lines,
    List<ItemStack> items,
    String contentHash
) {
}

