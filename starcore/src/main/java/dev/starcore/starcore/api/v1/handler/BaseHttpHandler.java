package dev.starcore.starcore.api.v1.handler;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import dev.starcore.starcore.api.v1.ApiError;
import dev.starcore.starcore.api.v1.ApiResponse;
import dev.starcore.starcore.api.v1.auth.ApiAuthContext;
import dev.starcore.starcore.api.v1.auth.ApiAuthService;
import dev.starcore.starcore.api.v1.websocket.WebSocketConnectionManager;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * HTTP 请求处理器基类
 * 提供统一的请求处理、认证、响应等基础功能
 */
public abstract class BaseHttpHandler {

    private static final Logger LOGGER = Logger.getLogger(BaseHttpHandler.class.getName());
    protected static final String CONTENT_TYPE_JSON = "application/json; charset=utf-8";
    protected static final String CONTENT_TYPE_HTML = "text/html; charset=utf-8";
    protected static final String CONTENT_TYPE_PLAIN = "text/plain; charset=utf-8";

    protected final Gson gson;
    protected final ApiAuthService authService;
    protected final WebSocketConnectionManager wsManager;

    protected BaseHttpHandler(Gson gson, ApiAuthService authService, WebSocketConnectionManager wsManager) {
        this.gson = gson != null ? gson : createDefaultGson();
        this.authService = authService;
        this.wsManager = wsManager;
    }

    protected BaseHttpHandler() {
        this(null, null, null);
    }

    /**
     * 创建默认 Gson 实例
     */
    public static Gson createDefaultGson() {
        return new GsonBuilder()
            .serializeNulls()
            .setPrettyPrinting()
            .create();
    }

    /**
     * 处理请求（由子类实现）
     */
    protected abstract void handleRequest(HttpExchange exchange, ApiAuthContext authContext) throws IOException;

    /**
     * 处理请求（带错误处理包装）
     */
    public void handle(HttpExchange exchange) throws IOException {
        // 处理 CORS 预检请求
        if (handleCorsPreflight(exchange)) {
            return;
        }

        // 获取认证上下文
        ApiAuthContext authContext = resolveAuthContext(exchange);

        try {
            handleRequest(exchange, authContext);
        } catch (Exception e) {
            handleException(exchange, e);
        }
    }

    /**
     * 解析认证上下文
     */
    protected ApiAuthContext resolveAuthContext(HttpExchange exchange) {
        if (authService == null) {
            return ApiAuthContext.anonymous();
        }

        // 从 X-API-Key 头获取 API Key
        String apiKey = getHeader(exchange, ApiAuthService.HEADER_API_KEY);
        if (apiKey != null && !apiKey.isBlank()) {
            Optional<ApiAuthService.ApiKeyInfo> keyInfo = authService.validateApiKey(apiKey);
            if (keyInfo.isPresent()) {
                ApiAuthService.ApiKeyInfo info = keyInfo.get();
                return ApiAuthContext.of(info.playerId(), info.playerName(), info.permissions());
            }
        }

        // 从 URL 参数获取 viewer（地图查看器兼容）
        Map<String, String> params = parseQueryParams(exchange);
        String viewerId = params.get("viewer");
        if (viewerId != null && !viewerId.isBlank()) {
            try {
                UUID playerId = UUID.fromString(viewerId);
                return ApiAuthContext.of(playerId, null, List.of());
            } catch (IllegalArgumentException ignored) {
            }
        }

        return ApiAuthContext.anonymous();
    }

    /**
     * 处理 CORS 预检请求
     */
    protected boolean handleCorsPreflight(HttpExchange exchange) throws IOException {
        if (!"OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            return false;
        }

        Headers headers = exchange.getResponseHeaders();
        headers.set("Access-Control-Allow-Origin", "*");
        headers.set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        headers.set("Access-Control-Allow-Headers", "Content-Type, Authorization, X-API-Key, X-Signature, X-Timestamp, X-Nonce");
        headers.set("Access-Control-Max-Age", "86400");

        exchange.sendResponseHeaders(204, -1);
        exchange.close();
        return true;
    }

    /**
     * 设置 CORS 头
     */
    protected void setCorsHeaders(HttpExchange exchange) {
        Headers headers = exchange.getResponseHeaders();
        headers.set("Access-Control-Allow-Origin", "*");
        headers.set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        headers.set("Access-Control-Allow-Headers", "Content-Type, Authorization, X-API-Key, X-Signature, X-Timestamp, X-Nonce");
    }

    /**
     * 处理异常
     */
    protected void handleException(HttpExchange exchange, Exception e) throws IOException {
        LOGGER.log(Level.SEVERE, "HTTP request handling failed", e);
        sendError(exchange, 500, "INTERNAL_ERROR", "An internal error occurred: " + e.getMessage());
    }

