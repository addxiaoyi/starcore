package dev.starcore.starcore.module.diplomacy.alliance;

import dev.starcore.starcore.module.nation.model.Nation;
import dev.starcore.starcore.module.nation.model.NationId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 联盟外交系统服务接口
 *
 * 提供完整的联盟管理功能：
 * - 联盟邀请与处理
 * - 联盟关系查询
 * - 联盟成员管理
 * - 联盟公告与外交
 */
public interface AllianceService {

    /**
     * 初始化服务
     */
    void initialize();

    /**
     * 关闭服务
     */
    void shutdown();

    // ==================== 联盟邀请系统 ====================

    /**
     * 发送联盟邀请
     * @param inviter 邀请方国家ID
     * @param invited 被邀请方国家ID
     * @return 邀请结果
     */
    AllianceInviteResult sendInvite(NationId inviter, NationId invited);

    /**
     * 接受联盟邀请
     * @param acceptor 接受方国家ID
     * @param inviterNationName 邀请方国家名称
     * @return 联盟结果
     */
    AllianceResult acceptInvite(NationId acceptor, String inviterNationName);

    /**
     * 拒绝联盟邀请
     * @param rejector 拒绝方国家ID
     * @param inviterNationName 邀请方国家名称
     */
    void rejectInvite(NationId rejector, String inviterNationName);

    /**
     * 取消联盟邀请
     * @param canceller 取消方国家ID
     * @param invited 被邀请方国家名称
     */
    void cancelInvite(NationId canceller, String invited);

    /**
     * 获取待处理的联盟邀请
     * @param nationId 接收邀请的国家ID
     * @return 邀请方列表
     */
    List<AllianceInviteInfo> getPendingInvites(NationId nationId);

    /**
     * 检查是否有待处理的联盟邀请
     * @param nationId 国家ID
     * @return 是否有邀请
     */
    boolean hasPendingInvite(NationId nationId);

    // ==================== 联盟关系管理 ====================

    /**
     * 获取国家的所有联盟国家
     * @param nationId 国家ID
     * @return 联盟国家列表
     */
    Collection<NationId> getAllies(NationId nationId);

    /**
     * 获取联盟详细信息
     * @param nation1 国家1
     * @param nation2 国家2
     * @return 联盟信息
     */
    Optional<AllianceInfo> getAllianceInfo(NationId nation1, NationId nation2);

    /**
     * 检查两个国家是否为联盟
     * @param nation1 国家1
     * @param nation2 国家2
     * @return 是否为联盟
     */
    boolean areAllied(NationId nation1, NationId nation2);

    /**
     * 解除联盟关系
     * @param nation1 国家1
     * @param nation2 国家2
     * @param brokenBy 解除方
     * @return 是否成功解除
     */
    boolean breakAlliance(NationId nation1, NationId nation2, NationId brokenBy);

    /**
     * 获取联盟总数
     * @return 联盟数量
     */
    int getAllianceCount();

    // ==================== 联盟外交策略 ====================

    /**
     * 获取联盟外交冷却状态
     * @param nation1 国家1
     * @param nation2 国家2
     * @return 是否在冷却中
     */
    boolean isInCooldown(NationId nation1, NationId nation2);

    /**
     * 获取剩余冷却时间（毫秒）
     * @param nation1 国家1
     * @param nation2 国家2
     * @return 剩余冷却时间
     */
    long getRemainingCooldownMs(NationId nation1, NationId nation2);

    // ==================== 统计数据 ====================

    /**
     * 获取联盟统计信息
     * @return 统计信息
     */
    AllianceStats getStats();

    /**
     * 摘要信息
     * @return 摘要
     */
    String summary();

    // ==================== 数据模型 ====================

    /**
     * 联盟邀请结果
     */
    record AllianceInviteResult(boolean success, String message) {}

    /**
     * 联盟操作结果
     */
    record AllianceResult(boolean success, String message) {}

    /**
     * 联盟邀请信息
     */
    record AllianceInviteInfo(
        NationId inviterId,
        String inviterName,
        Instant invitedAt,
        long remainingMs
    ) {}

    /**
     * 联盟详细信息
     */
    record AllianceInfo(
        NationId nation1,
        NationId nation2,
        String nation1Name,
        String nation2Name,
        Instant formedAt,
        long durationDays
    ) {}

    /**
     * 联盟统计数据
     */
    record AllianceStats(
        int totalAlliances,
        int totalInvitesPending,
        int largestAllianceSize,
        String mostActiveNation
    ) {}
}
