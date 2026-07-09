package dev.starcore.starcore.module.map;
import java.util.Optional;

import dev.starcore.starcore.module.map.model.*;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 地图标记服务
 * 管理所有类型的地图标记（自定义标记、动态标记、系统标记）
 */
public class MapMarkerService {
    private final Map<String, CustomMapMarker> customMarkers = new ConcurrentHashMap<>();
    private final Map<String, DynamicMapMarker> dynamicMarkers = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> playerMarkerIndex = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> nationMarkerIndex = new ConcurrentHashMap<>();

    // 标记变化监听器
    private final List<Consumer<MarkerChangeEvent>> listeners = new ArrayList<>();

    // 持久化支持
    private MarkerPersistenceService persistenceService;

    // 权限检查器
    private MarkerPermissionService permissionService;

    public MapMarkerService() {
    }

    public void setPersistenceService(MarkerPersistenceService service) {
        this.persistenceService = service;
    }

    public void setPermissionService(MarkerPermissionService service) {
        this.permissionService = service;
    }

    // ==================== 自定义标记管理 ====================

    /**
     * 创建自定义标记
     */
    public Optional<CustomMapMarker> createCustomMarker(Player player, CustomMapMarker marker) {
        // 权限检查
        if (permissionService != null && !permissionService.canCreateMarker(player, marker.getCategory())) {
            return Optional.empty();
        }

        // 限制检查
        int playerMarkerCount = getPlayerMarkerCount(player.getUniqueId());
        int maxMarkers = getMaxMarkersForPlayer(player);
        if (playerMarkerCount >= maxMarkers) {
            return Optional.empty();
        }

        // 验证分类
        if (!marker.getCategory().isPlayerCreatable()) {
            return Optional.empty();
        }

        CustomMapMarker savedMarker = CustomMapMarker.builder()
            .id(UUID.randomUUID().toString())
            .ownerId(player.getUniqueId())
            .name(marker.getName())
            .world(marker.getWorld())
            .position(marker.getX(), marker.getY(), marker.getZ())
            .category(marker.getCategory())
            .color(marker.getColor())
            .description(marker.getDescription())
            .pinned(marker.isPinned())
            .visibleToNation(marker.isVisibleToNation())
            .visibleToAll(marker.isVisibleToAll())
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .metadata(marker.getMetadata())
            .build();

        customMarkers.put(savedMarker.getId(), savedMarker);
        indexMarker(savedMarker);

        // 持久化
        if (persistenceService != null) {
            persistenceService.saveMarker(savedMarker);
        }

        // 通知监听器
        notifyListeners(new MarkerChangeEvent(MarkerChangeType.CREATED, savedMarker));

        return Optional.of(savedMarker);
    }

    /**
     * 更新自定义标记
     */
    public Optional<CustomMapMarker> updateCustomMarker(Player player, String markerId,
                                                         String name, String description,
                                                         String color, boolean pinned,
                                                         boolean visibleToNation, boolean visibleToAll) {
        CustomMapMarker marker = customMarkers.get(markerId);
        if (marker == null) {
            return Optional.empty();
        }

        // 权限检查
        if (permissionService != null) {
            if (!marker.getOwnerId().equals(player.getUniqueId()) &&
                !permissionService.hasPermission(player, MapMarkerPermission.EDIT_ALL)) {
                return Optional.empty();
            }
        }

        // 移除旧索引
        unindexMarker(marker);

        // 更新标记
        CustomMapMarker updated = CustomMapMarker.builder()
            .id(marker.getId())
            .ownerId(marker.getOwnerId())
            .name(name != null ? name : marker.getName())
            .world(marker.getWorld())
            .position(marker.getX(), marker.getY(), marker.getZ())
            .category(marker.getCategory())
            .color(color != null ? color : marker.getColor())
            .description(description != null ? description : marker.getDescription())
            .pinned(pinned)
            .visibleToNation(visibleToNation)
            .visibleToAll(visibleToAll)
            .createdAt(marker.getCreatedAt())
            .updatedAt(Instant.now())
            .metadata(marker.getMetadata())
            .build();

        customMarkers.put(markerId, updated);
        indexMarker(updated);

        // 持久化
        if (persistenceService != null) {
            persistenceService.saveMarker(updated);
        }

        // 通知监听器
        notifyListeners(new MarkerChangeEvent(MarkerChangeType.UPDATED, updated));

        return Optional.of(updated);
    }

