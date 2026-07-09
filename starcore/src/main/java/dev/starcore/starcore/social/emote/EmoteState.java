package dev.starcore.starcore.social.emote;

import java.util.*;

/**
 * 玩家动作状态追踪
 */
public class EmoteState {
    private final UUID playerId;
    private String currentEmoteId;
    private long emoteStartTime;
    private long lastEmoteTime; // 用于冷却计算（共享全局最近冷却，遗留兼容）
    private UUID targetPlayerId; // 目标玩家
    private boolean isAnimating;

    // D-046: 每个 emote 独立冷却开始时间，替代所有 emote 共享一个 lastEmoteTime
    private final Map<String, Long> emoteCooldowns = new HashMap<>();

    public EmoteState(UUID playerId) {
        this.playerId = playerId;
        this.currentEmoteId = null;
        this.emoteStartTime = 0;
        this.lastEmoteTime = 0;
        this.targetPlayerId = null;
        this.isAnimating = false;
    }

    public UUID getPlayerId() { return playerId; }
    public String getCurrentEmoteId() { return currentEmoteId; }
    public long getEmoteStartTime() { return emoteStartTime; }
    public long getLastEmoteTime() { return lastEmoteTime; }
    public UUID getTargetPlayerId() { return targetPlayerId; }
    public boolean isAnimating() { return isAnimating; }

    public void setCurrentEmote(String emoteId, long startTime, UUID target) {
        this.currentEmoteId = emoteId;
        this.emoteStartTime = startTime;
        this.targetPlayerId = target;
        this.isAnimating = true;
        // D-046: 该 emote 进入冷却
        emoteCooldowns.put(emoteId, startTime);
    }

    public void clearEmote() {
        this.currentEmoteId = null;
        this.emoteStartTime = 0;
        this.targetPlayerId = null;
        this.isAnimating = false;
    }

    public void updateLastEmoteTime() {
        this.lastEmoteTime = System.currentTimeMillis();
        if (this.currentEmoteId != null) {
            emoteCooldowns.put(this.currentEmoteId, this.lastEmoteTime);
        }
    }

    public boolean isOnCooldown(int cooldownSeconds) {
        // D-046: 优先以当前 emote 的冷却时间为准；向后兼容 lastEmoteTime 共享冷却
        if (currentEmoteId != null) {
            Long cd = emoteCooldowns.get(currentEmoteId);
            if (cd != null) {
                long elapsed = System.currentTimeMillis() - cd;
                return elapsed < (cooldownSeconds * 1000L);
            }
        }
        if (lastEmoteTime == 0) return false;
        long elapsed = System.currentTimeMillis() - lastEmoteTime;
        return elapsed < (cooldownSeconds * 1000L);
    }

    /** D-046: 检查指定 emote 的独立冷却 */
    public boolean isOnCooldown(String emoteId, int cooldownSeconds) {
        Long cd = emoteCooldowns.get(emoteId);
        if (cd == null) return false;
        long elapsed = System.currentTimeMillis() - cd;
        return elapsed < (cooldownSeconds * 1000L);
    }

    public int getRemainingCooldown(int cooldownSeconds) {
        if (lastEmoteTime == 0) return 0;
        long elapsed = System.currentTimeMillis() - lastEmoteTime;
        long remaining = (cooldownSeconds * 1000L) - elapsed;
        return (int) Math.max(0, remaining / 1000);
    }
}
