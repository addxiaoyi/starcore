package dev.starcore.starcore.webmap;
import java.util.Optional;

import dev.starcore.starcore.city.City;
import dev.starcore.starcore.city.CityManager;
import dev.starcore.starcore.clan.ClanManager;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.map.MapMarkerService;
import dev.starcore.starcore.module.map.model.CustomMapMarker;
import dev.starcore.starcore.module.map.model.DynamicMapMarker;
import dev.starcore.starcore.module.map.model.MapMarker;
import dev.starcore.starcore.module.map.model.MapMarkerCategory;
import dev.starcore.starcore.territory.MultiChunkTerritory;
import dev.starcore.starcore.territory.service.TerritoryClaimService;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 地图数据提供者
 * 为WebMap提供所有游戏数据
 *
 * <p>设计原则：
 * <ul>
 *   <li>依赖注入：通过构造函数或 Builder 注入依赖</li>
 *   <li>空安全：支持 null 依赖，回退到默认值</li>
 *   <li>可测试性：提供数据转换的钩子方法</li>
 *   <li>可扩展性：通过 Builder 自定义数据转换逻辑</li>
 * </ul>
 */
public class MapDataProvider {

    private final TerritoryClaimService territoryService;
    private final CityManager cityManager;
    private final ClanManager clanManager;
    private final NationService nationService;

    // 数据转换钩子
    private final Function<MultiChunkTerritory, TerritoryData> territoryConverter;
    private final Function<City, CityData> cityConverter;

    // 标记服务（可选）
    private MapMarkerService markerService;

    /**
     * Creates a MapDataProvider with required dependencies.
     * Uses default converters for data transformation.
     *
     * @param territoryService territory service (nullable for graceful degradation)
     * @param cityManager city manager (nullable for graceful degradation)
     * @param clanManager clan manager (nullable for graceful degradation)
     * @param nationService nation service (nullable for graceful degradation)
     */
    public MapDataProvider(TerritoryClaimService territoryService,
                          CityManager cityManager,
                          ClanManager clanManager,
                          NationService nationService) {
        this(territoryService, cityManager, clanManager, nationService,
             MapDataProvider::convertTerritoryDefault,
             MapDataProvider::convertCityDefaultSafe);
    }

    private static CityData convertCityDefaultSafe(City city) {
        // 计算城市中心坐标
        Integer centerX = null;
        Integer centerZ = null;

        // 方法1：使用出生点
        Location spawnPoint = city.getSpawnPoint();
        if (spawnPoint != null) {
            centerX = spawnPoint.getBlockX();
            centerZ = spawnPoint.getBlockZ();
        }

        return new CityData(
            city.getId().toString(),
            city.getName(),
            city.getNationId() != null ? city.getNationId().toString() : null,
            null, // nationName 需要单独处理
            city.getType().name(),
            city.getLevel(),
            city.getResidentCount(),
            centerX,
            centerZ,
            null
        );
    }

    /**
     * Creates a MapDataProvider with custom converters.
     *
     * @param territoryService territory service
     * @param cityManager city manager
     * @param clanManager clan manager
     * @param nationService nation service
     * @param territoryConverter custom territory converter
     * @param cityConverter custom city converter
     */
    public MapDataProvider(TerritoryClaimService territoryService,
                          CityManager cityManager,
                          ClanManager clanManager,
                          NationService nationService,
                          Function<MultiChunkTerritory, TerritoryData> territoryConverter,
                          Function<City, CityData> cityConverter) {
        this.territoryService = territoryService;
        this.cityManager = cityManager;
        this.clanManager = clanManager;
        this.nationService = nationService;
        this.territoryConverter = territoryConverter != null ? territoryConverter : MapDataProvider::convertTerritoryDefault;
        this.cityConverter = cityConverter != null ? cityConverter : MapDataProvider::convertCityDefaultSafe;
    }

    /**
     * Creates a MapDataProvider using builder pattern (recommended).
     *
     * <p>Example:
     * <pre>{@code
     * MapDataProvider provider = MapDataProvider.builder()
     *     .territoryService(territoryClaimService)
     *     .cityManager(cityManager)
     *     .clanManager(clanManager)
     *     .nationService(nationService)
     *     .build();
     * }</pre>
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for MapDataProvider with fluent API.
     */
    public static class Builder {
        private TerritoryClaimService territoryService;
        private CityManager cityManager;
        private ClanManager clanManager;
        private NationService nationService;
        private Function<MultiChunkTerritory, TerritoryData> territoryConverter;
        private Function<City, CityData> cityConverter;

        public Builder territoryService(TerritoryClaimService service) {
            this.territoryService = service;
            return this;
        }

