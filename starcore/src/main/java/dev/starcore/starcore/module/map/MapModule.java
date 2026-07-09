package dev.starcore.starcore.module.map;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.module.ModuleLayer;
import dev.starcore.starcore.core.module.ModuleMetadata;
import dev.starcore.starcore.core.module.StarCoreModule;
import dev.starcore.starcore.foundation.feedback.BukkitInGameFeedbackService;
import dev.starcore.starcore.foundation.message.MessageService;
import dev.starcore.starcore.foundation.player.OnlinePlayerDirectory;
import dev.starcore.starcore.foundation.player.PlayerProfileService;
import dev.starcore.starcore.foundation.territory.TerritoryService;
import dev.starcore.starcore.foundation.territory.model.ChunkCoordinate;
import dev.starcore.starcore.foundation.territory.model.ChunkClaimSelection;
import dev.starcore.starcore.foundation.territory.model.TerritoryClaim;
import dev.starcore.starcore.module.diplomacy.DiplomacyRelation;
import dev.starcore.starcore.module.diplomacy.DiplomacyService;
import dev.starcore.starcore.module.event.EventService;
import dev.starcore.starcore.module.event.LedgerCategoryService;
import dev.starcore.starcore.module.event.NationEventRecord;
import dev.starcore.starcore.module.map.model.MapLayerSnapshot;
import dev.starcore.starcore.module.map.model.MapLayerType;
import dev.starcore.starcore.module.map.model.MapMarker;
import dev.starcore.starcore.module.map.model.MapSnapshot;
import dev.starcore.starcore.module.map.model.MapTerritoryPolygon;
import dev.starcore.starcore.module.map.model.WebClaimConfirmationResult;
import dev.starcore.starcore.module.nation.NationOperationalOverview;
import dev.starcore.starcore.module.nation.NationOperationalSupport;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.ClaimSelectionExplanation;
import dev.starcore.starcore.module.nation.model.ClaimSelectionPreview;
import dev.starcore.starcore.module.nation.model.ClaimSelectionReason;
import dev.starcore.starcore.module.nation.model.ClaimSelectionResult;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.nation.resource.NationResourceDistrictCommandSupport;
import dev.starcore.starcore.module.nation.resource.NationResourceDistrictOperationalOverview;
import dev.starcore.starcore.module.nation.resource.NationResourceDistrictOperationalSupport;
import dev.starcore.starcore.module.nation.resource.NationResourceDistrictService;
import dev.starcore.starcore.module.nation.resource.NationResourceDistrictSnapshot;
import dev.starcore.starcore.module.nation.resource.NationResourceDistrictViewSupport;
import dev.starcore.starcore.module.officer.OfficerService;
import dev.starcore.starcore.module.treasury.TreasuryService;
import org.bukkit.Bukkit;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.world.StructureGrowEvent;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

public final class MapModule implements StarCoreModule, MapService, Listener {
    private static final String BUNDLED_WEB_ROOT = "web/map/";
    private static final int DEFAULT_TERRAIN_TILE_PIXELS = 256;
    private static final int TERRAIN_EMPTY_COLOR = 0xffd8ddd2;
    private static final List<String> BUNDLED_WEB_ASSETS = List.of(
        BUNDLED_WEB_ROOT + "index.html",
        BUNDLED_WEB_ROOT + "js/map.js",
        BUNDLED_WEB_ROOT + "css/styles.css",
        BUNDLED_WEB_ROOT + "vendor/leaflet/leaflet.css",
        BUNDLED_WEB_ROOT + "vendor/leaflet/leaflet.js",
        BUNDLED_WEB_ROOT + "vendor/leaflet/images/marker-icon.png",
        BUNDLED_WEB_ROOT + "vendor/leaflet/images/marker-icon-2x.png",
        BUNDLED_WEB_ROOT + "vendor/leaflet/images/marker-shadow.png",
        BUNDLED_WEB_ROOT + "vendor/gsap/gsap.min.js"
    );
    private static final ModuleMetadata METADATA = new ModuleMetadata(
        "map",
        "战略地图核心",
        ModuleLayer.MODULE,
        List.of("nation"),
        List.of(MapService.class),
        "Provides internal strategic map layers for territory, players, and future web rendering."
    );

    private StarCoreContext context;
    private NationService nationService;
    private NationResourceDistrictService resourceDistrictService;
    private DiplomacyService diplomacyService;
    private EventService eventService;
    private LedgerCategoryService ledgerCategories;
    private MapEventLogEndpoint eventLogEndpoint;
    private MapFinanceEndpoint financeEndpoint;
    private TerrainTileEndpoint terrainEndpoint;
    private final TerrainWorldMetadataService terrainMetadata = new TerrainWorldMetadataService(Bukkit::getWorld, this::rememberTerrainWorld);
    private final MapAvatarEndpoint avatarEndpoint = new MapAvatarEndpoint();
    private TreasuryService treasuryService;
    private OfficerService officerService;
    private TerritoryService territoryService;
    private PlayerProfileService playerProfileService;
    private OnlinePlayerDirectory onlinePlayerDirectory;
    private MessageService messages;
    private final MapWebServer webServer = new MapWebServer();
    private final MapAccessManager accessManager = new MapAccessManager();
    private final MapSseBroadcaster sseBroadcaster = new MapSseBroadcaster();
    private final TerrainTilePrewarmService terrainPrewarmService = new TerrainTilePrewarmService();
    private final MapSnapshotJsonWriter snapshotJsonWriter = new MapSnapshotJsonWriter();
    private final MapViewerJsonWriter viewerJsonWriter = new MapViewerJsonWriter();
    private TerrainTileService terrainTiles = new TerrainTileService(
        Path.of("."),
        new TerrainTileInvalidationService(List.of(), 0),
        this::renderTerrainTileRaster,
        this::dispatchTerrainTileRender
    );
    private final MapClaimEndpoint claimEndpoint = new MapClaimEndpoint();
    private final MapResourceDistrictEndpoint resourceDistrictEndpoint = new MapResourceDistrictEndpoint();
    private volatile MapSnapshot latestSnapshot;
    private volatile String latestSnapshotJson;
    private volatile String latestHealthJson;
    private volatile Set<String> latestNationIds = Set.of();
    private volatile Map<UUID, String> latestNationMembership = Map.of();
    private volatile Map<String, Map<String, String>> latestDiplomacyRelations = Map.of();
    private int terrainTilePixels = DEFAULT_TERRAIN_TILE_PIXELS;
    private int mapRefreshTaskId = -1;
    private int avatarCleanupTaskId = -1;
    private int terrainPrewarmBootstrapTaskId = -1;
    private int terrainPrewarmTaskId = -1;

    @Override
    public ModuleMetadata metadata() {
        return METADATA;
    }

    @Override
    public void enable(StarCoreContext context) {
        context.persistenceService().ensureNamespace(metadata().id());
        this.context = context;
        this.terrainTilePixels = context.configuration().mapTerrainTilePixels();
        this.terrainEndpoint = null;
        this.terrainTiles = new TerrainTileService(
            context.plugin().getDataFolder().toPath(),
            new TerrainTileInvalidationService(
                context.configuration().mapTerrainDirtyTileSizes(),
                context.configuration().mapTerrainDirtyMaxEntries()
            ),
            this::renderTerrainTileRaster,
            this::dispatchTerrainTileRender
        );
        this.nationService = context.serviceRegistry().require(NationService.class);
        this.resourceDistrictService = context.serviceRegistry().find(NationResourceDistrictService.class).orElse(null);
        this.diplomacyService = context.serviceRegistry().find(DiplomacyService.class).orElse(null);
        this.treasuryService = context.serviceRegistry().find(TreasuryService.class).orElse(null);
        this.ledgerCategories = new LedgerCategoryService(context.configuration());
        this.territoryService = context.serviceRegistry().require(TerritoryService.class);
        this.playerProfileService = context.serviceRegistry().require(PlayerProfileService.class);
        this.onlinePlayerDirectory = context.serviceRegistry().require(OnlinePlayerDirectory.class);
        this.messages = context.serviceRegistry().require(MessageService.class);

        // 注册 MapService 到 ServiceRegistry
        context.serviceRegistry().register(MapService.class, this);

        try {
            refreshCachedState();
            exportStaticSite();
            startWebServer();
            startMapRefresh();
            startAvatarCleanup();
            startTerrainPrewarm();
            startTerrainChangeTracking();
        } catch (IOException exception) {
            context.plugin().getLogger().warning("Failed to prepare map web output: " + exception.getMessage());
        }
    }

    @Override
    public void disable(StarCoreContext context) {
        stopMapRefresh();
        stopAvatarCleanup();
        stopTerrainPrewarm();
        stopTerrainChangeTracking();
        sseBroadcaster.closeAll();
        terrainTiles.clear();
        claimEndpoint.clear();
        this.terrainEndpoint = null;
        webServer.stop();
    }

    @Override
    public MapSnapshot snapshot() {
        if (Bukkit.isPrimaryThread()) {
            return refreshCachedState();
        }
        return latestSnapshot != null ? latestSnapshot : new MapSnapshot(Instant.EPOCH, List.of());
    }

    @Override
    public String summary() {
        MapSnapshot snapshot = snapshot();
        int territoryCount = snapshot.layers().stream().mapToInt(layer -> layer.territories().size()).sum();
        int playerMarkers = markerCount(snapshot, MapLayerType.PLAYER_MARKERS);
        int resourceDistricts = markerCount(snapshot, MapLayerType.RESOURCE_DISTRICTS);
        return territoryCount + " territory polygon(s), " + playerMarkers + " player marker(s), " + resourceDistricts + " resource district marker(s)";
    }

    @Override
    public Path exportStaticSite() throws IOException {
        MapSnapshot snapshot = snapshot();
        Path exportDirectory = resolveExportDirectory();
        Files.createDirectories(exportDirectory);
        copyBundledWebAssets(exportDirectory);

        String json = latestSnapshotJson != null ? latestSnapshotJson : toJson(publicSnapshot(snapshot), Set.of(), MapViewerAccess.publicView());
        Files.writeString(exportDirectory.resolve("snapshot.js"), "window.STARCORE_SNAPSHOT = " + json + ";\n", StandardCharsets.UTF_8);
        Files.writeString(exportDirectory.resolve("snapshot.json"), json + "\n", StandardCharsets.UTF_8);
        return exportDirectory.resolve("index.html");
    }

    private void copyBundledWebAssets(Path exportDirectory) throws IOException {
        for (String resourcePath : BUNDLED_WEB_ASSETS) {
            String relativePath = resourcePath.substring(BUNDLED_WEB_ROOT.length());
            copyBundledWebAsset(resourcePath, exportDirectory.resolve(relativePath));
        }
    }

