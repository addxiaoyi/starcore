package dev.starcore.starcore.foundation.protection;

import dev.starcore.starcore.foundation.territory.model.ChunkClaimSelection;
import dev.starcore.starcore.foundation.territory.model.ChunkCoordinate;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class ClaimConflictSampler {
    private ClaimConflictSampler() {
    }

    static List<BlockProbe> probesFor(ChunkCoordinate coordinate, int edgeInsetBlocks, List<Integer> sampleHeights, Integer surfaceHeight) {
        int inset = Math.clamp(edgeInsetBlocks, 0, (ChunkClaimSelection.CHUNK_SIZE / 2) - 1);
        int minBlockX = coordinate.x() * ChunkClaimSelection.CHUNK_SIZE;
        int minBlockZ = coordinate.z() * ChunkClaimSelection.CHUNK_SIZE;
        int maxBlockX = minBlockX + ChunkClaimSelection.CHUNK_SIZE - 1;
        int maxBlockZ = minBlockZ + ChunkClaimSelection.CHUNK_SIZE - 1;
        int left = minBlockX + inset;
        int right = maxBlockX - inset;
        int near = minBlockZ + inset;
        int far = maxBlockZ - inset;
        int centerX = minBlockX + (ChunkClaimSelection.CHUNK_SIZE / 2);
        int centerZ = minBlockZ + (ChunkClaimSelection.CHUNK_SIZE / 2);

        List<Integer> heights = new ArrayList<>(sampleHeights == null ? List.of() : sampleHeights);
        if (surfaceHeight != null) {
            heights.add(surfaceHeight);
        }

        Set<BlockProbe> probes = new LinkedHashSet<>();
        for (Integer y : heights) {
            if (y == null) {
                continue;
            }
            probes.add(new BlockProbe(centerX, y, centerZ));
            probes.add(new BlockProbe(left, y, near));
            probes.add(new BlockProbe(left, y, far));
            probes.add(new BlockProbe(right, y, near));
            probes.add(new BlockProbe(right, y, far));
        }
        return List.copyOf(probes);
    }

    record BlockProbe(int blockX, int blockY, int blockZ) {
    }
}