        public Builder cityManager(CityManager manager) {
            this.cityManager = manager;
            return this;
        }

        public Builder clanManager(ClanManager manager) {
            this.clanManager = manager;
            return this;
        }

        public Builder nationService(NationService service) {
            this.nationService = service;
            return this;
        }

        public Builder territoryConverter(Function<MultiChunkTerritory, TerritoryData> converter) {
            this.territoryConverter = converter;
            return this;
        }

        public Builder cityConverter(Function<City, CityData> converter) {
            this.cityConverter = converter;
            return this;
        }

        /**
         * Builds the MapDataProvider instance.
         * All services are optional - null services will use graceful degradation.
         */
        public MapDataProvider build() {
            return new MapDataProvider(territoryService, cityManager, clanManager,
                    nationService, territoryConverter, cityConverter);
        }
    }

    /**
     * 获取所有Territory数据
     */
    public List<TerritoryData> getTerritories() {
        if (territoryService == null) {
            return Collections.emptyList();
        }
        return territoryService.getAllTerritories().stream()
            .map(this::convertTerritory)
            .collect(Collectors.toList());
    }

    /**
     * 获取所有Nation数据
     */
    public List<NationData> getNations() {
        // 使用 NationService 获取真实的 Nation 数据
        Map<NationId, NationData> nationMap = new HashMap<>();

        // 首先，从 NationService 获取所有国家信息
        if (nationService != null) {
            for (Nation nation : nationService.nations()) {
                NationData nationData = new NationData(
                    nation.id().value().toString(),
                    nation.name(),  // 使用真实国家名称
                    new ArrayList<>(),
                    0
                );
                nationData.governmentType = nation.governmentType().name();
                nationData.level = (int) (nation.experience() / 1000);  // 等级基于经验值
                nationData.memberCount = nation.memberCount();
                nationData.experience = nation.experience();
                nationData.foundedAt = nation.foundedAt().toString();
                nationMap.put(nation.id(), nationData);
            }
        }

        // 然后，从 Territory 收集领土信息并补充国家数据
        if (territoryService != null) {
            for (MultiChunkTerritory territory : territoryService.getAllTerritories()) {
                UUID nationUuid = territory.getNationId();
                if (nationUuid == null) continue;
                NationId nationId = new NationId(nationUuid);

                // 如果国家不在 map 中（可能是旧数据），创建一个临时条目
                NationData nation = nationMap.computeIfAbsent(nationId, id -> {
                    // 尝试从 NationService 获取
                    if (nationService != null) {
                        Optional<Nation> n = nationService.nationById(id);
                        if (n.isPresent()) {
                            Nation nationObj = n.get();
                            NationData data = new NationData(
                                id.value().toString(),
                                nationObj.name(),
                                new ArrayList<>(),
                                0
                            );
                            data.governmentType = nationObj.governmentType().name();
                            return data;
                        }
                    }
                    // 降级：使用 ID 前缀作为名称
                    return new NationData(
                        id.value().toString(),
                        "Nation-" + id.value().toString().substring(0, 8),
                        new ArrayList<>(),
                        0
                    );
                });

                nation.territories.add(territory.getId().toString());
                nation.chunkCount += territory.getChunkCount();
            }
        }

        return new ArrayList<>(nationMap.values());
    }

    /**
     * 获取所有City数据
     */
    public List<CityData> getCities() {
        if (cityManager == null) {
            return Collections.emptyList();
        }
        return cityManager.getAllCities().stream()
            .map(this::convertCity)
            .collect(Collectors.toList());
    }

    /**
     * 获取在线玩家数据
     */
    public List<PlayerData> getOnlinePlayers() {
        return Bukkit.getOnlinePlayers().stream()
            .map(this::convertPlayer)
            .collect(Collectors.toList());
    }

    /**
     * 获取统计数据
     */
    public StatsData getStats() {
        int territoryCount = territoryService != null ? territoryService.getAllTerritories().size() : 0;
        int cityCount = cityManager != null ? cityManager.getAllCities().size() : 0;
        int clanCount = clanManager != null ? clanManager.getClanCount() : 0;
        int playerCount = Bukkit.getOnlinePlayers().size();

        // 获取国家数量
        int nationCount = nationService != null ? (int) nationService.nations().stream().count() : 0;

        var claimStats = territoryService != null ? territoryService.getStats() : null;

        return new StatsData(
            territoryCount,
            claimStats != null ? claimStats.totalChunks() : 0,
            nationCount,
            cityCount,
            clanCount,
            playerCount,
            System.currentTimeMillis()
        );
    }

    /**
     * 转换Territory（使用钩子）
     */
    private TerritoryData convertTerritory(MultiChunkTerritory territory) {
        return territoryConverter.apply(territory);
    }

