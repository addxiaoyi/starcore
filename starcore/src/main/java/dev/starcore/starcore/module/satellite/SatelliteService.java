package dev.starcore.starcore.module.satellite;

import dev.starcore.starcore.module.nation.model.NationId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * 卫星国服务接口
 * 提供卫星国（附庸国/保护国）关系的完整管理功能
 */
public interface SatelliteService {

    // ==================== 关系查询 ====================

    /**
     * 获取宗主国ID
     * @param satelliteId 卫星国ID
     * @return 宗主国ID（如果没有则返回null）
     */
    Optional<NationId> suzerainOf(NationId satelliteId);

    /**
     * 获取保护国ID
     * @param suzerainId 宗主国ID
     * @return 保护国ID（如果没有则返回null）
     */
    Optional<NationId> protectorOf(NationId suzerainId);

    /**
     * 获取宗主国下的所有卫星国
     * @param suzerainId 宗主国ID
     * @return 卫星国ID集合
     */
    Collection<NationId> satellitesOf(NationId suzerainId);

    /**
     * 获取两个国家之间的卫星关系类型
     * @param nation1 国家1
     * @param nation2 国家2
     * @return 关系类型
     */
    SatelliteRelation getRelation(NationId nation1, NationId nation2);

    /**
     * 检查是否是宗主国
     * @param nationId 国家ID
     * @return 是否是宗主国
     */
    boolean isSuzerain(NationId nationId);

    /**
     * 检查是否是卫星国
     * @param nationId 国家ID
     * @return 是否是卫星国
     */
    boolean isSatellite(NationId nationId);

    /**
     * 检查是否存在卫星关系
     * @param suzerainId 宗主国ID
     * @param satelliteId 卫星国ID
     * @return 是否存在关系
     */
    boolean hasRelation(NationId suzerainId, NationId satelliteId);

    // ==================== 关系操作 ====================

    /**
     * 建立卫星关系
     * @param suzerainId 宗主国ID
     * @param satelliteId 卫星国ID
     * @param relation 关系类型
     * @return 操作结果
     */
    SatelliteResult establishRelation(NationId suzerainId, NationId satelliteId, SatelliteRelation relation);

    /**
     * 解除卫星关系
     * @param suzerainId 宗主国ID
     * @param satelliteId 卫星国ID
     * @param initiatorId 发起方玩家ID
     * @return 操作结果
     */
    SatelliteResult dissolveRelation(NationId suzerainId, NationId satelliteId, UUID initiatorId);

    /**
     * 卫星国独立
     * @param satelliteId 卫星国ID
     * @param initiatorId 发起方玩家ID
     * @return 操作结果
     */
    SatelliteResult declareIndependence(NationId satelliteId, UUID initiatorId);

    /**
     * 宗主国释放卫星国
     * @param suzerainId 宗主国ID
     * @param satelliteId 卫星国ID
     * @param initiatorId 发起方玩家ID
     * @return 操作结果
     */
    SatelliteResult releaseSatellite(NationId suzerainId, NationId satelliteId, UUID initiatorId);

    // ==================== 保护关系 ====================

    /**
     * 建立保护关系
     * @param protectorId 保护国ID
     * @param protectedId 被保护国ID
     * @return 操作结果
     */
    SatelliteResult establishProtection(NationId protectorId, NationId protectedId);

    /**
     * 解除保护关系
     * @param protectorId 保护国ID
     * @param protectedId 被保护国ID
     * @return 操作结果
     */
    SatelliteResult endProtection(NationId protectorId, NationId protectedId);

    // ==================== 义务与收益 ====================

    /**
     * 获取卫星国应缴纳的贡金
     * @param satelliteId 卫星国ID
     * @return 贡金金额
     */
    BigDecimal getTributeAmount(NationId satelliteId);

    /**
     * 设置卫星国贡金
     * @param satelliteId 卫星国ID
     * @param amount 贡金金额
     * @return 是否成功
     */
    boolean setTributeAmount(NationId satelliteId, BigDecimal amount);

    /**
     * 获取卫星国向宗主国缴纳的税率
     * @param satelliteId 卫星国ID
     * @return 税率 (0.0 - 1.0)
     */
    double getTributeRate(NationId satelliteId);

    /**
     * 设置卫星国向宗主国缴纳的税率
     * @param satelliteId 卫星国ID
     * @param rate 税率 (0.0 - 1.0)
     * @return 是否成功
     */
    boolean setTributeRate(NationId satelliteId, double rate);

    /**
     * 收取卫星国贡金（由定时任务或手动触发）
     * @param suzerainId 宗主国ID
     * @return 收取的贡金总额
     */
    BigDecimal collectTributes(NationId suzerainId);

    // ==================== 防御保护 ====================

    /**
     * 检查宗主国是否保护卫星国免受攻击
     * @param satelliteId 卫星国ID
     * @param attackerId 攻击方ID
     * @return 是否受到保护
     */
    boolean isProtectedFrom(NationId satelliteId, NationId attackerId);

    /**
     * 获取卫星国的防御状态
     * @param satelliteId 卫星国ID
     * @return 防御详情
     */
    SatelliteDefenseStatus getDefenseStatus(NationId satelliteId);

    // ==================== 特殊状态 ====================

    /**
     * 检查卫星国是否可以宣战
     * @param satelliteId 卫星国ID
     * @return 是否可以宣战
     */
    boolean canDeclareWar(NationId satelliteId);

    /**
     * 检查卫星国是否可以结盟
     * @param satelliteId 卫星国ID
     * @param targetId 目标国ID
     * @return 是否可以结盟
     */
    boolean canFormAlliance(NationId satelliteId, NationId targetId);

    // ==================== 配置 ====================

    /**
     * 获取最大宗主国数量
     * @return 最大宗主国数量
     */
    int getMaxSuzerains();

    /**
     * 获取最大卫星国数量
     * @param suzerainId 宗主国ID
     * @return 最大卫星国数量
     */
    int getMaxSatellites(NationId suzerainId);

    // ==================== 统计 ====================

    /**
     * 获取卫星国总数
     * @return 卫星国总数
     */
    int getTotalSatellites();

    /**
     * 获取宗主国总数
     * @return 宗主国总数
     */
    int getTotalSuzerains();

    String summary();

    // ==================== 结果记录 ====================

    /**
     * 卫星国操作结果
     */
    record SatelliteResult(boolean success, String message, SatelliteRelation relation) {
        public static SatelliteResult success(String message) {
            return new SatelliteResult(true, message, null);
        }

        public static SatelliteResult success(String message, SatelliteRelation relation) {
            return new SatelliteResult(true, message, relation);
        }

        public static SatelliteResult failure(String message) {
            return new SatelliteResult(false, message, null);
        }
    }

    /**
     * 防御状态
     */
    record SatelliteDefenseStatus(
        NationId satelliteId,
        NationId suzerainId,
        boolean isProtected,
        boolean automaticDefense,
        BigDecimal defenseBonus
    ) {}

    /**
     * 卫星国详情快照
     */
    record SatelliteSnapshot(
        NationId satelliteId,
        NationId suzerainId,
        SatelliteRelation relation,
        Instant establishedAt,
        BigDecimal tributeAmount,
        double tributeRate
    ) {}
}
