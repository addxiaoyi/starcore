package dev.starcore.starcore.module.alliance;

import dev.starcore.starcore.module.nation.model.NationId;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 联盟外交系统服务接口
 *
 * 提供多国联盟（联邦）管理功能：
 * - 创建和解散联盟
 * - 邀请和管理联盟成员
 * - 设置联盟关系（友好/敌对）
 * - 联盟外交策略
 *
 * 联盟是由多个国家组成的政治组织，可以共同行动、共享资源、协调战争等。
 */
public interface AllianceService {

    // ==================== 联盟生命周期管理 ====================

    /**
     * 创建新联盟
     * @param name 联盟名称
     * @param leaderId 盟主国家ID
     * @return 创建结果
     */
    AllianceResult createAlliance(String name, NationId leaderId);

    /**
     * 解散联盟
     * @param allianceId 联盟ID
     * @return 解散结果
     */
    AllianceResult disbandAlliance(UUID allianceId);

    /**
     * 重命名联盟
     * @param actor 请求者国家ID（用于权限校验）
     * @param allianceId 联盟ID
     * @param newName 新名称
     * @return 重命名结果
     */
    AllianceResult renameAlliance(NationId actor, UUID allianceId, String newName);

    /**
     * 设置联盟标志/徽章
     * @param actor 请求者国家ID（用于权限校验）
     * @param allianceId 联盟ID
     * @param emblem 徽章标识
     * @return 设置结果
     */
    AllianceResult setEmblem(NationId actor, UUID allianceId, String emblem);

    /**
     * 获取联盟信息
     * @param allianceId 联盟ID
     * @return 联盟信息
     */
    Optional<Alliance> getAlliance(UUID allianceId);

    /**
     * 根据名称查找联盟
     * @param name 联盟名称
     * @return 联盟信息
     */
    Optional<Alliance> getAllianceByName(String name);

    /**
     * 获取所有联盟
     * @return 联盟列表
     */
    Collection<Alliance> getAllAlliances();

    /**
     * 获取联盟总数
     * @return 联盟数量
     */
    int getAllianceCount();

    // ==================== 成员管理 ====================

    /**
     * 邀请国家加入联盟
     * @param allianceId 联盟ID
     * @param nationId 被邀请的国家ID
     * @return 邀请结果
     */
    InviteResult inviteNation(UUID allianceId, NationId nationId);

    /**
     * 接受加入联盟邀请
     * @param nationId 接受邀请的国家ID
     * @param allianceId 联盟ID
     * @return 加入结果
     */
    AllianceResult acceptInvite(NationId nationId, UUID allianceId);

    /**
     * 拒绝加入联盟邀请
     * @param nationId 拒绝国家ID
     * @param allianceId 联盟ID
     */
    void rejectInvite(NationId nationId, UUID allianceId);

    /**
     * 国家主动离开联盟
     * @param nationId 离开的国家ID
     * @return 离开结果
     */
    AllianceResult leaveAlliance(NationId nationId);

    /**
     * 将国家从联盟中移除
     * @param allianceId 联盟ID
     * @param nationId 要移除的国家ID
     * @param removedBy 执行移除的领导者ID
     * @return 移除结果
     */
    AllianceResult removeMember(UUID allianceId, NationId nationId, NationId removedBy);

    /**
     * 设置成员在联盟中的角色
     * @param allianceId 联盟ID
     * @param nationId 目标国家ID
     * @param role 新角色
     * @return 设置结果
     */
    AllianceResult setMemberRole(UUID allianceId, NationId nationId, AllianceMember.Role role);

    /**
     * 转移联盟领导权
     * @param allianceId 联盟ID
     * @param newLeaderId 新盟主国家ID
     * @param currentLeaderId 当前盟主国家ID
     * @return 转移结果
     */
    AllianceResult transferLeadership(UUID allianceId, NationId newLeaderId, NationId currentLeaderId);

    /**
     * 获取联盟成员列表
     * @param allianceId 联盟ID
     * @return 成员列表
     */
    List<AllianceMember> getAllianceMembers(UUID allianceId);

    /**
     * 获取国家所属的联盟
     * @param nationId 国家ID
     * @return 联盟信息（如果在联盟中）
     */
    Optional<Alliance> getNationAlliance(NationId nationId);

    /**
     * 检查国家是否在联盟中
     * @param nationId 国家ID
     * @return 是否在联盟中
     */
    boolean isInAlliance(NationId nationId);

    /**
     * 获取待处理的加入邀请
     * @param nationId 接收邀请的国家ID
     * @return 邀请列表
     */
    List<AllianceInviteInfo> getPendingInvites(NationId nationId);

    /**
     * 检查是否有待处理的邀请
     * @param nationId 国家ID
     * @return 是否有邀请
     */
    boolean hasPendingInvite(NationId nationId);

