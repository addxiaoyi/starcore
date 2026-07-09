package dev.starcore.starcore.module.visualizer;

import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.Optional;

interface VisualizerRenderer {
    Optional<VisualizerRenderSnapshot> renderBlock(Block block, VisualizerEntry entry, Player viewer, VisualizerConfig config);

    Optional<VisualizerRenderSnapshot> renderEntity(Entity entity, VisualizerEntry entry, Player viewer, VisualizerConfig config);
}

