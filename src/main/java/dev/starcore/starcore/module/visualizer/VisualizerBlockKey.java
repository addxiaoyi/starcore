package dev.starcore.starcore.module.visualizer;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;

record VisualizerBlockKey(String type, String world, int x, int y, int z, String id) {
    static VisualizerBlockKey block(Block block) {
        return new VisualizerBlockKey(
            "block",
            block.getWorld().getName(),
            block.getX(),
            block.getY(),
            block.getZ(),
            block.getWorld().getName() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ()
        );
    }

    static VisualizerBlockKey entity(Entity entity) {
        Location location = entity.getLocation();
        return new VisualizerBlockKey(
            "entity",
            entity.getWorld().getName(),
            location.getBlockX(),
            location.getBlockY(),
            location.getBlockZ(),
            entity.getUniqueId().toString()
        );
    }

    String stableKey() {
        return type + ":" + id;
    }
}

