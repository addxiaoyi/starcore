package dev.starcore.starcore.moderation.jail;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 监禁服务
 */
public final class JailService {
    // 监禁记录（玩家UUID -> 监禁记录）
    private final Map<UUID, JailRecord> jailedPlayers = new ConcurrentHashMap<>();

    // 监狱位置
    private Location jailLocation;

    /**
     * 设置监狱位置
     */
    public void setJailLocation(Location location) {
        this.jailLocation = location;
    }

    /**
     * 监禁玩家
     */
    public JailRecord jailPlayer(UUID playerId, String playerName, UUID jailedBy, String reason, long duration) {
        if (jailLocation == null) {
            throw new IllegalStateException("监狱位置未设置");
        }

        Player player = Bukkit.getPlayer(playerId);
        Location previousLocation = player != null ? player.getLocation() : null;

        // 创建监禁记录
        JailRecord record = new JailRecord(playerId, playerName, jailedBy, reason, duration, previousLocation);
        jailedPlayers.put(playerId, record);

        // 传送到监狱
        if (player != null && player.isOnline()) {
            player.teleport(jailLocation);
            player.sendMessage("§c你已被监禁！");
            player.sendMessage("§7原因: " + reason);
            player.sendMessage("§7时长: " + formatRemainingTime(record));
        }

        // 广播
        broadcastJail(record);

        return record;
    }

    /**
     * 临时监禁
     */
    public JailRecord jailTemp(UUID playerId, String playerName, UUID jailedBy, String reason, long duration, TimeUnit unit) {
        long durationMillis = unit.toMillis(duration);
        return jailPlayer(playerId, playerName, jailedBy, reason, durationMillis);
    }

    /**
     * 永久监禁
     */
    public JailRecord jailPermanent(UUID playerId, String playerName, UUID jailedBy, String reason) {
        return jailPlayer(playerId, playerName, jailedBy, reason, -1);
    }

    /**
     * 释放玩家
     */
    public boolean releasePlayer(UUID playerId) {
        JailRecord record = jailedPlayers.remove(playerId);
        if (record != null) {
            record.release();

            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                // 传送回原位置
                if (record.getPreviousLocation() != null) {
                    player.teleport(record.getPreviousLocation());
                }
                player.sendMessage("§a你已被释放！");
            }

            broadcastRelease(record);
            return true;
        }
        return false;
    }

    /**
     * 检查玩家是否被监禁
     */
    public boolean isJailed(UUID playerId) {
        JailRecord record = jailedPlayers.get(playerId);
        if (record == null) return false;

        // 检查是否过期
        if (record.isExpired()) {
            releasePlayer(playerId);
            return false;
        }

        return record.isActive();
    }

    /**
     * 获取监禁记录
     */
    public JailRecord getJailRecord(UUID playerId) {
        JailRecord record = jailedPlayers.get(playerId);
        if (record != null && record.isExpired()) {
            releasePlayer(playerId);
            return null;
        }
        return record;
    }

    /**
     * 获取所有被监禁的玩家
     */
    public List<JailRecord> getAllJailedPlayers() {
        return new ArrayList<>(jailedPlayers.values());
    }

    /**
     * 格式化剩余时间
     */
    public String formatRemainingTime(JailRecord record) {
        if (record.isPermanent()) {
            return "永久";
        }

        long remaining = record.getRemainingTime();
        long seconds = remaining / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;

        if (hours > 0) {
            return hours + "小时" + (minutes % 60) + "分钟";
        } else if (minutes > 0) {
            return minutes + "分钟";
        } else {
            return seconds + "秒";
        }
    }

    /**
     * 广播监禁消息
     */
    private void broadcastJail(JailRecord record) {
        String duration = formatRemainingTime(record);
        String message = String.format(
            "§c[监禁] §f玩家 §e%s §f被监禁 §c%s §f原因: §7%s",
            record.getPlayerName(),
            duration,
            record.getReason()
        );
        Bukkit.broadcastMessage(message);
    }

    /**
     * 广播释放消息
     */
    private void broadcastRelease(JailRecord record) {
        String message = String.format(
            "§a[监禁] §f玩家 §e%s §f已被释放",
            record.getPlayerName()
        );
        Bukkit.broadcastMessage(message);
    }

    /**
     * 检查并自动释放过期玩家
     */
    public int checkExpired() {
        int count = 0;
        Iterator<Map.Entry<UUID, JailRecord>> iterator = jailedPlayers.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, JailRecord> entry = iterator.next();
            if (entry.getValue().isExpired()) {
                releasePlayer(entry.getKey());
                count++;
            }
        }
        return count;
    }

    public Location getJailLocation() {
        return jailLocation;
    }
}
