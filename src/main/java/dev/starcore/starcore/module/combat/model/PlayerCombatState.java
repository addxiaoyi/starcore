package dev.starcore.starcore.module.combat.model;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 玩家战斗状态 - 追踪玩家的实时战斗状态
 */
public final class PlayerCombatState {
    private final UUID playerId;

    // 战斗状态
    private volatile boolean inCombat;
    private volatile long combatStartTime;
    private volatile long lastDamageTime;

    // 战斗标记
    private volatile CombatTag combatTag;

    // 伤害统计
    private final AtomicInteger totalDamageDealt = new AtomicInteger(0);
    private final AtomicInteger totalDamageTaken = new AtomicInteger(0);
    private final AtomicInteger totalKills = new AtomicInteger(0);
    private final AtomicInteger totalDeaths = new AtomicInteger(0);

    // 当前战斗会话
    private volatile UUID currentSessionId;

    // 击杀记录
    private volatile UUID lastKillerId;
    private volatile UUID lastVictimId;

    public PlayerCombatState(UUID playerId) {
        this.playerId = playerId;
        this.inCombat = false;
        this.combatStartTime = 0;
        this.lastDamageTime = 0;
    }

    public UUID playerId() {
        return playerId;
    }

    public boolean isInCombat() {
        return inCombat;
    }

    public void setInCombat(boolean inCombat) {
        this.inCombat = inCombat;
        if (inCombat && combatStartTime == 0) {
            this.combatStartTime = System.currentTimeMillis();
        }
    }

    public long combatStartTime() {
        return combatStartTime;
    }

    public void setCombatStartTime(long combatStartTime) {
        this.combatStartTime = combatStartTime;
    }

    public long lastDamageTime() {
        return lastDamageTime;
    }

    public void setLastDamageTime(long lastDamageTime) {
        this.lastDamageTime = lastDamageTime;
    }

    public Optional<CombatTag> combatTag() {
        return Optional.ofNullable(combatTag);
    }

    public void setCombatTag(CombatTag combatTag) {
        this.combatTag = combatTag;
    }

    public void clearCombatTag() {
        this.combatTag = null;
    }

    public int getTotalDamageDealt() {
        return totalDamageDealt.get();
    }

    public void addDamageDealt(int damage) {
        totalDamageDealt.addAndGet(damage);
    }

    public int getTotalDamageTaken() {
        return totalDamageTaken.get();
    }

    public void addDamageTaken(int damage) {
        totalDamageTaken.addAndGet(damage);
    }

    public int getTotalKills() {
        return totalKills.get();
    }

    public void addKill() {
        totalKills.incrementAndGet();
    }

    public int getTotalDeaths() {
        return totalDeaths.get();
    }

    public void addDeath() {
        totalDeaths.incrementAndGet();
    }

    public Optional<UUID> currentSessionId() {
        return Optional.ofNullable(currentSessionId);
    }

    public void setCurrentSessionId(UUID sessionId) {
        this.currentSessionId = sessionId;
    }

    public void clearCurrentSession() {
        this.currentSessionId = null;
    }

    public Optional<UUID> lastKillerId() {
        return Optional.ofNullable(lastKillerId);
    }

    public void setLastKillerId(UUID killerId) {
        this.lastKillerId = killerId;
    }

    public Optional<UUID> lastVictimId() {
        return Optional.ofNullable(lastVictimId);
    }

    public void setLastVictimId(UUID victimId) {
        this.lastVictimId = victimId;
    }

    /**
     * 获取当前战斗持续时间（秒）
     */
    public long getCombatDurationSeconds() {
        if (!inCombat || combatStartTime == 0) {
            return 0;
        }
        return (System.currentTimeMillis() - combatStartTime) / 1000;
    }

    /**
     * 获取距离上次受伤的时间（秒）
     */
    public long getTimeSinceLastDamage() {
        if (lastDamageTime == 0) {
            return Long.MAX_VALUE;
        }
        return (System.currentTimeMillis() - lastDamageTime) / 1000;
    }

    /**
     * 获取战斗标签剩余时间（秒）
     */
    public long getCombatTagRemainingSeconds() {
        CombatTag tag = combatTag;
        if (tag == null) {
            return 0;
        }
        return tag.remainingTime() / 1000;
    }

    /**
     * 进入战斗状态
     */
    public void enterCombat(UUID taggerId, long timeout, CombatTag.CombatTagType type) {
        this.inCombat = true;
        this.combatStartTime = System.currentTimeMillis();
        this.lastDamageTime = System.currentTimeMillis();
        this.combatTag = new CombatTag(playerId, taggerId, timeout, type);
    }

    /**
     * 退出战斗状态
     */
    public void exitCombat() {
        this.inCombat = false;
        this.combatTag = null;
    }

    /**
     * 重置战斗状态
     */
    public void reset() {
        this.inCombat = false;
        this.combatStartTime = 0;
        this.lastDamageTime = 0;
        this.combatTag = null;
        this.currentSessionId = null;
    }

    /**
     * 获取玩家名称
     */
    public String getPlayerName() {
        var player = org.bukkit.Bukkit.getPlayer(playerId);
        return player != null ? player.getName() : playerId.toString();
    }

    @Override
    public String toString() {
        return "PlayerCombatState{player=" + getPlayerName()
            + ", inCombat=" + inCombat
            + ", duration=" + getCombatDurationSeconds() + "s"
            + ", tag=" + (combatTag != null ? combatTag.type() : "none")
            + "}";
    }

    /**
     * 状态快照用于持久化
     */
    public CombatStateSnapshot toSnapshot() {
        return new CombatStateSnapshot(
            playerId,
            inCombat,
            combatStartTime,
            lastDamageTime,
            combatTag != null ? combatTag.taggerId() : null,
            combatTag != null ? combatTag.timeout() : 0,
            combatTag != null ? combatTag.startTime() : 0,
            combatTag != null ? combatTag.type().name() : null,
            totalDamageDealt.get(),
            totalDamageTaken.get(),
            totalKills.get(),
            totalDeaths.get(),
            lastKillerId,
            lastVictimId
        );
    }

    /**
     * 战斗状态快照
     */
    public record CombatStateSnapshot(
        UUID playerId,
        boolean inCombat,
        long combatStartTime,
        long lastDamageTime,
        UUID lastTaggerId,
        long tagTimeout,
        long tagStartTime,
        String tagType,
        int totalDamageDealt,
        int totalDamageTaken,
        int totalKills,
        int totalDeaths,
        UUID lastKillerId,
        UUID lastVictimId
    ) {
        public static CombatStateSnapshot create(UUID playerId) {
            return new CombatStateSnapshot(
                playerId, false, 0, 0, null, 0, 0, null, 0, 0, 0, 0, null, null
            );
        }
    }
}
