package dev.starcore.starcore.territory;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import java.util.UUID;

/**
 * 权限检查器
 * 综合检查领土、子区域、临时权限和租赁权限
 */
public class PermissionChecker {

    private final TerritoryService territoryService;
    private final SubRegionService subRegionService;
    private final TemporaryPermissionService temporaryPermissionService;
    private final TerritoryLeaseService leaseService;

    public PermissionChecker(TerritoryService territoryService,
                            SubRegionService subRegionService,
                            TemporaryPermissionService temporaryPermissionService,
                            TerritoryLeaseService leaseService) {
        this.territoryService = territoryService;
        this.subRegionService = subRegionService;
        this.temporaryPermissionService = temporaryPermissionService;
        this.leaseService = leaseService;
    }

    // ==================== 主要权限检查 ====================

    /**
     * 检查玩家在指定位置是否有权限
     * 这是主要的权限检查入口
     */
    public boolean hasPermission(Player player, Location location, TerritoryPermission permission) {
        // 1. 管理员绕过
        if (player.hasPermission("starcore.territory.admin.bypass")) {
            return true;
        }

        // 2. 获取位置所在的领土
        Territory territory = territoryService.getTerritoryAt(location);
        if (territory == null) {
            // 荒野区域，默认允许
            return true;
        }

        // 3. 检查子区域（优先级最高）
        SubRegion subRegion = subRegionService.getSubRegionAt(location, territory.getId());
        if (subRegion != null && subRegion.isEnabled()) {
            return checkSubRegionPermission(player, territory, subRegion, permission);
        }

        // 4. 检查领土权限
        return checkTerritoryPermission(player, territory, permission);
    }

    /**
     * 检查领土权限
     */
    private boolean checkTerritoryPermission(Player player, Territory territory,
                                            TerritoryPermission permission) {
        UUID playerId = player.getUniqueId();

        // 1. 检查所有者
        if (territory.getOwnerId().equals(playerId)) {
            return true;
        }

        // 2. 检查临时权限
        if (temporaryPermissionService.hasPermission(territory.getId(), playerId, permission)) {
            return true;
        }

        // 3. 检查租赁权限
        if (checkLeasePermission(territory.getId(), playerId, permission)) {
            return true;
        }

        // 4. 检查成员权限
        PermissionLevel memberLevel = territory.getMemberLevel(playerId);
        PermissionLevel requiredLevel = territory.getPermission(permission);

        return memberLevel.isAtLeast(requiredLevel);
    }

    /**
     * 检查子区域权限
     */
    private boolean checkSubRegionPermission(Player player, Territory territory,
                                            SubRegion subRegion, TerritoryPermission permission) {
        UUID playerId = player.getUniqueId();

        // 1. 检查领土所有者
        if (territory.getOwnerId().equals(playerId)) {
            return true;
        }

        // 2. 检查临时权限
        if (temporaryPermissionService.hasPermission(territory.getId(), playerId, permission)) {
            return true;
        }

        // 3. 检查子区域专属成员
        PermissionLevel subRegionMemberLevel = subRegion.getMemberLevel(playerId);
        if (subRegionMemberLevel != PermissionLevel.NONE) {
            PermissionLevel requiredLevel = subRegion.getPermission(permission, territory);
            return subRegionMemberLevel.isAtLeast(requiredLevel);
        }

        // 4. 检查租赁权限
        if (checkLeasePermission(territory.getId(), playerId, permission)) {
            return true;
        }

        // 5. 检查继承的权限
        PermissionLevel memberLevel = territory.getMemberLevel(playerId);
        PermissionLevel requiredLevel = subRegion.getPermission(permission, territory);

        return memberLevel.isAtLeast(requiredLevel);
    }

    /**
     * 检查租赁权限
     */
    private boolean checkLeasePermission(UUID territoryId, UUID playerId,
                                        TerritoryPermission permission) {
        TerritoryLease lease = leaseService.getLeaseByTerritory(territoryId);
        if (lease == null) {
            return false;
        }

        // 检查是否为租客
        if (!lease.getTenantId().equals(playerId)) {
            return false;
        }

        // 检查租约是否生效
        if (!lease.isActive()) {
            return false;
        }

        // 租客拥有成员级别权限
        Territory territory = territoryService.getTerritory(territoryId);
        if (territory == null) {
            return false;
        }

        PermissionLevel requiredLevel = territory.getPermission(permission);
        return PermissionLevel.MEMBER.isAtLeast(requiredLevel);
    }

    // ==================== 批量权限检查 ====================

    /**
     * 检查玩家是否有管理领土的权限
     */
    public boolean canManageTerritory(Player player, Territory territory) {
        UUID playerId = player.getUniqueId();

        // 管理员
        if (player.hasPermission("starcore.territory.admin")) {
            return true;
        }

        // 所有者
        if (territory.getOwnerId().equals(playerId)) {
            return true;
        }

        // 管理员级别成员
        return territory.getMemberLevel(playerId).isAtLeast(PermissionLevel.ADMIN);
    }

