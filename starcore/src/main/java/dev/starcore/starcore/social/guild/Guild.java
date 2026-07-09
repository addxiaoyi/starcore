package dev.starcore.starcore.social.guild;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 公会（星座）
 */
public final class Guild {
    private final UUID id;
    private String name;
    private String tag;
    private UUID leader;

    // 成员列表（成员UUID -> 职位）
    private final Map<UUID, GuildRole> members = new ConcurrentHashMap<>();

    // 公会等级和经验
    private int level = 1;
    private int experience = 0;

    // 公会银行
    private double balance = 0.0;

    // 创建时间
    private final long createdTime;

    // 公会消息
    private String description = "";

    // 解散意图标记（D-012：二次确认）
    private transient boolean disbandRequested = false;

    public Guild(UUID id, String name, String tag, UUID leader) {
        this.id = id;
        this.name = name;
        this.tag = tag;
        this.leader = leader;
        this.createdTime = System.currentTimeMillis();

        // 会长自动加入
        this.members.put(leader, GuildRole.LEADER);
    }

    /**
     * 添加成员
     */
    public void addMember(UUID playerId) {
        members.put(playerId, GuildRole.MEMBER);
    }

    /**
     * 移除成员
     */
    public void removeMember(UUID playerId) {
        members.remove(playerId);
    }

    /**
     * 检查是否是成员
     */
    public boolean isMember(UUID playerId) {
        return members.containsKey(playerId);
    }

    /**
     * 获取成员职位
     */
    public GuildRole getMemberRole(UUID playerId) {
        return members.getOrDefault(playerId, GuildRole.MEMBER);
    }

    /**
     * 设置成员职位
     */
    public void setMemberRole(UUID playerId, GuildRole role) {
        if (members.containsKey(playerId)) {
            members.put(playerId, role);
        }
    }

    /**
     * 检查是否可以邀请
     */
    public boolean canInvite(UUID playerId) {
        GuildRole role = getMemberRole(playerId);
        return role == GuildRole.LEADER || role == GuildRole.OFFICER;
    }

    /**
     * 检查是否可以踢出
     */
    public boolean canKick(UUID playerId) {
        GuildRole role = getMemberRole(playerId);
        return role == GuildRole.LEADER || role == GuildRole.OFFICER;
    }

    /**
     * 检查是否可以管理职位
     */
    public boolean canManageRoles(UUID playerId) {
        return getMemberRole(playerId) == GuildRole.LEADER;
    }

    /**
     * 增加经验
     */
    public void addExperience(int exp) {
        this.experience += exp;

        // 检查升级
        int requiredExp = getRequiredExperience();
        while (this.experience >= requiredExp) {
            this.experience -= requiredExp;
            this.level++;
            requiredExp = getRequiredExperience();
        }
    }

    /**
     * 获取升级所需经验
     */
    public int getRequiredExperience() {
        return level * 1000;
    }

    /**
     * 存入公会银行
     */
    public void deposit(double amount) {
        this.balance += amount;
    }

    /**
     * 从公会银行取出
     */
    public boolean withdraw(double amount) {
        if (this.balance >= amount) {
            this.balance -= amount;
            return true;
        }
        return false;
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getTag() { return tag; }
    public void setTag(String tag) { this.tag = tag; }
    public UUID getLeader() { return leader; }
    public void setLeader(UUID leader) {
        this.leader = leader;
        members.put(leader, GuildRole.LEADER);
    }
    public Set<UUID> getMembers() { return new HashSet<>(members.keySet()); }
    public Map<UUID, GuildRole> getMembersRaw() { return members; }
    public int getMemberCount() { return members.size(); }
    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }
    public int getExperience() { return experience; }
    public void setExperience(int experience) { this.experience = experience; }
    public double getBalance() { return balance; }
    public void setBalance(double balance) { this.balance = balance; }
    public long getCreatedTime() { return createdTime; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    /**
     * Set level and experience directly (for loading from persistence)
     */
    public void setLevelAndExperience(int level, int experience) {
        this.level = level;
        this.experience = experience;
    }

    // ----- D-012 解散二次确认 -----
    public void requestDisband() { this.disbandRequested = true; }
    public boolean isDisbandRequested() { return this.disbandRequested; }
}
