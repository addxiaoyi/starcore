package dev.starcore.starcore.api.v1;

import com.google.gson.annotations.SerializedName;

import java.util.Map;

/**
 * REST API 模块元数据
 */
public final class RestApiMetadata {

    public static final String MODULE_ID = "rest-api";
    public static final String MODULE_NAME = "REST API 服务";
    public static final String MODULE_VERSION = "1.0.0";

    private RestApiMetadata() {}

    /**
     * API 版本信息
     */
    public record ApiVersion(
        @SerializedName("version")
        String version,

        @SerializedName("build")
        String build,

        @SerializedName("documentation")
        String documentation
    ) {
        public static final ApiVersion CURRENT = new ApiVersion("v1", "1.0.0", "/api/v1/docs");
    }

    /**
     * API 端点描述
     */
    public record EndpointInfo(
        @SerializedName("path")
        String path,

        @SerializedName("method")
        String method,

        @SerializedName("description")
        String description,

        @SerializedName("requiresAuth")
        boolean requiresAuth,

        @SerializedName("rateLimited")
        boolean rateLimited
    ) {}

    /**
     * 所有可用端点
     */
    public static final Map<String, EndpointInfo> ENDPOINTS = Map.ofEntries(
        Map.entry("health", new EndpointInfo("/api/v1/health", "GET", "健康检查", false, false)),
        Map.entry("nations_list", new EndpointInfo("/api/v1/nations", "GET", "获取国家列表", false, true)),
        Map.entry("nations_get", new EndpointInfo("/api/v1/nations/{id}", "GET", "获取国家详情", false, true)),
        Map.entry("territories_list", new EndpointInfo("/api/v1/territories", "GET", "获取领土列表", false, true)),
        Map.entry("territories_get", new EndpointInfo("/api/v1/territories/{world}/{x}/{z}", "GET", "获取领土详情", false, true)),
        Map.entry("stats", new EndpointInfo("/api/v1/stats", "GET", "获取服务器统计", false, true)),
        Map.entry("websocket", new EndpointInfo("/api/v1/ws", "WS", "WebSocket 连接", false, true))
    );
}
