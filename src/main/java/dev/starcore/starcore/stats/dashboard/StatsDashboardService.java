package dev.starcore.starcore.stats.dashboard;

import dev.starcore.starcore.pvp.stats.PvPStats;
import dev.starcore.starcore.pvp.stats.PvPStatsService;
import dev.starcore.starcore.ranking.RankPeriod;
import dev.starcore.starcore.ranking.RankingService;
import dev.starcore.starcore.stats.visualization.VisualizationService;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 统计仪表盘服务
 *
 * 提供实时的玩家统计仪表盘界面和数据聚合
 * 支持GUI展示和命令行查看
 */
public class StatsDashboardService {

    private final RankingService rankingService;
    private final PvPStatsService pvpStatsService;
    private final VisualizationService visualizationService;

    // 缓存
    private final ConcurrentHashMap<UUID, DashboardCache> playerDashboards = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 30_000; // 30秒缓存

    // 全局仪表盘缓存
    private final AtomicReference<ServerDashboard> globalDashboard = new AtomicReference<>();
    private volatile long lastGlobalUpdate = 0;
    private static final long GLOBAL_UPDATE_INTERVAL_MS = 60_000; // 60秒

    public StatsDashboardService(RankingService rankingService, PvPStatsService pvpStatsService) {
        this.rankingService = rankingService;
        this.pvpStatsService = pvpStatsService;
        this.visualizationService = new VisualizationService();
    }

    /**
     * 获取玩家个人仪表盘
     */
    public CompletableFuture<PlayerDashboard> getPlayerDashboard(UUID playerId, String playerName) {
        return CompletableFuture.supplyAsync(() -> {
            // 检查缓存
            DashboardCache cached = playerDashboards.get(playerId);
            if (cached != null && !cached.isExpired()) {
                return cached.dashboard;
            }

            // 生成新的仪表盘
            PlayerDashboard dashboard = generatePlayerDashboard(playerId, playerName);

            // 更新缓存
            playerDashboards.put(playerId, new DashboardCache(dashboard));

            return dashboard;
        });
    }

    /**
     * 获取玩家仪表盘（同步版本，用于命令执行）
     */
    public PlayerDashboard getPlayerDashboardSync(UUID playerId, String playerName) {
        DashboardCache cached = playerDashboards.get(playerId);
        if (cached != null && !cached.isExpired()) {
            return cached.dashboard;
        }

        return generatePlayerDashboard(playerId, playerName);
    }

    /**
     * 获取服务器全局仪表盘
     */
    public ServerDashboard getGlobalDashboard() {
        long now = System.currentTimeMillis();
        ServerDashboard cached = globalDashboard.get();

        if (cached != null && (now - lastGlobalUpdate) < GLOBAL_UPDATE_INTERVAL_MS) {
            return cached;
        }

        return updateGlobalDashboard();
    }

    /**
     * 异步获取服务器全局仪表盘
     */
    public CompletableFuture<ServerDashboard> getGlobalDashboardAsync() {
        return CompletableFuture.supplyAsync(this::getGlobalDashboard);
    }

    /**
     * 更新全局仪表盘
     */
    public ServerDashboard updateGlobalDashboard() {
        ServerDashboard dashboard = generateServerDashboard();
        globalDashboard.set(dashboard);
        lastGlobalUpdate = System.currentTimeMillis();
        return dashboard;
    }

    /**
     * 刷新玩家仪表盘缓存
     */
    public void refreshPlayerDashboard(UUID playerId) {
        playerDashboards.remove(playerId);
    }

    /**
     * 清除所有缓存
     */
    public void clearCache() {
        playerDashboards.clear();
        globalDashboard.set(null);
        lastGlobalUpdate = 0;
    }

    /**
     * 获取格式化仪表盘文本
     */
    public String getFormattedDashboard(UUID playerId, String playerName) {
        PlayerDashboard dashboard = getPlayerDashboardSync(playerId, playerName);
        return formatPlayerDashboard(dashboard);
    }

    // ==================== 仪表盘生成 ====================