    /**
     * 默认的 Territory 转换器
     */
    private static TerritoryData convertTerritoryDefault(MultiChunkTerritory territory) {
        List<ChunkData> chunks = territory.getChunks().stream()
            .map(coord -> new ChunkData(
                coord.world(),
                coord.x(),
                coord.z()
            ))
            .collect(Collectors.toList());

        return new TerritoryData(
            territory.getId().toString(),
            territory.getName(),
            territory.getNationId() != null ? territory.getNationId().toString() : null,
            null, // nationName 需要通过 nationService 获取
            territory.getType().name(),
            territory.getType().getColorCode(),
            territory.getLevel(),
            chunks,
            territory.isConnected()
        );
    }

    /**
     * 转换City（使用钩子）
     */
    private CityData convertCity(City city) {
        return cityConverter.apply(city);
    }

    /**
     * 默认的 City 转换器
     */
    private CityData convertCityDefault(City city) {
        // 计算城市中心坐标
        Integer centerX = null;
        Integer centerZ = null;

        // 方法1：使用出生点
        Location spawnPoint = city.getSpawnPoint();
        if (spawnPoint != null) {
            centerX = spawnPoint.getBlockX();
            centerZ = spawnPoint.getBlockZ();
        }

        // 方法2：使用领土计算中心点
        if ((centerX == null || centerZ == null) && territoryService != null) {
            Set<UUID> territories = city.getTerritories();
            if (territories != null && !territories.isEmpty()) {
                try {
                    List<MultiChunkTerritory> territoryList = territories.stream()
                        .map(territoryService::getTerritory)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());

                    if (!territoryList.isEmpty()) {
                        long sumX = 0;
                        long sumZ = 0;
                        int chunkCount = 0;

                        for (MultiChunkTerritory territory : territoryList) {
                            try {
                                for (var chunkCoord : territory.getChunks()) {
                                    sumX += (long) chunkCoord.x() * 16 + 8;
                                    sumZ += (long) chunkCoord.z() * 16 + 8;
                                    chunkCount++;
                                }
                            } catch (IllegalStateException | UnsupportedOperationException e) {
                                // 忽略不支持的操作或无效状态
                            }
                        }

                        if (chunkCount > 0) {
                            centerX = (int) (sumX / chunkCount);
                            centerZ = (int) (sumZ / chunkCount);
                        }
                    }
                } catch (NullPointerException | IllegalArgumentException e) {
                    // 忽略参数错误或空指针
                }
            }
        }

