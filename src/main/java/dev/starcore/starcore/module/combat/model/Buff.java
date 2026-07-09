package dev.starcore.starcore.module.combat.model;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Buff效果 - 战斗中的增益/减益效果
 * 提供临时属性加成，如攻击力提升、防御力提升、速度加成等
 */
public final class Buff {
    private final UUID buffId;
    private final UUID targetId;
    private final BuffType type;
    private final long startTime;
    private final long duration;
    private final double effectValue;

    // Buff来源（攻击者/施法者）
    private final UUID sourceId;

    // Buff状态
    private volatile boolean active;

    // 全局Buff追踪
    private static final ConcurrentMap<UUID, ConcurrentMap<UUID, Buff>> playerBuffs = new ConcurrentHashMap<>();

    public Buff(UUID targetId, BuffType type, long duration, double effectValue, UUID sourceId) {
        this.buffId = UUID.randomUUID();
        this.targetId = targetId;
        this.type = type;
        this.startTime = System.currentTimeMillis();
        this.duration = duration;
        this.effectValue = effectValue;
        this.sourceId = sourceId;
        this.active = true;

        // 注册到全局追踪
        registerBuff(this);
    }

    /**
     * 注册Buff到全局追踪
     */
    private static void registerBuff(Buff buff) {
        playerBuffs.computeIfAbsent(buff.targetId, k -> new ConcurrentHashMap<>())
                   .put(buff.buffId, buff);
    }

    /**
     * 移除Buff
     */
    public void remove() {
        this.active = false;
        ConcurrentMap<UUID, Buff> buffs = playerBuffs.get(targetId);
        if (buffs != null) {
            buffs.remove(buffId);
        }
    }

    /**
     * 检查Buff是否已过期
     */
    public boolean isExpired() {
        return System.currentTimeMillis() - startTime >= duration;
    }

    /**
     * 获取剩余时间（毫秒）
     */
    public long remainingTime() {
        long elapsed = System.currentTimeMillis() - startTime;
        return Math.max(0, duration - elapsed);
    }

    /**
     * 获取剩余时间（秒）
     */
    public long remainingSeconds() {
        return remainingTime() / 1000;
    }

    /**
     * 获取玩家当前的所有活跃Buff
     */
    public static ConcurrentMap<UUID, Buff> getPlayerBuffs(UUID playerId) {
        return playerBuffs.getOrDefault(playerId, new ConcurrentHashMap<>());
    }

    /**
     * 清理玩家的过期Buff
     */
    public static void cleanupExpiredBuffs(UUID playerId) {
        ConcurrentMap<UUID, Buff> buffs = playerBuffs.get(playerId);
        if (buffs != null) {
            buffs.entrySet().removeIf(entry -> {
                if (entry.getValue().isExpired()) {
                    entry.getValue().remove();
                    return true;
                }
                return false;
            });
        }
    }

    /**
     * 清除玩家的所有Buff
     */
    public static void clearPlayerBuffs(UUID playerId) {
        ConcurrentMap<UUID, Buff> buffs = playerBuffs.remove(playerId);
        if (buffs != null) {
            buffs.values().forEach(Buff::remove);
        }
    }

    /**
     * 获取玩家指定类型的Buff
     */
    public static Buff getBuff(UUID playerId, BuffType type) {
        ConcurrentMap<UUID, Buff> buffs = playerBuffs.get(playerId);
        if (buffs != null) {
            return buffs.values().stream()
                .filter(b -> b.type == type && b.active && !b.isExpired())
                .findFirst()
                .orElse(null);
        }
        return null;
    }

    // Getters

    public UUID buffId() {
        return buffId;
    }

    public UUID targetId() {
        return targetId;
    }

    public BuffType type() {
        return type;
    }

    public long startTime() {
        return startTime;
    }

    public long duration() {
        return duration;
    }

    public double effectValue() {
        return effectValue;
    }

    public UUID sourceId() {
        return sourceId;
    }

    public boolean isActive() {
        return active;
    }

    /**
     * Buff类型枚举
     */
    public enum BuffType {
        // 攻击增益
        ATTACK_BOOST(20, "攻击力提升", "提高 %value%% 攻击力"),
        CRITICAL_BOOST(21, "暴击率提升", "提高 %value%% 暴击率"),
        LIFESTEAL(22, "生命偷取", "造成伤害的 %value%% 转化为生命"),

        // 防御增益
        DEFENSE_BOOST(23, "防御力提升", "提高 %value%% 防御力"),
        ARMOR_BOOST(24, "护甲提升", "提高 %value%% 护甲"),
        RESISTANCE(25, "抗性提升", "减少 %value%% 受到的伤害"),

