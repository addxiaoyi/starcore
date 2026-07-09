package dev.starcore.starcore.module.war.reparations;

import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.war.WarReparation;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 战争赔款服务接口
 * 管理战争赔款的创建、支付、查询等操作
 */
public interface ReparationsService {

    /**
     * 创建赔款记录
     *
     * @param treatyId      条约ID
     * @param payerId       支付方国家ID
     * @param receiverId    接收方国家ID
     * @param totalAmount   赔款总额
     * @param installments  分期次数
     * @return 创建的赔款记录
     */
    WarReparation createReparation(UUID treatyId, NationId payerId, NationId receiverId,
                                   BigDecimal totalAmount, int installments);

    /**
     * 支付赔款
     *
     * @param reparationId 赔款ID
     * @param amount       支付金额
     * @return 是否支付成功
     */
    boolean payReparation(UUID reparationId, BigDecimal amount);

    /**
     * 支付赔款（自动计算分期金额）
     *
     * @param reparationId 赔款ID
     * @return 是否支付成功
     */
    boolean payNextInstallment(UUID reparationId);

    /**
     * 获取赔款记录
     *
     * @param reparationId 赔款ID
     * @return 赔款记录
     */
    Optional<WarReparation> getReparation(UUID reparationId);

    /**
     * 获取条约的所有赔款记录
     *
     * @param treatyId 条约ID
     * @return 赔款记录列表
     */
    List<WarReparation> getReparationsForTreaty(UUID treatyId);

    /**
     * 获取国家作为支付方的所有活跃赔款
     *
     * @param nationId 国家ID
     * @return 赔款记录列表
     */
    List<WarReparation> getReparationsAsPayer(NationId nationId);

    /**
     * 获取国家作为接收方的所有活跃赔款
     *
     * @param nationId 国家ID
     * @return 赔款记录列表
     */
    List<WarReparation> getReparationsAsReceiver(NationId nationId);

    /**
     * 获取所有活跃赔款记录
     *
     * @return 活跃赔款记录列表
     */
    Collection<WarReparation> getActiveReparations();

    /**
     * 获取所有赔款记录
     *
     * @return 所有赔款记录列表
     */
    Collection<WarReparation> getAllReparations();

    /**
     * 免除赔款
     *
     * @param reparationId 赔款ID
     * @return 是否成功
     */
    boolean forgiveReparation(UUID reparationId);

    /**
     * 标记赔款违约
     *
     * @param reparationId 赔款ID
     * @return 是否成功
     */
    boolean markDefault(UUID reparationId);

    /**
     * 检查国家是否有活跃赔款
     *
     * @param nationId 国家ID
     * @return 是否有活跃赔款
     */
    boolean hasActiveReparation(NationId nationId);

    /**
     * 检查国家是否有逾期赔款
     *
     * @param nationId 国家ID
     * @return 是否有逾期赔款
     */
    boolean hasOverdueReparation(NationId nationId);

    /**
     * 计算国家作为支付方的赔款总额
     *
     * @param nationId 国家ID
     * @return 赔款总额
     */
    BigDecimal calculateTotalReparationDebt(NationId nationId);

    /**
     * 计算国家作为支付方的已支付金额
     *
     * @param nationId 国家ID
     * @return 已支付金额
     */
    BigDecimal calculateTotalReparationPaid(NationId nationId);

    /**
     * 处理逾期赔款（定时任务调用）
     */
    void processOverdueReparations();

    /**
     * 统计信息
     */
    String summary();
}