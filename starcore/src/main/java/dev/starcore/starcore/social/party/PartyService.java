package dev.starcore.starcore.social.party;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

/**
 * 派对服务
 *
 * 支持两种持久化模式：
 * 1. YAML 文件持久化（默认，兼容性更好）
 * 2. 数据库持久化（通过 SqlSocialStateStorage）
 */
public final class PartyService {
    // 所有派对（派对ID -> 派对）
    private final Map<UUID, Party> parties = new ConcurrentHashMap<>();

    // 玩家派对映射（玩家UUID -> 派对ID）
    private final Map<UUID, UUID> playerParties = new ConcurrentHashMap<>();

    // 派对邀请（目标UUID -> 邀请者UUID列表）
    private final Map<UUID, Set<UUID>> partyInvites = new ConcurrentHashMap<>();

    // 派对邀请冷却（玩家UUID -> 上次邀请时间），D-019 防止重复邀请
    private final Map<UUID, Long> lastInviteTime = new ConcurrentHashMap<>();

    // 派对邀请时间戳（target UUID -> 邀请者 UUID -> 邀请时间），D-024 邀请 TTL（10 分钟）
    private final Map<UUID, Map<UUID, Long>> partyInviteTimestamps = new ConcurrentHashMap<>();
    private static final long PARTY_INVITE_TTL_MS = 10L * 60 * 1000; // 10 分钟
    private static final long PARTY_INVITE_COOLDOWN_MS = 30_000L; // 30 秒冷却

    // 持久化相关
    private Plugin plugin;
    private File partiesFile;
    private File invitesFile;

    // 数据库存储引用（可选）
    private dev.starcore.starcore.social.SqlSocialStateStorage sqlStorage;

    /**
     * 设置数据库存储（用于数据库持久化）
     */
    public void setSqlStorage(dev.starcore.starcore.social.SqlSocialStateStorage sqlStorage) {
        this.sqlStorage = sqlStorage;
    }

    /**
     * 检查数据库是否可用
     */
    public boolean isDatabaseAvailable() {
        return sqlStorage != null && sqlStorage.isDatabaseAvailable();
    }

    /**
     * 初始化持久化
     */
    public void initialize(Plugin plugin) {
        this.plugin = plugin;
        File dir = new File(plugin.getDataFolder(), "social");
        if (!dir.exists()) dir.mkdirs();
        this.partiesFile = new File(dir, "parties.yml");
        this.invitesFile = new File(dir, "party-invites.yml");
    }

    /**
     * 加载派对数据
     */
    public void loadData() {
        if (isDatabaseAvailable()) {
            loadFromDatabase();
        } else {
            loadFromYaml();
        }
    }

    /**
     * 保存派对数据
     */
    public void saveData() {
        if (isDatabaseAvailable()) {
            saveToDatabase();
        } else {
            saveToYaml();
        }
    }

    /**
     * 从 YAML 加载派对数据
     */
    private void loadFromYaml() {
        loadPartiesYaml();
        loadInvitesYaml();
    }

    /**
     * 保存派对数据到 YAML
     */
    private void saveToYaml() {
        savePartiesYaml();
        saveInvitesYaml();
    }

    /**
     * 从数据库加载派对数据
     */
    private void loadFromDatabase() {
        if (sqlStorage != null) {
            sqlStorage.loadAllParties(parties, playerParties);
            sqlStorage.loadAllPartyInvites(partyInvites);
            plugin.getLogger().info("已从数据库加载 " + parties.size() + " 个派对");
        }
    }

    /**
     * 保存派对数据到数据库
     */
    private void saveToDatabase() {
        if (sqlStorage != null) {
            sqlStorage.saveAllParties(parties.values());
        }
    }

    /**
     * 保存单个派对到数据库
     */
    public void savePartyToDatabase(Party party) {
        if (sqlStorage != null) {
            sqlStorage.saveParty(party);
        }
    }

    /**
     * 从数据库删除派对
     */
    public void deletePartyFromDatabase(UUID partyId) {
        if (sqlStorage != null) {
            sqlStorage.deleteParty(partyId);
        }
    }

    /**
     * 保存派对邀请到数据库
     */
    public void savePartyInviteToDatabase(UUID inviterId, UUID targetId) {
        if (sqlStorage != null) {
            sqlStorage.savePartyInvite(inviterId, targetId, System.currentTimeMillis());
        }
    }

