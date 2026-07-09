package dev.starcore.starcore.api.v1.dto;

import com.google.gson.annotations.SerializedName;

/**
 * 服务器统计 DTO
 */
public record ServerStatsDto(
    @SerializedName("serverName")
    String serverName,

    @SerializedName("onlinePlayers")
    int onlinePlayers,

    @SerializedName("maxPlayers")
    int maxPlayers,

    @SerializedName("totalNations")
    int totalNations,

    @SerializedName("totalTerritories")
    int totalTerritories,

    @SerializedName("totalCities")
    int totalCities,

    @SerializedName("activeWars")
    int activeWars,

    @SerializedName("uptime")
    long uptime,

    @SerializedName("tps")
    double tps,

    @SerializedName("memoryUsage")
    MemoryUsageDto memoryUsage
) {
    public record MemoryUsageDto(
        @SerializedName("used")
        long used,

        @SerializedName("max")
        long max,

        @SerializedName("percentage")
        double percentage
    ) {}
}
