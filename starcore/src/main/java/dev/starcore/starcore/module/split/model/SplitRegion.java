package dev.starcore.starcore.module.split.model;

import dev.starcore.starcore.foundation.territory.model.ChunkCoordinate;

import java.util.List;
import java.util.Objects;

/**
 * 分裂区域模型
 * 定义从原国家分离的领土范围
 */
public record SplitRegion(
    String world,
    List<ChunkCoordinate> chunks,
    int chunkCount
) {
    /**
     * 创建分裂区域
     *
     * @param world 世界名称
     * @param chunks 要分离的区块坐标列表
     * @return 分裂区域
     */
    public static SplitRegion of(String world, List<ChunkCoordinate> chunks) {
        Objects.requireNonNull(world, "world cannot be null");
        Objects.requireNonNull(chunks, "chunks cannot be null");
        return new SplitRegion(world, List.copyOf(chunks), chunks.size());
    }

    /**
     * 创建单区块分裂区域
     */
    public static SplitRegion single(String world, int chunkX, int chunkZ) {
        ChunkCoordinate coord = new ChunkCoordinate(world, chunkX, chunkZ);
        return of(world, List.of(coord));
    }

    /**
     * 创建矩形分裂区域
     */
    public static SplitRegion rectangle(String world, int minX, int maxX, int minZ, int maxZ) {
        List<ChunkCoordinate> coords = new java.util.ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                coords.add(new ChunkCoordinate(world, x, z));
            }
        }
        return of(world, coords);
    }

    /**
     * 获取中心区块
     */
    public ChunkCoordinate centerChunk() {
        if (chunks.isEmpty()) {
            throw new IllegalStateException("SplitRegion has no chunks");
        }
        int sumX = 0, sumZ = 0;
        for (ChunkCoordinate chunk : chunks) {
            sumX += chunk.x();
            sumZ += chunk.z();
        }
        int centerX = sumX / chunks.size();
        int centerZ = sumZ / chunks.size();
        return new ChunkCoordinate(world, centerX, centerZ);
    }
}