    /**
     * 从数据库删除派对邀请
     */
    public void deletePartyInviteFromDatabase(UUID inviterId, UUID targetId) {
        if (sqlStorage != null) {
            sqlStorage.deletePartyInvite(inviterId, targetId);
        }
    }

    /**
     * 加载派对数据（YAML）
     */
    private void loadPartiesYaml() {
        if (partiesFile == null || !partiesFile.exists()) return;
        try {
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(partiesFile);
            for (String key : yml.getKeys(false)) {
                UUID partyId;
                try {
                    partyId = UUID.fromString(key);
                } catch (IllegalArgumentException ex) {
                    continue;
                }
                Object dataObj = yml.get(key);
                if (dataObj instanceof PartyData data && data.getId() != null) {
                    Party party = data.toParty();
                    parties.put(partyId, party);
                    for (UUID memberId : party.getMembers()) {
                        playerParties.put(memberId, partyId);
                    }
                }
            }
            if (plugin != null) {
                plugin.getLogger().info("已从YAML加载 " + parties.size() + " 个派对");
            }
        } catch (Exception ex) {
            if (plugin != null) {
                plugin.getLogger().warning("加载派对数据失败: " + ex.getMessage());
            }
        }
    }

    /**
     * 保存派对数据（YAML）
     */
    private void savePartiesYaml() {
        if (partiesFile == null) return;
        try {
            YamlConfiguration yml = new YamlConfiguration();
            for (Party party : parties.values()) {
                PartyData data = PartyData.fromParty(party);
                yml.set(party.getId().toString(), data);
            }
            yml.save(partiesFile);
        } catch (IOException ex) {
            if (plugin != null) {
                plugin.getLogger().warning("保存派对数据失败: " + ex.getMessage());
            }
        }
    }

    /**
     * 加载派对邀请数据（YAML）
     */
    private void loadInvitesYaml() {
        if (invitesFile == null || !invitesFile.exists()) return;
        try {
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(invitesFile);
            for (String key : yml.getKeys(false)) {
                UUID targetId;
                try {
                    targetId = UUID.fromString(key);
                } catch (IllegalArgumentException ex) {
                    continue;
                }
                List<String> inviterList = yml.getStringList(key);
                Set<UUID> inviters = ConcurrentHashMap.newKeySet();
                for (String inviterStr : inviterList) {
                    try {
                        inviters.add(UUID.fromString(inviterStr));
                    } catch (IllegalArgumentException e) {
                        // 跳过无效的UUID
                    }
                }
                if (!inviters.isEmpty()) {
                    partyInvites.put(targetId, inviters);
                }
            }
            if (plugin != null) {
                plugin.getLogger().info("已从YAML加载 " + partyInvites.size() + " 个派对邀请");
            }
        } catch (Exception ex) {
            if (plugin != null) {
                plugin.getLogger().warning("加载派对邀请数据失败: " + ex.getMessage());
            }
        }
    }

    /**
     * 保存派对邀请数据（YAML）
     */
    private void saveInvitesYaml() {
        if (invitesFile == null) return;
        try {
            YamlConfiguration yml = new YamlConfiguration();
            for (Map.Entry<UUID, Set<UUID>> entry : partyInvites.entrySet()) {
                List<String> inviterList = new ArrayList<>();
                for (UUID inviterId : entry.getValue()) {
                    inviterList.add(inviterId.toString());
                }
                yml.set(entry.getKey().toString(), inviterList);
            }
            yml.save(invitesFile);
        } catch (IOException ex) {
            if (plugin != null) {
                plugin.getLogger().warning("保存派对邀请数据失败: " + ex.getMessage());
            }
        }
    }

    /**
     * 派对数据序列化类
     */
    public static class PartyData {
        private UUID id;
        private UUID leader;
        private List<String> members;
        private long createdTime;
        private boolean friendlyFire;
        private boolean expShare;
        private int maxMembers;

        public PartyData() {}

        public static PartyData fromParty(Party party) {
            PartyData data = new PartyData();
            data.id = party.getId();
            data.leader = party.getLeader();
            data.members = new ArrayList<>();
            for (UUID member : party.getMembers()) {
                data.members.add(member.toString());
            }
            data.createdTime = party.getCreatedTime();
            data.friendlyFire = party.isFriendlyFire();
            data.expShare = party.isExpShare();
            data.maxMembers = party.getMaxMembers();
            return data;
        }

