package dev.starcore.starcore.module.map.model;

import dev.starcore.starcore.foundation.territory.model.ChunkClaimSelection;
import dev.starcore.starcore.module.nation.model.ClaimSelectionExplanation;

import java.math.BigDecimal;

public record WebClaimConfirmationResult(
    String pendingId,
    String nationName,
    ChunkClaimSelection selection,
    int claimedChunks,
    BigDecimal price,
    String message,
    ClaimSelectionExplanation explanation,
    Status status
) {
    public WebClaimConfirmationResult {
        pendingId = pendingId == null ? "" : pendingId;
        nationName = nationName == null ? "" : nationName;
        price = price == null ? BigDecimal.ZERO : price;
        message = message == null ? "" : message;
        status = status == null ? Status.FAILED : status;
        explanation = explanation == null
            ? ClaimSelectionExplanation.basic(status == Status.CONFIRMED, message)
            : explanation;
    }

    public WebClaimConfirmationResult(
        String pendingId,
        String nationName,
        ChunkClaimSelection selection,
        int claimedChunks,
        BigDecimal price,
        String message
    ) {
        this(pendingId, nationName, selection, claimedChunks, price, message, ClaimSelectionExplanation.basic(true, message), Status.CONFIRMED);
    }

    public static WebClaimConfirmationResult failed(String pendingId, String message, ClaimSelectionExplanation explanation) {
        return new WebClaimConfirmationResult(pendingId, "", null, 0, BigDecimal.ZERO, message, explanation, Status.FAILED);
    }

    public static WebClaimConfirmationResult cancelled(
        String pendingId,
        String nationName,
        ChunkClaimSelection selection,
        int chunkCount,
        BigDecimal price,
        String message,
        ClaimSelectionExplanation explanation
    ) {
        return new WebClaimConfirmationResult(pendingId, nationName, selection, chunkCount, price, message, explanation, Status.CANCELLED);
    }

    public boolean confirmed() {
        return status == Status.CONFIRMED;
    }

    public boolean cancelled() {
        return status == Status.CANCELLED;
    }

    public enum Status {
        CONFIRMED,
        CANCELLED,
        FAILED
    }
}
