package dev.starcore.starcore.ranking.database;

import dev.starcore.starcore.ranking.*;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * MySQL排行榜数据库实现
 * 基于ajLeaderboards的增量Delta设计
 *
 * 支持多种统计类型：kills, deaths, playtime 等
 */
public class MySQLRankingDatabase implements RankingDatabase {
    private static final Logger LOGGER = Logger.getLogger(MySQLRankingDatabase.class.getName());

    private final HikariDataSource dataSource;

    public MySQLRankingDatabase(String host, int port, String database,
                               String username, String password) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(10);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);

        this.dataSource = new HikariDataSource(config);

        // 初始化表
        initTables();
    }

    /**
     * 初始化数据库表
     */
    private void initTables() {
        String createTable = """
            CREATE TABLE IF NOT EXISTS nation_rankings (
                player_uuid VARCHAR(36) NOT NULL,
                stat_type VARCHAR(50) NOT NULL,
                period VARCHAR(20) NOT NULL,
                value BIGINT NOT NULL DEFAULT 0,
                delta BIGINT NOT NULL DEFAULT 0,
                lasttotal BIGINT NOT NULL DEFAULT 0,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                PRIMARY KEY (player_uuid, stat_type, period),
                INDEX idx_stat_period (stat_type, period),
                INDEX idx_value (stat_type, period, value DESC),
                INDEX idx_delta (stat_type, period, delta DESC)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
            """;

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(createTable);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize tables", e);
        }
    }

    @Override
    public int queryPosition(UUID player, String statType, RankPeriod period) {
        // 使用窗口函数优化排名查询
        String sortField = period == RankPeriod.ALLTIME ? "value" : "delta";

        String sql = """
            SELECT rank_value FROM (
                SELECT player_uuid, %s AS score,
                       RANK() OVER (ORDER BY %s DESC) AS rank_value
                FROM nation_rankings
                WHERE stat_type = ?
                AND period = ?
            ) ranked
            WHERE player_uuid = ?
            """.formatted(sortField, sortField);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, statType);
            stmt.setString(2, period.name());
            stmt.setString(3, player.toString());

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("rank_value");
            }

            return -1;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Database error", e);
            return -1;
        }
    }

    @Override
    public PlayerRankingData queryStats(UUID player) {
        PlayerRankingData data = new PlayerRankingData(player);

        // 查询所有 stat_type 的数据（支持 kills, deaths, playtime 等）
        String sql = """
            SELECT stat_type, period, value, delta
            FROM nation_rankings
            WHERE player_uuid = ?
            AND stat_type IN ('kills', 'deaths', 'playtime')
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, player.toString());

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String statType = rs.getString("stat_type");
                String periodName = rs.getString("period");
                long value = rs.getLong("value");
                long delta = rs.getLong("delta");

                RankPeriod period = RankPeriod.valueOf(periodName);

                // 根据 stat_type 和周期填充数据
                switch (statType) {
                    case "kills" -> {
                        long val = period == RankPeriod.ALLTIME ? value : delta;
                        setStatValue(data, "kills", period, val);
                    }
                    case "deaths" -> {
                        long val = period == RankPeriod.ALLTIME ? value : delta;
                        setStatValue(data, "deaths", period, val);
                    }
                    case "playtime" -> {
                        long val = period == RankPeriod.ALLTIME ? value : delta;
                        setStatValue(data, "playtime", period, val);
                    }
                }
            }

            // 查询 kills 排名
            for (RankPeriod period : RankPeriod.values()) {
                int position = queryPosition(player, "kills", period);
                switch (period) {
                    case ALLTIME -> data.setAllTimePosition(position);
                    case DAILY -> data.setDailyPosition(position);
                    case WEEKLY -> data.setWeeklyPosition(position);
                    case MONTHLY -> data.setMonthlyPosition(position);
                }
            }

            return data;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Database error", e);
            return data;
        }
    }

    /**
     * 设置 PlayerRankingData 中的统计值
     */
    private void setStatValue(PlayerRankingData data, String statType, RankPeriod period, long value) {
        switch (statType) {
            case "kills" -> {
                switch (period) {
                    case ALLTIME -> data.setAllTimeValue(value);
                    case DAILY -> data.setDailyValue(value);
                    case WEEKLY -> data.setWeeklyValue(value);
                    case MONTHLY -> data.setMonthlyValue(value);
                }
            }
            case "deaths" -> {
                // 扩展: deaths 数据处理（需要 PlayerRankingData 扩展后支持）
            }
            case "playtime" -> {
                // 扩展: playtime 数据处理（需要 PlayerRankingData 扩展后支持）
            }
        }
    }

    @Override
    public int querySize(String statType) {
        String sql = """
            SELECT COUNT(DISTINCT player_uuid) AS total
            FROM nation_rankings
            WHERE stat_type = ?
            AND value > 0
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, statType);

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("total");
            }

            return 0;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Database error", e);
            return 0;
        }
    }

    @Override
    public void updateStats(UUID player, String statType, RankPeriod period, long newValue) {
        String sql = """
            INSERT INTO nation_rankings (player_uuid, stat_type, period, value, delta, lasttotal)
            VALUES (?, ?, ?, ?, 0, 0)
            ON DUPLICATE KEY UPDATE
                value = VALUES(value),
                delta = VALUES(value) - lasttotal
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, player.toString());
            stmt.setString(2, statType);
            stmt.setString(3, period.name());
            stmt.setLong(4, newValue);

            stmt.executeUpdate();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Database error", e);
        }
    }

    @Override
    public void updateStatsWithDelta(UUID player, String statType, RankPeriod period, long delta) {
        // ALLTIME 使用绝对值累加，周期赛使用增量累加
        String sql = """
            INSERT INTO nation_rankings (player_uuid, stat_type, period, value, delta, lasttotal)
            VALUES (?, ?, ?, ?, ?, 0)
            ON DUPLICATE KEY UPDATE
                value = VALUES(value),
                delta = CASE
                    WHEN period = 'ALLTIME' THEN VALUES(delta)
                    ELSE delta + VALUES(delta)
                END
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, player.toString());
            stmt.setString(2, statType);
            stmt.setString(3, period.name());
            stmt.setLong(4, delta);
            stmt.setLong(5, delta);

            stmt.executeUpdate();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Database error", e);
        }
    }

    @Override
    public void resetPeriod(String statType, RankPeriod period) {
        if (!period.shouldReset()) {
            return; // ALLTIME不重置
        }

        String sql = """
            UPDATE nation_rankings
            SET lasttotal = value,
                delta = 0
            WHERE stat_type = ?
            AND period = ?
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, statType);
            stmt.setString(2, period.name());

            stmt.executeUpdate();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Database error", e);
        }
    }

    @Override
    public void batchUpdate(List<RankingUpdate> updates) {
        if (updates.isEmpty()) return;

        String sql = """
            INSERT INTO nation_rankings (player_uuid, stat_type, period, value, delta, lasttotal)
            VALUES (?, ?, ?, ?, 0, 0)
            ON DUPLICATE KEY UPDATE
                value = VALUES(value),
                delta = VALUES(value) - lasttotal
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            conn.setAutoCommit(false);

            for (RankingUpdate update : updates) {
                stmt.setString(1, update.player().toString());
                stmt.setString(2, update.statType());
                stmt.setString(3, update.period().name());
                stmt.setLong(4, update.delta());
                stmt.addBatch();
            }

            stmt.executeBatch();
            conn.commit();

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Database error", e);
        }
    }

    @Override
    public void batchUpdateWithDelta(List<RankingUpdate> updates) {
        if (updates.isEmpty()) return;

        // ALLTIME 使用绝对值，周期赛使用增量
        String sql = """
            INSERT INTO nation_rankings (player_uuid, stat_type, period, value, delta, lasttotal)
            VALUES (?, ?, ?, ?, ?, 0)
            ON DUPLICATE KEY UPDATE
                value = VALUES(value),
                delta = CASE
                    WHEN period = 'ALLTIME' THEN VALUES(delta)
                    ELSE delta + VALUES(delta)
                END
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            conn.setAutoCommit(false);

            for (RankingUpdate update : updates) {
                stmt.setString(1, update.player().toString());
                stmt.setString(2, update.statType());
                stmt.setString(3, update.period().name());
                stmt.setLong(4, update.delta());
                stmt.setLong(5, update.delta());
                stmt.addBatch();
            }

            stmt.executeBatch();
            conn.commit();

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Database error", e);
        }
    }

    /**
     * 获取Top N排行榜
     */
    @Override
    public List<RankingEntry> getTopPlayers(String statType, RankPeriod period, int limit) {
        String sortField = period == RankPeriod.ALLTIME ? "value" : "delta";

        // 使用窗口函数直接获取排名
        String sql = """
            SELECT player_uuid, score, rank_value
            FROM (
                SELECT player_uuid, %s AS score,
                       RANK() OVER (ORDER BY %s DESC) AS rank_value
                FROM nation_rankings
                WHERE stat_type = ?
                AND period = ?
            ) ranked
            WHERE rank_value <= ?
            ORDER BY rank_value
            """.formatted(sortField, sortField);

        List<RankingEntry> result = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, statType);
            stmt.setString(2, period.name());
            stmt.setInt(3, limit);

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                UUID playerId = UUID.fromString(rs.getString("player_uuid"));
                long score = rs.getLong("score");
                int rank = rs.getInt("rank_value");
                result.add(new RankingEntry(rank, playerId, score));
            }

            return result;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Database error", e);
            return result;
        }
    }

    /**
     * 查询指定玩家的统计值
     */
    public long queryValue(UUID player, String statType, RankPeriod period) {
        String sql = """
            SELECT %s AS score
            FROM nation_rankings
            WHERE player_uuid = ?
            AND stat_type = ?
            AND period = ?
            """.formatted(period == RankPeriod.ALLTIME ? "value" : "delta");

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, player.toString());
            stmt.setString(2, statType);
            stmt.setString(3, period.name());

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getLong("score");
            }

            return 0;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Database error", e);
            return 0;
        }
    }

    /**
     * 关闭数据源
     */
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
