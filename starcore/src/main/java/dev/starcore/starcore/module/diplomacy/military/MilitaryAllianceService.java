package dev.starcore.starcore.module.diplomacy.military;

import dev.starcore.starcore.module.nation.model.NationId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 军事联盟服务接口
 *
 * 提供军事联盟管理功能：
 * - 军事联盟邀请与接受
 * - 军事联盟关系管理
 * - 互助防御条约
 * - 联合军事协议
 */
public interface MilitaryAllianceService {

    // ==================== 生命周期 ====================

    /**
     * 初始化服务
     */
    void initialize();

    /**
     * 关闭服务
     */
    void shutdown();

    // ==================== 军事联盟邀请系统 ====================

    /**
     * 发送军事联盟邀请
     * @param inviter 邀请方国家ID
     * @param invited 被邀请方国家ID
     * @param pactType 条约类型
     * @return 邀请结果
     */
    PactInviteResult sendPactInvite(NationId inviter, NationId invited, PactType pactType);

    /**
     * 接受军事联盟邀请
     * @param acceptor 接受方国家ID
     * @param inviterName 邀请方国家名称
     * @return 条约结果
     */
    PactResult acceptPactInvite(NationId acceptor, String inviterName);

    /**
     * 拒绝军事联盟邀请
     * @param rejector 拒绝方国家ID
     * @param inviterName 邀请方国家名称
     */
    void rejectPactInvite(NationId rejector, String inviterName);

    /**
     * 取消军事联盟邀请
     * @param canceller 取消方国家ID
     * @param invited 被邀请方国家名称
     */
    void cancelPactInvite(NationId canceller, String invited);

    /**
     * 获取待处理的军事联盟邀请
     * @param nationId 接收邀请的国家ID
     * @return 邀请列表
     */
    List<PactInviteInfo> getPendingInvites(NationId nationId);

    /**
     * 检查是否有待处理的邀请
     * @param nationId 国家ID
     * @return 是否有邀请
     */
    boolean hasPendingInvite(NationId nationId);

    // ==================== 军事联盟关系管理 ====================

    /**
     * 获取国家的所有军事联盟国家
     * @param nationId 国家ID
     * @param minLevel 最低条约等级
     * @return 军事联盟国家列表
     */
    Collection<NationId> getMilitaryAllies(NationId nationId, PactType minLevel);

    /**
     * 获取军事联盟详细信息
     * @param nation1 国家1
     * @param nation2 国家2
     * @return 联盟信息
     */
    Optional<MilitaryPactInfo> getPactInfo(NationId nation1, NationId nation2);

    /**
     * 检查两个国家是否有军事联盟关系
     * @param nation1 国家1
     * @param nation2 国家2
     * @param minLevel 最低条约等级
     * @return 是否有军事联盟
     */
    boolean hasMilitaryAlliance(NationId nation1, NationId nation2, PactType minLevel);

    /**
     * 解除军事联盟关系
     * @param nation1 国家1
     * @param nation2 国家2
     * @param brokenBy 解除方
     * @return 是否成功解除
     */
    boolean breakPact(NationId nation1, NationId nation2, NationId brokenBy);

    /**
     * 升级军事联盟条约
     * @param nation1 国家1
     * @param nation2 国家2
     * @param newType 新条约类型
     * @return 是否升级成功
     */
    PactResult upgradePact(NationId nation1, NationId nation2, PactType newType);

    /**
     * 获取军事联盟总数
     * @return 联盟数量
     */
    int getPactCount();

    // ==================== 军事联盟效果 ====================

    /**
     * 检查是否在联盟保护下
     * @param attacker 攻击方
     * @param defender 防守方
     * @return 是否受保护
     */
    boolean isUnderProtection(NationId attacker, NationId defender);

    /**
     * 检查是否可以联合防御
     * @param ally1 盟国1
     * @param ally2 盟国2
     * @param target 目标国家
     * @return 是否可以联合防御
     */
    boolean canJointDefense(NationId ally1, NationId ally2, NationId target);

    /**
     * 获取联合防御加成
     * @param defender 防守方
     * @param attacker 攻击方
     * @return 防御加成倍率
     */
    double getDefenseBonus(NationId defender, NationId attacker);

    /**
     * 获取剩余冷却时间（毫秒）
     * @param nation1 国家1
     * @param nation2 国家2
     * @return 剩余冷却时间
     */
    long getRemainingCooldown(NationId nation1, NationId nation2);

    // ==================== 统计数据 ====================

    /**
     * 获取军事联盟统计信息
     * @return 统计信息
     */
    MilitaryAllianceStats getStats();

    /**
     * 摘要信息
     * @return 摘要
     */
    String summary();

    // ==================== 数据模型 ====================

    /**
     * 条约类型枚举
     */
    enum PactType {
        /** 非军事同盟（普通外交联盟） */
        NONE(0, "无", 0.0),
        /** 观察员国 - 信息共享 */
        OBSERVER(1, "观察员国", 0.0),
        /** 防御同盟 - 被动防御承诺 */
        DEFENSIVE(2, "防御同盟", 0.1),
        /** 全面同盟 - 主动军事援助 */
        FULL_ALLIANCE(3, "全面同盟", 0.25),
        /** 军事一体化 - 最高级别联合 */
        INTEGRATED(4, "军事一体化", 0.5);

        private final int level;
        private final String displayName;
        private final double defenseBonus;

        PactType(int level, String displayName, double defenseBonus) {
            this.level = level;
            this.displayName = displayName;
            this.defenseBonus = defenseBonus;
        }

        public int level() { return level; }
        public String displayName() { return displayName; }
        public double defenseBonus() { return defenseBonus; }

        public boolean isHigherThan(PactType other) {
            return this.level > other.level;
        }

        public boolean isAtLeast(PactType minLevel) {
            return this.level >= minLevel.level;
        }
    }

    /**
     * 军事联盟邀请结果
     */
    record PactInviteResult(boolean success, String message) {}

    /**
     * 军事联盟操作结果
     */
    record PactResult(boolean success, String message) {}

    /**
     * 军事联盟邀请信息
     */
    record PactInviteInfo(
        NationId inviterId,
        String inviterName,
        PactType pactType,
        Instant invitedAt,
        long remainingMs
    ) {}

    /**
     * 军事联盟详细信息
     */
    record MilitaryPactInfo(
        NationId nation1,
        NationId nation2,
        String nation1Name,
        String nation2Name,
        PactType pactType,
        Instant formedAt,
        Instant upgradedAt,
        long durationDays
    ) {}

    /**
     * 军事联盟统计数据
     */
    record MilitaryAllianceStats(
        int totalPacts,
        int totalInvitesPending,
        int strongestAlliance,
        String mostAlliedNation
    ) {}
}
