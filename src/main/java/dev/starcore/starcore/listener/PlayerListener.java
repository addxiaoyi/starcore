package dev.starcore.starcore.listener;

import dev.starcore.starcore.moderation.ban.BanService;
import dev.starcore.starcore.moderation.jail.JailService;
import dev.starcore.starcore.moderation.vanish.VanishService;
import dev.starcore.starcore.social.friend.FriendService;
import dev.starcore.starcore.essentials.teleport.TeleportDelaySystem;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

/**
 * 玩家事件监听器（增强版）
 */
public final class PlayerListener implements Listener {
    private final BanService banService;
    private final JailService jailService;
    private final VanishService vanishService;
    private final FriendService friendService;
    private final TeleportDelaySystem teleportDelay;

    public PlayerListener(BanService banService, JailService jailService,
                         VanishService vanishService, FriendService friendService,
                         TeleportDelaySystem teleportDelay) {
        this.banService = banService;
        this.jailService = jailService;
        this.vanishService = vanishService;
        this.friendService = friendService;
        this.teleportDelay = teleportDelay;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerLogin(PlayerLoginEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        String ip = event.getAddress().getHostAddress();

        // 检查封禁
        if (banService.isBanned(playerId)) {
            var record = banService.getBanRecord(playerId);
            if (record != null) {
                String remaining = banService.formatRemainingTime(record);
                String message = String.format(
                    "§c你已被封禁\n§7原因: %s\n§7剩余时间: %s",
                    record.getReason(),
                    remaining
                );
                event.disallow(PlayerLoginEvent.Result.KICK_BANNED, message);
                return;
            }
        }

        // 检查IP封禁
        if (banService.isIPBanned(ip)) {
            event.disallow(PlayerLoginEvent.Result.KICK_BANNED, "§c你的IP已被封禁");
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // 检查监禁
        if (jailService.isJailed(playerId)) {
            var location = jailService.getJailLocation();
            if (location != null) {
                player.teleport(location);
                var record = jailService.getJailRecord(playerId);
                if (record != null) {
                    player.sendMessage("§c你仍在监禁中！");
                    player.sendMessage("§7剩余时间: " + jailService.formatRemainingTime(record));
                }
            }
        }

        // 对隐身玩家隐藏此玩家
        for (UUID vanishedId : vanishService.getVanishedPlayers()) {
            Player vanished = player.getServer().getPlayer(vanishedId);
            if (vanished != null) {
                player.hidePlayer(vanished);
            }
        }

        // 好友上线通知
        for (UUID friendId : friendService.getFriends(playerId)) {
            Player friend = player.getServer().getPlayer(friendId);
            if (friend != null && friend.isOnline()) {
                friend.sendMessage("§a[星链] §f好友 §e" + player.getName() + " §a上线了");
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // 清理隐身状态
        vanishService.cleanup(playerId);

        // 取消待处理的传送
        teleportDelay.cancelPendingTeleport(playerId);

        // 好友下线通知
        for (UUID friendId : friendService.getFriends(playerId)) {
            Player friend = player.getServer().getPlayer(friendId);
            if (friend != null && friend.isOnline()) {
                friend.sendMessage("§c[星链] §f好友 §e" + player.getName() + " §c下线了");
            }
        }
    }

    /**
     * 玩家受伤时取消传送
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        teleportDelay.onPlayerDamage(player.getUniqueId());
    }
}
