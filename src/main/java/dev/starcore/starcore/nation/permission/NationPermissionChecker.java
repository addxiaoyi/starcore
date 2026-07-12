package dev.starcore.starcore.nation.permission;

import dev.starcore.starcore.nation.rank.NationRank;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Nation权限检查器
 * 实现三层权限模型
 *
 * 检查顺序：
 * 1. Bukkit权限层（插件权限）
 * 2. 职位层级（FOUNDER/LEADER/TRUSTED/MEMBER）
 * 3. Rank自定义权限（职位系统）
 */
public class NationPermissionChecker {

    /**
     * 检查玩家是否拥有权限（默认严格模式）
     *
     * @param player 玩家
     * @param permission 权限
     * @param level 玩家职位层级
     * @param rank 玩家Rank（可为null）
     * @return 是否拥有权限
     */
    public boolean hasPermission(
        Player player,
        NationPermission permission,
        PermissionLevel level,
        NationRank rank
    ) {
        return hasPermission(player, permission, level, rank, true);
    }

    /**
     * 检查玩家是否拥有权限（显式指定严格模式）
     *
     * @param player 玩家
     * @param permission 权限
     * @param level 玩家职位层级
     * @param rank 玩家Rank（可为null）
     * @param strictMode 严格模式：节点缺失时拒绝
     * @return 是否拥有权限
     */
    public boolean hasPermission(
        Player player,
        NationPermission permission,
        PermissionLevel level,
        NationRank rank,
        boolean strictMode
    ) {
        // 第1层：Rank权限检查（最高优先级）
        if (hasRankPermission(rank, permission)) {
            return true;
        }

        // 第2层：Bukkit权限检查
        if (!hasBukkitPermission(player, permission, strictMode)) {
            return false;
        }

        // 第3层：职位层级检查
        if (hasLevelPermission(level, permission)) {
            return true;
        }

        return false;
    }

    /**
     * 宽松模式检查（向后兼容）
     */
    public boolean hasPermissionCompat(
        Player player,
        NationPermission permission,
        PermissionLevel level,
        NationRank rank
    ) {
        return hasPermission(player, permission, level, rank, false);
    }

    /**
     * 检查Bukkit权限
     * @param strictMode 严格模式：节点缺失时拒绝而非通过
     */
    private boolean hasBukkitPermission(Player player, NationPermission permission, boolean strictMode) {
        String node = permission.getBukkitNode();

        // audit A-029: 严格模式下节点缺失时拒绝，避免未定义权限节点提权
        if (node == null || node.isEmpty()) {
            return !strictMode;
        }

        return player.hasPermission(node);
    }

    /**
     * 检查职位层级权限
     */
    private boolean hasLevelPermission(PermissionLevel level, NationPermission permission) {
        // 玩家层级 >= 权限要求层级
        return level != null && level.isAtLeast(permission.getDefaultLevel());
    }

    /**
     * 检查Rank权限
     */
    private boolean hasRankPermission(NationRank rank, NationPermission permission) {
        return rank != null && rank.hasPermission(permission);
    }

    /**
     * 发送无权限消息
     */
    public void sendNoPermissionMessage(Player player, NationPermission permission) {
        player.sendMessage("§c你没有权限执行此操作！");
        player.sendMessage("§7需要权限: §e" + permission.getDescription());
        player.sendMessage("§7所需层级: §e" + permission.getDefaultLevel().getDisplayName());
    }
}
