package dev.starcore.starcore.util;

import dev.starcore.starcore.city.City;
import dev.starcore.starcore.city.CityManager;
import dev.starcore.starcore.clan.Clan;
import dev.starcore.starcore.clan.ClanManager;
import dev.starcore.starcore.core.StarCoreContext;
import dev.starcore.starcore.core.service.ServiceRegistry;
import dev.starcore.starcore.module.nation.NationService;
import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.nation.model.NationMember;
import dev.starcore.starcore.module.officer.OfficerModule;
import dev.starcore.starcore.module.officer.OfficerService;
import dev.starcore.starcore.nation.permission.NationPermission;
import dev.starcore.starcore.nation.permission.NationPermissionChecker;
import dev.starcore.starcore.nation.permission.PermissionLevel;
import dev.starcore.starcore.nation.rank.NationRank;
import dev.starcore.starcore.nation.rank.NationRankManager;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;
import java.util.UUID;

/**
 * 权限工具类
 * 统一处理权限检查
 */
public class PermissionUtil {

    // 服务实例（通过 init 方法初始化）
    private static NationService nationService;
    private static ClanManager clanManager;
    private static CityManager cityManager;
    private static OfficerService officerService;
    private static NationRankManager rankManager;
    private static final NationPermissionChecker permissionChecker = new NationPermissionChecker();

    // 初始化标志
    private static volatile boolean initialized = false;

    /**
     * 初始化权限工具类的服务引用
     * 应在插件 onEnable 时调用
     */
    public static void init(JavaPlugin plugin) {
        ServiceRegistry registry = null;
        if (plugin instanceof dev.starcore.starcore.StarCorePlugin sc) {
            registry = sc.context().serviceRegistry();
        } else {
            // 尝试从其他方式获取 ServiceRegistry
            try {
                var ctxField = plugin.getClass().getDeclaredField("context");
                ctxField.setAccessible(true);
                StarCoreContext ctx = (StarCoreContext) ctxField.get(plugin);
                registry = ctx.serviceRegistry();
            } catch (Exception e) {
                // 无法获取 ServiceRegistry，服务将为 null
            }
        }

        nationService = registry != null ? registry.find(NationService.class).orElse(null) : null;
        clanManager = registry != null ? registry.find(ClanManager.class).orElse(null) : null;
        cityManager = registry != null ? registry.find(CityManager.class).orElse(null) : null;
        officerService = registry != null ? registry.find(OfficerService.class).orElse(null) : null;

        // 初始化 Rank 管理器
        if (registry != null) {
            rankManager = registry.find(NationRankManager.class).orElse(null);
            if (rankManager == null) {
                // 如果没有找到，创建一个新的管理器
                rankManager = new NationRankManager();
                rankManager.initDefaultRanks();
            }
        } else {
            rankManager = new NationRankManager();
            rankManager.initDefaultRanks();
        }

        initialized = true;
    }

    /**
     * 检查是否已初始化
     */
    public static boolean isInitialized() {
        return initialized;
    }

    /**
     * 确保已初始化
     */
    private static void ensureInitialized() {
        if (!initialized) {
            throw new IllegalStateException("PermissionUtil 尚未初始化，请先调用 init()");
        }
    }

    /**
     * 设置服务实例（供测试或手动初始化使用）
     */
    public static void setServices(NationService nationSvc, ClanManager clanMgr, CityManager cityMgr) {
        nationService = nationSvc;
        clanManager = clanMgr;
        cityManager = cityMgr;
        officerService = null;
        rankManager = new NationRankManager();
        rankManager.initDefaultRanks();
        initialized = true;
    }

    /**
     * 设置所有服务实例
     */
    public static void setServices(NationService nationSvc, ClanManager clanMgr, CityManager cityMgr,
                                   OfficerService officerSvc, NationRankManager rankMgr) {
        nationService = nationSvc;
        clanManager = clanMgr;
        cityManager = cityMgr;
        officerService = officerSvc;
        rankManager = rankMgr != null ? rankMgr : new NationRankManager();
        rankManager.initDefaultRanks();
        initialized = true;
    }

    /**
     * 重置初始化状态（用于测试）
     */
    public static void reset() {
        nationService = null;
        clanManager = null;
        cityManager = null;
        officerService = null;
        rankManager = null;
        initialized = false;
    }

    // ==================== 管理员权限 ====================

    public static boolean isAdmin(Player player) {
        return player.hasPermission("starcore.admin") || player.isOp();
    }

    // ==================== Nation权限 ====================

    public static boolean canCreateNation(Player player) {
        return player.hasPermission("starcore.nation.create");
    }

    public static boolean canDeleteNation(Player player) {
        return player.hasPermission("starcore.nation.delete");
    }

    public static boolean canManageNation(Player player) {
        return player.hasPermission("starcore.nation.manage");
    }

    public static boolean canInviteToNation(Player player) {
        return player.hasPermission("starcore.nation.invite");
    }

    public static boolean canKickFromNation(Player player) {
        return player.hasPermission("starcore.nation.kick");
    }

