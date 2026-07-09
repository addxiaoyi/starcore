package dev.starcore.starcore.module.nation.resource;

import org.bukkit.Material;

import java.util.Locale;

record ResourceBlockLocation(String world, int x, int y, int z, Material material) {
    String key() {
        return world + ':' + x + ':' + y + ':' + z;
    }

    String serialize() {
        return key() + ':' + material.name().toLowerCase(Locale.ROOT);
    }

    static ResourceBlockLocation parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] parts = raw.split(":");
        if (parts.length != 5) {
            return null;
        }
        try {
            Material material = Material.matchMaterial(parts[4].trim().toUpperCase(Locale.ROOT));
            if (material == null) {
                return null;
            }
            return new ResourceBlockLocation(
                parts[0],
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2]),
                Integer.parseInt(parts[3]),
                material
            );
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
