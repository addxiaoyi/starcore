package dev.starcore.starcore.foundation.player;

import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OnlinePlayerDirectory {
    Optional<Player> findOnlinePlayer(UUID playerId);

    Optional<Player> findPlayerById(UUID playerId);

    Collection<Player> onlinePlayers();

    /**
     * 获取玩家的显示名称
     * @param playerId 玩家ID
     * @return 显示名称（优先在线名称，否则返回UUID字符串）
     */
    default Optional<String> displayName(UUID playerId) {
        return findOnlinePlayer(playerId)
            .map(Player::getName)
            .or(() -> Optional.of(playerId.toString()));
    }

    default List<String> onlinePlayerNames() {
        return onlinePlayers().stream()
            .map(Player::getName)
            .filter(name -> name != null && !name.isBlank())
            .toList();
    }
}