    /**
     * 发送成功响应
     */
    protected <T> void sendJson(HttpExchange exchange, int statusCode, T data) throws IOException {
        setCorsHeaders(exchange);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", CONTENT_TYPE_JSON);
        exchange.sendResponseHeaders(statusCode, -1);
        try (var os = exchange.getResponseBody()) {
            String json = gson.toJson(data);
            os.write(json.getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * 发送成功响应（使用 ApiResponse 包装）
     */
    protected <T> void sendSuccess(HttpExchange exchange, T data) throws IOException {
        sendJson(exchange, 200, ApiResponse.success(data));
    }

    /**
     * 发送成功响应（带分页）
     */
    protected <T> void sendSuccess(HttpExchange exchange, T data, ApiResponse.ResponseMeta meta) throws IOException {
        sendJson(exchange, 200, ApiResponse.success(data, meta));
    }

    /**
     * 发送错误响应
     */
    protected void sendError(HttpExchange exchange, int statusCode, String code, String message) throws IOException {
        setCorsHeaders(exchange);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", CONTENT_TYPE_JSON);
        exchange.sendResponseHeaders(statusCode, -1);
        try (var os = exchange.getResponseBody()) {
            String json = gson.toJson(ApiResponse.error(code, message));
            os.write(json.getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * 发送验证错误响应
     */
    protected void sendValidationError(HttpExchange exchange, List<String> errors) throws IOException {
        sendJson(exchange, 400, ApiResponse.validationError(errors));
    }

    /**
     * 发送未授权响应
     */
    protected void sendUnauthorized(HttpExchange exchange, String message) throws IOException {
        sendError(exchange, 401, "UNAUTHORIZED", message != null ? message : "Authentication required");
    }

    /**
     * 发送禁止访问响应
     */
    protected void sendForbidden(HttpExchange exchange, String message) throws IOException {
        sendError(exchange, 403, "FORBIDDEN", message != null ? message : "Access denied");
    }

    /**
     * 发送未找到响应
     */
    protected void sendNotFound(HttpExchange exchange, String resource) throws IOException {
        sendError(exchange, 404, "NOT_FOUND", resource + " not found");
    }

    /**
     * 发送方法不允许响应
     */
    protected void sendMethodNotAllowed(HttpExchange exchange) throws IOException {
        sendError(exchange, 405, "METHOD_NOT_ALLOWED", "This HTTP method is not allowed for this endpoint");
    }

    /**
     * 获取请求体
     */
    protected <T> T parseBody(HttpExchange exchange, Class<T> clazz) throws IOException {
        try (var reader = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8)) {
            return gson.fromJson(reader, clazz);
        }
    }

    /**
     * 解析查询参数
     */
    protected Map<String, String> parseQueryParams(HttpExchange exchange) {
        String query = exchange.getRequestURI().getQuery();
        if (query == null || query.isBlank()) {
            return Map.of();
        }
        return java.util.Arrays.stream(query.split("&"))
            .map(s -> {
                int idx = s.indexOf('=');
                if (idx < 0) {
                    return Map.entry(s, "");
                }
                try {
                    return Map.entry(
                        java.net.URLDecoder.decode(s.substring(0, idx), StandardCharsets.UTF_8),
                        java.net.URLDecoder.decode(s.substring(idx + 1), StandardCharsets.UTF_8)
                    );
                } catch (Exception e) {
                    return Map.entry(s.substring(0, idx), s.substring(idx + 1));
                }
            })
            .collect(java.util.stream.Collectors.toMap(
                java.util.Map.Entry::getKey,
                java.util.Map.Entry::getValue,
                (a, b) -> a
            ));
    }

    /**
     * 获取请求头
     */
    protected String getHeader(HttpExchange exchange, String name) {
        List<String> values = exchange.getRequestHeaders().get(name);
        if (values != null && !values.isEmpty()) {
            return values.get(0);
        }
        return null;
    }

    /**
     * 检查请求方法
     */
    protected boolean isMethod(HttpExchange exchange, String... methods) {
        String method = exchange.getRequestMethod().toUpperCase();
        for (String m : methods) {
            if (m.equalsIgnoreCase(method)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取请求路径
     */
    protected String getPath(HttpExchange exchange) {
        String path = exchange.getRequestURI().getPath();
        return path != null ? path : "";
    }

    /**
     * 获取路径参数
     */
    protected String[] getPathSegments(HttpExchange exchange) {
        String path = getPath(exchange);
        return path.split("/");
    }

    /**
     * 生成请求 ID
     */
    protected String generateRequestId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
