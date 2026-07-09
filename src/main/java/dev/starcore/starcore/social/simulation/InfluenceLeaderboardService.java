package dev.starcore.starcore.social.simulation;
import java.util.Optional;
import java.util.logging.Logger;

import dev.starcore.starcore.core.database.DatabaseService;
import dev.starcore.starcore.social.simulation.SocialInfluenceService.SocialStatus;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 影响力排行榜服务
 *
 * 提供完整的影响力排行榜功能:
 * - 多周期排行榜 (日/周/月/总)
 * - 排名变化追踪
 * - 排行榜缓存
 * - 影响力称号系统
 * - 排行榜GUI集成
 * - 排名变化通知
 */
public class InfluenceLeaderboardService {

    private final JavaPlugin plugin;
    private final DatabaseService databaseService;
    private final SocialInfluenceService influenceService;
    private final Logger logger;

    // 排行榜缓存: period -> (playerId -> rank)
    private final Map<LeaderboardPeriod, Map<UUID, Integer>> rankCache = new ConcurrentHashMap<>();
    // 排行榜缓存: period -> sorted list
    private final Map<LeaderboardPeriod, List<InfluenceLeaderboardEntry>> leaderboardCache = new ConcurrentHashMap<>();
    // 上一周期排名: period -> (playerId -> previousRank)
    private final Map<LeaderboardPeriod, Map<UUID, Integer>> previousRanks = new ConcurrentHashMap<>();
    // 玩家影响力快照: period -> (playerId -> score)
    private final Map<LeaderboardPeriod, Map<UUID, Integer>> influenceSnapshots = new ConcurrentHashMap<>();

    // 定时任务
    private BukkitTask cacheRefreshTask;
    private BukkitTask dailyResetTask;
    private BukkitTask notificationTask;

    // 排行榜大小
    private static final int LEADERBOARD_SIZE = 100;
    private static final int TOP_LIMIT = 10;

    // 缓存刷新间隔 (20 ticks = 1秒)
    private static final long CACHE_REFRESH_INTERVAL = 20 * 60; // 1分钟

    // 每日影响力之星称号配置
    private static final String DAILY_STAR_TITLE = "§6★ 每日影响力之星 §f";
    private static final String WEEKLY_STAR_TITLE = "§b◆ 每周影响力之星 §f";
    private static final String MONTHLY_STAR_TITLE = "§d✦ 每月影响力之星 §f";

    public InfluenceLeaderboardService(JavaPlugin plugin, DatabaseService databaseService,
                                       SocialInfluenceService influenceService) {
        this.plugin = plugin;
        this.databaseService = databaseService;
        this.influenceService = influenceService;
        this.logger = plugin.getLogger();

        if (databaseService != null) {
            initializeTables();
            try {
                loadPreviousRanks();
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load leaderboard ranks: " + e.getMessage());
            }
        }
        initializeSnapshots();
        startScheduledTasks();
    }

    // ==================== 初始化 ====================

