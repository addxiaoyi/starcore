package dev.starcore.starcore.clan;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Clan管理器
 * 管理所有Clan的创建、查询、关系、邀请系统
 */
public class ClanManager {

    // Clan存储
    private final Map<UUID, Clan> clans = new ConcurrentHashMap<>();

    // 标签索引（快速查找）
    private final Map<String, UUID> tagIndex = new ConcurrentHashMap<>();

    // 玩家Clan映射
    private final Map<UUID, UUID> playerClanMap = new ConcurrentHashMap<>();

    // 邀请系统（目标UUID -> 邀请列表）
    private final Map<UUID, Set<ClanInvite>> clanInvites = new ConcurrentHashMap<>();

    // 玩家聊天模式。D-092: 改为 ConcurrentHashMap.newKeySet()，避免并发 toggle 触发 CME
    private final Set<UUID> clanChatEnabled = ConcurrentHashMap.newKeySet();

    // 持久化
    private Plugin plugin;

    /**
     * 设置插件引用（用于持久化）
     */
    public void setPlugin(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 保存数据到文件
     */
    public void saveData() {
        if (plugin == null) return;
        try {
            File file = new File(plugin.getDataFolder(), "clan_data.json");
            ClanDataStore store = new ClanDataStore();

            // 序列化所有Clan
            for (Clan clan : clans.values()) {
                store.clans.add(serializeClan(clan));
            }

            com.google.gson.Gson gson = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
            try (java.io.FileWriter writer = new java.io.FileWriter(file)) {
                gson.toJson(store, writer);
            }
        } catch (IOException e) {
            plugin.getLogger().warning("保存Clan数据失败: " + e.getMessage());
        }
    }

    /**
     * 加载数据从文件
     */
    public void loadData() {
        if (plugin == null) return;
        File file = new File(plugin.getDataFolder(), "clan_data.json");
        if (!file.exists()) return;

        try {
            com.google.gson.Gson gson = new com.google.gson.GsonBuilder().create();
            try (java.io.FileReader reader = new java.io.FileReader(file)) {
                ClanDataStore store = gson.fromJson(reader, ClanDataStore.class);
                if (store != null && store.clans != null) {
                    for (ClanData data : store.clans) {
                        Clan clan = deserializeClan(data);
                        if (clan != null) {
                            clans.put(clan.getId(), clan);
                            tagIndex.put(clan.getTag().toLowerCase(), clan.getId());
                            for (UUID memberId : clan.getMembers()) {
                                playerClanMap.put(memberId, clan.getId());
                            }
                        }
                    }
                }
            }
            plugin.getLogger().info("已加载 " + clans.size() + " 个Clan");
        } catch (Exception e) {
            plugin.getLogger().warning("加载Clan数据失败: " + e.getMessage());
        }
    }

    // ==================== 数据序列化 ====================

    private ClanData serializeClan(Clan clan) {
        ClanData data = new ClanData();
        data.id = clan.getId().toString();
        data.tag = clan.getTag();
        data.name = clan.getName();
        data.leader = clan.getLeader().toString();
        data.nationId = clan.getNationId() != null ? clan.getNationId().toString() : null;

        data.members = clan.getMembers().stream().map(UUID::toString).collect(Collectors.toList());
        data.ranks = new HashMap<>();
        clan.getMemberRanks().forEach((id, rank) -> data.ranks.put(id.toString(), rank.name()));

        data.allies = clan.getAllies().stream().map(UUID::toString).collect(Collectors.toList());
        data.rivals = clan.getRivals().stream().map(UUID::toString).collect(Collectors.toList());

        data.kills = clan.getKills();
        data.deaths = clan.getDeaths();
        data.balance = clan.getBalance();
        data.friendlyFire = clan.isFriendlyFire();
        data.pvpEnabled = clan.isPvpEnabled();
        data.createdTime = clan.getCreatedTime();
        data.lastActiveTime = clan.getLastActiveTime();

        // 据点位置
        if (clan.hasHome()) {
            Location home = clan.getHome();
            data.homeWorld = home.getWorld().getName();
            data.homeX = home.getX();
            data.homeY = home.getY();
            data.homeZ = home.getZ();
            data.homeYaw = home.getYaw();
            data.homePitch = home.getPitch();
        }

        return data;
    }

    private Clan deserializeClan(ClanData data) {
        try {
            UUID id = UUID.fromString(data.id);
            UUID leader = UUID.fromString(data.leader);

            Clan clan = new Clan(id, data.tag, data.name, leader);

            if (data.nationId != null) {
                clan.setNationId(UUID.fromString(data.nationId));
            }

            // 成员（领导者已自动添加）
            for (String memberId : data.members) {
                UUID mId = UUID.fromString(memberId);
                if (!mId.equals(leader)) {
                    clan.getMembers().add(mId);
                }
            }

            // 职位
            if (data.ranks != null) {
                data.ranks.forEach((idStr, rankName) -> {
                    try {
                        UUID mId = UUID.fromString(idStr);
                        Clan.ClanRank rank = Clan.ClanRank.valueOf(rankName);
                        clan.setMemberRank(mId, rank);
                    } catch (Exception e) {
                        // 静默跳过无效的职位数据，保持向后兼容
                    }
                });
            }

            // 盟友
            if (data.allies != null) {
                for (String allyId : data.allies) {
                    clan.addAlly(UUID.fromString(allyId));
                }
            }

            // 敌对
            if (data.rivals != null) {
                for (String rivalId : data.rivals) {
                    clan.addRival(UUID.fromString(rivalId));
                }
            }

            // 统计
            for (int i = 0; i < data.kills; i++) clan.addKill();
            for (int i = 0; i < data.deaths; i++) clan.addDeath();

            clan.deposit(data.balance);
            clan.setFriendlyFire(data.friendlyFire);
            clan.setPvpEnabled(data.pvpEnabled);

            // 据点
            if (data.homeWorld != null) {
                org.bukkit.World world = Bukkit.getWorld(data.homeWorld);
                if (world != null) {
                    Location home = new Location(world, data.homeX, data.homeY, data.homeZ, data.homeYaw, data.homePitch);
                    clan.setHome(home);
                }
            }

            return clan;
        } catch (Exception e) {
            if (plugin != null) {
                plugin.getLogger().warning("反序列化Clan失败: " + e.getMessage());
            }
            return null;
        }
    }

    // ==================== 创建/解散 ====================

    /**
     * 创建Clan
     */
    public Clan createClan(String tag, String name, UUID leaderId) {
        // 验证标签
        if (!isValidTag(tag)) {
            return null;
        }

        // 检查标签是否已存在
        if (tagIndex.containsKey(tag.toLowerCase())) {
            return null;
        }

        // 检查玩家是否已有Clan
        if (playerClanMap.containsKey(leaderId)) {
            return null;
        }

        // 创建Clan
        UUID clanId = UUID.randomUUID();
        Clan clan = new Clan(clanId, tag, name, leaderId);

        // 注册
        clans.put(clanId, clan);
        tagIndex.put(tag.toLowerCase(), clanId);
        playerClanMap.put(leaderId, clanId);

        // 自动保存
        saveData();

        return clan;
    }

    /**
     * 解散Clan
     * D-097: 加入 requesterId 校验，确保只有会长可解散，防止任意调用者凭 clanId 解散他人公会
     */
    public boolean disbandClan(UUID requesterId, UUID clanId) {
        Clan clan = clans.get(clanId);
        if (clan == null) {
            return false;
        }

        // 校验请求者是否为会长
        if (!clan.isLeader(requesterId)) {
            return false;
        }

        // 移除索引
        tagIndex.remove(clan.getTag().toLowerCase());

        // 移除所有成员映射
        for (UUID memberId : clan.getMembers()) {
            playerClanMap.remove(memberId);
            clanInvites.remove(memberId); // 清除邀请
        }

        clans.remove(clanId);
        saveData();
        return true;
    }

    /**
     * 获取Clan（通过ID）
     */
    public Clan getClan(UUID clanId) {
        return clans.get(clanId);
    }

    /**
     * 获取Clan（通过标签）
     */
    public Clan getClanByTag(String tag) {
        UUID clanId = tagIndex.get(tag.toLowerCase());
        return clanId != null ? clans.get(clanId) : null;
    }

    /**
     * 获取玩家的Clan
     */
    public Clan getPlayerClan(UUID playerId) {
        UUID clanId = playerClanMap.get(playerId);
        return clanId != null ? clans.get(clanId) : null;
    }

    /**
     * 获取玩家的Clan
     */
    public Clan getPlayerClan(Player player) {
        return getPlayerClan(player.getUniqueId());
    }

    // ==================== 成员管理 ====================

    /**
     * 玩家加入Clan
     */
    public boolean joinClan(UUID playerId, UUID clanId) {
        // 检查玩家是否已有Clan
        if (playerClanMap.containsKey(playerId)) {
            return false;
        }

        Clan clan = clans.get(clanId);
        if (clan == null) {
            return false;
        }

        // 添加成员
        if (clan.addMember(playerId)) {
            playerClanMap.put(playerId, clanId);
            clanInvites.remove(playerId); // 清除邀请
            saveData();
            return true;
        }

        return false;
    }

    /**
     * 玩家离开Clan
     */
    public boolean leaveClan(UUID playerId) {
        UUID clanId = playerClanMap.remove(playerId);
        if (clanId == null) {
            return false;
        }

        Clan clan = clans.get(clanId);
        if (clan == null) {
            return false;
        }

        // 如果是领导者离开
        if (clan.isLeader(playerId)) {
            // 如果只剩领导者一人，解散Clan
            if (clan.getMemberCount() <= 1) {
                disbandClan(playerId, clanId);
                return true;
            }

            // 否则转让给第一个成员
            UUID newLeader = clan.getMembers().stream()
                .filter(id -> !id.equals(playerId))
                .findFirst()
                .orElse(null);

            if (newLeader != null) {
                clan.transferLeadership(newLeader);
            }
        }

        // 移除成员
        clan.removeMember(playerId);

        saveData();
        return true;
    }

    /**
     * 踢出成员
     */
    public boolean kickMember(UUID leaderId, UUID targetId) {
        Clan clan = getPlayerClan(leaderId);
        if (clan == null || !clan.isLeader(leaderId)) {
            return false;
        }

        if (!clan.isMember(targetId)) {
            return false;
        }

        // 移除成员
        clan.removeMember(targetId);
        playerClanMap.remove(targetId);

        saveData();
        return true;
    }

    /**
     * 转让族长
     */
    public boolean transferLeader(UUID leaderId, UUID newLeaderId) {
        Clan clan = getPlayerClan(leaderId);
        if (clan == null || !clan.isLeader(leaderId)) {
            return false;
        }

        if (!clan.isMember(newLeaderId)) {
            return false;
        }

        clan.transferLeadership(newLeaderId);
        saveData();
        return true;
    }

    // ==================== 邀请系统 ====================

    /**
     * 邀请玩家加入Clan
     */
    public boolean invitePlayer(UUID inviterId, UUID targetId) {
        Clan clan = getPlayerClan(inviterId);
        if (clan == null) {
            return false;
        }

        // 检查权限
        if (!clan.hasPermission(inviterId, Clan.ClanPermission.INVITE)) {
            return false;
        }

        // 检查目标是否已有Clan
        if (playerClanMap.containsKey(targetId)) {
            return false;
        }

        // 检查是否已有邀请
        Set<ClanInvite> invites = clanInvites.get(targetId);
        if (invites != null) {
            for (ClanInvite invite : invites) {
                if (invite.clanId().equals(clan.getId())) {
                    return false; // 已邀请
                }
            }
        }

        // 创建邀请
        ClanInvite invite = new ClanInvite(clan.getId(), inviterId, targetId, System.currentTimeMillis());
        clanInvites.computeIfAbsent(targetId, k -> ConcurrentHashMap.newKeySet()).add(invite);

        return true;
    }

    /**
     * 获取玩家的Clan邀请（自动清理过期）
     * D-099: 每次获取时清理过期邀请（30分钟TTL），避免离线玩家邀请永久堆积
     */
    public Set<ClanInvite> getInvites(UUID playerId) {
        Set<ClanInvite> invites = clanInvites.get(playerId);
        if (invites == null) return Collections.emptySet();

        // 清理过期邀请
        long now = System.currentTimeMillis();
        long TTL_MS = 30 * 60 * 1000L;
        invites.removeIf(inv -> (now - inv.timestamp()) > TTL_MS);
        if (invites.isEmpty()) {
            clanInvites.remove(playerId);
        }
        return new HashSet<>(invites);
    }

    /**
     * 手动清理所有过期邀请（供定时任务调用）
     */
    public void cleanupExpiredInvites() {
        long now = System.currentTimeMillis();
        long TTL_MS = 30 * 60 * 1000L;
        for (Map.Entry<UUID, Set<ClanInvite>> entry : clanInvites.entrySet()) {
            entry.getValue().removeIf(inv -> (now - inv.timestamp()) > TTL_MS);
            if (entry.getValue().isEmpty()) {
                clanInvites.remove(entry.getKey());
            }
        }
    }

    /**
     * 接受Clan邀请
     */
    public boolean acceptInvite(UUID playerId, UUID clanId) {
        Clan clan = clans.get(clanId);
        if (clan == null) {
            return false;
        }

        // 检查邀请是否存在
        Set<ClanInvite> invites = clanInvites.get(playerId);
        if (invites == null) {
            return false;
        }

        ClanInvite foundInvite = null;
        for (ClanInvite invite : invites) {
            if (invite.clanId().equals(clanId)) {
                foundInvite = invite;
                break;
            }
        }

        if (foundInvite == null) {
            return false;
        }

        // 检查玩家是否已有Clan
        if (playerClanMap.containsKey(playerId)) {
            return false;
        }

        // 移除邀请
        invites.remove(foundInvite);
        if (invites.isEmpty()) {
            clanInvites.remove(playerId);
        }

        // 添加成员
        clan.addMember(playerId);
        playerClanMap.put(playerId, clanId);

        saveData();
        return true;
    }

    /**
     * 拒绝Clan邀请
     */
    public boolean rejectInvite(UUID playerId, UUID clanId) {
        Set<ClanInvite> invites = clanInvites.get(playerId);
        if (invites == null) {
            return false;
        }

        boolean removed = invites.removeIf(invite -> invite.clanId().equals(clanId));
        if (invites.isEmpty()) {
            clanInvites.remove(playerId);
        }

        return removed;
    }

    /**
     * 根据Clan标签接受邀请
     */
    public Clan acceptInviteByTag(UUID playerId, String clanTag) {
        Clan clan = getClanByTag(clanTag);
        if (clan != null && acceptInvite(playerId, clan.getId())) {
            return clan;
        }
        return null;
    }

    /**
     * 根据Clan标签拒绝邀请
     */
    public boolean rejectInviteByTag(UUID playerId, String clanTag) {
        Clan clan = getClanByTag(clanTag);
        if (clan != null) {
            return rejectInvite(playerId, clan.getId());
        }
        return false;
    }

    // ==================== 据点 ====================

    /**
     * 设置Clan据点
     */
    public boolean setHome(UUID playerId, Location location) {
        Clan clan = getPlayerClan(playerId);
        if (clan == null) {
            return false;
        }

        if (!clan.hasPermission(playerId, Clan.ClanPermission.SET_HOME)) {
            return false;
        }

        clan.setHome(location);
        saveData();
        return true;
    }

    /**
     * 传送到Clan据点
     * D-093: 原实现仅 return clan.hasHome()，未实际传送玩家。这里补全传送逻辑。
     */
    public boolean teleportToHome(Player player) {
        UUID playerId = player.getUniqueId();
        Clan clan = getPlayerClan(playerId);
        if (clan == null) {
            return false;
        }

        if (!clan.hasPermission(playerId, Clan.ClanPermission.HOME)) {
            return false;
        }

        if (!clan.hasHome()) {
            return false;
        }

        Location home = clan.getHome();
        if (home == null || home.getWorld() == null) {
            return false;
        }

        player.teleport(home);
        return true;
    }

    /**
     * 获取Clan据点
     */
    public Location getHome(UUID clanId) {
        Clan clan = clans.get(clanId);
        return clan != null ? clan.getHome() : null;
    }

    // ==================== 设置 ====================

    /**
     * 设置友军伤害
     */
    public boolean setFriendlyFire(UUID playerId, boolean enabled) {
        Clan clan = getPlayerClan(playerId);
        if (clan == null) {
            return false;
        }

        if (!clan.hasPermission(playerId, Clan.ClanPermission.SETTINGS)) {
            return false;
        }

        clan.setFriendlyFire(enabled);
        saveData();
        return true;
    }

    /**
     * 设置PvP
     */
    public boolean setPvPEnabled(UUID playerId, boolean enabled) {
        Clan clan = getPlayerClan(playerId);
        if (clan == null) {
            return false;
        }

        if (!clan.hasPermission(playerId, Clan.ClanPermission.SETTINGS)) {
            return false;
        }

        clan.setPvpEnabled(enabled);
        saveData();
        return true;
    }

    // ==================== 经济 ====================

    /**
     * 存款到Clan银行（简化版，不检查玩家余额）
     * 完整的经济检查由 ClanCommand 处理
     */
    public void deposit(UUID playerId, double amount) {
        Clan clan = getPlayerClan(playerId);
        if (clan != null && amount > 0) {
            clan.deposit(amount);
            saveData();
        }
    }

    /**
     * 从Clan银行取款
     */
    public boolean withdraw(UUID playerId, double amount) {
        Clan clan = getPlayerClan(playerId);
        if (clan == null) {
            return false;
        }

        if (!clan.hasPermission(playerId, Clan.ClanPermission.WITHDRAW)) {
            return false;
        }

        if (clan.withdraw(amount)) {
            saveData();
            return true;
        }

        return false;
    }

    /**
     * 获取Clan余额
     */
    public double getBalance(UUID clanId) {
        Clan clan = clans.get(clanId);
        return clan != null ? clan.getBalance() : 0;
    }

    // ==================== 标签 ====================

    /**
     * 修改Clan标签
     */
    public boolean setTag(UUID playerId, String newTag) {
        Clan clan = getPlayerClan(playerId);
        if (clan == null) {
            return false;
        }

        if (!clan.isLeader(playerId)) {
            return false;
        }

        if (!isValidTag(newTag)) {
            return false;
        }

        // 检查标签是否已被使用
        if (tagIndex.containsKey(newTag.toLowerCase()) && !tagIndex.get(newTag.toLowerCase()).equals(clan.getId())) {
            return false;
        }

        // 更新索引
        tagIndex.remove(clan.getTag().toLowerCase());
        clan.setTag(newTag);
        tagIndex.put(newTag.toLowerCase(), clan.getId());

        saveData();
        return true;
    }

    // ==================== 权限管理 ====================

    /**
     * 设置成员职位
     */
    public boolean setRank(UUID leaderId, UUID targetId, Clan.ClanRank rank) {
        Clan clan = getPlayerClan(leaderId);
        if (clan == null || !clan.isLeader(leaderId)) {
            return false;
        }

        if (!clan.isMember(targetId)) {
            return false;
        }

        if (rank == Clan.ClanRank.LEADER) {
            return false; // 不能用此方法设置族长
        }

        clan.setRank(targetId, rank);
        saveData();
        return true;
    }

    // ==================== 联盟 ====================

    /**
     * 解除联盟
     */
    public boolean removeAlly(UUID playerId, String targetTag) {
        Clan clan = getPlayerClan(playerId);
        if (clan == null || !clan.isLeader(playerId)) {
            return false;
        }

        Clan targetClan = getClanByTag(targetTag);
        if (targetClan == null) {
            return false;
        }

        // 双向移除
        clan.removeAlly(targetClan.getId());
        targetClan.removeAlly(clan.getId());

        saveData();
        return true;
    }

    // ==================== 聊天 ====================

    /**
     * 切换Clan聊天模式
     */
    public boolean toggleClanChat(UUID playerId) {
        Clan clan = getPlayerClan(playerId);
        if (clan == null) {
            return false;
        }

        if (clanChatEnabled.contains(playerId)) {
            clanChatEnabled.remove(playerId);
            return false;
        } else {
            clanChatEnabled.add(playerId);
            return true;
        }
    }

    /**
     * 是否启用Clan聊天
     */
    public boolean isClanChatEnabled(UUID playerId) {
        return clanChatEnabled.contains(playerId);
    }

    /**
     * 广播消息到Clan
     */
    public void broadcastToClan(UUID clanId, String message) {
        Clan clan = clans.get(clanId);
        if (clan == null) return;

        for (UUID memberId : clan.getMembers()) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null && member.isOnline()) {
                member.sendMessage(message);
            }
        }
    }

    // ==================== 验证 ====================

    /**
     * 验证标签
     */
    private boolean isValidTag(String tag) {
        if (tag == null || tag.isEmpty()) {
            return false;
        }

        // 3-4个字符
        if (tag.length() < 3 || tag.length() > 4) {
            return false;
        }

        // 只允许字母和数字
        return tag.matches("[a-zA-Z0-9]+");
    }

    /**
     * 获取所有Clan
     */
    public Collection<Clan> getAllClans() {
        return Collections.unmodifiableCollection(clans.values());
    }

    /**
     * 获取Clan总数
     */
    public int getClanCount() {
        return clans.size();
    }

    /**
     * 获取总成员数
     */
    public int getTotalMembers() {
        return playerClanMap.size();
    }

    /**
     * 根据KDR排序
     */
    public List<Clan> getTopClansByKDR(int limit) {
        return clans.values().stream()
            .sorted((a, b) -> Double.compare(b.getKDR(), a.getKDR()))
            .limit(limit)
            .toList();
    }

    /**
     * 根据成员数排序
     */
    public List<Clan> getTopClansByMembers(int limit) {
        return clans.values().stream()
            .sorted((a, b) -> Integer.compare(b.getMemberCount(), a.getMemberCount()))
            .limit(limit)
            .toList();
    }

    /**
     * 检查两个Clan是否为盟友
     */
    public boolean areAllies(UUID clan1Id, UUID clan2Id) {
        Clan clan1 = clans.get(clan1Id);
        Clan clan2 = clans.get(clan2Id);

        if (clan1 == null || clan2 == null) {
            return false;
        }

        // 双向确认
        return clan1.isAlly(clan2Id) && clan2.isAlly(clan1Id);
    }

    /**
     * 检查两个Clan是否为敌对
     */
    public boolean areRivals(UUID clan1Id, UUID clan2Id) {
        Clan clan1 = clans.get(clan1Id);
        Clan clan2 = clans.get(clan2Id);

        if (clan1 == null || clan2 == null) {
            return false;
        }

        // 单向即可
        return clan1.isRival(clan2Id) || clan2.isRival(clan1Id);
    }

    /**
     * 建立联盟（双向）
     * D-098: 加入 actorId 权限校验，只有联盟成员才能建立/解除联盟
     */
    public boolean createAlliance(UUID actorId, UUID clan1Id, UUID clan2Id) {
        Clan clan1 = clans.get(clan1Id);
        Clan clan2 = clans.get(clan2Id);

        if (clan1 == null || clan2 == null) {
            return false;
        }

        // 权限校验：调用者必须是 clan1 的成员
        if (!clan1.isMember(actorId)) {
            return false;
        }

        clan1.addAlly(clan2Id);
        clan2.addAlly(clan1Id);
        saveData();
        return true;
    }

    /**
     * 解除联盟（双向）
     * D-098: 加入 actorId 权限校验
     */
    public boolean removeAlliance(UUID actorId, UUID clan1Id, UUID clan2Id) {
        Clan clan1 = clans.get(clan1Id);
        Clan clan2 = clans.get(clan2Id);

        if (clan1 == null || clan2 == null) {
            return false;
        }

        // 权限校验：调用者必须是 clan1 的成员
        if (!clan1.isMember(actorId)) {
            return false;
        }

        clan1.removeAlly(clan2Id);
        clan2.removeAlly(clan1Id);
        saveData();
        return true;
    }

    /**
     * 宣布敌对（单向）
     */
    public void declareRivalry(UUID clan1Id, UUID clan2Id) {
        Clan clan1 = clans.get(clan1Id);
        if (clan1 != null) {
            clan1.addRival(clan2Id);
            saveData();
        }
    }

    /**
     * 获取统计信息
     */
    public ClanStats getStats() {
        int totalKills = 0;
        int totalDeaths = 0;
        int activeClans = 0;

        for (Clan clan : clans.values()) {
            totalKills += clan.getKills();
            totalDeaths += clan.getDeaths();
            if (clan.isActive()) {
                activeClans++;
            }
        }

        return new ClanStats(
            clans.size(),
            playerClanMap.size(),
            totalKills,
            totalDeaths,
            activeClans
        );
    }

    /**
     * 统计信息记录
     */
    public record ClanStats(
        int totalClans,
        int totalMembers,
        int totalKills,
        int totalDeaths,
        int activeClans
    ) {
        @Override
        public String toString() {
            return String.format(
                "ClanStats[clans=%d, members=%d, kills=%d, deaths=%d, active=%d]",
                totalClans, totalMembers, totalKills, totalDeaths, activeClans
            );
        }
    }

    /**
     * 邀请记录
     */
    public record ClanInvite(
        UUID clanId,
        UUID inviterId,
        UUID targetId,
        long timestamp
    ) {}

    // ==================== 数据类 ====================

    private static class ClanDataStore {
        List<ClanData> clans = new ArrayList<>();
    }

    private static class ClanData {
        String id;
        String tag;
        String name;
        String leader;
        String nationId;
        List<String> members;
        Map<String, String> ranks;
        List<String> allies;
        List<String> rivals;
        int kills;
        int deaths;
        double balance;
        boolean friendlyFire;
        boolean pvpEnabled;
        long createdTime;
        long lastActiveTime;
        // 据点
        String homeWorld;
        double homeX, homeY, homeZ;
        float homeYaw, homePitch;
    }
}
