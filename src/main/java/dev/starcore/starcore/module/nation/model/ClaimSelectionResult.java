package dev.starcore.starcore.module.nation.model;

import dev.starcore.starcore.foundation.territory.model.ChunkClaimSelection;

import java.math.BigDecimal;

public record ClaimSelectionResult(
    NationId nationId,
    String nationName,
    ChunkClaimSelection selection,
    int claimedChunks,
    BigDecimal price
) {
}
