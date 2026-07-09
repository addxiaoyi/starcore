package dev.starcore.starcore.moderation.ban;

import java.util.UUID;

/**
 * 封禁记录
 */
public final class BanRecord {
    private final UUID playerId;
    private final String playerName;
    private final UUID bannedBy;
    private final String reason;
    private final long banTime;
    private final long duration;      // 封禁时长（毫秒），-1表示永久
    private final boolean ipBan;      // 是否IP封禁
    private final String ipAddress;   // IP地址
    private boolean active;

    public BanRecord(UUID playerId, String playerName, UUID bannedBy, String reason, long duration, boolean ipBan, String ipAddress) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.bannedBy = bannedBy;
        this.reason = reason;
        this.banTime = System.currentTimeMillis();
        this.duration = duration;
        this.ipBan = ipBan;
        this.ipAddress = ipAddress;
        this.active = true;
    }

    /**
     * 检查是否已过期
     */
    public boolean isExpired() {
        if (duration == -1) return false; // 永久封禁
        return System.currentTimeMillis() > (banTime + duration);
    }

    /**
     * 获取剩余时间（毫秒）
     */
    public long getRemainingTime() {
        if (duration == -1) return -1; // 永久
        long remaining = (banTime + duration) - System.currentTimeMillis();
        return Math.max(0, remaining);
    }

    /**
     * 检查是否永久封禁
     */
    public boolean isPermanent() {
        return duration == -1;
    }

    /**
     * 解除封禁
     */
    public void unban() {
        this.active = false;
    }

    // Getters
    public UUID getPlayerId() { return playerId; }
    public String getPlayerName() { return playerName; }
    public UUID getBannedBy() { return bannedBy; }
    public String getReason() { return reason; }
    public long getBanTime() { return banTime; }
    public long getDuration() { return duration; }
    public boolean isIpBan() { return ipBan; }
    public String getIpAddress() { return ipAddress; }
    public boolean isActive() { return active; }
}
