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
     * 检查玩家是否拥有权限
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
        // 第1层：Bukkit权限检查
        if (!hasBukkitPermission(player, permission)) {
            return false;
        }

        // 第2层：职位层级检查
        if (hasLevelPermission(level, permission)) {
            return true;
        }

        // 第3层：Rank权限检查
        if (hasRankPermission(rank, permission)) {
            return true;
        }

        return false;
    }

    /**
     * 检查Bukkit权限
     */
    private boolean hasBukkitPermission(Player player, NationPermission permission) {
        String node = permission.getBukkitNode();

        // TODO audit A-029: 严格模式应在节点缺失时拒绝而非通过，避免未定义权限节点提权；
        //   当前保留宽松语义以兼容存量调用，后续可引入 strict 模式开关逐步收紧。
        // 如果没有定义Bukkit权限节点，默认通过
        if (node == null || node.isEmpty()) {
            return true;
        }

        return player.hasPermission(node);
    }

    /**
     * 检查职位层级权限
     */
    private boolean hasLevelPermission(PermissionLevel level, NationPermission permission) {
        // TODO audit A-030: 仅按 level >= getDefaultLevel 判定，BANK_DEPOSIT/CHAT_NATION 等默认 MEMBER
        //   会让任意成员直接放行；缺少细节策略。后续引入 NationsSettings 对敏感操作加审核/上限/冷却。
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
     * 检查并发送消息（带提示）
     */
    public boolean checkPermission(
        Player player,
        NationPermission permission,
        PermissionLevel level,
        NationRank rank,
        boolean sendMessage
    ) {
        boolean has = hasPermission(player, permission, level, rank);

        if (!has && sendMessage) {
            sendNoPermissionMessage(player, permission);
        }

        return has;
    }

    /**
     * 发送无权限消息
     */
    private void sendNoPermissionMessage(Player player, NationPermission permission) {
        player.sendMessage("§c你没有权限执行此操作！");
        player.sendMessage("§7需要权限: §e" + permission.getDescription());
        player.sendMessage("§7所需层级: §e" + permission.getDefaultLevel().getDisplayName());
    }

    /**
     * 批量检查权限（任意一个满足即可）
     */
    public boolean hasAnyPermission(
        Player player,
        PermissionLevel level,
        NationRank rank,
        NationPermission... permissions
    ) {
        for (NationPermission permission : permissions) {
            if (hasPermission(player, permission, level, rank)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 批量检查权限（全部满足才通过）
     */
    public boolean hasAllPermissions(
        Player player,
        PermissionLevel level,
        NationRank rank,
        NationPermission... permissions
    ) {
        for (NationPermission permission : permissions) {
            if (!hasPermission(player, permission, level, rank)) {
                return false;
            }
        }
        return true;
    }
}
