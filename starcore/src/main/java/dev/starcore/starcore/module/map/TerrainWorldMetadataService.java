package dev.starcore.starcore.module.map;

import org.bukkit.Location;
import org.bukkit.World;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

final class TerrainWorldMetadataService {
    private static final int FALLBACK_RADIUS_BLOCKS = 256;
    private static final int REGION_SIZE_BLOCKS = 512;

    private final Function<String, World> worldResolver;
    private final Consumer<World> worldRememberer;

    TerrainWorldMetadataService(Function<String, World> worldResolver, Consumer<World> worldRememberer) {
        this.worldResolver = worldResolver == null ? ignored -> null : worldResolver;
        this.worldRememberer = worldRememberer == null ? ignored -> {
        } : worldRememberer;
    }

    String terrainJson(Set<String> worldNames, int tilePixels, long revision) {
        StringBuilder builder = new StringBuilder(256);
        builder.append("\"terrain\":{");
        builder.append("\"tileSize\":").append(Math.clamp(tilePixels, 64, 512)).append(',');
        builder.append("\"revision\":").append(revision).append(',');
        builder.append("\"worlds\":{");
        int index = 0;
        for (String worldName : worldNames == null ? Set.<String>of() : worldNames) {
            World world = worldResolver.apply(worldName);
            if (world == null) {
                continue;
            }
            if (index++ > 0) {
                builder.append(',');
            }
            appendStringValue(builder, worldName);
            builder.append(':');
            appendWorldMetadata(builder, world);
        }
        builder.append('}');
        builder.append('}');
        return builder.toString();
    }

    private void appendWorldMetadata(StringBuilder builder, World world) {
        worldRememberer.accept(world);
        Location spawn = world.getSpawnLocation();
        int spawnX = spawn.getBlockX();
        int spawnZ = spawn.getBlockZ();
        RegionBounds bounds = generatedRegionBounds(world);
        builder.append('{');
        appendNumberField(builder, "spawnX", spawnX);
        builder.append(',');
        appendNumberField(builder, "spawnZ", spawnZ);
        builder.append(',');
        appendNumberField(builder, "seaLevel", world.getSeaLevel());
        builder.append(',');
        if (bounds == null) {
            appendBooleanField(builder, "generated", false);
            builder.append(',');
            appendNumberField(builder, "minX", spawnX - FALLBACK_RADIUS_BLOCKS);
            builder.append(',');
            appendNumberField(builder, "minZ", spawnZ - FALLBACK_RADIUS_BLOCKS);
            builder.append(',');
            appendNumberField(builder, "maxX", spawnX + FALLBACK_RADIUS_BLOCKS);
            builder.append(',');
            appendNumberField(builder, "maxZ", spawnZ + FALLBACK_RADIUS_BLOCKS);
        } else {
            appendBooleanField(builder, "generated", true);
            builder.append(',');
            appendNumberField(builder, "minX", bounds.minX());
            builder.append(',');
            appendNumberField(builder, "minZ", bounds.minZ());
            builder.append(',');
            appendNumberField(builder, "maxX", bounds.maxX());
            builder.append(',');
            appendNumberField(builder, "maxZ", bounds.maxZ());
        }
        builder.append('}');
    }

    RegionBounds generatedRegionBounds(World world) {
        Path regionDirectory = world.getWorldFolder().toPath().resolve("region");
        if (Files.notExists(regionDirectory)) {
            return null;
        }
        int minRegionX = Integer.MAX_VALUE;
        int minRegionZ = Integer.MAX_VALUE;
        int maxRegionX = Integer.MIN_VALUE;
        int maxRegionZ = Integer.MIN_VALUE;
        try (Stream<Path> files = Files.list(regionDirectory)) {
            for (Path file : files.toList()) {
                String name = file.getFileName().toString();
                if (!name.startsWith("r.") || !name.endsWith(".mca")) {
                    continue;
                }
                String[] parts = name.substring(2, name.length() - 4).split("\\.");
                if (parts.length != 2) {
                    continue;
                }
                try {
                    int regionX = Integer.parseInt(parts[0]);
                    int regionZ = Integer.parseInt(parts[1]);
                    minRegionX = Math.min(minRegionX, regionX);
                    minRegionZ = Math.min(minRegionZ, regionZ);
                    maxRegionX = Math.max(maxRegionX, regionX);
                    maxRegionZ = Math.max(maxRegionZ, regionZ);
                } catch (NumberFormatException ignored) {
                }
            }
        } catch (IOException exception) {
            return null;
        }
        if (minRegionX == Integer.MAX_VALUE) {
            return null;
        }
        return new RegionBounds(
            minRegionX * REGION_SIZE_BLOCKS,
            minRegionZ * REGION_SIZE_BLOCKS,
            (maxRegionX + 1) * REGION_SIZE_BLOCKS,
            (maxRegionZ + 1) * REGION_SIZE_BLOCKS
        );
    }

    private static void appendNumberField(StringBuilder builder, String name, int value) {
        appendStringValue(builder, name);
        builder.append(':').append(value);
    }

    private static void appendBooleanField(StringBuilder builder, String name, boolean value) {
        appendStringValue(builder, name);
        builder.append(':').append(value);
    }

    private static void appendStringValue(StringBuilder builder, String value) {
        builder.append('"');
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '\\' -> builder.append("\\\\");
                case '"' -> builder.append("\\\"");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> builder.append(ch);
            }
        }
        builder.append('"');
    }

    record RegionBounds(int minX, int minZ, int maxX, int maxZ) {
    }
}
