package dev.starcore.starcore.api.v1.dto;

import com.google.gson.annotations.SerializedName;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * 国家 DTO（数据传输对象）
 */
public record NationDto(
    @SerializedName("id")
    String id,

    @SerializedName("name")
    String name,

    @SerializedName("kind")
    String kind,

    @SerializedName("government")
    String government,

    @SerializedName("founderId")
    String founderId,

    @SerializedName("founderName")
    String founderName,

    @SerializedName("memberCount")
    int memberCount,

    @SerializedName("claimCount")
    int claimCount,

    @SerializedName("level")
    int level,

    @SerializedName("experience")
    long experience,

    @SerializedName("population")
    int population,

    @SerializedName("treasuryBalance")
    BigDecimal treasuryBalance,

    @SerializedName("taxRate")
    double taxRate,

    @SerializedName("allyCount")
    int allyCount,

    @SerializedName("warCount")
    int warCount,

    @SerializedName("policyCount")
    int policyCount,

    @SerializedName("technologyCount")
    int technologyCount,

    @SerializedName("foundedAt")
    long foundedAt,

    @SerializedName("capitalLocation")
    LocationDto capitalLocation
) {
    /**
     * 创建位置 DTO
     */
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

    /**
     * 创建简略版本（列表用）
     */
    public static NationDto summary(
        UUID id,
        String name,
        String kind,
        String government,
        String founderId,
        int memberCount,
        int claimCount,
        int level
    ) {
        return new NationDto(
            id.toString(),
            name,
            kind,
            government,
            founderId,
            null,
            memberCount,
            claimCount,
            level,
            0L,
            memberCount,
            BigDecimal.ZERO,
            0.0,
            0,
            0,
            0,
            0,
            0L,
            null
        );
    }
}
