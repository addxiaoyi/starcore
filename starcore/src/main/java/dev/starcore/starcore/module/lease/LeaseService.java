package dev.starcore.starcore.module.lease;

import dev.starcore.starcore.module.lease.model.LeaseContract;
import dev.starcore.starcore.module.lease.model.LeaseStatus;
import dev.starcore.starcore.module.lease.model.LeaseType;
import dev.starcore.starcore.module.nation.model.NationId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * 租约契约服务接口
 * 提供租约创建、管理、查询等功能
 */
public interface LeaseService {

    /**
     * 创建新租约
     * @param lessorNationId 出租方国家ID
     * @param lessorPlayerId 出租方玩家ID
     * @param tenantNationId 承租方国家ID（可为null表示租给个人）
     * @param tenantPlayerId 承租方玩家ID
     * @param type 租约类型
     * @param regionId 区域ID
     * @param monthlyRent 月租金
     * @param durationDays 租期天数
     * @return 创建的租约
     */
    LeaseContract createLease(
        NationId lessorNationId,
        UUID lessorPlayerId,
        NationId tenantNationId,
        UUID tenantPlayerId,
        LeaseType type,
        String regionId,
        BigDecimal monthlyRent,
        int durationDays
    );

    /**
     * 签署租约
     * @param contractId 租约ID
     * @param signerId 签署方ID
     * @return 签署是否成功
     */
    boolean signLease(UUID contractId, UUID signerId);

    /**
     * 拒绝租约
     * @param contractId 租约ID
     * @param rejecterId 拒绝方ID
     * @return 拒绝是否成功
     */
    boolean rejectLease(UUID contractId, UUID rejecterId);

    /**
     * 终止租约
     * @param contractId 租约ID
     * @param terminatorId 终止方ID
     * @param reason 终止原因
     * @return 终止是否成功
     */
    boolean terminateLease(UUID contractId, UUID terminatorId, String reason);

    /**
     * 续租
     * @param contractId 租约ID
     * @param extenderId 续租方ID
     * @param additionalDays 额外天数
     * @return 续租是否成功
     */
    boolean renewLease(UUID contractId, UUID extenderId, int additionalDays);

    /**
     * 支付租金
     * @param contractId 租约ID
     * @param payerId 支付方ID
     * @param months 支付月数
     * @return 支付是否成功
     */
    boolean payRent(UUID contractId, UUID payerId, int months);

    /**
     * 获取租约
     * @param contractId 租约ID
     * @return 租约（如果存在）
     */
    Optional<LeaseContract> getLease(UUID contractId);

    /**
     * 获取国家作为出租方的所有租约
     * @param nationId 国家ID
     * @return 租约列表
     */
    Collection<LeaseContract> getLeasesByLessor(NationId nationId);

    /**
     * 获取国家作为承租方的所有租约
     * @param nationId 国家ID
     * @return 租约列表
     */
    Collection<LeaseContract> getLeasesByTenant(NationId nationId);

    /**
     * 获取区域的所有租约
     * @param regionId 区域ID
     * @return 租约列表
     */
    Collection<LeaseContract> getLeasesByRegion(String regionId);

    /**
     * 获取玩家的所有租约
     * @param playerId 玩家ID
     * @return 租约列表
     */
    Collection<LeaseContract> getLeasesByPlayer(UUID playerId);

    /**
     * 获取即将过期的租约
     * @param daysRemaining 剩余天数阈值
     * @return 即将过期的租约列表
     */
    Collection<LeaseContract> getExpiringLeases(int daysRemaining);

    /**
     * 获取逾期未付租金的租约
     * @return 逾期租约列表
     */
    Collection<LeaseContract> getOverdueLeases();

    /**
     * 检查租约是否有效
     * @param contractId 租约ID
     * @return 是否有效
     */
    boolean isLeaseActive(UUID contractId);

    /**
     * 处理租约到期（定时任务调用）
     */
    void processExpiredLeases();

    /**
     * 处理逾期租金（定时任务调用）
     */
    void processOverduePayments();

    /**
     * 初始化数据库表
     */
    void initializeTables();

    /**
     * 获取服务摘要
     */
    String summary();
}
