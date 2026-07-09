package dev.starcore.starcore.api.v1.dto;

import com.google.gson.annotations.SerializedName;

/**
 * 玩家 DTO
 */
public record PlayerDto(
    @SerializedName("id")
    String id,

    @SerializedName("name")
    String name,

    @SerializedName("nationId")
    String nationId,

    @SerializedName("nationName")
    String nationName,

    @SerializedName("rank")
    String rank,

    @SerializedName("online")
    boolean online,

    @SerializedName("lastSeen")
    long lastSeen,

    @SerializedName("location")
    LocationDto location,

    @SerializedName("relation")
    String relation,

    @SerializedName("kda")
    KdaDto kda
) {
    public record LocationDto(
        @SerializedName("world")
        String world,

        @SerializedName("x")
        int x,

        @SerializedName("y")
        int y,

        @SerializedName("z")
        int z
    ) {}

    public record KdaDto(
        @SerializedName("kills")
        int kills,

        @SerializedName("deaths")
        int deaths,

        @SerializedName("assists")
        int assists,

        @SerializedName("ratio")
        double ratio
    ) {}
}