    private void copyBundledWebAsset(String resourcePath, Path targetPath) throws IOException {
        try (InputStream input = MapModule.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw new IOException("Missing bundled map asset: " + resourcePath);
            }
            Path parent = targetPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.copy(input, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Override
    public Optional<String> webAddress() {
        if (!context.configuration().mapWebEnabled()) {
            return Optional.empty();
        }
        String publicUrl = context.configuration().mapWebPublicUrl();
        if (publicUrl != null && !publicUrl.isBlank()) {
            return Optional.of(normalizeBaseUrl(publicUrl));
        }
        return Optional.of(normalizeBaseUrl("http://" + context.configuration().mapWebHost() + ':' + context.configuration().mapWebPort()));
    }

    @Override
    public Optional<String> viewerWebAddress(UUID viewerId, boolean fullAccess) {
        Optional<String> base = webAddress();
        if (base.isEmpty() || viewerId == null || !mapAccessSecretConfigured()) {
            return Optional.empty();
        }
        String access = fullAccess ? "full" : "allied";
        long expiresAtEpochSecond = Instant.now()
            .plus(Duration.ofMinutes(context.configuration().mapWebAccessTtlMinutes()))
            .getEpochSecond();
        String signature = accessManager.viewerSignature(viewerId, fullAccess, expiresAtEpochSecond, context.configuration().mapWebAccessSecret());
        return Optional.of(base.get() + "?viewer=" + viewerId + "&access=" + access + "&exp=" + expiresAtEpochSecond + "&sig=" + signature);
    }

    @Override
    public Optional<String> bindViewerWebAddress(UUID viewerId, boolean fullAccess, String remoteAddress) {
        Optional<String> base = webAddress();
        if (base.isEmpty() || viewerId == null) {
            return Optional.empty();
        }
        long expiresAtEpochSecond = Instant.now()
            .plus(Duration.ofMinutes(context.configuration().mapWebIpAccessTtlMinutes()))
            .getEpochSecond();
        return accessManager.bindViewerAccess(viewerId, fullAccess, remoteAddress, expiresAtEpochSecond) ? base : Optional.empty();
    }

    private void startWebServer() throws IOException {
        boolean started = webServer.start(
            new MapWebServer.Settings(
                context.configuration().mapWebEnabled(),
                context.configuration().mapWebHost(),
                context.configuration().mapWebPort(),
                resolveExportDirectory()
            ),
            new MapWebServer.Routes(
                this::handleSnapshotRequest,
                this::handleHealthRequest,
                this::handleSse,
                this::handleAvatarProxy,
                this::handleTerrainTile,
                this::handleTerrainTileData,
                this::handleTerrainTileBinary,
                this::handleClaimPreview,
                this::handleClaimRequest,
                this::handleResourceDistrictMigrationRequest,
                this::handleEventLogRequest,
                this::handleFinanceEventsRequest,
                this::handleNationsRequest,
                this::handleNationByIdRequest,
                this::handleTerritoriesRequest,
                this::handleTerritoryByIdRequest,
                this::handleCitiesRequest,
                this::handleCityByIdRequest,
                this::handlePlayersRequest,
                this::handlePlayerByIdRequest,
                this::handleWebSocketRequest
            )
        );
        if (!started) {
            return;
        }
        context.plugin().getLogger().info("STARCORE map web listening on " + context.configuration().mapWebHost() + ':' + context.configuration().mapWebPort()
            + " | public URL " + webAddress().orElse("unavailable"));
        if (!mapAccessSecretConfigured()) {
            context.plugin().getLogger().warning("STARCORE map personal links are disabled until map.web.access-secret is changed from the default placeholder.");
        }
    }

    private void handleTerrainTileData(HttpExchange exchange) throws IOException {
        handleTerrainTileRequest(exchange, TerrainTileEndpoint.Format.DATA);
    }

    private void handleTerrainTileBinary(HttpExchange exchange) throws IOException {
        handleTerrainTileRequest(exchange, TerrainTileEndpoint.Format.BINARY);
    }

    private void handleTerrainTile(HttpExchange exchange) throws IOException {
        handleTerrainTileRequest(exchange, TerrainTileEndpoint.Format.PNG);
    }

    private void handleTerrainTileRequest(HttpExchange exchange, TerrainTileEndpoint.Format format) throws IOException {
        if (handleCorsPreflight(exchange)) {
            return;
        }
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
            return;
        }
        if (!context.configuration().mapTerrainEnabled()) {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
            return;
        }

        try {
            resolveMapViewerAccess(exchange);
        } catch (IllegalArgumentException exception) {
            writeText(exchange, 403, "text/plain; charset=utf-8", exception.getMessage());
            return;
        }

        Map<String, String> params = MapHttpRequestParser.query(exchange.getRequestURI());
        TerrainTileEndpoint.Response response = terrainEndpoint().response(format, params);
        writeBytes(exchange, response.body(), response.contentType(), response.status(), response.maxAgeSeconds());
    }

    private String normalizeBaseUrl(String baseUrl) {
        String normalized = baseUrl.trim();
        return normalized.endsWith("/") ? normalized : normalized + '/';
    }

    private void handleSnapshotRequest(HttpExchange exchange) throws IOException {
        if (handleCorsPreflight(exchange)) {
            return;
        }
        try {
            writeJson(exchange, snapshotJson(resolveMapViewerAccess(exchange)));
        } catch (IllegalArgumentException exception) {
            writeText(exchange, 403, "text/plain; charset=utf-8", exception.getMessage());
        }
    }

    private void handleHealthRequest(HttpExchange exchange) throws IOException {
        if (handleCorsPreflight(exchange)) {
            return;
        }
        try {
            writeJson(exchange, healthJson(resolveMapViewerAccess(exchange)));
        } catch (IllegalArgumentException exception) {
            writeText(exchange, 403, "text/plain; charset=utf-8", exception.getMessage());
        }
    }

    private void handleSse(HttpExchange exchange) throws IOException {
        if (handleCorsPreflight(exchange)) {
            return;
        }
        if (!context.configuration().mapSseEnabled()) {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
            return;
        }
        MapViewerAccess access;
        try {
            access = resolveMapViewerAccess(exchange);
        } catch (IllegalArgumentException exception) {
            writeText(exchange, 403, "text/plain; charset=utf-8", exception.getMessage());
            return;
        }
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "text/event-stream; charset=utf-8");
        headers.set("Cache-Control", "no-store, no-cache, must-revalidate");
        headers.set("Connection", "keep-alive");
        applyCorsHeaders(exchange);
        exchange.sendResponseHeaders(200, 0);
        sseBroadcaster.register(exchange, access, snapshotJson(access));
    }

    private void handleAvatarProxy(HttpExchange exchange) throws IOException {
        if (handleCorsPreflight(exchange)) {
            return;
        }
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
            return;
        }

