package dev.starcore.starcore.module.resource;

import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.resource.model.TradeAgreement;
import dev.starcore.starcore.module.resource.model.TradeRoute;
import dev.starcore.starcore.module.resource.model.ResourceEmbargo;
import dev.starcore.starcore.module.resource.model.ResourceQuota;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 资源贸易服务
 * 管理国家间的资源贸易、禁运、配额等
 */
public interface ResourceTradeService {
    /**
     * 创建贸易路线
     *
     * @param originNationId 起点国家
     * @param destinationNationId 终点国家
     * @param routeName 路线名称
     * @return 贸易路线
     */
    TradeRoute createTradeRoute(NationId originNationId, NationId destinationNationId, String routeName);

    /**
     * 获取贸易路线
     *
     * @param routeId 路线ID
     * @return 贸易路线
     */
    Optional<TradeRoute> getTradeRoute(UUID routeId);

    /**
     * 获取国家的所有贸易路线
     *
     * @param nationId 国家ID
     * @return 贸易路线列表
     */
    Collection<TradeRoute> getTradeRoutes(NationId nationId);

    /**
     * 获取两国之间的贸易路线
     *
     * @param nation1 国家1
     * @param nation2 国家2
     * @return 贸易路线（如果存在）
     */
    Optional<TradeRoute> getTradeRouteBetween(NationId nation1, NationId nation2);

    /**
     * 创建贸易协定
     *
     * @param exporterNationId 出口国
     * @param importerNationId 进口国
     * @param resourceId 资源ID
     * @param amount 数量
     * @param pricePerUnit 单价
     * @param duration 持续时间（秒）
     * @return 贸易协定
     */
    TradeAgreement createTradeAgreement(NationId exporterNationId, NationId importerNationId,
                                        String resourceId, long amount, double pricePerUnit,
                                        long duration);

    /**
     * 获取贸易协定
     *
     * @param agreementId 协定ID
     * @return 贸易协定
     */
    Optional<TradeAgreement> getTradeAgreement(UUID agreementId);

    /**
     * 获取国家的所有贸易协定
     *
     * @param nationId 国家ID
     * @return 贸易协定列表
     */
    Collection<TradeAgreement> getTradeAgreements(NationId nationId);

    /**
     * 执行贸易协定
     * 转移资源和货币
     *
     * @param agreementId 协定ID
     * @return 是否成功执行
     */
    boolean executeTradeAgreement(UUID agreementId);

    /**
     * 取消贸易协定
     *
     * @param agreementId 协定ID
     * @return 是否成功取消
     */
    boolean cancelTradeAgreement(UUID agreementId);

    /**
     * 创建资源禁运
     *
     * @param initiatorNationId 发起国
     * @param targetNationIds 目标国家集合
     * @param resourceId 资源ID
     * @param duration 持续时间（秒，null表示无限期）
     * @param reason 原因
     * @return 禁运记录
     */
    ResourceEmbargo createEmbargo(NationId initiatorNationId, Set<NationId> targetNationIds,
                                  String resourceId, Long duration, String reason);

    /**
     * 获取资源禁运
     *
     * @param embargoId 禁运ID
     * @return 禁运记录
     */
    Optional<ResourceEmbargo> getEmbargo(UUID embargoId);

    /**
     * 获取国家发起的所有禁运
     *
     * @param nationId 国家ID
     * @return 禁运列表
     */
    Collection<ResourceEmbargo> getEmbargoesBy(NationId nationId);

    /**
     * 获取针对国家的所有禁运
     *
     * @param nationId 国家ID
     * @return 禁运列表
     */
    Collection<ResourceEmbargo> getEmbargoesAgainst(NationId nationId);

    /**
     * 检查两国间的资源是否被禁运
     *
     * @param fromNation 出口国
     * @param toNation 进口国
     * @param resourceId 资源ID
     * @return 是否被禁运
     */
    boolean isEmbargoed(NationId fromNation, NationId toNation, String resourceId);

    /**
     * 解除禁运
     *
     * @param embargoId 禁运ID
     * @return 是否成功解除
     */
    boolean liftEmbargo(UUID embargoId);

    /**
     * 创建资源配额
     *
     * @param nationId 国家ID
     * @param resourceId 资源ID
     * @param maxAmount 最大配额
     * @param duration 有效期（秒）
     * @param type 配额类型
     * @return 配额记录
     */
    ResourceQuota createQuota(NationId nationId, String resourceId, long maxAmount,
                              long duration, ResourceQuota.QuotaType type);

    /**
     * 获取资源配额
     *
     * @param quotaId 配额ID
     * @return 配额记录
     */
    Optional<ResourceQuota> getQuota(UUID quotaId);

    /**
     * 获取国家的所有配额
     *
     * @param nationId 国家ID
     * @return 配额列表
     */
    Collection<ResourceQuota> getQuotas(NationId nationId);

    /**
     * 获取国家特定资源的配额
     *
     * @param nationId 国家ID
     * @param resourceId 资源ID
     * @param type 配额类型
     * @return 配额记录
     */
    Optional<ResourceQuota> getQuota(NationId nationId, String resourceId, ResourceQuota.QuotaType type);

    /**
     * 使用配额
     *
     * @param quotaId 配额ID
     * @param amount 使用数量
     * @return 是否成功使用
     */
    boolean useQuota(UUID quotaId, long amount);

    /**
     * 重置过期的配额
     */
    void resetExpiredQuotas();

    // ==================== 税收集成方法 ====================

    /**
     * 计算国家在指定时间段内的贸易总额
     * 用于税收计算
     *
     * @param nationId 国家ID
     * @param durationSeconds 统计时间段（秒）
     * @return 贸易总额（BigDecimal格式）
     */
    default java.math.BigDecimal calculateTradeVolume(java.time.Duration duration) {
        return calculateTradeVolume(duration.toMillis());
    }

    /**
     * 计算国家在指定时间段内的贸易总额
     * 用于税收计算
     *
     * @param nationId 国家ID
     * @param durationMillis 统计时间段（毫秒）
     * @return 贸易总额
     */
    default java.math.BigDecimal calculateTradeVolume(long durationMillis) {
        // 默认实现：获取所有贸易协定并计算总额
        // 子类可覆盖以提供更高效的实现
        return java.math.BigDecimal.ZERO;
    }

    /**
     * 计算国家在指定时间段内的贸易总额
     *
     * @param nationId 国家ID
     * @param duration 统计时间段
     * @return 贸易总额
     */
    java.math.BigDecimal calculateTradeVolume(NationId nationId, java.time.Duration duration);
}
