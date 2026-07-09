package dev.starcore.starcore.social;

import dev.starcore.starcore.core.database.DatabaseService;
import dev.starcore.starcore.core.persistence.PersistenceService;
import dev.starcore.starcore.core.storage.AbstractModuleStateStorage;

import javax.sql.DataSource;
import java.sql.*;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * 社交系统 SQL 存储实现
 *
 * 支持：好友、公会、派对数据的数据库持久化
 */
public final class SqlSocialStateStorage {

    private final DatabaseService databaseService;
    private final Logger logger;

    public SqlSocialStateStorage(DatabaseService databaseService, Logger logger) {
        this.databaseService = databaseService;
        this.logger = logger;
    }

    private Connection getConnection() throws SQLException {
        return databaseService.dataSource()
            .orElseThrow(() -> new SQLException("Database not available"))
            .getConnection();
    }

    /**
     * 创建所有必要的表 (迁移脚本已完成，主要用于 SQLite 兼容性检查)
     */
    public void ensureTables() {
        // 表已在迁移脚本中创建 (V7 for MySQL, V10 for SQLite)
        // 这里仅检查表是否存在，不创建
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            // 检查是否需要 SQLite 兼容模式
            DatabaseMetaData meta = conn.getMetaData();
            String dbProduct = meta.getDatabaseProductName();
            boolean isSQLite = "SQLite".equalsIgnoreCase(dbProduct);

            if (isSQLite) {
                // SQLite 模式下，迁移脚本已创建表，这里仅验证
                try {
                    stmt.executeQuery("SELECT COUNT(*) FROM starcore_friend_relations");
                    logger.info("社交系统数据库表已存在 (SQLite)");
                } catch (SQLException e) {
                    // 表不存在，尝试创建 SQLite 兼容版本
                    createSqliteTables(stmt);
                }
            } else {
                // MySQL 模式下，迁移脚本已创建表
                logger.info("社交系统数据库表已初始化 (MySQL/其他)");
            }
        } catch (Exception e) {
            logger.warning("检查社交系统数据库表失败: " + e.getMessage());
        }
    }

    /**
     * 创建 SQLite 兼容的表
     */
    private void createSqliteTables(Statement stmt) throws SQLException {
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS starcore_friend_relations (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                player_uuid TEXT NOT NULL,
                friend_uuid TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )
        """);
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_friend_player ON starcore_friend_relations(player_uuid)");

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS starcore_friend_requests (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                sender_uuid TEXT NOT NULL,
                receiver_uuid TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                expires_at INTEGER NOT NULL,
                status TEXT NOT NULL DEFAULT 'PENDING'
            )
        """);
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_friend_req_receiver ON starcore_friend_requests(receiver_uuid, status)");

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS starcore_blacklist (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                player_uuid TEXT NOT NULL,
                blocked_uuid TEXT NOT NULL,
                reason TEXT,
                created_at INTEGER NOT NULL
            )
        """);

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS starcore_player_status (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                player_uuid TEXT NOT NULL UNIQUE,
                status_message TEXT,
                last_online INTEGER NOT NULL,
                online_status TEXT NOT NULL DEFAULT 'OFFLINE'
            )
        """);

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS starcore_guilds (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                guild_uuid TEXT NOT NULL UNIQUE,
                name TEXT NOT NULL UNIQUE,
                tag TEXT NOT NULL,
                leader_uuid TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                level INTEGER NOT NULL DEFAULT 1,
                experience INTEGER NOT NULL DEFAULT 0,
                description TEXT,
                max_members INTEGER NOT NULL DEFAULT 20
            )
        """);

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS starcore_guild_members (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                guild_id TEXT NOT NULL,
                player_uuid TEXT NOT NULL UNIQUE,
                role TEXT NOT NULL DEFAULT 'MEMBER',
                joined_at INTEGER NOT NULL,
                contributed_exp INTEGER NOT NULL DEFAULT 0
            )
        """);
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_guild ON starcore_guild_members(guild_id)");

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS starcore_guild_invites (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                guild_id TEXT NOT NULL,
                inviter_uuid TEXT NOT NULL,
                target_uuid TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                expires_at INTEGER NOT NULL
            )
        """);
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_invite_target ON starcore_guild_invites(target_uuid)");

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS starcore_parties (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                party_uuid TEXT NOT NULL UNIQUE,
                owner_uuid TEXT NOT NULL,
                name TEXT,
                created_at INTEGER NOT NULL,
                max_size INTEGER NOT NULL DEFAULT 10,
                is_private INTEGER NOT NULL DEFAULT 0
            )
        """);

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS starcore_party_members (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                party_uuid TEXT NOT NULL,
                player_uuid TEXT NOT NULL UNIQUE,
                joined_at INTEGER NOT NULL,
                is_leader INTEGER NOT NULL DEFAULT 0
            )
        """);

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS starcore_party_invites (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                party_uuid TEXT NOT NULL,
                player_uuid TEXT NOT NULL,
                invited_by TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                expires_at INTEGER NOT NULL
            )
        """);

        logger.info("社交系统 SQLite 兼容表已创建");
    }

    // ==================== 好友关系操作 ====================

    public void saveFriendRelation(UUID playerId, UUID friendId, long createdAt) {
        String sql = "INSERT IGNORE INTO starcore_friend_relations (player_uuid, friend_uuid, created_at) VALUES (?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerId.toString());
            stmt.setString(2, friendId.toString());
            stmt.setLong(3, createdAt);
            stmt.executeUpdate();
        } catch (Exception e) {
            logger.warning("保存好友关系失败: " + e.getMessage());
        }
    }

    public void deleteFriendRelation(UUID playerId, UUID friendId) {
        String sql = "DELETE FROM starcore_friend_relations WHERE (player_uuid = ? AND friend_uuid = ?) OR (player_uuid = ? AND friend_uuid = ?)";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerId.toString());
            stmt.setString(2, friendId.toString());
            stmt.setString(3, friendId.toString());
            stmt.setString(4, playerId.toString());
            stmt.executeUpdate();
        } catch (Exception e) {
            logger.warning("删除好友关系失败: " + e.getMessage());
        }
    }

    public void loadAllFriendRelations(java.util.Map<UUID, java.util.Set<UUID>> friendships) {
        String sql = "SELECT player_uuid, friend_uuid FROM starcore_friend_relations";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                UUID playerId = UUID.fromString(rs.getString("player_uuid"));
                UUID friendId = UUID.fromString(rs.getString("friend_uuid"));
                friendships.computeIfAbsent(playerId, k -> java.util.concurrent.ConcurrentHashMap.newKeySet()).add(friendId);
            }
        } catch (Exception e) {
            logger.warning("加载好友关系失败: " + e.getMessage());
        }
    }

    // ==================== 好友请求操作 ====================

    public void saveFriendRequest(UUID targetId, UUID senderId, long createdAt) {
        String sql = "INSERT IGNORE INTO starcore_friend_requests (receiver_uuid, sender_uuid, created_at, expires_at, status) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, targetId.toString());
            stmt.setString(2, senderId.toString());
            stmt.setLong(3, createdAt);
            stmt.executeUpdate();
        } catch (Exception e) {
            logger.warning("保存好友请求失败: " + e.getMessage());
        }
    }

    public void deleteFriendRequest(UUID targetId, UUID senderId) {
        String sql = "DELETE FROM starcore_friend_requests WHERE receiver_uuid = ? AND sender_uuid = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, targetId.toString());
            stmt.setString(2, senderId.toString());
            stmt.executeUpdate();
        } catch (Exception e) {
            logger.warning("删除好友请求失败: " + e.getMessage());
        }
    }

    public void loadAllFriendRequests(java.util.Map<UUID, java.util.Set<UUID>> requests) {
        String sql = "SELECT receiver_uuid, sender_uuid FROM starcore_friend_requests";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                UUID targetId = UUID.fromString(rs.getString("receiver_uuid"));
                UUID senderId = UUID.fromString(rs.getString("sender_uuid"));
                requests.computeIfAbsent(targetId, k -> java.util.concurrent.ConcurrentHashMap.newKeySet()).add(senderId);
            }
        } catch (Exception e) {
            logger.warning("加载好友请求失败: " + e.getMessage());
        }
    }

    // ==================== 黑名单操作 ====================

    public void saveBlacklistEntry(UUID playerId, UUID blockedId, long createdAt) {
        String sql = "INSERT IGNORE INTO starcore_blacklist (player_uuid, blocked_uuid, created_at) VALUES (?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerId.toString());
            stmt.setString(2, blockedId.toString());
            stmt.setLong(3, createdAt);
            stmt.executeUpdate();
        } catch (Exception e) {
            logger.warning("保存黑名单失败: " + e.getMessage());
        }
    }

    public void deleteBlacklistEntry(UUID playerId, UUID blockedId) {
        String sql = "DELETE FROM starcore_blacklist WHERE player_uuid = ? AND blocked_uuid = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerId.toString());
            stmt.setString(2, blockedId.toString());
            stmt.executeUpdate();
        } catch (Exception e) {
            logger.warning("删除黑名单失败: " + e.getMessage());
        }
    }

    public void loadAllBlacklist(java.util.Map<UUID, java.util.Set<UUID>> blacklist) {
        String sql = "SELECT player_uuid, blocked_uuid FROM starcore_blacklist";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                UUID playerId = UUID.fromString(rs.getString("player_uuid"));
                UUID blockedId = UUID.fromString(rs.getString("blocked_uuid"));
                blacklist.computeIfAbsent(playerId, k -> java.util.concurrent.ConcurrentHashMap.newKeySet()).add(blockedId);
            }
        } catch (Exception e) {
            logger.warning("加载黑名单失败: " + e.getMessage());
        }
    }

    // ==================== 公会操作 ====================

    public void saveGuild(dev.starcore.starcore.social.guild.Guild guild) {
        String sql = "INSERT INTO starcore_guilds (id, name, tag, leader_uuid, level, experience, created_at, description) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
                     "ON DUPLICATE KEY UPDATE name = VALUES(name), tag = VALUES(tag), leader_uuid = VALUES(leader_uuid), " +
                     "level = VALUES(level), experience = VALUES(experience), description = VALUES(description)";

        String memberSql = "INSERT INTO starcore_guild_members (guild_id, player_uuid, role, joined_at) VALUES (?, ?, ?, ?) " +
                          "ON DUPLICATE KEY UPDATE role = VALUES(role)";

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, guild.getId().toString());
                stmt.setString(2, guild.getName());
                stmt.setString(3, guild.getTag());
                stmt.setString(4, guild.getLeader().toString());
                stmt.setInt(5, guild.getLevel());
                stmt.setInt(6, guild.getExperience());
                stmt.setLong(7, guild.getCreatedTime());
                stmt.setString(8, guild.getDescription() != null ? guild.getDescription() : "");
                stmt.executeUpdate();

                // 删除旧成员
                try (Statement delStmt = conn.createStatement()) {
                    delStmt.execute("DELETE FROM starcore_guild_members WHERE guild_id = '" + guild.getId() + "'");
                }

                // 插入新成员
                try (PreparedStatement memberStmt = conn.prepareStatement(memberSql)) {
                    for (UUID memberId : guild.getMembers()) {
                        memberStmt.setString(1, guild.getId().toString());
                        memberStmt.setString(2, memberId.toString());
                        memberStmt.setString(3, guild.getMemberRole(memberId).name());
                        memberStmt.setLong(4, System.currentTimeMillis());
                        memberStmt.addBatch();
                    }
                    memberStmt.executeBatch();
                }
            }
            conn.commit();
        } catch (Exception e) {
            logger.warning("保存公会失败: " + e.getMessage());
        }
    }

    public void deleteGuild(UUID guildId) {
        String sql = "DELETE FROM starcore_guilds WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, guildId.toString());
            stmt.executeUpdate();
        } catch (Exception e) {
            logger.warning("删除公会失败: " + e.getMessage());
        }
    }

    public void loadAllGuilds(java.util.Map<UUID, dev.starcore.starcore.social.guild.Guild> guilds,
                              java.util.Map<String, UUID> guildNames,
                              java.util.Map<UUID, UUID> playerGuilds) {
        String guildSql = "SELECT * FROM starcore_guilds";
        String memberSql = "SELECT * FROM starcore_guild_members WHERE guild_id = ?";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet guildRs = stmt.executeQuery(guildSql)) {

            while (guildRs.next()) {
                UUID guildId = UUID.fromString(guildRs.getString("id"));
                String name = guildRs.getString("name");
                String tag = guildRs.getString("tag");
                UUID leaderId = UUID.fromString(guildRs.getString("leader_uuid"));
                int level = guildRs.getInt("level");
                int exp = guildRs.getInt("experience");
                long createdAt = guildRs.getLong("created_at");
                String description = guildRs.getString("description");

                dev.starcore.starcore.social.guild.Guild guild = new dev.starcore.starcore.social.guild.Guild(
                    guildId, name, tag, leaderId
                );
                guild.setLevel(level);
                guild.setExperience(exp);
                if (description != null && !description.isEmpty()) {
                    guild.setDescription(description);
                }

                // 加载成员
                try (PreparedStatement memberStmt = conn.prepareStatement(memberSql)) {
                    memberStmt.setString(1, guildId.toString());
                    try (ResultSet memberRs = memberStmt.executeQuery()) {
                        while (memberRs.next()) {
                            UUID memberId = UUID.fromString(memberRs.getString("player_uuid"));
                            String roleStr = memberRs.getString("role");
                            guild.addMember(memberId);
                            if (roleStr != null) {
                                try {
                                    guild.setMemberRole(memberId, dev.starcore.starcore.social.guild.GuildRole.valueOf(roleStr));
                                } catch (IllegalArgumentException e) {
                                    logger.warning("加载公会成员角色失败，未知角色: " + roleStr + " - " + e.getMessage());
                                }
                            }
                            playerGuilds.put(memberId, guildId);
                        }
                    }
                }

                guilds.put(guildId, guild);
                guildNames.put(name.toLowerCase(), guildId);
            }
        } catch (Exception e) {
            logger.warning("加载公会失败: " + e.getMessage());
        }
    }

    // ==================== 派对操作 ====================

    public void saveParty(dev.starcore.starcore.social.party.Party party) {
        String sql = "INSERT INTO starcore_parties (id, leader_uuid, created_at, friendly_fire, exp_share, max_members) " +
                     "VALUES (?, ?, ?, ?, ?, ?) " +
                     "ON DUPLICATE KEY UPDATE leader_uuid = VALUES(leader_uuid), friendly_fire = VALUES(friendly_fire), " +
                     "exp_share = VALUES(exp_share), max_members = VALUES(max_members)";

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, party.getId().toString());
                stmt.setString(2, party.getLeader().toString());
                stmt.setLong(3, party.getCreatedTime());
                stmt.setBoolean(4, party.isFriendlyFire());
                stmt.setBoolean(5, party.isExpShare());
                stmt.setInt(6, party.getMaxMembers());
                stmt.executeUpdate();

                // 删除旧成员
                try (Statement delStmt = conn.createStatement()) {
                    delStmt.execute("DELETE FROM starcore_party_members WHERE party_id = '" + party.getId() + "'");
                }

                // 插入新成员
                String memberSql = "INSERT INTO starcore_party_members (party_id, player_uuid, joined_at) VALUES (?, ?, ?)";
                try (PreparedStatement memberStmt = conn.prepareStatement(memberSql)) {
                    for (UUID memberId : party.getMembers()) {
                        memberStmt.setString(1, party.getId().toString());
                        memberStmt.setString(2, memberId.toString());
                        memberStmt.setLong(3, System.currentTimeMillis());
                        memberStmt.addBatch();
                    }
                    memberStmt.executeBatch();
                }
            }
            conn.commit();
        } catch (Exception e) {
            logger.warning("保存派对失败: " + e.getMessage());
        }
    }

    public void deleteParty(UUID partyId) {
        String sql = "DELETE FROM starcore_parties WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, partyId.toString());
            stmt.executeUpdate();
        } catch (Exception e) {
            logger.warning("删除派对失败: " + e.getMessage());
        }
    }

    public void loadAllParties(java.util.Map<UUID, dev.starcore.starcore.social.party.Party> parties,
                               java.util.Map<UUID, UUID> playerParties) {
        String partySql = "SELECT * FROM starcore_parties";
        String memberSql = "SELECT * FROM starcore_party_members WHERE party_id = ?";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet partyRs = stmt.executeQuery(partySql)) {

            while (partyRs.next()) {
                UUID partyId = UUID.fromString(partyRs.getString("id"));
                UUID leaderId = UUID.fromString(partyRs.getString("leader_uuid"));
                long createdAt = partyRs.getLong("created_at");
                boolean friendlyFire = partyRs.getBoolean("friendly_fire");
                boolean expShare = partyRs.getBoolean("exp_share");
                int maxMembers = partyRs.getInt("max_members");

                dev.starcore.starcore.social.party.Party party = new dev.starcore.starcore.social.party.Party(partyId, leaderId);
                party.setFriendlyFire(friendlyFire);
                party.setExpShare(expShare);
                party.setMaxMembers(maxMembers);

                // 加载成员
                try (PreparedStatement memberStmt = conn.prepareStatement(memberSql)) {
                    memberStmt.setString(1, partyId.toString());
                    try (ResultSet memberRs = memberStmt.executeQuery()) {
                        while (memberRs.next()) {
                            UUID memberId = UUID.fromString(memberRs.getString("player_uuid"));
                            party.addMember(memberId);
                            playerParties.put(memberId, partyId);
                        }
                    }
                }

                parties.put(partyId, party);
            }
        } catch (Exception e) {
            logger.warning("加载派对失败: " + e.getMessage());
        }
    }

    // ==================== 邀请操作 ====================

    public void saveGuildInvite(UUID guildId, UUID inviterId, UUID targetId, long timestamp) {
        String sql = "INSERT IGNORE INTO starcore_guild_invites (guild_id, inviter_uuid, target_uuid, created_at) VALUES (?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, guildId.toString());
            stmt.setString(2, inviterId.toString());
            stmt.setString(3, targetId.toString());
            stmt.setLong(4, timestamp);
            stmt.executeUpdate();
        } catch (Exception e) {
            logger.warning("保存公会邀请失败: " + e.getMessage());
        }
    }

    public void deleteGuildInvite(UUID guildId, UUID targetId) {
        String sql = "DELETE FROM starcore_guild_invites WHERE guild_id = ? AND target_uuid = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, guildId.toString());
            stmt.setString(2, targetId.toString());
            stmt.executeUpdate();
        } catch (Exception e) {
            logger.warning("删除公会邀请失败: " + e.getMessage());
        }
    }

    public void loadAllGuildInvites(java.util.Map<UUID, java.util.Set<dev.starcore.starcore.social.guild.GuildService.GuildInvite>> invites) {
        String sql = "SELECT guild_id, inviter_uuid, target_uuid, created_at FROM starcore_guild_invites";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                UUID guildId = UUID.fromString(rs.getString("guild_id"));
                UUID inviterId = UUID.fromString(rs.getString("inviter_uuid"));
                UUID targetId = UUID.fromString(rs.getString("target_uuid"));
                long timestamp = rs.getLong("created_at");
                var invite = new dev.starcore.starcore.social.guild.GuildService.GuildInvite(guildId, inviterId, targetId, timestamp);
                invites.computeIfAbsent(targetId, k -> java.util.concurrent.ConcurrentHashMap.newKeySet()).add(invite);
            }
        } catch (Exception e) {
            logger.warning("加载公会邀请失败: " + e.getMessage());
        }
    }

    public void savePartyInvite(UUID inviterId, UUID targetId, long timestamp) {
        String sql = "INSERT IGNORE INTO starcore_party_invites (inviter_uuid, target_uuid, created_at) VALUES (?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, inviterId.toString());
            stmt.setString(2, targetId.toString());
            stmt.setLong(3, timestamp);
            stmt.executeUpdate();
        } catch (Exception e) {
            logger.warning("保存派对邀请失败: " + e.getMessage());
        }
    }

    public void deletePartyInvite(UUID inviterId, UUID targetId) {
        String sql = "DELETE FROM starcore_party_invites WHERE inviter_uuid = ? AND target_uuid = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, inviterId.toString());
            stmt.setString(2, targetId.toString());
            stmt.executeUpdate();
        } catch (Exception e) {
            logger.warning("删除派对邀请失败: " + e.getMessage());
        }
    }

    public void loadAllPartyInvites(java.util.Map<UUID, java.util.Set<UUID>> invites) {
        String sql = "SELECT invited_by, player_uuid FROM starcore_party_invites";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                UUID inviterId = UUID.fromString(rs.getString("invited_by"));
                UUID targetId = UUID.fromString(rs.getString("player_uuid"));
                invites.computeIfAbsent(targetId, k -> java.util.concurrent.ConcurrentHashMap.newKeySet()).add(inviterId);
            }
        } catch (Exception e) {
            logger.warning("加载派对邀请失败: " + e.getMessage());
        }
    }

    // ==================== 在线状态操作 ====================

    public void savePlayerStatus(UUID playerId, boolean isOnline) {
        String sql = "INSERT OR REPLACE INTO starcore_player_status (player_uuid, online_status, last_online) VALUES (?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerId.toString());
            stmt.setString(2, isOnline ? "ONLINE" : "OFFLINE");
            stmt.setLong(3, System.currentTimeMillis());
            stmt.executeUpdate();
        } catch (Exception e) {
            logger.warning("保存玩家状态失败: " + e.getMessage());
        }
    }

    public void loadAllPlayerStatus(java.util.Map<UUID, Boolean> statusMap) {
        String sql = "SELECT player_uuid, online_status FROM starcore_player_status";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                UUID playerId = UUID.fromString(rs.getString("player_uuid"));
                String status = rs.getString("online_status");
                statusMap.put(playerId, "ONLINE".equals(status));
            }
        } catch (Exception e) {
            logger.warning("加载玩家状态失败: " + e.getMessage());
        }
    }

    // ==================== 批量操作（用于定期保存）====================

    public void saveAllFriends(java.util.Map<UUID, java.util.Set<UUID>> friendships) {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("DELETE FROM starcore_friend_relations");
            }

            String sql = "INSERT INTO starcore_friend_relations (player_uuid, friend_uuid, created_at) VALUES (?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                long now = System.currentTimeMillis();
                for (var entry : friendships.entrySet()) {
                    for (UUID friendId : entry.getValue()) {
                        stmt.setString(1, entry.getKey().toString());
                        stmt.setString(2, friendId.toString());
                        stmt.setLong(3, now);
                        stmt.addBatch();
                    }
                }
                stmt.executeBatch();
            }
            conn.commit();
        } catch (Exception e) {
            logger.warning("批量保存好友关系失败: " + e.getMessage());
        }
    }

    public void saveAllGuilds(java.util.Collection<dev.starcore.starcore.social.guild.Guild> guilds) {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("DELETE FROM starcore_guild_members");
                stmt.execute("DELETE FROM starcore_guilds");
            }

            String guildSql = "INSERT INTO starcore_guilds (id, name, tag, leader_uuid, level, experience, created_at, description) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            String memberSql = "INSERT INTO starcore_guild_members (guild_id, player_uuid, role, joined_at) VALUES (?, ?, ?, ?)";

            for (dev.starcore.starcore.social.guild.Guild guild : guilds) {
                try (PreparedStatement guildStmt = conn.prepareStatement(guildSql)) {
                    guildStmt.setString(1, guild.getId().toString());
                    guildStmt.setString(2, guild.getName());
                    guildStmt.setString(3, guild.getTag());
                    guildStmt.setString(4, guild.getLeader().toString());
                    guildStmt.setInt(5, guild.getLevel());
                    guildStmt.setInt(6, guild.getExperience());
                    guildStmt.setLong(7, guild.getCreatedTime());
                    guildStmt.setString(8, guild.getDescription() != null ? guild.getDescription() : "");
                    guildStmt.executeUpdate();
                }

                try (PreparedStatement memberStmt = conn.prepareStatement(memberSql)) {
                    for (UUID memberId : guild.getMembers()) {
                        memberStmt.setString(1, guild.getId().toString());
                        memberStmt.setString(2, memberId.toString());
                        memberStmt.setString(3, guild.getMemberRole(memberId).name());
                        memberStmt.setLong(4, System.currentTimeMillis());
                        memberStmt.addBatch();
                    }
                    memberStmt.executeBatch();
                }
            }
            conn.commit();
        } catch (Exception e) {
            logger.warning("批量保存公会失败: " + e.getMessage());
        }
    }

    public void saveAllParties(java.util.Collection<dev.starcore.starcore.social.party.Party> parties) {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("DELETE FROM starcore_party_members");
                stmt.execute("DELETE FROM starcore_parties");
            }

            String partySql = "INSERT INTO starcore_parties (id, leader_uuid, created_at, friendly_fire, exp_share, max_members) VALUES (?, ?, ?, ?, ?, ?)";
            String memberSql = "INSERT INTO starcore_party_members (party_id, player_uuid, joined_at) VALUES (?, ?, ?)";

            for (dev.starcore.starcore.social.party.Party party : parties) {
                try (PreparedStatement partyStmt = conn.prepareStatement(partySql)) {
                    partyStmt.setString(1, party.getId().toString());
                    partyStmt.setString(2, party.getLeader().toString());
                    partyStmt.setLong(3, party.getCreatedTime());
                    partyStmt.setBoolean(4, party.isFriendlyFire());
                    partyStmt.setBoolean(5, party.isExpShare());
                    partyStmt.setInt(6, party.getMaxMembers());
                    partyStmt.executeUpdate();
                }

                try (PreparedStatement memberStmt = conn.prepareStatement(memberSql)) {
                    for (UUID memberId : party.getMembers()) {
                        memberStmt.setString(1, party.getId().toString());
                        memberStmt.setString(2, memberId.toString());
                        memberStmt.setLong(3, System.currentTimeMillis());
                        memberStmt.addBatch();
                    }
                    memberStmt.executeBatch();
                }
            }
            conn.commit();
        } catch (Exception e) {
            logger.warning("批量保存派对失败: " + e.getMessage());
        }
    }

    /**
     * 检查数据库是否可用
     */
    public boolean isDatabaseAvailable() {
        return databaseService.isRunning();
    }
}
