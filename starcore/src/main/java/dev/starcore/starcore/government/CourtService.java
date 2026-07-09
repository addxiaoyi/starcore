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
 * 法庭服务
 * 管理法官、案件、陪审团、判决和上诉
 */
public final class CourtService {
    private final JavaPlugin plugin;
    private final DataSource dataSource;

    // 内存缓存（使用 ConcurrentHashMap 保证线程安全）
    private final Map<UUID, Judge> judges = new ConcurrentHashMap<>();
    private final Map<Integer, CourtCase> cases = new ConcurrentHashMap<>();
    private final Map<Integer, Jury> juries = new ConcurrentHashMap<>();

    public CourtService(JavaPlugin plugin, DataSource dataSource) {
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

            // 法官表
            try (Statement stmt = conn.createStatement()) {
                String tableSql;
                if (isSQLite) {
                    tableSql =
                        "CREATE TABLE IF NOT EXISTS court_judges (" +
                        "player_id TEXT PRIMARY KEY," +
                        "appointed_at INTEGER NOT NULL," +
                        "term_ends_at INTEGER," +
                        "active INTEGER NOT NULL DEFAULT 1," +
                        "cases_handled INTEGER NOT NULL DEFAULT 0," +
                        "specialization TEXT" +
                        ")";
                } else {
                    tableSql =
                        "CREATE TABLE IF NOT EXISTS court_judges (" +
                        "player_id VARCHAR(36) PRIMARY KEY," +
                        "appointed_at BIGINT NOT NULL," +
                        "term_ends_at BIGINT," +
                        "active BOOLEAN NOT NULL DEFAULT TRUE," +
                        "cases_handled INT NOT NULL DEFAULT 0," +
                        "specialization VARCHAR(100)" +
                        ")";
                }
                stmt.execute(tableSql);
            }

            // 案件表
            try (Statement stmt = conn.createStatement()) {
                String tableSql;
                if (isSQLite) {
                    tableSql =
                        "CREATE TABLE IF NOT EXISTS court_cases (" +
                        "case_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "plaintiff TEXT NOT NULL," +
                        "defendant TEXT NOT NULL," +
                        "case_type TEXT NOT NULL," +
                        "description TEXT NOT NULL," +
                        "filed_at INTEGER NOT NULL," +
                        "status TEXT NOT NULL," +
                        "assigned_judge TEXT," +
                        "verdict_id INTEGER," +
                        "hearing_date INTEGER," +
                        "evidence TEXT" +
                        ")";
                } else {
                    tableSql =
                        "CREATE TABLE IF NOT EXISTS court_cases (" +
                        "case_id INT PRIMARY KEY AUTO_INCREMENT," +
                        "plaintiff VARCHAR(36) NOT NULL," +
                        "defendant VARCHAR(36) NOT NULL," +
                        "case_type VARCHAR(20) NOT NULL," +
                        "description TEXT NOT NULL," +
                        "filed_at BIGINT NOT NULL," +
                        "status VARCHAR(20) NOT NULL," +
                        "assigned_judge VARCHAR(36)," +
                        "verdict_id INT," +
                        "hearing_date BIGINT," +
                        "evidence TEXT" +
                        ")";
                }
                stmt.execute(tableSql);
            }

            // 陪审团表
            try (Statement stmt = conn.createStatement()) {
                String tableSql;
                if (isSQLite) {
                    tableSql =
                        "CREATE TABLE IF NOT EXISTS court_jury (" +
                        "case_id INTEGER NOT NULL," +
                        "juror_id TEXT NOT NULL," +
                        "vote TEXT," +
                        "PRIMARY KEY (case_id, juror_id)" +
                        ")";
                } else {
                    tableSql =
                        "CREATE TABLE IF NOT EXISTS court_jury (" +
                        "case_id INT NOT NULL," +
                        "juror_id VARCHAR(36) NOT NULL," +
                        "vote VARCHAR(20)," +
                        "PRIMARY KEY (case_id, juror_id)" +
                        ")";
                }
                stmt.execute(tableSql);
            }

            // 判决表
            try (Statement stmt = conn.createStatement()) {
                String tableSql;
                if (isSQLite) {
                    tableSql =
                        "CREATE TABLE IF NOT EXISTS court_verdicts (" +
                        "verdict_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "case_id INTEGER NOT NULL," +
                        "judge_id TEXT NOT NULL," +
                        "verdict_type TEXT NOT NULL," +
                        "reasoning TEXT NOT NULL," +
                        "issued_at INTEGER NOT NULL," +
                        "fine_amount REAL," +
                        "jail_time_minutes INTEGER," +
                        "banishment INTEGER," +
                        "additional_conditions TEXT" +
                        ")";
                } else {
                    tableSql =
                        "CREATE TABLE IF NOT EXISTS court_verdicts (" +
                        "verdict_id INT PRIMARY KEY AUTO_INCREMENT," +
                        "case_id INT NOT NULL," +
                        "judge_id VARCHAR(36) NOT NULL," +
                        "verdict_type VARCHAR(20) NOT NULL," +
                        "reasoning TEXT NOT NULL," +
                        "issued_at BIGINT NOT NULL," +
                        "fine_amount DOUBLE," +
                        "jail_time_minutes INT," +
                        "banishment BOOLEAN," +
                        "additional_conditions TEXT" +
                        ")";
                }
                stmt.execute(tableSql);
            }

            // 上诉表
            try (Statement stmt = conn.createStatement()) {
                String tableSql;
                if (isSQLite) {
                    tableSql =
                        "CREATE TABLE IF NOT EXISTS court_appeals (" +
                        "appeal_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "original_case_id INTEGER NOT NULL," +
                        "original_verdict_id INTEGER NOT NULL," +
                        "appellant TEXT NOT NULL," +
                        "grounds TEXT NOT NULL," +
                        "filed_at INTEGER NOT NULL," +
                        "status TEXT NOT NULL," +
                        "assigned_judge TEXT," +
                        "new_verdict_id INTEGER," +
                        "reviewed_at INTEGER," +
                        "decision TEXT" +
                        ")";
                } else {
                    tableSql =
                        "CREATE TABLE IF NOT EXISTS court_appeals (" +
                        "appeal_id INT PRIMARY KEY AUTO_INCREMENT," +
                        "original_case_id INT NOT NULL," +
                        "original_verdict_id INT NOT NULL," +
                        "appellant VARCHAR(36) NOT NULL," +
                        "grounds TEXT NOT NULL," +
                        "filed_at BIGINT NOT NULL," +
                        "status VARCHAR(20) NOT NULL," +
                        "assigned_judge VARCHAR(36)," +
                        "new_verdict_id INT," +
                        "reviewed_at BIGINT," +
                        "decision TEXT" +
                        ")";
                }
                stmt.execute(tableSql);
            }

            plugin.getLogger().info("Court tables initialized");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize court tables", e);
        }
    }

    // ==================== 法官管理 ====================

    /**
     * 任命法官
     */
    public boolean appointJudge(UUID playerId, Instant appointedAt, Instant termEndsAt) {
        Judge judge = new Judge(playerId, appointedAt);
        judge.setTermEndsAt(termEndsAt);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "INSERT INTO court_judges (player_id, appointed_at, term_ends_at, active, cases_handled) " +
                 "VALUES (?, ?, ?, ?, ?)"
             )) {
            stmt.setString(1, playerId.toString());
            stmt.setLong(2, appointedAt.toEpochMilli());
            stmt.setLong(3, termEndsAt != null ? termEndsAt.toEpochMilli() : 0);
            stmt.setBoolean(4, true);
            stmt.setInt(5, 0);
            stmt.executeUpdate();

            judges.put(playerId, judge);
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to appoint judge", e);
            return false;
        }
    }

    /**
     * 获取法官
     */
    public Optional<Judge> getJudge(UUID playerId) {
        if (judges.containsKey(playerId)) {
            return Optional.of(judges.get(playerId));
        }

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT * FROM court_judges WHERE player_id = ?"
             )) {
            stmt.setString(1, playerId.toString());
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                Judge judge = loadJudgeFromResultSet(rs);
                judges.put(playerId, judge);
                return Optional.of(judge);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load judge", e);
        }

        return Optional.empty();
    }

    /**
     * 获取所有活跃法官
     */
    public List<Judge> getActiveJudges() {
        List<Judge> activeJudges = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT * FROM court_judges WHERE active = TRUE"
             )) {
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                Judge judge = loadJudgeFromResultSet(rs);
                judges.put(judge.getPlayerId(), judge);
                activeJudges.add(judge);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load active judges", e);
        }

        return activeJudges;
    }

    /**
     * 停职法官
     */
    public boolean deactivateJudge(UUID playerId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "UPDATE court_judges SET active = FALSE WHERE player_id = ?"
             )) {
            stmt.setString(1, playerId.toString());
            int updated = stmt.executeUpdate();

            if (updated > 0) {
                Judge judge = judges.get(playerId);
                if (judge != null) {
                    judge.setActive(false);
                }
                return true;
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to deactivate judge", e);
        }

        return false;
    }

    // ==================== 案件管理 ====================

    /**
     * 提交案件
     */
    public Optional<CourtCase> fileCase(UUID plaintiff, UUID defendant,
                                        CourtCase.CaseType caseType, String description) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "INSERT INTO court_cases (plaintiff, defendant, case_type, description, filed_at, status) " +
                 "VALUES (?, ?, ?, ?, ?, ?)",
                 Statement.RETURN_GENERATED_KEYS
             )) {
            Instant now = Instant.now();
            stmt.setString(1, plaintiff.toString());
            stmt.setString(2, defendant.toString());
            stmt.setString(3, caseType.name());
            stmt.setString(4, description);
            stmt.setLong(5, now.toEpochMilli());
            stmt.setString(6, CourtCase.CaseStatus.FILED.name());
            stmt.executeUpdate();

            ResultSet rs = stmt.getGeneratedKeys();
            if (rs.next()) {
                int caseId = rs.getInt(1);
                CourtCase courtCase = new CourtCase(caseId, plaintiff, defendant, caseType, description, now);
                cases.put(caseId, courtCase);
                return Optional.of(courtCase);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to file case", e);
        }

        return Optional.empty();
    }

    /**
     * 获取案件
     */
    public Optional<CourtCase> getCase(int caseId) {
        if (cases.containsKey(caseId)) {
            return Optional.of(cases.get(caseId));
        }

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT * FROM court_cases WHERE case_id = ?"
             )) {
            stmt.setInt(1, caseId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                CourtCase courtCase = loadCaseFromResultSet(rs);
                cases.put(caseId, courtCase);
                return Optional.of(courtCase);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load case", e);
        }

        return Optional.empty();
    }

    /**
     * 分配法官到案件
     */
    public boolean assignJudgeToCase(int caseId, UUID judgeId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "UPDATE court_cases SET assigned_judge = ?, status = ? WHERE case_id = ?"
             )) {
            stmt.setString(1, judgeId.toString());
            stmt.setString(2, CourtCase.CaseStatus.UNDER_REVIEW.name());
            stmt.setInt(3, caseId);
            int updated = stmt.executeUpdate();

            if (updated > 0) {
                CourtCase courtCase = cases.get(caseId);
                if (courtCase != null) {
                    courtCase.setAssignedJudge(judgeId);
                    courtCase.setStatus(CourtCase.CaseStatus.UNDER_REVIEW);
                }
                return true;
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to assign judge to case", e);
        }

        return false;
    }

    /**
     * 安排听证日期
     */
    public boolean scheduleHearing(int caseId, Instant hearingDate) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "UPDATE court_cases SET hearing_date = ?, status = ? WHERE case_id = ?"
             )) {
            stmt.setLong(1, hearingDate.toEpochMilli());
            stmt.setString(2, CourtCase.CaseStatus.SCHEDULED.name());
            stmt.setInt(3, caseId);
            int updated = stmt.executeUpdate();

            if (updated > 0) {
                CourtCase courtCase = cases.get(caseId);
                if (courtCase != null) {
                    courtCase.setHearingDate(hearingDate);
                    courtCase.setStatus(CourtCase.CaseStatus.SCHEDULED);
                }
                return true;
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to schedule hearing", e);
        }

        return false;
    }

    // ==================== 陪审团管理 ====================

    /**
     * 随机抽取陪审团
     */
    public Optional<Jury> selectJury(int caseId, List<UUID> eligiblePlayers, int jurySize) {
        if (eligiblePlayers.size() < jurySize) {
            return Optional.empty();
        }

        // 随机打乱并选择
        List<UUID> shuffled = new ArrayList<>(eligiblePlayers);
        Collections.shuffle(shuffled);
        List<UUID> selectedJurors = shuffled.subList(0, jurySize);

        Jury jury = new Jury(caseId, selectedJurors);

        // 保存到数据库
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "INSERT INTO court_jury (case_id, juror_id) VALUES (?, ?)"
             )) {
            for (UUID jurorId : selectedJurors) {
                stmt.setInt(1, caseId);
                stmt.setString(2, jurorId.toString());
                stmt.addBatch();
            }
            stmt.executeBatch();

            // 更新案件状态
            CourtCase courtCase = cases.get(caseId);
            if (courtCase != null) {
                courtCase.setJury(selectedJurors);
            }

            juries.put(caseId, jury);
            return Optional.of(jury);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to select jury", e);
        }

        return Optional.empty();
    }

    /**
     * 获取陪审团
     */
    public Optional<Jury> getJury(int caseId) {
        if (juries.containsKey(caseId)) {
            return Optional.of(juries.get(caseId));
        }

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT juror_id, vote FROM court_jury WHERE case_id = ?"
             )) {
            stmt.setInt(1, caseId);
            ResultSet rs = stmt.executeQuery();

            List<UUID> jurors = new ArrayList<>();
            Map<UUID, Jury.JuryVote> votes = new HashMap<>();

            while (rs.next()) {
                UUID jurorId = UUID.fromString(rs.getString("juror_id"));
                jurors.add(jurorId);

                String voteStr = rs.getString("vote");
                if (voteStr != null) {
                    votes.put(jurorId, Jury.JuryVote.valueOf(voteStr));
                }
            }

            if (!jurors.isEmpty()) {
                Jury jury = new Jury(caseId, jurors);
                for (Map.Entry<UUID, Jury.JuryVote> entry : votes.entrySet()) {
                    jury.castVote(entry.getKey(), entry.getValue());
                }
                juries.put(caseId, jury);
                return Optional.of(jury);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load jury", e);
        }

        return Optional.empty();
    }

    /**
     * 记录陪审团投票
     */
    public boolean recordJuryVote(int caseId, UUID jurorId, Jury.JuryVote vote) {
        Jury jury = juries.get(caseId);
        if (jury == null || !jury.isJuror(jurorId)) {
            return false;
        }

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "UPDATE court_jury SET vote = ? WHERE case_id = ? AND juror_id = ?"
             )) {
            stmt.setString(1, vote.name());
            stmt.setInt(2, caseId);
            stmt.setString(3, jurorId.toString());
            int updated = stmt.executeUpdate();

            if (updated > 0) {
                jury.castVote(jurorId, vote);
                return true;
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to record jury vote", e);
        }

        return false;
    }

    // ==================== 判决管理 ====================

    /**
     * 发布判决
     */
    public Optional<Verdict> issueVerdict(int caseId, UUID judgeId, Verdict.VerdictType verdictType,
                                          String reasoning) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "INSERT INTO court_verdicts (case_id, judge_id, verdict_type, reasoning, issued_at) " +
                 "VALUES (?, ?, ?, ?, ?)",
                 Statement.RETURN_GENERATED_KEYS
             )) {
            Instant now = Instant.now();
            stmt.setInt(1, caseId);
            stmt.setString(2, judgeId.toString());
            stmt.setString(3, verdictType.name());
            stmt.setString(4, reasoning);
            stmt.setLong(5, now.toEpochMilli());
            stmt.executeUpdate();

            ResultSet rs = stmt.getGeneratedKeys();
            if (rs.next()) {
                int verdictId = rs.getInt(1);
                Verdict verdict = new Verdict(verdictId, caseId, judgeId, verdictType, reasoning, now);

                // 更新案件状态
                updateCaseVerdict(caseId, verdictId);

                // 增加法官处理案件数
                incrementJudgeCaseCount(judgeId);

                return Optional.of(verdict);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to issue verdict", e);
        }

        return Optional.empty();
    }

    /**
     * 获取判决
     */
    public Optional<Verdict> getVerdict(int verdictId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT * FROM court_verdicts WHERE verdict_id = ?"
             )) {
            stmt.setInt(1, verdictId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return Optional.of(loadVerdictFromResultSet(rs));
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load verdict", e);
        }

        return Optional.empty();
    }

    /**
     * 设置判决惩罚
     */
    public boolean setVerdictPunishment(int verdictId, Double fineAmount,
                                        Integer jailTimeMinutes, Boolean banishment) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "UPDATE court_verdicts SET fine_amount = ?, jail_time_minutes = ?, banishment = ? " +
                 "WHERE verdict_id = ?"
             )) {
            if (fineAmount != null) {
                stmt.setDouble(1, fineAmount);
            } else {
                stmt.setNull(1, Types.DOUBLE);
            }

            if (jailTimeMinutes != null) {
                stmt.setInt(2, jailTimeMinutes);
            } else {
                stmt.setNull(2, Types.INTEGER);
            }

            if (banishment != null) {
                stmt.setBoolean(3, banishment);
            } else {
                stmt.setNull(3, Types.BOOLEAN);
            }

            stmt.setInt(4, verdictId);
            return stmt.executeUpdate() > 0;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to set verdict punishment", e);
            return false;
        }
    }

    // ==================== 上诉管理 ====================

    /**
     * 提交上诉
     */
    public Optional<Appeal> fileAppeal(int originalCaseId, int originalVerdictId,
                                       UUID appellant, String grounds) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "INSERT INTO court_appeals (original_case_id, original_verdict_id, appellant, grounds, filed_at, status) " +
                 "VALUES (?, ?, ?, ?, ?, ?)",
                 Statement.RETURN_GENERATED_KEYS
             )) {
            Instant now = Instant.now();
            stmt.setInt(1, originalCaseId);
            stmt.setInt(2, originalVerdictId);
            stmt.setString(3, appellant.toString());
            stmt.setString(4, grounds);
            stmt.setLong(5, now.toEpochMilli());
            stmt.setString(6, Appeal.AppealStatus.FILED.name());
            stmt.executeUpdate();

            ResultSet rs = stmt.getGeneratedKeys();
            if (rs.next()) {
                int appealId = rs.getInt(1);
                Appeal appeal = new Appeal(appealId, originalCaseId, originalVerdictId, appellant, grounds, now);

                // 更新原案件状态为已上诉
                updateCaseStatus(originalCaseId, CourtCase.CaseStatus.APPEALED);

                return Optional.of(appeal);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to file appeal", e);
        }

        return Optional.empty();
    }

    /**
     * 获取上诉
     */
    public Optional<Appeal> getAppeal(int appealId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT * FROM court_appeals WHERE appeal_id = ?"
             )) {
            stmt.setInt(1, appealId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return Optional.of(loadAppealFromResultSet(rs));
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load appeal", e);
        }

        return Optional.empty();
    }

    /**
     * 分配法官到上诉案件
     */
    public boolean assignJudgeToAppeal(int appealId, UUID judgeId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "UPDATE court_appeals SET assigned_judge = ?, status = ? WHERE appeal_id = ?"
             )) {
            stmt.setString(1, judgeId.toString());
            stmt.setString(2, Appeal.AppealStatus.UNDER_REVIEW.name());
            stmt.setInt(3, appealId);
            return stmt.executeUpdate() > 0;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to assign judge to appeal", e);
            return false;
        }
    }

    /**
     * 完成上诉审查
     */
    public boolean completeAppeal(int appealId, Appeal.AppealStatus finalStatus,
                                  String decision, Integer newVerdictId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "UPDATE court_appeals SET status = ?, decision = ?, new_verdict_id = ?, reviewed_at = ? " +
                 "WHERE appeal_id = ?"
             )) {
            stmt.setString(1, finalStatus.name());
            stmt.setString(2, decision);
            if (newVerdictId != null) {
                stmt.setInt(3, newVerdictId);
            } else {
                stmt.setNull(3, Types.INTEGER);
            }
            stmt.setLong(4, Instant.now().toEpochMilli());
            stmt.setInt(5, appealId);
            return stmt.executeUpdate() > 0;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to complete appeal", e);
            return false;
        }
    }

    // ==================== 判决执行相关 ====================

    /**
     * 获取已发布但未执行的判决
     */
    public List<Verdict> getUnexecutedVerdicts() {
        List<Verdict> result = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT * FROM court_verdicts v " +
                 "WHERE v.verdict_type = 'GUILTY' " +
                 "AND (v.fine_amount IS NOT NULL OR v.jail_time_minutes IS NOT NULL OR v.banishment = TRUE) " +
                 "AND NOT EXISTS (SELECT 1 FROM court_appeals a WHERE a.original_verdict_id = v.verdict_id AND a.status = 'REVERSED') " +
                 "ORDER BY v.issued_at DESC"
             )) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                result.add(loadVerdictFromResultSet(rs));
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load unexecuted verdicts", e);
        }

        return result;
    }

    /**
     * 获取案件的所有判决
     */
    public List<Verdict> getVerdictsByCase(int caseId) {
        List<Verdict> result = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT * FROM court_verdicts WHERE case_id = ? ORDER BY issued_at DESC"
             )) {
            stmt.setInt(1, caseId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                result.add(loadVerdictFromResultSet(rs));
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load verdicts by case", e);
        }

        return result;
    }

    /**
     * 获取案件关联的上诉记录
     */
    public List<Appeal> getAppealsByCase(int caseId) {
        List<Appeal> result = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT * FROM court_appeals WHERE original_case_id = ? ORDER BY filed_at DESC"
             )) {
            stmt.setInt(1, caseId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                result.add(loadAppealFromResultSet(rs));
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load appeals by case", e);
        }

        return result;
    }

    /**
     * 获取待处理的上诉
     */
    public List<Appeal> getPendingAppeals() {
        List<Appeal> result = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT * FROM court_appeals WHERE status IN ('FILED', 'UNDER_REVIEW', 'SCHEDULED', 'IN_HEARING') " +
                 "ORDER BY filed_at ASC"
             )) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                result.add(loadAppealFromResultSet(rs));
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load pending appeals", e);
        }

        return result;
    }

    /**
     * 检查上诉是否已撤销原判决
     */
    public boolean isOriginalVerdictReversed(int verdictId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT COUNT(*) FROM court_appeals WHERE original_verdict_id = ? AND status = 'REVERSED'"
             )) {
            stmt.setInt(1, verdictId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to check reversed verdict", e);
        }
        return false;
    }

    // ==================== 查询方法 ====================

    /**
     * 获取玩家作为原告的案件
     */
    public List<CourtCase> getCasesByPlaintiff(UUID playerId) {
        List<CourtCase> result = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT * FROM court_cases WHERE plaintiff = ? ORDER BY filed_at DESC"
             )) {
            stmt.setString(1, playerId.toString());
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                result.add(loadCaseFromResultSet(rs));
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load cases by plaintiff", e);
        }

        return result;
    }

    /**
     * 获取玩家作为被告的案件
     */
    public List<CourtCase> getCasesByDefendant(UUID playerId) {
        List<CourtCase> result = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT * FROM court_cases WHERE defendant = ? ORDER BY filed_at DESC"
             )) {
            stmt.setString(1, playerId.toString());
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                result.add(loadCaseFromResultSet(rs));
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load cases by defendant", e);
        }

        return result;
    }

    /**
     * 获取法官处理的案件
     */
    public List<CourtCase> getCasesByJudge(UUID judgeId) {
        List<CourtCase> result = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT * FROM court_cases WHERE assigned_judge = ? ORDER BY filed_at DESC"
             )) {
            stmt.setString(1, judgeId.toString());
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                result.add(loadCaseFromResultSet(rs));
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load cases by judge", e);
        }

        return result;
    }

    /**
     * 获取待分配的案件
     */
    public List<CourtCase> getPendingCases() {
        List<CourtCase> result = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT * FROM court_cases WHERE status = ? ORDER BY filed_at ASC"
             )) {
            stmt.setString(1, CourtCase.CaseStatus.FILED.name());
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                result.add(loadCaseFromResultSet(rs));
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load pending cases", e);
        }

        return result;
    }

    // ==================== 辅助方法 ====================

    private void updateCaseVerdict(int caseId, int verdictId) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "UPDATE court_cases SET verdict_id = ?, status = ? WHERE case_id = ?"
             )) {
            stmt.setInt(1, verdictId);
            stmt.setString(2, CourtCase.CaseStatus.VERDICT.name());
            stmt.setInt(3, caseId);
            stmt.executeUpdate();

            CourtCase courtCase = cases.get(caseId);
            if (courtCase != null) {
                courtCase.setVerdictId(verdictId);
                courtCase.setStatus(CourtCase.CaseStatus.VERDICT);
            }
        }
    }

    private void updateCaseStatus(int caseId, CourtCase.CaseStatus status) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "UPDATE court_cases SET status = ? WHERE case_id = ?"
             )) {
            stmt.setString(1, status.name());
            stmt.setInt(2, caseId);
            stmt.executeUpdate();

            CourtCase courtCase = cases.get(caseId);
            if (courtCase != null) {
                courtCase.setStatus(status);
            }
        }
    }

    private void incrementJudgeCaseCount(UUID judgeId) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "UPDATE court_judges SET cases_handled = cases_handled + 1 WHERE player_id = ?"
             )) {
            stmt.setString(1, judgeId.toString());
            stmt.executeUpdate();

            Judge judge = judges.get(judgeId);
            if (judge != null) {
                judge.incrementCasesHandled();
            }
        }
    }

    private Judge loadJudgeFromResultSet(ResultSet rs) throws SQLException {
        UUID playerId = UUID.fromString(rs.getString("player_id"));
        Instant appointedAt = Instant.ofEpochMilli(rs.getLong("appointed_at"));
        Judge judge = new Judge(playerId, appointedAt);

        long termEnds = rs.getLong("term_ends_at");
        if (termEnds > 0) {
            judge.setTermEndsAt(Instant.ofEpochMilli(termEnds));
        }

        judge.setActive(rs.getBoolean("active"));

        String specialization = rs.getString("specialization");
        if (specialization != null) {
            judge.setSpecialization(specialization);
        }

        return judge;
    }

    private CourtCase loadCaseFromResultSet(ResultSet rs) throws SQLException {
        int caseId = rs.getInt("case_id");
        UUID plaintiff = UUID.fromString(rs.getString("plaintiff"));
        UUID defendant = UUID.fromString(rs.getString("defendant"));
        CourtCase.CaseType caseType = CourtCase.CaseType.valueOf(rs.getString("case_type"));
        String description = rs.getString("description");
        Instant filedAt = Instant.ofEpochMilli(rs.getLong("filed_at"));

        CourtCase courtCase = new CourtCase(caseId, plaintiff, defendant, caseType, description, filedAt);
        courtCase.setStatus(CourtCase.CaseStatus.valueOf(rs.getString("status")));

        String judgeStr = rs.getString("assigned_judge");
        if (judgeStr != null) {
            courtCase.setAssignedJudge(UUID.fromString(judgeStr));
        }

        int verdictId = rs.getInt("verdict_id");
        if (!rs.wasNull()) {
            courtCase.setVerdictId(verdictId);
        }

        long hearingDate = rs.getLong("hearing_date");
        if (hearingDate > 0) {
            courtCase.setHearingDate(Instant.ofEpochMilli(hearingDate));
        }

        String evidence = rs.getString("evidence");
        if (evidence != null) {
            courtCase.setEvidence(evidence);
        }

        return courtCase;
    }

    private Verdict loadVerdictFromResultSet(ResultSet rs) throws SQLException {
        int verdictId = rs.getInt("verdict_id");
        int caseId = rs.getInt("case_id");
        UUID judgeId = UUID.fromString(rs.getString("judge_id"));
        Verdict.VerdictType verdictType = Verdict.VerdictType.valueOf(rs.getString("verdict_type"));
        String reasoning = rs.getString("reasoning");
        Instant issuedAt = Instant.ofEpochMilli(rs.getLong("issued_at"));

        Verdict verdict = new Verdict(verdictId, caseId, judgeId, verdictType, reasoning, issuedAt);

        double fineAmount = rs.getDouble("fine_amount");
        if (!rs.wasNull()) {
            verdict.setFineAmount(fineAmount);
        }

        int jailTime = rs.getInt("jail_time_minutes");
        if (!rs.wasNull()) {
            verdict.setJailTimeMinutes(jailTime);
        }

        boolean banishment = rs.getBoolean("banishment");
        if (!rs.wasNull()) {
            verdict.setBanishment(banishment);
        }

        String additionalConditions = rs.getString("additional_conditions");
        if (additionalConditions != null) {
            verdict.setAdditionalConditions(additionalConditions);
        }

        return verdict;
    }

    private Appeal loadAppealFromResultSet(ResultSet rs) throws SQLException {
        int appealId = rs.getInt("appeal_id");
        int originalCaseId = rs.getInt("original_case_id");
        int originalVerdictId = rs.getInt("original_verdict_id");
        UUID appellant = UUID.fromString(rs.getString("appellant"));
        String grounds = rs.getString("grounds");
        Instant filedAt = Instant.ofEpochMilli(rs.getLong("filed_at"));

        Appeal appeal = new Appeal(appealId, originalCaseId, originalVerdictId, appellant, grounds, filedAt);
        appeal.setStatus(Appeal.AppealStatus.valueOf(rs.getString("status")));

        String judgeStr = rs.getString("assigned_judge");
        if (judgeStr != null) {
            appeal.setAssignedJudge(UUID.fromString(judgeStr));
        }

        int newVerdictId = rs.getInt("new_verdict_id");
        if (!rs.wasNull()) {
            appeal.setNewVerdictId(newVerdictId);
        }

        long reviewedAt = rs.getLong("reviewed_at");
        if (reviewedAt > 0) {
            appeal.setReviewedAt(Instant.ofEpochMilli(reviewedAt));
        }

        String decision = rs.getString("decision");
        if (decision != null) {
            appeal.setDecision(decision);
        }

        return appeal;
    }
}
