package dev.starcore.starcore.module.army.wounded;

import dev.starcore.starcore.module.army.model.ArmyUnit;
import dev.starcore.starcore.module.nation.model.NationId;
import org.bukkit.Location;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 伤兵服务接口
 * 管理战场上受伤的士兵，包括治疗、康复等功能
 */
public interface WoundedService {

    /**
     * 将士兵标记为伤兵
     * @param armyUnit 军队单位
     * @param woundedCount 受伤士兵数量
     * @return 伤兵记录
     */
    WoundedRecord createWounded(ArmyUnit armyUnit, int woundedCount);

    /**
     * 获取国家的所有伤兵记录
     * @param nationId 国家ID
     * @return 伤兵记录列表
     */
    List<WoundedRecord> getNationWounded(UUID nationId);

    /**
     * 获取玩家拥有的所有伤兵记录
     * @param playerId 玩家ID
     * @return 伤兵记录列表
     */
    List<WoundedRecord> getPlayerWounded(UUID playerId);

    /**
     * 获取特定伤兵记录
     * @param woundedId 伤兵记录ID
     * @return 伤兵记录
     */
    Optional<WoundedRecord> getWounded(UUID woundedId);

    /**
     * 开始治疗伤兵
     * @param woundedId 伤兵记录ID
     * @param location 治疗设施位置
     * @return 是否成功开始治疗
     */
    boolean startHealing(UUID woundedId, Location location);

    /**
     * 取消治疗
     * @param woundedId 伤兵记录ID
     * @return 是否成功取消
     */
    boolean cancelHealing(UUID woundedId);

    /**
     * 治疗完成，将伤兵转为可用士兵
     * @param woundedId 伤兵记录ID
     * @return 康复士兵数量
     */
    int completeHealing(UUID woundedId);

    /**
     * 伤兵死亡（治疗失败或超时）
     * @param woundedId 伤兵记录ID
     */
    void woundedDeath(UUID woundedId);

    /**
     * 获取国家伤兵总数
     * @param nationId 国家ID
     * @return 伤兵总数
     */
    int getNationWoundedCount(UUID nationId);

    /**
     * 获取正在治疗的伤兵数量
     * @param nationId 国家ID
     * @return 正在治疗的伤兵数量
     */
    int getNationHealingCount(UUID nationId);

    /**
     * 获取伤兵上限
     * @param nationId 国家ID
     * @return 伤兵上限
     */
    int getWoundedLimit(UUID nationId);

    /**
     * 获取配置
     */
    WoundedConfig getConfig();

    /**
     * 保存所有伤兵状态
     */
    void saveAll();

    /**
     * 关闭服务
     */
    void shutdown();

    /**
     * 伤兵记录
     */
    record WoundedRecord(
        UUID id,
        UUID nationId,
        UUID armyId,
        UUID playerId,
        int originalSoldiers,
        int currentWounded,
        WoundedSeverity severity,
        WoundedStatus status,
        Location injuryLocation,
        Location hospitalLocation,
        long injuredAt,
        long healingStartedAt,
        long expectedRecoveryAt,
        double healingProgress
    ) {
        /**
         * 是否正在治疗中
         */
        public boolean isHealing() {
            return status == WoundedStatus.HEALING;
        }

        /**
         * 是否可以开始治疗
         */
        public boolean canStartHealing() {
            return status == WoundedStatus.WAITING;
        }

        /**
         * 是否已康复
         */
        public boolean isRecovered() {
            return status == WoundedStatus.RECOVERED;
        }

        /**
         * 是否已死亡
         */
        public boolean isDead() {
            return status == WoundedStatus.DEAD;
        }

        /**
         * 获取康复进度百分比
         */
        public int healingProgressPercent() {
            return (int) Math.min(100, healingProgress * 100);
        }

        /**
         * 获取剩余治疗时间（秒）
         */
        public long remainingHealingTime() {
            if (!isHealing()) {
                return 0;
            }
            return Math.max(0, (expectedRecoveryAt - System.currentTimeMillis()) / 1000);
        }

        /**
         * 创建新的伤兵记录
         */
        public static WoundedRecord create(
            UUID nationId,
            UUID armyId,
            UUID playerId,
            int woundedCount,
            Location location,
            WoundedSeverity severity,
            WoundedConfig config
        ) {
            long now = System.currentTimeMillis();
            return new WoundedRecord(
                UUID.randomUUID(),
                nationId,
                armyId,
                playerId,
                woundedCount,
                woundedCount,
                severity,
                WoundedStatus.WAITING,
                location,
                null,
                now,
                0,
                0,
                0.0
            );
        }

        /**
         * 开始治疗
         */
        public WoundedRecord startHealing(Location hospital) {
            if (!canStartHealing()) {
                return this;
            }
            long now = System.currentTimeMillis();
            long duration = getHealingDurationMs();
            return new WoundedRecord(
                id,
                nationId,
                armyId,
                playerId,
                originalSoldiers,
                currentWounded,
                severity,
                WoundedStatus.HEALING,
                injuryLocation,
                hospital,
                injuredAt,
                now,
                now + duration,
                0.0
            );
        }

        /**
         * 更新治疗进度
         */
        public WoundedRecord updateProgress(double progress) {
            if (!isHealing()) {
                return this;
            }
            return new WoundedRecord(
                id,
                nationId,
                armyId,
                playerId,
                originalSoldiers,
                currentWounded,
                severity,
                status,
                injuryLocation,
                hospitalLocation,
                injuredAt,
                healingStartedAt,
                expectedRecoveryAt,
                Math.min(1.0, progress)
            );
        }

        /**
         * 获取治疗持续时间（毫秒）
         */
        private long getHealingDurationMs() {
            return switch (severity) {
                case LIGHT -> 60_000L;      // 1分钟
                case MODERATE -> 300_000L;   // 5分钟
                case SEVERE -> 900_000L;     // 15分钟
                case CRITICAL -> 1800_000L;  // 30分钟
            };
        }
    }