    // ==================== 联盟外交关系 ====================

    /**
     * 设置两个联盟之间的关系
     * @param alliance1 联盟1 ID
     * @param alliance2 联盟2 ID
     * @param relationType 关系类型
     * @return 设置结果
     */
    AllianceResult setAllianceRelation(UUID alliance1, UUID alliance2, AllianceRelationType relationType);

    /**
     * 获取两个联盟之间的关系
     * @param alliance1 联盟1 ID
     * @param alliance2 联盟2 ID
     * @return 关系信息
     */
    Optional<AllianceRelation> getAllianceRelation(UUID alliance1, UUID alliance2);

    /**
     * 获取联盟的所有友好关系
     * @param allianceId 联盟ID
     * @return 友好联盟列表
     */
    Collection<UUID> getFriendlyAlliances(UUID allianceId);

    /**
     * 获取联盟的所有敌对关系
     * @param allianceId 联盟ID
     * @return 敌对联盟列表
     */
    Collection<UUID> getHostileAlliances(UUID allianceId);

    // ==================== 公告和外交 ====================

    /**
     * 设置联盟公告
     * @param allianceId 联盟ID
     * @param announcement 公告内容
     * @param announcerId 发布公告的国家ID
     * @return 设置结果
     */
    AllianceResult setAnnouncement(UUID allianceId, String announcement, NationId announcerId);

    /**
     * 获取联盟公告
     * @param allianceId 联盟ID
     * @return 公告信息
     */
    Optional<AllianceAnnouncement> getAnnouncement(UUID allianceId);

    // ==================== 初始化和关闭 ====================

    /**
     * 初始化服务
     */
    void initialize();

    /**
     * 关闭服务
     */
    void shutdown();

    /**
     * 摘要信息
     * @return 摘要
     */
    String summary();

    // ==================== 数据模型 ====================

    /**
     * 联盟基本信息
     * @param id 联盟唯一ID
     * @param name 联盟名称
     * @param leaderId 盟主国家ID
     * @param createdAt 创建时间
     * @param emblem 联盟徽章标识
     */
    record Alliance(
        UUID id,
        String name,
        NationId leaderId,
        Instant createdAt,
        String emblem
    ) {}

    /**
     * 联盟成员信息
     * @param nationId 成员国家ID
     * @param role 成员角色（LEADER, OFFICER, MEMBER）
     * @param joinedAt 加入时间
     */
    record AllianceMember(
        NationId nationId,
        Role role,
        Instant joinedAt
    ) {
        /**
         * 成员角色枚举
         */
        public enum Role {
            /** 盟主 - 创建者，拥有最高权限 */
            LEADER,
            /** 官员 - 可以邀请和管理普通成员 */
            OFFICER,
            /** 普通成员 */
            MEMBER
        }
    }

    /**
     * 联盟邀请信息
     * @param allianceId 联盟ID
     * @param allianceName 联盟名称
     * @param invitedNationId 被邀请国家ID
     * @param invitedAt 邀请时间
     * @param invitedBy 邀请者国家ID
     * @param expiresAt 过期时间
     */
    record AllianceInviteInfo(
        UUID allianceId,
        String allianceName,
        NationId invitedNationId,
        Instant invitedAt,
        NationId invitedBy,
        Instant expiresAt
    ) {}

    /**
     * 联盟关系信息
     * @param alliance1 联盟1 ID
     * @param alliance2 联盟2 ID
     * @param type 关系类型
     * @param startedAt 开始时间
     * @param notes 备注说明
     */
    record AllianceRelation(
        UUID alliance1,
        UUID alliance2,
        AllianceRelationType type,
        Instant startedAt,
        String notes
    ) {}

    /**
     * 联盟关系类型
     */
    enum AllianceRelationType {
        /** 友好 - 互相支援、共享信息 */
        FRIENDLY,
        /** 中立 - 无特殊关系 */
        NEUTRAL,
        /** 敌对 - 竞争或战争状态 */
        HOSTILE,
        /** 战争 - 正式宣战 */
        WAR
    }

    /**
     * 联盟公告
     * @param allianceId 联盟ID
     * @param content 公告内容
     * @param publishedBy 发布国家ID
     * @param publishedAt 发布时间
     */
    record AllianceAnnouncement(
        UUID allianceId,
        String content,
        NationId publishedBy,
        Instant publishedAt
    ) {}

    /**
     * 联盟操作结果
     * @param success 是否成功
     * @param message 结果消息
     */
    record AllianceResult(boolean success, String message) {}

    /**
     * 邀请操作结果
     * @param success 是否成功
     * @param message 结果消息
     * @param allianceId 联盟ID（成功时返回）
     */
    record InviteResult(boolean success, String message, UUID allianceId) {}
}
