package dev.starcore.starcore.api.v1;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import dev.starcore.starcore.api.v1.auth.ApiAuthService;
import dev.starcore.starcore.api.v1.endpoint.NationEndpoint;
import dev.starcore.starcore.api.v1.endpoint.StatsEndpoint;
import dev.starcore.starcore.api.v1.endpoint.TerritoryEndpoint;
import dev.starcore.starcore.api.v1.websocket.WebSocketConnectionManager;
import dev.starcore.starcore.foundation.player.OnlinePlayerDirectory;
import dev.starcore.starcore.foundation.territory.TerritoryService;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.war.WarService;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * REST API 服务器
 * 提供完整的 REST API 服务
 */
public final class RestApiServer {

    private final Gson gson;
    private final ApiAuthService authService;
    private final WebSocketConnectionManager wsManager;
    private final NationService nationService;
    private final TerritoryService territoryService;
    private final WarService warService;
    private final OnlinePlayerDirectory playerDirectory;

    private HttpServer server;
    private ExecutorService executor;
    private final String host;
    private final int port;

    public RestApiServer(
        NationService nationService,
        TerritoryService territoryService,
        WarService warService,
        OnlinePlayerDirectory playerDirectory,
        String host,
        int port,
        ApiAuthService authService
    ) {
        this.nationService = nationService;
        this.territoryService = territoryService;
        this.warService = warService;
        this.playerDirectory = playerDirectory;
        this.host = host;
        this.port = port;

        this.gson = new GsonBuilder()
            .serializeNulls()
            .setPrettyPrinting()
            .create();

        this.authService = authService;
        this.wsManager = new WebSocketConnectionManager(gson);
    }

    /**
     * 启动服务器
     */
    public void start() throws IOException {
        if (server != null) {
            return;
        }

        server = HttpServer.create(new InetSocketAddress(host, port), 0);
        executor = Executors.newFixedThreadPool(
            Math.max(2, Runtime.getRuntime().availableProcessors()),
            new RestApiThreadFactory()
        );
        server.setExecutor(executor);

        // 注册处理器
        registerHandlers();

        server.start();
    }

    /**
     * 停止服务器
     */
    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            executor = null;
        }
    }

    /**
     * 注册所有处理器
     */
    private void registerHandlers() {
        // 创建端点处理器
        NationEndpoint nationEndpoint = new NationEndpoint(gson, authService, wsManager, nationService);
        TerritoryEndpoint territoryEndpoint = new TerritoryEndpoint(gson, authService, wsManager, territoryService, nationService);
        StatsEndpoint statsEndpoint = new StatsEndpoint(gson, authService, wsManager, nationService, warService, playerDirectory);

        // 健康检查端点
        HttpHandler healthHandler = exchange -> {
            if (handleCorsPreflight(exchange)) return;
            setCorsHeaders(exchange);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, -1);
            try (var os = exchange.getResponseBody()) {
                os.write(gson.toJson(ApiResponse.success(Map.of(
                    "status", "ok",
                    "version", "1.0.0",
                    "uptime", System.currentTimeMillis()
                ))).getBytes());
            }
        };

        // 根端点
        HttpHandler rootHandler = exchange -> {
            if (handleCorsPreflight(exchange)) return;
            setCorsHeaders(exchange);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, -1);
            try (var os = exchange.getResponseBody()) {
                os.write(gson.toJson(ApiResponse.success(Map.of(
                    "name", "StarCore REST API",
                    "version", "1.0.0",
                    "endpoints", Map.of(
                        "health", "/api/v1/health",
                        "nations", "/api/v1/nations",
                        "territories", "/api/v1/territories",
                        "stats", "/api/v1/stats",
                        "websocket", "/api/v1/ws"
                    )
                ))).getBytes());
            }
        };

        // WebSocket 升级端点
        HttpHandler wsHandler = exchange -> {
            if (handleCorsPreflight(exchange)) return;
            setCorsHeaders(exchange);
            // 返回 WebSocket 握手信息
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(426, -1); // Upgrade Required
            try (var os = exchange.getResponseBody()) {
                os.write(gson.toJson(ApiResponse.error("UPGRADE_REQUIRED", "WebSocket upgrade required")).getBytes());
            }
        };

        // 注册路由
        server.createContext("/", rootHandler);
        server.createContext("/api/v1/health", healthHandler);
        server.createContext("/api/v1/nations", nationEndpoint::handle);
        server.createContext("/api/v1/territories", territoryEndpoint::handle);
        server.createContext("/api/v1/stats", statsEndpoint::handle);
        server.createContext("/api/v1/ws", wsHandler);
    }

    /**
     * 处理 CORS 预检请求
     */
    private boolean handleCorsPreflight(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        if (!"OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            return false;
        }

        var headers = exchange.getResponseHeaders();
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
    private void setCorsHeaders(com.sun.net.httpserver.HttpExchange exchange) {
        var headers = exchange.getResponseHeaders();
        headers.set("Access-Control-Allow-Origin", "*");
        headers.set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        headers.set("Access-Control-Allow-Headers", "Content-Type, Authorization, X-API-Key, X-Signature, X-Timestamp, X-Nonce");
    }

    /**
     * 获取认证服务
     */
    public ApiAuthService getAuthService() {
        return authService;
    }

    /**
     * 获取 WebSocket 管理器
     */
    public WebSocketConnectionManager getWsManager() {
        return wsManager;
    }

    /**
     * 获取 Gson 实例
     */
    public Gson getGson() {
        return gson;
    }

    /**
     * 检查服务器是否运行
     */
    public boolean isRunning() {
        return server != null;
    }

    /**
     * 获取端口
     */
    public int getPort() {
        return port;
    }

    private static final class RestApiThreadFactory implements ThreadFactory {
        private final AtomicInteger id = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "starcore-rest-api-" + id.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
