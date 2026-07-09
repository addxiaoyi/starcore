package dev.starcore.starcore.module.combat.model;
import java.util.Optional;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 战斗会话 - 记录一场战斗的详细信息
 */
public final class CombatSession {
    private final UUID sessionId;
    private final UUID attackerId;
    private final UUID defenderId;
    private final long startTime;
    private final Location location;
    private final CombatSessionType type;

    // 伤害记录
    private int attackerDamage;
    private int defenderDamage;
    private int attackerHits;
    private int defenderHits;

    // 击杀记录
    private UUID killerId;
    private UUID victimId;
    private CombatEndReason endReason;

    // 状态
    private volatile CombatSessionState state = CombatSessionState.ACTIVE;
    private volatile long lastDamageTime;

    // 参与者列表（可能有多个玩家参与战斗，如混战）
    private final Set<UUID> participants;
    private final ConcurrentMap<UUID, Integer> damageDealtByPlayer;
    private final ConcurrentMap<UUID, Integer> damageTakenByPlayer;

    public CombatSession(UUID attackerId, UUID defenderId, Location location, CombatSessionType type) {
        this.sessionId = UUID.randomUUID();
        this.attackerId = attackerId;
        this.defenderId = defenderId;
        this.startTime = System.currentTimeMillis();
        this.location = location;
        this.type = type;
        this.lastDamageTime = startTime;

        this.participants = ConcurrentHashMap.newKeySet();
        this.participants.add(attackerId);
        this.participants.add(defenderId);

        this.damageDealtByPlayer = new ConcurrentHashMap<>();
        this.damageTakenByPlayer = new ConcurrentHashMap<>();
    }

    public UUID sessionId() {
        return sessionId;
    }

    public UUID attackerId() {
        return attackerId;
    }

    public UUID defenderId() {
        return defenderId;
    }

    public long startTime() {
        return startTime;
    }

    public Location location() {
        return location;
    }

    public CombatSessionType type() {
        return type;
    }

    public int attackerDamage() {
        return attackerDamage;
    }

    public int defenderDamage() {
        return defenderDamage;
    }

    public int attackerHits() {
        return attackerHits;
    }

    public int defenderHits() {
        return defenderHits;
    }

    public Optional<UUID> killerId() {
        return Optional.ofNullable(killerId);
    }

    public Optional<UUID> victimId() {
        return Optional.ofNullable(victimId);
    }

    public CombatEndReason endReason() {
        return endReason;
    }

    public CombatSessionState state() {
        return state;
    }

    public long lastDamageTime() {
        return lastDamageTime;
    }

    public Set<UUID> participants() {
        return Collections.unmodifiableSet(participants);
    }

    public ConcurrentMap<UUID, Integer> damageDealtByPlayer() {
        return damageDealtByPlayer;
    }

    public ConcurrentMap<UUID, Integer> damageTakenByPlayer() {
        return damageTakenByPlayer;
    }

    /**
     * 记录伤害
     */
    public void recordDamage(UUID dealer, UUID target, int damage) {
        lastDamageTime = System.currentTimeMillis();

        // 更新总伤害
        if (dealer.equals(attackerId)) {
            attackerDamage += damage;
        } else if (dealer.equals(defenderId)) {
            defenderDamage += damage;
        }

        // 更新命中率
        if (dealer.equals(attackerId)) {
            attackerHits++;
        } else if (dealer.equals(defenderId)) {
            defenderHits++;
        }

        // 更新个人伤害统计
        damageDealtByPlayer.merge(dealer, damage, Integer::sum);
        damageTakenByPlayer.merge(target, damage, Integer::sum);

        // 添加到参与者列表
        participants.add(dealer);
        participants.add(target);
    }

    /**
     * 记录击杀
     */
    public void recordKill(UUID killer, UUID victim, CombatEndReason reason) {
        this.killerId = killer;
        this.victimId = victim;
        this.endReason = reason;
        this.state = CombatSessionState.ENDED;
    }

    /**
     * 结束战斗会话
     */
    public void end(CombatEndReason reason) {
        this.endReason = reason;
        this.state = CombatSessionState.ENDED;
    }

    /**
     * 获取战斗持续时间（秒）
     */
    public long getDurationSeconds() {
        long endTime = state == CombatSessionState.ENDED ? lastDamageTime : System.currentTimeMillis();
        return (endTime - startTime) / 1000;
    }

    /**
     * 获取战斗持续时间（毫秒）
     */
    public long getDurationMillis() {
        long endTime = state == CombatSessionState.ENDED ? lastDamageTime : System.currentTimeMillis();
        return endTime - startTime;
    }

    /**
     * 检查玩家是否是这场战斗的参与者
     */
    public boolean isParticipant(UUID playerId) {
        return participants.contains(playerId);
    }

    /**
     * 获取指定玩家的伤害输出
     */
    public int getDamageDealtBy(UUID playerId) {
        return damageDealtByPlayer.getOrDefault(playerId, 0);
    }

    /**
     * 获取指定玩家的承受伤害
     */
    public int getDamageTakenBy(UUID playerId) {
        return damageTakenByPlayer.getOrDefault(playerId, 0);
    }

    /**
     * 获取战斗摘要
     */
    public CombatSummary getSummary() {
        String attackerName = getPlayerName(attackerId);
        String defenderName = getPlayerName(defenderId);
        String killerName = killerId != null ? getPlayerName(killerId) : null;
        String victimName = victimId != null ? getPlayerName(victimId) : null;

        return new CombatSummary(
            sessionId,
            attackerName,
            defenderName,
            attackerDamage,
            defenderDamage,
            attackerHits,
            defenderHits,
            killerName,
            victimName,
            endReason != null ? endReason.name() : null,
            getDurationSeconds(),
            type.name(),
            state.name()
        );
    }

    private String getPlayerName(UUID playerId) {
        Player player = org.bukkit.Bukkit.getPlayer(playerId);
        return player != null ? player.getName() : playerId.toString();
    }

    /**
     * 战斗会话类型
     */
    public enum CombatSessionType {
        DUEL,
        WAR,
        ARMY_BATTLE,
        FREE_PVP,
        ARENA
    }

    /**
     * 战斗会话状态
     */
    public enum CombatSessionState {
        ACTIVE,
        ENDED,
        TIMEOUT
    }

    /**
     * 战斗结束原因
     */
    public enum CombatEndReason {
        KILL,
        DISCONNECT,
        LOGOUT,
        TIMEOUT,
        PEACE_SIGNED,
        AREA_LEAVE,
        ADMIN_CANCEL,
        SURRENDER
    }

    /**
     * 战斗摘要记录
     */
    public record CombatSummary(
        UUID sessionId,
        String attackerName,
        String defenderName,
        int attackerDamage,
        int defenderDamage,
        int attackerHits,
        int defenderHits,
        String killerName,
        String victimName,
        String endReason,
        long durationSeconds,
        String type,
        String state
    ) {}
}
