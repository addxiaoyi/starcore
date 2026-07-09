package dev.starcore.starcore.api.v1.dto;

import com.google.gson.annotations.SerializedName;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 国库/财务 DTO
 */
public record TreasuryDto(
    @SerializedName("nationId")
    String nationId,

    @SerializedName("nationName")
    String nationName,

    @SerializedName("balance")
    BigDecimal balance,

    @SerializedName("lastUpdated")
    long lastUpdated,

    @SerializedName("recentTransactions")
    java.util.List<TransactionDto> recentTransactions
) {
    public record TransactionDto(
        @SerializedName("id")
        String id,

        @SerializedName("type")
        String type,

        @SerializedName("amount")
        BigDecimal amount,

        @SerializedName("balanceAfter")
        BigDecimal balanceAfter,

        @SerializedName("description")
        String description,

        @SerializedName("timestamp")
        long timestamp,

        @SerializedName("actorId")
        String actorId,

        @SerializedName("actorName")
        String actorName
    ) {}
}