        MapAvatarEndpoint.Response response = avatarEndpoint.response(MapHttpRequestParser.query(exchange.getRequestURI()), avatarSettings());
        writeBytes(exchange, response.body(), response.contentType(), response.status(), response.maxAgeSeconds());
    }

    private int intParam(Map<String, String> params, String name) {
        String value = params.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing " + name);
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid " + name);
        }
    }

    private void handleClaimPreview(HttpExchange exchange) throws IOException {
        handleClaimSelection(exchange, false);
    }

    private void handleClaimRequest(HttpExchange exchange) throws IOException {
        handleClaimSelection(exchange, true);
    }

    private void handleResourceDistrictMigrationRequest(HttpExchange exchange) throws IOException {
        if (handleCorsPreflight(exchange)) {
            return;
        }
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            writeJson(exchange, 405, errorJson("method-not-allowed", msg("command.map.web-error-method-not-allowed")));
            return;
        }
        if (!context.configuration().mapWebResourceDistrictManagementEnabled()) {
            writeJson(exchange, 404, errorJson("disabled", msg("command.map.web-resource-district-disabled")));
            return;
        }
        if (resourceDistrictService == null) {
            writeJson(exchange, 404, errorJson("disabled", msg("command.map.web-resource-district-disabled")));
            return;
        }
        MapViewerAccess access;
        try {
            access = resolveMapViewerAccess(exchange);
        } catch (IllegalArgumentException exception) {
            writeJson(exchange, 403, errorJson("access-denied", exception.getMessage()));
            return;
        }
        if (access.isPublic()) {
            writeJson(exchange, 403, errorJson("login-required", msg("command.map.login-required")));
            return;
        }
        try {
            Map<String, String> params = MapHttpRequestParser.requestParams(exchange);
            UUID districtId = resourceDistrictIdFromParams(params);
            ClaimHttpResponse response = callSync(() -> buildResourceDistrictMigrationResponse(access.viewerId(), districtId));
            writeJson(exchange, response.status(), response.json());
        } catch (IllegalArgumentException exception) {
            writeJson(exchange, 400, errorJson("bad-request", exception.getMessage()));
        } catch (RuntimeException exception) {
            writeJson(exchange, 500, errorJson("server-error", exception.getMessage()));
        }
    }

    private void handleFinanceEventsRequest(HttpExchange exchange) throws IOException {
        if (handleCorsPreflight(exchange)) {
            return;
        }
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            writeJson(exchange, 405, errorJson("method-not-allowed", msg("command.map.web-error-method-not-allowed")));
            return;
        }
        MapViewerAccess access;
        try {
            access = resolveMapViewerAccess(exchange);
        } catch (IllegalArgumentException exception) {
            writeJson(exchange, 403, errorJson("access-denied", exception.getMessage()));
            return;
        }
        if (access.isPublic()) {
            writeJson(exchange, 403, errorJson("login-required", msg("command.map.login-required")));
            return;
        }
        try {
            ClaimHttpResponse response = callSync(() -> buildFinanceEventsResponse(access, MapHttpRequestParser.query(exchange.getRequestURI())));
            writeClaimResponse(exchange, response);
        } catch (IllegalArgumentException exception) {
            writeJson(exchange, 400, errorJson("bad-request", exception.getMessage()));
        } catch (RuntimeException exception) {
            writeJson(exchange, 500, errorJson("server-error", exception.getMessage()));
        }
    }

    private void handleEventLogRequest(HttpExchange exchange) throws IOException {
        if (handleCorsPreflight(exchange)) {
            return;
        }
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            writeJson(exchange, 405, errorJson("method-not-allowed", msg("command.map.web-error-method-not-allowed")));
            return;
        }
        MapViewerAccess access;
        try {
            access = resolveMapViewerAccess(exchange);
        } catch (IllegalArgumentException exception) {
            writeJson(exchange, 403, errorJson("access-denied", exception.getMessage()));
            return;
        }
        if (access.isPublic()) {
            writeJson(exchange, 403, errorJson("login-required", msg("command.map.login-required")));
            return;
        }
        try {
            ClaimHttpResponse response = callSync(() -> buildEventLogResponse(access, MapHttpRequestParser.query(exchange.getRequestURI())));
            writeClaimResponse(exchange, response);
        } catch (IllegalArgumentException exception) {
            writeJson(exchange, 400, errorJson("bad-request", exception.getMessage()));
        } catch (RuntimeException exception) {
            writeJson(exchange, 500, errorJson("server-error", exception.getMessage()));
        }
    }

    private void handleClaimSelection(HttpExchange exchange, boolean submitRequest) throws IOException {
        if (handleCorsPreflight(exchange)) {
            return;
        }
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            writeJson(exchange, 405, errorJson("method-not-allowed", msg("command.map.web-error-method-not-allowed")));
            return;
        }
        if (!context.configuration().mapWebClaimSelectionEnabled()) {
            writeJson(exchange, 404, errorJson("disabled", msg("command.map.web-claim-disabled")));
            return;
        }
        MapViewerAccess access;
        try {
            access = resolveMapViewerAccess(exchange);
        } catch (IllegalArgumentException exception) {
            writeJson(exchange, 403, errorJson("access-denied", exception.getMessage()));
            return;
        }
        if (access.isPublic()) {
            writeJson(exchange, 403, errorJson("login-required", msg("command.map.login-required")));
            return;
        }

        try {
            Map<String, String> params = MapHttpRequestParser.requestParams(exchange);
            MapClaimEndpoint.Response response = callSync(() -> claimEndpoint.response(access.viewerId(), params, submitRequest, claimSettings()));
            writeJson(exchange, response.status(), response.json());
        } catch (IllegalArgumentException exception) {
            writeJson(exchange, 400, errorJson("bad-request", exception.getMessage()));
        } catch (RuntimeException exception) {
            writeJson(exchange, 500, errorJson("server-error", exception.getMessage()));
        }
    }

    private void handleNationsRequest(HttpExchange exchange) throws IOException {
        if (handleCorsPreflight(exchange)) {
            return;
        }
        MapViewerAccess access;
        try {
            access = resolveMapViewerAccess(exchange);
        } catch (IllegalArgumentException exception) {
            writeJson(exchange, 403, errorJson("access-denied", exception.getMessage()));
            return;
        }
        try {
            StringBuilder json = new StringBuilder();
            json.append("{\"nations\":[");
            boolean first = true;
            for (Nation nation : nationService.nations()) {
                if (!first) json.append(",");
                first = false;
                json.append(nationJson(nation, access));
            }
            json.append("]}");
            writeJson(exchange, json.toString());
        } catch (RuntimeException exception) {
            writeJson(exchange, 500, errorJson("server-error", exception.getMessage()));
        }
    }

    private void handleNationByIdRequest(HttpExchange exchange) throws IOException {
        if (handleCorsPreflight(exchange)) {
            return;
        }
        MapViewerAccess access;
        try {
            access = resolveMapViewerAccess(exchange);
        } catch (IllegalArgumentException exception) {
            writeJson(exchange, 403, errorJson("access-denied", exception.getMessage()));
            return;
        }
        String path = exchange.getRequestURI().getPath();
        String nationIdStr = path.substring("/api/nations/".length());
        try {
            UUID nationId = UUID.fromString(nationIdStr);
            NationId id = new NationId(nationId);
            Optional<Nation> nationOpt = nationService.nationById(id);
            if (nationOpt.isEmpty()) {
                writeJson(exchange, 404, errorJson("not-found", msg("command.nation.nation-not-found")));
                return;
            }
            writeJson(exchange, nationJson(nationOpt.get(), access));
        } catch (IllegalArgumentException exception) {
            writeJson(exchange, 400, errorJson("invalid-id", "Invalid nation ID format"));
        } catch (RuntimeException exception) {
            writeJson(exchange, 500, errorJson("server-error", exception.getMessage()));
        }
    }

    private void handleTerritoriesRequest(HttpExchange exchange) throws IOException {
        if (handleCorsPreflight(exchange)) {
            return;
        }
        MapViewerAccess access;
        try {
            access = resolveMapViewerAccess(exchange);
        } catch (IllegalArgumentException exception) {
            writeJson(exchange, 403, errorJson("access-denied", exception.getMessage()));
            return;
        }
        try {
            StringBuilder json = new StringBuilder();
            json.append("{\"territories\":[");
            boolean first = true;
            for (Nation nation : nationService.nations()) {
                for (TerritoryClaim claim : territoryService.claimsByOwner(nation.id().toString())) {
                    if (!first) json.append(",");
                    first = false;
                    json.append(territoryJson(nation, claim, access));
                }
            }
            json.append("]}");
            writeJson(exchange, json.toString());
        } catch (RuntimeException exception) {
            writeJson(exchange, 500, errorJson("server-error", exception.getMessage()));
        }
    }

    private void handleTerritoryByIdRequest(HttpExchange exchange) throws IOException {
        if (handleCorsPreflight(exchange)) {
            return;
        }
        MapViewerAccess access;
        try {
            access = resolveMapViewerAccess(exchange);
        } catch (IllegalArgumentException exception) {
            writeJson(exchange, 403, errorJson("access-denied", exception.getMessage()));
            return;
        }
        String path = exchange.getRequestURI().getPath();
        String[] parts = path.substring("/api/territories/".length()).split("/");
        try {
            String world = parts[0];
            int x = Integer.parseInt(parts[1]);
            int z = Integer.parseInt(parts[2]);
            Optional<TerritoryClaim> claimOpt = territoryService.claimAt(new ChunkCoordinate(world, x, z));
            if (claimOpt.isEmpty()) {
                writeJson(exchange, 404, errorJson("not-found", "Territory not found"));
                return;
            }
            TerritoryClaim claim = claimOpt.get();
            Optional<Nation> nationOpt = nationService.nationById(parseNationId(claim.ownerId()));
            writeJson(exchange, nationOpt.map(n -> territoryJson(n, claim, access)).orElse("{}"));
        } catch (Exception exception) {
            writeJson(exchange, 400, errorJson("invalid-request", exception.getMessage()));
        }
    }

    private void handleCitiesRequest(HttpExchange exchange) throws IOException {
        if (handleCorsPreflight(exchange)) {
            return;
        }
        MapViewerAccess access;
        try {
            access = resolveMapViewerAccess(exchange);
        } catch (IllegalArgumentException exception) {
            writeJson(exchange, 403, errorJson("access-denied", exception.getMessage()));
            return;
        }
        try {
            StringBuilder json = new StringBuilder();
            json.append("{\"cities\":[");
            boolean first = true;
            for (Nation nation : nationService.nations()) {
                if (nation.parentNationId() != null) {
                    if (!first) json.append(",");
                    first = false;
                    json.append(cityJson(nation, access));
                }
            }
            json.append("]}");
            writeJson(exchange, json.toString());
        } catch (RuntimeException exception) {
            writeJson(exchange, 500, errorJson("server-error", exception.getMessage()));
        }
    }

    private void handleCityByIdRequest(HttpExchange exchange) throws IOException {
        if (handleCorsPreflight(exchange)) {
            return;
        }
        MapViewerAccess access;
        try {
            access = resolveMapViewerAccess(exchange);
        } catch (IllegalArgumentException exception) {
            writeJson(exchange, 403, errorJson("access-denied", exception.getMessage()));
            return;
        }
        String path = exchange.getRequestURI().getPath();
        String cityIdStr = path.substring("/api/cities/".length());
        try {
            UUID cityId = UUID.fromString(cityIdStr);
            NationId id = new NationId(cityId);
            Optional<Nation> cityOpt = nationService.nationById(id);
            if (cityOpt.isEmpty() || cityOpt.get().parentNationId() == null) {
                writeJson(exchange, 404, errorJson("not-found", "City not found"));
                return;
            }
            writeJson(exchange, cityJson(cityOpt.get(), access));
        } catch (IllegalArgumentException exception) {
            writeJson(exchange, 400, errorJson("invalid-id", "Invalid city ID format"));
        } catch (RuntimeException exception) {
            writeJson(exchange, 500, errorJson("server-error", exception.getMessage()));
        }
    }

    private void handlePlayersRequest(HttpExchange exchange) throws IOException {
        if (handleCorsPreflight(exchange)) {
            return;
        }
        MapViewerAccess access;
        try {
            access = resolveMapViewerAccess(exchange);
        } catch (IllegalArgumentException exception) {
            writeJson(exchange, 403, errorJson("access-denied", exception.getMessage()));
            return;
        }
        try {
            StringBuilder json = new StringBuilder();
            json.append("{\"players\":[");
            boolean first = true;
            for (UUID playerId : latestNationMembership.keySet()) {
                if (!first) json.append(",");
                first = false;
                json.append(playerJson(playerId, access));
            }
            json.append("]}");
            writeJson(exchange, json.toString());
        } catch (RuntimeException exception) {
            writeJson(exchange, 500, errorJson("server-error", exception.getMessage()));
        }
    }

    private void handlePlayerByIdRequest(HttpExchange exchange) throws IOException {
        if (handleCorsPreflight(exchange)) {
            return;
        }
        MapViewerAccess access;
        try {
            access = resolveMapViewerAccess(exchange);
        } catch (IllegalArgumentException exception) {
            writeJson(exchange, 403, errorJson("access-denied", exception.getMessage()));
            return;
        }
        String path = exchange.getRequestURI().getPath();
        String playerIdStr = path.substring("/api/players/".length());
        try {
            UUID playerId = UUID.fromString(playerIdStr);
            writeJson(exchange, playerJson(playerId, access));
        } catch (IllegalArgumentException exception) {
            writeJson(exchange, 400, errorJson("invalid-id", "Invalid player ID format"));
        } catch (RuntimeException exception) {
            writeJson(exchange, 500, errorJson("server-error", exception.getMessage()));
        }
    }

    private void handleWebSocketRequest(HttpExchange exchange) throws IOException {
        if (handleCorsPreflight(exchange)) {
            return;
        }
        if (!context.configuration().mapSseEnabled()) {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
            return;
        }
        MapViewerAccess access;
        try {
            access = resolveMapViewerAccess(exchange);
        } catch (IllegalArgumentException exception) {
            writeText(exchange, 403, "text/plain; charset=utf-8", exception.getMessage());
            return;
        }
        // For WebSocket, we return HTTP 426 Upgrade Required (actual WS upgrade not implemented)
        // The client should use SSE stream as fallback
        exchange.sendResponseHeaders(426, -1);
        exchange.close();
    }

    private String nationJson(Nation nation, MapViewerAccess access) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"id\":\"").append(nation.id().toString()).append("\",");
        json.append("\"name\":\"").append(escape(nation.name())).append("\",");
        json.append("\"kind\":\"").append(nation.kind().name().toLowerCase()).append("\",");
        json.append("\"government\":\"").append(nation.governmentType().name().toLowerCase()).append("\",");
        json.append("\"founderId\":\"").append(nation.founderId().toString()).append("\",");
        json.append("\"memberCount\":").append(nation.members().size()).append(",");
        json.append("\"claimCount\":").append(nationService.claimCount(nation.id())).append(",");
        json.append("\"level\":").append(nationService.levelOf(nation.id())).append(",");
        if (nation.parentNationId() != null) {
            json.append("\"parentId\":\"").append(nation.parentNationId().toString()).append("\",");
        }
        String relation = access.isPublic() ? "neutral" : relationValue(latestNationMembership.get(access.viewerId()), nation.id().toString());
        json.append("\"relation\":\"").append(relation).append("\"");
        json.append("}");
        return json.toString();
    }

    private String territoryJson(Nation nation, TerritoryClaim claim, MapViewerAccess access) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"world\":\"").append(escape(claim.coordinate().world())).append("\",");
        json.append("\"x\":").append(claim.coordinate().x()).append(",");
        json.append("\"z\":").append(claim.coordinate().z()).append(",");
        json.append("\"nationId\":\"").append(nation.id().toString()).append("\",");
        json.append("\"nationName\":\"").append(escape(nation.name())).append("\"");
        json.append("}");
        return json.toString();
    }

    private String cityJson(Nation city, MapViewerAccess access) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"id\":\"").append(city.id().toString()).append("\",");
        json.append("\"name\":\"").append(escape(city.name())).append("\",");
        json.append("\"parentId\":\"").append(city.parentNationId().toString()).append("\",");
        json.append("\"founderId\":\"").append(city.founderId().toString()).append("\",");
        json.append("\"memberCount\":").append(city.members().size()).append(",");
        json.append("\"claimCount\":").append(nationService.claimCount(city.id())).append(",");
        json.append("\"level\":").append(nationService.levelOf(city.id())).append(",");
        String relation = access.isPublic() ? "neutral" : relationValue(latestNationMembership.get(access.viewerId()), city.parentNationId().toString());
        json.append("\"relation\":\"").append(relation).append("\"");
        json.append("}");
        return json.toString();
    }

    private String playerJson(UUID playerId, MapViewerAccess access) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"id\":\"").append(playerId.toString()).append("\",");
        String playerName = playerProfileService.snapshot().getOrDefault(playerId, playerId.toString());
        json.append("\"name\":\"").append(escape(playerName)).append("\",");
        String nationId = latestNationMembership.get(playerId);
        if (nationId != null) {
            json.append("\"nationId\":\"").append(nationId).append("\",");
            Optional<Nation> nationOpt = nationService.nationById(parseNationId(nationId));
            nationOpt.ifPresent(n -> json.append("\"nationName\":\"").append(escape(n.name())).append("\","));
        }
        Player online = onlinePlayerDirectory.findOnlinePlayer(playerId).orElse(null);
        json.append("\"online\":").append(online != null && online.isOnline()).append(",");
        if (online != null && online.isOnline()) {
            json.append("\"world\":\"").append(escape(online.getWorld().getName())).append("\",");
            json.append("\"x\":").append(online.getLocation().getBlockX()).append(",");
            json.append("\"y\":").append(online.getLocation().getBlockY()).append(",");
            json.append("\"z\":").append(online.getLocation().getBlockZ()).append(",");
        }
        json.append("\"relation\":\"").append(access.isPublic() ? "neutral" : relationValue(latestNationMembership.get(access.viewerId()), nationId)).append("\"");
        json.append("}");
        return json.toString();
    }

    private ClaimHttpResponse buildFinanceEventsResponse(MapViewerAccess access, Map<String, String> params) {
        MapFinanceEndpoint.Response response = financeEndpoint().buildResponse(access, params);
        return new ClaimHttpResponse(response.status(), response.json(), response.contentType(), response.filename());
    }

    private ClaimHttpResponse buildEventLogResponse(MapViewerAccess access, Map<String, String> params) {
        MapEventLogEndpoint.Response response = eventLogEndpoint().buildResponse(access, params);
        return new ClaimHttpResponse(response.status(), response.json(), response.contentType(), response.filename());
    }

    private boolean financeNationVisible(MapViewerAccess access, NationId nationId) {
        if (access == null || nationId == null) {
            return false;
        }
        if (access.fullAccess()) {
            return true;
        }
        String viewerNationId = latestNationMembership.get(access.viewerId());
        return visibleNationIdsFor(viewerNationId).contains(nationId.toString());
    }

    private MapClaimEndpoint.Settings claimSettings() {
        return new MapClaimEndpoint.Settings(
            context.configuration().mapWebClaimMaxChunks(),
            context.configuration().maxClaimsPerNation(),
            context.configuration().mapWebClaimCooldownSeconds(),
            context.configuration().mapWebClaimPendingMinutes(),
            context.configuration().claimPricingDetailLimit(),
            context.economyService()::balance,
            nationService::previewClaimSelection,
            this::sendPendingClaimNotification,
            this::msg
        );
    }

    private void sendPendingClaimNotification(MapClaimEndpoint.PendingClaim pending) {
        Player player = onlinePlayerDirectory.findOnlinePlayer(pending.playerId()).orElse(null);
        if (player != null && player.isOnline()) {
            player.sendMessage(net.kyori.adventure.text.Component.text(msg("command.map.pending-confirm"), net.kyori.adventure.text.format.NamedTextColor.GOLD)
                .append(net.kyori.adventure.text.Component.text(msg("command.map.pending-chunks", pending.selection().chunkCount()), net.kyori.adventure.text.format.NamedTextColor.WHITE))
                .append(net.kyori.adventure.text.Component.text(msg("command.map.pending-cost", pending.price().toPlainString()), net.kyori.adventure.text.format.NamedTextColor.GRAY)));
            player.sendMessage(net.kyori.adventure.text.Component.text(msg("command.map.confirm-command"), net.kyori.adventure.text.format.NamedTextColor.YELLOW)
                .append(net.kyori.adventure.text.Component.text("/starcore map confirm " + pending.id(), net.kyori.adventure.text.format.NamedTextColor.WHITE)));
            player.sendMessage(net.kyori.adventure.text.Component.text(msg("command.map.confirm-command-zh"), net.kyori.adventure.text.format.NamedTextColor.YELLOW)
                .append(net.kyori.adventure.text.Component.text(msg("command.map.confirm-command-zh-value", pending.id()), net.kyori.adventure.text.format.NamedTextColor.WHITE)));
            player.sendMessage(net.kyori.adventure.text.Component.text(msg("command.map.cancel-command"), net.kyori.adventure.text.format.NamedTextColor.YELLOW)
                .append(net.kyori.adventure.text.Component.text("/starcore map cancel " + pending.id(), net.kyori.adventure.text.format.NamedTextColor.WHITE)));
            player.sendMessage(net.kyori.adventure.text.Component.text(msg("command.map.cancel-command-zh"), net.kyori.adventure.text.format.NamedTextColor.YELLOW)
                .append(net.kyori.adventure.text.Component.text(msg("command.map.cancel-command-zh-value", pending.id()), net.kyori.adventure.text.format.NamedTextColor.WHITE)));
            emitClaimFeedback("web-pending", player);
        }
    }

    private void emitClaimFeedback(String eventKey, Player player) {
        if (player == null || context == null || context.plugin() == null || context.configuration() == null) {
            return;
        }
        new BukkitInGameFeedbackService(context.plugin(), context.configuration()::nationClaimFeedbackProfile)
            .emit(eventKey, player, player.getLocation());
    }

    @Override
    public WebClaimConfirmationResult confirmWebClaim(UUID playerId, String pendingId) {
        return callSync(() -> confirmWebClaimSync(playerId, pendingId));
    }

    private WebClaimConfirmationResult confirmWebClaimSync(UUID playerId, String pendingId) {
        return claimEndpoint.confirm(playerId, pendingId, claimSettings(), pending -> {
            ClaimSelectionPreview preview = nationService.previewClaimSelection(playerId, pending.selection());
            if (!preview.canSubmit()) {
                return WebClaimConfirmationResult.failed(pending.id(), preview.message(), preview.explanation());
            }
            if (preview.nationId() == null || !preview.nationId().equals(pending.nationId())) {
                String message = msg("command.map.confirm-state-changed");
                return WebClaimConfirmationResult.failed(
                    pending.id(),
                    message,
                    confirmationExplanation(
                        "pending-state-changed",
                        "warning",
                        message,
                        Map.of(
                            "pendingId", pending.id(),
                            "originalNationId", pending.nationId() == null ? "" : pending.nationId().toString(),
                            "currentNationId", preview.nationId() == null ? "" : preview.nationId().toString()
                        )
                    )
                );
            }
            ClaimSelectionResult result;
            try {
                result = nationService.claimSelection(playerId, pending.selection());
            } catch (RuntimeException exception) {
                String message = exception.getMessage() == null || exception.getMessage().isBlank()
                    ? msg("command.map.confirm-state-changed")
                    : exception.getMessage();
                return WebClaimConfirmationResult.failed(
                    pending.id(),
                    message,
                    confirmationExplanation("confirm-commit-failed", "error", message, Map.of("pendingId", pending.id()))
                );
            }
            refreshCacheAndBroadcast();
            return new WebClaimConfirmationResult(
                pending.id(),
                result.nationName(),
                result.selection(),
                result.claimedChunks(),
                result.price(),
                msg("command.map.web-claim-completed")
            );
        });
    }

    @Override
    public WebClaimConfirmationResult cancelWebClaim(UUID playerId, String pendingId) {
        return callSync(() -> claimEndpoint.cancel(playerId, pendingId, claimSettings()));
    }

    private ClaimSelectionExplanation confirmationExplanation(String state, String severity, String message, Map<String, String> details) {
        return ClaimSelectionExplanation.of(
            state,
            severity,
            message,
            List.of(ClaimSelectionReason.of(state, message, details))
        );
    }

    private ClaimHttpResponse buildResourceDistrictMigrationResponse(UUID viewerId, UUID districtId) {
        NationResourceDistrictService service = resourceDistrictService;
        MapResourceDistrictEndpoint.PlayerLookup playerLookup = onlinePlayerDirectory == null
            ? ignored -> Optional.empty()
            : onlinePlayerDirectory::findOnlinePlayer;
        MapResourceDistrictEndpoint.Response response = resourceDistrictEndpoint.migrationResponse(
            viewerId,
            districtId,
            new MapResourceDistrictEndpoint.Settings(
                service != null,
                this::findResourceDistrictSnapshot,
                playerLookup,
                service == null ? null : service::beginMigration,
                nationId -> nationService == null || nationId == null ? Optional.empty() : nationService.nationById(nationId),
                this::nationOperationalOverview,
                this::resourceDistrictOperationalOverview,
                this::resourceDistrictCommandState,
                this::resourceDistrictCommandPresentation,
                this::resourceDistrictMigrationLabel,
                this::resourceDistrictBeaconPosition,
                this::resourceDistrictTimestamp,
                this::msg,
                this::refreshCacheAndBroadcast
            )
        );
        return new ClaimHttpResponse(response.status(), response.json());
    }

    private UUID resourceDistrictIdFromParams(Map<String, String> params) {
        String raw = params.get("districtId");
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException(msg("command.map.web-resource-district-missing-id"));
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(msg("command.map.web-resource-district-id-invalid"));
        }
    }

    private String errorJson(String code, String message) {
        StringBuilder builder = new StringBuilder(128);
        builder.append('{');
        appendBooleanField(builder, "ok", false);
        builder.append(',');
        appendField(builder, "code", code == null ? "error" : code);
        builder.append(',');
        appendField(builder, "message", message == null || message.isBlank() ? msg("command.map.request-failed") : message);
        builder.append(',');
        appendBooleanField(builder, "canSubmit", false);
        builder.append('}');
        return builder.toString();
    }

    private <T> T callSync(Supplier<T> supplier) {
        if (Bukkit.isPrimaryThread()) {
            return supplier.get();
        }
        CompletableFuture<T> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTask(context.plugin(), () -> {
            try {
                future.complete(supplier.get());
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        });
        try {
            return future.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(msg("command.map.sync-interrupted"), exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException(cause);
        }
    }

    private byte[] terrainTile(String worldName, int minX, int minZ, int worldSize) throws IOException {
        return terrainTiles.png(worldName, minX, minZ, worldSize, terrainTileSettings());
    }

    private byte[] terrainTileBinary(String worldName, int minX, int minZ, int worldSize) throws IOException {
        return terrainTiles.binary(worldName, minX, minZ, worldSize, terrainTileSettings());
    }

    private TerrainTileRaster terrainTileRaster(String worldName, int minX, int minZ, int worldSize) throws IOException {
        return terrainTiles.raster(worldName, minX, minZ, worldSize, terrainTileSettings());
    }

    private TerrainTileEndpoint terrainEndpoint() {
        if (terrainEndpoint == null) {
            terrainEndpoint = new TerrainTileEndpoint(
                this::terrainTile,
                this::terrainTileBinary,
                this::terrainTileRaster,
                () -> context.configuration().mapTerrainTileCacheSeconds()
            );
        }
        return terrainEndpoint;
    }

    private long terrainTileCacheTtlMillis() {
        return context.configuration().mapTerrainTileCacheSeconds() * 1000L;
    }

    private long terrainTileDiskCacheTtlMillis() {
        return Duration.ofHours(context.configuration().mapTerrainTileDiskCacheHours()).toMillis();
    }

    private TerrainTileService.Settings terrainTileSettings() {
        ExecutorService executor = webServer.executor();
        return new TerrainTileService.Settings(
            terrainTileCacheTtlMillis(),
            context.configuration().mapTerrainTileCacheMaxEntries(),
            context.configuration().mapTerrainTileDiskCacheEnabled(),
            terrainTileDiskCacheTtlMillis(),
            terrainTilePixels(),
            context.configuration().mapTerrainTileMaxConcurrentRenders(),
            executor == null || executor.isShutdown() ? null : executor
        );
    }

    private void rememberTerrainWorld(World world) {
        terrainTiles.rememberWorld(world.getName(), world.getWorldFolder().toPath());
    }

    private void dispatchTerrainTileRender(Runnable renderTask) {
        if (Bukkit.isPrimaryThread()) {
            renderTask.run();
        } else {
            Bukkit.getScheduler().runTask(context.plugin(), renderTask);
        }
    }

    private TerrainTileRaster renderTerrainTileRaster(String worldName, int minX, int minZ, int worldSize, long renderedAtMillis) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        rememberTerrainWorld(world);
        int tilePixels = terrainTilePixels();
        int[] colors = new int[tilePixels * tilePixels];
        int[] heights = new int[tilePixels * tilePixels];
        byte[] lights = new byte[tilePixels * tilePixels];
        Map<Long, ChunkSnapshot> snapshots = new HashMap<>();
        Set<Long> missingChunks = new LinkedHashSet<>();
        int minHeight = Integer.MAX_VALUE;
        int maxHeight = Integer.MIN_VALUE;
        for (int py = 0; py < tilePixels; py++) {
            int blockZ = minZ + (int) Math.floor((double) py * worldSize / tilePixels);
            int chunkZ = Math.floorDiv(blockZ, 16);
            int localZ = Math.floorMod(blockZ, 16);
            for (int px = 0; px < tilePixels; px++) {
                int blockX = minX + (int) Math.floor((double) px * worldSize / tilePixels);
                int chunkX = Math.floorDiv(blockX, 16);
                int localX = Math.floorMod(blockX, 16);
                ChunkSnapshot snapshot = terrainChunkSnapshot(world, chunkX, chunkZ, snapshots, missingChunks);
                int index = py * tilePixels + px;
                if (snapshot == null) {
                    colors[index] = terrainEmptyColor(blockX, blockZ);
                    heights[index] = Integer.MIN_VALUE;
                    lights[index] = 0;
                    continue;
                }
                TerrainSurface surface = terrainSurface(snapshot, localX, localZ, world.getMinHeight(), world.getMaxHeight(), world.getSeaLevel());
                if (surface == null) {
                    colors[index] = terrainEmptyColor(blockX, blockZ);
                    heights[index] = Integer.MIN_VALUE;
                    lights[index] = 0;
                    continue;
                }
                heights[index] = surface.y();
                minHeight = Math.min(minHeight, surface.y());
                maxHeight = Math.max(maxHeight, surface.y());
                lights[index] = (byte) Math.clamp(Math.max(surface.skyLight(), surface.emittedLight()), 0, 15);
                colors[index] = terrainColor(surface, blockX, blockZ, world.getSeaLevel());
            }
        }
        for (int py = 0; py < tilePixels; py++) {
            for (int px = 0; px < tilePixels; px++) {
                int index = py * tilePixels + px;
                int blockX = minX + (int) Math.floor((double) px * worldSize / tilePixels);
                int blockZ = minZ + (int) Math.floor((double) py * worldSize / tilePixels);
                colors[index] = detailedTerrainColor(colors[index], heights, px, py, tilePixels, blockX, blockZ);
            }
        }
        if (minHeight == Integer.MAX_VALUE) {
            minHeight = 0;
            maxHeight = 0;
        }
        return new TerrainTileRaster(worldName, minX, minZ, worldSize, tilePixels, colors, heights, lights, minHeight, maxHeight, renderedAtMillis);
    }

    private ChunkSnapshot terrainChunkSnapshot(World world, int chunkX, int chunkZ, Map<Long, ChunkSnapshot> snapshots, Set<Long> missingChunks) {
        long key = (((long) chunkX) << 32) ^ (chunkZ & 0xffffffffL);
        if (missingChunks.contains(key)) {
            return null;
        }
        ChunkSnapshot snapshot = snapshots.get(key);
        if (snapshot != null) {
            return snapshot;
        }
        if (!world.isChunkLoaded(chunkX, chunkZ)
            && (!context.configuration().mapTerrainLoadGeneratedChunks() || !terrainChunkRegionFileExists(world, chunkX, chunkZ))) {
            missingChunks.add(key);
            return null;
        }
        snapshot = world.getChunkAt(chunkX, chunkZ, false).getChunkSnapshot(true, true, false);
        snapshots.put(key, snapshot);
        return snapshot;
    }

    private boolean terrainChunkRegionFileExists(World world, int chunkX, int chunkZ) {
        rememberTerrainWorld(world);
        return terrainTiles.regionFileExists(world.getName(), chunkX, chunkZ);
    }

    private TerrainSurface terrainSurface(ChunkSnapshot snapshot, int localX, int localZ, int minHeight, int maxHeight, int seaLevel) {
        int topY = Math.clamp(snapshot.getHighestBlockYAt(localX, localZ), minHeight, maxHeight - 1);
        for (int y = topY; y >= minHeight; y--) {
            BlockData blockData = snapshot.getBlockData(localX, y, localZ);
            Material material = blockData.getMaterial();
            if (material.isAir() || shouldSkipTerrainSurface(material)) {
                continue;
            }
            Biome biome = snapshot.getBiome(localX, y, localZ);
            int waterDepth = waterDepth(snapshot, localX, y, localZ, minHeight);
            int skyLight = snapshot.getBlockSkyLight(localX, y, localZ);
            int emittedLight = snapshot.getBlockEmittedLight(localX, y, localZ);
            return new TerrainSurface(blockData, material, biome, y, waterDepth, skyLight, emittedLight);
        }
        return null;
    }

    private boolean shouldSkipTerrainSurface(Material material) {
        String name = material.name();
        return name.contains("AIR")
            || name.equals("SHORT_GRASS")
            || name.equals("TALL_GRASS")
            || name.contains("FERN")
            || name.contains("SAPLING")
            || name.endsWith("_BUSH")
            || name.contains("FLOWER")
            || name.contains("TULIP")
            || name.contains("DANDELION")
            || name.contains("POPPY")
            || name.contains("TORCH")
            || name.contains("SIGN")
            || name.contains("BANNER")
            || name.contains("PRESSURE_PLATE")
            || name.contains("BUTTON")
            || name.contains("CARPET")
            || name.contains("RAIL")
            || name.contains("TRIPWIRE")
            || name.contains("STRING")
            || name.contains("LADDER")
            || name.contains("VINE");
    }

    private int terrainColor(TerrainSurface surface, int blockX, int blockZ, int seaLevel) {
        Material material = surface.material();
        Biome biome = surface.biome();
        int y = surface.y();
        int waterDepth = surface.waterDepth();
        if (material.isAir()) {
            return TERRAIN_EMPTY_COLOR;
        }

        String name = material.name();
        int color = mapColor(surface.blockData());
        if (name.contains("WATER") || name.contains("KELP") || name.contains("SEAGRASS")) {
            color = waterColor(biome, waterDepth, y, seaLevel);
        } else if (name.contains("LAVA")) {
            color = blend(color, 0xE86B2A, 72);
        } else if (name.equals("GRASS_BLOCK") || name.contains("MOSS")) {
            color = blend(color, grassColor(biome, name), 58);
        } else if (name.contains("LEAVES")) {
            color = blend(color, leavesColor(biome, name), 52);
        } else if (name.contains("CROP") || name.contains("WHEAT") || name.contains("CARROT") || name.contains("POTATO") || name.contains("BEETROOT")) {
            color = blend(color, 0x9EA74B, 42);
        } else {
            color = enhanceMapColorFallback(color, name);
        }
        int heightShade = Math.clamp(98 + Math.floorDiv(y - seaLevel, 3), 82, 116);
        int lightShade = Math.clamp((surface.skyLight() - 12) * 3 + surface.emittedLight() * 2, -18, 18);
        return shiftShade(shadedColor(color, heightShade), materialTexture(name, blockX, blockZ) + lightShade);
    }

    private int mapColor(BlockData blockData) {
        Color color = blockData.getMapColor();
        if (color == null || color.getAlpha() <= 0) {
            return 0x7C806E;
        }
        return color.asRGB();
    }

    private int enhanceMapColorFallback(int color, String name) {
        if (name.contains("DEEPSLATE")) {
            return blend(color, 0x34343A, 36);
        }
        if (name.contains("BASALT") || name.contains("BLACKSTONE")) {
            return blend(color, 0x25262A, 42);
        }
        if (name.contains("NETHERRACK") || name.contains("CRIMSON")) {
            return blend(color, 0x743134, 34);
        }
        if (name.contains("WARPED")) {
            return blend(color, 0x287B74, 34);
        }
        return color;
    }

    private int waterDepth(ChunkSnapshot snapshot, int localX, int surfaceY, int localZ, int minHeight) {
        Material surface = snapshot.getBlockType(localX, surfaceY, localZ);
        String surfaceName = surface.name();
        if (!surfaceName.contains("WATER") && !surfaceName.contains("KELP") && !surfaceName.contains("SEAGRASS")) {
            return 0;
        }
        int depth = 0;
        for (int y = surfaceY; y >= minHeight && depth < 32; y--) {
            String name = snapshot.getBlockType(localX, y, localZ).name();
            if (!name.contains("WATER") && !name.contains("KELP") && !name.contains("SEAGRASS")) {
                break;
            }
            depth++;
        }
        return depth;
    }

    private boolean isGrassLike(String name) {
        return name.equals("GRASS_BLOCK")
            || name.equals("SHORT_GRASS")
            || name.equals("TALL_GRASS")
            || name.contains("FERN")
            || name.contains("SAPLING")
            || name.contains("AZALEA")
            || name.contains("BUSH");
    }

    private int grassColor(Biome biome, String materialName) {
        String biomeName = biome.getKey().getKey();
        int color;
        if (biomeName.contains("SWAMP") || biomeName.contains("MANGROVE")) {
            color = 0x4F7A3D;
        } else if (biomeName.contains("JUNGLE")) {
            color = 0x4E9637;
        } else if (biomeName.contains("SAVANNA")) {
            color = 0x8A8F3A;
        } else if (biomeName.contains("TAIGA") || biomeName.contains("OLD_GROWTH")) {
            color = 0x577B45;
        } else if (biomeName.contains("SNOW") || biomeName.contains("ICE") || biomeName.contains("FROZEN")) {
            color = 0x6E8B5E;
        } else if (biomeName.contains("DARK_FOREST")) {
            color = 0x3F7138;
        } else if (biomeName.contains("CHERRY")) {
            color = 0x72A657;
        } else if (biomeName.contains("BADLANDS") || biomeName.contains("DESERT")) {
            color = 0xA79B55;
        } else {
            color = 0x5F9B45;
        }
        return materialName.equals("GRASS_BLOCK") ? color : blend(color, 0x8DBB55, 34);
    }

    private int leavesColor(Biome biome, String materialName) {
        String biomeName = biome.getKey().getKey();
        int color;
        if (materialName.contains("CHERRY")) {
            color = 0xD99EB0;
        } else if (biomeName.contains("JUNGLE")) {
            color = 0x2F7D30;
        } else if (biomeName.contains("SWAMP") || biomeName.contains("MANGROVE")) {
            color = 0x3F6E36;
        } else if (biomeName.contains("TAIGA") || materialName.contains("SPRUCE")) {
            color = 0x355E3A;
        } else if (biomeName.contains("DARK_FOREST")) {
            color = 0x2E5F32;
        } else {
            color = 0x3F8240;
        }
        return materialName.contains("MOSS") ? blend(color, 0x5C8B45, 45) : color;
    }

    private int waterColor(Biome biome, int depth, int y, int seaLevel) {
        String biomeName = biome.getKey().getKey();
        int color;
        if (biomeName.contains("SWAMP") || biomeName.contains("MANGROVE")) {
            color = 0x406F64;
        } else if (biomeName.contains("FROZEN") || biomeName.contains("ICE")) {
            color = 0x6FA6C8;
        } else if (biomeName.contains("LUKEWARM") || biomeName.contains("WARM")) {
            color = 0x3E9FC5;
        } else if (biomeName.contains("OCEAN") && depth > 10) {
            color = 0x2F679A;
        } else {
            color = 0x4C89C6;
        }
        int deepening = Math.clamp(depth * 4 + Math.max(0, seaLevel - y), 0, 44);
        return shiftShade(color, -deepening);
    }

    private int flowerColor(String name) {
        if (name.contains("BLUE")) {
            return 0x5D72B8;
        }
        if (name.contains("RED") || name.contains("POPPY")) {
            return 0xB94A3E;
        }
        if (name.contains("PINK") || name.contains("TULIP")) {
            return 0xD58A9C;
        }
        if (name.contains("YELLOW") || name.contains("DANDELION") || name.contains("SUNFLOWER")) {
            return 0xD7B547;
        }
        return 0xD7D2C4;
    }

    private int materialTexture(String name, int blockX, int blockZ) {
        int noise = Math.floorMod(name.hashCode() * 31 + blockX * 7349 + blockZ * 9151, 13) - 6;
        if (name.contains("LEAVES") || name.contains("GRASS") || name.contains("MOSS")) {
            return noise * 2;
        }
        if (name.contains("SAND") || name.contains("GRAVEL") || name.contains("DIRT") || name.contains("STONE")) {
            return noise;
        }
        if (name.contains("WATER")) {
            return Math.clamp(noise, -3, 3);
        }
        return Math.clamp(noise, -4, 4);
    }

    private int blend(int color, int overlay, int overlayPercent) {
        int basePercent = 100 - overlayPercent;
        int red = (((color >> 16) & 0xff) * basePercent + ((overlay >> 16) & 0xff) * overlayPercent) / 100;
        int green = (((color >> 8) & 0xff) * basePercent + ((overlay >> 8) & 0xff) * overlayPercent) / 100;
        int blue = ((color & 0xff) * basePercent + (overlay & 0xff) * overlayPercent) / 100;
        return (red << 16) | (green << 8) | blue;
    }

    private int terrainEmptyColor(int blockX, int blockZ) {
        int shade = 96 + Math.floorMod(blockX * 31 + blockZ * 17, 9) - 4;
        return shadedColor(TERRAIN_EMPTY_COLOR, shade);
    }

    private int detailedTerrainColor(int color, int[] heights, int px, int py, int tilePixels, int blockX, int blockZ) {
        int index = py * tilePixels + px;
        int height = heights[index];
        int texture = Math.floorMod(blockX * 7349 + blockZ * 9151, 9) - 4;
        if (height == Integer.MIN_VALUE) {
            return shiftShade(color, texture);
        }
        int west = terrainHeightAt(heights, px - 1, py, tilePixels, height);
        int east = terrainHeightAt(heights, px + 1, py, tilePixels, height);
        int north = terrainHeightAt(heights, px, py - 1, tilePixels, height);
        int south = terrainHeightAt(heights, px, py + 1, tilePixels, height);
        int relief = Math.clamp(((west - east) + (north - south)) * 4, -34, 34);
        int edge = Math.clamp((Math.abs(height - east) + Math.abs(height - south)) * -2, -18, 0);
        return shiftShade(color, relief + edge + texture);
    }

    private int terrainHeightAt(int[] heights, int px, int py, int tilePixels, int fallback) {
        if (px < 0 || py < 0 || px >= tilePixels || py >= tilePixels) {
            return fallback;
        }
        int height = heights[py * tilePixels + px];
        return height == Integer.MIN_VALUE ? fallback : height;
    }

    private int shiftShade(int color, int delta) {
        int shade = Math.clamp(100 + delta, 52, 148);
        int red = Math.clamp(((color >> 16) & 0xff) * shade / 100, 0, 255);
        int green = Math.clamp(((color >> 8) & 0xff) * shade / 100, 0, 255);
        int blue = Math.clamp((color & 0xff) * shade / 100, 0, 255);
        return 0xff000000 | (red << 16) | (green << 8) | blue;
    }

    private int stoneColor(String name) {
        if (name.contains("GRANITE")) {
            return 0x9A705F;
        }
        if (name.contains("DIORITE")) {
            return 0xB7B5AC;
        }
        if (name.contains("ANDESITE")) {
            return 0x85847F;
        }
        if (name.contains("COPPER")) {
            return 0xB56F4C;
        }
        return 0x777872;
    }

    private int woodColor(String name) {
        if (name.contains("BIRCH")) {
            return 0xC9B47A;
        }
        if (name.contains("SPRUCE") || name.contains("DARK_OAK")) {
            return 0x5B3E27;
        }
        if (name.contains("ACACIA")) {
            return 0xA75D34;
        }
        if (name.contains("MANGROVE")) {
            return 0x7A3C32;
        }
        if (name.contains("CHERRY")) {
            return 0xD7A7A8;
        }
        if (name.contains("BAMBOO")) {
            return 0xC2A64F;
        }
        return 0x8A6A3F;
    }

    private int terracottaColor(String name) {
        if (name.contains("WHITE")) {
            return 0xD1B8A1;
        }
        if (name.contains("ORANGE")) {
            return 0xB66A36;
        }
        if (name.contains("RED")) {
            return 0x8F3C31;
        }
        if (name.contains("YELLOW")) {
            return 0xC5A24B;
        }
        if (name.contains("BROWN")) {
            return 0x734B36;
        }
        if (name.contains("GRAY") || name.contains("BLACK")) {
            return 0x4D4743;
        }
        if (name.contains("BLUE") || name.contains("CYAN")) {
            return 0x586B74;
        }
        if (name.contains("GREEN") || name.contains("LIME")) {
            return 0x677245;
        }
        return 0x9C6D55;
    }

    private int concreteColor(String name) {
        if (name.contains("WHITE")) {
            return 0xD8D9D4;
        }
        if (name.contains("BLACK")) {
            return 0x202226;
        }
        if (name.contains("RED")) {
            return 0x9F3331;
        }
        if (name.contains("BLUE")) {
            return 0x3B5796;
        }
        if (name.contains("GREEN") || name.contains("LIME")) {
            return 0x5F7C37;
        }
        if (name.contains("YELLOW")) {
            return 0xD0AA35;
        }
        if (name.contains("ORANGE")) {
            return 0xD37A2E;
        }
        if (name.contains("PURPLE") || name.contains("MAGENTA") || name.contains("PINK")) {
            return 0x9A5E9B;
        }
        return 0x8B8D87;
    }

    private int shadedColor(int color, int shade) {
        int red = Math.clamp(((color >> 16) & 0xff) * shade / 100, 0, 255);
        int green = Math.clamp(((color >> 8) & 0xff) * shade / 100, 0, 255);
        int blue = Math.clamp((color & 0xff) * shade / 100, 0, 255);
        return 0xff000000 | (red << 16) | (green << 8) | blue;
    }

    private void startMapRefresh() {
        if (!context.configuration().mapWebEnabled() || this.mapRefreshTaskId != -1) {
            return;
        }
        this.mapRefreshTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(
            context.plugin(),
            this::refreshCacheAndBroadcast,
            context.configuration().mapSseIntervalTicks(),
            context.configuration().mapSseIntervalTicks()
        );
    }

    private void stopMapRefresh() {
        if (this.mapRefreshTaskId != -1) {
            Bukkit.getScheduler().cancelTask(this.mapRefreshTaskId);
            this.mapRefreshTaskId = -1;
        }
    }

    private void startAvatarCleanup() {
        if (!context.configuration().mapAvatarCacheEnabled() || this.avatarCleanupTaskId != -1) {
            return;
        }
        this.avatarCleanupTaskId = Bukkit.getScheduler().runTaskTimerAsynchronously(
            context.plugin(),
            this::cleanupAvatarCache,
            context.configuration().mapAvatarCleanupIntervalTicks(),
            context.configuration().mapAvatarCleanupIntervalTicks()
        ).getTaskId();
    }

    private void stopAvatarCleanup() {
        if (this.avatarCleanupTaskId != -1) {
            Bukkit.getScheduler().cancelTask(this.avatarCleanupTaskId);
            this.avatarCleanupTaskId = -1;
        }
    }

    private void startTerrainPrewarm() {
        if (!context.configuration().mapWebEnabled()
            || !context.configuration().mapTerrainEnabled()
            || !context.configuration().mapTerrainPrewarmEnabled()
            || this.terrainPrewarmBootstrapTaskId != -1
            || this.terrainPrewarmTaskId != -1) {
            return;
        }

        this.terrainPrewarmBootstrapTaskId = Bukkit.getScheduler().scheduleSyncDelayedTask(
            context.plugin(),
            () -> {
                this.terrainPrewarmBootstrapTaskId = -1;
                beginTerrainPrewarm();
            },
            80L
        );
    }

    private void beginTerrainPrewarm() {
        if (!context.configuration().mapWebEnabled()
            || !context.configuration().mapTerrainEnabled()
            || !context.configuration().mapTerrainPrewarmEnabled()
            || this.terrainPrewarmTaskId != -1) {
            return;
        }
        int queuedTiles = terrainPrewarmService.rebuild(
            terrainPrewarmWorlds(),
            context.configuration().mapTerrainPrewarmRadiusBlocks(),
            context.configuration().mapTerrainPrewarmMaxTiles(),
            context.configuration().mapTerrainPrewarmTileSizes()
        );
        if (queuedTiles <= 0) {
            return;
        }
        context.plugin().getLogger().info("STARCORE terrain prewarm queued " + queuedTiles + " tile(s) near world spawn.");
        this.terrainPrewarmTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(
            context.plugin(),
            this::runTerrainPrewarmStep,
            context.configuration().mapTerrainPrewarmIntervalTicks(),
            context.configuration().mapTerrainPrewarmIntervalTicks()
        );
    }

    private void stopTerrainPrewarm() {
        if (this.terrainPrewarmBootstrapTaskId != -1) {
            Bukkit.getScheduler().cancelTask(this.terrainPrewarmBootstrapTaskId);
            this.terrainPrewarmBootstrapTaskId = -1;
        }
        if (this.terrainPrewarmTaskId != -1) {
            Bukkit.getScheduler().cancelTask(this.terrainPrewarmTaskId);
            this.terrainPrewarmTaskId = -1;
        }
        terrainPrewarmService.clear();
    }

    private void startTerrainChangeTracking() {
        if (!context.configuration().mapWebEnabled() || !context.configuration().mapTerrainEnabled()) {
            return;
        }
        Bukkit.getPluginManager().registerEvents(this, context.plugin());
    }

    private void stopTerrainChangeTracking() {
        HandlerList.unregisterAll(this);
    }

    private List<TerrainTilePrewarmService.WorldSpawn> terrainPrewarmWorlds() {
        List<TerrainTilePrewarmService.WorldSpawn> worlds = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            rememberTerrainWorld(world);
            int spawnX = world.getSpawnLocation().getBlockX();
            int spawnZ = world.getSpawnLocation().getBlockZ();
            worlds.add(new TerrainTilePrewarmService.WorldSpawn(world.getName(), spawnX, spawnZ));
        }
        return worlds;
    }

    private void runTerrainPrewarmStep() {
        TerrainPrewarmTile tile = terrainPrewarmService.poll();
        if (tile == null) {
            completeTerrainPrewarm();
            return;
        }

        TerrainTileKey key = tile.key();
        if (terrainTiles.isRendering(key)) {
            terrainPrewarmService.requeue(tile);
            return;
        }
        if (terrainTiles.activeRenderJobs() >= context.configuration().mapTerrainTileMaxConcurrentRenders()) {
            terrainPrewarmService.requeue(tile);
            return;
        }

        try {
            terrainTile(tile.world(), tile.minX(), tile.minZ(), tile.worldSize());
        } catch (IOException exception) {
            if (context.configuration().debug()) {
                context.plugin().getLogger().fine("STARCORE terrain prewarm skipped " + tile + ": " + exception.getMessage());
            }
        }

        if (terrainPrewarmService.isEmpty()) {
            completeTerrainPrewarm();
        }
    }

    private void completeTerrainPrewarm() {
        if (this.terrainPrewarmTaskId != -1) {
            Bukkit.getScheduler().cancelTask(this.terrainPrewarmTaskId);
            this.terrainPrewarmTaskId = -1;
            context.plugin().getLogger().info("STARCORE terrain prewarm complete.");
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockPlace(BlockPlaceEvent event) {
        markTerrainDirty(event.getBlockPlaced(), true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockBreak(BlockBreakEvent event) {
        markTerrainDirty(event.getBlock(), true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockBurn(BlockBurnEvent event) {
        markTerrainDirty(event.getBlock(), true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockFade(BlockFadeEvent event) {
        markTerrainDirty(event.getBlock(), true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockForm(BlockFormEvent event) {
        markTerrainDirty(event.getBlock(), true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockGrow(BlockGrowEvent event) {
        markTerrainDirty(event.getBlock(), true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockSpread(BlockSpreadEvent event) {
        markTerrainDirty(event.getBlock(), true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockFromTo(BlockFromToEvent event) {
        markTerrainDirty(event.getBlock(), false);
        markTerrainDirty(event.getToBlock(), false);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onLeavesDecay(LeavesDecayEvent event) {
        markTerrainDirty(event.getBlock(), true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockExplode(BlockExplodeEvent event) {
        markTerrainDirty(event.getBlock(), true);
        markTerrainDirtyBlocks(event.blockList());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onEntityExplode(EntityExplodeEvent event) {
        markTerrainDirty(event.getLocation().getBlock(), true);
        markTerrainDirtyBlocks(event.blockList());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onStructureGrow(StructureGrowEvent event) {
        markTerrainDirty(event.getLocation().getBlock(), true);
        for (BlockState blockState : event.getBlocks()) {
            markTerrainDirty(blockState.getBlock(), false);
        }
    }

    private void markTerrainDirty(Block block, boolean includeNeighbors) {
        if (block == null) {
            return;
        }
        markTerrainDirty(block.getWorld().getName(), block.getX(), block.getZ(), includeNeighbors);
    }

    private void markTerrainDirtyBlocks(Collection<Block> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return;
        }
        for (Block block : blocks) {
            markTerrainDirty(block, true);
        }
    }

    private void markTerrainDirty(String worldName, int blockX, int blockZ, boolean includeNeighbors) {
        long dirtyAt = System.currentTimeMillis();
        List<TerrainTileKey> dirtyKeys = terrainTiles.markDirty(
            worldName,
            blockX,
            blockZ,
            includeNeighbors,
            dirtyAt,
            terrainTileDiskCacheTtlMillis()
        );
        if (dirtyKeys.isEmpty()) {
            return;
        }
        terrainTiles.scheduleRevisionBroadcast(
            context.plugin(),
            context.configuration().mapWebEnabled(),
            this::refreshCacheAndBroadcast
        );
    }

    private void cleanupAvatarCache() {
        avatarEndpoint.cleanup(avatarSettings());
    }

    private void refreshCacheAndBroadcast() {
        refreshCachedState();
        if (!context.configuration().mapSseEnabled() || sseBroadcaster.isEmpty()) {
            return;
        }
        long nowEpochSecond = Instant.now().getEpochSecond();
        sseBroadcaster.broadcastSnapshots(nowEpochSecond, this::snapshotJson);
        accessManager.cleanupExpiredBoundViewerAccesses(nowEpochSecond);
    }

    private MapSnapshot refreshCachedState() {
        MapSnapshot snapshot = buildSnapshot();
        latestSnapshot = snapshot;
        latestNationMembership = buildNationMembership();
        latestNationIds = Set.copyOf(latestNationMembership.values());
        latestDiplomacyRelations = buildDiplomacyRelations();
        MapSnapshot publicSnapshot = publicSnapshot(snapshot);
        latestSnapshotJson = toJson(publicSnapshot, Set.of(), MapViewerAccess.publicView());
        latestHealthJson = buildHealthJson(publicSnapshot, Set.of(), MapViewerAccess.publicView());
        return snapshot;
    }

    private MapSnapshot buildSnapshot() {
        return new MapSnapshot(Instant.now(), List.of(territoryLayer(), resourceDistrictLayer(), playerLayer()));
    }

    private String snapshotJson() {
        return latestSnapshotJson != null ? latestSnapshotJson : "{}";
    }

    private String snapshotJson(MapViewerAccess access) {
        SnapshotView view = snapshotView(access);
        return toJson(view.snapshot(), view.visibleNationIds(), access);
    }

    private void writeJson(HttpExchange exchange, String json) throws IOException {
        MapHttpResponses.writeJson(exchange, json, context.configuration().mapWebCorsAllowedOrigins());
    }

    private void writeJson(HttpExchange exchange, int status, String json) throws IOException {
        MapHttpResponses.writeJson(exchange, status, json, context.configuration().mapWebCorsAllowedOrigins());
    }

    private void writeClaimResponse(HttpExchange exchange, ClaimHttpResponse response) throws IOException {
        MapHttpResponses.writeClaimResponse(
            exchange,
            response.status(),
            response.json(),
            response.contentType(),
            response.filename(),
            context.configuration().mapWebCorsAllowedOrigins()
        );
    }

    private void writeText(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        MapHttpResponses.writeText(exchange, status, contentType, body, context.configuration().mapWebCorsAllowedOrigins());
    }

    private void writeBytes(HttpExchange exchange, byte[] bytes, String contentType, int status, int maxAgeSeconds) throws IOException {
        MapHttpResponses.writeBytes(exchange, bytes, contentType, status, maxAgeSeconds, context.configuration().mapWebCorsAllowedOrigins());
    }

    private boolean handleCorsPreflight(HttpExchange exchange) throws IOException {
        return MapHttpResponses.handleCorsPreflight(exchange, context.configuration().mapWebCorsAllowedOrigins());
    }

    private void applyCorsHeaders(HttpExchange exchange) {
        MapHttpResponses.applyCorsHeaders(exchange, context.configuration().mapWebCorsAllowedOrigins());
    }

    private Path resolveExportDirectory() {
        String configured = context.plugin().getConfig().getString("map.export-directory", "..");
        Path base = context.plugin().getDataFolder().toPath();
        Path resolved = base.resolve(configured).normalize();
        return resolved.resolve("map").normalize();
    }

    private Path avatarCacheDirectory() {
        return context.plugin().getDataFolder().toPath().resolve("cache").resolve("avatars");
    }

    private MapAvatarEndpoint.Settings avatarSettings() {
        return new MapAvatarEndpoint.Settings(
            avatarCacheDirectory(),
            context.configuration().mapAvatarCacheEnabled(),
            context.configuration().mapAvatarCacheTtlMinutes(),
            context.configuration().mapAvatarUpstreams()
        );
    }

    private MapLayerSnapshot territoryLayer() {
        List<MapTerritoryPolygon> polygons = nationService.nations().stream()
            .flatMap(nation -> territoryService.claimsByOwner(nation.id().toString()).stream().map(claim -> polygonFor(nation, claim)))
            .toList();
        return new MapLayerSnapshot(MapLayerType.TERRITORY, polygons, List.of());
    }

    private MapLayerSnapshot playerLayer() {
        List<MapMarker> markers = onlinePlayerDirectory.onlinePlayers().stream().map(this::markerFor).toList();
        return new MapLayerSnapshot(MapLayerType.PLAYER_MARKERS, List.of(), markers);
    }

    private MapLayerSnapshot resourceDistrictLayer() {
        NationResourceDistrictService service = resourceDistrictService;
        if (service == null) {
            return new MapLayerSnapshot(MapLayerType.RESOURCE_DISTRICTS, List.of(), List.of());
        }
        List<MapMarker> markers = service.districts().stream()
            .map(this::markerForResourceDistrict)
            .flatMap(Optional::stream)
            .toList();
        return new MapLayerSnapshot(MapLayerType.RESOURCE_DISTRICTS, List.of(), markers);
    }

    private MapTerritoryPolygon polygonFor(Nation nation, TerritoryClaim claim) {
        DiplomacyRelation relation = diplomacyRelationWithAny(nation);
        String color = defaultColorFor(nation.id().toString());
        NationOperationalOverview nationOverview = nationOperationalOverview(nation);
        Map<String, String> metadata = NationMapMetadataSupport.baseMetadata(
            nation,
            nationOverview,
            color,
            relation.name().toLowerCase(Locale.ROOT)
        );
        appendFinanceSummary(metadata, nation.id());
        appendRecentNationEvents(metadata, nation.id());
        NationMapMetadataSupport.appendOfficerAuthorizationMetadata(
            metadata,
            context == null ? null : context.configuration()
        );
        return new MapTerritoryPolygon(
            nation.id().toString(),
            nation.name(),
            claim.coordinate().world(),
            claim.coordinate().x(),
            claim.coordinate().z(),
            color,
            metadata
        );
    }

    private MapMarker markerFor(Player player) {
        Optional<Nation> nation = nationService.nationOf(player.getUniqueId());
        String avatarUrl = avatarUrlFor(player.getUniqueId());
        playerProfileService.recordSeen(player.getUniqueId(), player.getName());
        Map<String, String> metadata = new HashMap<>();
        metadata.put("player", player.getName());
        metadata.put("playerId", player.getUniqueId().toString());
        metadata.put("world", player.getWorld().getName());
        metadata.put("blockX", String.valueOf(player.getLocation().getBlockX()));
        metadata.put("blockZ", String.valueOf(player.getLocation().getBlockZ()));
        metadata.put("isOnline", "true");
        metadata.put("avatarHint", avatarHint(player.getName()));
        metadata.put("avatarUrl", avatarUrl);
        nation.ifPresentOrElse(value -> {
            DiplomacyRelation relation = diplomacyRelationWithAny(value);
            metadata.put("nation", value.name());
            metadata.put("nationId", value.id().toString());
            metadata.put("government", value.governmentType().name());
            metadata.put("relation", relation == DiplomacyRelation.ALLIED ? "member" : relation.name().toLowerCase());
            metadata.put("displayColor", defaultColorFor(value.id().toString()));
        }, () -> metadata.put("relation", "neutral"));
        return new MapMarker(
            "player:" + player.getUniqueId(),
            player.getName(),
            player.getWorld().getName(),
            player.getLocation().getX(),
            player.getLocation().getZ(),
            nation.isPresent() ? "allied-avatar" : "player",
            metadata
        );
    }

    private Optional<MapMarker> markerForResourceDistrict(NationResourceDistrictSnapshot district) {
        Nation nation = nationService.nationById(district.nationId()).orElse(null);
        if (nation == null) {
            return Optional.empty();
        }
        DiplomacyRelation relation = diplomacyRelationWithAny(nation);
        String color = defaultColorFor(nation.id().toString());
        NationOperationalOverview nationOverview = nationOperationalOverview(nation);
        NationResourceDistrictOperationalOverview overview = resourceDistrictOperationalOverview(district);
        Map<String, String> metadata = ResourceDistrictMapMetadataSupport.baseMetadata(
            nation,
            nationOverview,
            district,
            relation.name().toLowerCase(Locale.ROOT),
            color,
            overview,
            resourceDistrictMigrationLabel(district),
            resourceDistrictBeaconPosition(district),
            district.nextRefreshAtMillis() <= 0L ? "" : resourceDistrictTimestamp(district.nextRefreshAtMillis()),
            district.forceMigrationAtMillis() <= 0L ? "" : resourceDistrictTimestamp(district.forceMigrationAtMillis())
        );
        double x = district.coordinate().x() * ChunkClaimSelection.CHUNK_SIZE + 8.5D;
        double z = district.coordinate().z() * ChunkClaimSelection.CHUNK_SIZE + 8.5D;
        return Optional.of(new MapMarker(
            resourceDistrictMarkerId(district.id()),
            msg("command.map.resource-district-label", nation.name()),
            district.coordinate().world(),
            x,
            z,
            "resource-district",
            metadata
        ));
    }

    private String resourceDistrictMigrationLabel(NationResourceDistrictSnapshot district) {
        return NationResourceDistrictViewSupport.localizedMigrationLabel(messages, district);
    }

    private Optional<NationResourceDistrictSnapshot> findResourceDistrictSnapshot(UUID districtId) {
        NationResourceDistrictService service = resourceDistrictService;
        if (service == null || districtId == null) {
            return Optional.empty();
        }
        return service.districts().stream()
            .filter(snapshot -> districtId.equals(snapshot.id()))
            .findFirst();
    }

    private String resourceDistrictBeaconPosition(NationResourceDistrictSnapshot district) {
        return NationResourceDistrictViewSupport.beaconPosition(district);
    }

    private String resourceDistrictTimestamp(long epochMillis) {
        return NationResourceDistrictViewSupport.isoTimestamp(epochMillis);
    }

    private NationResourceDistrictOperationalOverview resourceDistrictOperationalOverview(NationResourceDistrictSnapshot district) {
        return NationResourceDistrictOperationalSupport.overview(context == null ? null : context.configuration(), district);
    }

    private NationOperationalOverview nationOperationalOverview(Nation nation) {
        return NationOperationalSupport.overview(
            context == null ? null : context.configuration(),
            nationService,
            resourceDistrictService,
            nation
        );
    }

    private EventService eventService() {
        if (eventService == null && context != null) {
            eventService = context.serviceRegistry().find(EventService.class).orElse(null);
        }
        return eventService;
    }

    private LedgerCategoryService ledgerCategories() {
        if (ledgerCategories == null) {
            ledgerCategories = new LedgerCategoryService(context == null ? null : context.configuration());
        }
        return ledgerCategories;
    }

    private MapFinanceEndpoint financeEndpoint() {
        if (financeEndpoint == null) {
            financeEndpoint = new MapFinanceEndpoint(
                this::eventService,
                () -> nationService,
                this::treasuryService,
                this::ledgerCategories,
                this::financeNationVisible,
                () -> context == null || context.configuration().mapWebFinanceExportCsvBomEnabled(),
                this::msg
            );
        }
        return financeEndpoint;
    }

    private MapEventLogEndpoint eventLogEndpoint() {
        if (eventLogEndpoint == null) {
            eventLogEndpoint = new MapEventLogEndpoint(
                this::eventService,
                () -> nationService,
                this::ledgerCategories,
                this::financeNationVisible,
                this::recentEventResourceId,
                () -> context == null || context.configuration().mapWebFinanceExportCsvBomEnabled(),
                this::msg
            );
        }
        return eventLogEndpoint;
    }

    private void appendRecentNationEvents(Map<String, String> metadata, NationId nationId) {
        if (metadata == null || nationId == null) {
            return;
        }
        EventService service = eventService();
        if (service == null) {
            return;
        }
        List<NationEventRecord> recentEvents = NationMapMetadataSupport.selectRecentEvents(
            service.eventsOf(nationId).stream().toList(),
            5
        );
        NationMapMetadataSupport.appendRecentEvents(metadata, recentEvents, this::recentEventResourceId);
    }

    private void appendFinanceSummary(Map<String, String> metadata, NationId nationId) {
        if (metadata == null || nationId == null) {
            return;
        }
        TreasuryService treasury = treasuryService();
        BigDecimal treasuryBalance = null;
        if (treasury != null) {
            treasuryBalance = treasury.balance(nationId);
        }
        EventService service = eventService();
        if (service == null) {
            metadata.put("financeEventCount", "0");
            return;
        }
        List<NationEventRecord> financeEvents = service.eventsOf(nationId).stream()
            .filter(event -> event.type() != null && event.type().startsWith("treasury."))
            .toList();
        NationMapMetadataSupport.appendFinanceSummary(metadata, treasuryBalance, financeEvents);
    }

    private TreasuryService treasuryService() {
        if (treasuryService == null && context != null) {
            treasuryService = context.serviceRegistry().find(TreasuryService.class).orElse(null);
        }
        return treasuryService;
    }

    private OfficerService officerService() {
        if (officerService == null && context != null) {
            officerService = context.serviceRegistry().find(OfficerService.class).orElse(null);
        }
        return officerService;
    }

    private Optional<String> recentEventResourceId(NationEventRecord event) {
        if (event == null) {
            return Optional.empty();
        }
        String context = event.context() == null ? "" : event.context().trim();
        if (!context.isEmpty()) {
            if (context.startsWith("resource:")) {
                return Optional.of(context);
            }
            try {
                UUID districtId = UUID.fromString(context);
                if (findResourceDistrictSnapshot(districtId).isPresent()) {
                    return Optional.of(resourceDistrictMarkerId(districtId));
                }
            } catch (IllegalArgumentException ignored) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private String resourceDistrictMarkerId(UUID districtId) {
        return "resource:" + districtId;
    }

    private String avatarHint(String playerName) {
        String name = playerName == null ? "?" : playerName.trim();
        if (name.isEmpty()) {
            return "?";
        }
        return name.substring(0, Math.min(2, name.length())).toUpperCase();
    }

    private String avatarUrlFor(UUID playerId) {
        if (!context.configuration().mapWebEnabled()) {
            return avatarEndpoint.upstreamAvatarUrl(context.configuration().mapAvatarUpstreams().getFirst(), playerId);
        }
        return webAddress().orElse("") + "api/map/avatar?id=" + playerId;
    }

    private DiplomacyRelation relationOf(Nation target, Nation viewer) {
        if (target == null) {
            return DiplomacyRelation.NEUTRAL;
        }
        if (viewer != null && target.id().equals(viewer.id())) {
            return DiplomacyRelation.ALLIED;
        }
        return diplomacyRelationBetween(viewer, target);
    }

    private DiplomacyService diplomacyService() {
        if (diplomacyService == null && context != null) {
            diplomacyService = context.serviceRegistry().find(DiplomacyService.class).orElse(null);
        }
        return diplomacyService;
    }

    private DiplomacyRelation diplomacyRelationBetween(Nation source, Nation target) {
        if (source == null || target == null) {
            return DiplomacyRelation.NEUTRAL;
        }
        DiplomacyService service = diplomacyService();
        if (service == null) {
            return DiplomacyRelation.NEUTRAL;
        }
        return service.relationBetween(source.id(), target.id());
    }

    /**
     * Returns the best diplomatic relation this nation has with any other nation.
     * Used for layer markers where there's no specific viewer context.
     */
    private DiplomacyRelation diplomacyRelationWithAny(Nation nation) {
        if (nation == null) {
            return DiplomacyRelation.NEUTRAL;
        }
        DiplomacyService service = diplomacyService();
        if (service == null) {
            return DiplomacyRelation.NEUTRAL;
        }
        DiplomacyRelation best = DiplomacyRelation.NEUTRAL;
        for (Nation other : nationService.nations()) {
            if (other.id().equals(nation.id())) {
                continue;
            }
            DiplomacyRelation relation = service.relationBetween(nation.id(), other.id());
            if (relation.ordinal() < best.ordinal()) {
                best = relation;
                if (best == DiplomacyRelation.ALLIED) {
                    break;
                }
            }
        }
        return best;
    }

    private String defaultColorFor(String nationId) {
        int rgb = Math.abs(nationId.hashCode()) & 0xFFFFFF;
        return String.format("#%06X", rgb);
    }

    private String healthJson() {
        return latestHealthJson != null ? latestHealthJson : "{}";
    }

    private String healthJson(MapViewerAccess access) {
        SnapshotView view = snapshotView(access);
        return buildHealthJson(view.snapshot(), view.visibleNationIds(), access);
    }

    private String buildHealthJson(MapSnapshot snapshot, Set<String> visibleNationIds, MapViewerAccess access) {
        int onlinePlayers = markerCount(snapshot, MapLayerType.PLAYER_MARKERS);
        int resourceDistricts = markerCount(snapshot, MapLayerType.RESOURCE_DISTRICTS);
        return "{" +
            "\"status\":\"ok\"," +
            "\"sseEnabled\":" + context.configuration().mapSseEnabled() + ',' +
            "\"sseClients\":" + sseBroadcaster.clientCount() + ',' +
            "\"avatarCacheEnabled\":" + context.configuration().mapAvatarCacheEnabled() + ',' +
            "\"avatarCacheTtlMinutes\":" + context.configuration().mapAvatarCacheTtlMinutes() + ',' +
            "\"avatarUpstreams\":" + context.configuration().mapAvatarUpstreams().size() + ',' +
            "\"onlinePlayers\":" + onlinePlayers + ',' +
            "\"resourceDistricts\":" + resourceDistricts + ',' +
            "\"nations\":" + visibleNationIds.size() + ',' +
            "\"access\":" + accessJson(access) +
            "}";
    }

    private String toJson(MapSnapshot snapshot, Set<String> visibleNationIds, MapViewerAccess access) {
        return snapshotJsonWriter.toJson(
            snapshot,
            visibleNationIds,
            access,
            this::publicWorlds,
            this::viewerJson,
            this::terrainJson,
            latestDiplomacyRelations
        );
    }

    private String terrainJson(Set<String> worlds) {
        return terrainMetadata.terrainJson(worlds, terrainTilePixels(), terrainTiles.revision());
    }

    private int terrainTilePixels() {
        return Math.clamp(terrainTilePixels, 64, 512);
    }

    private String accessJson(MapViewerAccess access) {
        return snapshotJsonWriter.accessJson(access);
    }

    private String viewerJson(MapViewerAccess access) {
        return viewerJsonWriter.toJson(viewerDetails(access));
    }

    private MapViewerJsonWriter.ViewerDetails viewerDetails(MapViewerAccess access) {
        if (access == null || access.isPublic()) {
            return null;
        }
        UUID viewerId = access.viewerId();
        Player onlinePlayer = onlinePlayerDirectory.findOnlinePlayer(viewerId).orElse(null);
        String profileName = playerProfileService.snapshot().get(viewerId);
        String viewerName = onlinePlayer == null ? profileName : onlinePlayer.getName();
        if (viewerName == null || viewerName.isBlank()) {
            viewerName = viewerId.toString();
        }

        Nation nation = nationService.nationOf(viewerId).orElse(null);
        NationOperationalOverview nationOverview = null;
        int claimCount = 0;
        int claimLimit = context.configuration().maxClaimsPerNation();
        int nationLevel = 1;
        long nationExperience = 0L;
        long nationExperienceProgress = 0L;
        long nationNextLevelExperience = 0L;
        long nationExperienceRemaining = 0L;
        boolean nationMaxLevelReached = false;
        int cityStateCount = 0;
        int cityStateLimit = 0;
        int resourceDistrictCount = 0;
        int resourceDistrictLimit = 0;
        boolean founder = false;
        String founderName = "";
        String government = "";
        String role = "independent";
        String worldName = "";
        int blockX = 0;
        int blockY = 0;
        int blockZ = 0;
        boolean online = onlinePlayer != null && onlinePlayer.isOnline();
        if (online) {
            worldName = onlinePlayer.getWorld().getName();
            blockX = onlinePlayer.getLocation().getBlockX();
            blockY = onlinePlayer.getLocation().getBlockY();
            blockZ = onlinePlayer.getLocation().getBlockZ();
        }
        if (nation != null) {
            nationOverview = nationOperationalOverview(nation);
            claimCount = nationOverview.claimCount();
            claimLimit = nationOverview.claimLimit();
            nationLevel = nationOverview.level();
            nationExperience = nationOverview.experience();
            nationExperienceProgress = nationOverview.currentLevelProgress();
            nationNextLevelExperience = nationOverview.nextLevelExperienceRequired();
            nationExperienceRemaining = nationOverview.remainingExperienceToNextLevel();
            nationMaxLevelReached = nationOverview.maxLevelReached();
            cityStateCount = nationOverview.cityStateCount();
            cityStateLimit = nationOverview.cityStateLimit();
            resourceDistrictCount = nationOverview.resourceDistrictCount();
            resourceDistrictLimit = nationOverview.resourceDistrictLimit();
            founderName = nationOverview.founderName();
            government = nation.governmentType().name();
            founder = nation.founderId().equals(viewerId);
            role = founder ? "founder" : "member";
        }
        return new MapViewerJsonWriter.ViewerDetails(
            viewerId.toString(),
            viewerName,
            context.economyService().balance(viewerId),
            nation == null ? "" : nation.id().toString(),
            nation == null ? "" : nation.name(),
            nation == null ? "independent" : nation.kind().name().toLowerCase(Locale.ROOT),
            founderName,
            government,
            role,
            nationLevel,
            nationExperience,
            nationExperienceProgress,
            nationNextLevelExperience,
            nationExperienceRemaining,
            nationMaxLevelReached,
            claimCount,
            claimLimit,
            cityStateCount,
            cityStateLimit,
            resourceDistrictCount,
            resourceDistrictLimit,
            online,
            worldName,
            blockX,
            blockY,
            blockZ,
            founder
        );
    }

    private Set<String> publicWorlds() {
        return Bukkit.getWorlds().stream()
            .map(World::getName)
            .collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);
    }

    private void appendField(StringBuilder builder, String name, String value) {
        builder.append('"').append(escape(name)).append("\":\"").append(escape(value)).append('"');
    }

    private void appendBooleanField(StringBuilder builder, String name, boolean value) {
        builder.append('"').append(escape(name)).append("\":").append(value);
    }

    private String msg(String key, Object... args) {
        return messages.format(key, args);
    }

    private String escape(String input) {
        return input
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\r", "\\r")
            .replace("\n", "\\n")
            .replace("\t", "\\t");
    }

    private MapViewerAccess resolveMapViewerAccess(HttpExchange exchange) {
        Map<String, String> params = MapHttpRequestParser.query(exchange.getRequestURI());
        long nowEpochSecond = Instant.now().getEpochSecond();
        if (!accessManager.hasSignedAccessParameters(params)) {
            return accessManager
                .resolveBoundViewerAccess(remoteAddress(exchange), nowEpochSecond)
                .orElse(MapViewerAccess.publicView());
        }
        return accessManager.resolveSignedAccess(
            params,
            mapAccessSecretConfigured(),
            context.configuration().mapWebAccessSecret(),
            nowEpochSecond
        );
    }

    private boolean mapAccessSecretConfigured() {
        return context.configuration().mapWebAccessSecretConfigured();
    }

    private String remoteAddress(HttpExchange exchange) {
        String connectionAddress = exchange.getRemoteAddress() == null || exchange.getRemoteAddress().getAddress() == null
            ? null
            : exchange.getRemoteAddress().getAddress().getHostAddress();
        return accessManager.resolveClientAddress(
            connectionAddress,
            exchange.getRequestHeaders().getFirst("X-Forwarded-For"),
            context.configuration().mapWebTrustedProxies()
        );
    }

    private SnapshotView snapshotView(MapViewerAccess access) {
        MapSnapshot snapshot = latestSnapshot;
        if (snapshot == null) {
            return new SnapshotView(new MapSnapshot(Instant.EPOCH, List.of()), Set.of());
        }
        if (access.isPublic()) {
            return new SnapshotView(publicSnapshot(snapshot), Set.of());
        }
        String viewerNationId = latestNationMembership.get(access.viewerId());
        Set<String> visibleNationIds = access.fullAccess()
            ? latestNationIds
            : visibleNationIdsFor(viewerNationId);
        return new SnapshotView(filterSnapshot(snapshot, access.viewerId(), viewerNationId, visibleNationIds, access.fullAccess()), visibleNationIds);
    }

    private MapSnapshot publicSnapshot(MapSnapshot snapshot) {
        return new MapSnapshot(snapshot.generatedAt(), List.of(
            new MapLayerSnapshot(MapLayerType.TERRITORY, List.of(), List.of()),
            new MapLayerSnapshot(MapLayerType.RESOURCE_DISTRICTS, List.of(), List.of()),
            new MapLayerSnapshot(MapLayerType.PLAYER_MARKERS, List.of(), List.of())
        ));
    }

    private Set<String> visibleNationIdsFor(String viewerNationId) {
        if (viewerNationId == null || viewerNationId.isBlank()) {
            return Set.of();
        }
        Set<String> visible = new LinkedHashSet<>();
        visible.add(viewerNationId);
        Map<String, String> relations = latestDiplomacyRelations.getOrDefault(viewerNationId, Map.of());
        for (Map.Entry<String, String> entry : relations.entrySet()) {
            if (isFriendlyRelation(entry.getValue())) {
                visible.add(entry.getKey());
            }
        }
        return Set.copyOf(visible);
    }

    private boolean isFriendlyRelation(String relation) {
        return "allied".equalsIgnoreCase(relation)
            || "friendly".equalsIgnoreCase(relation)
            || "vassal".equalsIgnoreCase(relation);
    }

    private MapSnapshot filterSnapshot(MapSnapshot snapshot, UUID viewerId, String viewerNationId, Set<String> visibleNationIds, boolean fullAccess) {
        List<MapLayerSnapshot> layers = snapshot.layers().stream().map(layer -> switch (layer.type()) {
            case TERRITORY -> new MapLayerSnapshot(
                layer.type(),
                layer.territories().stream()
                    .filter(territory -> visibleNationIds.contains(territory.ownerId()))
                    .map(territory -> territoryWithRelation(territory, viewerNationId, viewerId))
                    .toList(),
                List.of()
            );
            case PLAYER_MARKERS -> new MapLayerSnapshot(
                layer.type(),
                List.of(),
                layer.markers().stream()
                    .filter(marker -> markerVisibleTo(marker, viewerId, visibleNationIds, fullAccess))
                    .map(marker -> markerWithRelation(marker, viewerNationId, viewerId))
                    .toList()
            );
            case RESOURCE_DISTRICTS -> new MapLayerSnapshot(
                layer.type(),
                List.of(),
                layer.markers().stream()
                    .filter(marker -> markerVisibleTo(marker, viewerId, visibleNationIds, fullAccess))
                    .map(marker -> markerWithRelation(marker, viewerNationId, viewerId))
                    .toList()
            );
            default -> layer;
        }).toList();
        return new MapSnapshot(snapshot.generatedAt(), layers);
    }

    private int markerCount(MapSnapshot snapshot, MapLayerType type) {
        return snapshot.layers().stream()
            .filter(layer -> layer.type() == type)
            .mapToInt(layer -> layer.markers().size())
            .sum();
    }

    private boolean markerVisibleTo(MapMarker marker, UUID viewerId, Set<String> visibleNationIds, boolean fullAccess) {
        Map<String, String> metadata = marker.metadata();
        if (fullAccess) {
            return true;
        }
        if (viewerId != null && viewerId.toString().equals(metadata.get("playerId"))) {
            return true;
        }
        String nationId = metadata.get("nationId");
        return nationId != null && visibleNationIds.contains(nationId);
    }

    private MapTerritoryPolygon territoryWithRelation(MapTerritoryPolygon territory, String viewerNationId, UUID viewerId) {
        Map<String, String> metadata = new HashMap<>(territory.metadata());
        metadata.put("relation", relationValue(viewerNationId, territory.ownerId()));
        appendOfficerAuthorizationViewerMetadata(metadata, territory.ownerId(), viewerNationId, viewerId);
        return new MapTerritoryPolygon(
            territory.ownerId(),
            territory.ownerName(),
            territory.world(),
            territory.chunkX(),
            territory.chunkZ(),
            territory.fillColor(),
            metadata
        );
    }

    private void appendOfficerAuthorizationViewerMetadata(
        Map<String, String> metadata,
        String targetNationId,
        String viewerNationId,
        UUID viewerId
    ) {
        if (metadata == null) {
            return;
        }
        boolean sameNation = viewerNationId != null && !viewerNationId.isBlank() && viewerNationId.equals(targetNationId);
        boolean founder = sameNation && viewerId != null && viewerId.toString().equals(metadata.get("founderId"));
        NationId nationId = parseNationId(targetNationId);
        metadata.put("viewerOfficerNationScope", viewerId == null ? "anonymous" : (sameNation ? (founder ? "founder" : "member") : "external"));
        if (context == null || context.configuration() == null) {
            appendOfficerAuthorizationViewerAction(metadata, "ResourceMigration", nationId, viewerId, sameNation, founder, List.of());
            appendOfficerAuthorizationViewerAction(metadata, "TreasuryWithdraw", nationId, viewerId, sameNation, founder, List.of());
            appendOfficerAuthorizationViewerAction(metadata, "DiplomacySet", nationId, viewerId, sameNation, founder, List.of());
            appendOfficerAuthorizationViewerAction(metadata, "WarDeclare", nationId, viewerId, sameNation, founder, List.of());
            appendOfficerAuthorizationViewerAction(metadata, "WarEnd", nationId, viewerId, sameNation, founder, List.of());
            appendOfficerAuthorizationViewerAction(metadata, "PolicySet", nationId, viewerId, sameNation, founder, List.of());
            appendOfficerAuthorizationViewerAction(metadata, "PolicyClear", nationId, viewerId, sameNation, founder, List.of());
            appendOfficerAuthorizationViewerAction(metadata, "TechnologyUnlock", nationId, viewerId, sameNation, founder, List.of());
            appendOfficerAuthorizationViewerAction(metadata, "TechnologyRevoke", nationId, viewerId, sameNation, founder, List.of());
            return;
        }
        appendOfficerAuthorizationViewerAction(metadata, "ResourceMigration", nationId, viewerId, sameNation, founder, context.configuration().nationResourceMigrationOfficerRoles());
        appendOfficerAuthorizationViewerAction(metadata, "TreasuryWithdraw", nationId, viewerId, sameNation, founder, context.configuration().nationTreasuryWithdrawOfficerRoles());
        appendOfficerAuthorizationViewerAction(metadata, "DiplomacySet", nationId, viewerId, sameNation, founder, context.configuration().nationDiplomacySetOfficerRoles());
        appendOfficerAuthorizationViewerAction(metadata, "WarDeclare", nationId, viewerId, sameNation, founder, context.configuration().nationWarDeclareOfficerRoles());
        appendOfficerAuthorizationViewerAction(metadata, "WarEnd", nationId, viewerId, sameNation, founder, context.configuration().nationWarEndOfficerRoles());
        appendOfficerAuthorizationViewerAction(metadata, "PolicySet", nationId, viewerId, sameNation, founder, context.configuration().nationPolicySetOfficerRoles());
        appendOfficerAuthorizationViewerAction(metadata, "PolicyClear", nationId, viewerId, sameNation, founder, context.configuration().nationPolicyClearOfficerRoles());
        appendOfficerAuthorizationViewerAction(metadata, "TechnologyUnlock", nationId, viewerId, sameNation, founder, context.configuration().nationTechnologyUnlockOfficerRoles());
        appendOfficerAuthorizationViewerAction(metadata, "TechnologyRevoke", nationId, viewerId, sameNation, founder, context.configuration().nationTechnologyRevokeOfficerRoles());
    }

    private void appendOfficerAuthorizationViewerAction(
        Map<String, String> metadata,
        String suffix,
        NationId nationId,
        UUID viewerId,
        boolean sameNation,
        boolean founder,
        List<String> allowedRoles
    ) {
        String status;
        String matchedRole = "";
        boolean canOperate = false;
        if (viewerId == null) {
            status = "anonymous";
        } else if (!sameNation) {
            status = "external-nation";
        } else if (founder) {
            status = "founder";
            canOperate = true;
        } else if (nationId == null) {
            status = "unknown-nation";
        } else if (allowedRoles == null || allowedRoles.isEmpty()) {
            status = "no-role-config";
        } else {
            matchedRole = viewerAuthorizedOfficerRole(nationId, viewerId, allowedRoles);
            canOperate = !matchedRole.isBlank();
            status = canOperate ? "officer" : "needs-appointment";
        }
        metadata.put("viewerCanOfficer" + suffix, String.valueOf(canOperate));
        metadata.put("viewerOfficerStatus" + suffix, status);
        metadata.put("viewerOfficerMatchedRole" + suffix, matchedRole);
    }

    private String viewerAuthorizedOfficerRole(NationId nationId, UUID viewerId, List<String> allowedRoles) {
        OfficerService service = officerService();
        if (service == null || nationId == null || viewerId == null || allowedRoles == null || allowedRoles.isEmpty()) {
            return "";
        }
        for (String role : allowedRoles) {
            String normalized = normalizeOfficerRole(role);
            if (normalized.isBlank()) {
                continue;
            }
            boolean appointed = service.officer(nationId, normalized)
                .map(appointment -> viewerId.equals(appointment.playerId()))
                .orElse(false);
            if (appointed) {
                return normalized;
            }
        }
        return "";
    }

    private NationId parseNationId(String nationId) {
        if (nationId == null || nationId.isBlank()) {
            return null;
        }
        try {
            return new NationId(UUID.fromString(nationId));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private String normalizeOfficerRole(String role) {
        return role == null ? "" : role.trim().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private MapMarker markerWithRelation(MapMarker marker, String viewerNationId, UUID viewerId) {
        Map<String, String> metadata = new HashMap<>(marker.metadata());
        String nationId = metadata.get("nationId");
        if (viewerId != null && viewerId.toString().equals(metadata.get("playerId")) && nationId == null) {
            metadata.put("relation", "member");
        } else {
            metadata.put("relation", relationValue(viewerNationId, nationId));
        }
        if ("resource-district".equals(marker.icon())) {
            appendResourceDistrictViewerMetadata(metadata, viewerId);
        }
        return new MapMarker(
            marker.id(),
            marker.label(),
            marker.world(),
            marker.x(),
            marker.z(),
            marker.icon(),
            metadata
        );
    }

    private void appendResourceDistrictViewerMetadata(Map<String, String> metadata, UUID viewerId) {
        NationResourceDistrictCommandSupport.CommandState commandState = resourceDistrictCommandState(
            viewerId,
            metadata.get("nationId"),
            metadata.get("migrationState"),
            "ready"
        );
        NationResourceDistrictCommandSupport.CommandPresentation commandPresentation = resourceDistrictCommandPresentation(commandState);
        ResourceDistrictMapMetadataSupport.appendViewerCommandMetadata(metadata, commandState, commandPresentation);
    }

    private NationResourceDistrictCommandSupport.CommandState resourceDistrictCommandState(
        UUID viewerId,
        NationResourceDistrictSnapshot district,
        String fallbackActionState
    ) {
        return NationResourceDistrictCommandSupport.resolve(
            context == null ? null : context.configuration(),
            context == null ? null : context.economyService(),
            nationService,
            officerService(),
            onlinePlayerDirectory,
            viewerId,
            district,
            fallbackActionState
        );
    }

    private NationResourceDistrictCommandSupport.CommandState resourceDistrictCommandState(
        UUID viewerId,
        String districtNationId,
        String migrationState,
        String fallbackActionState
    ) {
        return NationResourceDistrictCommandSupport.resolve(
            context == null ? null : context.configuration(),
            context == null ? null : context.economyService(),
            nationService,
            officerService(),
            onlinePlayerDirectory,
            viewerId,
            districtNationId,
            migrationState,
            fallbackActionState
        );
    }

    private NationResourceDistrictCommandSupport.CommandPresentation resourceDistrictCommandPresentation(
        NationResourceDistrictCommandSupport.CommandState commandState
    ) {
        MessageService messageService = messages != null ? messages : (key, args) -> key;
        return NationResourceDistrictCommandSupport.presentation(messageService, commandState);
    }

    private String relationValue(String viewerNationId, String targetNationId) {
        if (viewerNationId == null || viewerNationId.isBlank() || targetNationId == null || targetNationId.isBlank()) {
            return DiplomacyRelation.NEUTRAL.name().toLowerCase();
        }
        if (viewerNationId.equals(targetNationId)) {
            return "member";
        }
        return latestDiplomacyRelations
            .getOrDefault(viewerNationId, Map.of())
            .getOrDefault(targetNationId, DiplomacyRelation.NEUTRAL.name().toLowerCase());
    }

    private Map<UUID, String> buildNationMembership() {
        Map<UUID, String> membership = new HashMap<>();
        for (Nation nation : nationService.nations()) {
            nation.members().forEach(member -> membership.put(member.playerId(), nation.id().toString()));
        }
        return Map.copyOf(membership);
    }

    private Map<String, Map<String, String>> buildDiplomacyRelations() {
        Map<String, Map<String, String>> relations = new HashMap<>();
        for (Nation source : nationService.nations()) {
            Map<String, String> targets = new HashMap<>();
            for (Nation target : nationService.nations()) {
                String relation = source.id().equals(target.id())
                    ? "member"
                    : diplomacyRelationBetween(source, target).name().toLowerCase();
                targets.put(target.id().toString(), relation);
            }
            relations.put(source.id().toString(), Map.copyOf(targets));
        }
        return Map.copyOf(relations);
    }

    private record SnapshotView(MapSnapshot snapshot, Set<String> visibleNationIds) {
    }

    private record ClaimHttpResponse(int status, String json, String contentType, String filename) {
        private ClaimHttpResponse(int status, String json) {
            this(status, json, "application/json; charset=utf-8", "");
        }
    }

    private record TerrainSurface(BlockData blockData, Material material, Biome biome, int y, int waterDepth, int skyLight, int emittedLight) {
    }

}
