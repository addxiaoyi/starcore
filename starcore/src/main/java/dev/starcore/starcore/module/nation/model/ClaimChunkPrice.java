package dev.starcore.starcore.module.nation.model;

import java.math.BigDecimal;

public record ClaimChunkPrice(
    String world,
    int chunkX,
    int chunkZ,
    String biome,
    double biomeRichness,
    long distanceBlocks,
    double distanceMultiplier,
    double biomeMultiplier,
    BigDecimal price
) {
}
