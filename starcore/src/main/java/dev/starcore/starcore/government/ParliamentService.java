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
 * 议会服务
 * 管理议会、议员、议案和投票
 */
public final class ParliamentService {
    private final JavaPlugin plugin;
    private final DataSource dataSource;

    // 内存缓存（使用 ConcurrentHashMap 保证线程安全）
    private final Map<Integer, Parliament> parliaments = new ConcurrentHashMap<>();
    private final Map<UUID, List<Member>> membersByPlayer = new ConcurrentHashMap<>();
    private final Map<Integer, Bill> bills = new ConcurrentHashMap<>();

    public ParliamentService(JavaPlugin plugin, DataSource dataSource) {
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

            // 议会表
            try (Statement stmt = conn.createStatement()) {
                String tableSql;
                if (isSQLite) {
                    tableSql =
                        "CREATE TABLE IF NOT EXISTS parliament (" +
                        "parliament_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "nation_id TEXT NOT NULL," +
                        "name TEXT NOT NULL," +
                        "established_at INTEGER NOT NULL," +
                        "total_seats INTEGER NOT NULL," +
                        "term_length_days INTEGER NOT NULL," +
                        "active INTEGER NOT NULL DEFAULT 1," +
                        "next_election_at INTEGER" +
                        ")";
                } else {
                    tableSql =
                        "CREATE TABLE IF NOT EXISTS parliament (" +
                        "parliament_id INT PRIMARY KEY AUTO_INCREMENT," +
                        "nation_id VARCHAR(100) NOT NULL," +
                        "name VARCHAR(200) NOT NULL," +
                        "established_at BIGINT NOT NULL," +
                        "total_seats INT NOT NULL," +
                        "term_length_days INT NOT NULL," +
                        "active BOOLEAN NOT NULL DEFAULT TRUE," +
                        "next_election_at BIGINT" +
                        ")";
                }
                stmt.execute(tableSql);
            }

            // 议员表
            try (Statement stmt = conn.createStatement()) {
                String tableSql;
                if (isSQLite) {
                    tableSql =
                        "CREATE TABLE IF NOT EXISTS parliament_members (" +
                        "player_id TEXT NOT NULL," +
                        "parliament_id INTEGER NOT NULL," +
                        "elected_at INTEGER NOT NULL," +
                        "term_ends_at INTEGER," +
                        "active INTEGER NOT NULL DEFAULT 1," +
                        "party_id INTEGER," +
                        "constituency TEXT," +
                        "votes_received INTEGER NOT NULL DEFAULT 0," +
                        "bills_proposed INTEGER NOT NULL DEFAULT 0," +
                        "votes_participated INTEGER NOT NULL DEFAULT 0," +
                        "PRIMARY KEY (player_id, parliament_id)" +
                        ")";
                } else {
                    tableSql =
                        "CREATE TABLE IF NOT EXISTS parliament_members (" +
                        "player_id VARCHAR(36) NOT NULL," +
                        "parliament_id INT NOT NULL," +
                        "elected_at BIGINT NOT NULL," +
                        "term_ends_at BIGINT," +
                        "active BOOLEAN NOT NULL DEFAULT TRUE," +
                        "party_id INT," +
                        "constituency VARCHAR(100)," +
                        "votes_received INT NOT NULL DEFAULT 0," +
                        "bills_proposed INT NOT NULL DEFAULT 0," +
                        "votes_participated INT NOT NULL DEFAULT 0," +
                        "PRIMARY KEY (player_id, parliament_id)" +
                        ")";
                }
                stmt.execute(tableSql);
            }

            // 议案表
            try (Statement stmt = conn.createStatement()) {
                String tableSql;
                if (isSQLite) {
                    tableSql =
                        "CREATE TABLE IF NOT EXISTS parliament_bills (" +
                        "bill_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "parliament_id INTEGER NOT NULL," +
                        "proposer_id TEXT NOT NULL," +
                        "title TEXT NOT NULL," +
                        "content TEXT NOT NULL," +
                        "bill_type TEXT NOT NULL," +
                        "proposed_at INTEGER NOT NULL," +
                        "status TEXT NOT NULL," +
                        "voting_starts_at INTEGER," +
                        "voting_ends_at INTEGER," +
                        "votes_for INTEGER NOT NULL DEFAULT 0," +
                        "votes_against INTEGER NOT NULL DEFAULT 0," +
                        "votes_abstain INTEGER NOT NULL DEFAULT 0," +
                        "enacted_at INTEGER" +
                        ")";
                } else {
                    tableSql =
                        "CREATE TABLE IF NOT EXISTS parliament_bills (" +
                        "bill_id INT PRIMARY KEY AUTO_INCREMENT," +
                        "parliament_id INT NOT NULL," +
                        "proposer_id VARCHAR(36) NOT NULL," +
                        "title VARCHAR(500) NOT NULL," +
                        "content TEXT NOT NULL," +
                        "bill_type VARCHAR(20) NOT NULL," +
                        "proposed_at BIGINT NOT NULL," +
                        "status VARCHAR(20) NOT NULL," +
                        "voting_starts_at BIGINT," +
                        "voting_ends_at BIGINT," +
                        "votes_for INT NOT NULL DEFAULT 0," +
                        "votes_against INT NOT NULL DEFAULT 0," +
                        "votes_abstain INT NOT NULL DEFAULT 0," +
                        "enacted_at BIGINT" +
                        ")";
                }
                stmt.execute(tableSql);
            }

            // 投票记录表
            try (Statement stmt = conn.createStatement()) {
                String tableSql;
                if (isSQLite) {
                    tableSql =
                        "CREATE TABLE IF NOT EXISTS parliament_votes (" +
                        "vote_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "bill_id INTEGER NOT NULL," +
                        "voter_id TEXT NOT NULL," +
                        "choice TEXT NOT NULL," +
                        "voted_at INTEGER NOT NULL," +
                        "comment TEXT" +
                        ")";
                    stmt.execute(tableSql);
                    stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_vote_unique ON parliament_votes(bill_id, voter_id)");
                } else {
                    tableSql =
                        "CREATE TABLE IF NOT EXISTS parliament_votes (" +
                        "vote_id INT PRIMARY KEY AUTO_INCREMENT," +
                        "bill_id INT NOT NULL," +
                        "voter_id VARCHAR(36) NOT NULL," +
                        "choice VARCHAR(20) NOT NULL," +
                        "voted_at BIGINT NOT NULL," +
                        "comment TEXT," +
                        "UNIQUE KEY unique_vote (bill_id, voter_id)" +
                        ")";
                    stmt.execute(tableSql);
                }
            }

            plugin.getLogger().info("Parliament tables initialized");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize parliament tables", e);
        }
    }

    // ==================== 议会管理 ====================

    /**
     * 创建议会
     */
    public Optional<Parliament> createParliament(String nationId, String name,
                                                 int totalSeats, int termLengthDays) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "INSERT INTO parliament (nation_id, name, established_at, total_seats, term_length_days, active) " +
                 "VALUES (?, ?, ?, ?, ?, ?)",
                 Statement.RETURN_GENERATED_KEYS
             )) {
            Instant now = Instant.now();
            stmt.setString(1, nationId);
            stmt.setString(2, name);
            stmt.setLong(3, now.toEpochMilli());
            stmt.setInt(4, totalSeats);
            stmt.setInt(5, termLengthDays);
            stmt.setBoolean(6, true);
            stmt.executeUpdate();

            ResultSet rs = stmt.getGeneratedKeys();
            if (rs.next()) {
                int parliamentId = rs.getInt(1);
                Parliament parliament = new Parliament(parliamentId, nationId, name, now, totalSeats, termLengthDays);
                parliaments.put(parliamentId, parliament);
                return Optional.of(parliament);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create parliament", e);
        }

        return Optional.empty();
    }

    /**
     * 获取议会
     */
    public Optional<Parliament> getParliament(int parliamentId) {
        if (parliaments.containsKey(parliamentId)) {
            return Optional.of(parliaments.get(parliamentId));
        }

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT * FROM parliament WHERE parliament_id = ?"
             )) {
            stmt.setInt(1, parliamentId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                Parliament parliament = loadParliamentFromResultSet(rs);
                parliaments.put(parliamentId, parliament);
                return Optional.of(parliament);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load parliament", e);
        }

        return Optional.empty();
    }

    /**
     * 获取国家的议会
     */
    public Optional<Parliament> getParliamentByNation(String nationId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT * FROM parliament WHERE nation_id = ? AND active = TRUE"
             )) {
            stmt.setString(1, nationId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                Parliament parliament = loadParliamentFromResultSet(rs);
                parliaments.put(parliament.getParliamentId(), parliament);
                return Optional.of(parliament);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load parliament by nation", e);
        }

        return Optional.empty();
    }

    /**
     * 安排下次选举
     */
    public boolean scheduleNextElection(int parliamentId, Instant electionDate) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "UPDATE parliament SET next_election_at = ? WHERE parliament_id = ?"
             )) {
            stmt.setLong(1, electionDate.toEpochMilli());
            stmt.setInt(2, parliamentId);
            int updated = stmt.executeUpdate();

            if (updated > 0) {
                Parliament parliament = parliaments.get(parliamentId);
                if (parliament != null) {
                    parliament.setNextElectionAt(electionDate);
                }
                return true;
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to schedule election", e);
        }

        return false;
    }

    // ==================== 议员管理 ====================

    /**
     * 选举议员
     */
    public boolean electMember(int parliamentId, UUID playerId, Instant electedAt,
                               Instant termEndsAt, int votesReceived, String constituency) {
        Member member = new Member(playerId, parliamentId, electedAt);
        member.setTermEndsAt(termEndsAt);
        member.setVotesReceived(votesReceived);
        member.setConstituency(constituency);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "INSERT INTO parliament_members (player_id, parliament_id, elected_at, term_ends_at, " +
                 "active, votes_received, constituency, bills_proposed, votes_participated) " +
                 "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                 "ON DUPLICATE KEY UPDATE elected_at = ?, term_ends_at = ?, active = TRUE, " +
                 "votes_received = ?, constituency = ?"
             )) {
            stmt.setString(1, playerId.toString());
            stmt.setInt(2, parliamentId);
            stmt.setLong(3, electedAt.toEpochMilli());
            stmt.setLong(4, termEndsAt.toEpochMilli());
            stmt.setBoolean(5, true);
            stmt.setInt(6, votesReceived);
            stmt.setString(7, constituency);
            stmt.setInt(8, 0);
            stmt.setInt(9, 0);
            // ON DUPLICATE KEY UPDATE
            stmt.setLong(10, electedAt.toEpochMilli());
            stmt.setLong(11, termEndsAt.toEpochMilli());
            stmt.setInt(12, votesReceived);
            stmt.setString(13, constituency);
            stmt.executeUpdate();

            // 清除缓存
            membersByPlayer.remove(playerId);
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to elect member", e);
            return false;
        }
    }

    /**
     * 获取议员
     */
    public Optional<Member> getMember(int parliamentId, UUID playerId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT * FROM parliament_members WHERE parliament_id = ? AND player_id = ?"
             )) {
            stmt.setInt(1, parliamentId);
            stmt.setString(2, playerId.toString());
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return Optional.of(loadMemberFromResultSet(rs));
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load member", e);
        }

        return Optional.empty();
    }

    /**
     * 获取议会的所有活跃议员
     */
    public List<Member> getActiveMembers(int parliamentId) {
        List<Member> members = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT * FROM parliament_members WHERE parliament_id = ? AND active = TRUE"
             )) {
            stmt.setInt(1, parliamentId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                members.add(loadMemberFromResultSet(rs));
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load active members", e);
        }

        return members;
    }

    /**
     * 设置议员政党
     */
    public boolean setMemberParty(int parliamentId, UUID playerId, Integer partyId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "UPDATE parliament_members SET party_id = ? WHERE parliament_id = ? AND player_id = ?"
             )) {
            if (partyId != null) {
                stmt.setInt(1, partyId);
            } else {
                stmt.setNull(1, Types.INTEGER);
            }
            stmt.setInt(2, parliamentId);
            stmt.setString(3, playerId.toString());
            return stmt.executeUpdate() > 0;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to set member party", e);
            return false;
        }
    }

    /**
     * 停止议员职务
     */
    public boolean deactivateMember(int parliamentId, UUID playerId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "UPDATE parliament_members SET active = FALSE WHERE parliament_id = ? AND player_id = ?"
             )) {
            stmt.setInt(1, parliamentId);
            stmt.setString(2, playerId.toString());
            int updated = stmt.executeUpdate();

            if (updated > 0) {
                membersByPlayer.remove(playerId);
                return true;
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to deactivate member", e);
        }

        return false;
    }

    // ==================== 议案管理 ====================

    /**
     * 提交议案
     */
    public Optional<Bill> proposeBill(int parliamentId, UUID proposerId, String title,
                                      String content, Bill.BillType billType) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "INSERT INTO parliament_bills (parliament_id, proposer_id, title, content, bill_type, " +
                 "proposed_at, status, votes_for, votes_against, votes_abstain) " +
                 "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                 Statement.RETURN_GENERATED_KEYS
             )) {
            Instant now = Instant.now();
            stmt.setInt(1, parliamentId);
            stmt.setString(2, proposerId.toString());
            stmt.setString(3, title);
            stmt.setString(4, content);
            stmt.setString(5, billType.name());
            stmt.setLong(6, now.toEpochMilli());
            stmt.setString(7, Bill.BillStatus.PROPOSED.name());
            stmt.setInt(8, 0);
            stmt.setInt(9, 0);
            stmt.setInt(10, 0);
            stmt.executeUpdate();

            ResultSet rs = stmt.getGeneratedKeys();
            if (rs.next()) {
                int billId = rs.getInt(1);
                Bill bill = new Bill(billId, parliamentId, proposerId, title, content, billType, now);
                bills.put(billId, bill);

                // 增加议员提案数
                incrementMemberBillCount(parliamentId, proposerId);

                return Optional.of(bill);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to propose bill", e);
        }

        return Optional.empty();
    }

    /**
     * 获取议案
     */
    public Optional<Bill> getBill(int billId) {
        if (bills.containsKey(billId)) {
            return Optional.of(bills.get(billId));
        }

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT * FROM parliament_bills WHERE bill_id = ?"
             )) {
            stmt.setInt(1, billId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                Bill bill = loadBillFromResultSet(rs);
                bills.put(billId, bill);
                return Optional.of(bill);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load bill", e);
        }

        return Optional.empty();
    }

    /**
     * 安排议案投票
     */
    public boolean scheduleBillVoting(int billId, Instant votingStartsAt, Instant votingEndsAt) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "UPDATE parliament_bills SET status = ?, voting_starts_at = ?, voting_ends_at = ? " +
                 "WHERE bill_id = ?"
             )) {
            stmt.setString(1, Bill.BillStatus.SCHEDULED.name());
            stmt.setLong(2, votingStartsAt.toEpochMilli());
            stmt.setLong(3, votingEndsAt.toEpochMilli());
            stmt.setInt(4, billId);
            int updated = stmt.executeUpdate();

            if (updated > 0) {
                Bill bill = bills.get(billId);
                if (bill != null) {
                    bill.setStatus(Bill.BillStatus.SCHEDULED);
                    bill.setVotingStartsAt(votingStartsAt);
                    bill.setVotingEndsAt(votingEndsAt);
                }
                return true;
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to schedule bill voting", e);
        }

        return false;
    }

    /**
     * 开始投票
     */
    public boolean startVoting(int billId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "UPDATE parliament_bills SET status = ? WHERE bill_id = ?"
             )) {
            stmt.setString(1, Bill.BillStatus.VOTING.name());
            stmt.setInt(2, billId);
            int updated = stmt.executeUpdate();

            if (updated > 0) {
                Bill bill = bills.get(billId);
                if (bill != null) {
                    bill.setStatus(Bill.BillStatus.VOTING);
                }
                return true;
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to start voting", e);
        }

        return false;
    }

    // ==================== 投票管理 ====================

    /**
     * 投票
     */
    public Optional<Vote> castVote(int billId, UUID voterId, Vote.VoteChoice choice, String comment) {
        // 检查是否已投票
        if (hasVoted(billId, voterId)) {
            return Optional.empty();
        }

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "INSERT INTO parliament_votes (bill_id, voter_id, choice, voted_at, comment) " +
                 "VALUES (?, ?, ?, ?, ?)",
                 Statement.RETURN_GENERATED_KEYS
             )) {
            Instant now = Instant.now();
            stmt.setInt(1, billId);
            stmt.setString(2, voterId.toString());
            stmt.setString(3, choice.name());
            stmt.setLong(4, now.toEpochMilli());
            stmt.setString(5, comment);
            stmt.executeUpdate();

            ResultSet rs = stmt.getGeneratedKeys();
            if (rs.next()) {
                int voteId = rs.getInt(1);
                Vote vote = new Vote(voteId, billId, voterId, choice, now);
                if (comment != null) {
                    vote.setComment(comment);
                }

                // 更新议案投票统计
                updateBillVoteCount(billId, choice);

                // 获取议案所属议会
                Bill bill = bills.get(billId);
                if (bill != null) {
                    incrementMemberVoteCount(bill.getParliamentId(), voterId);
                }

                return Optional.of(vote);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to cast vote", e);
        }

        return Optional.empty();
    }

    /**
     * 检查是否已投票
     */
    public boolean hasVoted(int billId, UUID voterId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT COUNT(*) FROM parliament_votes WHERE bill_id = ? AND voter_id = ?"
             )) {
            stmt.setInt(1, billId);
            stmt.setString(2, voterId.toString());
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to check vote", e);
        }

        return false;
    }

    /**
     * 获取议案的投票记录
     */
    public List<Vote> getVotes(int billId) {
        List<Vote> votes = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT * FROM parliament_votes WHERE bill_id = ? ORDER BY voted_at ASC"
             )) {
            stmt.setInt(1, billId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                votes.add(loadVoteFromResultSet(rs));
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load votes", e);
        }

        return votes;
    }

    /**
     * 完成投票并确定结果
     */
    public boolean finalizeBillVoting(int billId, double requiredRate) {
        Bill bill = bills.get(billId);
        if (bill == null) {
            Optional<Bill> billOpt = getBill(billId);
            if (!billOpt.isPresent()) {
                return false;
            }
            bill = billOpt.get();
        }

        Bill.BillStatus newStatus = bill.isPassed(requiredRate) ?
                Bill.BillStatus.PASSED : Bill.BillStatus.REJECTED;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "UPDATE parliament_bills SET status = ? WHERE bill_id = ?"
             )) {
            stmt.setString(1, newStatus.name());
            stmt.setInt(2, billId);
            int updated = stmt.executeUpdate();

            if (updated > 0) {
                bill.setStatus(newStatus);
                return true;
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to finalize bill voting", e);
        }

        return false;
    }

    /**
     * 议案生效
     */
    public boolean enactBill(int billId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "UPDATE parliament_bills SET status = ?, enacted_at = ? WHERE bill_id = ?"
             )) {
            Instant now = Instant.now();
            stmt.setString(1, Bill.BillStatus.ENACTED.name());
            stmt.setLong(2, now.toEpochMilli());
            stmt.setInt(3, billId);
            int updated = stmt.executeUpdate();

            if (updated > 0) {
                Bill bill = bills.get(billId);
                if (bill != null) {
                    bill.setStatus(Bill.BillStatus.ENACTED);
                    bill.setEnactedAt(now);
                }
                return true;
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to enact bill", e);
        }

        return false;
    }

    // ==================== 查询方法 ====================

    /**
     * 获取议会的所有议案
     */
    public List<Bill> getBillsByParliament(int parliamentId) {
        List<Bill> result = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT * FROM parliament_bills WHERE parliament_id = ? ORDER BY proposed_at DESC"
             )) {
            stmt.setInt(1, parliamentId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                result.add(loadBillFromResultSet(rs));
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load bills by parliament", e);
        }

        return result;
    }

    /**
     * 获取待审议的议案
     */
    public List<Bill> getPendingBills(int parliamentId) {
        List<Bill> result = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT * FROM parliament_bills WHERE parliament_id = ? AND status = ? " +
                 "ORDER BY proposed_at ASC"
             )) {
            stmt.setInt(1, parliamentId);
            stmt.setString(2, Bill.BillStatus.PROPOSED.name());
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                result.add(loadBillFromResultSet(rs));
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load pending bills", e);
        }

        return result;
    }

    /**
     * 获取正在投票的议案
     */
    public List<Bill> getVotingBills(int parliamentId) {
        List<Bill> result = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT * FROM parliament_bills WHERE parliament_id = ? AND status = ? " +
                 "ORDER BY voting_ends_at ASC"
             )) {
            stmt.setInt(1, parliamentId);
            stmt.setString(2, Bill.BillStatus.VOTING.name());
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                result.add(loadBillFromResultSet(rs));
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load voting bills", e);
        }

        return result;
    }

    /**
     * 获取议员提出的议案
     */
    public List<Bill> getBillsByProposer(UUID proposerId) {
        List<Bill> result = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT * FROM parliament_bills WHERE proposer_id = ? ORDER BY proposed_at DESC"
             )) {
            stmt.setString(1, proposerId.toString());
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                result.add(loadBillFromResultSet(rs));
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load bills by proposer", e);
        }

        return result;
    }

    /**
     * 获取政党的议员数量
     */
    public int getPartyMemberCount(int parliamentId, int partyId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT COUNT(*) FROM parliament_members WHERE parliament_id = ? AND party_id = ? AND active = TRUE"
             )) {
            stmt.setInt(1, parliamentId);
            stmt.setInt(2, partyId);
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
     * 获取政党的所有议员
     */
    public List<Member> getMembersByParty(int parliamentId, int partyId) {
        List<Member> members = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT * FROM parliament_members WHERE parliament_id = ? AND party_id = ? AND active = TRUE"
             )) {
            stmt.setInt(1, parliamentId);
            stmt.setInt(2, partyId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                members.add(loadMemberFromResultSet(rs));
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load members by party", e);
        }

        return members;
    }

    // ==================== 辅助方法 ====================

    private void updateBillVoteCount(int billId, Vote.VoteChoice choice) throws SQLException {
        String column;
        switch (choice) {
            case FOR:
                column = "votes_for";
                break;
            case AGAINST:
                column = "votes_against";
                break;
            case ABSTAIN:
                column = "votes_abstain";
                break;
            default:
                return;
        }

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "UPDATE parliament_bills SET " + column + " = " + column + " + 1 WHERE bill_id = ?"
             )) {
            stmt.setInt(1, billId);
            stmt.executeUpdate();

            Bill bill = bills.get(billId);
            if (bill != null) {
                switch (choice) {
                    case FOR:
                        bill.setVotesFor(bill.getVotesFor() + 1);
                        break;
                    case AGAINST:
                        bill.setVotesAgainst(bill.getVotesAgainst() + 1);
                        break;
                    case ABSTAIN:
                        bill.setVotesAbstain(bill.getVotesAbstain() + 1);
                        break;
                }
            }
        }
    }

    private void incrementMemberBillCount(int parliamentId, UUID playerId) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "UPDATE parliament_members SET bills_proposed = bills_proposed + 1 " +
                 "WHERE parliament_id = ? AND player_id = ?"
             )) {
            stmt.setInt(1, parliamentId);
            stmt.setString(2, playerId.toString());
            stmt.executeUpdate();
        }
    }

    private void incrementMemberVoteCount(int parliamentId, UUID playerId) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "UPDATE parliament_members SET votes_participated = votes_participated + 1 " +
                 "WHERE parliament_id = ? AND player_id = ?"
             )) {
            stmt.setInt(1, parliamentId);
            stmt.setString(2, playerId.toString());
            stmt.executeUpdate();
        }
    }

    private Parliament loadParliamentFromResultSet(ResultSet rs) throws SQLException {
        int parliamentId = rs.getInt("parliament_id");
        String nationId = rs.getString("nation_id");
        String name = rs.getString("name");
        Instant establishedAt = Instant.ofEpochMilli(rs.getLong("established_at"));
        int totalSeats = rs.getInt("total_seats");
        int termLengthDays = rs.getInt("term_length_days");

        Parliament parliament = new Parliament(parliamentId, nationId, name, establishedAt, totalSeats, termLengthDays);
        parliament.setActive(rs.getBoolean("active"));

        long nextElection = rs.getLong("next_election_at");
        if (nextElection > 0) {
            parliament.setNextElectionAt(Instant.ofEpochMilli(nextElection));
        }

        return parliament;
    }

    private Member loadMemberFromResultSet(ResultSet rs) throws SQLException {
        UUID playerId = UUID.fromString(rs.getString("player_id"));
        int parliamentId = rs.getInt("parliament_id");
        Instant electedAt = Instant.ofEpochMilli(rs.getLong("elected_at"));

        Member member = new Member(playerId, parliamentId, electedAt);

        long termEnds = rs.getLong("term_ends_at");
        if (termEnds > 0) {
            member.setTermEndsAt(Instant.ofEpochMilli(termEnds));
        }

        member.setActive(rs.getBoolean("active"));

        int partyId = rs.getInt("party_id");
        if (!rs.wasNull()) {
            member.setPartyId(partyId);
        }

        String constituency = rs.getString("constituency");
        if (constituency != null) {
            member.setConstituency(constituency);
        }

        member.setVotesReceived(rs.getInt("votes_received"));
        member.setBillsProposed(rs.getInt("bills_proposed"));
        member.setVotesParticipated(rs.getInt("votes_participated"));

        return member;
    }

    private Bill loadBillFromResultSet(ResultSet rs) throws SQLException {
        int billId = rs.getInt("bill_id");
        int parliamentId = rs.getInt("parliament_id");
        UUID proposerId = UUID.fromString(rs.getString("proposer_id"));
        String title = rs.getString("title");
        String content = rs.getString("content");
        Bill.BillType billType = Bill.BillType.valueOf(rs.getString("bill_type"));
        Instant proposedAt = Instant.ofEpochMilli(rs.getLong("proposed_at"));

        Bill bill = new Bill(billId, parliamentId, proposerId, title, content, billType, proposedAt);
        bill.setStatus(Bill.BillStatus.valueOf(rs.getString("status")));

        long votingStarts = rs.getLong("voting_starts_at");
        if (votingStarts > 0) {
            bill.setVotingStartsAt(Instant.ofEpochMilli(votingStarts));
        }

        long votingEnds = rs.getLong("voting_ends_at");
        if (votingEnds > 0) {
            bill.setVotingEndsAt(Instant.ofEpochMilli(votingEnds));
        }

        bill.setVotesFor(rs.getInt("votes_for"));
        bill.setVotesAgainst(rs.getInt("votes_against"));
        bill.setVotesAbstain(rs.getInt("votes_abstain"));

        long enactedAt = rs.getLong("enacted_at");
        if (enactedAt > 0) {
            bill.setEnactedAt(Instant.ofEpochMilli(enactedAt));
        }

        return bill;
    }

    private Vote loadVoteFromResultSet(ResultSet rs) throws SQLException {
        int voteId = rs.getInt("vote_id");
        int billId = rs.getInt("bill_id");
        UUID voterId = UUID.fromString(rs.getString("voter_id"));
        Vote.VoteChoice choice = Vote.VoteChoice.valueOf(rs.getString("choice"));
        Instant votedAt = Instant.ofEpochMilli(rs.getLong("voted_at"));

        Vote vote = new Vote(voteId, billId, voterId, choice, votedAt);

        String comment = rs.getString("comment");
        if (comment != null) {
            vote.setComment(comment);
        }

        return vote;
    }
}
