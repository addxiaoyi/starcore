package dev.starcore.starcore.module.army.raid;

import dev.starcore.starcore.module.army.raid.model.Raid;
import dev.starcore.starcore.module.army.raid.model.RaidAlert;
import dev.starcore.starcore.module.army.raid.model.RaidParticipant;
import dev.starcore.starcore.module.army.raid.model.RaidPhase;
import dev.starcore.starcore.module.army.raid.model.RaidStatus;
import dev.starcore.starcore.module.nation.model.NationId;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 夜间突袭服务接口
 * 提供突袭的创建、管理、执行等核心功能
 */
public interface NightRaidService {

    /**
     * 创建新的突袭
     *
     * @param attackerNationId 攻击方国家ID
     * @param targetNationId 目标国家ID
     * @param raidLocation 突袭地点
     * @param attackerId 发起突袭的玩家ID
     * @return 创建的突袭对象
     */
    Raid createRaid(NationId attackerNationId, NationId targetNationId, Location raidLocation, UUID attackerId);

    /**
     * 加入突袭
     *
     * @param raidId 突袭ID
     * @param player 玩家
     * @param nationId 玩家所属国家ID
     * @param isAttacker 是否为攻击方
     */
    void joinRaid(UUID raidId, Player player, NationId nationId, boolean isAttacker);

    /**
     * 离开突袭
     *
     * @param raidId 突袭ID
     * @param player 玩家
     */
    void leaveRaid(UUID raidId, Player player);

    /**
     * 开始突袭战斗
     *
     * @param raidId 突袭ID
     */
    void startRaid(UUID raidId);

    /**
     * 结束突袭
     *
     * @param raidId 突袭ID
     * @param reason 结束原因
     */
    void endRaid(UUID raidId, String reason);

    /**
     * 获取突袭
     *
     * @param raidId 突袭ID
     * @return 突袭对象
     */
    Optional<Raid> getRaid(UUID raidId);

    /**
     * 获取国家参与的所有突袭
     *
     * @param nationId 国家ID
     * @return 突袭列表
     */
    List<Raid> getNationRaids(NationId nationId);

    /**
     * 获取位置附近的突袭
     *
     * @param location 位置
     * @param radius 半径
     * @return 突袭列表
     */
    List<Raid> getRaidsNear(Location location, double radius);

    /**
     * 获取所有活跃突袭
     *
     * @return 活跃突袭列表
     */
    Collection<Raid> getActiveRaids();

    /**
     * 检查是否可以发起突袭
     *
     * @param attackerNationId 攻击方国家ID
     * @param targetNationId 目标国家ID
     * @return 检查结果消息，空表示可以发起
     */
    String canInitiateRaid(NationId attackerNationId, NationId targetNationId);

    /**
     * 获取突袭配置
     *
     * @return 突袭配置
     */
    NightRaidConfig getConfig();

    /**
     * 保存所有突袭状态
     */
    void saveAll();

    /**
     * 清理并关闭服务
     */
    void shutdown();

    /**
     * 获取玩家的突袭参与者信息
     *
     * @param playerId 玩家ID
     * @return 参与者信息
     */
    Optional<RaidParticipant> getParticipant(UUID playerId);

    /**
     * 获取当前玩家的活跃突袭
     *
     * @param playerId 玩家ID
     * @return 突袭列表
     */
    List<Raid> getPlayerActiveRaids(UUID playerId);

    /**
     * 获取最新的突袭警报
     * @param nationId 国家ID
     * @return 警报（如果有）
     */
    Optional<RaidAlert> getLatestAlert(NationId nationId);

    /**
     * 确认警报
     * @param alertId 警报ID
     */
    void acknowledgeAlert(UUID alertId);

    /**
     * 检查是否在突袭时间窗口内
     */
    boolean isWithinRaidWindow();

    /**
     * 检查突袭系统是否启用
     */
    boolean isEnabled();

    /**
     * 检查国家是否可以发起突袭
     * @param nationId 国家ID
     * @return 是否可以
     */
    boolean canRaid(NationId nationId);

    /**
     * 计算突袭成本
     * @param participantCount 参与者数量
     * @return 成本
     */
    double calculateRaidCost(int participantCount);

    /**
     * 夜间突袭配置
     */
    record NightRaidConfig(
        int maxRaidsPerNation,
        int minRaidParticipants,
        int maxRaidParticipants,
        int raidDurationMinutes,
        int preparationTimeSeconds,
        int cooldownHours,
        double victoryPointThreshold,
        int pointsPerKill,
        int pointsPerBuildingDestroyed,
        double raidCostMultiplier,
        boolean allowNightOnly,
        int nightStartHour,
        int nightEndHour,
        boolean notifyTarget
    ) {
        public static NightRaidConfig defaults() {
            return new NightRaidConfig(
                3,           // maxRaidsPerNation
                3,           // minRaidParticipants
                20,          // maxRaidParticipants
                30,          // raidDurationMinutes
                60,          // preparationTimeSeconds
                24,          // cooldownHours
                100.0,       // victoryPointThreshold
                10,          // pointsPerKill
                25,          // pointsPerBuildingDestroyed
                1.0,         // raidCostMultiplier
                true,        // allowNightOnly
                20,          // nightStartHour (8 PM)
                6,           // nightEndHour (6 AM)
                true         // notifyTarget
            );
        }

        public static NightRaidConfig fromConfig(org.bukkit.configuration.ConfigurationSection section) {
            if (section == null) {
                return defaults();
            }
            return new NightRaidConfig(
                section.getInt("max-raids-per-nation", 3),
                section.getInt("min-raid-participants", 3),
                section.getInt("max-raid-participants", 20),
                section.getInt("raid-duration-minutes", 30),
                section.getInt("preparation-time-seconds", 60),
                section.getInt("cooldown-hours", 24),
                section.getDouble("victory-point-threshold", 100.0),
                section.getInt("points-per-kill", 10),
                section.getInt("points-per-building-destroyed", 25),
                section.getDouble("raid-cost-multiplier", 1.0),
                section.getBoolean("allow-night-only", true),
                section.getInt("night-start-hour", 20),
                section.getInt("night-end-hour", 6),
                section.getBoolean("notify-target", true)
            );
        }
    }
}