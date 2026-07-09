package dev.starcore.starcore.module.map;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;

final class TerrainTileDiskCache {
    private static final String VERSION = "v5";

    private final Path dataFolder;
    private final Path root;
    private final Map<String, Path> worldRegionDirectories = new ConcurrentHashMap<>();

    TerrainTileDiskCache(Path dataFolder) {
        this.dataFolder = dataFolder == null ? Path.of(".").toAbsolutePath().normalize() : dataFolder.toAbsolutePath().normalize();
        this.root = this.dataFolder.resolve("cache").resolve("terrain").resolve(VERSION).normalize();
    }

    byte[] read(TerrainTileKey key, long nowMillis, long ttlMillis, BiPredicate<TerrainTileKey, Long> dirtyAfter) {
        return read(key, 256, nowMillis, ttlMillis, dirtyAfter);
    }

    byte[] read(TerrainTileKey key, int tilePixels, long nowMillis, long ttlMillis, BiPredicate<TerrainTileKey, Long> dirtyAfter) {
        Path cacheFile = cacheFile(key, tilePixels);
        try {
            if (Files.notExists(cacheFile)) {
                return null;
            }
            long modifiedAt = Files.getLastModifiedTime(cacheFile).toMillis();
            if (nowMillis - modifiedAt >= ttlMillis) {
                Files.deleteIfExists(cacheFile);
                return null;
            }
            if (dirtyAfter != null && dirtyAfter.test(key, modifiedAt)) {
                Files.deleteIfExists(cacheFile);
                return null;
            }
            if (sourceModifiedAfter(key, modifiedAt)) {
                Files.deleteIfExists(cacheFile);
                return null;
            }
            byte[] bytes = Files.readAllBytes(cacheFile);
            if (bytes.length == 0) {
                Files.deleteIfExists(cacheFile);
                return null;
            }
            return bytes;
        } catch (IOException exception) {
            return null;
        }
    }

    void write(TerrainTileKey key, byte[] bytes, long renderedAtMillis) {
        write(key, 256, bytes, renderedAtMillis);
    }

    void write(TerrainTileKey key, int tilePixels, byte[] bytes, long renderedAtMillis) {
        if (bytes == null || bytes.length == 0) {
            return;
        }
        Path cacheFile = cacheFile(key, tilePixels);
        try {
            Path parent = cacheFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(cacheFile, bytes);
            Files.setLastModifiedTime(cacheFile, FileTime.fromMillis(renderedAtMillis));
        } catch (IOException ignored) {
        }
    }

    void rememberWorld(String worldName, Path worldFolder) {
        if (worldName == null || worldName.isBlank() || worldFolder == null) {
            return;
        }
        worldRegionDirectories.put(worldName, worldFolder.toAbsolutePath().normalize().resolve("region"));
    }

    void clearRememberedWorlds() {
        worldRegionDirectories.clear();
    }

    boolean regionFileExists(String worldName, int chunkX, int chunkZ) {
        int regionX = Math.floorDiv(chunkX, 32);
        int regionZ = Math.floorDiv(chunkZ, 32);
        return Files.isRegularFile(regionDirectory(worldName).resolve("r." + regionX + '.' + regionZ + ".mca"));
    }

    boolean sourceModifiedAfter(TerrainTileKey key, long cacheModifiedAtMillis) {
        return sourceModifiedAtMillis(key) > cacheModifiedAtMillis;
    }

    Path cacheFile(TerrainTileKey key) {
        return cacheFile(key, 256);
    }

    Path cacheFile(TerrainTileKey key, int tilePixels) {
        String world = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString((key == null || key.world() == null ? "" : key.world()).getBytes(StandardCharsets.UTF_8));
        String tilePixelsPath = String.valueOf(Math.max(1, tilePixels));
        Path cacheFile = root
            .resolve(world)
            .resolve(String.valueOf(key == null ? 0 : key.worldSize()))
            .resolve(tilePixelsPath)
            .resolve((key == null ? 0 : key.minX()) + "_" + (key == null ? 0 : key.minZ()) + ".png")
            .normalize();
        return cacheFile.startsWith(root) ? cacheFile : root.resolve("invalid.png");
    }

    Path root() {
        return root;
    }

    Path regionDirectory(String worldName) {
        Path remembered = worldRegionDirectories.get(worldName);
        if (remembered != null) {
            return remembered;
        }
        Path pluginsDirectory = dataFolder.getParent();
        Path serverDirectory = pluginsDirectory == null ? null : pluginsDirectory.getParent();
        if (serverDirectory == null) {
            return dataFolder.resolve(worldName == null ? "" : worldName).resolve("region");
        }
        return serverDirectory.resolve(worldName == null ? "" : worldName).resolve("region");
    }

    private long sourceModifiedAtMillis(TerrainTileKey key) {
        Path regionDirectory = regionDirectory(key == null ? "" : key.world());
        if (Files.notExists(regionDirectory)) {
            return 0L;
        }

        int minRegionX = Math.floorDiv(key.minX(), 512);
        int maxRegionX = Math.floorDiv(key.minX() + key.worldSize() - 1, 512);
        int minRegionZ = Math.floorDiv(key.minZ(), 512);
        int maxRegionZ = Math.floorDiv(key.minZ() + key.worldSize() - 1, 512);
        long modifiedAtMillis = 0L;
        for (int regionX = minRegionX; regionX <= maxRegionX; regionX++) {
            for (int regionZ = minRegionZ; regionZ <= maxRegionZ; regionZ++) {
                Path regionFile = regionDirectory.resolve("r." + regionX + '.' + regionZ + ".mca");
                try {
                    if (Files.isRegularFile(regionFile)) {
                        modifiedAtMillis = Math.max(modifiedAtMillis, Files.getLastModifiedTime(regionFile).toMillis());
                    }
                } catch (IOException ignored) {
                }
            }
        }
        return modifiedAtMillis;
    }
}
