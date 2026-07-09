package dev.starcore.starcore.module.map;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiPredicate;

final class TerrainTileInvalidationService {
    private final List<Integer> dirtyTileSizes;
    private final int maxDirtyEntries;
    private final Map<TerrainTileKey, Long> dirtyAtMillis = new ConcurrentHashMap<>();
    private final AtomicLong revision = new AtomicLong(System.currentTimeMillis());
    private int revisionBroadcastTaskId = -1;

    TerrainTileInvalidationService(List<Integer> dirtyTileSizes, int maxDirtyEntries) {
        this.dirtyTileSizes = sanitizeTileSizes(dirtyTileSizes);
        this.maxDirtyEntries = Math.max(0, maxDirtyEntries);
    }

    long revision() {
        return revision.get();
    }

    List<TerrainTileKey> markDirty(String worldName, int blockX, int blockZ, boolean includeNeighbors, long dirtyAt, long dirtyTtlMillis) {
        if (worldName == null || worldName.isBlank() || dirtyTileSizes.isEmpty()) {
            return List.of();
        }
        bumpRevision(dirtyAt);
        int minX = includeNeighbors ? blockX - 1 : blockX;
        int maxX = includeNeighbors ? blockX + 1 : blockX;
        int minZ = includeNeighbors ? blockZ - 1 : blockZ;
        int maxZ = includeNeighbors ? blockZ + 1 : blockZ;
        Set<TerrainTileKey> dirtyKeys = new LinkedHashSet<>();

        for (int tileSize : dirtyTileSizes) {
            int startX = alignTerrainTile(minX, tileSize);
            int endX = alignTerrainTile(maxX, tileSize);
            int startZ = alignTerrainTile(minZ, tileSize);
            int endZ = alignTerrainTile(maxZ, tileSize);
            for (int x = startX; x <= endX; x += tileSize) {
                for (int z = startZ; z <= endZ; z += tileSize) {
                    TerrainTileKey key = new TerrainTileKey(worldName, x, z, tileSize);
                    dirtyAtMillis.merge(key, dirtyAt, Math::max);
                    dirtyKeys.add(key);
                }
            }
        }
        pruneDirtyAt(dirtyAt, dirtyTtlMillis);
        return List.copyOf(dirtyKeys);
    }

    boolean invalidatedAfter(TerrainTileKey key, long cacheModifiedAtMillis, BiPredicate<TerrainTileKey, Long> sourceModifiedAfter) {
        return dirtyAfter(key, cacheModifiedAtMillis)
            || (sourceModifiedAfter != null && sourceModifiedAfter.test(key, cacheModifiedAtMillis));
    }

    boolean dirtyAfter(TerrainTileKey key, long cacheModifiedAtMillis) {
        return dirtyAtMillis.getOrDefault(key, 0L) > cacheModifiedAtMillis;
    }

    void clearDirtyBefore(TerrainTileKey key, long renderedAtMillis) {
        dirtyAtMillis.computeIfPresent(key, (ignored, dirtyAt) -> dirtyAt <= renderedAtMillis ? null : dirtyAt);
    }

    void scheduleRevisionBroadcast(JavaPlugin plugin, boolean mapWebEnabled, Runnable broadcast) {
        if (!mapWebEnabled || plugin == null || broadcast == null) {
            return;
        }
        synchronized (this) {
            if (revisionBroadcastTaskId != -1) {
                return;
            }
            revisionBroadcastTaskId = Bukkit.getScheduler().scheduleSyncDelayedTask(
                plugin,
                () -> {
                    synchronized (TerrainTileInvalidationService.this) {
                        revisionBroadcastTaskId = -1;
                    }
                    broadcast.run();
                },
                1L
            );
        }
    }

    void stopRevisionBroadcast() {
        synchronized (this) {
            if (revisionBroadcastTaskId != -1) {
                Bukkit.getScheduler().cancelTask(revisionBroadcastTaskId);
                revisionBroadcastTaskId = -1;
            }
        }
    }

    boolean revisionBroadcastScheduled() {
        synchronized (this) {
            return revisionBroadcastTaskId != -1;
        }
    }

    int dirtySize() {
        return dirtyAtMillis.size();
    }

    void clear() {
        dirtyAtMillis.clear();
        stopRevisionBroadcast();
    }

    private void bumpRevision(long dirtyAt) {
        revision.updateAndGet(current -> Math.max(current + 1, dirtyAt));
    }

    private void pruneDirtyAt(long nowMillis, long dirtyTtlMillis) {
        if (dirtyAtMillis.size() <= maxDirtyEntries) {
            return;
        }
        if (dirtyTtlMillis > 0L) {
            long oldestUsefulDirtyAt = nowMillis - dirtyTtlMillis;
            dirtyAtMillis.entrySet().removeIf(entry -> entry.getValue() < oldestUsefulDirtyAt);
        }
        int excess = dirtyAtMillis.size() - maxDirtyEntries;
        if (excess <= 0) {
            return;
        }
        dirtyAtMillis.entrySet().stream()
            .sorted(Map.Entry.comparingByValue())
            .limit(excess)
            .map(Map.Entry::getKey)
            .toList()
            .forEach(dirtyAtMillis::remove);
    }

    private static List<Integer> sanitizeTileSizes(List<Integer> tileSizes) {
        if (tileSizes == null || tileSizes.isEmpty()) {
            return List.of();
        }
        List<Integer> sanitized = new ArrayList<>();
        for (Integer tileSize : tileSizes) {
            if (tileSize == null || tileSize <= 0 || sanitized.contains(tileSize)) {
                continue;
            }
            sanitized.add(tileSize);
        }
        return List.copyOf(sanitized);
    }

    private static int alignTerrainTile(int coordinate, int tileSize) {
        return Math.floorDiv(coordinate, tileSize) * tileSize;
    }
}
