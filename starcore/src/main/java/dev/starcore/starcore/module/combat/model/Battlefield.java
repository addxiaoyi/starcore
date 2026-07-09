package dev.starcore.starcore.module.combat.model;
import java.util.Optional;

import dev.starcore.starcore.module.nation.model.NationId;
import org.bukkit.Location;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 战场 - 追踪特定区域的战斗活动
 * 支持国家对抗战场和自由PVP战场
 */
public final class Battlefield {
    private final UUID battlefieldId;
    private final String name;
    private final Location center;
    private final double radius;
    private final BattlefieldType type;

    // 国家对抗信息
    private NationId nation1;
    private NationId nation2;
    private volatile NationId faction1Nation;
    private volatile NationId faction2Nation;
    private volatile NationId winner;

    // 参与者
    private final Set<UUID> participants;
    private final ConcurrentMap<UUID, BattlefieldPlayerInfo> playerInfo;

    // 时间
    private final long startTime;
    private volatile long endTime;

    // 战斗统计
    private int totalKills;
    private int totalDeaths;
    private int totalDamage;

    // 战斗会话
    private final Set<UUID> combatSessions;

    // 战场状态
    private volatile BattlefieldStatus status = BattlefieldStatus.WAITING;

    public Battlefield(String name, Location center, double radius, BattlefieldType type) {
        this.battlefieldId = UUID.randomUUID();
        this.name = name;
        this.center = center;
        this.radius = radius;
        this.type = type;
        this.startTime = System.currentTimeMillis();

        this.participants = ConcurrentHashMap.newKeySet();
        this.playerInfo = new ConcurrentHashMap<>();
        this.combatSessions = ConcurrentHashMap.newKeySet();
    }

    // ==================== 国家对抗方法 ====================

    /**
     * 设置参与国家（对阵双方）
     */
    public void setNation1(NationId nationId) {
        this.nation1 = nationId;
    }

    public void setNation2(NationId nationId) {
        this.nation2 = nationId;
    }

    public NationId getNation1() {
        return nation1;
    }

    public NationId getNation2() {
        return nation2;
    }

    /**
     * 设置实际加入的国家阵营
     */
    public void setFaction1Nation(NationId nationId) {
        this.faction1Nation = nationId;
    }

    public void setFaction2Nation(NationId nationId) {
        this.faction2Nation = nationId;
    }

    public NationId getFaction1Nation() {
        return faction1Nation;
    }

    public NationId getFaction2Nation() {
        return faction2Nation;
    }

    /**
     * 设置获胜国家
     */
    public void setWinner(NationId winner) {
        this.winner = winner;
    }

    public NationId getWinner() {
        return winner;
    }

    /**
     * 开始战斗
     */
    public void startBattle() {
        this.status = BattlefieldStatus.ACTIVE;
    }

    /**
     * 获取战场状态
     */
    public BattlefieldStatus getStatus() {
        return status;
    }

    /**
     * 检查国家是否参与此战场
     */
    public boolean isNationParticipating(NationId nationId) {
        if (nationId == null) return false;
        UUID id = nationId.value();
        return (nation1 != null && nation1.value().equals(id)) ||
               (nation2 != null && nation2.value().equals(id)) ||
               (faction1Nation != null && faction1Nation.value().equals(id)) ||
               (faction2Nation != null && faction2Nation.value().equals(id));
    }

    /**
     * 获取战场对手国家
     */
    public NationId getOpposingNation(NationId nationId) {
        if (nationId == null) return null;
        UUID id = nationId.value();

        if (faction1Nation != null && faction1Nation.value().equals(id)) {
            return faction2Nation;
        }
        if (faction2Nation != null && faction2Nation.value().equals(id)) {
            return faction1Nation;
        }
        return null;
    }

    /**
     * 战场状态枚举
     */
    public enum BattlefieldStatus {
        WAITING,    // 等待开始
        ACTIVE,     // 进行中
        ENDED,      // 已结束
        CANCELLED   // 已取消
    }

    public UUID battlefieldId() {
        return battlefieldId;
    }

    public String name() {
        return name;
    }

    public Location center() {
        return center;
    }

    public double radius() {
        return radius;
    }

    public BattlefieldType type() {
        return type;
    }

    public long startTime() {
        return startTime;
    }

    public Optional<Long> endTime() {
        return endTime > 0 ? Optional.of(endTime) : Optional.empty();
    }

    public Set<UUID> participants() {
        return Collections.unmodifiableSet(participants);
    }

    public ConcurrentMap<UUID, BattlefieldPlayerInfo> playerInfo() {
        return playerInfo;
    }

    public Set<UUID> combatSessions() {
        return Collections.unmodifiableSet(combatSessions);
    }

    public int totalKills() {
        return totalKills;
    }

    public int totalDeaths() {
        return totalDeaths;
    }

    public int totalDamage() {
        return totalDamage;
    }

