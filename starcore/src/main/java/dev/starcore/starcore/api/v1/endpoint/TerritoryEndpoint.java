package dev.starcore.starcore.api.v1.endpoint;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import dev.starcore.starcore.api.v1.ApiResponse;
import dev.starcore.starcore.api.v1.PageRequest;
import dev.starcore.starcore.api.v1.PageResponse;
import dev.starcore.starcore.api.v1.auth.ApiAuthContext;
import dev.starcore.starcore.api.v1.dto.TerritoryDto;
import dev.starcore.starcore.api.v1.handler.BaseHttpHandler;
import dev.starcore.starcore.api.v1.websocket.WebSocketConnectionManager;
import dev.starcore.starcore.api.v1.auth.ApiAuthService;
import dev.starcore.starcore.foundation.territory.TerritoryService;
import dev.starcore.starcore.foundation.territory.model.ChunkCoordinate;
import dev.starcore.starcore.foundation.territory.model.TerritoryClaim;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 领土 REST API 端点
 */
public final class TerritoryEndpoint extends BaseHttpHandler {

    private final TerritoryService territoryService;
    private final NationService nationService;

    public TerritoryEndpoint(
        Gson gson,
        ApiAuthService authService,
        WebSocketConnectionManager wsManager,
        TerritoryService territoryService,
        NationService nationService
    ) {
        super(gson, authService, wsManager);
        this.territoryService = territoryService;
        this.nationService = nationService;
    }

    @Override
    protected void handleRequest(HttpExchange exchange, ApiAuthContext authContext) throws IOException {
        String path = getPath(exchange);

        // 路由
        if (path.equals("/api/v1/territories")) {
            if (isMethod(exchange, "GET")) {
                handleListTerritories(exchange, authContext);
            } else {
                sendMethodNotAllowed(exchange);
            }
        } else if (path.startsWith("/api/v1/territories/")) {
            String idPart = path.substring("/api/v1/territories/".length());
            if (idPart.isEmpty() || idPart.endsWith("/")) {
                sendNotFound(exchange, "Territory");
            } else {
                handleTerritoryById(exchange, authContext, idPart.split("/"));
            }
        } else {
            sendNotFound(exchange, "Endpoint");
        }
    }

    /**
     * 获取领土列表
     */
    private void handleListTerritories(HttpExchange exchange, ApiAuthContext authContext) throws IOException {
        Map<String, String> params = parseQueryParams(exchange);
        PageRequest pageRequest = PageRequest.of(params.get("page"), params.get("pageSize"));

        // 获取世界过滤参数
        String worldFilter = params.get("world");
        String nationIdFilter = params.get("nationId");

        // 获取所有领土
        List<TerritoryDto> allTerritories = collectTerritories(worldFilter, nationIdFilter);
        long total = allTerritories.size();

        // 分页
        int fromIndex = (int) Math.min(pageRequest.offset(), allTerritories.size());
        int toIndex = (int) Math.min(pageRequest.offset() + pageRequest.pageSize(), allTerritories.size());
        List<TerritoryDto> pageTerritories = fromIndex < toIndex ? allTerritories.subList(fromIndex, toIndex) : List.of();

        PageResponse<TerritoryDto> response = PageResponse.of(pageTerritories, pageRequest.page(), pageRequest.pageSize(), total);

        sendSuccess(exchange, response, ApiResponse.ResponseMeta.of(
            pageRequest.page(),
            pageRequest.pageSize(),
            total,
            generateRequestId()
        ));
    }

    /**
     * 获取单个领土详情
     */
    private void handleTerritoryById(HttpExchange exchange, ApiAuthContext authContext, String[] pathParts) throws IOException {
        if (!isMethod(exchange, "GET")) {
            sendMethodNotAllowed(exchange);
            return;
        }

        if (pathParts.length < 3) {
            sendError(exchange, 400, "INVALID_PATH", "Territory ID should be: /api/v1/territories/{world}/{x}/{z}");
            return;
        }

        String world = pathParts[0];
        int x, z;
        try {
            x = Integer.parseInt(pathParts[1]);
            z = Integer.parseInt(pathParts[2]);
        } catch (NumberFormatException e) {
            sendError(exchange, 400, "INVALID_COORDINATES", "Invalid coordinate format");
            return;
        }

        ChunkCoordinate coord = new ChunkCoordinate(world, x, z);
        Optional<TerritoryClaim> claimOpt = territoryService != null ? territoryService.claimAt(coord) : Optional.<TerritoryClaim>empty();

        if (claimOpt.isEmpty()) {
            sendNotFound(exchange, "Territory");
            return;
        }

        TerritoryClaim claim = claimOpt.get();
        TerritoryDto dto = toTerritoryDto(claim);
        sendSuccess(exchange, dto);
    }

    /**
     * 收集所有领土
     */
    private List<TerritoryDto> collectTerritories(String worldFilter, String nationIdFilter) {
        List<TerritoryDto> territories = new ArrayList<>();

        if (nationService == null || territoryService == null) {
            return territories;
        }

        for (Nation nation : nationService.nations()) {
            // 按国家过滤
            if (nationIdFilter != null && !nationIdFilter.isBlank()) {
                if (!nation.id().toString().equals(nationIdFilter)) {
                    continue;
                }
            }

            // 获取该国家的所有领土
            Collection<TerritoryClaim> claims = territoryService.claimsByOwner(nation.id().toString());

            for (TerritoryClaim claim : claims) {
                // 按世界过滤
                if (worldFilter != null && !worldFilter.isBlank()) {
                    if (!claim.coordinate().world().equals(worldFilter)) {
                        continue;
                    }
                }

                territories.add(toTerritoryDto(claim, nation));
            }
        }

        return territories;
    }

    /**
     * 将 TerritoryClaim 转换为 DTO（带国家信息）
     */
    private TerritoryDto toTerritoryDto(TerritoryClaim claim, Nation nation) {
        return new TerritoryDto(
            claim.coordinate().world(),
            claim.coordinate().x(),
            claim.coordinate().z(),
            nation.id().toString(),
            nation.name(),
            claim.ownerId(),
            0, // claimedAt - TerritoryClaim 不存储此信息
            false
        );
    }

    /**
     * 将 TerritoryClaim 转换为 DTO（无国家信息）
     */
    private TerritoryDto toTerritoryDto(TerritoryClaim claim) {
        return new TerritoryDto(
            claim.coordinate().world(),
            claim.coordinate().x(),
            claim.coordinate().z(),
            "",
            "", // 无国家信息
            claim.ownerId(),
            0, // claimedAt - TerritoryClaim 不存储此信息
            false
        );
    }
}
