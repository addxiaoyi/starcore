package dev.starcore.starcore.foundation.player;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class BukkitOnlinePlayerDirectory implements OnlinePlayerDirectory {
    @Override
    public Optional<Player> findOnlinePlayer(UUID playerId) {
        return Optional.ofNullable(Bukkit.getPlayer(playerId)).filter(Player::isOnline);
    }

    @Override
    public Optional<Player> findPlayerById(UUID playerId) {
        return Optional.ofNullable(Bukkit.getPlayer(playerId));
    }

    @Override
    public Collection<Player> onlinePlayers() {
        return List.copyOf(Bukkit.getOnlinePlayers());
    }
}
