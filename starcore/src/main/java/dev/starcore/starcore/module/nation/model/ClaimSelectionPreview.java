package dev.starcore.starcore.module.nation.model;

import dev.starcore.starcore.foundation.territory.model.ChunkClaimSelection;

import java.math.BigDecimal;

public record ClaimSelectionPreview(
    NationId nationId,
    String nationName,
    ChunkClaimSelection selection,
    int chunkCount,
    int overlapCount,
    int currentClaimCount,
    int maxClaims,
    BigDecimal price,
    BigDecimal balance,
    ClaimPriceBreakdown pricing,
    boolean canSubmit,
    String message,
    ClaimSelectionExplanation explanation
) {
    public ClaimSelectionPreview {
        explanation = explanation == null ? ClaimSelectionExplanation.basic(canSubmit, message) : explanation;
    }

    public ClaimSelectionPreview(NationId nationId, String nationName, ChunkClaimSelection selection, int chunkCount,
                                 int overlapCount, int currentClaimCount, int maxClaims, BigDecimal price,
                                 BigDecimal balance, ClaimPriceBreakdown pricing, boolean canSubmit, String message) {
        this(nationId, nationName, selection, chunkCount, overlapCount, currentClaimCount, maxClaims, price, balance,
            pricing, canSubmit, message, ClaimSelectionExplanation.basic(canSubmit, message));
    }

    public ClaimSelectionPreview(NationId nationId, String nationName, ChunkClaimSelection selection, int chunkCount,
                                 int overlapCount, int currentClaimCount, int maxClaims, BigDecimal price,
                                 BigDecimal balance, boolean canSubmit, String message) {
        this(nationId, nationName, selection, chunkCount, overlapCount, currentClaimCount, maxClaims, price, balance,
            ClaimPriceBreakdown.empty(price, price, chunkCount), canSubmit, message, ClaimSelectionExplanation.basic(canSubmit, message));
    }

    public ClaimSelectionPreview(NationId nationId, String nationName, ChunkClaimSelection selection, int chunkCount,
                                 int overlapCount, int currentClaimCount, int maxClaims, BigDecimal price,
                                 BigDecimal balance, boolean canSubmit, String message, ClaimSelectionExplanation explanation) {
        this(nationId, nationName, selection, chunkCount, overlapCount, currentClaimCount, maxClaims, price, balance,
            ClaimPriceBreakdown.empty(price, price, chunkCount), canSubmit, message, explanation);
    }
}
