package dev.starcore.starcore.api.v1.module;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.starcore.starcore.api.v1.RestApiServer;
import dev.starcore.starcore.api.v1.auth.ApiAuthService;
import dev.starcore.starcore.api.v1.websocket.WebSocketConnectionManager;
import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.module.ModuleLayer;
import dev.starcore.starcore.core.module.ModuleMetadata;
import dev.starcore.starcore.core.module.StarCoreModule;
import dev.starcore.starcore.foundation.player.OnlinePlayerDirectory;
import dev.starcore.starcore.foundation.territory.TerritoryService;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.war.WarService;

import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

/**
 * REST API 模块
 * 提供完整的 REST API 服务、认证和 WebSocket 支持
 */
public final class RestApiModule implements StarCoreModule {

    private static final ModuleMetadata METADATA = new ModuleMetadata(
        "rest-api",
        "REST API 服务",
        ModuleLayer.MODULE,
        List.of("nation"),
        List.of(),
        "Provides REST API endpoints with authentication, validation, and WebSocket support"
    );

    private StarCoreContext context;
    private Logger logger;

    private RestApiServer restApiServer;
    private ApiAuthService authService;
    private WebSocketConnectionManager wsManager;
    private Gson gson;

    private volatile boolean enabled = false;

    @Override
    public ModuleMetadata metadata() {
        return METADATA;
    }

    @Override
    public void enable(StarCoreContext context) {
        this.context = context;
        this.logger = context.plugin().getLogger();

        // 读取配置
        this.enabled = context.configuration().restApiEnabled();
        if (!enabled) {
            logger.info("REST API is disabled in configuration");
            return;
        }

        if (context.configuration().ensureRestApiSigningSecretConfigured()) {
            logger.warning("Generated a new REST API signing secret; update any external API clients with new credentials.");
        }

        // 初始化 Gson
        this.gson = new GsonBuilder()
            .serializeNulls()
            .setPrettyPrinting()
            .create();

        // 初始化认证服务
        String signingSecret = context.configuration().restApiSigningSecret();
        this.authService = new ApiAuthService(signingSecret);

        // 初始化 WebSocket 管理器
        this.wsManager = new WebSocketConnectionManager(gson);

        // 获取依赖服务
        NationService nationService = context.serviceRegistry().find(NationService.class).orElse(null);
        TerritoryService territoryService = context.serviceRegistry().find(TerritoryService.class).orElse(null);
        OnlinePlayerDirectory playerDirectory = context.serviceRegistry().find(OnlinePlayerDirectory.class).orElse(null);
        WarService warService = context.serviceRegistry().find(WarService.class).orElse(null);

        // 初始化 REST API 服务器
        String host = context.configuration().restApiHost();
        int port = context.configuration().restApiPort();

        this.restApiServer = new RestApiServer(
            nationService,
            territoryService,
            warService,
            playerDirectory,
            host,
            port,
            authService
        );

        // 注册服务
        context.serviceRegistry().register(RestApiServer.class, restApiServer);
        context.serviceRegistry().register(ApiAuthService.class, authService);
        context.serviceRegistry().register(WebSocketConnectionManager.class, wsManager);

        // 启动服务器
        try {
            restApiServer.start();
            logger.info("REST API server started on http://" + host + ":" + port);
            logger.info("API documentation: http://" + host + ":" + port + "/api/v1/");
        } catch (IOException e) {
            logger.severe("Failed to start REST API server: " + e.getMessage());
            this.enabled = false;
        }
    }

    @Override
    public void disable(StarCoreContext context) {
        if (restApiServer != null) {
            restApiServer.stop();
            logger.info("REST API server stopped");
        }

        // 注销服务
        if (context != null && context.serviceRegistry() != null) {
            context.serviceRegistry().unregister(RestApiServer.class);
            context.serviceRegistry().unregister(ApiAuthService.class);
            context.serviceRegistry().unregister(WebSocketConnectionManager.class);
        }
    }

    /**
     * 检查模块是否启用
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 获取 REST API 服务器
     */
    public RestApiServer getRestApiServer() {
        return restApiServer;
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
     * 获取活跃 WebSocket 连接数
     */
    public int getActiveWebSocketConnections() {
        return wsManager != null ? wsManager.getActiveSessionCount() : 0;
    }
}
