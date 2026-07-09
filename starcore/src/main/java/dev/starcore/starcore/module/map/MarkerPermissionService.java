package dev.starcore.starcore.module.map;

import dev.starcore.starcore.module.map.model.MapMarkerCategory;
import dev.starcore.starcore.module.map.model.MapMarkerPermission;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 地图标记权限服务
 * 管理玩家对地图标记的权限
 */
public class MarkerPermissionService implements MapMarkerService.MarkerPermissionService {

    private final Plugin plugin;
    private final Map<String, Integer> playerMarkerCounts = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> playerPermissions = new ConcurrentHashMap<>();

    // 默认配置
    private int defaultMaxMarkers = 50;
    private int vipMaxMarkers = 100;
    private int adminMaxMarkers = 500;

    public MarkerPermissionService(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean canCreateMarker(Player player, MapMarkerCategory category) {
        if (!category.isPlayerCreatable()) {
            return hasPermission(player, MapMarkerPermission.CREATE_CUSTOM);
        }

        return switch (category) {
            case CUSTOM_WAYPOINT -> hasPermission(player, MapMarkerPermission.CREATE_WAYPOINT);
            case CUSTOM_HOME -> hasPermission(player, MapMarkerPermission.CREATE_HOME);
            case CUSTOM_SHOP -> hasPermission(player, MapMarkerPermission.CREATE_SHOP);
            case CUSTOM_FARM -> hasPermission(player, MapMarkerPermission.CREATE_FARM);
            case CUSTOM_SPAWN -> hasPermission(player, MapMarkerPermission.CREATE_HOME);
            case CUSTOM_BATTLE -> hasPermission(player, MapMarkerPermission.CREATE_BATTLE);
            case CUSTOM_EVENT -> hasPermission(player, MapMarkerPermission.CREATE_EVENT);
            default -> hasPermission(player, MapMarkerPermission.CREATE_CUSTOM);
        };
    }

    @Override
    public boolean hasPermission(Player player, MapMarkerPermission permission) {
        // 检查玩家是否拥有该权限节点
        if (player.hasPermission(permission.getPermissionNode())) {
            return true;
        }

        // 检查玩家是否拥有通配符权限
        String node = permission.getPermissionNode();
        if (node.startsWith("starcore.map.view.")) {
            return player.hasPermission("starcore.map.view.all");
        }
        if (node.startsWith("starcore.map.create.")) {
            return player.hasPermission("starcore.map.create.all") ||
                   player.hasPermission("starcore.map.create.custom");
        }
        if (node.startsWith("starcore.map.edit.")) {
            return player.hasPermission("starcore.map.edit.all");
        }
        if (node.startsWith("starcore.map.delete.")) {
            return player.hasPermission("starcore.map.delete.all");
        }
        if (node.startsWith("starcore.map.pin.")) {
            return player.hasPermission("starcore.map.pin.global");
        }

        // 检查自定义权限缓存
        Set<String> perms = playerPermissions.get(player.getUniqueId());
        if (perms != null && perms.contains(permission.getPermissionNode())) {
            return true;
        }

        return false;
    }

    @Override
    public int getMaxMarkersForPlayer(Player player) {
        if (player.hasPermission("starcore.map.admin")) {
            return adminMaxMarkers;
        }
        if (player.hasPermission("starcore.map.vip")) {
            return vipMaxMarkers;
        }
        return defaultMaxMarkers;
    }

    /**
     * 检查玩家是否可以编辑特定标记
     */
    public boolean canEditMarker(Player player, String ownerId) {
        if (hasPermission(player, MapMarkerPermission.EDIT_ALL)) {
            return true;
        }
        return player.getUniqueId().toString().equals(ownerId) &&
               hasPermission(player, MapMarkerPermission.EDIT_OWN);
    }

    /**
     * 检查玩家是否可以删除特定标记
     */
    public boolean canDeleteMarker(Player player, String ownerId, String nationId) {
        // 管理员可以删除所有
        if (hasPermission(player, MapMarkerPermission.DELETE_ALL)) {
            return true;
        }

        // 所有者可以删除自己的
        if (player.getUniqueId().toString().equals(ownerId) &&
            hasPermission(player, MapMarkerPermission.DELETE_OWN)) {
            return true;
        }

        // 国家官员可以删除国家标记
        if (nationId != null && hasPermission(player, MapMarkerPermission.DELETE_NATION)) {
            // 这里可以添加更细粒度的国家权限检查
            return true;
        }

        return false;
    }

    /**
     * 获取玩家可以查看的标记分类
     */
    public Set<MapMarkerCategory> getViewableCategories(Player player) {
        Set<MapMarkerCategory> categories = EnumSet.noneOf(MapMarkerCategory.class);

        if (hasPermission(player, MapMarkerPermission.VIEW_ALL)) {
            categories.addAll(EnumSet.allOf(MapMarkerCategory.class));
            return categories;
        }

        // 基础权限
        categories.add(MapMarkerCategory.PLAYER);
        categories.add(MapMarkerCategory.TERRITORY);
        categories.add(MapMarkerCategory.RESOURCE);

        // 公共标记
        if (hasPermission(player, MapMarkerPermission.VIEW_PUBLIC)) {
            categories.add(MapMarkerCategory.CUSTOM_PUBLIC);
        }

        // 国家标记
        if (hasPermission(player, MapMarkerPermission.VIEW_NATION)) {
            categories.add(MapMarkerCategory.NATION);
            categories.add(MapMarkerCategory.NATION_CAPITAL);
            categories.add(MapMarkerCategory.NATION_BATTLEFIELD);
            categories.add(MapMarkerCategory.NATION_PORT);
            categories.add(MapMarkerCategory.NATION_FORTRESS);
            categories.add(MapMarkerCategory.NATION_TRADE_HUB);
        }

        // 友方标记
        if (hasPermission(player, MapMarkerPermission.VIEW_FRIENDLY)) {
            categories.add(MapMarkerCategory.CUSTOM_PLAYER);
            categories.add(MapMarkerCategory.CUSTOM_WAYPOINT);
            categories.add(MapMarkerCategory.CUSTOM_HOME);
        }

        // 动态标记
        if (hasPermission(player, MapMarkerPermission.VIEW_DYNAMIC)) {
            categories.add(MapMarkerCategory.DYNAMIC_WAR);
            categories.add(MapMarkerCategory.DYNAMIC_PVP);
            categories.add(MapMarkerCategory.DYNAMIC_SAFE);
            categories.add(MapMarkerCategory.DYNAMIC_TRADING);
            categories.add(MapMarkerCategory.DYNAMIC_DUNGEON);
            categories.add(MapMarkerCategory.DYNAMIC_BOSS);
        }

        return categories;
    }

    /**
     * 添加玩家自定义权限（用于运行时授权）
     */
    public void addPermission(UUID playerId, String permissionNode) {
        playerPermissions
            .computeIfAbsent(playerId, k -> new HashSet<>())
            .add(permissionNode);
    }

    /**
     * 移除玩家自定义权限
     */
    public void removePermission(UUID playerId, String permissionNode) {
        Set<String> perms = playerPermissions.get(playerId);
        if (perms != null) {
            perms.remove(permissionNode);
        }
    }

    /**
     * 清除玩家所有自定义权限
     */
    public void clearPermissions(UUID playerId) {
        playerPermissions.remove(playerId);
    }

    /**
     * 设置默认最大标记数
     */
    public void setDefaultMaxMarkers(int count) {
        this.defaultMaxMarkers = count;
    }

    /**
     * 设置VIP最大标记数
     */
    public void setVipMaxMarkers(int count) {
        this.vipMaxMarkers = count;
    }

    /**
     * 设置管理员最大标记数
     */
    public void setAdminMaxMarkers(int count) {
        this.adminMaxMarkers = count;
    }

    /**
     * 从配置文件加载权限设置
     */
    public void loadFromConfig() {
        // 从配置文件读取设置
        var config = plugin.getConfig();
        this.defaultMaxMarkers = config.getInt("map.markers.default-max", 50);
        this.vipMaxMarkers = config.getInt("map.markers.vip-max", 100);
        this.adminMaxMarkers = config.getInt("map.markers.admin-max", 500);
    }
}
