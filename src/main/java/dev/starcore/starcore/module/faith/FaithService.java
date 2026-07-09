package dev.starcore.starcore.module.faith;

import dev.starcore.starcore.module.faith.model.FaithConfig;
import dev.starcore.starcore.module.faith.model.FaithData;
import dev.starcore.starcore.module.faith.model.FaithStats;
import dev.starcore.starcore.module.nation.model.NationId;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 领土信仰服务接口
 * 管理每个国家的信仰值系统
 */
public interface FaithService {

    /**
     * 获取国家的信仰值数据
     * @param nationId 国家ID
     * @return 信仰值数据，如果不存在返回空
     */
    Optional<FaithData> getFaithData(NationId nationId);

    /**
     * 获取国家的当前信仰值
     * @param nationId 国家ID
     * @return 信仰值，范围 0-100
     */
    int getFaith(NationId nationId);

    /**
     * 设置国家的信仰值
     * @param nationId 国家ID
     * @param faith 信仰值 (0-100)
     * @return 是否设置成功
     */
    boolean setFaith(NationId nationId, int faith);

    /**
     * 增加国家的信仰值
     * @param nationId 国家ID
     * @param amount 增加量（可为负数）
     * @return 新的信仰值
     */
    int addFaith(NationId nationId, int amount);

    /**
     * 获取信仰等级
     * @param nationId 国家ID
     * @return 信仰等级 (1-5)
     */
    int getFaithLevel(NationId nationId);

    /**
     * 获取信仰等级名称
     * @param level 等级 (1-5)
     * @return 等级名称
     */
    String getFaithLevelName(int level);

    /**
     * 获取信仰加成效果
     * @param nationId 国家ID
     * @return 加成效果映射 (效果类型 -> 倍率/数值)
     */
    Map<String, Double> getFaithBonuses(NationId nationId);

    /**
     * 记录一次祈祷行为
     * @param playerId 玩家ID
     * @param nationId 国家ID
     * @param locationX 位置X
     * @param locationY 位置Y
     * @param locationZ 位置Z
     * @param world 世界名
     */
    void recordPrayer(UUID playerId, NationId nationId, int locationX, int locationY, int locationZ, String world);

    /**
     * 检查并触发信仰事件
     * @param nationId 国家ID
     */
    void checkFaithEvents(NationId nationId);

    /**
     * 获取信仰统计信息
     * @param nationId 国家ID
     * @return 统计信息
     */
    FaithStats getStats(NationId nationId);

    /**
     * 消耗信仰值进行祈福
     * @param nationId 国家ID
     * @param blessingType 祈福类型
     * @return 是否成功
     */
    boolean useFaithBlessing(NationId nationId, String blessingType);

    /**
     * 获取信仰配置
     * @return 信仰配置
     */
    FaithConfig getConfig();

    /**
     * 保存所有信仰数据
     */
    void saveAll();

    /**
     * 初始化新国家的信仰数据
     * @param nationId 国家ID
     * @param founderId 创始人ID
     */
    void initializeFaith(NationId nationId, UUID founderId);

    /**
     * 移除国家的信仰数据
     * @param nationId 国家ID
     */
    void removeFaith(NationId nationId);

    /**
     * 获取信仰值上限
     * @return 信仰值上限
     */
    int getMaxFaith();

    /**
     * 获取信仰阈值
     * @param level 等级 (1-5)
     * @return 该等级的最低信仰阈值
     */
    int getFaithThreshold(int level);

    /**
     * 初始化信仰服务（加载数据等）
     */
    void initialize();
}