package dev.starcore.starcore.api.v1.dto;

import com.google.gson.annotations.SerializedName;

/**
 * 事件日志 DTO
 */
public record EventDto(
    @SerializedName("id")
    String id,

    @SerializedName("type")
    String type,

    @SerializedName("category")
    String category,

    @SerializedName("nationId")
    String nationId,

    @SerializedName("nationName")
    String nationName,

    @SerializedName("actorId")
    String actorId,

    @SerializedName("actorName")
    String actorName,

    @SerializedName("targetId")
    String targetId,

    @SerializedName("targetName")
    String targetName,

    @SerializedName("description")
    String description,

    @SerializedName("metadata")
    java.util.Map<String, String> metadata,

    @SerializedName("timestamp")
    long timestamp
) {}
