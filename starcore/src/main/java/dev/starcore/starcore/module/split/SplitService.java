package dev.starcore.starcore.module.split;

import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.split.model.SplitRequest;
import dev.starcore.starcore.module.split.model.SplitRegion;
import dev.starcore.starcore.module.split.model.SplitResult;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * 国家分裂服务接口
 * 提供国家分裂相关功能
 */
public interface SplitService {

    /**
     * 创建分裂请求
     *
     * @param requesterId 请求分裂的玩家ID
     * @param nationId 要分裂的国家ID
     * @param newNationName 新国家名称
     * @param region 分离的领土区域
     * @return 分裂请求结果
     */
    SplitResult createSplitRequest(UUID requesterId, NationId nationId, String newNationName, SplitRegion region);

    /**
     * 批准分裂请求
     *
     * @param approverId 批准者ID（必须是国家领导人）
     * @param requestId 请求ID
     * @return 批准结果
     */
    SplitResult approveSplitRequest(UUID approverId, UUID requestId);

    /**
     * 拒绝分裂请求
     *
     * @param rejecterId 拒绝者ID
     * @param requestId 请求ID
     * @return 拒绝结果
     */
    SplitResult rejectSplitRequest(UUID rejecterId, UUID requestId);

    /**
     * 取消分裂请求
     *
     * @param requesterId 请求者ID
     * @param requestId 请求ID
     * @return 取消结果
     */
    SplitResult cancelSplitRequest(UUID requesterId, UUID requestId);

    /**
     * 强制执行分裂（管理员）
     *
     * @param nationId 要分裂的国家ID
     * @param newNationName 新国家名称
     * @param region 分离的领土区域
     * @return 分裂结果
     */
    SplitResult forceSplit(NationId nationId, String newNationName, SplitRegion region);

    /**
     * 获取玩家的待处理分裂请求
     *
     * @param playerId 玩家ID
     * @return 待处理的分裂请求列表
     */
    Collection<SplitRequest> getPendingRequests(UUID playerId);

    /**
     * 获取国家的待处理分裂请求
     *
     * @param nationId 国家ID
     * @return 待处理的分裂请求列表
     */
    Collection<SplitRequest> getPendingRequestsForNation(NationId nationId);

    /**
     * 获取分裂请求详情
     *
     * @param requestId 请求ID
     * @return 请求详情
     */
    Optional<SplitRequest> getRequest(UUID requestId);

    /**
     * 检查是否可以发起分裂
     *
     * @param playerId 玩家ID
     * @param nationId 国家ID
     * @return 检查结果信息
     */
    String canInitiateSplit(UUID playerId, NationId nationId);

    /**
     * 计算分裂所需费用
     *
     * @param nationId 国家ID
     * @param region 分离的领土区域
     * @return 费用金额
     */
    BigDecimal calculateSplitCost(NationId nationId, SplitRegion region);

    /**
     * 获取服务配置
     */
    SplitConfig getConfig();

    /**
     * 获取服务摘要
     */
    String summary();

    /**
     * 清理过期的分裂请求
     */
    void cleanupExpiredProposals();
}