    // ==================== Clan权限 ====================

    public static boolean canCreateClan(Player player) {
        return player.hasPermission("starcore.clan.create");
    }

    public static boolean canDisbandClan(Player player) {
        return player.hasPermission("starcore.clan.disband");
    }

    public static boolean canManageClan(Player player) {
        return player.hasPermission("starcore.clan.manage");
    }

    public static boolean canInviteToClan(Player player) {
        return player.hasPermission("starcore.clan.invite");
    }

    public static boolean canKickFromClan(Player player) {
        return player.hasPermission("starcore.clan.kick");
    }

    // ==================== City权限 ====================

    public static boolean canCreateCity(Player player) {
        return player.hasPermission("starcore.city.create");
    }

    public static boolean canDeleteCity(Player player) {
        return player.hasPermission("starcore.city.delete");
    }

    public static boolean canManageCity(Player player) {
        return player.hasPermission("starcore.city.manage");
    }

    public static boolean canInviteToCity(Player player) {
        return player.hasPermission("starcore.city.invite");
    }

    public static boolean canKickFromCity(Player player) {
        return player.hasPermission("starcore.city.kick");
    }

    // ==================== Territory权限 ====================

    public static boolean canClaimTerritory(Player player) {
        return player.hasPermission("starcore.territory.claim");
    }

    public static boolean canUnclaimTerritory(Player player) {
        return player.hasPermission("starcore.territory.unclaim");
    }

    public static boolean canManageTerritory(Player player) {
        return player.hasPermission("starcore.territory.manage");
    }

    public static boolean canBypassProtection(Player player) {
        return player.hasPermission("starcore.territory.bypass");
    }

    // ==================== 经济权限 ====================

    public static boolean canManageEconomy(Player player) {
        return player.hasPermission("starcore.economy.manage");
    }

    public static boolean canCollectTax(Player player) {
        return player.hasPermission("starcore.economy.tax.collect");
    }

    public static boolean canSetTaxRate(Player player) {
        return player.hasPermission("starcore.economy.tax.set");
    }

    // ==================== 外交权限 ====================

    public static boolean canManageDiplomacy(Player player) {
        return player.hasPermission("starcore.diplomacy.manage");
    }

    public static boolean canCreateAlliance(Player player) {
        return player.hasPermission("starcore.diplomacy.ally");
    }

    public static boolean canDeclareWar(Player player) {
        return player.hasPermission("starcore.diplomacy.war");
    }

    // ==================== 其他权限 ====================

    public static boolean canUseGUI(Player player) {
        return player.hasPermission("starcore.gui.use");
    }

    public static boolean canSeeRanking(Player player) {
        return player.hasPermission("starcore.ranking.view");
    }

    public static boolean canUseWebMap(Player player) {
        return player.hasPermission("starcore.webmap.use");
    }

    // ==================== 权限检查辅助方法 ====================