    private PlayerDashboard generatePlayerDashboard(UUID playerId, String playerName) {
        // 收集各周期数据
        Map<RankPeriod, PlayerPeriodStats> periodStats = new EnumMap<>(RankPeriod.class);

        for (RankPeriod period : RankPeriod.values()) {
            PlayerPeriodStats stats = new PlayerPeriodStats();
            stats.kills = rankingService.getKillCount(playerId, period).join();
            stats.deaths = rankingService.getDeathCount(playerId, period).join();
            stats.assists = rankingService.getAssistCount(playerId, period).join();
            stats.onlineTime = rankingService.getOnlineTime(playerId, period).join();
            stats.killRank = rankingService.getKillRank(playerId, period).join();
            stats.kdrRank = rankingService.getKDRatioRank(playerId, period).join();
            stats.onlineRank = rankingService.getOnlineTimeRank(playerId, period).join();
            periodStats.put(period, stats);
        }

        // 获取PvP详细统计
        PvPStats pvpStats = pvpStatsService != null ? pvpStatsService.getStats(playerId) : null;
        PvPDetailStats pvpDetail = new PvPDetailStats();
        if (pvpStats != null) {
            pvpDetail.currentKillStreak = pvpStats.getCurrentKillStreak();
            pvpDetail.bestKillStreak = pvpStats.getBestKillStreak();
            pvpDetail.duelWins = pvpStats.getDuelWins();
            pvpDetail.duelLosses = pvpStats.getDuelLosses();
            pvpDetail.totalDamage = pvpStats.getTotalDamage();
            pvpDetail.weeklyKills = Map.<Integer, Long>of(0, (long) pvpStats.getWeeklyKills());
            pvpDetail.weeklyDeaths = Map.<Integer, Long>of(0, (long) pvpStats.getWeeklyDeaths());
        }

        return new PlayerDashboard(
            playerId,
            playerName,
            LocalDateTime.now(),
            periodStats,
            pvpDetail,
            calculateKDA(periodStats.get(RankPeriod.ALLTIME)),
            calculateKD(periodStats.get(RankPeriod.ALLTIME))
        );
    }

    private ServerDashboard generateServerDashboard() {
        // 收集Top玩家
        Map<String, List<RankingService.TopPlayerData>> leaderboards = new HashMap<>();
        for (String statType : Arrays.asList("kills", "deaths", "kdratio", "playtime")) {
            leaderboards.put(statType, rankingService.getTopPlayers(statType, 10, RankPeriod.ALLTIME));
        }

        // 全局统计
        ServerStats globalStats = new ServerStats();
        globalStats.totalPlayers = rankingService.getLeaderboardSize("kills");
        globalStats.activePlayers = countActivePlayers();
        globalStats.totalKills = calculateTotalKills();
        globalStats.totalDeaths = calculateTotalDeaths();

        // 周期统计
        Map<RankPeriod, PeriodStats> periodStats = new EnumMap<>(RankPeriod.class);
        for (RankPeriod period : Arrays.asList(RankPeriod.DAILY, RankPeriod.WEEKLY, RankPeriod.MONTHLY)) {
            PeriodStats ps = new PeriodStats();
            ps.totalKills = leaderboards.get("kills").stream()
                .filter(d -> d.position() <= 100)
                .mapToLong(RankingService.TopPlayerData::value)
                .sum();
            ps.activePlayers = leaderboards.get("kills").size();
            periodStats.put(period, ps);
        }

        // 趋势数据（简化版）
        Map<String, List<Long>> trends = new HashMap<>();
        trends.put("kills", generateTrendData("kills", 7));
        trends.put("deaths", generateTrendData("deaths", 7));
        trends.put("players", generatePlayerTrendData(7));

        return new ServerDashboard(
            LocalDateTime.now(),
            globalStats,
            periodStats,
            leaderboards,
            trends
        );
    }

    // ==================== 格式化输出 ====================

    private String formatPlayerDashboard(PlayerDashboard dashboard) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append("=".repeat(50)).append("\n");
        sb.append("  玩家统计仪表盘: ").append(dashboard.playerName()).append("\n");
        sb.append("=".repeat(50)).append("\n\n");

