package dev.starcore.starcore.api.v1.dto;

import com.google.gson.annotations.SerializedName;

/**
 * 领土 DTO
 */
public record TerritoryDto(
    @SerializedName("world")
    String world,

    @SerializedName("x")
    int x,

    @SerializedName("z")
    int z,

    @SerializedName("nationId")
    String nationId,

    @SerializedName("nationName")
    String nationName,

    @SerializedName("ownerId")
    String ownerId,

    @SerializedName("claimedAt")
    long claimedAt,

    @SerializedName("resourceDistrict")
    boolean hasResourceDistrict
) {}
