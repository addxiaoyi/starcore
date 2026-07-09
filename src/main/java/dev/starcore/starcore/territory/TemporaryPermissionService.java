package dev.starcore.starcore.territory;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 临时权限服务
 * 管理临时权限的授予、撤销和自动过期
 */
public class TemporaryPermissionService {

    private final JavaPlugin plugin;
    private final TerritoryService territoryService;

    // 临时权限存储 - ID -> TemporaryPermission
    private final Map<UUID, TemporaryPermission> permissions = new ConcurrentHashMap<>();

    // 玩家索引 - GranteeID -> Set<PermissionID>
    private final Map<UUID, Set<UUID>> granteeIndex = new ConcurrentHashMap<>();

    // 领土索引 - TerritoryID -> Set<PermissionID>
    private final Map<UUID, Set<UUID>> territoryIndex = new ConcurrentHashMap<>();

    // 定时任务
    private BukkitTask expiryCheckTask;

    // 配置
    private long maxDuration = 604800000L; // 7天
    private long checkInterval = 300000L; // 5分钟

    public TemporaryPermissionService(JavaPlugin plugin, TerritoryService territoryService) {
        this.plugin = plugin;
        this.territoryService = territoryService;
    }

    // ==================== 启动和关闭 ====================

    /**
     * 启动服务
     */
    public void start() {
        // 启动过期检查定时任务
        expiryCheckTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
            plugin,
            this::checkExpiredPermissions,
            checkInterval / 50, // 转换为ticks
            checkInterval / 50
        );

