package dev.starcore.starcore.module.nation.claimtool;

import dev.starcore.starcore.foundation.territory.model.ChunkClaimSelection;
import dev.starcore.starcore.module.nation.model.ClaimSelectionPreview;

import java.util.Optional;

public record ClaimToolSelectionUpdate(
    ClaimToolPoint point,
    String world,
    int blockX,
    int blockZ,
    int chunkX,
    int chunkZ,
    Optional<ChunkClaimSelection> selection,
    Optional<ClaimSelectionPreview> preview
) {
}
