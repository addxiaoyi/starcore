package dev.starcore.starcore.module.army.raid.model;

import dev.starcore.starcore.module.nation.model.NationId;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 突袭数据模型
 * 表示一个完整的突袭实例
 */
public final class Raid {
    private final UUID id;
    private final NationId attackerNationId;
    private final NationId targetNationId;
    private final java.time.Instant createdAt;
    private final org.bukkit.Location location;
    private final String worldName;
    private final UUID initiatorId;

    private RaidPhase phase;
    private RaidStatus status;
    private Instant startedAt;
    private Instant endedAt;
    private Instant expiresAt;

    private final Map<UUID, RaidParticipant> attackers;
    private final Map<UUID, RaidParticipant> defenders;
    private final List<RaidEvent> events;
    private final Map<UUID, Double> attackerPoints;
    private final Map<UUID, Double> defenderPoints;

    private String endReason;

    public Raid(
        UUID id,
        NationId attackerNationId,
        NationId targetNationId,
        org.bukkit.Location location,
        UUID initiatorId
    ) {
        this.id = id;
        this.attackerNationId = attackerNationId;
        this.targetNationId = targetNationId;
        this.location = location;
        this.worldName = location.getWorld().getName();
        this.initiatorId = initiatorId;
        this.createdAt = Instant.now();
        this.phase = RaidPhase.PREPARATION;
        this.status = RaidStatus.PENDING;
        this.attackers = new java.util.concurrent.ConcurrentHashMap<>();
        this.defenders = new java.util.concurrent.ConcurrentHashMap<>();
        this.events = new CopyOnWriteArrayList<>();
        this.attackerPoints = new java.util.concurrent.ConcurrentHashMap<>();
        this.defenderPoints = new java.util.concurrent.ConcurrentHashMap<>();
    }

    public static Raid create(NationId attackerNationId, NationId targetNationId,
                              org.bukkit.Location location, UUID initiatorId) {
        return new Raid(UUID.randomUUID(), attackerNationId, targetNationId, location, initiatorId);
    }

    // Getters
    public UUID id() { return id; }
    public NationId attackerNationId() { return attackerNationId; }
    public NationId targetNationId() { return targetNationId; }
    public Instant createdAt() { return createdAt; }
    public org.bukkit.Location location() { return location; }
    public String worldName() { return worldName; }
    public UUID initiatorId() { return initiatorId; }
    public RaidPhase phase() { return phase; }
    public RaidStatus status() { return status; }
    public Instant startedAt() { return startedAt; }
    public Instant endedAt() { return endedAt; }
    public Instant expiresAt() { return expiresAt; }
    public String endReason() { return endReason; }
    public List<RaidEvent> events() { return List.copyOf(events); }

    public Collection<RaidParticipant> attackers() { return List.copyOf(attackers.values()); }
    public Collection<RaidParticipant> defenders() { return List.copyOf(defenders.values()); }

    public int attackerCount() { return attackers.size(); }
    public int defenderCount() { return defenders.size(); }
    public int totalParticipants() { return attackers.size() + defenders.size(); }

    public double attackerTotalPoints() { return attackerPoints.values().stream().mapToDouble(Double::doubleValue).sum(); }
    public double defenderTotalPoints() { return defenderPoints.values().stream().mapToDouble(Double::doubleValue).sum(); }

    public double attackerPointsOf(UUID playerId) { return attackerPoints.getOrDefault(playerId, 0.0); }
    public double defenderPointsOf(UUID playerId) { return defenderPoints.getOrDefault(playerId, 0.0); }

    public boolean isAttacker(UUID playerId) { return attackers.containsKey(playerId); }
    public boolean isDefender(UUID playerId) { return defenders.containsKey(playerId); }
    public boolean isParticipant(UUID playerId) { return isAttacker(playerId) || isDefender(playerId); }

    public boolean isActive() {
        return status == RaidStatus.ACTIVE && phase == RaidPhase.COMBAT;
    }

    public boolean isPending() {
        return status == RaidStatus.PENDING && phase == RaidPhase.PREPARATION;
    }

    public boolean isEnded() {
        return status == RaidStatus.ENDED;
    }

    public boolean isExpired() {
        if (expiresAt == null) return false;
        return Instant.now().isAfter(expiresAt);
    }

