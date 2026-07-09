package dev.starcore.starcore.social.guild;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

/**
 * 公会服务（星座系统）- 支持持久化和邀请请求
 *
 * 支持两种持久化模式：
 * 1. YAML 文件持久化（默认，兼容性更好）
 * 2. 数据库持久化（通过 SqlSocialStateStorage）
 */
public final class GuildService {
    // 所有公会（公会ID -> 公会）
    private final Map<UUID, Guild> guilds = new ConcurrentHashMap<>();

    // 玩家公会映射（玩家UUID -> 公会ID）
    private final Map<UUID, UUID> playerGuilds = new ConcurrentHashMap<>();

    // 公会名称映射（名称 -> 公会ID）
    private final Map<String, UUID> guildNames = new ConcurrentHashMap<>();

    // 公会邀请（目标UUID -> Set<邀请信息>)
    private final Map<UUID, Set<GuildInvite>> guildInvites = new ConcurrentHashMap<>();

    // 持久化相关
    private Plugin plugin;
    private File guildsFile;

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
        this.guildsFile = new File(dir, "guilds.yml");
    }

    /**
     * 加载公会数据（自动选择存储方式）
     */
    public void loadGuilds() {
        if (isDatabaseAvailable()) {
            loadFromDatabase();
        } else {
            loadFromYaml();
        }
    }

    /**
     * 从 YAML 加载公会数据
     */
    public void loadFromYaml() {
        if (guildsFile == null || !guildsFile.exists()) return;
        try {
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(guildsFile);
            for (String key : yml.getKeys(false)) {
                UUID guildId;
                try {
                    guildId = UUID.fromString(key);
                } catch (IllegalArgumentException ex) {
                    continue;
                }
                Object dataObj = yml.get(key);
                if (dataObj instanceof GuildData data && data.getId() != null) {
                    Guild guild = data.toGuild();
                    guilds.put(guildId, guild);
                    guildNames.put(guild.getName().toLowerCase(), guildId);
                    for (UUID memberId : guild.getMembers()) {
                        playerGuilds.put(memberId, guildId);
                    }
                }
            }
            plugin.getLogger().info("已从YAML加载 " + guilds.size() + " 个公会");
        } catch (Exception ex) {
            if (plugin != null) {
                plugin.getLogger().warning("加载公会数据失败: " + ex.getMessage());
            }
        }
    }

    /**
     * 从数据库加载公会数据
     */
    public void loadFromDatabase() {
        if (sqlStorage != null) {
            sqlStorage.loadAllGuilds(guilds, guildNames, playerGuilds);
            sqlStorage.loadAllGuildInvites(guildInvites);
            plugin.getLogger().info("已从数据库加载 " + guilds.size() + " 个公会");
        }
    }

    /**
     * 保存公会数据（自动选择存储方式）
     */
    public void saveGuilds() {
        if (isDatabaseAvailable()) {
            saveToDatabase();
        } else {
            saveToYaml();
        }
    }

    /**
     * 保存公会数据到 YAML
     */
    public void saveToYaml() {
        if (guildsFile == null) return;
        try {
            YamlConfiguration yml = new YamlConfiguration();
            for (Guild guild : guilds.values()) {
                GuildData data = GuildData.fromGuild(guild);
                yml.set(guild.getId().toString(), data);
            }
            yml.save(guildsFile);
        } catch (IOException ex) {
            if (plugin != null) {
                plugin.getLogger().warning("保存公会数据失败: " + ex.getMessage());
            }
        }
    }

    /**
     * 保存公会数据到数据库
     */
    public void saveToDatabase() {
        if (sqlStorage != null) {
            sqlStorage.saveAllGuilds(guilds.values());
        }
    }

    /**
     * 保存单个公会到数据库
     */
    public void saveGuildToDatabase(Guild guild) {
        if (sqlStorage != null) {
            sqlStorage.saveGuild(guild);
        }
    }

    /**
     * 从数据库删除公会
     */
    public void deleteGuildFromDatabase(UUID guildId) {
        if (sqlStorage != null) {
            sqlStorage.deleteGuild(guildId);
        }
    }

    /**
     * 保存公会邀请到数据库
     */
    public void saveGuildInviteToDatabase(GuildInvite invite) {
        if (sqlStorage != null) {
            sqlStorage.saveGuildInvite(invite.guildId(), invite.inviterId(), invite.targetId(), invite.timestamp());
        }
    }

    /**
     * 从数据库删除公会邀请
     */
    public void deleteGuildInviteFromDatabase(UUID guildId, UUID targetId) {
        if (sqlStorage != null) {
            sqlStorage.deleteGuildInvite(guildId, targetId);
        }
    }

    /**
     * 创建公会
     */
    public Guild createGuild(UUID creatorId, String name, String tag) {
        // 检查名称是否已存在
        if (guildNames.containsKey(name.toLowerCase())) {
            throw new IllegalArgumentException("公会名称已存在");
        }

        // 检查玩家是否已有公会
        if (playerGuilds.containsKey(creatorId)) {
            throw new IllegalStateException("你已经在一个公会中");
        }

        // 创建公会
        UUID guildId = UUID.randomUUID();
        Guild guild = new Guild(guildId, name, tag, creatorId);

        // 保存
        guilds.put(guildId, guild);
        guildNames.put(name.toLowerCase(), guildId);
        playerGuilds.put(creatorId, guildId);

        // 单条即时持久化，避免宕机时段内创建丢失
        saveGuildToDatabase(guild);

        return guild;
    }

    /**
     * 解散公会
     */
    /**
     * 解散公会（带二次确认）。第一次调用标记 pendingDisband=true，需 confirm=true 才真正解散。
     */
    public boolean disbandGuild(UUID guildId, UUID requesterId) {
        return disbandGuild(guildId, requesterId, false);
    }

    /**
     * 解散公会（带二次确认）。
     * 第一次调用需要 confirm=false（仅标记意图），第二次调用 confirm=true 才真正执行。
     */
    public boolean disbandGuild(UUID guildId, UUID requesterId, boolean confirm) {
        Guild guild = guilds.get(guildId);
        if (guild == null) return false;

        // 只有会长可以解散
        if (!guild.getLeader().equals(requesterId)) {
            throw new IllegalStateException("只有会长可以解散公会");
        }

        if (!confirm) {
            // 仅标记意图，等待二次确认
            guild.requestDisband();
            throw new IllegalStateException("解散公会需二次确认：请再次执行 /guild disband confirm 确认解散，整公会所有数据将被清空。");
        }
        // 校验之前确实发起过解散意图
        if (!guild.isDisbandRequested()) {
            throw new IllegalStateException("请先执行一次解散命令发起解散意图。");
        }

        // 移除所有成员
        for (UUID memberId : guild.getMembers()) {
            playerGuilds.remove(memberId);
            // 清除该成员的所有公会邀请
            guildInvites.remove(memberId);
        }

        // 移除公会
        guilds.remove(guildId);
        guildNames.remove(guild.getName().toLowerCase());

        // 单条 DB 删除
        deleteGuildFromDatabase(guildId);

        return true;
    }

    /**
     * 邀请玩家（发送邀请请求）
     */
    public boolean inviteMember(UUID guildId, UUID inviterId, UUID targetId) {
        Guild guild = guilds.get(guildId);
        if (guild == null) return false;

        // 重新读取 inviter 当前角色，避免被踢后持旧权限对象 race
        if (!guild.isMember(inviterId) || !guild.canInvite(inviterId)) {
            throw new IllegalStateException("你没有邀请权限");
        }

        // 区分"已在自家公会"与"已在其他公会"
        UUID targetGuildId = playerGuilds.get(targetId);
        if (targetGuildId != null) {
            if (targetGuildId.equals(guildId)) {
                throw new IllegalStateException("该玩家已在本公会中");
            }
            throw new IllegalStateException("该玩家已在其他公会中");
        }

        // 检查是否已有邀请
        Set<GuildInvite> invites = guildInvites.get(targetId);
        if (invites != null) {
            for (GuildInvite invite : invites) {
                if (invite.guildId().equals(guildId)) {
                    throw new IllegalStateException("已向该玩家发送过邀请");
                }
            }
        }

        // 添加邀请
        GuildInvite invite = new GuildInvite(guildId, inviterId, targetId, System.currentTimeMillis());
        guildInvites.computeIfAbsent(targetId, k -> ConcurrentHashMap.newKeySet()).add(invite);

        saveGuildInviteToDatabase(invite);

        return true;
    }

    /**
     * 获取玩家的公会邀请
     */
    public Set<GuildInvite> getGuildInvites(UUID playerId) {
        Set<GuildInvite> invites = guildInvites.get(playerId);
        return invites != null ? new HashSet<>(invites) : Collections.emptySet();
    }

    /**
     * 接受公会邀请
     */
    public boolean acceptInvite(UUID playerId, UUID guildId) {
        Guild guild = guilds.get(guildId);
        if (guild == null) {
            throw new IllegalStateException("公会不存在");
        }

        // 检查邀请是否存在
        Set<GuildInvite> invites = guildInvites.get(playerId);
        if (invites == null) {
            throw new IllegalStateException("没有该公会的邀请");
        }

        GuildInvite foundInvite = null;
        for (GuildInvite invite : invites) {
            if (invite.guildId().equals(guildId)) {
                foundInvite = invite;
                break;
            }
        }

        if (foundInvite == null) {
            throw new IllegalStateException("没有该公会的邀请");
        }

        // 同步块防止满员并发进入
        synchronized (this) {
            // 校验邀请者依然是公会成员（公会可能被解散/邀请者已被踢）
            if (!guild.isMember(foundInvite.inviterId())) {
                // 邀请者已离开公会，邀请失效但仍可加入公会（除非已解散）
            }
            // 检查玩家是否已有公会
            if (playerGuilds.containsKey(playerId)) {
                throw new IllegalStateException("你已经在其他公会中");
            }
            // 上限校验（使用 20 作为默认上限）
            int maxMembers = 20; // 与 SqlSocialStateStorage 中 starcore_guilds.max_members 默认一致
            if (guild.getMemberCount() >= maxMembers) {
                throw new IllegalStateException("公会成员已满");
            }

            // 移除邀请
            invites.remove(foundInvite);
            if (invites.isEmpty()) {
                guildInvites.remove(playerId);
            }

            // 添加成员
            guild.addMember(playerId);
            playerGuilds.put(playerId, guildId);
        }

        // 单条持久化
        saveGuildToDatabase(guild);
        deleteGuildInviteFromDatabase(guildId, playerId);

        return true;
    }

    /**
     * 拒绝公会邀请
     */
    public boolean rejectInvite(UUID playerId, UUID guildId) {
        Set<GuildInvite> invites = guildInvites.get(playerId);
        if (invites == null) return false;

        boolean removed = invites.removeIf(invite -> invite.guildId().equals(guildId));
        if (invites.isEmpty()) {
            guildInvites.remove(playerId);
        }
        return removed;
    }

    /**
     * 踢出成员
     */
    public boolean kickMember(UUID guildId, UUID kickerId, UUID targetId) {
        Guild guild = guilds.get(guildId);
        if (guild == null) return false;

        // 检查权限
        if (!guild.canKick(kickerId)) {
            throw new IllegalStateException("你没有踢出权限");
        }

        // 不能踢出会长
        if (guild.getLeader().equals(targetId)) {
            throw new IllegalStateException("不能踢出会长");
        }

        // 不能踢出自己
        if (kickerId.equals(targetId)) {
            throw new IllegalStateException("不能踢出自己，请使用离开命令");
        }

        // 校验目标仍是成员
        if (!guild.isMember(targetId)) {
            throw new IllegalStateException("目标已不在公会中");
        }

        // 移除成员
        guild.removeMember(targetId);
        playerGuilds.remove(targetId);

        saveGuildToDatabase(guild);
        return true;
    }

    /**
     * 离开公会
     */
    public boolean leaveGuild(UUID playerId) {
        UUID guildId = playerGuilds.get(playerId);
        if (guildId == null) return false;

        Guild guild = guilds.get(guildId);
        if (guild == null) return false;

        // 会长不能直接离开，需要先转让会长
        if (guild.getLeader().equals(playerId)) {
            throw new IllegalStateException("会长需要先转让会长职位或解散公会");
        }

        guild.removeMember(playerId);
        playerGuilds.remove(playerId);

        saveGuildToDatabase(guild);
        return true;
    }

    /**
     * 转让会长
     */
    public boolean transferLeadership(UUID guildId, UUID currentLeaderId, UUID newLeaderId) {
        Guild guild = guilds.get(guildId);
        if (guild == null) return false;

        // 只有会长可以转让
        if (!guild.getLeader().equals(currentLeaderId)) {
            throw new IllegalStateException("只有会长可以转让职位");
        }

        // 与自身相等则拒绝
        if (currentLeaderId.equals(newLeaderId)) {
            throw new IllegalStateException("不能转让给自己");
        }

        // 新会长必须仍是成员（实时重读，避免已被踢导致转让给非成员）
        if (!guild.isMember(newLeaderId)) {
            throw new IllegalStateException("新会长必须是公会成员");
        }

        guild.setLeader(newLeaderId);

        // 单条持久化
        saveGuildToDatabase(guild);
        return true;
    }

    /**
     * 设置成员职位
     */
    public boolean setMemberRole(UUID guildId, UUID operatorId, UUID targetId, GuildRole role) {
        Guild guild = guilds.get(guildId);
        if (guild == null) return false;

        // 检查权限
        if (!guild.canManageRoles(operatorId)) {
            throw new IllegalStateException("你没有管理职位的权限");
        }

        guild.setMemberRole(targetId, role);

        saveGuildToDatabase(guild);
        return true;
    }

    /**
     * 获取玩家公会
     */
    public Guild getPlayerGuild(UUID playerId) {
        UUID guildId = playerGuilds.get(playerId);
        return guildId != null ? guilds.get(guildId) : null;
    }

    /**
     * 根据名称获取公会
     */
    public Guild getGuildByName(String name) {
        UUID guildId = guildNames.get(name.toLowerCase());
        return guildId != null ? guilds.get(guildId) : null;
    }

    /**
     * 获取所有公会
     */
    public Collection<Guild> getAllGuilds() {
        return new ArrayList<>(guilds.values());
    }

    /**
     * 增加公会经验
     */
    public void addGuildExperience(UUID guildId, int exp) {
        Guild guild = guilds.get(guildId);
        if (guild != null) {
            guild.addExperience(exp);
        }
    }

    /**
     * 获取公会排行榜
     */
    public List<Guild> getGuildLeaderboard(int limit) {
        return guilds.values().stream()
            .sorted((g1, g2) -> {
                // 主键 level 降序，平手时按经验降序，再按创建时间升序保证稳定
                int c = Integer.compare(g2.getLevel(), g1.getLevel());
                if (c != 0) return c;
                c = Integer.compare(g2.getExperience(), g1.getExperience());
                if (c != 0) return c;
                return Long.compare(g1.getCreatedTime(), g2.getCreatedTime());
            })
            .limit(limit)
            .toList();
    }

    /**
     * 公会邀请记录
     */
    public record GuildInvite(
        UUID guildId,
        UUID inviterId,
        UUID targetId,
        long timestamp
    ) {}

    // ========== 持久化支持（供 SocialModule 使用）==========
    // 注意：这些方法仅用于内部持久化操作

    /**
     * 获取所有公会的原始 Map（用于加载）
     */
    public Map<UUID, Guild> getAllGuildsRaw() {
        return guilds;
    }

    /**
     * 获取公会名称映射的原始 Map（用于加载）
     */
    public Map<String, UUID> getGuildNamesRaw() {
        return guildNames;
    }

    /**
     * 获取玩家公会映射的原始 Map（用于加载）
     */
    public Map<UUID, UUID> getPlayerGuildsRaw() {
        return playerGuilds;
    }
}
