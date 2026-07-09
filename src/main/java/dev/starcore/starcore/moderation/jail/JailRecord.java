package dev.starcore.starcore.moderation.jail;

import org.bukkit.Location;
import java.util.UUID;

/**
 * 监禁记录
 */
public final class JailRecord {
    private final UUID playerId;
    private final String playerName;
    private final UUID jailedBy;
    private final String reason;
    private final long jailTime;
    private final long duration;      // 监禁时长（毫秒），-1表示永久
    private final Location previousLocation;  // 之前的位置
    private boolean active;

    public JailRecord(UUID playerId, String playerName, UUID jailedBy, String reason, long duration, Location previousLocation) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.jailedBy = jailedBy;
        this.reason = reason;
        this.jailTime = System.currentTimeMillis();
        this.duration = duration;
        this.previousLocation = previousLocation;
        this.active = true;
    }

    /**
     * 检查是否已过期
     */
    public boolean isExpired() {
        if (duration == -1) return false; // 永久监禁
        return System.currentTimeMillis() > (jailTime + duration);
    }

    /**
     * 获取剩余时间（毫秒）
     */
    public long getRemainingTime() {
        if (duration == -1) return -1; // 永久
        long remaining = (jailTime + duration) - System.currentTimeMillis();
        return Math.max(0, remaining);
    }

    /**
     * 检查是否永久监禁
     */
    public boolean isPermanent() {
        return duration == -1;
    }

    /**
     * 释放
     */
    public void release() {
        this.active = false;
    }

    // Getters
    public UUID getPlayerId() { return playerId; }
    public String getPlayerName() { return playerName; }
    public UUID getJailedBy() { return jailedBy; }
    public String getReason() { return reason; }
    public long getJailTime() { return jailTime; }
    public long getDuration() { return duration; }
    public Location getPreviousLocation() { return previousLocation; }
    public boolean isActive() { return active; }
}
