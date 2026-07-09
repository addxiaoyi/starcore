package dev.starcore.starcore.module.diplomacy;

import dev.starcore.starcore.module.nation.model.NationId;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DiplomacyService {
    // D-128: 已知问题：接口仅 106 行，部分方法缺少实现细节。
    // 建议：补全 AllianceService 与 DiplomacyService 的职责边界，补充关系变化的过期/冷却配置。

    DiplomacyRelation relationBetween(NationId left, NationId right);

    DiplomacyRelation setRelation(NationId left, NationId right, DiplomacyRelation relation);

    Collection<DiplomacyRelationSnapshot> relationsOf(NationId nationId);

    Optional<DiplomacyRelationSnapshot> relationSnapshot(NationId left, NationId right);

    /**
     * 获取国家间的战争记录
     */
    Optional<WarRecord> getWarRecord(NationId left, NationId right);

    /**
     * 获取一个国家正在进行的战争列表
     */
    Collection<WarRecord> activeWarsOf(NationId nationId);

    /**
     * 宣布战争（带费用检查）
     * @throws IllegalStateException 如果余额不足或冷却中
     */
    WarRecord declareWar(NationId declarer, NationId target);

    /**
     * 结束战争
     * @return 是否成功结束（战争不存在时返回false）
     */
    boolean endWar(NationId left, NationId right);

    // ==================== 联盟邀请系统 ====================

    /**
     * 发送联盟邀请
     * @param inviter 邀请方
     * @param invited 被邀请方
     * @return 邀请结果
     */
    AllianceInviteResult sendAllianceInvite(NationId inviter, NationId invited);

    /**
     * 接受联盟邀请
     * @param acceptor 接受方
     * @param inviter 邀请方
     * @return 联盟结果
     */
    AllianceResult acceptAllianceInvite(NationId acceptor, NationId inviter);

    /**
     * 拒绝联盟邀请
     * @param rejector 拒绝方
     * @param inviter 邀请方
     */
    void rejectAllianceInvite(NationId rejector, NationId inviter);

    /**
     * 取消联盟邀请（邀请方使用）
     * @param canceller 取消方
     * @param invited 被邀请方
     */
    void cancelAllianceInvite(NationId canceller, NationId invited);

    /**
     * 获取待处理的联盟邀请列表
     * @param nationId 接收邀请的国家
     * @return 邀请方列表
     */
    List<NationId> getPendingInvites(NationId nationId);

    /**
     * 检查是否有待处理的联盟邀请
     * @param nationId 接收邀请的国家
     * @param fromInviter 邀请方（可选，用于检查特定邀请）
     * @return 是否有邀请
     */
    boolean hasPendingInvite(NationId nationId, NationId fromInviter);

    /**
     * 获取宣战费用
     */
    BigDecimal getWarDeclarationCost();

    String summary();

    // ==================== 结果记录 ====================

    /**
     * 联盟邀请结果
     */
    record AllianceInviteResult(boolean success, String message) {}

    /**
     * 联盟操作结果
     */
    record AllianceResult(boolean success, String message) {}
}
