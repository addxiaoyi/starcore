package dev.starcore.starcore.government;

import dev.starcore.starcore.core.database.DatabaseUtils;

import java.util.Optional;

import org.bukkit.plugin.java.JavaPlugin;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * 政党服务
 * 管理政党、党员和政党联盟
 */
public final class PartyService {
    private final JavaPlugin plugin;
    private final DataSource dataSource;

    // 内存缓存（使用 ConcurrentHashMap 保证线程安全）
    private final Map<Integer, PoliticalParty> parties = new ConcurrentHashMap<>();
    private final Map<Integer, PartyAlliance> alliances = new ConcurrentHashMap<>();

    public PartyService(JavaPlugin plugin, DataSource dataSource) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    /**
     * 初始化数据库表
     */
    public void initializeTables() {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseUtils.DatabaseType dbType = DatabaseUtils.detectDatabaseType(conn);
            boolean isSQLite = dbType == DatabaseUtils.DatabaseType.SQLITE;

            // 政党表
            try (Statement stmt = conn.createStatement()) {
                String tableSql;
                if (isSQLite) {
                    tableSql =
                        "CREATE TABLE IF NOT EXISTS political_parties (" +
                        "party_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "name TEXT NOT NULL," +
                        "abbreviation TEXT NOT NULL," +
                        "founder_id TEXT NOT NULL," +
                        "founded_at INTEGER NOT NULL," +
                        "ideology TEXT," +
                        "platform TEXT," +
                        "color TEXT," +
                        "active INTEGER NOT NULL DEFAULT 1," +
                        "in_power INTEGER NOT NULL DEFAULT 0" +
                        ")";
                } else {
                    tableSql =
                        "CREATE TABLE IF NOT EXISTS political_parties (" +
                        "party_id INT PRIMARY KEY AUTO_INCREMENT," +
                        "name VARCHAR(200) NOT NULL," +
                        "abbreviation VARCHAR(20) NOT NULL," +
                        "founder_id VARCHAR(36) NOT NULL," +
                        "founded_at BIGINT NOT NULL," +
                        "ideology VARCHAR(100)," +
                        "platform TEXT," +
                        "color VARCHAR(7)," +
                        "active BOOLEAN NOT NULL DEFAULT TRUE," +
                        "in_power BOOLEAN NOT NULL DEFAULT FALSE" +
                        ")";
                }
                stmt.execute(tableSql);
            }

            // 党员表
            try (Statement stmt = conn.createStatement()) {
                String tableSql;
                if (isSQLite) {
                    tableSql =
                        "CREATE TABLE IF NOT EXISTS party_members (" +
                        "party_id INTEGER NOT NULL," +
                        "player_id TEXT NOT NULL," +
                        "joined_at INTEGER NOT NULL," +
                        "role TEXT NOT NULL DEFAULT 'MEMBER'," +
                        "active INTEGER NOT NULL DEFAULT 1," +
                        "PRIMARY KEY (party_id, player_id)" +
                        ")";
                } else {
                    tableSql =
                        "CREATE TABLE IF NOT EXISTS party_members (" +
                        "party_id INT NOT NULL," +
                        "player_id VARCHAR(36) NOT NULL," +
                        "joined_at BIGINT NOT NULL," +
                        "role VARCHAR(50) NOT NULL DEFAULT 'MEMBER'," +
                        "active BOOLEAN NOT NULL DEFAULT TRUE," +
                        "PRIMARY KEY (party_id, player_id)" +
                        ")";
                }
                stmt.execute(tableSql);
            }

            // 政党联盟表
            try (Statement stmt = conn.createStatement()) {
                String tableSql;
                if (isSQLite) {
                    tableSql =
                        "CREATE TABLE IF NOT EXISTS party_alliances (" +
                        "alliance_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "name TEXT NOT NULL," +
                        "formed_at INTEGER NOT NULL," +
                        "active INTEGER NOT NULL DEFAULT 1," +
                        "lead_party_id INTEGER," +
                        "purpose TEXT" +
                        ")";
                } else {
                    tableSql =
                        "CREATE TABLE IF NOT EXISTS party_alliances (" +
                        "alliance_id INT PRIMARY KEY AUTO_INCREMENT," +
                        "name VARCHAR(200) NOT NULL," +
                        "formed_at BIGINT NOT NULL," +
                        "active BOOLEAN NOT NULL DEFAULT TRUE," +
                        "lead_party_id INT," +
                        "purpose TEXT" +
                        ")";
                }
                stmt.execute(tableSql);
            }

            // 联盟成员表
            try (Statement stmt = conn.createStatement()) {
                String tableSql;
                if (isSQLite) {
                    tableSql =
                        "CREATE TABLE IF NOT EXISTS alliance_members (" +
                        "alliance_id INTEGER NOT NULL," +
                        "party_id INTEGER NOT NULL," +
                        "joined_at INTEGER NOT NULL," +
                        "PRIMARY KEY (alliance_id, party_id)" +
                        ")";
                } else {
                    tableSql =
                        "CREATE TABLE IF NOT EXISTS alliance_members (" +
                        "alliance_id INT NOT NULL," +
                        "party_id INT NOT NULL," +
                        "joined_at BIGINT NOT NULL," +
                        "PRIMARY KEY (alliance_id, party_id)" +
                        ")";
                }
                stmt.execute(tableSql);
            }

            plugin.getLogger().info("Party tables initialized");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize party tables", e);
        }
    }

    // ==================== 政党管理 ====================

    /**
     * 创建政党
     */
    public Optional<PoliticalParty> createParty(String name, String abbreviation,
                                                UUID founderId, String ideology, String platform) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "INSERT INTO political_parties (name, abbreviation, founder_id, founded_at, ideology, platform, active, in_power) " +
                 "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                 Statement.RETURN_GENERATED_KEYS
             )) {
            Instant now = Instant.now();
            stmt.setString(1, name);
            stmt.setString(2, abbreviation);
            stmt.setString(3, founderId.toString());
            stmt.setLong(4, now.toEpochMilli());
            stmt.setString(5, ideology);
            stmt.setString(6, platform);
            stmt.setBoolean(7, true);
            stmt.setBoolean(8, false);
            stmt.executeUpdate();

            ResultSet rs = stmt.getGeneratedKeys();
            if (rs.next()) {
                int partyId = rs.getInt(1);
                PoliticalParty party = new PoliticalParty(partyId, name, abbreviation, founderId, now);
                party.setIdeology(ideology);
                party.setPlatform(platform);
                parties.put(partyId, party);

                // 创始人自动加入政党
                addPartyMember(partyId, founderId, "LEADER");

                return Optional.of(party);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create party", e);
        }

        return Optional.empty();
    }

    /**
     * 获取政党
     */
    public Optional<PoliticalParty> getParty(int partyId) {
        if (parties.containsKey(partyId)) {
            return Optional.of(parties.get(partyId));
        }

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT * FROM political_parties WHERE party_id = ?"
             )) {
            stmt.setInt(1, partyId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                PoliticalParty party = loadPartyFromResultSet(rs);
                parties.put(partyId, party);
                return Optional.of(party);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load party", e);
        }

        return Optional.empty();
    }

    /**
     * 获取所有活跃政党
     */
    public List<PoliticalParty> getActiveParties() {
        List<PoliticalParty> result = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT * FROM political_parties WHERE active = TRUE ORDER BY founded_at DESC"
             )) {
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                PoliticalParty party = loadPartyFromResultSet(rs);
                parties.put(party.getPartyId(), party);
                result.add(party);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load active parties", e);
        }

        return result;
    }

    /**
     * 更新政党纲领
     */
    public boolean updatePartyPlatform(int partyId, String platform) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "UPDATE political_parties SET platform = ? WHERE party_id = ?"
             )) {
            stmt.setString(1, platform);
            stmt.setInt(2, partyId);
            int updated = stmt.executeUpdate();

            if (updated > 0) {
                PoliticalParty party = parties.get(partyId);
                if (party != null) {
                    party.setPlatform(platform);
                }
                return true;
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to update party platform", e);
        }

        return false;
    }

    /**
     * 设置政党颜色
     */
    public boolean setPartyColor(int partyId, String color) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "UPDATE political_parties SET color = ? WHERE party_id = ?"
             )) {
            stmt.setString(1, color);
            stmt.setInt(2, partyId);
            int updated = stmt.executeUpdate();

            if (updated > 0) {
                PoliticalParty party = parties.get(partyId);
                if (party != null) {
                    party.setColor(color);
                }
                return true;
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to set party color", e);
        }

        return false;
    }

    /**
     * 设置执政党
     */
    public boolean setPartyInPower(int partyId, boolean inPower) {
        try (Connection conn = dataSource.getConnection()) {
            // 如果设置为执政，先清除其他政党的执政状态
            if (inPower) {
                try (PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE political_parties SET in_power = FALSE WHERE in_power = TRUE"
                )) {
                    stmt.executeUpdate();
                }
            }

            // 设置目标政党
            try (PreparedStatement stmt = conn.prepareStatement(
                "UPDATE political_parties SET in_power = ? WHERE party_id = ?"
            )) {
                stmt.setBoolean(1, inPower);
                stmt.setInt(2, partyId);
                int updated = stmt.executeUpdate();

                if (updated > 0) {
                    // 更新缓存
                    for (PoliticalParty party : parties.values()) {
                        party.setInPower(false);
                    }
                    PoliticalParty party = parties.get(partyId);
                    if (party != null) {
                        party.setInPower(inPower);
                    }
                    return true;
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to set party in power", e);
        }

        return false;
    }

    /**
     * 解散政党
     */
    public boolean dissolveParty(int partyId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "UPDATE political_parties SET active = FALSE WHERE party_id = ?"
             )) {
            stmt.setInt(1, partyId);
            int updated = stmt.executeUpdate();

            if (updated > 0) {
                PoliticalParty party = parties.get(partyId);
                if (party != null) {
                    party.setActive(false);
                }
                return true;
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to dissolve party", e);
        }

        return false;
    }

    // ==================== 党员管理 ====================

    /**
     * 添加党员
     */
    public boolean addPartyMember(int partyId, UUID playerId, String role) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "INSERT INTO party_members (party_id, player_id, joined_at, role, active) " +
                 "VALUES (?, ?, ?, ?, ?) " +
                 "ON DUPLICATE KEY UPDATE active = TRUE, role = ?"
             )) {
            Instant now = Instant.now();
            stmt.setInt(1, partyId);
            stmt.setString(2, playerId.toString());
            stmt.setLong(3, now.toEpochMilli());
            stmt.setString(4, role);
            stmt.setBoolean(5, true);
            stmt.setString(6, role);
            stmt.executeUpdate();
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to add party member", e);
            return false;
        }
    }

    /**
     * 移除党员
     */
    public boolean removePartyMember(int partyId, UUID playerId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "UPDATE party_members SET active = FALSE WHERE party_id = ? AND player_id = ?"
             )) {
            stmt.setInt(1, partyId);
            stmt.setString(2, playerId.toString());
            return stmt.executeUpdate() > 0;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to remove party member", e);
            return false;
        }
    }

    /**
     * 获取党员数量
     */
    public int getPartyMemberCount(int partyId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT COUNT(*) FROM party_members WHERE party_id = ? AND active = TRUE"
             )) {
            stmt.setInt(1, partyId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to count party members", e);
        }

        return 0;
    }

    /**
     * 获取党员列表
     */
    public List<UUID> getPartyMembers(int partyId) {
        List<UUID> members = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT player_id FROM party_members WHERE party_id = ? AND active = TRUE"
             )) {
            stmt.setInt(1, partyId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                members.add(UUID.fromString(rs.getString("player_id")));
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load party members", e);
        }

        return members;
    }

    /**
     * 获取玩家所属政党
     */
    public Optional<Integer> getPlayerParty(UUID playerId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT party_id FROM party_members WHERE player_id = ? AND active = TRUE LIMIT 1"
             )) {
            stmt.setString(1, playerId.toString());
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return Optional.of(rs.getInt("party_id"));
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to get player party", e);
        }

        return Optional.empty();
    }

    /**
     * 检查玩家是否是党员
     */
    public boolean isPartyMember(int partyId, UUID playerId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT COUNT(*) FROM party_members WHERE party_id = ? AND player_id = ? AND active = TRUE"
             )) {
            stmt.setInt(1, partyId);
            stmt.setString(2, playerId.toString());
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to check party membership", e);
        }

        return false;
    }

    // ==================== 政党联盟管理 ====================

    /**
     * 创建政党联盟
     */
    public Optional<PartyAlliance> createAlliance(String name, List<Integer> partyIds, String purpose) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "INSERT INTO party_alliances (name, formed_at, active, purpose) VALUES (?, ?, ?, ?)",
                 Statement.RETURN_GENERATED_KEYS
             )) {
            Instant now = Instant.now();
            stmt.setString(1, name);
            stmt.setLong(2, now.toEpochMilli());
            stmt.setBoolean(3, true);
            stmt.setString(4, purpose);
            stmt.executeUpdate();

            ResultSet rs = stmt.getGeneratedKeys();
            if (rs.next()) {
                int allianceId = rs.getInt(1);

                // 添加成员政党
                try (PreparedStatement memberStmt = conn.prepareStatement(
                    "INSERT INTO alliance_members (alliance_id, party_id, joined_at) VALUES (?, ?, ?)"
                )) {
                    for (Integer partyId : partyIds) {
                        memberStmt.setInt(1, allianceId);
                        memberStmt.setInt(2, partyId);
                        memberStmt.setLong(3, now.toEpochMilli());
                        memberStmt.addBatch();
                    }
                    memberStmt.executeBatch();
                }

                PartyAlliance alliance = new PartyAlliance(allianceId, name, partyIds, now);
                alliance.setPurpose(purpose);
                alliances.put(allianceId, alliance);
                return Optional.of(alliance);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create alliance", e);
        }

        return Optional.empty();
    }

    /**
     * 获取联盟
     */
    public Optional<PartyAlliance> getAlliance(int allianceId) {
        if (alliances.containsKey(allianceId)) {
            return Optional.of(alliances.get(allianceId));
        }

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT * FROM party_alliances WHERE alliance_id = ?"
             )) {
            stmt.setInt(1, allianceId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                PartyAlliance alliance = loadAllianceFromResultSet(rs, conn);
                alliances.put(allianceId, alliance);
                return Optional.of(alliance);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load alliance", e);
        }

        return Optional.empty();
    }

    /**
     * 获取所有活跃联盟
     */
    public List<PartyAlliance> getActiveAlliances() {
        List<PartyAlliance> result = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT * FROM party_alliances WHERE active = TRUE ORDER BY formed_at DESC"
             )) {
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                PartyAlliance alliance = loadAllianceFromResultSet(rs, conn);
                alliances.put(alliance.getAllianceId(), alliance);
                result.add(alliance);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load active alliances", e);
        }

        return result;
    }

    /**
     * 添加政党到联盟
     */
    public boolean addPartyToAlliance(int allianceId, int partyId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "INSERT INTO alliance_members (alliance_id, party_id, joined_at) VALUES (?, ?, ?)"
             )) {
            stmt.setInt(1, allianceId);
            stmt.setInt(2, partyId);
            stmt.setLong(3, Instant.now().toEpochMilli());
            stmt.executeUpdate();

            PartyAlliance alliance = alliances.get(allianceId);
            if (alliance != null) {
                alliance.addParty(partyId);
            }

            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to add party to alliance", e);
            return false;
        }
    }

    /**
     * 从联盟中移除政党
     */
    public boolean removePartyFromAlliance(int allianceId, int partyId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "DELETE FROM alliance_members WHERE alliance_id = ? AND party_id = ?"
             )) {
            stmt.setInt(1, allianceId);
            stmt.setInt(2, partyId);
            int deleted = stmt.executeUpdate();

            if (deleted > 0) {
                PartyAlliance alliance = alliances.get(allianceId);
                if (alliance != null) {
                    alliance.removeParty(partyId);
                }
                return true;
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to remove party from alliance", e);
        }

        return false;
    }

    /**
     * 设置联盟主导政党
     */
    public boolean setAllianceLeadParty(int allianceId, Integer leadPartyId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "UPDATE party_alliances SET lead_party_id = ? WHERE alliance_id = ?"
             )) {
            if (leadPartyId != null) {
                stmt.setInt(1, leadPartyId);
            } else {
                stmt.setNull(1, Types.INTEGER);
            }
            stmt.setInt(2, allianceId);
            int updated = stmt.executeUpdate();

            if (updated > 0) {
                PartyAlliance alliance = alliances.get(allianceId);
                if (alliance != null) {
                    alliance.setLeadPartyId(leadPartyId);
                }
                return true;
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to set alliance lead party", e);
        }

        return false;
    }

    /**
     * 解散联盟
     */
    public boolean dissolveAlliance(int allianceId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "UPDATE party_alliances SET active = FALSE WHERE alliance_id = ?"
             )) {
            stmt.setInt(1, allianceId);
            int updated = stmt.executeUpdate();

            if (updated > 0) {
                PartyAlliance alliance = alliances.get(allianceId);
                if (alliance != null) {
                    alliance.setActive(false);
                }
                return true;
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to dissolve alliance", e);
        }

        return false;
    }

    /**
     * 获取政党所属联盟
     */
    public Optional<PartyAlliance> getPartyAlliance(int partyId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT a.* FROM party_alliances a " +
                 "JOIN alliance_members m ON a.alliance_id = m.alliance_id " +
                 "WHERE m.party_id = ? AND a.active = TRUE LIMIT 1"
             )) {
            stmt.setInt(1, partyId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                PartyAlliance alliance = loadAllianceFromResultSet(rs, conn);
                alliances.put(alliance.getAllianceId(), alliance);
                return Optional.of(alliance);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to get party alliance", e);
        }

        return Optional.empty();
    }

    // ==================== 统计方法 ====================

    /**
     * 更新政党席位数（基于议会数据）
     */
    public void updatePartySeats(int parliamentId, ParliamentService parliamentService) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT party_id, COUNT(*) as seat_count FROM parliament_members " +
                 "WHERE parliament_id = ? AND active = TRUE AND party_id IS NOT NULL " +
                 "GROUP BY party_id"
             )) {
            stmt.setInt(1, parliamentId);
            ResultSet rs = stmt.executeQuery();

            Map<Integer, Integer> seatCounts = new HashMap<>();
            while (rs.next()) {
                int partyId = rs.getInt("party_id");
                int seatCount = rs.getInt("seat_count");
                seatCounts.put(partyId, seatCount);
            }

            // 更新所有政党的席位数
            for (PoliticalParty party : parties.values()) {
                int seats = seatCounts.getOrDefault(party.getPartyId(), 0);
                party.setTotalSeats(seats);
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to update party seats", e);
        }
    }

    /**
     * 获取联盟总席位数
     */
    public int getAllianceTotalSeats(int allianceId) {
        PartyAlliance alliance = alliances.get(allianceId);
        if (alliance == null) {
            Optional<PartyAlliance> allianceOpt = getAlliance(allianceId);
            if (!allianceOpt.isPresent()) {
                return 0;
            }
            alliance = allianceOpt.get();
        }

        int totalSeats = 0;
        for (Integer partyId : alliance.getPartyIds()) {
            PoliticalParty party = parties.get(partyId);
            if (party == null) {
                Optional<PoliticalParty> partyOpt = getParty(partyId);
                if (partyOpt.isPresent()) {
                    party = partyOpt.get();
                }
            }
            if (party != null) {
                totalSeats += party.getTotalSeats();
            }
        }

        return totalSeats;
    }

    // ==================== 辅助方法 ====================

    private PoliticalParty loadPartyFromResultSet(ResultSet rs) throws SQLException {
        int partyId = rs.getInt("party_id");
        String name = rs.getString("name");
        String abbreviation = rs.getString("abbreviation");
        UUID founderId = UUID.fromString(rs.getString("founder_id"));
        Instant foundedAt = Instant.ofEpochMilli(rs.getLong("founded_at"));

        PoliticalParty party = new PoliticalParty(partyId, name, abbreviation, founderId, foundedAt);

        String ideology = rs.getString("ideology");
        if (ideology != null) {
            party.setIdeology(ideology);
        }

        String platform = rs.getString("platform");
        if (platform != null) {
            party.setPlatform(platform);
        }

        String color = rs.getString("color");
        if (color != null) {
            party.setColor(color);
        }

        party.setActive(rs.getBoolean("active"));
        party.setInPower(rs.getBoolean("in_power"));

        return party;
    }

    private PartyAlliance loadAllianceFromResultSet(ResultSet rs, Connection conn) throws SQLException {
        int allianceId = rs.getInt("alliance_id");
        String name = rs.getString("name");
        Instant formedAt = Instant.ofEpochMilli(rs.getLong("formed_at"));

        // 加载成员政党
        List<Integer> partyIds = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(
            "SELECT party_id FROM alliance_members WHERE alliance_id = ?"
        )) {
            stmt.setInt(1, allianceId);
            ResultSet memberRs = stmt.executeQuery();
            while (memberRs.next()) {
                partyIds.add(memberRs.getInt("party_id"));
            }
        }

        PartyAlliance alliance = new PartyAlliance(allianceId, name, partyIds, formedAt);
        alliance.setActive(rs.getBoolean("active"));

        int leadPartyId = rs.getInt("lead_party_id");
        if (!rs.wasNull()) {
            alliance.setLeadPartyId(leadPartyId);
        }

        String purpose = rs.getString("purpose");
        if (purpose != null) {
            alliance.setPurpose(purpose);
        }

        return alliance;
    }
}

