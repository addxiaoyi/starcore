package dev.starcore.starcore.api.v1.dto;

import com.google.gson.annotations.SerializedName;

/**
 * 战争 DTO
 */
public record WarDto(
    @SerializedName("id")
    String id,

    @SerializedName("attackerId")
    String attackerId,

    @SerializedName("attackerName")
    String attackerName,

    @SerializedName("defenderId")
    String defenderId,

    @SerializedName("defenderName")
    String defenderName,

    @SerializedName("status")
    String status,

    @SerializedName("startedAt")
    long startedAt,

    @SerializedName("endedAt")
    long endedAt,

    @SerializedName("preparationEndsAt")
    long preparationEndsAt,

    @SerializedName("declarationReason")
    String declarationReason,

    @SerializedName("winner")
    String winner,

    @SerializedName("attackerCasualties")
    int attackerCasualties,

    @SerializedName("defenderCasualties")
    int defenderCasualties,

    @SerializedName("reparations")
    double reparations,

    @SerializedName("allyParticipants")
    java.util.List<String> allyParticipants
) {
    /**
     * 战争状态
     */
    public static final String STATUS_PREPARATION = "PREPARATION";
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_ENDED = "ENDED";
    public static final String STATUS_SURRENDERED = "SURRENDERED";
}
