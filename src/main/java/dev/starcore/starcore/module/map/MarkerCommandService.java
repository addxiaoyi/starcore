package dev.starcore.starcore.module.map;
import java.util.Optional;

import dev.starcore.starcore.module.map.model.*;
import dev.starcore.starcore.module.map.model.MapMarker;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 地图标记命令处理服务
 * 提供地图标记的CRUD操作接口
 */
public class MarkerCommandService {

    private final MapMarkerService markerService;
    private final MarkerPermissionService permissionService;

    public MarkerCommandService(MapMarkerService markerService, MarkerPermissionService permissionService) {
        this.markerService = markerService;
        this.permissionService = permissionService;
    }

    // ==================== 创建标记 ====================

    /**
     * 创建路径点标记
     */
    public MarkerCreateResult createWaypoint(Player player, String name, String color, String description) {
        return createMarker(player, name, MapMarkerCategory.CUSTOM_WAYPOINT, color, description);
    }

    /**
     * 创建家标记
     */
    public MarkerCreateResult createHome(Player player, String name, String color) {
        return createMarker(player, name, MapMarkerCategory.CUSTOM_HOME, color, "");
    }

    /**
     * 创建商店标记
     */
    public MarkerCreateResult createShop(Player player, String name, String color, String description) {
        return createMarker(player, name, MapMarkerCategory.CUSTOM_SHOP, color, description);
    }

    /**
     * 创建农场标记
     */
    public MarkerCreateResult createFarm(Player player, String name, String color, String description) {
        return createMarker(player, name, MapMarkerCategory.CUSTOM_FARM, color, description);
    }

    /**
     * 创建战场标记
     */
    public MarkerCreateResult createBattleMarker(Player player, String name, String description) {
        return createMarker(player, name, MapMarkerCategory.CUSTOM_BATTLE, "#DC2626", description);
    }

    /**
     * 创建事件标记
     */
    public MarkerCreateResult createEventMarker(Player player, String name, String description) {
        return createMarker(player, name, MapMarkerCategory.CUSTOM_EVENT, "#8B5CF6", description);
    }

    /**
     * 创建自定义标记
     */
    public MarkerCreateResult createMarker(Player player, String name, MapMarkerCategory category,
                                            String color, String description) {
        // 验证名称
        if (name == null || name.trim().isEmpty()) {
            return MarkerCreateResult.failure("标记名称不能为空");
        }
        name = name.trim();
        if (name.length() > 32) {
            return MarkerCreateResult.failure("标记名称不能超过32个字符");
        }

        // 验证分类
        if (!category.isPlayerCreatable()) {
            return MarkerCreateResult.failure("该分类不允许玩家创建标记");
        }

        // 权限检查
        if (!permissionService.canCreateMarker(player, category)) {
            return MarkerCreateResult.failure("你没有权限创建此类标记");
        }

        // 验证颜色
        if (color != null && !isValidColor(color)) {
            return MarkerCreateResult.failure("无效的颜色格式");
        }

        // 创建标记
        CustomMapMarker marker = CustomMapMarker.builder()
            .ownerId(player.getUniqueId())
            .name(name)
            .world(player.getWorld().getName())
            .position(player.getLocation().getX(), player.getLocation().getY(), player.getLocation().getZ())
            .category(category)
            .color(color != null ? color : getDefaultColor(category))
            .description(description != null ? description : "")
            .visibleToNation(false)
            .visibleToAll(false)
            .build();

        Optional<CustomMapMarker> created = markerService.createCustomMarker(player, marker);

        if (created.isPresent()) {
            return MarkerCreateResult.success(created.get());
        } else {
            return MarkerCreateResult.failure("创建标记失败，可能已达到最大标记数量限制");
        }
    }

    // ==================== 更新标记 ====================

    /**
     * 更新标记
     */
    public MarkerUpdateResult updateMarker(Player player, String markerId,
                                           String name, String description,
                                           String color, Boolean pinned,
                                           Boolean visibleToNation, Boolean visibleToAll) {
        Optional<CustomMapMarker> updated = markerService.updateCustomMarker(
            player, markerId, name, description, color,
            pinned != null ? pinned : false,
            visibleToNation != null ? visibleToNation : false,
            visibleToAll != null ? visibleToAll : false
        );

        if (updated.isPresent()) {
            return MarkerUpdateResult.success(updated.get());
        } else {
            return MarkerUpdateResult.failure("更新标记失败");
        }
    }

    // ==================== 删除标记 ====================

    /**
     * 删除标记
     */
    public MarkerDeleteResult deleteMarker(Player player, String markerId) {
        boolean deleted = markerService.deleteCustomMarker(player, markerId);

        if (deleted) {
            return MarkerDeleteResult.success();
        } else {
            return MarkerDeleteResult.failure("删除标记失败，你可能没有权限");
        }
    }

