package dev.starcore.starcore.module.army.siege;

import dev.starcore.starcore.module.army.siege.model.*;

/**
 * 攻城器械服务接口
 * 提供攻城器械的创建、管理、部署、攻击等功能
 */
public interface SiegeService {

    /**
     * 攻城器械配置
     */
    record SiegeConfig(
        int maxSiegePerNation,
        int minCrewSize,
        int maxCrewSize,
        long deploymentCooldownSeconds
    ) {
        public static SiegeConfig defaults() {
            return new SiegeConfig(5, 5, 50, 300);
        }

        public static SiegeConfig fromConfig(org.bukkit.configuration.ConfigurationSection section) {
            if (section == null) {
                return defaults();
            }
            return new SiegeConfig(
                section.getInt("max-siege-per-nation", 5),
                section.getInt("min-crew-size", 5),
                section.getInt("max-crew-size", 50),
                section.getLong("deployment-cooldown-seconds", 300)
            );
        }
    }

    /**
     * 获取配置
     */
    SiegeConfig getConfig();

    /**
     * 获取攻城器械类型配置
     */
    SiegeTypeConfig getTypeConfig(SiegeType type);

    /**
     * 创建攻城器械
     * @param nationId 国家ID
     * @param type 器械类型
     * @param crewSize 船员数量
     * @param location 部署位置
     * @return 创建的攻城器械
     */
    SiegeUnit createSiege(java.util.UUID nationId, SiegeType type, int crewSize, org.bukkit.Location location);

    /**
     * 部署攻城器械到目标位置
     * @param siegeId 攻城器械ID
     * @param targetLocation 目标位置
     */
    void deploySiege(java.util.UUID siegeId, org.bukkit.Location targetLocation);

    /**
     * 启动攻城
     * @param siegeId 攻城器械ID
     * @param targetWallId 目标城墙ID
     */
    SiegeResult startSiege(java.util.UUID siegeId, java.util.UUID targetWallId);

    /**
     * 攻城器械开火
     * @param siegeId 攻城器械ID
     * @param targetLocation 目标位置
     * @return 造成的伤害
     */
    double fireSiege(java.util.UUID siegeId, org.bukkit.Location targetLocation);

    /**
     * 修复攻城器械
     * @param siegeId 攻城器械ID
     * @param repairAmount 修复量
     */
    void repairSiege(java.util.UUID siegeId, double repairAmount);

    /**
     * 解散攻城器械
     * @param siegeId 攻城器械ID
     */
    void disbandSiege(java.util.UUID siegeId);

    /**
     * 获取攻城器械
     * @param siegeId 攻城器械ID
     * @return 攻城器械（如果存在）
     */
    java.util.Optional<SiegeUnit> getSiege(java.util.UUID siegeId);

    /**
     * 获取国家的所有攻城器械
     * @param nationId 国家ID
     * @return 攻城器械列表
     */
    java.util.List<SiegeUnit> getNationSieges(java.util.UUID nationId);

    /**
     * 获取某位置附近的攻城器械
     * @param location 位置
     * @param radius 半径
     * @return 攻城器械列表
     */
    java.util.List<SiegeUnit> getSiegesNear(org.bukkit.Location location, double radius);

    /**
     * 创建/注册城墙
     * @param location 位置
     * @param nationId 所属国家ID
     * @param type 城墙类型
     * @return 城墙数据
     */
    WallData createWall(org.bukkit.Location location, java.util.UUID nationId, WallType type);

    /**
     * 获取城墙
     * @param wallId 城墙ID
     * @return 城墙数据
     */
    java.util.Optional<WallData> getWall(java.util.UUID wallId);

    /**
     * 获取某位置最近的城墙
     * @param location 位置
     * @param maxDistance 最大距离
     * @return 城墙数据
     */
    java.util.Optional<WallData> getNearestWall(org.bukkit.Location location, double maxDistance);

    /**
     * 修复城墙
     * @param wallId 城墙ID
     * @param repairAmount 修复量
     */
    void repairWall(java.util.UUID wallId, double repairAmount);

    /**
     * 重新装载攻城器械弹药
     * @param siegeId 攻城器械ID
     * @param amount 弹药数量
     */
    void reloadSiege(java.util.UUID siegeId, int amount);

    /**
     * 移动攻城器械到新位置
     * @param siegeId 攻城器械ID
     * @param targetLocation 目标位置
     */
    void moveSiege(java.util.UUID siegeId, org.bukkit.Location targetLocation);

    /**
     * 让攻城器械撤退
     * @param siegeId 攻城器械ID
     */
    void retreatSiege(java.util.UUID siegeId);

    /**
     * 检查攻城器械部署冷却
     * @param nationId 国家ID
     * @return 是否可以部署
     */
    boolean canDeploy(java.util.UUID nationId);

    /**
     * 获取部署冷却剩余时间（秒）
     * @param nationId 国家ID
     * @return 剩余时间，0表示可以部署
     */
    long getDeploymentCooldownRemaining(java.util.UUID nationId);

    /**
     * 获取攻城器械类型信息
     */
    record SiegeTypeConfig(
        int maxPerType,
        int effectiveRange,
        double baseDamage,
        double siegeDamageMultiplier,
        int constructionCost,
        int maintenanceCostPerHour
    ) {
        public static SiegeTypeConfig defaults() {
            return new SiegeTypeConfig(3, 50, 30, 2.0, 5000, 100);
        }
    }

    /**
     * 保存所有数据
     */
    void saveAll();

    /**
     * 关闭服务
     */
    void shutdown();
}
