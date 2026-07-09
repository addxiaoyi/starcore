package dev.starcore.starcore.api.v1.endpoint;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import dev.starcore.starcore.api.v1.ApiResponse;
import dev.starcore.starcore.api.v1.PageRequest;
import dev.starcore.starcore.api.v1.PageResponse;
import dev.starcore.starcore.api.v1.auth.ApiAuthContext;
import dev.starcore.starcore.api.v1.dto.NationDto;
import dev.starcore.starcore.api.v1.handler.BaseHttpHandler;
import dev.starcore.starcore.api.v1.websocket.WebSocketConnectionManager;
import dev.starcore.starcore.api.v1.auth.ApiAuthService;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.nation.model.NationMember;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 国家 REST API 端点
 */
public final class NationEndpoint extends BaseHttpHandler {

    private final NationService nationService;

    public NationEndpoint(
        Gson gson,
        ApiAuthService authService,
        WebSocketConnectionManager wsManager,
        NationService nationService
    ) {
        super(gson, authService, wsManager);
        this.nationService = nationService;
    }

    @Override
    protected void handleRequest(HttpExchange exchange, ApiAuthContext authContext) throws IOException {
        String path = getPath(exchange);

        // 路由
        if (path.equals("/api/v1/nations")) {
            if (isMethod(exchange, "GET")) {
                handleListNations(exchange, authContext);
            } else {
                sendMethodNotAllowed(exchange);
            }
        } else if (path.startsWith("/api/v1/nations/")) {
            String idPart = path.substring("/api/v1/nations/".length());
            if (idPart.isEmpty() || idPart.endsWith("/")) {
                sendNotFound(exchange, "Nation");
            } else {
                handleNationById(exchange, authContext, idPart.split("/")[0]);
            }
        } else {
            sendNotFound(exchange, "Endpoint");
        }
    }

    /**
     * 获取国家列表
     */
    private void handleListNations(HttpExchange exchange, ApiAuthContext authContext) throws IOException {
        Map<String, String> params = parseQueryParams(exchange);
        PageRequest pageRequest = PageRequest.of(params.get("page"), params.get("pageSize"));

        // 获取所有国家
        List<Nation> allNations = nationService != null ? new java.util.ArrayList<>(nationService.nations()) : List.of();
        long total = allNations.size();

        // 分页
        int fromIndex = (int) Math.min(pageRequest.offset(), allNations.size());
        int toIndex = (int) Math.min(pageRequest.offset() + pageRequest.pageSize(), allNations.size());
        List<Nation> pageNations = fromIndex < toIndex ? allNations.subList(fromIndex, toIndex) : List.of();

        // 转换为 DTO
        List<NationDto> dtos = pageNations.stream()
            .map(this::toNationDto)
            .collect(Collectors.toList());

        PageResponse<NationDto> response = PageResponse.of(dtos, pageRequest.page(), pageRequest.pageSize(), total);

        sendSuccess(exchange, response, ApiResponse.ResponseMeta.of(
            pageRequest.page(),
            pageRequest.pageSize(),
            total,
            generateRequestId()
        ));
    }

    /**
     * 获取单个国家详情
     */
    private void handleNationById(HttpExchange exchange, ApiAuthContext authContext, String id) throws IOException {
        if (!isMethod(exchange, "GET")) {
            sendMethodNotAllowed(exchange);
            return;
        }

        // 验证 ID 格式
        UUID uuid;
        try {
            uuid = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            sendError(exchange, 400, "INVALID_ID", "Invalid nation ID format");
            return;
        }

        NationId nationId = new NationId(uuid);
        Optional<Nation> nationOpt = nationService != null ? nationService.nationById(nationId) : Optional.empty();

        if (nationOpt.isEmpty()) {
            sendNotFound(exchange, "Nation");
            return;
        }

        NationDto dto = toNationDto(nationOpt.get());
        sendSuccess(exchange, dto);
    }

    /**
     * 将 Nation 转换为 DTO
     */
    private NationDto toNationDto(Nation nation) {
        String founderName = null;
        if (nation.founderId() != null) {
            for (NationMember member : nation.members()) {
                if (member.playerId().equals(nation.founderId())) {
                    founderName = member.playerName();
                    break;
                }
            }
        }

        NationDto.LocationDto capitalLocation = null;
        if (nation.capitalLocation() != null) {
            var loc = nation.capitalLocation();
            capitalLocation = new NationDto.LocationDto(
                loc.getWorld().getName(),
                loc.getBlockX(),
                loc.getBlockY(),
                loc.getBlockZ()
            );
        }

        return new NationDto(
            nation.id().toString(),
            nation.name(),
            nation.kind().name().toLowerCase(),
            nation.governmentType().name().toLowerCase(),
            nation.founderId() != null ? nation.founderId().toString() : null,
            founderName,
            nation.members().size(),
            nation.territoryCount(),
            nationService != null ? nationService.levelOf(nation.id()) : 1,
            nation.experience(),
            nation.getPopulation(),
            nation.getTreasuryBalance(),
            nation.getTaxRate(),
            nation.getAllyCount(),
            nation.getWarCount(),
            nation.getActivePolicyCount(),
            nation.getUnlockedTechCount(),
            nation.foundedAt() != null ? nation.foundedAt().toEpochMilli() : 0,
            capitalLocation
        );
    }
}