    /**
     * 检查多个权限（任意一个满足即可）
     */
    public static boolean hasAnyPermission(Player player, String... permissions) {
        for (String permission : permissions) {
            if (player.hasPermission(permission)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查多个权限（全部满足）
     */
    public static boolean hasAllPermissions(Player player, String... permissions) {
        for (String permission : permissions) {
            if (!player.hasPermission(permission)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 检查并发送错误消息
     */
    public static boolean checkPermission(Player player, String permission) {
        if (!player.hasPermission(permission)) {
            MessageUtil.noPermission(player);
            return false;
        }
        return true;
    }

    /**
     * 检查管理员权限并发送错误消息
     */
    public static boolean checkAdmin(Player player) {
        if (!isAdmin(player)) {
            MessageUtil.noPermission(player);
            return false;
        }
        return true;
    }

    // ==================== Nation内部权限 ====================

    /**
     * 检查Nation内部权限 - 使用 NationRank 权限系统
     */
    public static boolean hasNationPermission(Player player, UUID nationId, NationPermission permission) {
        return hasNationPermission(player, nationId, permission, null);
    }

    /**
     * 检查Nation内部权限 - 通过玩家UUID（用于服务层）
     */
    public static boolean hasNationPermission(UUID playerId, UUID nationId, NationPermission permission) {
        if (nationService == null) {
            return false;
        }

        Optional<Nation> nationOpt = nationService.nationById(NationId.of(nationId));
        if (nationOpt.isEmpty()) {
            return false;
        }

        Nation nation = nationOpt.get();

        // 检查玩家是否为国家成员
        Optional<Nation> playerNationOpt = nationService.nationOf(playerId);
        if (playerNationOpt.isEmpty() || !playerNationOpt.get().id().equals(nation.id())) {
            return false;
        }

        // 确定玩家职位层级
        PermissionLevel level = determinePermissionLevel(nation, playerId);

        // FOUNDER 层级自动拥有所有权限
        if (level == PermissionLevel.FOUNDER) {
            return true;
        }

        // 获取玩家的 Rank
        NationRank playerRank = null;
        if (rankManager != null) {
            playerRank = rankManager.getPlayerRank(playerId, nationId);
        }

        // 使用权限检查器验证（player 为 null）
        return permissionChecker.hasPermission(null, permission, level, playerRank);
    }

    /**
     * 检查Nation内部权限（带 NationRank）
     */
    public static boolean hasNationPermission(Player player, UUID nationId, NationPermission permission, NationRank rank) {
        if (nationService == null) {
            return false;
        }

        Optional<Nation> nationOpt = nationService.nationById(NationId.of(nationId));
        if (nationOpt.isEmpty()) {
            return false;
        }

        Nation nation = nationOpt.get();
        UUID playerId = player.getUniqueId();

        // 检查玩家是否为国家成员
        Optional<Nation> playerNationOpt = nationService.nationOf(playerId);
        if (playerNationOpt.isEmpty() || !playerNationOpt.get().id().equals(nation.id())) {
            return false;
        }

        // 确定玩家职位层级
        PermissionLevel level = determinePermissionLevel(nation, playerId);

        // FOUNDER 层级自动拥有所有权限
        if (level == PermissionLevel.FOUNDER) {
            return true;
        }

        // 如果没有传入 rank，尝试获取玩家的 Rank
        NationRank playerRank = rank;
        if (playerRank == null && rankManager != null) {
            playerRank = rankManager.getPlayerRank(playerId, nationId);
        }

        // 使用权限检查器验证
        return permissionChecker.hasPermission(player, permission, level, playerRank);
    }

    /**
     * 确定玩家的权限层级
     */
    private static PermissionLevel determinePermissionLevel(Nation nation, UUID playerId) {
        // 检查是否为创建者
        if (nation.founderId().equals(playerId)) {
            return PermissionLevel.FOUNDER;
        }

        // 获取成员信息
        NationMember member = nation.hasMember(playerId)
            ? nation.members().stream().filter(m -> m.playerId().equals(playerId)).findFirst().orElse(null)
            : null;
        String rankName = member != null ? member.rank() : null;

        // 根据 rank 名称推断权限层级
        if (rankName != null) {
            String lowerRank = rankName.toLowerCase();
            if (lowerRank.contains("leader") || lowerRank.contains("领袖") || lowerRank.contains("君主")) {
                return PermissionLevel.LEADER;
            } else if (lowerRank.contains("trust") || lowerRank.contains("信任") || lowerRank.contains("长老")) {
                return PermissionLevel.TRUSTED;
            }
        }

        // 检查是否为官员
        if (officerService != null) {
            Optional<?> officerRole = officerService.officer(nation.id(), "");
            if (officerRole.isPresent()) {
                // 官员默认有 TRUSTED 权限
                return PermissionLevel.TRUSTED;
            }
        }

        return PermissionLevel.MEMBER;
    }

    /**
     * 检查玩家是否为Nation领袖或创始人
     */
    public static boolean isNationLeader(Player player, UUID nationId) {
        if (nationService == null) {
            return false;
        }

        Optional<Nation> nationOpt = nationService.nationById(NationId.of(nationId));
        if (nationOpt.isEmpty()) {
            return false;
        }

        Nation nation = nationOpt.get();
        return nation.founderId().equals(player.getUniqueId());
    }

    /**
     * 检查玩家是否为国家成员
     */
    public static boolean isNationMember(Player player, UUID nationId) {
        if (nationService == null) {
            return false;
        }

        Optional<Nation> nationOpt = nationService.nationById(NationId.of(nationId));
        if (nationOpt.isEmpty()) {
            return false;
        }

        Nation nation = nationOpt.get();
        return nation.hasMember(player.getUniqueId());
    }

    /**
     * 获取玩家在国家中的权限层级
     */
    public static PermissionLevel getNationPermissionLevel(Player player, UUID nationId) {
        if (nationService == null) {
            return PermissionLevel.MEMBER;
        }

        Optional<Nation> nationOpt = nationService.nationById(NationId.of(nationId));
        if (nationOpt.isEmpty()) {
            return PermissionLevel.MEMBER;
        }

        Nation nation = nationOpt.get();
        return determinePermissionLevel(nation, player.getUniqueId());
    }

    /**
     * 获取玩家在国家中的 NationRank
     */
    public static NationRank getNationRank(Player player, UUID nationId) {
        if (rankManager == null) {
            return null;
        }
        return rankManager.getPlayerRank(player.getUniqueId(), nationId);
    }

    /**
     * 检查是否为Clan领袖
     */
    public static boolean isClanLeader(Player player, UUID clanId) {
        if (clanManager == null) {
            return false;
        }

        Clan clan = clanManager.getClan(clanId);
        if (clan == null) {
            return false;
        }

        return clan.isLeader(player.getUniqueId());
    }

    /**
     * 检查是否为City市长
     */
    public static boolean isCityMayor(Player player, UUID cityId) {
        if (cityManager == null) {
            return false;
        }

        City city = cityManager.getCity(cityId);
        if (city == null) {
            return false;
        }

        return city.isMayor(player.getUniqueId());
    }
}
