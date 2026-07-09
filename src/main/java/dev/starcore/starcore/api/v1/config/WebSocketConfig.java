package dev.starcore.starcore.api.v1.config;

import com.google.gson.annotations.SerializedName;

/**
 * WebSocket 配置
 */
public record WebSocketConfig(
    @SerializedName("enabled")
    boolean enabled,

    @SerializedName("path")
    String path,

    @SerializedName("maxConnections")
    int maxConnections,

    @SerializedName("pingIntervalSeconds")
    int pingIntervalSeconds,

    @SerializedName("pingTimeoutSeconds")
    int pingTimeoutSeconds,

    @SerializedName("messageMaxSize")
    int messageMaxSize,

    @SerializedName("channels")
    java.util.List<ChannelConfig> channels
) {
    public static final WebSocketConfig DEFAULT = new WebSocketConfig(
        true,
        "/api/v1/ws",
        1000,
        30,
        60,
        65536,
        java.util.List.of(
            new ChannelConfig("nations", true, 100),
            new ChannelConfig("territories", true, 200),
            new ChannelConfig("wars", true, 50),
            new ChannelConfig("finance", true, 50),
            new ChannelConfig("events", true, 100),
            new ChannelConfig("chat", false, 200)
        )
    );

    /**
     * 频道配置
     */
    public record ChannelConfig(
        @SerializedName("name")
        String name,

        @SerializedName("requiresAuth")
        boolean requiresAuth,

        @SerializedName("maxSubscribers")
        int maxSubscribers
    ) {}
}
