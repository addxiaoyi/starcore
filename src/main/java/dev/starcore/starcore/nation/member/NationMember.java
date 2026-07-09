package dev.starcore.starcore.nation.member;

import dev.starcore.starcore.nation.permission.PermissionLevel;
import dev.starcore.starcore.nation.rank.NationRank;

import java.util.UUID;

/**
 * Nation成员信息
 * 扩展现有成员类以支持权限系统
 */
public class NationMember {

    private final UUID playerId;
    private UUID nationId;

    // 职位层级
    private PermissionLevel level = PermissionLevel.MEMBER;

    // Rank职位（可选）
    private String rankName;

    // 加入时间
    private long joinTime;

    // 最后活跃时间
    private long lastActiveTime;

    public NationMember(UUID playerId, UUID nationId) {
        this.playerId = playerId;
        this.nationId = nationId;
        this.joinTime = System.currentTimeMillis();
        this.lastActiveTime = System.currentTimeMillis();
    }

    // ==================== Getter/Setter ====================

    public UUID getPlayerId() {
        return playerId;
    }

    public UUID getNationId() {
        return nationId;
    }

    public void setNationId(UUID nationId) {
        this.nationId = nationId;
    }

    public PermissionLevel getLevel() {
        return level;
    }

    public void setLevel(PermissionLevel level) {
        // TODO audit A-004: 直接 setLevel 可绕过 promote 流程；为防止越权提升到 FOUNDER，
        //   此处禁止通过该 setter 直接设为 FOUNDER，请使用专门的 promote/transferOwnership 流程。
        if (level == PermissionLevel.FOUNDER && this.level != PermissionLevel.FOUNDER) {
            throw new IllegalStateException("Cannot promote to FOUNDER via setLevel; use dedicated founder transfer flow");
        }
        this.level = level;
    }

    public String getRankName() {
        return rankName;
    }

    public void setRankName(String rankName) {
        this.rankName = rankName;
    }

    public long getJoinTime() {
        return joinTime;
    }

    public long getLastActiveTime() {
        return lastActiveTime;
    }

    public void updateActiveTime() {
        this.lastActiveTime = System.currentTimeMillis();
    }

    // ==================== 便捷方法 ====================

    /**
     * 是否为创始人
     */
    public boolean isFounder() {
        return level == PermissionLevel.FOUNDER;
    }

    /**
     * 是否为领导者
     */
    public boolean isLeader() {
        return level.isAtLeast(PermissionLevel.LEADER);
    }

    /**
     * 是否为受信任成员
     */
    public boolean isTrusted() {
        return level.isAtLeast(PermissionLevel.TRUSTED);
    }

    /**
     * 检查是否拥有Rank
     */
    public boolean hasRank() {
        return rankName != null && !rankName.isEmpty();
    }

    /**
     * 提升为领导者
     * @return true 表示等级发生了变更，false 表示当前已是 LEADER 或权限级别不满足提升条件
     */
    public boolean promoteToLeader() {
        if (level == PermissionLevel.MEMBER || level == PermissionLevel.TRUSTED) {
            level = PermissionLevel.LEADER;
            return true;
        }
        return false;
    }

    /**
     * 降级为普通成员
     */
    public void demoteToMember() {
        if (level == PermissionLevel.LEADER || level == PermissionLevel.TRUSTED) {
            level = PermissionLevel.MEMBER;
        }
    }

    /**
     * 设置为受信任成员
     */
    public void setTrusted() {
        if (level == PermissionLevel.MEMBER) {
            level = PermissionLevel.TRUSTED;
        }
    }

    /**
     * 取消受信任
     */
    public void removeTrusted() {
        if (level == PermissionLevel.TRUSTED) {
            level = PermissionLevel.MEMBER;
        }
    }

    /**
     * 获取成员时长（天）
     */
    public long getMembershipDays() {
        long diff = System.currentTimeMillis() - joinTime;
        return diff / (1000 * 60 * 60 * 24);
    }

    /**
     * 是否活跃（最近7天内）
     */
    public boolean isActive() {
        long diff = System.currentTimeMillis() - lastActiveTime;
        long days = diff / (1000 * 60 * 60 * 24);
        return days <= 7;
    }

    @Override
    public String toString() {
        return String.format(
            "NationMember[player=%s, nation=%s, level=%s, rank=%s]",
            playerId, nationId, level, rankName
        );
    }
}
