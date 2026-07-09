package dev.starcore.starcore.foundation.territory.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record ChunkClaimSelection(String world, int minChunkX, int maxChunkX, int minChunkZ, int maxChunkZ) {
    public static final int CHUNK_SIZE = 16;

    public ChunkClaimSelection {
        Objects.requireNonNull(world, "world");
        if (world.isBlank()) {
            throw new IllegalArgumentException("world cannot be blank");
        }
        if (minChunkX > maxChunkX || minChunkZ > maxChunkZ) {
            throw new IllegalArgumentException("chunk bounds must be ordered");
        }
        chunkCount();
    }

    public static ChunkClaimSelection fromBlockBounds(String world, int minBlockX, int maxBlockX, int minBlockZ, int maxBlockZ) {
        int orderedMinX = Math.min(minBlockX, maxBlockX);
        int orderedMaxX = Math.max(minBlockX, maxBlockX);
        int orderedMinZ = Math.min(minBlockZ, maxBlockZ);
        int orderedMaxZ = Math.max(minBlockZ, maxBlockZ);
        return new ChunkClaimSelection(
            world,
            Math.floorDiv(orderedMinX, CHUNK_SIZE),
            Math.floorDiv(orderedMaxX, CHUNK_SIZE),
            Math.floorDiv(orderedMinZ, CHUNK_SIZE),
            Math.floorDiv(orderedMaxZ, CHUNK_SIZE)
        );
    }

    public int chunkCount() {
        long width = (long) maxChunkX - minChunkX + 1L;
        long height = (long) maxChunkZ - minChunkZ + 1L;
        long total = width * height;
        if (total <= 0L || total > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("claim selection is too large");
        }
        return (int) total;
    }

    public long minBlockX() {
        return (long) minChunkX * CHUNK_SIZE;
    }

    public long maxBlockX() {
        return (((long) maxChunkX + 1L) * CHUNK_SIZE) - 1L;
    }

    public long minBlockZ() {
        return (long) minChunkZ * CHUNK_SIZE;
    }

    public long maxBlockZ() {
        return (((long) maxChunkZ + 1L) * CHUNK_SIZE) - 1L;
    }

    public List<ChunkCoordinate> coordinates() {
        List<ChunkCoordinate> coordinates = new ArrayList<>(chunkCount());
        for (int x = minChunkX; x <= maxChunkX; x++) {
            for (int z = minChunkZ; z <= maxChunkZ; z++) {
                coordinates.add(new ChunkCoordinate(world, x, z));
            }
        }
        return coordinates;
    }
}