    private void initializeTables() {
        if (databaseService == null) return;

        // 检测数据库类型
        boolean isSQLite = "SQLITE".equalsIgnoreCase(databaseService.settings().type().name());

        // 排行榜历史记录表
        String leaderboardHistorySql;
        if (isSQLite) {
            leaderboardHistorySql = """
                CREATE TABLE IF NOT EXISTS influence_leaderboard_history (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    player_id TEXT NOT NULL,
                    period TEXT NOT NULL,
                    score INTEGER NOT NULL,
                    rank_position INTEGER NOT NULL,
                    snapshot_date TEXT NOT NULL,
                    UNIQUE(player_id, period, snapshot_date)
                )
                """;
        } else {
            leaderboardHistorySql = """
                CREATE TABLE IF NOT EXISTS influence_leaderboard_history (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    player_id VARCHAR(36) NOT NULL,
                    period VARCHAR(20) NOT NULL,
                    score INT NOT NULL,
                    rank_position INT NOT NULL,
                    snapshot_date DATE NOT NULL,
                    UNIQUE KEY unique_record (player_id, period, snapshot_date),
                    INDEX idx_period (period),
                    INDEX idx_snapshot_date (snapshot_date)
                )
                """;
        }

        // 排行榜称号表
        String titlesSql;
        if (isSQLite) {
            titlesSql = """
                CREATE TABLE IF NOT EXISTS influence_leaderboard_titles (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    player_id TEXT NOT NULL UNIQUE,
                    daily_star TEXT,
                    daily_star_date TEXT,
                    weekly_star TEXT,
                    weekly_star_week INTEGER,
                    monthly_star TEXT,
                    monthly_star_month INTEGER,
                    total_top10_count INTEGER DEFAULT 0,
                    total_wins INTEGER DEFAULT 0
                )
                """;
        } else {
            titlesSql = """
                CREATE TABLE IF NOT EXISTS influence_leaderboard_titles (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    player_id VARCHAR(36) NOT NULL UNIQUE,
                    daily_star VARCHAR(255),
                    daily_star_date DATE,
                    weekly_star VARCHAR(255),
                    weekly_star_week INT,
                    monthly_star VARCHAR(255),
                    monthly_star_month INT,
                    total_top10_count INT DEFAULT 0,
                    total_wins INT DEFAULT 0
                )
                """;
        }

        // 排行榜变更通知表
        String notificationSql;
        if (isSQLite) {
            notificationSql = """
                CREATE TABLE IF NOT EXISTS leaderboard_notifications (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    player_id TEXT NOT NULL,
                    message TEXT NOT NULL,
                    is_read INTEGER DEFAULT 0,
                    created_at TEXT DEFAULT CURRENT_TIMESTAMP,
                    read_at TEXT
                )
                """;
        } else {
            notificationSql = """
                CREATE TABLE IF NOT EXISTS leaderboard_notifications (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    player_id VARCHAR(36) NOT NULL,
                    message TEXT NOT NULL,
                    is_read BOOLEAN DEFAULT FALSE,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    INDEX idx_player (player_id)
                )
                """;
        }

        databaseService.execute(leaderboardHistorySql);
        databaseService.execute(titlesSql);
        databaseService.execute(notificationSql);

        // SQLite 需要创建额外索引
        if (isSQLite) {
            databaseService.execute("CREATE INDEX IF NOT EXISTS idx_leaderboard_period ON influence_leaderboard_history(period)");
            databaseService.execute("CREATE INDEX IF NOT EXISTS idx_leaderboard_snapshot ON influence_leaderboard_history(snapshot_date)");
            databaseService.execute("CREATE INDEX IF NOT EXISTS idx_leaderboard_notif_player ON leaderboard_notifications(player_id)");
        }
    }

    private void loadPreviousRanks() {
        // 加载昨天的排行榜作为基准
        LocalDate yesterday = LocalDate.now().minusDays(1);
        String yesterdayStr = yesterday.toString(); // ISO格式: YYYY-MM-DD

        for (LeaderboardPeriod period : LeaderboardPeriod.values()) {
            Map<UUID, Integer> previous = new ConcurrentHashMap<>();
            String sql = """
                SELECT player_id, rank_position
                FROM influence_leaderboard_history
                WHERE period = ? AND snapshot_date = ?
                """;

            try {
                databaseService.query(sql, rs -> {
                    try {
                        while (rs.next()) {
                            UUID playerId = UUID.fromString(rs.getString("player_id"));
                            int rank = rs.getInt("rank_position");
                            previous.put(playerId, rank);
                        }
                    } catch (Exception e) {
                        logger.warning("Failed to parse leaderboard entry: " + e.getMessage());
                    }
                    return null;
                }, period.name(), yesterdayStr);
            } catch (Exception e) {
                logger.warning("Failed to load previous ranks for period " + period.name() + ": " + e.getMessage());
            }
                        // 静默跳过，保持数据兼容

            previousRanks.put(period, previous);
        }
    }

