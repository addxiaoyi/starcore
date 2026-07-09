package dev.starcore.starcore.module.prosperity;

import dev.starcore.starcore.module.nation.model.NationId;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 繁荣度服务接口
 * 管理国家的繁荣度数据、计算和修改
 */
public interface ProsperityService {

    /**
     * 获取国家的繁荣度信息
     * @param nationId 国家ID
     * @return 繁荣度信息，如果不存在返回默认值
     */
    NationProsperity getProsperity(NationId nationId);

    /**
     * 获取国家当前繁荣度值 (0-100)
     * @param nationId 国家ID
     * @return 繁荣度值
     */
    double getProsperityValue(NationId nationId);

    /**
     * 获取国家繁荣度等级 (1-10)
     * @param nationId 国家ID
     * @return 繁荣度等级
     */
    int getProsperityLevel(NationId nationId);

    /**
     * 修改繁荣度
     * @param nationId 国家ID
     * @param amount 变化量（可为负数）
     * @param reason 原因
     * @return 新的繁荣度值
     */
    double modifyProsperity(NationId nationId, double amount, String reason);

    /**
     * 设置繁荣度（管理员用）
     * @param nationId 国家ID
     * @param value 繁荣度值
     */
    void setProsperity(NationId nationId, double value);

    /**
     * 增加特定区块的繁荣度贡献
     * @param nationId 国家ID
     * @param chunkWorld 区块所在世界
     * @param chunkX 区块X坐标
     * @param chunkZ 区块Z坐标
     * @param amount 增量
     */
    void addChunkContribution(UUID nationId, String chunkWorld, int chunkX, int chunkZ, double amount);

    /**
     * 获取区块贡献值
     * @param chunkWorld 区块所在世界
     * @param chunkX 区块X坐标
     * @param chunkZ 区块Z坐标
     * @return 贡献值
     */
    double getChunkContribution(String chunkWorld, int chunkX, int chunkZ);

    /**
     * 记录繁荣度事件
     * @param nationId 国家ID
     * @param eventType 事件类型
     * @param description 描述
     * @param amount 影响值
     */
    void recordEvent(NationId nationId, String eventType, String description, double amount);

    /**
     * 获取国家繁荣度历史事件
     * @param nationId 国家ID
     * @param limit 限制数量
     * @return 事件列表
     */
    java.util.List<ProsperityEvent> getRecentEvents(NationId nationId, int limit);

    /**
     * 获取繁荣度加成因子
     * @param nationId 国家ID
     * @return 加成因子 (例如 1.2 表示 +20%)
     */
    double getBonusMultiplier(NationId nationId);

    /**
     * 获取税收加成（基于繁荣度）
     * @param nationId 国家ID
     * @return 税收加成倍率
     */
    double getTaxBonus(NationId nationId);

    /**
     * 获取资源产出加成（基于繁荣度）
     * @param nationId 国家ID
     * @return 资源产出加成倍率
     */
    double getResourceBonus(NationId nationId);

    /**
     * 处理每日繁荣度衰减
     * @param nationId 国家ID
     */
    void processDecay(NationId nationId);

    /**
     * 处理所有国家的每日衰减
     */
    void processAllDecay();

    /**
     * 刷新国家繁荣度数据
     * @param nationId 国家ID
     */
    void refreshProsperity(NationId nationId);

    /**
     * 刷新所有国家繁荣度
     */
    void refreshAllProsperity();

    /**
     * 记录国家活跃度
     * @param nationId 国家ID
     * @param playerId 玩家ID
     * @param activityType 活动类型
     */
    void recordActivity(NationId nationId, UUID playerId, String activityType);

    /**
     * 获取国家活跃度得分
     * @param nationId 国家ID
     * @return 活跃度得分
     */
    int getActivityScore(NationId nationId);

    /**
     * 保存所有繁荣度数据到数据库
     */
    void saveAll();

    /**
     * 获取所有国家的繁荣度排名
     * @return 按繁荣度排序的国家列表
     */
    java.util.List<java.util.Map.Entry<NationId, Double>> getRanking();

    /**
     * 繁荣度事件记录
     */
    record ProsperityEvent(
        UUID id,
        NationId nationId,
        String eventType,
        String description,
        double amount,
        Instant timestamp
    ) {}

    /**
     * 国家繁荣度数据
     */
    record NationProsperity(
        NationId nationId,
        double prosperity,
        int level,
        double activityScore,
        Instant lastActivity,
        Instant lastUpdate,
        Map<String, Double> chunkContributions
    ) {
        public static NationProsperity defaultFor(NationId nationId) {
            return new NationProsperity(
                nationId,
                50.0,  // 默认50%
                3,      // 默认等级3
                0.0,
                Instant.now(),
                Instant.now(),
                Map.of()
            );
        }
    }
}
