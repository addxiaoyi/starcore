package dev.starcore.starcore.module.army.doctrine;

import dev.starcore.starcore.module.army.doctrine.model.DoctrineType;
import dev.starcore.starcore.module.army.doctrine.model.NationDoctrine;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 军事学说服务接口
 * 提供学说的设置、查询和战斗加成计算等功能
 */
public interface DoctrineService {

    /**
     * 获取国家的当前学说
     */
    Optional<NationDoctrine> getNationDoctrine(UUID nationId);

    /**
     * 设置国家的学说
     * @return 是否设置成功
     */
    boolean setDoctrine(UUID nationId, DoctrineType doctrine, String changedBy);

    /**
     * 清除国家的学说（重置为无）
     */
    boolean clearDoctrine(UUID nationId, String clearedBy);

    /**
     * 获取国家的学说类型（快捷方法）
     */
    DoctrineType getDoctrineType(UUID nationId);

    /**
     * 计算学说对攻击力的加成
     */
    int calculateAttackBonus(UUID nationId, int baseAttack);

    /**
     * 计算学说对防御力的加成
     */
    int calculateDefenseBonus(UUID nationId, int baseDefense);

    /**
     * 计算学说对士兵数量的加成（效率影响）
     */
    int calculateEffectiveSoldiers(UUID nationId, int baseSoldiers);

    /**
     * 获取学说成本乘数
     */
    double getCostMultiplier(UUID nationId);

    /**
     * 获取学说士气消耗乘数
     */
    double getMoraleConsumptionMultiplier(UUID nationId);

    /**
     * 获取学说伏击加成
     */
    double getAmbushBonus(UUID nationId);

    /**
     * 获取学说机动性加成
     */
    double getMobilityBonus(UUID nationId);

    /**
     * 检查国家是否可以切换学说
     */
    boolean canSwitchDoctrine(UUID nationId);

    /**
     * 获取学说切换冷却剩余时间（毫秒）
     */
    long getDoctrineSwitchCooldownRemaining(UUID nationId);

    /**
     * 注册学说变更监听器
     */
    void onDoctrineChanged(Consumer<DoctrineChangedEvent> listener);

    /**
     * 加载所有国家学说数据（从数据库/配置文件）
     */
    void loadAllDoctrines();

    /**
     * 保存所有国家学说数据（到数据库/配置文件）
     */
    void saveAllDoctrines();

    /**
     * 获取学说切换冷却时间（毫秒）
     */
    long getSwitchCooldownMs();

    /**
     * 获取学说切换费用
     */
    double getSwitchCost();

    /**
     * 获取所有采用特定学说的国家数量
     */
    int getNationCountByDoctrine(DoctrineType doctrine);
}
