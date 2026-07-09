package dev.starcore.starcore.module.siege;

import dev.starcore.starcore.module.siege.model.SiegeResult;
import dev.starcore.starcore.module.siege.model.SiegeWeapon;
import dev.starcore.starcore.module.siege.model.SiegeWeaponType;
import dev.starcore.starcore.module.siege.model.SiegeWeaponState;
import org.bukkit.Location;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 攻城器械服务接口
 */
public interface SiegeService {

    /**
     * 创建攻城器械
     * @param nationId 国家ID
     * @param type 器械类型
     * @param location 放置位置
     * @return 创建的攻城器械
     */
    SiegeWeapon createSiegeWeapon(UUID nationId, SiegeWeaponType type, Location location);

    /**
     * 部署攻城器械
     * @param siegeId 器械ID
     */
    void deploySiegeWeapon(UUID siegeId);

    /**
     * 收起攻城器械
     * @param siegeId 器械ID
     */
    void storeSiegeWeapon(UUID siegeId);

    /**
     * 移动攻城器械
     * @param siegeId 器械ID
     * @param destination 目标位置
     */
    void moveSiegeWeapon(UUID siegeId, Location destination);

    /**
     * 射击
     * @param siegeId 器械ID
     * @param target 目标位置
     * @return 造成的伤害
     */
    int fireSiegeWeapon(UUID siegeId, Location target);

    /**
     * 装填弹药
     * @param siegeId 器械ID
     * @param amount 弹药数量
     */
    void reloadSiegeWeapon(UUID siegeId, int amount);

    /**
     * 维修器械
     * @param siegeId 器械ID
     * @param amount 维修量
     */
    void repairSiegeWeapon(UUID siegeId, int amount);

    /**
     * 销毁攻城器械
     * @param siegeId 器械ID
     */
    void destroySiegeWeapon(UUID siegeId);

    /**
     * 获取攻城器械
     * @param siegeId 器械ID
     * @return 攻城器械（如果存在）
     */
    Optional<SiegeWeapon> getSiegeWeapon(UUID siegeId);

    /**
     * 获取国家所有攻城器械
     * @param nationId 国家ID
     * @return 攻城器械列表
     */
    List<SiegeWeapon> getNationSiegeWeapons(UUID nationId);

    /**
     * 获取某位置附近的攻城器械
     * @param location 位置
     * @param radius 半径
     * @return 攻城器械列表
     */
    List<SiegeWeapon> getSiegeWeaponsNear(Location location, double radius);

    /**
     * 开始攻城战
     * @param siegeId 攻城器械ID
     * @param targetLocation 目标位置
     * @return 攻城结果
     */
    SiegeResult startSiege(UUID siegeId, Location targetLocation);

    /**
     * 获取配置
     */
    SiegeConfig getConfig();

    /**
     * 保存所有数据
     */
    void saveAll();

    /**
     * 攻城器械配置
     */
    record SiegeConfig(
        int maxSiegeWeaponsPerNation,
        int siegeStartDurationSeconds,
        int siegeBreachDamage,
        double siegeWeaponDecayRate,
        int defaultAmmunitionPerReload
    ) {
        public static SiegeConfig defaults() {
            return new SiegeConfig(5, 300, 5000, 0.1, 50);
        }

        public static SiegeConfig fromConfig(org.bukkit.configuration.ConfigurationSection section) {
            if (section == null) {
                return defaults();
            }
            return new SiegeConfig(
                section.getInt("max-siege-weapons-per-nation", 5),
                section.getInt("siege-start-duration-seconds", 300),
                section.getInt("siege-breach-damage", 5000),
                section.getDouble("siege-weapon-decay-rate", 0.1),
                section.getInt("default-ammunition-per-reload", 50)
            );
        }
    }
}