    private void initializeSnapshots() {
        for (LeaderboardPeriod period : LeaderboardPeriod.values()) {
            influenceSnapshots.put(period, new ConcurrentHashMap<>());
        }
        // 创建ALLTIME快照
        Map<UUID, Integer> alltimeSnapshot = new ConcurrentHashMap<>();
        Map<UUID, Integer> currentInfluence = getCurrentInfluenceMap();
        alltimeSnapshot.putAll(currentInfluence);
        influenceSnapshots.put(LeaderboardPeriod.ALLTIME, alltimeSnapshot);
    }

    private Map<UUID, Integer> getCurrentInfluenceMap() {
        Map<UUID, Integer> result = new HashMap<>();
        // 从数据库获取所有玩家影响力
        String sql = "SELECT player_id, influence FROM player_influence";
        databaseService.query(sql, rs -> {
            try {
                while (rs.next()) {
                    UUID playerId = UUID.fromString(rs.getString("player_id"));
                    int influence = rs.getInt("influence");
                    result.put(playerId, influence);
                }
            } catch (Exception e) {
                logger.warning("Failed to process ResultSet: " + e.getMessage());
            }
            return null;
        });
        return result;
    }

    // ==================== 排行榜计算 ====================

    /**
     * 获取当前影响力排行榜
     */
    public List<InfluenceLeaderboardEntry> getLeaderboard(LeaderboardPeriod period) {
        return leaderboardCache.computeIfAbsent(period, this::calculateLeaderboard);
    }

    /**
     * 计算排行榜
     */
    private List<InfluenceLeaderboardEntry> calculateLeaderboard(LeaderboardPeriod period) {
        Map<UUID, Integer> influenceData = getInfluenceForPeriod(period);
        Map<UUID, Integer> previousRankMap = previousRanks.getOrDefault(period, Collections.emptyMap());

        // 按影响力排序
        List<Map.Entry<UUID, Integer>> sorted = influenceData.entrySet().stream()
            .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
            .limit(LEADERBOARD_SIZE)
            .collect(Collectors.toList());

        List<InfluenceLeaderboardEntry> entries = new ArrayList<>();
        AtomicInteger rank = new AtomicInteger(1);

        for (Map.Entry<UUID, Integer> entry : sorted) {
            UUID playerId = entry.getKey();
            int influence = entry.getValue();
            int previousRank = previousRankMap.getOrDefault(playerId, -1);
            int change = calculateRankChange(rank.get(), previousRank);
            String playerName = getPlayerName(playerId);

            entries.add(InfluenceLeaderboardEntry.of(
                rank.get(),
                playerId,
                playerName,
                influence,
                change,
                period
            ));

            rank.incrementAndGet();
        }

        return entries;
    }

    /**
     * 根据周期获取影响力数据
     */
    private Map<UUID, Integer> getInfluenceForPeriod(LeaderboardPeriod period) {
        return switch (period) {
            case DAILY -> getDailyInfluence();
            case WEEKLY -> getWeeklyInfluence();
            case MONTHLY -> getMonthlyInfluence();
            case ALLTIME -> getAlltimeInfluence();
        };
    }

    private Map<UUID, Integer> getDailyInfluence() {
        // 日榜：从 influence_sources 获取今日的影响力变化
        return getPeriodicInfluence(LocalDate.now());
    }

    private Map<UUID, Integer> getWeeklyInfluence() {
        // 周榜：从周一开始累计
        return getPeriodicInfluence(getWeekStartDate());
    }

    private Map<UUID, Integer> getMonthlyInfluence() {
        // 月榜：从本月1号开始累计
        return getPeriodicInfluence(getMonthStartDate());
    }

    private Map<UUID, Integer> getAlltimeInfluence() {
        // 总榜：获取所有玩家的历史累计影响力
        // 从 player_influence 获取当前总影响力
        // 这是累积值，不需要再从 influence_sources 求和
        Map<UUID, Integer> result = new HashMap<>();
        String sql = "SELECT player_id, influence FROM player_influence";
        databaseService.query(sql, rs -> {
            try {
                while (rs.next()) {
                    UUID playerId = UUID.fromString(rs.getString("player_id"));
                    int influence = rs.getInt("influence");
                    result.put(playerId, influence);
                }
            } catch (Exception e) {
                logger.warning("Failed to process ResultSet: " + e.getMessage());
            }
            return null;
        });
        return result;
    }