        public Party toParty() {
            if (id == null || leader == null || members == null) {
                return null;
            }
            Party party = new Party(id, leader);
            // 清空默认添加的队长
            party.removeMember(leader);
            // 重新添加所有成员
            for (String memberStr : members) {
                try {
                    party.addMember(UUID.fromString(memberStr));
                } catch (IllegalArgumentException e) {
                    // 跳过无效的UUID
                }
            }
            // 如果成员为空，说明是旧数据，需要重新添加队长
            if (party.getMembers().isEmpty()) {
                party.addMember(leader);
            }
            party.setFriendlyFire(friendlyFire);
            party.setExpShare(expShare);
            party.setMaxMembers(maxMembers);
            return party;
        }

        // Getters and Setters
        public UUID getId() { return id; }
        public void setId(UUID id) { this.id = id; }
        public UUID getLeader() { return leader; }
        public void setLeader(UUID leader) { this.leader = leader; }
        public List<String> getMembers() { return members; }
        public void setMembers(List<String> members) { this.members = members; }
        public long getCreatedTime() { return createdTime; }
        public void setCreatedTime(long createdTime) { this.createdTime = createdTime; }
        public boolean isFriendlyFire() { return friendlyFire; }
        public void setFriendlyFire(boolean friendlyFire) { this.friendlyFire = friendlyFire; }
        public boolean isExpShare() { return expShare; }
        public void setExpShare(boolean expShare) { this.expShare = expShare; }
        public int getMaxMembers() { return maxMembers; }
        public void setMaxMembers(int maxMembers) { this.maxMembers = maxMembers; }
    }

    /**
     * 创建派对
     */
    public Party createParty(UUID leaderId) {
        // 检查是否已在派对中
        if (playerParties.containsKey(leaderId)) {
            throw new IllegalStateException("你已经在一个派对中");
        }

        // 创建派对
        UUID partyId = UUID.randomUUID();
        Party party = new Party(partyId, leaderId);

        parties.put(partyId, party);
        playerParties.put(leaderId, partyId);

        savePartyToDatabase(party);

        return party;
    }

    /**
     * 解散派对
     */
    public boolean disbandParty(UUID partyId, UUID leaderId) {
        Party party = parties.get(partyId);
        if (party == null) return false;

        // 只有队长可以解散
        if (!party.isLeader(leaderId)) {
            throw new IllegalStateException("只有队长可以解散派对");
        }

        // 收集成员，解散时清除这些成员发送的派对邀请
        Set<UUID> members = party.getMembers();
        // 移除所有成员
        for (UUID memberId : members) {
            playerParties.remove(memberId);
        }

        // 清理已解散派对的邀请：删除所有由本派对成员发出的 pending 邀请
        for (UUID memberId : members) {
            // 别人邀请本成员的，删除（派对已不在）
            partyInvites.remove(memberId);
            partyInviteTimestamps.remove(memberId);
        }
        // 删掉其他玩家收到本派对成员发出的邀请
        for (Map.Entry<UUID, Set<UUID>> entry : partyInvites.entrySet()) {
            entry.getValue().removeAll(members);
        }
        for (Map.Entry<UUID, Map<UUID, Long>> entry : partyInviteTimestamps.entrySet()) {
            for (UUID m : members) entry.getValue().remove(m);
        }

        parties.remove(partyId);

        deletePartyFromDatabase(partyId);
        return true;
    }