    /**
     * 删除自定义标记
     */
    public boolean deleteCustomMarker(Player player, String markerId) {
        CustomMapMarker marker = customMarkers.get(markerId);
        if (marker == null) {
            return false;
        }

        // 权限检查
        if (permissionService != null) {
            boolean isOwner = marker.getOwnerId().equals(player.getUniqueId());
            boolean canDeleteOwn = permissionService.hasPermission(player, MapMarkerPermission.DELETE_OWN);
            boolean canDeleteAll = permissionService.hasPermission(player, MapMarkerPermission.DELETE_ALL);

            if (!isOwner && !canDeleteAll) {
                return false;
            }
            if (isOwner && !canDeleteOwn && !canDeleteAll) {
                return false;
            }
        }

        unindexMarker(marker);
        customMarkers.remove(markerId);

        // 持久化
        if (persistenceService != null) {
            persistenceService.deleteMarker(markerId);
        }

        // 通知监听器
        notifyListeners(new MarkerChangeEvent(MarkerChangeType.DELETED, marker));

        return true;
    }

    /**
     * 获取玩家的所有标记
     */
    public List<CustomMapMarker> getPlayerMarkers(UUID playerId) {
        Set<String> markerIds = playerMarkerIndex.get(playerId.toString());
        if (markerIds == null) {
            return List.of();
        }
        return markerIds.stream()
            .map(customMarkers::get)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    // ==================== 动态标记管理 ====================

    /**
     * 创建动态标记
     */
    public DynamicMapMarker createDynamicMarker(DynamicMapMarker marker) {
        dynamicMarkers.put(marker.getId(), marker);

        if (marker.getNationId() != null) {
            nationMarkerIndex
                .computeIfAbsent(marker.getNationId(), k -> ConcurrentHashMap.newKeySet())
                .add(marker.getId());
        }

        // 通知监听器
        notifyListeners(new MarkerChangeEvent(MarkerChangeType.CREATED, marker));

        return marker;
    }

    /**
     * 删除动态标记
     */
    public boolean deleteDynamicMarker(String markerId) {
        DynamicMapMarker marker = dynamicMarkers.remove(markerId);
        if (marker == null) {
            return false;
        }

        if (marker.getNationId() != null) {
            Set<String> nationMarkers = nationMarkerIndex.get(marker.getNationId());
            if (nationMarkers != null) {
                nationMarkers.remove(markerId);
            }
        }

        // 通知监听器
        notifyListeners(new MarkerChangeEvent(MarkerChangeType.DELETED, marker));

        return true;
    }

    /**
     * 清理过期标记
     */
    public int cleanupExpiredMarkers() {
        List<String> expiredIds = dynamicMarkers.entrySet().stream()
            .filter(e -> e.getValue().isExpired())
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());

        for (String id : expiredIds) {
            deleteDynamicMarker(id);
        }

        return expiredIds.size();
    }

    /**
     * 获取国家的动态标记
     */
    public List<DynamicMapMarker> getNationDynamicMarkers(String nationId) {
        Set<String> markerIds = nationMarkerIndex.get(nationId);
        if (markerIds == null) {
            return List.of();
        }
        return markerIds.stream()
            .map(dynamicMarkers::get)
            .filter(Objects::nonNull)
            .filter(m -> !m.isExpired())
            .collect(Collectors.toList());
    }

    // ==================== 标记查询 ====================

    /**
     * 获取所有可见的标记（用于玩家）
     */
    public List<MapMarker> getVisibleMarkers(Player player, String playerNationId) {
        List<MapMarker> result = new ArrayList<>();

        // 添加自定义标记
        for (CustomMapMarker marker : customMarkers.values()) {
            if (isMarkerVisibleTo(marker, player, playerNationId)) {
                result.add(marker.toMapMarker());
            }
        }

        // 添加动态标记
        for (DynamicMapMarker marker : dynamicMarkers.values()) {
            if (!marker.isExpired() && isDynamicMarkerVisible(marker, player, playerNationId)) {
                result.add(marker.toMapMarker());
            }
        }

        // 按优先级排序
        result.sort((a, b) -> {
            int priorityA = Integer.parseInt(a.metadata().getOrDefault("priority", "0"));
            int priorityB = Integer.parseInt(b.metadata().getOrDefault("priority", "0"));
            return Integer.compare(priorityB, priorityA);
        });

        return result;
    }

    /**
     * 按分类获取标记
     */
    public List<MapMarker> getMarkersByCategory(MapMarkerCategory category) {
        List<MapMarker> result = new ArrayList<>();

        for (CustomMapMarker marker : customMarkers.values()) {
            if (marker.getCategory() == category) {
                result.add(marker.toMapMarker());
            }
        }

        for (DynamicMapMarker marker : dynamicMarkers.values()) {
            if (marker.getCategory() == category && !marker.isExpired()) {
                result.add(marker.toMapMarker());
            }
        }

        return result;
    }