    // Setters/Modifiers
    public void setPhase(RaidPhase phase) { this.phase = phase; }
    public void setStatus(RaidStatus status) { this.status = status; }
    public void setEndedAt(Instant endedAt) { this.endedAt = endedAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public void setEndReason(String reason) { this.endReason = reason; }

    public void start() {
        this.startedAt = Instant.now();
        this.status = RaidStatus.ACTIVE;
        this.phase = RaidPhase.COMBAT;
    }

    public void end(String reason) {
        this.endedAt = Instant.now();
        this.status = RaidStatus.ENDED;
        this.endReason = reason;
    }

    public boolean addAttacker(RaidParticipant participant) {
        if (attackers.containsKey(participant.playerId())) {
            return false;
        }
        attackers.put(participant.playerId(), participant);
        return true;
    }

    public boolean addDefender(RaidParticipant participant) {
        if (defenders.containsKey(participant.playerId())) {
            return false;
        }
        defenders.put(participant.playerId(), participant);
        return true;
    }

    public boolean removeAttacker(UUID playerId) {
        return attackers.remove(playerId) != null;
    }

    public boolean removeDefender(UUID playerId) {
        return defenders.remove(playerId) != null;
    }

    public RaidParticipant getAttacker(UUID playerId) {
        return attackers.get(playerId);
    }

    public RaidParticipant getDefender(UUID playerId) {
        return defenders.get(playerId);
    }

    public RaidParticipant getParticipant(UUID playerId) {
        RaidParticipant p = attackers.get(playerId);
        if (p != null) return p;
        return defenders.get(playerId);
    }

    public void addAttackerPoints(UUID playerId, double points) {
        attackerPoints.merge(playerId, points, Double::sum);
    }

    public void addDefenderPoints(UUID playerId, double points) {
        defenderPoints.merge(playerId, points, Double::sum);
    }

    public void addEvent(RaidEvent event) {
        events.add(event);
    }

    public void recordKill(UUID killerId, UUID victimId, boolean isAttackerKill) {
        if (isAttackerKill) {
            addAttackerPoints(killerId, 10.0);
        } else {
            addDefenderPoints(killerId, 10.0);
        }
        addEvent(new RaidEvent(
            Instant.now(),
            RaidEventType.PLAYER_KILL,
            killerId,
            victimId,
            isAttackerKill ? "Attacker killed defender" : "Defender killed attacker"
        ));
    }

    public void recordBuildingDestroyed(UUID playerId, boolean isAttacker) {
        if (isAttacker) {
            addAttackerPoints(playerId, 25.0);
        } else {
            addDefenderPoints(playerId, 25.0);
        }
        addEvent(new RaidEvent(
            Instant.now(),
            RaidEventType.BUILDING_DESTROYED,
            playerId,
            null,
            isAttacker ? "Attacker destroyed building" : "Defender destroyed building"
        ));
    }

    public void recordLoot(UUID playerId, double amount, boolean isAttacker) {
        if (isAttacker) {
            addAttackerPoints(playerId, amount * 0.5); // 0.5 points per currency unit looted
        }
        addEvent(new RaidEvent(
            Instant.now(),
            RaidEventType.LOOT,
            playerId,
            null,
            String.format("%s looted %.2f", playerId, amount)
        ));
    }

    public RaidResult determineResult() {
        double attackPoints = attackerTotalPoints();
        double defendPoints = defenderTotalPoints();

        if (attackPoints >= 100.0) {
            return RaidResult.ATTACKER_VICTORY;
        } else if (defendPoints >= 100.0) {
            return RaidResult.DEFENDER_VICTORY;
        } else if (attackPoints > defendPoints * 1.5) {
            return RaidResult.ATTACKER_VICTORY;
        } else if (defendPoints > attackPoints * 1.5) {
            return RaidResult.DEFENDER_VICTORY;
        } else {
            return RaidResult.DRAW;
        }
    }

    public String getSummary() {
        return String.format("Raid[id=%s, phase=%s, status=%s, attackers=%d, defenders=%d, attackPoints=%.1f, defendPoints=%.1f]",
            id.toString().substring(0, 8), phase, status, attackerCount(), defenderCount(),
            attackerTotalPoints(), defenderTotalPoints());
    }
}