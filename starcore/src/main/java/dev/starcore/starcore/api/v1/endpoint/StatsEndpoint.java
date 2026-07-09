package dev.starcore.starcore.api.v1.endpoint;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import dev.starcore.starcore.api.v1.ApiResponse;
import dev.starcore.starcore.api.v1.auth.ApiAuthContext;
import dev.starcore.starcore.api.v1.dto.ServerStatsDto;
import dev.starcore.starcore.api.v1.handler.BaseHttpHandler;
import dev.starcore.starcore.api.v1.websocket.WebSocketConnectionManager;
import dev.starcore.starcore.api.v1.auth.ApiAuthService;
import dev.starcore.starcore.foundation.player.OnlinePlayerDirectory;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.war.WarService;
import org.bukkit.Bukkit;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 统计 REST API 端点
 */
public final class StatsEndpoint extends BaseHttpHandler {

    private final NationService nationService;
    private final WarService warService;
    private final OnlinePlayerDirectory playerDirectory;
    private final String serverName;

    public StatsEndpoint(
        Gson gson,
        ApiAuthService authService,
        WebSocketConnectionManager wsManager,
        NationService nationService,
        WarService warService,
        OnlinePlayerDirectory playerDirectory
    ) {
        super(gson, authService, wsManager);
        this.nationService = nationService;
        this.warService = warService;
        this.playerDirectory = playerDirectory;
        this.serverName = "StarCore Server";
    }

    @Override
    protected void handleRequest(HttpExchange exchange, ApiAuthContext authContext) throws IOException {
        String path = getPath(exchange);

        if (path.equals("/api/v1/stats")) {
            if (isMethod(exchange, "GET")) {
                handleGetStats(exchange, authContext);
            } else {
                sendMethodNotAllowed(exchange);
            }
        } else {
            sendNotFound(exchange, "Endpoint");
        }
    }

    /**
     * 获取服务器统计
     */
    private void handleGetStats(HttpExchange exchange, ApiAuthContext authContext) throws IOException {
        int onlinePlayers = playerDirectory != null ? playerDirectory.onlinePlayers().size() : Bukkit.getOnlinePlayers().size();
        int maxPlayers = Bukkit.getMaxPlayers();
        int totalNations = nationService != null ? nationService.nations().size() : 0;

        // 统计领土
        int totalTerritories = 0;
        int totalCities = 0;
        if (nationService != null) {
            for (Nation nation : nationService.nations()) {
                if (nation.kind().name().equalsIgnoreCase("CITY")) {
                    totalCities++;
                }
            }
            // 简化：使用国家数作为城邦数的替代
            // 实际应从 TerritoryService 获取
        }

        // 统计活跃战争
        int activeWars = warService != null ? warService.activeWars().size() : 0;

        // 计算 TPS
        double tps = calculateTps();

        // 计算运行时间
        long uptime = ManagementFactory.getRuntimeMXBean().getUptime();

        // 内存使用
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        long maxMemory = runtime.maxMemory();
        double memoryPercentage = (double) usedMemory / maxMemory * 100;

        ServerStatsDto.MemoryUsageDto memoryUsage = new ServerStatsDto.MemoryUsageDto(
            usedMemory / (1024 * 1024),
            maxMemory / (1024 * 1024),
            memoryPercentage
        );

        ServerStatsDto stats = new ServerStatsDto(
            serverName,
            onlinePlayers,
            maxPlayers,
            totalNations,
            totalTerritories,
            totalCities,
            activeWars,
            uptime,
            tps,
            memoryUsage
        );

        sendSuccess(exchange, stats, ApiResponse.ResponseMeta.of(generateRequestId()));
    }

    /**
     * 计算服务器 TPS
     */
    private double calculateTps() {
        try {
            // 尝试获取服务器 TPS
            Object server = Bukkit.getServer();
            // Paper/Bukkit API 没有标准方法获取 TPS，使用默认值
            // 实际实现可能需要使用反射或依赖特定 API
            return 20.0; // 默认 20 TPS
        } catch (Exception e) {
            return 20.0;
        }
    }
}