    /**
     * 按世界获取标记
     */
    public List<MapMarker> getMarkersByWorld(String world) {
        List<MapMarker> result = new ArrayList<>();

        for (CustomMapMarker marker : customMarkers.values()) {
            if (marker.getWorld().equals(world)) {
                result.add(marker.toMapMarker());
            }
        }

        for (DynamicMapMarker marker : dynamicMarkers.values()) {
            if (marker.getWorld().equals(world) && !marker.isExpired()) {
                result.add(marker.toMapMarker());
            }
        }

        return result;
    }

    // ==================== 内部方法 ====================

    private void indexMarker(CustomMapMarker marker) {
        playerMarkerIndex
            .computeIfAbsent(marker.getOwnerId().toString(), k -> ConcurrentHashMap.newKeySet())
            .add(marker.getId());

        if (marker.isVisibleToNation()) {
            String nationId = marker.getMetadata().get("nationId");
            if (nationId != null) {
                nationMarkerIndex
                    .computeIfAbsent(nationId, k -> ConcurrentHashMap.newKeySet())
                    .add(marker.getId());
            }
        }
    }

    private void unindexMarker(CustomMapMarker marker) {
        Set<String> playerMarkers = playerMarkerIndex.get(marker.getOwnerId().toString());
        if (playerMarkers != null) {
            playerMarkers.remove(marker.getId());
        }

        if (marker.isVisibleToNation()) {
            String nationId = marker.getMetadata().get("nationId");
            if (nationId != null) {
                Set<String> nationMarkers = nationMarkerIndex.get(nationId);
                if (nationMarkers != null) {
                    nationMarkers.remove(marker.getId());
                }
            }
        }
    }

    private boolean isMarkerVisibleTo(CustomMapMarker marker, Player player, String playerNationId) {
        // 公开标记
        if (marker.isVisibleToAll()) {
            return true;
        }

        // 国家可见标记
        if (marker.isVisibleToNation()) {
            String markerNationId = marker.getMetadata().get("nationId");
            if (markerNationId != null && markerNationId.equals(playerNationId)) {
                return true;
            }
        }

        // 所有者可见
        return marker.getOwnerId().equals(player.getUniqueId());
    }

    private boolean isDynamicMarkerVisible(DynamicMapMarker marker, Player player, String playerNationId) {
        // 管理员可见所有
        if (permissionService != null && permissionService.hasPermission(player, MapMarkerPermission.VIEW_ALL)) {
            return true;
        }

        // 国家成员可见
        if (marker.getNationId() != null && marker.getNationId().equals(playerNationId)) {
            return true;
        }

        // 敌人可见战争区域
        if (marker.getCategory() == MapMarkerCategory.DYNAMIC_WAR) {
            return true; // 所有人可见战争区域
        }

        return false;
    }

    private int getPlayerMarkerCount(UUID playerId) {
        Set<String> markers = playerMarkerIndex.get(playerId.toString());
        return markers != null ? markers.size() : 0;
    }

    private int getMaxMarkersForPlayer(Player player) {
        if (permissionService != null) {
            return permissionService.getMaxMarkersForPlayer(player);
        }
        return 50; // 默认最大标记数
    }

    // ==================== 监听器管理 ====================

    public void addListener(Consumer<MarkerChangeEvent> listener) {
        listeners.add(listener);
    }

    public void removeListener(Consumer<MarkerChangeEvent> listener) {
        listeners.remove(listener);
    }

    private void notifyListeners(MarkerChangeEvent event) {
        for (Consumer<MarkerChangeEvent> listener : listeners) {
            try {
                listener.accept(event);
            } catch (Exception ignored) {
            }
        }
    }

    // ==================== 静态工具方法 ====================

    public static Map<String, String> wrapMetadata(Map<String, String> metadata) {
        if (metadata == null) {
            return Map.of();
        }
        return Map.copyOf(metadata);
    }

    // ==================== 内部类 ====================

    public enum MarkerChangeType {
        CREATED,
        UPDATED,
        DELETED
    }

    public record MarkerChangeEvent(MarkerChangeType type, Object marker) {}

    public interface MarkerPersistenceService {
        void saveMarker(CustomMapMarker marker);
        void deleteMarker(String markerId);
        List<CustomMapMarker> loadAllMarkers();
    }

    public interface MarkerPermissionService {
        boolean canCreateMarker(Player player, MapMarkerCategory category);
        boolean hasPermission(Player player, MapMarkerPermission permission);
        int getMaxMarkersForPlayer(Player player);
    }
}
