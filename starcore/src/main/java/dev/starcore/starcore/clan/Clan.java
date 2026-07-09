package dev.starcore.starcore.clan;

import org.bukkit.Location;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Clan氏族系统
 * 基于SimpleClans的社交组织设计
 *
 * Nation内的次级社交组织
 */
public class Clan {

    private final UUID id;
    private String tag;              // 4字符标签
    private String name;             // 全名
    private UUID leader;             // 族长
    private final Set<UUID> members = new HashSet<>();

    // 成员职位 (UUID -> ClanRank) - 使用ConcurrentHashMap保证线程安全
    private final Map<UUID, ClanRank> memberRanks = new ConcurrentHashMap<>();

    // 所属Nation
    private UUID nationId;

    // Clan关系
    private final Set<UUID> allies = new HashSet<>();   // 盟友Clan
    private final Set<UUID> rivals = new HashSet<>();   // 敌对Clan

    // Clan统计
    private int kills = 0;
    private int deaths = 0;
    private double kdr = 0.0;

    // Clan经济
    private double balance = 0.0;

    // Clan设置
    private boolean friendlyFire = false;
    private boolean pvpEnabled = true;

    // Clan据点
    private Location homeLocation;
    private String homeWorld;

    // 时间戳
    private final long createdTime;
    private long lastActiveTime;

    public Clan(UUID id, String tag, String name, UUID leader) {
        this.id = id;
        this.tag = tag;
        this.name = name;
        this.leader = leader;
        this.createdTime = System.currentTimeMillis();
        this.lastActiveTime = System.currentTimeMillis();

        // 领导者自动加入
        this.members.add(leader);
        this.memberRanks.put(leader, ClanRank.LEADER);
    }

    // ==================== 成员管理 ====================

    /**
     * 添加成员
     */
    public boolean addMember(UUID playerId) {
        if (members.size() >= getMaxMembers()) {
            return false;
        }

        boolean added = members.add(playerId);
        if (added) {
            memberRanks.put(playerId, ClanRank.MEMBER);
            updateActiveTime();
        }
        return added;
    }

    /**
     * 移除成员
     */
    public boolean removeMember(UUID playerId) {
        // 不能移除领导者
        if (playerId.equals(leader)) {
            return false;
        }

        memberRanks.remove(playerId);
        return members.remove(playerId);
    }

    /**
     * 转让族长
     */
    public void transferLeadership(UUID newLeader) {
        if (members.contains(newLeader)) {
            // 原族长降为成员
            memberRanks.put(leader, ClanRank.OFFICER);
            this.leader = newLeader;
            memberRanks.put(newLeader, ClanRank.LEADER);
        }
    }

    /**
     * 设置成员职位
     */
    public void setRank(UUID playerId, ClanRank rank) {
        if (members.contains(playerId) && rank != ClanRank.LEADER) {
            memberRanks.put(playerId, rank);
        }
    }

    /**
     * 获取成员职位
     */
    public ClanRank getRank(UUID playerId) {
        return memberRanks.getOrDefault(playerId, ClanRank.MEMBER);
    }

    /**
     * 检查是否有权限
     */
    public boolean hasPermission(UUID playerId, ClanPermission permission) {
        ClanRank rank = getRank(playerId);
        if (rank == ClanRank.LEADER) {
            return true; // 族长拥有所有权限
        }
        return rank.getPermissions().contains(permission);
    }

    /**
     * 是否为成员
     */
    public boolean isMember(UUID playerId) {
        return members.contains(playerId);
    }

    /**
     * 是否为领导者
     */
    public boolean isLeader(UUID playerId) {
        return playerId.equals(leader);
    }

    /**
     * 获取最大成员数
     */
    public int getMaxMembers() {
        return 30; // 可配置
    }

    // ==================== Clan关系 ====================

    /**
     * 添加盟友
     */
    public void addAlly(UUID clanId) {
        allies.add(clanId);
        rivals.remove(clanId); // 移除敌对
    }

    /**
     * 移除盟友
     */
    public void removeAlly(UUID clanId) {
        allies.remove(clanId);
    }

    /**
     * 添加敌对
     */
    public void addRival(UUID clanId) {
        rivals.add(clanId);
        allies.remove(clanId); // 移除盟友
    }

    /**
     * 移除敌对
     */
    public void removeRival(UUID clanId) {
        rivals.remove(clanId);
    }

    /**
     * 是否为盟友
     */
    public boolean isAlly(UUID clanId) {
        return allies.contains(clanId);
    }

    /**
     * 是否为敌对
     */
    public boolean isRival(UUID clanId) {
        return rivals.contains(clanId);
    }

    // ==================== 统计 ====================

    /**
     * 记录击杀
     */
    public void addKill() {
        kills++;
        updateKDR();
        updateActiveTime();
    }

    /**
     * 记录死亡
     */
    public void addDeath() {
        deaths++;
        updateKDR();
        updateActiveTime();
    }

    /**
     * 更新KDR
     */
    private void updateKDR() {
        if (deaths == 0) {
            kdr = kills;
        } else {
            kdr = (double) kills / deaths;
        }
    }

    // ==================== 经济 ====================

    /**
     * 存款
     */
    public void deposit(double amount) {
        if (amount > 0) {
            balance += amount;
        }
    }