        // 速度增益
        SPEED_BOOST(26, "速度提升", "提高 %value%% 移动速度"),
        HASTE(27, "急迫效果", "提高 %value%% 采掘和攻击速度"),

        // 生命增益
        HEALING_BOOST(28, "治疗提升", "提高 %value%% 治疗效果"),
        REGENERATION(29, "生命恢复", "每秒恢复 %value%% 最大生命值"),
        ABSORPTION(30, "吸收护盾", "获得 %value% 点临时生命值"),

        // 负面效果
        WEAKNESS(1, "虚弱", "降低 %value%% 攻击力"),
        SLOWNESS(2, "缓慢", "降低 %value%% 移动速度"),
        POISON(3, "中毒", "每秒受到 %value% 点伤害"),
        WITHER(4, "凋零", "每秒受到 %value% 点伤害"),
        BLINDNESS(5, "失明", "视野范围降低 %value%%"),
        FATIGUE(6, "疲劳", "降低 %value%% 攻击速度"),
        MINING_FATIGUE(7, "挖掘疲劳", "降低 %value%% 采掘速度"),
        NAUSEA(8, "恶心", "画面扭曲效果"),
        GLOWING(9, "发光", "在黑暗中可见"),
        LEVITATION(10, "悬浮", "向上漂浮"),
        UNLUCKY(11, "倒霉", "降低 %value%% 运气"),
        DARKNESS(12, "黑暗", "视野受限"),
        WARNING(13, "警告", "显示战斗警告效果"),

        // 特殊Buff
        INVULNERABILITY(31, "无敌", "免疫所有伤害"),
        INVISIBILITY(32, "隐身", "对其他玩家不可见"),
        FROZEN(33, "冰冻", "无法移动"),
        STUNNED(34, "眩晕", "无法操作"),
        SILENCED(35, "沉默", "无法使用命令"),

        // 战场专属
        BATTLE_RALLY(40, "战斗激励", "提高范围内友军 %value%% 战斗力"),
        BATTLE_FEAR(41, "战场恐惧", "降低敌人 %value%% 战斗力"),
        SIEGE_BREAKER(42, "攻城破击", "对建筑伤害提高 %value%%"),

        // 国家战争Buff
        NATION_MORALE(50, "国家士气", "提高国家成员 %value%% 全属性"),
        WAR_BANNER(51, "战旗效果", "在旗帜范围内提高 %value%% 战斗力");

        private final int effectId;
        private final String displayName;
        private final String description;

        BuffType(int effectId, String displayName, String description) {
            this.effectId = effectId;
            this.displayName = displayName;
            this.description = description;
        }

        public int effectId() {
            return effectId;
        }

        public String displayName() {
            return displayName;
        }

        public String description() {
            return description;
        }

        /**
         * 格式化描述（替换 %value%）
         */
        public String formatDescription(double value) {
            if (description.contains("%value%")) {
                return description.replace("%value%", String.valueOf((int) value));
            } else if (description.contains("%value")) {
                return description.replace("%value", String.valueOf((int) value));
            }
            return description;
        }

        /**
         * 是否为增益效果
         */
        public boolean isPositive() {
            return switch (this) {
                case ATTACK_BOOST, CRITICAL_BOOST, LIFESTEAL,
                     DEFENSE_BOOST, ARMOR_BOOST, RESISTANCE,
                     SPEED_BOOST, HASTE, HEALING_BOOST, REGENERATION, ABSORPTION,
                     INVULNERABILITY, INVISIBILITY,
                     BATTLE_RALLY, NATION_MORALE, WAR_BANNER -> true;
                default -> false;
            };
        }

        /**
         * 是否为负面效果
         */
        public boolean isNegative() {
            return !isPositive() && this != FROZEN && this != STUNNED &&
                   this != SILENCED && this != GLOWING && this != LEVITATION;
        }
    }

    /**
     * Buff信息摘要
     */
    public record BuffInfo(
        UUID buffId,
        BuffType type,
        String displayName,
        long remainingSeconds,
        double effectValue,
        String description,
        boolean isPositive
    ) {
        public static BuffInfo from(Buff buff) {
            return new BuffInfo(
                buff.buffId,
                buff.type,
                buff.type.displayName(),
                buff.remainingSeconds(),
                buff.effectValue(),
                buff.type.formatDescription(buff.effectValue()),
                buff.type.isPositive()
            );
        }
    }
}
