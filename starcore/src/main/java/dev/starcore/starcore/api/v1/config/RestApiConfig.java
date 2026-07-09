package dev.starcore.starcore.api.v1.config;

import com.google.gson.annotations.SerializedName;

/**
 * REST API 配置
 */
public record RestApiConfig(
    @SerializedName("enabled")
    boolean enabled,

    @SerializedName("host")
    String host,

    @SerializedName("port")
    int port,

    @SerializedName("signingSecret")
    String signingSecret,

    @SerializedName("cors")
    CorsConfig cors,

    @SerializedName("rateLimit")
    RateLimitConfig rateLimit,

    @SerializedName("endpoints")
    EndpointsConfig endpoints
) {
    public static final RestApiConfig DEFAULT = new RestApiConfig(
        false,
        "127.0.0.1",
        8717,
        "change-this-secret",
        CorsConfig.DEFAULT,
        RateLimitConfig.DEFAULT,
        EndpointsConfig.DEFAULT
    );

    /**
     * CORS 配置
     */
    public record CorsConfig(
        @SerializedName("enabled")
        boolean enabled,

        @SerializedName("allowedOrigins")
        java.util.List<String> allowedOrigins,

        @SerializedName("allowedMethods")
        java.util.List<String> allowedMethods,

        @SerializedName("allowedHeaders")
        java.util.List<String> allowedHeaders,

        @SerializedName("maxAge")
        int maxAge
    ) {
        public static final CorsConfig DEFAULT = new CorsConfig(
            true,
            java.util.List.of("*"),
            java.util.List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"),
            java.util.List.of("Content-Type", "Authorization", "X-API-Key", "X-Signature", "X-Timestamp", "X-Nonce"),
            86400
        );
    }

    /**
     * 速率限制配置
     */
    public record RateLimitConfig(
        @SerializedName("enabled")
        boolean enabled,

        @SerializedName("requestsPerMinute")
        int requestsPerMinute,

        @SerializedName("burstSize")
        int burstSize,

        @SerializedName("blockDurationSeconds")
        int blockDurationSeconds
    ) {
        public static final RateLimitConfig DEFAULT = new RateLimitConfig(
            true,
            60,
            10,
            60
        );
    }

    /**
     * 端点配置
     */
    public record EndpointsConfig(
        @SerializedName("nations")
        EndpointConfig nations,

        @SerializedName("territories")
        EndpointConfig territories,

        @SerializedName("stats")
        EndpointConfig stats,

        @SerializedName("finance")
        EndpointConfig finance,

        @SerializedName("websocket")
        EndpointConfig websocket
    ) {
        public static final EndpointsConfig DEFAULT = new EndpointsConfig(
            EndpointConfig.DEFAULT,
            EndpointConfig.DEFAULT,
            EndpointConfig.DEFAULT,
            new EndpointConfig(true, true), // finance 需要认证
            new EndpointConfig(true, false)
        );
    }

    /**
     * 单个端点配置
     */
    public record EndpointConfig(
        @SerializedName("enabled")
        boolean enabled,

        @SerializedName("requiresAuth")
        boolean requiresAuth
    ) {
        public static final EndpointConfig DEFAULT = new EndpointConfig(true, false);
    }
}
