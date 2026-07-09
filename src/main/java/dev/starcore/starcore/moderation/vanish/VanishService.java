package dev.starcore.starcore.moderation.vanish;

import dev.starcore.starcore.util.ColorCodes;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 隐身服务（隐形斗篷）
 */
public final class VanishService {
    // 隐身的玩家
    private final Set<UUID> vanishedPlayers = ConcurrentHashMap.newKeySet();

    /**
     * 隐身
     */
    public void vanish(Player player) {
        UUID playerId = player.getUniqueId();
        if (vanishedPlayers.add(playerId)) {
            // 对所有玩家隐藏
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                if (!onlinePlayer.equals(player)) {
                    onlinePlayer.hidePlayer(player);
                }
            }

            player.sendMessage(ColorCodes.SUCCESS + "你已进入隐身模式");
        }
    }

    /**
     * 显示
     */
    public void unvanish(Player player) {
        UUID playerId = player.getUniqueId();
        if (vanishedPlayers.remove(playerId)) {
            // 对所有玩家显示
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                if (!onlinePlayer.equals(player)) {
                    onlinePlayer.showPlayer(player);
                }
            }

            player.sendMessage(ColorCodes.ERROR + "你已退出隐身模式");
        }
    }

    /**
     * 切换隐身状态
     */
    public void toggle(Player player) {
        if (isVanished(player)) {
            unvanish(player);
        } else {
            vanish(player);
        }
    }

    /**
     * 检查是否隐身
     */
    public boolean isVanished(Player player) {
        return vanishedPlayers.contains(player.getUniqueId());
    }

    /**
     * 检查是否隐身（UUID）
     */
    public boolean isVanished(UUID playerId) {
        return vanishedPlayers.contains(playerId);
    }

    /**
     * 获取所有隐身玩家
     */
    public Set<UUID> getVanishedPlayers() {
        return Set.copyOf(vanishedPlayers);
    }

    /**
     * 清理离线玩家
     */
    public void cleanup(UUID playerId) {
        vanishedPlayers.remove(playerId);
    }
}
