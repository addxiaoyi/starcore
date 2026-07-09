package dev.starcore.starcore.moderation;

import dev.starcore.starcore.util.ColorCodes;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 隐身服务
 * 管理管理员隐身模式
 */
public final class VanishService {
    // 隐身玩家列表
    private final Set<UUID> vanishedPlayers = ConcurrentHashMap.newKeySet();

    /**
     * 设置隐身
     */
    public void setVanished(Player player, boolean vanished) {
        if (vanished) {
            vanishedPlayers.add(player.getUniqueId());

            // 隐藏玩家
            for (Player online : player.getServer().getOnlinePlayers()) {
                if (!online.hasPermission("starcore.vanish.see")) {
                    online.hidePlayer(player.getServer().getPluginManager().getPlugin("STARCORE"), player);
                }
            }

            player.sendMessage("§a你已进入隐身模式");
        } else {
            vanishedPlayers.remove(player.getUniqueId());

            // 显示玩家
            for (Player online : player.getServer().getOnlinePlayers()) {
                online.showPlayer(player.getServer().getPluginManager().getPlugin("STARCORE"), player);
            }

            player.sendMessage("§a你已退出隐身模式");
        }
    }

    /**
     * 切换隐身状态
     */
    public void toggleVanish(Player player) {
        setVanished(player, !isVanished(player));
    }

    /**
     * 检查是否隐身
     */
    public boolean isVanished(Player player) {
        return vanishedPlayers.contains(player.getUniqueId());
    }

    /**
     * 玩家加入时处理
     */
    public void onPlayerJoin(Player player) {
        // 隐藏所有隐身的玩家
        if (!player.hasPermission("starcore.vanish.see")) {
            for (UUID vanishedId : vanishedPlayers) {
                Player vanished = player.getServer().getPlayer(vanishedId);
                if (vanished != null && vanished.isOnline()) {
                    player.hidePlayer(
                        player.getServer().getPluginManager().getPlugin("STARCORE"),
                        vanished
                    );
                }
            }
        }
    }

    /**
     * 玩家退出时清理
     */
    public void onPlayerQuit(Player player) {
        vanishedPlayers.remove(player.getUniqueId());
    }

    /**
     * 获取所有隐身玩家
     */
    public Set<UUID> getVanishedPlayers() {
        return new HashSet<>(vanishedPlayers);
    }
}