    /**
     * 取款
     */
    public boolean withdraw(double amount) {
        if (amount > 0 && balance >= amount) {
            balance -= amount;
            return true;
        }
        return false;
    }

    // ==================== 据点 ====================

    /**
     * 设置据点
     */
    public void setHome(Location location) {
        this.homeLocation = location;
        this.homeWorld = location.getWorld().getName();
    }

    /**
     * 获取据点
     */
    public Location getHome() {
        return homeLocation;
    }

    /**
     * 获取据点世界
     */
    public String getHomeWorld() {
        return homeWorld;
    }

    /**
     * 是否有据点
     */
    public boolean hasHome() {
        return homeLocation != null && homeWorld != null;
    }

    // ==================== 活跃度 ====================

    /**
     * 更新活跃时间
     */
    public void updateActiveTime() {
        this.lastActiveTime = System.currentTimeMillis();
    }

    /**
     * 是否活跃（最近7天）
     */
    public boolean isActive() {
        long diff = System.currentTimeMillis() - lastActiveTime;
        long days = diff / (1000 * 60 * 60 * 24);
        return days <= 7;
    }

    /**
     * 获取存在天数
     */
    public long getAgeDays() {
        long diff = System.currentTimeMillis() - createdTime;
        return diff / (1000 * 60 * 60 * 24);
    }

    // ==================== Getter/Setter ====================

    public UUID getId() {
        return id;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public UUID getLeader() {
        return leader;
    }

    public Set<UUID> getMembers() {
        return Collections.unmodifiableSet(members);
    }

    public Map<UUID, ClanRank> getMemberRanks() {
        return Collections.unmodifiableMap(memberRanks);
    }

    /**
     * 设置成员职位（仅供反序列化使用）
     */
    public void setMemberRank(UUID playerId, ClanRank rank) {
        memberRanks.put(playerId, rank);
    }

    public int getMemberCount() {
        return members.size();
    }

    public UUID getNationId() {
        return nationId;
    }

    public void setNationId(UUID nationId) {
        this.nationId = nationId;
    }

    public Set<UUID> getAllies() {
        return Collections.unmodifiableSet(allies);
    }

    public Set<UUID> getRivals() {
        return Collections.unmodifiableSet(rivals);
    }

    public int getKills() {
        return kills;
    }

    public int getDeaths() {
        return deaths;
    }

    public double getKDR() {
        return kdr;
    }

    public double getBalance() {
        return balance;
    }

    public boolean isFriendlyFire() {
        return friendlyFire;
    }

    public void setFriendlyFire(boolean friendlyFire) {
        this.friendlyFire = friendlyFire;
    }

    public boolean isPvpEnabled() {
        return pvpEnabled;
    }

    public void setPvpEnabled(boolean pvpEnabled) {
        this.pvpEnabled = pvpEnabled;
    }

    public long getCreatedTime() {
        return createdTime;
    }

    public long getLastActiveTime() {
        return lastActiveTime;
    }

    // ==================== 便捷方法 ====================

    /**
     * 获取带颜色的标签
     */
    public String getColoredTag() {
        return "§6[§e" + tag + "§6]";
    }

    /**
     * 获取完整显示名
     */
    public String getDisplayName() {
        return getColoredTag() + " §f" + name;
    }

    @Override
    public String toString() {
        return String.format(
            "Clan[tag=%s, name=%s, members=%d, kdr=%.2f]",
            tag, name, members.size(), kdr
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Clan)) return false;
        Clan clan = (Clan) o;
        return id.equals(clan.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    // ==================== 职位枚举 ====================

    public enum ClanRank {
        LEADER("族长", 100),
        OFFICER("官员", 50),
        MEMBER("成员", 10);

        private final String displayName;
        private final int level;
        private final Set<ClanPermission> permissions;

        ClanRank(String displayName, int level) {
            this.displayName = displayName;
            this.level = level;
            this.permissions = new HashSet<>();

            switch (this) {
                case LEADER -> {
                    permissions.add(ClanPermission.ALL);
                }
                case OFFICER -> {
                    permissions.add(ClanPermission.INVITE);
                    permissions.add(ClanPermission.KICK);
                    permissions.add(ClanPermission.RANK);
                    permissions.add(ClanPermission.SET_HOME);
                    permissions.add(ClanPermission.HOME);
                    permissions.add(ClanPermission.CHAT);
                    permissions.add(ClanPermission.DEPOSIT);
                    permissions.add(ClanPermission.WITHDRAW);
                }
                case MEMBER -> {
                    permissions.add(ClanPermission.HOME);
                    permissions.add(ClanPermission.CHAT);
                    permissions.add(ClanPermission.DEPOSIT);
                }
            }
        }

        public String getDisplayName() {
            return displayName;
        }

        public int getLevel() {
            return level;
        }

        public Set<ClanPermission> getPermissions() {
            return permissions;
        }
    }

    // ==================== 权限枚举 ====================

    public enum ClanPermission {
        ALL,
        INVITE,
        KICK,
        SET_HOME,
        HOME,
        SETTINGS,
        RANK,
        ALLY,
        WITHDRAW,
        DEPOSIT,
        CHAT,
        TAG,
        DISBAND
    }
}
