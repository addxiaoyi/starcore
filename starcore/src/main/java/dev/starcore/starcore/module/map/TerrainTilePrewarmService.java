package dev.starcore.starcore.module.map;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

final class TerrainTilePrewarmService {
    private final Queue<TerrainPrewarmTile> queue = new ConcurrentLinkedQueue<>();

    int rebuild(Collection<WorldSpawn> worlds, int radiusBlocks, int maxTiles, List<Integer> tileSizes) {
        queue.clear();
        queue.addAll(buildTiles(worlds, radiusBlocks, maxTiles, tileSizes));
        return queue.size();
    }

    TerrainPrewarmTile poll() {
        return queue.poll();
    }

    void requeue(TerrainPrewarmTile tile) {
        if (tile != null) {
            queue.offer(tile);
        }
    }

    boolean isEmpty() {
        return queue.isEmpty();
    }

    int size() {
        return queue.size();
    }

    void clear() {
        queue.clear();
    }

    static List<TerrainPrewarmTile> buildTiles(Collection<WorldSpawn> worlds, int radiusBlocks, int maxTiles, List<Integer> tileSizes) {
        if (worlds == null || worlds.isEmpty() || radiusBlocks <= 0 || maxTiles <= 0 || tileSizes == null || tileSizes.isEmpty()) {
            return List.of();
        }
        List<TerrainPrewarmTile> candidates = new ArrayList<>();
        for (WorldSpawn world : worlds) {
            if (world == null || world.name() == null || world.name().isBlank()) {
                continue;
            }
            for (int order = 0; order < tileSizes.size(); order++) {
                int tileSize = tileSizes.get(order);
                if (tileSize <= 0) {
                    continue;
                }
                int minX = alignTerrainTile(world.spawnX() - radiusBlocks, tileSize);
                int maxX = alignTerrainTile(world.spawnX() + radiusBlocks, tileSize);
                int minZ = alignTerrainTile(world.spawnZ() - radiusBlocks, tileSize);
                int maxZ = alignTerrainTile(world.spawnZ() + radiusBlocks, tileSize);
                for (int x = minX; x <= maxX; x += tileSize) {
                    for (int z = minZ; z <= maxZ; z += tileSize) {
                        long distanceSquared = terrainTileDistanceSquared(world.spawnX(), world.spawnZ(), x, z, tileSize);
                        candidates.add(new TerrainPrewarmTile(world.name(), x, z, tileSize, order, distanceSquared));
                    }
                }
            }
        }
        return candidates.stream()
            .sorted(Comparator.comparingInt(TerrainPrewarmTile::sortOrder).thenComparingLong(TerrainPrewarmTile::distanceSquared))
            .limit(maxTiles)
            .toList();
    }

    private static int alignTerrainTile(int coordinate, int tileSize) {
        return Math.floorDiv(coordinate, tileSize) * tileSize;
    }

    private static long terrainTileDistanceSquared(int spawnX, int spawnZ, int minX, int minZ, int tileSize) {
        long centerX = minX + tileSize / 2L;
        long centerZ = minZ + tileSize / 2L;
        long deltaX = centerX - spawnX;
        long deltaZ = centerZ - spawnZ;
        return deltaX * deltaX + deltaZ * deltaZ;
    }

    record WorldSpawn(String name, int spawnX, int spawnZ) {
    }
}