        return new CityData(
            city.getId().toString(),
            city.getName(),
            city.getNationId() != null ? city.getNationId().toString() : null,
            null, // nationName 需要单独处理
            city.getType().name(),
            city.getLevel(),
            city.getResidentCount(),
            centerX,
            centerZ,
            city.getColoredTypeName()
        );
    }

    /**
     * 转换玩家
     */
    private PlayerData convertPlayer(Player player) {
        var clan = clanManager != null ? clanManager.getPlayerClan(player.getUniqueId()) : null;
        var city = cityManager != null ? cityManager.getPlayerCity(player.getUniqueId()) : null;

        return new PlayerData(
            player.getUniqueId().toString(),
            player.getName(),
            player.getLocation().getBlockX(),
            player.getLocation().getBlockY(),
            player.getLocation().getBlockZ(),
            player.getWorld().getName(),
            clan != null ? clan.getTag() : null,
            city != null ? city.getName() : null,
            null // nation 需要单独处理
        );
    }

    // ==================== 数据类 ====================

    public record TerritoryData(
        String id,
        String name,
        String nationId,
        String nationName,
        String type,
        String color,
        int level,
        List<ChunkData> chunks,
        boolean connected
    ) {}

    public record ChunkData(
        String world,
        int x,
        int z
    ) {}

    public static class NationData {
        public final String id;
        public final String name;
        public final List<String> territories;
        public int chunkCount;
        public String governmentType;
        public int level;
        public int memberCount;
        public long experience;
        public String foundedAt;

        public NationData(String id, String name, List<String> territories, int chunkCount) {
            this.id = id;
            this.name = name;
            this.territories = territories;
            this.chunkCount = chunkCount;
            this.governmentType = "MONARCHY";
            this.level = 1;
            this.memberCount = 0;
            this.experience = 0;
            this.foundedAt = "";
        }
    }

    public record CityData(
        String id,
        String name,
        String nationId,
        String nationName,
        String type,
        int level,
        int residents,
        Integer x,
        Integer z,
        String displayType
    ) {}

    public record PlayerData(
        String id,
        String name,
        int x,
        int y,
        int z,
        String world,
        String clan,
        String city,
        String nation
    ) {}

    public record StatsData(
        int territories,
        int chunks,
        int nations,
        int cities,
        int clans,
        int onlinePlayers,
        long timestamp
    ) {}

    // ==================== 标记数据方法 ====================

    /**
     * 设置标记服务
     */
    public void setMarkerService(MapMarkerService markerService) {
        this.markerService = markerService;
    }

    /**
     * 获取所有自定义标记
     */
    public List<MarkerData> getCustomMarkers() {
        if (markerService == null) {
            return Collections.emptyList();
        }
        return List.of(); // 需要通过MarkerCommandService获取
    }

    /**
     * 获取所有动态标记
     */
    public List<MarkerData> getDynamicMarkers() {
        if (markerService == null) {
            return Collections.emptyList();
        }
        return List.of(); // 需要通过MarkerCommandService获取
    }

    /**
     * 获取玩家的可见标记
     */
    public List<MarkerData> getVisibleMarkers(UUID playerId) {
        if (markerService == null) {
            return Collections.emptyList();
        }
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return List.of();
        }
        String nationId = getPlayerNationId(player);
        List<MapMarker> markers = markerService.getVisibleMarkers(player, nationId);
        return markers.stream()
            .map(this::convertMarker)
            .collect(Collectors.toList());
    }

    /**
     * 获取特定国家的标记
     */
    public List<MarkerData> getNationMarkers(String nationId) {
        if (markerService == null) {
            return Collections.emptyList();
        }
        List<MapMarker> markers = new ArrayList<>();

        // 添加国家自定义标记
        for (DynamicMapMarker marker : markerService.getNationDynamicMarkers(nationId)) {
            markers.add(marker.toMapMarker());
        }

        return markers.stream()
            .map(this::convertMarker)
            .collect(Collectors.toList());
    }

    /**
     * 获取按分类分组的标记
     */
    public Map<String, List<MarkerData>> getMarkersGroupedByCategory() {
        Map<String, List<MarkerData>> grouped = new LinkedHashMap<>();
        for (MapMarkerCategory category : MapMarkerCategory.values()) {
            grouped.put(category.name(), new ArrayList<>());
        }
        return grouped;
    }

    /**
     * 搜索标记
     */
    public List<MarkerData> searchMarkers(String query, String world) {
        if (markerService == null) {
            return Collections.emptyList();
        }
        List<MapMarker> allMarkers = new ArrayList<>();
        String lowerQuery = query != null ? query.toLowerCase() : "";

        // 收集所有标记
        for (DynamicMapMarker marker : getAllDynamicMarkers()) {
            if (!marker.isExpired()) {
                allMarkers.add(marker.toMapMarker());
            }
        }

        return allMarkers.stream()
            .filter(m -> lowerQuery.isEmpty() || m.label().toLowerCase().contains(lowerQuery))
            .filter(m -> world == null || world.isEmpty() || world.equals(m.world()))
            .map(this::convertMarker)
            .collect(Collectors.toList());
    }

    // ==================== 私有辅助方法 ====================

    private String getPlayerNationId(Player player) {
        if (nationService == null) {
            return null;
        }
        Optional<Nation> nation = nationService.nationOf(player.getUniqueId());
        return nation.map(n -> n.id().toString()).orElse(null);
    }

    private List<DynamicMapMarker> getAllDynamicMarkers() {
        // 从markerService获取所有动态标记
        // 这里需要添加相应方法到markerService
        return List.of();
    }

    private MarkerData convertMarker(MapMarker marker) {
        Map<String, String> meta = marker.metadata();
        return new MarkerData(
            marker.id(),
            marker.label(),
            marker.world(),
            marker.x(),
            marker.z(),
            meta.get("category") != null ? meta.get("category") : "custom",
            meta.get("color") != null ? meta.get("color") : "#3B82F6",
            meta.get("description") != null ? meta.get("description") : "",
            meta.get("ownerId") != null ? meta.get("ownerId") : "",
            meta.get("nationId") != null ? meta.get("nationId") : "",
            Boolean.parseBoolean(meta.getOrDefault("pinned", "false")),
            Boolean.parseBoolean(meta.getOrDefault("pulse", "false")),
            meta.get("expiresAt") != null ? meta.get("expiresAt") : "",
            Integer.parseInt(meta.getOrDefault("priority", "0"))
        );
    }

    /**
     * 标记数据DTO
     */
    public record MarkerData(
        String id,
        String name,
        String world,
        double x,
        double z,
        String category,
        String color,
        String description,
        String ownerId,
        String nationId,
        boolean pinned,
        boolean pulse,
        String expiresAt,
        int priority
    ) {}
}