    /**
     * 邀请玩家
     */
    public boolean inviteMember(UUID partyId, UUID inviterId, UUID targetId) {
        Party party = parties.get(partyId);
        if (party == null) return false;

        // 检查权限（只有队长可以邀请）
        if (!party.isLeader(inviterId)) {
            throw new IllegalStateException("只有队长可以邀请玩家");
        }

        // 检查派对是否已满
        if (party.isFull()) {
            throw new IllegalStateException("派对已满");
        }

        // 检查目标是否已在派对中
        if (playerParties.containsKey(targetId)) {
            throw new IllegalStateException("该玩家已在其他派对中");
        }

        // 重复邀请检测：若已存在该玩家对该 inviter 的邀请（且未过期），拒绝再次发出
        long now = System.currentTimeMillis();
        Set<UUID> invites = partyInvites.get(targetId);
        Map<UUID, Long> stamps = partyInviteTimestamps.get(targetId);
        if (invites != null && invites.contains(inviterId)) {
            Long ts = stamps == null ? null : stamps.get(inviterId);
            if (ts != null && (now - ts) < PARTY_INVITE_TTL_MS) {
                throw new IllegalStateException("已向该玩家发送过邀请，请等待 30 秒");
            }
        }

        // 30 秒冷却，防止对同玩家刷屏
        Long last = lastInviteTime.get(targetId);
        if (last != null && (now - last) < PARTY_INVITE_COOLDOWN_MS) {
            throw new IllegalStateException("邀请冷却中，请稍候再试");
        }

        // 添加邀请
        partyInvites.computeIfAbsent(targetId, k -> ConcurrentHashMap.newKeySet())
            .add(inviterId);
        partyInviteTimestamps.computeIfAbsent(targetId, k -> new ConcurrentHashMap<>())
            .put(inviterId, now);
        lastInviteTime.put(targetId, now);

        savePartyInviteToDatabase(inviterId, targetId);

        return true;
    }

    /**
     * 接受邀请
     */
    public boolean acceptInvite(UUID playerId, UUID inviterId) {
        // 同步块防止并发 accept 满员派对造成超员
        synchronized (this) {
            // 检查邀请是否存在
            Set<UUID> invites = partyInvites.get(playerId);
            if (invites == null || !invites.contains(inviterId)) {
                throw new IllegalStateException("邀请不存在");
            }

            // 校验邀请是否过期
            Map<UUID, Long> stamps = partyInviteTimestamps.get(playerId);
            Long ts = stamps == null ? null : stamps.get(inviterId);
            long now = System.currentTimeMillis();
            if (ts != null && (now - ts) > PARTY_INVITE_TTL_MS) {
                // 过期邀请，直接清理
                invites.remove(inviterId);
                if (stamps != null) stamps.remove(inviterId);
                deletePartyInviteFromDatabase(inviterId, playerId);
                throw new IllegalStateException("邀请已过期");
            }

            // 获取邀请者的派对
            UUID partyId = playerParties.get(inviterId);
            if (partyId == null) {
                // 邀请者已离开/解散派对，清理邀请
                invites.remove(inviterId);
                if (stamps != null) stamps.remove(inviterId);
                deletePartyInviteFromDatabase(inviterId, playerId);
                throw new IllegalStateException("派对不存在");
            }

            Party party = parties.get(partyId);
            if (party == null) {
                invites.remove(inviterId);
                if (stamps != null) stamps.remove(inviterId);
                deletePartyInviteFromDatabase(inviterId, playerId);
                throw new IllegalStateException("派对不存在");
            }

            // 检查是否已满（移除 invites 之前再次校验，避免并发超员）
            if (party.isFull()) {
                throw new IllegalStateException("派对已满");
            }

            // 移除邀请（addMember 之前，避免加入失败后邀请残留）
            invites.remove(inviterId);
            if (stamps != null) stamps.remove(inviterId);

            // 加入派对
            party.addMember(playerId);
            playerParties.put(playerId, partyId);

            savePartyToDatabase(party);
            deletePartyInviteFromDatabase(inviterId, playerId);

            return true;
        }
    }

    /**
     * 拒绝邀请
     */
    public boolean rejectInvite(UUID playerId, UUID inviterId) {
        Set<UUID> invites = partyInvites.get(playerId);
        if (invites != null) {
            return invites.remove(inviterId);
        }
        return false;
    }

    /**
     * 踢出成员
     */
    public boolean kickMember(UUID partyId, UUID kickerId, UUID targetId) {
        Party party = parties.get(partyId);
        if (party == null) return false;

        // 只有队长可以踢人
        if (!party.isLeader(kickerId)) {
            throw new IllegalStateException("只有队长可以踢出成员");
        }

        // 不能踢出队长自己
        if (party.isLeader(targetId)) {
            throw new IllegalStateException("不能踢出队长");
        }

        // 不能踢自己（用 leave）
        if (kickerId.equals(targetId)) {
            throw new IllegalStateException("不能踢出自己，请使用离开命令");
        }

        party.removeMember(targetId);
        playerParties.remove(targetId);

        savePartyToDatabase(party);
        // 清理踢出成员的遗留邀请
        Set<UUID> invites = partyInvites.get(targetId);
        if (invites != null) {
            for (UUID inviter : invites) {
                deletePartyInviteFromDatabase(inviter, targetId);
            }
            partyInvites.remove(targetId);
            partyInviteTimestamps.remove(targetId);
        }

        return true;
    }