    /**
     * 检查玩家是否有管理子区域的权限
     */
    public boolean canManageSubRegion(Player player, Territory territory, SubRegion subRegion) {
        UUID playerId = player.getUniqueId();

        // 管理员
        if (player.hasPermission("starcore.territory.admin")) {
            return true;
        }

        // 领土所有者
        if (territory.getOwnerId().equals(playerId)) {
            return true;
        }

        // 领土管理员
        if (territory.getMemberLevel(playerId).isAtLeast(PermissionLevel.ADMIN)) {
            return true;
        }

        // 子区域管理员
        return subRegion.getMemberLevel(playerId).isAtLeast(PermissionLevel.ADMIN);
    }

    /**
     * 检查玩家是否可以授予临时权限
     */
    public boolean canGrantTemporaryPermission(Player player, Territory territory) {
        UUID playerId = player.getUniqueId();

        // 管理员
        if (player.hasPermission("starcore.territory.admin")) {
            return true;
        }

        // 所有者
        if (territory.getOwnerId().equals(playerId)) {
            return true;
        }

        // 管理员级别成员
        return territory.getMemberLevel(playerId).isAtLeast(PermissionLevel.ADMIN);
    }

    /**
     * 检查玩家是否可以发布租赁
     */
    public boolean canCreateLease(Player player, Territory territory) {
        UUID playerId = player.getUniqueId();

        // 管理员
        if (player.hasPermission("starcore.territory.admin")) {
            return true;
        }

        // 只有所有者可以发布租赁
        return territory.getOwnerId().equals(playerId);
    }

    // ==================== 特殊检查 ====================

    /**
     * 检查是否有进入领土的权限
     */
    public boolean canEnterTerritory(Player player, Territory territory) {
        // 默认所有人都可以进入
        // 可以根据需要扩展为特定权限
        return true;
    }

    /**
     * 检查是否有传送到领土的权限
     */
    public boolean canTeleportToTerritory(Player player, Territory territory) {
        return hasPermission(player, territory.getSpawnPoint(), TerritoryPermission.TELEPORT);
    }

    /**
     * 获取玩家在领土的有效权限级别
     */
    public PermissionLevel getEffectiveLevel(Player player, Territory territory) {
        UUID playerId = player.getUniqueId();

        // 管理员
        if (player.hasPermission("starcore.territory.admin.bypass")) {
            return PermissionLevel.OWNER;
        }

        // 所有者
        if (territory.getOwnerId().equals(playerId)) {
            return PermissionLevel.OWNER;
        }

        // 租客（成员级别）
        TerritoryLease lease = leaseService.getLeaseByTerritory(territory.getId());
        if (lease != null && lease.getTenantId() != null &&
            lease.getTenantId().equals(playerId) && lease.isActive()) {
            return PermissionLevel.MEMBER;
        }

        // 成员级别
        return territory.getMemberLevel(playerId);
    }

    /**
     * 获取玩家在子区域的有效权限级别
     */
    public PermissionLevel getEffectiveLevel(Player player, Territory territory, SubRegion subRegion) {
        UUID playerId = player.getUniqueId();

        // 管理员
        if (player.hasPermission("starcore.territory.admin.bypass")) {
            return PermissionLevel.OWNER;
        }

        // 领土所有者
        if (territory.getOwnerId().equals(playerId)) {
            return PermissionLevel.OWNER;
        }

        // 子区域专属成员
        PermissionLevel subRegionLevel = subRegion.getMemberLevel(playerId);
        if (subRegionLevel != PermissionLevel.NONE) {
            return subRegionLevel;
        }

        // 领土成员级别
        return getEffectiveLevel(player, territory);
    }

    // ==================== 调试方法 ====================

    /**
     * 获取权限检查的详细信息（用于调试）
     */
    public String getPermissionDebugInfo(Player player, Location location, TerritoryPermission permission) {
        StringBuilder info = new StringBuilder();
        info.append("§e=== 权限检查详情 ===\n");
        info.append("§7玩家: §f").append(player.getName()).append("\n");
        info.append("§7位置: §f").append(location.getBlockX()).append(", ")
            .append(location.getBlockY()).append(", ").append(location.getBlockZ()).append("\n");
        info.append("§7权限: §f").append(permission.getDisplayName()).append("\n\n");

        // 领土信息
        Territory territory = territoryService.getTerritoryAt(location);
        if (territory == null) {
            info.append("§a✓ 荒野区域，允许操作\n");
            return info.toString();
        }

        info.append("§7领土: §f").append(territory.getName()).append("\n");
        info.append("§7所有者: §f").append(territory.getOwnerId()).append("\n");

        // 子区域信息
        SubRegion subRegion = subRegionService.getSubRegionAt(location, territory.getId());
        if (subRegion != null) {
            info.append("§7子区域: §f").append(subRegion.getName()).append("\n");
            info.append("§7优先级: §f").append(subRegion.getPriority()).append("\n");
        }

        // 权限级别
        PermissionLevel effectiveLevel = getEffectiveLevel(player, territory);
        info.append("§7有效级别: §f").append(effectiveLevel.getDisplayName()).append("\n");

        // 所需级别
        PermissionLevel requiredLevel = territory.getPermission(permission);
        info.append("§7所需级别: §f").append(requiredLevel.getDisplayName()).append("\n");

        // 最终结果
        boolean hasPermission = hasPermission(player, location, permission);
        info.append("\n§7结果: ").append(hasPermission ? "§a✓ 允许" : "§c✗ 拒绝").append("\n");

        return info.toString();
    }
}
