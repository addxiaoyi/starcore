package dev.starcore.starcore.webmap;

import dev.starcore.starcore.module.map.MapMarkerService;
import dev.starcore.starcore.module.map.model.CustomMapMarker;
import dev.starcore.starcore.module.map.model.DynamicMapMarker;
import dev.starcore.starcore.module.map.model.MapMarker;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

/**
 * WebMap标记数据提供者
 * 将地图标记数据提供给WebMap前端
 */
public class WebMapMarkerProvider {

    private final MapMarkerService markerService;

    public WebMapMarkerProvider(MapMarkerService markerService) {
        this.markerService = markerService;
    }

    /**
     * 获取玩家的可见标记
     */
    public List<MarkerDto> getVisibleMarkers(Player player) {
        String nationId = getPlayerNationId(player);
        List<MapMarker> markers = markerService.getVisibleMarkers(player, nationId);
        return markers.stream()
            .map(this::toDto)
            .collect(Collectors.toList());
    }

    /**
     * 获取所有公开标记
     */
    public List<MarkerDto> getPublicMarkers() {
        List<MarkerDto> result = new ArrayList<>();

        // 获取所有自定义标记中的公开标记
        for (CustomMapMarker marker : getAllCustomMarkers()) {
            if (marker.isVisibleToAll()) {
                result.add(toDto(marker.toMapMarker()));
            }
        }

        // 获取所有动态标记
        for (DynamicMapMarker marker : getAllDynamicMarkers()) {
            if (!marker.isExpired()) {
                result.add(toDto(marker.toMapMarker()));
            }
        }

        return result;
    }

    /**
     * 获取特定国家的标记
     */
    public List<MarkerDto> getNationMarkers(String nationId) {
        List<MarkerDto> result = new ArrayList<>();

        // 国家可见的标记
        for (CustomMapMarker marker : getAllCustomMarkers()) {
            if (marker.isVisibleToNation() &&
                nationId.equals(marker.getMetadata().get("nationId"))) {
                result.add(toDto(marker.toMapMarker()));
            }
        }

        // 国家动态标记
        for (DynamicMapMarker marker : markerService.getNationDynamicMarkers(nationId)) {
            result.add(toDto(marker.toMapMarker()));
        }

        return result;
    }

    /**
     * 获取按分类分组的标记
     */
    public Map<String, List<MarkerDto>> getMarkersGroupedByCategory() {
        Map<String, List<MarkerDto>> grouped = new LinkedHashMap<>();

        for (MarkerDto marker : getPublicMarkers()) {
            grouped.computeIfAbsent(marker.category, k -> new ArrayList<>()).add(marker);
        }

        return grouped;
    }

    /**
     * 搜索标记
     */
    public List<MarkerDto> searchMarkers(String query, String world) {
        List<MarkerDto> result = new ArrayList<>();
        String lowerQuery = query != null ? query.toLowerCase() : "";

        for (MarkerDto marker : getPublicMarkers()) {
            boolean matchesQuery = lowerQuery.isEmpty() ||
                marker.name.toLowerCase().contains(lowerQuery) ||
                (marker.description != null && marker.description.toLowerCase().contains(lowerQuery));

            boolean matchesWorld = world == null || world.isEmpty() ||
                world.equals(marker.world);

            if (matchesQuery && matchesWorld) {
                result.add(marker);
            }
        }

        return result;
    }

    // ==================== 内部方法 ====================

    private String getPlayerNationId(Player player) {
        // 从其他服务获取玩家所属国家
        // 这里简化处理，实际应从 NationService 获取
        return player.getMetadata("nationId")
            .stream()
            .findFirst()
            .map(m -> m.value().toString())
            .orElse(null);
    }

    private List<CustomMapMarker> getAllCustomMarkers() {
        // 需要从 markerService 获取所有标记
        // 这里需要添加相应方法
        return List.of();
    }

    private List<DynamicMapMarker> getAllDynamicMarkers() {
        // 需要从 markerService 获取所有动态标记
        // 这里需要添加相应方法
        return List.of();
    }

    private MarkerDto toDto(MapMarker marker) {
        Map<String, String> meta = marker.metadata();
        return new MarkerDto(
            marker.id(),
            marker.label(),
            marker.world(),
            marker.x(),
            marker.z(),
            marker.icon(),
            meta.get("color") != null ? meta.get("color") : "#3B82F6",
            meta.get("category") != null ? meta.get("category") : "custom",
            meta.get("description") != null ? meta.get("description") : "",
            Boolean.parseBoolean(meta.getOrDefault("pinned", "false")),
            meta.get("ownerId") != null ? meta.get("ownerId") : "",
            meta.get("nationId") != null ? meta.get("nationId") : "",
            meta.get("expiresAt") != null ? meta.get("expiresAt") : "",
            Integer.parseInt(meta.getOrDefault("priority", "0")),
            Boolean.parseBoolean(meta.getOrDefault("pulse", "false"))
        );
    }

    // ==================== DTO类 ====================

    public record MarkerDto(
        String id,
        String name,
        String world,
        double x,
        double z,
        String icon,
        String color,
        String category,
        String description,
        boolean pinned,
        String ownerId,
        String nationId,
        String expiresAt,
        int priority,
        boolean pulse
    ) {}
}