        plugin.getLogger().info("临时权限服务已启动");
    }

    /**
     * 停止服务
     */
    public void stop() {
        if (expiryCheckTask != null) {
            expiryCheckTask.cancel();
            expiryCheckTask = null;
        }

        plugin.getLogger().info("临时权限服务已停止");
    }

    // ==================== 授予和撤销 ====================

    /**
     * 授予临时权限
     */
    public TemporaryPermission grantPermission(UUID territoryId, UUID granterId,
                                              UUID granteeId, TerritoryPermission permission,
                                              long durationMillis, String reason) {
        // 验证领土存在
        Territory territory = territoryService.getTerritory(territoryId);
        if (territory == null) {
            plugin.getLogger().warning("领土不存在: " + territoryId);
            return null;
        }

        // 验证持续时间
        if (durationMillis > maxDuration) {
            plugin.getLogger().warning("持续时间超过最大限制: " + durationMillis + " > " + maxDuration);
            durationMillis = maxDuration;
        }

        // 创建临时权限
        TemporaryPermission tempPerm = new TemporaryPermission(
            territoryId, granterId, granteeId, permission, durationMillis, reason
        );

        // 存储
        permissions.put(tempPerm.getId(), tempPerm);

        // 更新索引
        granteeIndex.computeIfAbsent(granteeId, k -> ConcurrentHashMap.newKeySet())
            .add(tempPerm.getId());
        territoryIndex.computeIfAbsent(territoryId, k -> ConcurrentHashMap.newKeySet())
            .add(tempPerm.getId());

        plugin.getLogger().info(String.format("授予临时权限: %s -> %s (%s) 持续 %s",
            granterId, granteeId, permission, tempPerm.getFormattedRemainingTime()));

        return tempPerm;
    }

    /**
     * 撤销临时权限
     */
    public boolean revokePermission(UUID permissionId) {
        TemporaryPermission tempPerm = permissions.get(permissionId);
        if (tempPerm == null) {
            return false;
        }

        tempPerm.revoke();
        removePermission(permissionId);

        plugin.getLogger().info("撤销临时权限: " + permissionId);
        return true;
    }

    /**
     * 撤销玩家在领土的所有临时权限
     */
    public int revokeAllPermissions(UUID territoryId, UUID granteeId) {
        List<TemporaryPermission> perms = getPermissions(territoryId, granteeId);

        int count = 0;
        for (TemporaryPermission perm : perms) {
            if (revokePermission(perm.getId())) {
                count++;
            }
        }

        return count;
    }

    /**
     * 撤销玩家的所有临时权限
     */
    public int revokeAllPermissions(UUID granteeId) {
        Set<UUID> permissionIds = granteeIndex.get(granteeId);
        if (permissionIds == null) {
            return 0;
        }

        int count = 0;
        for (UUID permId : new HashSet<>(permissionIds)) {
            if (revokePermission(permId)) {
                count++;
            }
        }

        return count;
    }

    // ==================== 查询方法 ====================

    /**
     * 检查玩家是否有临时权限
     */
    public boolean hasPermission(UUID territoryId, UUID playerId, TerritoryPermission permission) {
        List<TemporaryPermission> perms = getValidPermissions(territoryId, playerId);

        return perms.stream()
            .anyMatch(p -> p.getPermission() == permission);
    }

    /**
     * 获取玩家在领土的所有临时权限
     */
    public List<TemporaryPermission> getPermissions(UUID territoryId, UUID playerId) {
        Set<UUID> permissionIds = granteeIndex.get(playerId);
        if (permissionIds == null) {
            return Collections.emptyList();
        }

        return permissionIds.stream()
            .map(permissions::get)
            .filter(Objects::nonNull)
            .filter(p -> p.getTerritoryId().equals(territoryId))
            .collect(Collectors.toList());
    }

    /**
     * 获取玩家在领土的所有有效临时权限
     */
    public List<TemporaryPermission> getValidPermissions(UUID territoryId, UUID playerId) {
        return getPermissions(territoryId, playerId).stream()
            .filter(TemporaryPermission::isValid)
            .collect(Collectors.toList());
    }

    /**
     * 获取玩家的所有临时权限
     */
    public List<TemporaryPermission> getPermissionsByPlayer(UUID playerId) {
        Set<UUID> permissionIds = granteeIndex.get(playerId);
        if (permissionIds == null) {
            return Collections.emptyList();
        }

        return permissionIds.stream()
            .map(permissions::get)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    /**
     * 获取领土的所有临时权限
     */
    public List<TemporaryPermission> getPermissionsByTerritory(UUID territoryId) {
        Set<UUID> permissionIds = territoryIndex.get(territoryId);
        if (permissionIds == null) {
            return Collections.emptyList();
        }

        return permissionIds.stream()
            .map(permissions::get)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    /**
     * 获取所有临时权限
     */
    public Collection<TemporaryPermission> getAllPermissions() {
        return Collections.unmodifiableCollection(permissions.values());
    }

    // ==================== 过期检查 ====================

    /**
     * 检查并清理过期权限
     */
    public void checkExpiredPermissions() {
        List<UUID> expired = new ArrayList<>();

        for (TemporaryPermission perm : permissions.values()) {
            if (perm.isExpired()) {
                perm.markExpired();
                expired.add(perm.getId());
            }
        }

        // 移除过期权限
        for (UUID permId : expired) {
            removePermission(permId);
        }

        if (!expired.isEmpty()) {
            plugin.getLogger().info("清理了 " + expired.size() + " 个过期的临时权限");
        }
    }

    /**
     * 强制清理所有过期权限
     */
    public int cleanupExpiredPermissions() {
        checkExpiredPermissions();
        return 0;
    }

    // ==================== 内部方法 ====================

    /**
     * 移除权限（内部方法）
     */
    private void removePermission(UUID permissionId) {
        TemporaryPermission perm = permissions.remove(permissionId);
        if (perm == null) {
            return;
        }

        // 移除索引
        Set<UUID> granteePerms = granteeIndex.get(perm.getGranteeId());
        if (granteePerms != null) {
            granteePerms.remove(permissionId);
        }

        Set<UUID> territoryPerms = territoryIndex.get(perm.getTerritoryId());
        if (territoryPerms != null) {
            territoryPerms.remove(permissionId);
        }
    }

    // ==================== 配置 ====================

    /**
     * 设置最大持续时间
     */
    public void setMaxDuration(long maxDuration) {
        this.maxDuration = maxDuration;
    }

    public long getMaxDuration() {
        return maxDuration;
    }

    /**
     * 设置检查间隔
     */
    public void setCheckInterval(long checkInterval) {
        this.checkInterval = checkInterval;

        // 重启定时任务
        if (expiryCheckTask != null) {
            expiryCheckTask.cancel();
            start();
        }
    }

    public long getCheckInterval() {
        return checkInterval;
    }

    // ==================== 统计方法 ====================

    /**
     * 获取临时权限总数
     */
    public int getPermissionCount() {
        return permissions.size();
    }

    /**
     * 获取有效的临时权限数量
     */
    public int getValidPermissionCount() {
        return (int) permissions.values().stream()
            .filter(TemporaryPermission::isValid)
            .count();
    }

    /**
     * 获取过期的临时权限数量
     */
    public int getExpiredPermissionCount() {
        return (int) permissions.values().stream()
            .filter(TemporaryPermission::isExpired)
            .count();
    }

    /**
     * 清空所有临时权限
     */
    public void clearAll() {
        permissions.clear();
        granteeIndex.clear();
        territoryIndex.clear();
        plugin.getLogger().info("清空所有临时权限数据");
    }

    /**
     * 重建索引
     */
    public void rebuildIndexes() {
        granteeIndex.clear();
        territoryIndex.clear();

        for (TemporaryPermission perm : permissions.values()) {
            granteeIndex.computeIfAbsent(perm.getGranteeId(),
                k -> ConcurrentHashMap.newKeySet()).add(perm.getId());
            territoryIndex.computeIfAbsent(perm.getTerritoryId(),
                k -> ConcurrentHashMap.newKeySet()).add(perm.getId());
        }

        plugin.getLogger().info("重建临时权限索引完成");
    }

    /**
     * 获取统计信息
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("total_permissions", permissions.size());
        stats.put("valid_permissions", getValidPermissionCount());
        stats.put("expired_permissions", getExpiredPermissionCount());
        stats.put("players_with_permissions", granteeIndex.size());
        stats.put("territories_with_permissions", territoryIndex.size());
        return stats;
    }
}