        // 基础统计
        sb.append("【基础统计】\n");
        PlayerPeriodStats alltime = dashboard.periodStats().get(RankPeriod.ALLTIME);
        if (alltime != null) {
            sb.append(String.format("  击杀: %d  |  死亡: %d  |  助攻: %d\n",
                alltime.kills, alltime.deaths, alltime.assists));
            sb.append(String.format("  K/D: %.2f  |  KDA: %.2f\n",
                dashboard.kdRatio(), dashboard.kdaRatio()));
            sb.append(String.format("  在线时间: %s\n\n",
                formatDuration(alltime.onlineTime)));
        }

        // 排名
        sb.append("【排名】\n");
        if (alltime != null) {
            sb.append(String.format("  击杀榜: #%d / %d\n",
                alltime.killRank > 0 ? alltime.killRank : 0,
                dashboard.periodStats().get(RankPeriod.ALLTIME) != null ? rankingService.getLeaderboardSize("kills") : 0));
            sb.append(String.format("  K/D榜: #%d\n",
                alltime.kdrRank > 0 ? alltime.kdrRank : 0));
            sb.append(String.format("  在线时间榜: #%d\n\n",
                alltime.onlineRank > 0 ? alltime.onlineRank : 0));
        }

        // 周期对比
        sb.append("【周期对比】\n");
        for (RankPeriod period : Arrays.asList(RankPeriod.DAILY, RankPeriod.WEEKLY, RankPeriod.MONTHLY)) {
            PlayerPeriodStats stats = dashboard.periodStats().get(period);
            if (stats != null) {
                sb.append(String.format("  %s: %d 击杀, 排名 #%d\n",
                    period.getDisplayName(),
                    stats.kills,
                    stats.killRank > 0 ? stats.killRank : -1));
            }
        }
        sb.append("\n");

        // 详细统计
        if (dashboard.pvpStats() != null) {
            sb.append("【详细统计】\n");
            PvPDetailStats pvp = dashboard.pvpStats();
            sb.append(String.format("  当前连杀: %d  |  最高连杀: %d\n",
                pvp.currentKillStreak, pvp.bestKillStreak));
            sb.append(String.format("  决斗: %d 胜 / %d 负\n",
                pvp.duelWins, pvp.duelLosses));
            if (pvp.totalDamage > 0) {
                sb.append(String.format("  总伤害: %s\n", formatLargeNumber(pvp.totalDamage)));
            }
        }