    private Map<UUID, Integer> getPeriodicInfluence(LocalDate startDate) {
        Map<UUID, Integer> result = new HashMap<>();
        Timestamp startTimestamp = Timestamp.valueOf(startDate.atStartOfDay());

        String sql = """
            SELECT player_id, SUM(amount) as period_influence
            FROM influence_sources
            WHERE timestamp >= ?
            GROUP BY player_id
            """;

        databaseService.query(sql, rs -> {
            try {
                while (rs.next()) {
                    UUID playerId = UUID.fromString(rs.getString("player_id"));
                    int influence = rs.getInt("period_influence");
                    result.put(playerId, influence);
                }
            } catch (Exception e) {
                logger.warning("Failed to process ResultSet: " + e.getMessage());
            }
            return null;
        }, startTimestamp);

        return result;
    }

    private LocalDate getWeekStartDate() {
        LocalDate today = LocalDate.now();
        return today.minusDays(today.getDayOfWeek().getValue() - 1);
    }

    private LocalDate getMonthStartDate() {
        return LocalDate.now().withDayOfMonth(1);
    }

    private int calculateRankChange(int currentRank, int previousRank) {
        if (previousRank <= 0) return 0;
        return previousRank - currentRank; // 上升为正，下降为负
    }

    // ==================== 玩家排名 ====================

    /**
     * 获取玩家排名
     */
    public int getPlayerRank(UUID playerId, LeaderboardPeriod period) {
        List<InfluenceLeaderboardEntry> leaderboard = getLeaderboard(period);
        for (InfluenceLeaderboardEntry entry : leaderboard) {
            if (entry.playerId().equals(playerId)) {
                return entry.rank();
            }
        }
        return -1;
    }

    /**
     * 获取玩家的排行榜条目
     */
    public Optional<InfluenceLeaderboardEntry> getPlayerEntry(UUID playerId, LeaderboardPeriod period) {
        return getLeaderboard(period).stream()
            .filter(e -> e.playerId().equals(playerId))
            .findFirst();
    }

    /**
     * 获取玩家在所有周期的排名
     */
    public Map<LeaderboardPeriod, Integer> getAllRanks(UUID playerId) {
        Map<LeaderboardPeriod, Integer> ranks = new EnumMap<>(LeaderboardPeriod.class);
        for (LeaderboardPeriod period : LeaderboardPeriod.values()) {
            ranks.put(period, getPlayerRank(playerId, period));
        }
        return ranks;
    }

    /**
     * 获取Top N玩家
     */
    public List<InfluenceLeaderboardEntry> getTopPlayers(LeaderboardPeriod period, int limit) {
        List<InfluenceLeaderboardEntry> leaderboard = getLeaderboard(period);
        return leaderboard.stream()
            .limit(limit)
            .collect(Collectors.toList());
    }

    // ==================== 影响力称号 ====================

    /**
     * 授予每日影响力之星称号
     */
    public void grantDailyStarTitle() {
        List<InfluenceLeaderboardEntry> topPlayers = getTopPlayers(LeaderboardPeriod.DAILY, 3);
        if (topPlayers.isEmpty()) return;

        LocalDate today = LocalDate.now();

        for (int i = 0; i < topPlayers.size() && i < 3; i++) {
            UUID topPlayerId = topPlayers.get(i).playerId();
            String title = DAILY_STAR_TITLE + (i + 1);
            saveStarTitle(topPlayerId, "daily", title, today);
        }
    }

    /**
     * 授予每周影响力之星称号
     */
    public void grantWeeklyStarTitle() {
        List<InfluenceLeaderboardEntry> topPlayers = getTopPlayers(LeaderboardPeriod.WEEKLY, 3);
        if (topPlayers.isEmpty()) return;

        int currentWeek = LocalDate.now().getDayOfYear() / 7;

        for (int i = 0; i < topPlayers.size() && i < 3; i++) {
            UUID topPlayerId = topPlayers.get(i).playerId();
            String title = WEEKLY_STAR_TITLE + (i + 1);
            saveStarTitle(topPlayerId, "weekly", title, currentWeek);
        }
    }

