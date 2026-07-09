package dev.starcore.starcore.module.combat.model;

import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 战斗标记 - 记录玩家被标记的信息
 */
public final class CombatTag {
    private final UUID playerId;
    private final UUID taggerId;
    private final long startTime;
    private final long timeout;
    private final CombatTagType type;

    public CombatTag(UUID playerId, UUID taggerId, long timeout, CombatTagType type) {
        this.playerId = playerId;
        this.taggerId = taggerId;
        this.startTime = System.currentTimeMillis();
        this.timeout = timeout;
        this.type = type;
    }

    public CombatTag(UUID playerId, UUID taggerId, long startTime, long timeout, CombatTagType type) {
        this.playerId = playerId;
        this.taggerId = taggerId;
        this.startTime = startTime;
        this.timeout = timeout;
        this.type = type;
    }

    public UUID playerId() {
        return playerId;
    }

    public UUID taggerId() {
        return taggerId;
    }

    public long startTime() {
        return startTime;
    }

    public long timeout() {
        return timeout;
    }

    public CombatTagType type() {
        return type;
    }

    /**
     * 获取战斗标记的剩余时间（毫秒）
     */
    public long remainingTime() {
        long elapsed = System.currentTimeMillis() - startTime;
        return Math.max(0, timeout - elapsed);
    }

    /**
     * 检查战斗标记是否已过期
     */
    public boolean isExpired() {
        return System.currentTimeMillis() - startTime >= timeout;
    }

    /**
     * 获取玩家名称
     */
    public String getPlayerName() {
        Player player = org.bukkit.Bukkit.getPlayer(playerId);
        return player != null ? player.getName() : playerId.toString();
    }

    /**
     * 获取标记者名称
     */
    public String getTaggerName() {
        Player tagger = org.bukkit.Bukkit.getPlayer(taggerId);
        return tagger != null ? tagger.getName() : taggerId.toString();
    }

    @Override
    public String toString() {
        return "CombatTag{player=" + getPlayerName() + ", tagger=" + getTaggerName()
            + ", type=" + type + ", remaining=" + (remainingTime() / 1000) + "s}";
    }

    /**
     * 战斗标记类型
     */
    public enum CombatTagType {
        PLAYER,      // 玩家对玩家
        ARMY,        // 军队攻击
        MOB,         // 生物攻击
        ENVIRONMENT, // 环境伤害（岩浆、虚空等）
        PVP_ARENA    // PvP竞技场
    }
}