    /**
     * 伤兵严重程度
     */
    enum WoundedSeverity {
        /** 轻伤 - 快速康复 */
        LIGHT("light", 0.2, 1.0),
        /** 中伤 - 标准康复 */
        MODERATE("moderate", 0.4, 0.8),
        /** 重伤 - 慢速康复 */
        SEVERE("severe", 0.6, 0.5),
        /** 危急 - 最慢康复，有死亡风险 */
        CRITICAL("critical", 0.8, 0.2);

        private final String key;
        private final double deathChanceOnArrival;  // 到达时的死亡几率
        private final double recoveryRate;          // 康复率修正

        WoundedSeverity(String key, double deathChanceOnArrival, double recoveryRate) {
            this.key = key;
            this.deathChanceOnArrival = deathChanceOnArrival;
            this.recoveryRate = recoveryRate;
        }

        public String key() {
            return key;
        }

        public double deathChanceOnArrival() {
            return deathChanceOnArrival;
        }

        public double recoveryRate() {
            return recoveryRate;
        }

        public static WoundedSeverity fromDamagePercent(double damagePercent) {
            if (damagePercent < 0.25) {
                return LIGHT;
            } else if (damagePercent < 0.50) {
                return MODERATE;
            } else if (damagePercent < 0.75) {
                return SEVERE;
            } else {
                return CRITICAL;
            }
        }
    }

    /**
     * 伤兵状态
     */
    enum WoundedStatus {
        /** 等待治疗 */
        WAITING("waiting"),
        /** 正在治疗 */
        HEALING("healing"),
        /** 已康复 */
        RECOVERED("recovered"),
        /** 已死亡 */
        DEAD("dead");

        private final String key;

        WoundedStatus(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }
    }

    /**
     * 伤兵配置
     */
    record WoundedConfig(
        int baseWoundedLimit,           // 基础伤兵容量
        int woundedLimitPerLevel,       // 每级增加容量
        double healingSpeedBonus,       // 治疗速度加成
        double deathChanceOnArrival,    // 到达时基础死亡几率
        int healingCheckInterval,       // 治疗检查间隔（秒）
        boolean enableHospitalRequired, // 是否需要医院设施
        boolean enableDeathRisk        // 是否启用死亡风险
    ) {
        public static WoundedConfig defaults() {
            return new WoundedConfig(
                100,    // 基础100伤兵
                20,     // 每级+20
                1.0,    // 无加成
                0.05,   // 5%基础死亡几率
                10,     // 每10秒检查一次
                true,   // 需要医院
                true    // 启用死亡风险
            );
        }

        public static WoundedConfig fromConfig(org.bukkit.configuration.ConfigurationSection section) {
            if (section == null) {
                return defaults();
            }
            return new WoundedConfig(
                section.getInt("base-wounded-limit", 100),
                section.getInt("wounded-limit-per-level", 20),
                section.getDouble("healing-speed-bonus", 1.0),
                section.getDouble("death-chance-on-arrival", 0.05),
                section.getInt("healing-check-interval", 10),
                section.getBoolean("enable-hospital-required", true),
                section.getBoolean("enable-death-risk", true)
            );
        }
    }
}