    /**
     * 授予每月影响力之星称号
     */
    public void grantMonthlyStarTitle() {
        List<InfluenceLeaderboardEntry> topPlayers = getTopPlayers(LeaderboardPeriod.MONTHLY, 3);
        if (topPlayers.isEmpty()) return;

        int currentMonth = LocalDate.now().getMonthValue();

        for (int i = 0; i < topPlayers.size() && i < 3; i++) {
            UUID topPlayerId = topPlayers.get(i).playerId();
            String title = MONTHLY_STAR_TITLE + (i + 1);
            saveStarTitle(topPlayerId, "monthly", title, currentMonth);
        }
    }

    private void saveStarTitle(UUID playerId, String type, String title, Object period) {
        String sql;
        boolean isSqlite = databaseService != null && "SQLITE".equalsIgnoreCase(databaseService.settings().type().name());

        switch (type) {
            case "daily" -> {
                if (isSqlite) {
                    sql = "INSERT OR REPLACE INTO influence_leaderboard_titles (player_id, daily_star, daily_star_date) VALUES (?, ?, ?)";
                } else {
                    sql = """
                        INSERT INTO influence_leaderboard_titles (player_id, daily_star, daily_star_date)
                        VALUES (?, ?, ?)
                        ON DUPLICATE KEY UPDATE daily_star = VALUES(daily_star), daily_star_date = VALUES(daily_star_date)
                        """;
                }
            }
            case "weekly" -> {
                if (isSqlite) {
                    sql = "INSERT OR REPLACE INTO influence_leaderboard_titles (player_id, weekly_star, weekly_star_week) VALUES (?, ?, ?)";
                } else {
                    sql = """
                        INSERT INTO influence_leaderboard_titles (player_id, weekly_star, weekly_star_week)
                        VALUES (?, ?, ?)
                        ON DUPLICATE KEY UPDATE weekly_star = VALUES(weekly_star), weekly_star_week = VALUES(weekly_star_week)
                        """;
                }
            }
            case "monthly" -> {
                if (isSqlite) {
                    sql = "INSERT OR REPLACE INTO influence_leaderboard_titles (player_id, monthly_star, monthly_star_month) VALUES (?, ?, ?)";
                } else {
                    sql = """
                        INSERT INTO influence_leaderboard_titles (player_id, monthly_star, monthly_star_month)
                        VALUES (?, ?, ?)
                        ON DUPLICATE KEY UPDATE monthly_star = VALUES(monthly_star), monthly_star_month = VALUES(monthly_star_month)
                        """;
                }
            }
            default -> sql = "";
        }

        if (!sql.isEmpty()) {
            if (period instanceof LocalDate date) {
                databaseService.update(sql, playerId.toString(), title, Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant()));
            } else if (period instanceof Integer intVal) {
                databaseService.update(sql, playerId.toString(), title, intVal);
            }
        }
    }

    /**
     * 获取玩家的影响力称号
     */
    public List<String> getPlayerTitles(UUID playerId) {
        List<String> titles = new ArrayList<>();

        // 添加社会地位称号
        SocialStatus status = influenceService.getStatus(playerId);
        titles.add(status.getColor() + status.getName());

        // 从数据库获取排行榜称号
        String sql = "SELECT daily_star, weekly_star, monthly_star FROM influence_leaderboard_titles WHERE player_id = ?";
        databaseService.query(sql, rs -> {
            try {
                if (rs.next()) {
                    String dailyStar = rs.getString("daily_star");
                    String weeklyStar = rs.getString("weekly_star");
                    String monthlyStar = rs.getString("monthly_star");

                    if (dailyStar != null) titles.add(dailyStar);
                    if (weeklyStar != null) titles.add(weeklyStar);
                    if (monthlyStar != null) titles.add(monthlyStar);
                }
            } catch (Exception e) {
                logger.warning("Failed to process ResultSet: " + e.getMessage());
            }
            return null;
        }, playerId.toString());

        return titles;
    }

    // ==================== 排名变化通知 ====================

    /**
     * 通知玩家排名变化
     */
    public void notifyRankChange(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) return;

        Map<LeaderboardPeriod, Integer> ranks = getAllRanks(playerId);
        Map<LeaderboardPeriod, Integer> previous = getPreviousRanks(playerId);

        StringBuilder message = new StringBuilder();
        message.append("§6=== 影响力排行榜变化 ===\n");

        for (Map.Entry<LeaderboardPeriod, Integer> entry : ranks.entrySet()) {
            LeaderboardPeriod period = entry.getKey();
            int currentRank = entry.getValue();
            int prevRank = previous.getOrDefault(period, -1);

            if (currentRank > 0 && prevRank > 0 && currentRank != prevRank) {
                int change = prevRank - currentRank;
                String changeStr = change > 0 ? "§a▲ +" + change : "§c▼ " + change;
                message.append(String.format("§7%s: §f#%d %s\n", period.getDisplayName(), currentRank, changeStr));
            } else if (currentRank > 0 && prevRank <= 0) {
                message.append(String.format("§7%s: §a首次上榜! #%d\n", period.getDisplayName(), currentRank));
            }
        }

        player.sendMessage(message.toString());
    }

    /**
     * 保存排名变化到数据库
     */
    public void saveRankNotification(UUID playerId, String message) {
        String sql = "INSERT INTO leaderboard_notifications (player_id, message) VALUES (?, ?)";
        databaseService.update(sql, playerId.toString(), message);
    }

    /**
     * 获取玩家的未读通知
     */
    public List<String> getUnreadNotifications(UUID playerId) {
        List<String> notifications = new ArrayList<>();
        String sql = "SELECT message FROM leaderboard_notifications WHERE player_id = ? AND is_read = FALSE";
        databaseService.query(sql, rs -> {
            try {
                while (rs.next()) {
                    notifications.add(rs.getString("message"));
                }
            } catch (Exception e) {
                logger.warning("Failed to process ResultSet: " + e.getMessage());
            }
            return null;
        }, playerId.toString());
        return notifications;
    }

    /**
     * 标记通知为已读
     */
    public void markNotificationsAsRead(UUID playerId) {
        String sql = "UPDATE leaderboard_notifications SET is_read = TRUE WHERE player_id = ?";
        databaseService.update(sql, playerId.toString());
    }

    private Map<LeaderboardPeriod, Integer> getPreviousRanks(UUID playerId) {
        Map<LeaderboardPeriod, Integer> result = new EnumMap<>(LeaderboardPeriod.class);
        for (LeaderboardPeriod period : LeaderboardPeriod.values()) {
            Map<UUID, Integer> periodRanks = previousRanks.get(period);
            result.put(period, periodRanks != null ? periodRanks.getOrDefault(playerId, -1) : -1);
        }
        return result;
    }

    // ==================== 缓存管理 ====================

    /**
     * 刷新排行榜缓存
     */
    public void refreshCache() {
        for (LeaderboardPeriod period : LeaderboardPeriod.values()) {
            leaderboardCache.remove(period);
            getLeaderboard(period); // 重新计算
        }
    }

    /**
     * 清除所有缓存
     */
    public void clearCache() {
        rankCache.clear();
        leaderboardCache.clear();
    }

    /**
     * 保存当前快照用于明日比较
     */
    public void saveSnapshot() {
        LocalDate today = LocalDate.now();
        boolean isSqlite = databaseService != null && "SQLITE".equalsIgnoreCase(databaseService.settings().type().name());

        for (LeaderboardPeriod period : LeaderboardPeriod.values()) {
            List<InfluenceLeaderboardEntry> leaderboard = getLeaderboard(period);
            for (InfluenceLeaderboardEntry entry : leaderboard) {
                String sql;
                if (isSqlite) {
                    sql = "INSERT OR REPLACE INTO influence_leaderboard_history (player_id, period, score, rank_position, snapshot_date) VALUES (?, ?, ?, ?, ?)";
                } else {
                    sql = """
                        INSERT INTO influence_leaderboard_history
                        (player_id, period, score, rank_position, snapshot_date)
                        VALUES (?, ?, ?, ?, ?)
                        ON DUPLICATE KEY UPDATE score = VALUES(score), rank_position = VALUES(rank_position)
                        """;
                }
                databaseService.update(sql,
                    entry.playerId().toString(),
                    period.name(),
                    entry.influence(),
                    entry.rank(),
                    Date.from(today.atStartOfDay(ZoneId.systemDefault()).toInstant())
                );
            }
        }
    }

    // ==================== 定时任务 ====================

    private void startScheduledTasks() {
        // 缓存刷新任务
        cacheRefreshTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            refreshCache();
        }, CACHE_REFRESH_INTERVAL, CACHE_REFRESH_INTERVAL);

        // 每日重置任务 (在午夜执行)
        scheduleDailyReset();

        // 排名通知检查任务
        notificationTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            checkAndNotifyRankChanges();
        }, 20 * 60 * 5, 20 * 60 * 5); // 每5分钟检查一次
    }

    private void scheduleDailyReset() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextMidnight = now.plusDays(1).withHour(0).withMinute(0).withSecond(0);
        long delayTicks = java.time.Duration.between(now, nextMidnight).getSeconds() * 20;

        dailyResetTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            performDailyReset();
            // 然后每天重复执行
            Bukkit.getScheduler().runTaskTimer(plugin, (task) -> {
                performDailyReset();
            }, 20 * 60 * 60 * 24, 20 * 60 * 60 * 24); // 24小时
        }, delayTicks);
    }

    private void performDailyReset() {
        // 保存昨日快照
        saveSnapshot();

        // 授予昨日的每日之星称号
        grantDailyStarTitle();

        // 重置日榜缓存
        leaderboardCache.remove(LeaderboardPeriod.DAILY);

        // 更新前一日排名
        previousRanks.put(LeaderboardPeriod.DAILY, new ConcurrentHashMap<>(
            leaderboardCache.getOrDefault(LeaderboardPeriod.DAILY, Collections.emptyList())
                .stream()
                .collect(Collectors.toMap(InfluenceLeaderboardEntry::playerId, InfluenceLeaderboardEntry::rank))
        ));

        plugin.getLogger().info("影响力排行榜日榜已重置");
    }

    private void checkAndNotifyRankChanges() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            notifyRankChange(player.getUniqueId());
        }
    }

    // ==================== 辅助方法 ====================

    private String getPlayerName(UUID playerId) {
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerId);
        return offlinePlayer.getName() != null ? offlinePlayer.getName() : "Unknown";
    }

    /**
     * 获取排行榜统计数据
     */
    public LeaderboardStats getStats() {
        Map<UUID, Integer> alltimeData = getInfluenceForPeriod(LeaderboardPeriod.ALLTIME);
        return new LeaderboardStats(
            alltimeData.size(),  // 总玩家数
            leaderboardCache.getOrDefault(LeaderboardPeriod.ALLTIME, Collections.emptyList()).size(),
            getTopPlayers(LeaderboardPeriod.DAILY, 1).stream()
                .findFirst()
                .map(InfluenceLeaderboardEntry::influence)
                .orElse(0)
        );
    }

    /**
     * 排行榜统计数据
     */
    public record LeaderboardStats(int totalPlayers, int rankedPlayers, int topInfluence) {}

    // ==================== 生命周期管理 ====================

    /**
     * 获取影响力服务引用
     */
    public SocialInfluenceService getInfluenceService() {
        return influenceService;
    }

    /**
     * 获取排行榜服务引用
     */
    public InfluenceLeaderboardService getLeaderboardService() {
        return this;
    }

    /**
     * 关闭服务，保存数据并取消任务
     */
    public void shutdown() {
        if (cacheRefreshTask != null) {
            cacheRefreshTask.cancel();
        }
        if (dailyResetTask != null) {
            dailyResetTask.cancel();
        }
        if (notificationTask != null) {
            notificationTask.cancel();
        }

        // 保存最终快照
        saveSnapshot();

        plugin.getLogger().info("影响力排行榜服务已关闭");
    }
}