    /**
     * 添加参与者
     */
    public void addParticipant(UUID playerId) {
        if (participants.add(playerId)) {
            playerInfo.put(playerId, new BattlefieldPlayerInfo(playerId));
        }
    }

    /**
     * 移除参与者
     */
    public void removeParticipant(UUID playerId) {
        participants.remove(playerId);
        playerInfo.remove(playerId);
    }

    /**
     * 检查位置是否在战场范围内
     */
    public boolean isInBattlefield(Location location) {
        if (!location.getWorld().equals(center.getWorld())) {
            return false;
        }
        double dx = location.getX() - center.getX();
        double dy = location.getY() - center.getY();
        double dz = location.getZ() - center.getZ();
        return (dx * dx + dy * dy + dz * dz) <= radius * radius;
    }

    /**
     * 添加战斗会话
     */
    public void addCombatSession(UUID sessionId) {
        combatSessions.add(sessionId);
    }

    /**
     * 移除战斗会话
     */
    public void removeCombatSession(UUID sessionId) {
        combatSessions.remove(sessionId);
    }

    /**
     * 记录击杀
     */
    public void recordKill() {
        totalKills++;
    }

    /**
     * 记录死亡
     */
    public void recordDeath() {
        totalDeaths++;
    }

    /**
     * 记录伤害
     */
    public void recordDamage(int damage) {
        totalDamage += damage;
    }

    /**
     * 更新玩家信息
     */
    public void updatePlayerInfo(UUID playerId, int damageDealt, int damageTaken, boolean killed, boolean died) {
        BattlefieldPlayerInfo info = playerInfo.get(playerId);
        if (info != null) {
            info.addDamageDealt(damageDealt);
            info.addDamageTaken(damageTaken);
            if (killed) info.addKill();
            if (died) info.addDeath();
        }
    }

    /**
     * 结束战场
     */
    public void end() {
        this.endTime = System.currentTimeMillis();
    }

    /**
     * 获取战场持续时间（秒）
     */
    public long getDurationSeconds() {
        long end = endTime > 0 ? endTime : System.currentTimeMillis();
        return (end - startTime) / 1000;
    }

    /**
     * 获取参与者数量
     */
    public int getParticipantCount() {
        return participants.size();
    }

    /**
     * 检查战场是否活跃
     */
    public boolean isActive() {
        return endTime == 0 && !participants.isEmpty();
    }

    /**
     * 获取战场摘要
     */
    public BattlefieldSummary getSummary() {
        return new BattlefieldSummary(
            battlefieldId,
            name,
            center.getWorld().getName(),
            center.getBlockX() + "," + center.getBlockY() + "," + center.getBlockZ(),
            radius,
            type.name(),
            participants.size(),
            totalKills,
            totalDeaths,
            totalDamage,
            getDurationSeconds(),
            combatSessions.size(),
            endTime > 0
        );
    }

    /**
     * 战场类型
     */
    public enum BattlefieldType {
        WAR_ZONE,      // 战争区域
        PVP_ARENA,     // PvP竞技场
        ARMY_BATTLE,   // 军队战斗区域
        FREE_PVP,      // 自由PVP区域
        EVENT          // 活动战场
    }

    /**
     * 战场玩家信息
     */
    public static class BattlefieldPlayerInfo {
        private final UUID playerId;
        private int damageDealt;
        private int damageTaken;
        private int kills;
        private int deaths;
        private long joinTime;
        private long lastActivityTime;

        public BattlefieldPlayerInfo(UUID playerId) {
            this.playerId = playerId;
            this.joinTime = System.currentTimeMillis();
            this.lastActivityTime = joinTime;
        }

        public UUID playerId() {
            return playerId;
        }

        public int damageDealt() {
            return damageDealt;
        }

        public void addDamageDealt(int damage) {
            this.damageDealt += damage;
            this.lastActivityTime = System.currentTimeMillis();
        }

        public int damageTaken() {
            return damageTaken;
        }

        public void addDamageTaken(int damage) {
            this.damageTaken += damage;
            this.lastActivityTime = System.currentTimeMillis();
        }

        public int kills() {
            return kills;
        }

        public void addKill() {
            this.kills++;
        }

        public int deaths() {
            return deaths;
        }

        public void addDeath() {
            this.deaths++;
        }

        public long joinTime() {
            return joinTime;
        }

        public long lastActivityTime() {
            return lastActivityTime;
        }

        public String getPlayerName() {
            var player = org.bukkit.Bukkit.getPlayer(playerId);
            return player != null ? player.getName() : playerId.toString();
        }
    }

    /**
     * 战场摘要
     */
    public record BattlefieldSummary(
        UUID battlefieldId,
        String name,
        String world,
        String location,
        double radius,
        String type,
        int participantCount,
        int totalKills,
        int totalDeaths,
        int totalDamage,
        long durationSeconds,
        int combatSessionCount,
        boolean ended
    ) {}
}