        sb.append("\n").append("=".repeat(50));
        return sb.toString();
    }

    /**
     * 获取用于GUI显示的仪表盘数据
     */
    public Map<String, Object> getDashboardDataForGui(UUID playerId, String playerName) {
        PlayerDashboard dashboard = getPlayerDashboardSync(playerId, playerName);
        Map<String, Object> data = new LinkedHashMap<>();

        PlayerPeriodStats alltime = dashboard.periodStats().get(RankPeriod.ALLTIME);
        if (alltime != null) {
            data.put("kills", alltime.kills);
            data.put("deaths", alltime.deaths);
            data.put("assists", alltime.assists);
            data.put("killRank", alltime.killRank > 0 ? alltime.killRank : "-");
            data.put("kdrRank", alltime.kdrRank > 0 ? alltime.kdrRank : "-");
            data.put("onlineRank", alltime.onlineRank > 0 ? alltime.onlineRank : "-");
        }

        data.put("kdRatio", dashboard.kdRatio());
        data.put("kdaRatio", dashboard.kdaRatio());
        data.put("onlineTime", formatDuration(alltime != null ? alltime.onlineTime : 0));

        if (dashboard.pvpStats() != null) {
            PvPDetailStats pvp = dashboard.pvpStats();
            data.put("currentStreak", pvp.currentKillStreak);
            data.put("bestStreak", pvp.bestKillStreak);
            data.put("duelWins", pvp.duelWins);
            data.put("duelLosses", pvp.duelLosses);
            data.put("duelWinRate", pvp.duelLosses + pvp.duelWins > 0
                ? String.format("%.1f%%", (double) pvp.duelWins / (pvp.duelWins + pvp.duelLosses) * 100)
                : "N/A");
        }

        // 添加可视化数据
        data.put("killBar", visualizationService.generateHorizontalBar(
            alltime != null ? alltime.kills : 0,
            Math.max(1, rankingService.getTopPlayers("kills", 1, RankPeriod.ALLTIME)
                .stream().findFirst().map(RankingService.TopPlayerData::value).orElse(1L)),
            20
        ));

        return data;
    }

    // ==================== 辅助方法 ====================

    private double calculateKDA(PlayerPeriodStats stats) {
        if (stats == null || stats.deaths == 0) {
            return stats != null ? stats.kills + stats.assists : 0;
        }
        return (double) (stats.kills + stats.assists) / stats.deaths;
    }

    private double calculateKD(PlayerPeriodStats stats) {
        if (stats == null || stats.deaths == 0) {
            return stats != null ? stats.kills : 0;
        }
        return (double) stats.kills / stats.deaths;
    }

    private String formatDuration(long seconds) {
        if (seconds < 60) return seconds + "秒";
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        if (hours > 24) {
            long days = hours / 24;
            hours = hours % 24;
            return String.format("%d天 %d小时 %d分钟", days, hours, minutes);
        }
        if (hours > 0) {
            return String.format("%d小时 %d分钟", hours, minutes);
        }
        return minutes + "分钟";
    }

    private String formatLargeNumber(long number) {
        if (number >= 1_000_000) {
            return String.format("%.1fM", number / 1_000_000.0);
        } else if (number >= 1_000) {
            return String.format("%.1fK", number / 1_000.0);
        }
        return String.valueOf(number);
    }

    private int countActivePlayers() {
        return rankingService.getLeaderboardSize("kills");
    }

    private long calculateTotalKills() {
        return rankingService.getTopPlayers("kills", 1000, RankPeriod.ALLTIME)
            .stream()
            .mapToLong(RankingService.TopPlayerData::value)
            .sum();
    }

    private long calculateTotalDeaths() {
        return rankingService.getTopPlayers("deaths", 1000, RankPeriod.ALLTIME)
            .stream()
            .mapToLong(RankingService.TopPlayerData::value)
            .sum();
    }

    private List<Long> generateTrendData(String statType, int days) {
        // 简化实现：返回模拟数据
        List<Long> data = new ArrayList<>();
        Random random = new Random();
        for (int i = 0; i < days; i++) {
            data.add((long) (50 + random.nextInt(100)));
        }
        return data;
    }

    private List<Long> generatePlayerTrendData(int days) {
        List<Long> data = new ArrayList<>();
        Random random = new Random();
        for (int i = 0; i < days; i++) {
            data.add((long) (20 + random.nextInt(30)));
        }
        return data;
    }

    private static class DashboardCache {
        final PlayerDashboard dashboard;
        final long timestamp;

        DashboardCache(PlayerDashboard dashboard) {
            this.dashboard = dashboard;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_TTL_MS;
        }
    }

    // ==================== 数据模型 ====================

    /**
     * 玩家个人仪表盘
     */
    public record PlayerDashboard(
        UUID playerId,
        String playerName,
        LocalDateTime lastUpdate,
        Map<RankPeriod, PlayerPeriodStats> periodStats,
        PvPDetailStats pvpStats,
        double kdaRatio,
        double kdRatio
    ) {}

    /**
     * 玩家周期统计
     */
    public static class PlayerPeriodStats {
        public long kills;
        public long deaths;
        public long assists;
        public long onlineTime;
        public int killRank;
        public int kdrRank;
        public int onlineRank;
    }

    /**
     * PvP详细统计
     */
    public static class PvPDetailStats {
        public int currentKillStreak;
        public int bestKillStreak;
        public int duelWins;
        public int duelLosses;
        public long totalDamage;
        public Map<Integer, Long> weeklyKills = new HashMap<>();
        public Map<Integer, Long> weeklyDeaths = new HashMap<>();
    }

    /**
     * 服务器全局仪表盘
     */
    public record ServerDashboard(
        LocalDateTime lastUpdate,
        ServerStats globalStats,
        Map<RankPeriod, PeriodStats> periodStats,
        Map<String, List<RankingService.TopPlayerData>> leaderboards,
        Map<String, List<Long>> trends
    ) {}

    /**
     * 服务器统计
     */
    public static class ServerStats {
        public int totalPlayers;
        public int activePlayers;
        public long totalKills;
        public long totalDeaths;
    }

    /**
     * 周期统计
     */
    public static class PeriodStats {
        public long totalKills;
        public int activePlayers;
    }
}
