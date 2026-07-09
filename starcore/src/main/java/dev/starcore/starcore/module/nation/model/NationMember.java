package dev.starcore.starcore.module.nation.model;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * 国家成员信息
 * 用于 GUI 显示
 */
public record NationMember(
    UUID playerId,
    String playerName,
    String rank,
    Instant joinedAt,
    Instant lastSeen
) {

    /**
     * 获取最后已知名称
     */
    public String lastKnownName() {
        return playerName;
    }

    /**
     * 玩家是否在线
     */
    public boolean isOnline() {
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerId);
        return player.isOnline();
    }

    /**
     * 加入日期（格式化）
     */
    public String joinedDate() {
        return formatDate(joinedAt);
    }

    /**
     * 最后在线时间（多少天前）
     */
    public long lastSeenDaysAgo() {
        if (isOnline()) {
            return 0;
        }
        Duration duration = Duration.between(lastSeen, Instant.now());
        return duration.toDays();
    }

    private String formatDate(Instant instant) {
        // 简单格式化，实际应该使用配置的格式
        long daysAgo = Duration.between(instant, Instant.now()).toDays();
        if (daysAgo == 0) {
            return "今天";
        } else if (daysAgo == 1) {
            return "昨天";
        } else if (daysAgo < 30) {
            return daysAgo + "天前";
        } else {
            return (daysAgo / 30) + "个月前";
        }
    }

    /**
     * 创建成员信息
     */
    public static NationMember of(UUID playerId, String playerName, String rank, Instant joinedAt, Instant lastSeen) {
        return new NationMember(playerId, playerName, rank, joinedAt, lastSeen);
    }
}
