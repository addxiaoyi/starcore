package dev.starcore.starcore.moderation.mute;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 禁言服务（单例模式）
 */
public final class MuteService {
    // 单例实例
    private static MuteService instance;

    // 禁言记录（玩家UUID -> 禁言记录）
    private final Map<UUID, MuteRecord> mutedPlayers = new ConcurrentHashMap<>();

    /**
     * 获取单例实例
     */
    public static synchronized MuteService getInstance() {
        if (instance == null) {
            instance = new MuteService();
        }
        return instance;
    }

    /**
     * 获取单例实例（插件实例版本）
     */
    public static synchronized MuteService getInstance(org.bukkit.plugin.Plugin plugin) {
        if (instance == null) {
            instance = new MuteService();
        }
        return instance;
    }

    /**
     * 私有构造函数
     */
    private MuteService() {}

    /**
     * 禁言玩家
     */
    public MuteRecord mutePlayer(UUID playerId, String playerName, UUID mutedBy, String reason, long duration) {
        // 创建禁言记录
        MuteRecord record = new MuteRecord(playerId, playerName, mutedBy, reason, duration);
        mutedPlayers.put(playerId, record);

        // 全服广播
        broadcastMute(record);

        return record;
    }

    /**
     * 临时禁言（指定时长）
     */
    public MuteRecord muteTempPlayer(UUID playerId, String playerName, UUID mutedBy, String reason, long duration, TimeUnit unit) {
        long durationMillis = unit.toMillis(duration);
        return mutePlayer(playerId, playerName, mutedBy, reason, durationMillis);
    }

    /**
     * 永久禁言
     */
    public MuteRecord mutePermanent(UUID playerId, String playerName, UUID mutedBy, String reason) {
        return mutePlayer(playerId, playerName, mutedBy, reason, -1);
    }

    /**
     * 解除禁言
     */
    public boolean unmutePlayer(UUID playerId) {
        MuteRecord record = mutedPlayers.remove(playerId);
        if (record != null) {
            record.unmute();
            broadcastUnmute(record);
            return true;
        }
        return false;
    }

    /**
     * 检查玩家是否被禁言
     */
    public boolean isMuted(UUID playerId) {
        MuteRecord record = mutedPlayers.get(playerId);
        if (record == null) return false;

        // 检查是否过期
        if (record.isExpired()) {
            mutedPlayers.remove(playerId);
            return false;
        }

        return record.isActive();
    }

    /**
     * 获取禁言记录
     */
    public MuteRecord getMuteRecord(UUID playerId) {
        MuteRecord record = mutedPlayers.get(playerId);
        if (record != null && record.isExpired()) {
            mutedPlayers.remove(playerId);
            return null;
        }
        return record;
    }

    /**
     * 获取所有被禁言的玩家
     */
    public List<MuteRecord> getAllMutedPlayers() {
        // 清理过期记录
        mutedPlayers.entrySet().removeIf(entry -> entry.getValue().isExpired());
        return new ArrayList<>(mutedPlayers.values());
    }

    /**
     * 格式化剩余时间
     */
    public String formatRemainingTime(MuteRecord record) {
        if (record.isPermanent()) {
            return "永久";
        }

        long remaining = record.getRemainingTime();
        long seconds = remaining / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) {
            return days + "天" + (hours % 24) + "小时";
        } else if (hours > 0) {
            return hours + "小时" + (minutes % 60) + "分钟";
        } else if (minutes > 0) {
            return minutes + "分钟" + (seconds % 60) + "秒";
        } else {
            return seconds + "秒";
        }
    }

    /**
     * 广播禁言消息
     */
    private void broadcastMute(MuteRecord record) {
        String duration = record.isPermanent() ? "永久" : formatRemainingTime(record);
        String message = String.format(
            "§c[放逐] §f玩家 §e%s §f被禁言 §c%s §f原因: §7%s",
            record.getPlayerName(),
            duration,
            record.getReason()
        );
        Bukkit.broadcastMessage(message);
    }

    /**
     * 广播解除禁言消息
     */
    private void broadcastUnmute(MuteRecord record) {
        String message = String.format(
            "§a[放逐] §f玩家 §e%s §f已被解除禁言",
            record.getPlayerName()
        );
        Bukkit.broadcastMessage(message);
    }

    /**
     * 清理过期记录
     */
    public int cleanupExpired() {
        int count = 0;
        Iterator<Map.Entry<UUID, MuteRecord>> iterator = mutedPlayers.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, MuteRecord> entry = iterator.next();
            if (entry.getValue().isExpired()) {
                iterator.remove();
                count++;
            }
        }
        return count;
    }
}
