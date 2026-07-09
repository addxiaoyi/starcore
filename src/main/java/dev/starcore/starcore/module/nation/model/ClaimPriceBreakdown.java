package dev.starcore.starcore.module.nation.model;

import java.math.BigDecimal;
import java.util.List;

public record ClaimPriceBreakdown(
    BigDecimal baseChunkPrice,
    BigDecimal totalPrice,
    int chunkCount,
    List<ClaimChunkPrice> chunks
) {
    public ClaimPriceBreakdown {
        baseChunkPrice = baseChunkPrice == null ? BigDecimal.ZERO : baseChunkPrice;
        totalPrice = totalPrice == null ? BigDecimal.ZERO : totalPrice;
        chunks = chunks == null ? List.of() : List.copyOf(chunks);
    }

    public static ClaimPriceBreakdown empty(BigDecimal baseChunkPrice, BigDecimal totalPrice, int chunkCount) {
        return new ClaimPriceBreakdown(baseChunkPrice, totalPrice, chunkCount, List.of());
    }
}