    /**
     * 离开派对
     */
    public boolean leaveParty(UUID playerId) {
        UUID partyId = playerParties.get(playerId);
        if (partyId == null) return false;

        Party party = parties.get(partyId);
        if (party == null) return false;

        // 如果是队长，转让或解散
        if (party.isLeader(playerId)) {
            if (party.getMemberCount() > 1) {
                // 优先转让给在线且最近活跃的成员
                UUID newLeader = party.getMembers().stream()
                    .filter(id -> !id.equals(playerId))
                    .min(Comparator.comparingLong(id -> -lastOnlineTime(id)))
                    .orElse(null);

                if (newLeader != null) {
                    party.transferLeadership(newLeader);
                    savePartyToDatabase(party);
                } else {
                    parties.remove(partyId);
                    deletePartyFromDatabase(partyId);
                }
            } else {
                // 解散派对
                parties.remove(partyId);
                deletePartyFromDatabase(partyId);
            }
        } else {
            party.removeMember(playerId);
            savePartyToDatabase(party);
        }

        playerParties.remove(playerId);
        // 清理该玩家收到/发出的派对邀请
        Set<UUID> myInvites = partyInvites.get(playerId);
        if (myInvites != null) {
            for (UUID inviter : myInvites) {
                deletePartyInviteFromDatabase(inviter, playerId);
            }
            partyInvites.remove(playerId);
        }
        partyInviteTimestamps.remove(playerId);

        return true;
    }

    /** 获取玩家最近在线时间（在线=now，离线使用 Bukkit 离线记录）；最后在线越晚越优先 */
    private long lastOnlineTime(UUID id) {
        if (org.bukkit.Bukkit.getPlayer(id) != null) return System.currentTimeMillis();
        return org.bukkit.Bukkit.getOfflinePlayer(id).getLastPlayed();
    }

    /**
     * 转让队长
     */
    public boolean transferLeadership(UUID partyId, UUID currentLeader, UUID newLeader) {
        Party party = parties.get(partyId);
        if (party == null) return false;

        // 只有队长可以转让
        if (!party.isLeader(currentLeader)) {
            throw new IllegalStateException("只有队长可以转让职位");
        }

        // 新队长必须是成员
        if (!party.isMember(newLeader)) {
            throw new IllegalStateException("新队长必须是派对成员");
        }

        party.transferLeadership(newLeader);
        savePartyToDatabase(party);
        return true;
    }

    /**
     * 获取玩家派对
     */
    public Party getPlayerParty(UUID playerId) {
        UUID partyId = playerParties.get(playerId);
        return partyId != null ? parties.get(partyId) : null;
    }

    /**
     * 检查是否在同一派对
     */
    public boolean areInSameParty(UUID player1, UUID player2) {
        UUID party1 = playerParties.get(player1);
        UUID party2 = playerParties.get(player2);
        return party1 != null && party1.equals(party2);
    }

    /**
     * 获取派对邀请列表
     */
    public Set<UUID> getPartyInvites(UUID playerId) {
        Set<UUID> invites = partyInvites.get(playerId);
        return invites != null ? new HashSet<>(invites) : Collections.emptySet();
    }

    /**
     * 设置友军伤害
     */
    public boolean setFriendlyFire(UUID partyId, UUID playerId, boolean enabled) {
        Party party = parties.get(partyId);
        if (party == null) return false;

        if (!party.isLeader(playerId)) {
            throw new IllegalStateException("只有队长可以修改设置");
        }

        party.setFriendlyFire(enabled);
        return true;
    }

    /**
     * 设置经验共享
     */
    public boolean setExpShare(UUID partyId, UUID playerId, boolean enabled) {
        Party party = parties.get(partyId);
        if (party == null) return false;

        if (!party.isLeader(playerId)) {
            throw new IllegalStateException("只有队长可以修改设置");
        }

        party.setExpShare(enabled);
        return true;
    }

    /**
     * 获取所有派对
     */
    public Collection<Party> getAllParties() {
        return new ArrayList<>(parties.values());
    }
}
