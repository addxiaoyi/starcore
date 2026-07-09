package dev.starcore.starcore.region;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 区域检测监听器
 * 监听玩家移动并检测区域变化
 */
public class RegionDetectionListener implements Listener {
    private final Plugin plugin;
    private final RegionTitleService regionTitleService;

    // 待检查的玩家队列
    private final Set<UUID> playersToCheck = new HashSet<>();

    // 定时检查任务
    private BukkitRunnable checkTask;

    // 检查间隔（tick）
    private static final int CHECK_INTERVAL = 5; // 0.25秒检查一次

    public RegionDetectionListener(Plugin plugin, RegionTitleService regionTitleService) {
        this.plugin = plugin;
        this.regionTitleService = regionTitleService;
        startCheckTask();
    }

    /**
     * 启动定期检查任务
     */
    private void startCheckTask() {
        checkTask = new BukkitRunnable() {
            @Override
            public void run() {
                // 创建副本避免并发修改
                Set<UUID> toCheck = new HashSet<>(playersToCheck);
                playersToCheck.clear();

                for (UUID playerId : toCheck) {
                    Player player = plugin.getServer().getPlayer(playerId);
                    if (player != null && player.isOnline()) {
                        try {
                            regionTitleService.checkPlayerRegion(player, player.getLocation());
                        } catch (Exception e) {
                            plugin.getLogger().warning("检查玩家区域时出错: " + e.getMessage());
                        }
                    }
                }
            }
        };

        checkTask.runTaskTimer(plugin, 0L, CHECK_INTERVAL);
    }

    /**
     * 停止检查任务
     */
    public void stopCheckTask() {
        if (checkTask != null && !checkTask.isCancelled()) {
            checkTask.cancel();
        }
    }

    /**
     * 监听玩家移动
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        // 只有当玩家移动到不同方块时才检查
        Location from = event.getFrom();
        Location to = event.getTo();

        if (to == null) {
            return;
        }

        if (from.getBlockX() != to.getBlockX() ||
            from.getBlockY() != to.getBlockY() ||
            from.getBlockZ() != to.getBlockZ()) {

            // 将玩家加入待检查队列
            playersToCheck.add(event.getPlayer().getUniqueId());
        }
    }

    /**
     * 监听玩家传送
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        // 传送后立即检查
        playersToCheck.add(event.getPlayer().getUniqueId());
    }

    /**
     * 玩家加入时检查
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        // 延迟检查，确保玩家完全加载
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            playersToCheck.add(event.getPlayer().getUniqueId());
        }, 20L);
    }

    /**
     * 玩家退出时清理
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        playersToCheck.remove(playerId);
        regionTitleService.clearPlayerData(playerId);
    }

    /**
     * 强制检查玩家
     */
    public void forceCheckPlayer(Player player) {
        playersToCheck.add(player.getUniqueId());
    }

    /**
     * 强制检查所有在线玩家
     */
    public void forceCheckAllPlayers() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            playersToCheck.add(player.getUniqueId());
        }
    }
}
