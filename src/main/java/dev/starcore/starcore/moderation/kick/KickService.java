package dev.starcore.starcore.moderation.kick;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * 踢出服务
 */
public final class KickService {

    /**
     * 踢出玩家
     */
    public boolean kickPlayer(UUID playerId, String reason) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return false;
        }

        String kickMessage = formatKickMessage(reason);
        player.kickPlayer(kickMessage);

        // 广播
        broadcastKick(player.getName(), reason);

        return true;
    }

    /**
     * 踢出玩家（使用Player对象）
     */
    public void kickPlayer(Player player, String reason) {
        String kickMessage = formatKickMessage(reason);
        player.kickPlayer(kickMessage);

        // 广播
        broadcastKick(player.getName(), reason);
    }

    /**
     * 格式化踢出消息
     */
    private String formatKickMessage(String reason) {
        StringBuilder message = new StringBuilder();
        message.append("§c╔════════════════════════════════╗\n");
        message.append("§c║                                ║\n");
        message.append("§c║      §f你已被 §c§l踢出 §f服务器      §c║\n");
        message.append("§c║                                ║\n");
        message.append("§c╚════════════════════════════════╝\n");
        message.append("\n");
        message.append("§f原因: §7").append(reason).append("\n");
        message.append("\n");
        message.append("§7你可以重新加入服务器\n");
        return message.toString();
    }

    /**
     * 广播踢出消息
     */
    private void broadcastKick(String playerName, String reason) {
        String message = String.format(
            "§e[管理] §f玩家 §e%s §f被踢出服务器 §7原因: %s",
            playerName,
            reason
        );
        Bukkit.broadcastMessage(message);
    }
}
