package dev.starcore.starcore.social.party;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 派对（小队）
 */
public final class Party {
    private final UUID id;
    private UUID leader;
    private final Set<UUID> members = ConcurrentHashMap.newKeySet();
    private final long createdTime;

    // 派对设置
    private boolean friendlyFire = false;      // 友军伤害
    private boolean expShare = true;            // 经验共享
    private int maxMembers = 8;                // 最大成员数

    public Party(UUID id, UUID leader) {
        this.id = id;
        this.leader = leader;
        this.createdTime = System.currentTimeMillis();
        this.members.add(leader);
    }

    /**
     * 添加成员
     */
    public boolean addMember(UUID playerId) {
        if (members.size() >= maxMembers) {
            return false;
        }
        return members.add(playerId);
    }

    /**
     * 移除成员
     */
    public boolean removeMember(UUID playerId) {
        return members.remove(playerId);
    }

    /**
     * 检查是否是成员
     */
    public boolean isMember(UUID playerId) {
        return members.contains(playerId);
    }

    /**
     * 检查是否是队长
     */
    public boolean isLeader(UUID playerId) {
        return leader.equals(playerId);
    }

    /**
     * 转让队长
     */
    public void transferLeadership(UUID newLeader) {
        if (members.contains(newLeader)) {
            this.leader = newLeader;
        }
    }

    /**
     * 获取成员数量
     */
    public int getMemberCount() {
        return members.size();
    }

    /**
     * 检查是否已满
     */
    public boolean isFull() {
        return members.size() >= maxMembers;
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public UUID getLeader() { return leader; }
    public Set<UUID> getMembers() { return new HashSet<>(members); }
    public long getCreatedTime() { return createdTime; }
    public boolean isFriendlyFire() { return friendlyFire; }
    public void setFriendlyFire(boolean friendlyFire) { this.friendlyFire = friendlyFire; }
    public boolean isExpShare() { return expShare; }
    public void setExpShare(boolean expShare) { this.expShare = expShare; }
    public int getMaxMembers() { return maxMembers; }
    public void setMaxMembers(int maxMembers) { this.maxMembers = maxMembers; }
}
