package dev.starcore.starcore.moderation.mute;

import java.util.UUID;

/**
 * 禁言记录
 */
public final class MuteRecord {
    private final UUID playerId;
    private final String playerName;
    private final UUID mutedBy;
    private final String reason;
    private final long muteTime;
    private final long duration;      // 禁言时长（毫秒），-1表示永久
    private boolean active;

    public MuteRecord(UUID playerId, String playerName, UUID mutedBy, String reason, long duration) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.mutedBy = mutedBy;
        this.reason = reason;
        this.muteTime = System.currentTimeMillis();
        this.duration = duration;
        this.active = true;
    }

    /**
     * 检查是否已过期
     */
    public boolean isExpired() {
        if (duration == -1) return false; // 永久禁言
        return System.currentTimeMillis() > (muteTime + duration);
    }

    /**
     * 获取剩余时间（毫秒）
     */
    public long getRemainingTime() {
        if (duration == -1) return -1; // 永久
        long remaining = (muteTime + duration) - System.currentTimeMillis();
        return Math.max(0, remaining);
    }

    /**
     * 检查是否永久禁言
     */
    public boolean isPermanent() {
        return duration == -1;
    }

    /**
     * 解除禁言
     */
    public void unmute() {
        this.active = false;
    }

    // Getters
    public UUID getPlayerId() { return playerId; }
    public String getPlayerName() { return playerName; }
    public UUID getMutedBy() { return mutedBy; }
    public String getReason() { return reason; }
    public long getMuteTime() { return muteTime; }
    public long getDuration() { return duration; }
    public boolean isActive() { return active; }
}
