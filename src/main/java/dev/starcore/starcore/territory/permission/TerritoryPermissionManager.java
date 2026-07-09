package dev.starcore.starcore.territory.permission;

import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.territory.TerritoryPermission;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Territory权限管理器
 * 支持细粒度的领地权限控制
 */
public class TerritoryPermissionManager {

    private final UUID territoryId;
    private final NationService nationService;

    // 权限设置
    private final Map<TerritoryPermission, PermissionMode> permissions = new EnumMap<>(TerritoryPermission.class);

    // 白名单玩家（拥有所有权限）
    private final Set<UUID> whitelistedPlayers = new HashSet<>();

    // 黑名单玩家（没有任何权限）
    private final Set<UUID> blacklistedPlayers = new HashSet<>();

    /**
     * 构造一个无外部依赖的权限管理器（向后兼容）
     */
    public TerritoryPermissionManager(UUID territoryId) {
        this(territoryId, null);
    }

    /**
     * 构造一个带有 NationService 的权限管理器
     */
    public TerritoryPermissionManager(UUID territoryId, NationService nationService) {
        this.territoryId = territoryId;
        this.nationService = nationService;
        initializeDefaultPermissions();
    }

    /**
     * 初始化默认权限
     */
    private void initializeDefaultPermissions() {
        // BUILD: 放置方块
        permissions.put(TerritoryPermission.BUILD, PermissionMode.NATION_ONLY);
        // BREAK: 破坏方块
        permissions.put(TerritoryPermission.BREAK, PermissionMode.NATION_ONLY);
        // INTERACT: 使用门、按钮、拉杆等
        permissions.put(TerritoryPermission.INTERACT, PermissionMode.ALLIES);
        // CONTAINER: 打开箱子、熔炉等（包含SWITCH的开关功能）
        permissions.put(TerritoryPermission.CONTAINER, PermissionMode.ALLIES);
        // ITEM_USE: 使用物品
        permissions.put(TerritoryPermission.ITEM_USE, PermissionMode.ALLIES);
        // PVP: 玩家对战
        permissions.put(TerritoryPermission.PVP, PermissionMode.DISABLED);
    }

    /**
     * 检查权限
     */
    public boolean hasPermission(Player player, TerritoryPermission permission,
                                 UUID nationId, Set<UUID> allyNations) {
        UUID playerId = player.getUniqueId();

        // 黑名单检查
        if (blacklistedPlayers.contains(playerId)) {
            return false;
        }

        // 白名单检查
        if (whitelistedPlayers.contains(playerId)) {
            return true;
        }

        // OP权限
        if (player.hasPermission("starcore.admin.bypass")) {
            return true;
        }

        // 获取权限模式
        PermissionMode mode = permissions.getOrDefault(permission, PermissionMode.DISABLED);

        return switch (mode) {
            case EVERYONE -> true;
            case ALLIES -> {
                // 检查玩家是否属于Nation或盟友Nation
                UUID playerNation = getPlayerNation(player);
                yield playerNation != null &&
                      (playerNation.equals(nationId) || allyNations.contains(playerNation));
            }
            case NATION_ONLY -> {
                // 只有Nation成员
                UUID playerNation = getPlayerNation(player);
                yield playerNation != null && playerNation.equals(nationId);
            }
            case DISABLED -> false;
        };
    }

    /**
     * 设置权限模式
     */
    public void setPermission(TerritoryPermission permission, PermissionMode mode) {
        permissions.put(permission, mode);
    }

    /**
     * 获取权限模式
     */
    public PermissionMode getPermission(TerritoryPermission permission) {
        return permissions.getOrDefault(permission, PermissionMode.DISABLED);
    }

    /**
     * 添加白名单玩家
     */
    public void addWhitelistedPlayer(UUID playerId) {
        whitelistedPlayers.add(playerId);
        blacklistedPlayers.remove(playerId);
    }

    /**
     * 移除白名单玩家
     */
    public void removeWhitelistedPlayer(UUID playerId) {
        whitelistedPlayers.remove(playerId);
    }

    /**
     * 添加黑名单玩家
     */
    public void addBlacklistedPlayer(UUID playerId) {
        blacklistedPlayers.add(playerId);
        whitelistedPlayers.remove(playerId);
    }

    /**
     * 移除黑名单玩家
     */
    public void removeBlacklistedPlayer(UUID playerId) {
        blacklistedPlayers.remove(playerId);
    }

    /**
     * 是否在白名单
     */
    public boolean isWhitelisted(UUID playerId) {
        return whitelistedPlayers.contains(playerId);
    }

    /**
     * 是否在黑名单
     */
    public boolean isBlacklisted(UUID playerId) {
        return blacklistedPlayers.contains(playerId);
    }

    /**
     * 获取玩家所属Nation
     */
    private UUID getPlayerNation(Player player) {
        if (nationService == null) {
            return null;
        }
        return nationService.nationOf(player.getUniqueId())
            .map(Nation::id)
            .map(dev.starcore.starcore.module.nation.model.NationId::value)
            .orElse(null);
    }

    /**
     * 获取所有权限设置
     */
    public Map<TerritoryPermission, PermissionMode> getAllPermissions() {
        return Collections.unmodifiableMap(permissions);
    }

    /**
     * 克隆权限设置
     */
    public TerritoryPermissionManager clone(UUID newTerritoryId) {
        TerritoryPermissionManager clone = new TerritoryPermissionManager(newTerritoryId);
        clone.permissions.putAll(this.permissions);
        clone.whitelistedPlayers.addAll(this.whitelistedPlayers);
        clone.blacklistedPlayers.addAll(this.blacklistedPlayers);
        return clone;
    }

    /**
     * 权限模式
     */
    public enum PermissionMode {
        EVERYONE("§a所有人", "任何人都可以"),
        ALLIES("§e盟友", "Nation及盟友可以"),
        NATION_ONLY("§6Nation", "只有Nation成员"),
        DISABLED("§c禁止", "禁止所有人");

        private final String displayName;
        private final String description;

        PermissionMode(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getDescription() {
            return description;
        }
    }
}