    /**
     * 删除所有玩家标记
     */
    public int deleteAllPlayerMarkers(Player player) {
        List<CustomMapMarker> markers = markerService.getPlayerMarkers(player.getUniqueId());
        int count = 0;

        for (CustomMapMarker marker : markers) {
            if (markerService.deleteCustomMarker(player, marker.getId())) {
                count++;
            }
        }

        return count;
    }

    // ==================== 查询标记 ====================

    /**
     * 获取玩家的所有标记
     */
    public List<CustomMapMarker> getPlayerMarkers(Player player) {
        return markerService.getPlayerMarkers(player.getUniqueId());
    }

    /**
     * 获取玩家的标记数量
     */
    public int getPlayerMarkerCount(Player player) {
        return markerService.getPlayerMarkers(player.getUniqueId()).size();
    }

    /**
     * 获取玩家可用的最大标记数
     */
    public int getPlayerMaxMarkers(Player player) {
        return permissionService.getMaxMarkersForPlayer(player);
    }

    /**
     * 按分类获取标记
     */
    public List<CustomMapMarker> getMarkersByCategory(Player player, MapMarkerCategory category) {
        return markerService.getPlayerMarkers(player.getUniqueId()).stream()
            .filter(m -> m.getCategory() == category)
            .collect(Collectors.toList());
    }

    // ==================== 动态标记 ====================

    /**
     * 创建战争区域标记
     */
    public DynamicMapMarker createWarZone(String name, String world, double x, double z,
                                         String nationId, String enemyNationId,
                                         Duration duration) {
        DynamicMapMarker marker = DynamicMapMarker.warZone(name, world, x, z, nationId, enemyNationId, duration);
        return markerService.createDynamicMarker(marker);
    }

    /**
     * 创建PVP区域标记
     */
    public DynamicMapMarker createPvpZone(String name, String world, double x, double z,
                                          Duration duration) {
        DynamicMapMarker marker = DynamicMapMarker.pvpZone(name, world, x, z, duration);
        return markerService.createDynamicMarker(marker);
    }

    /**
     * 创建安全区域标记
     */
    public DynamicMapMarker createSafeZone(String name, String world, double x, double z) {
        DynamicMapMarker marker = DynamicMapMarker.safeZone(name, world, x, z);
        return markerService.createDynamicMarker(marker);
    }

    // ==================== 工具方法 ====================

    private boolean isValidColor(String color) {
        if (color == null || color.isEmpty()) {
            return true;
        }
        return color.matches("^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$");
    }

    private String getDefaultColor(MapMarkerCategory category) {
        return switch (category) {
            case CUSTOM_WAYPOINT -> "#3B82F6";
            case CUSTOM_HOME -> "#22C55E";
            case CUSTOM_SHOP -> "#EAB308";
            case CUSTOM_FARM -> "#84CC16";
            case CUSTOM_SPAWN -> "#06B6D4";
            case CUSTOM_BATTLE -> "#DC2626";
            case CUSTOM_EVENT -> "#8B5CF6";
            default -> "#6B7280";
        };
    }

    // ==================== 结果类 ====================

    public static class MarkerCreateResult {
        private final boolean success;
        private final String message;
        private final CustomMapMarker marker;

        private MarkerCreateResult(boolean success, String message, CustomMapMarker marker) {
            this.success = success;
            this.message = message;
            this.marker = marker;
        }

        public static MarkerCreateResult success(CustomMapMarker marker) {
            return new MarkerCreateResult(true, "标记创建成功", marker);
        }

        public static MarkerCreateResult failure(String message) {
            return new MarkerCreateResult(false, message, null);
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public CustomMapMarker getMarker() { return marker; }
    }

    public static class MarkerUpdateResult {
        private final boolean success;
        private final String message;
        private final CustomMapMarker marker;

        private MarkerUpdateResult(boolean success, String message, CustomMapMarker marker) {
            this.success = success;
            this.message = message;
            this.marker = marker;
        }

        public static MarkerUpdateResult success(CustomMapMarker marker) {
            return new MarkerUpdateResult(true, "标记更新成功", marker);
        }

        public static MarkerUpdateResult failure(String message) {
            return new MarkerUpdateResult(false, message, null);
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public CustomMapMarker getMarker() { return marker; }
    }

    public static class MarkerDeleteResult {
        private final boolean success;
        private final String message;

        private MarkerDeleteResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public static MarkerDeleteResult success() {
            return new MarkerDeleteResult(true, "标记删除成功");
        }

        public static MarkerDeleteResult failure(String message) {
            return new MarkerDeleteResult(false, message);
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
    }
}
