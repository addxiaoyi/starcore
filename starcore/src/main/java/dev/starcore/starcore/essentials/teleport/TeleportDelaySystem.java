package dev.starcore.starcore.essentials.teleport;

import dev.starcore.starcore.util.ColorCodes;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 传送延迟和冷却系统
 */
public final class TeleportDelaySystem {
    private final Map<UUID, BukkitTask> pendingTeleports = new ConcurrentHashMap<>();
    private final Map<UUID, Long> teleportCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Location> lastLocations = new ConcurrentHashMap<>();

    // 配置
    private int teleportDelay = 3; // 秒
    private int teleportCooldown = 5; // 秒
    private boolean cancelOnMove = true;
    private boolean cancelOnDamage = true;

    /**
     * 传送玩家（带延迟）
     */
    public void teleportWithDelay(Player player, Location target, Runnable onSuccess, Runnable onCancel) {
        UUID playerId = player.getUniqueId();

        // 检查冷却
        if (isOnCooldown(playerId)) {
            long remaining = getRemainingCooldown(playerId);
            player.sendMessage("§c传送冷却中，请等待 " + remaining + " 秒");
            if (onCancel != null) onCancel.run();
            return;
        }

        // 取消已有的传送
        cancelPendingTeleport(playerId);

        // 记录初始位置
        lastLocations.put(playerId, player.getLocation());

        // 发送提示
        player.sendMessage("§e传送将在 §c" + teleportDelay + " §e秒后进行...");
        player.sendMessage("§7请不要移动或受到伤害");

        // 延迟传送
        BukkitTask task = new BukkitRunnable() {
            private int countdown = teleportDelay;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }

                // 检查是否移动
                if (cancelOnMove && hasMoved(playerId, player.getLocation())) {
                    player.sendMessage("§c传送已取消（你移动了）");
                    cancel();
                    pendingTeleports.remove(playerId);
                    lastLocations.remove(playerId);
                    if (onCancel != null) onCancel.run();
                    return;
                }

                countdown--;

                if (countdown > 0) {
                    player.sendMessage("§e传送倒计时: §c" + countdown + "秒");
                } else {
                    // 执行传送
                    player.teleport(target, PlayerTeleportEvent.TeleportCause.PLUGIN);
                    player.sendMessage("§a传送成功！");

                    // 设置冷却
                    setCooldown(playerId);

                    // 清理
                    pendingTeleports.remove(playerId);
                    lastLocations.remove(playerId);

                    if (onSuccess != null) onSuccess.run();
                    cancel();
                }
            }
        }.runTaskTimer(null, 0L, 20L); // 每秒执行

        pendingTeleports.put(playerId, task);
    }

    /**
     * 立即传送（无延迟）
     */
    public void teleportInstantly(Player player, Location target) {
        UUID playerId = player.getUniqueId();

        // 取消已有的传送
        cancelPendingTeleport(playerId);

        // 执行传送
        player.teleport(target, PlayerTeleportEvent.TeleportCause.PLUGIN);

        // 设置冷却
        setCooldown(playerId);
    }

    /**
     * 取消待处理的传送
     */
    public void cancelPendingTeleport(UUID playerId) {
        BukkitTask task = pendingTeleports.remove(playerId);
        if (task != null) {
            task.cancel();
        }
        lastLocations.remove(playerId);
    }

    /**
     * 检查玩家是否有待处理的传送
     */
    public boolean hasPendingTeleport(UUID playerId) {
        return pendingTeleports.containsKey(playerId);
    }

    /**
     * 检查是否在冷却中
     */
    public boolean isOnCooldown(UUID playerId) {
        Long cooldownEnd = teleportCooldowns.get(playerId);
        if (cooldownEnd == null) return false;

        long now = System.currentTimeMillis();
        if (now >= cooldownEnd) {
            teleportCooldowns.remove(playerId);
            return false;
        }

        return true;
    }

    /**
     * 获取剩余冷却时间（秒）
     */
    public long getRemainingCooldown(UUID playerId) {
        Long cooldownEnd = teleportCooldowns.get(playerId);
        if (cooldownEnd == null) return 0;

        long remaining = (cooldownEnd - System.currentTimeMillis()) / 1000;
        return Math.max(0, remaining);
    }

    /**
     * 设置冷却
     */
    private void setCooldown(UUID playerId) {
        long cooldownEnd = System.currentTimeMillis() + (teleportCooldown * 1000L);
        teleportCooldowns.put(playerId, cooldownEnd);
    }

    /**
     * 检查是否移动
     */
    private boolean hasMoved(UUID playerId, Location currentLocation) {
        Location lastLocation = lastLocations.get(playerId);
        if (lastLocation == null) return false;

        // 忽略头部转动
        return lastLocation.getBlockX() != currentLocation.getBlockX()
            || lastLocation.getBlockY() != currentLocation.getBlockY()
            || lastLocation.getBlockZ() != currentLocation.getBlockZ();
    }

    /**
     * 玩家受伤时取消传送
     */
    public void onPlayerDamage(UUID playerId) {
        if (!cancelOnDamage) return;

        if (hasPendingTeleport(playerId)) {
            cancelPendingTeleport(playerId);
            Player player = org.bukkit.Bukkit.getPlayer(playerId);
            if (player != null) {
                player.sendMessage("§c传送已取消（你受到了伤害）");
            }
        }
    }

    // Getters and Setters
    public void setTeleportDelay(int seconds) {
        this.teleportDelay = seconds;
    }

    public void setTeleportCooldown(int seconds) {
        this.teleportCooldown = seconds;
    }

    public void setCancelOnMove(boolean cancelOnMove) {
        this.cancelOnMove = cancelOnMove;
    }

    public void setCancelOnDamage(boolean cancelOnDamage) {
        this.cancelOnDamage = cancelOnDamage;
    }

    public int getTeleportDelay() {
        return teleportDelay;
    }

    public int getTeleportCooldown() {
        return teleportCooldown;
    }
}
