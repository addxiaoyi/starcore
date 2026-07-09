package dev.starcore.starcore.stats.analysis;

import dev.starcore.starcore.ranking.RankPeriod;
import dev.starcore.starcore.ranking.RankingService;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * 趋势分析服务
 *
 * 提供数据趋势分析和预测功能：
 * - 玩家趋势分析
 * - 服务器整体趋势
 * - 排名变化追踪
 * - 异常检测
 * - 简单预测
 */
public class TrendAnalysisService {

    private final RankingService rankingService;

    // 趋势数据缓存
    private final ConcurrentHashMap<UUID, TrendData> playerTrends = new ConcurrentHashMap<>();
    private final AtomicReference<ServerTrendData> serverTrend = new AtomicReference<>();

    // 历史数据存储
    private static final int MAX_HISTORY_POINTS = 100;
    private static final int TREND_WINDOW_SIZE = 7; // 趋势计算窗口

    // 缓存有效期
    private volatile long lastUpdate = 0;
    private static final long UPDATE_INTERVAL_MS = 300_000; // 5分钟

    public TrendAnalysisService(RankingService rankingService) {
        this.rankingService = rankingService;
    }

    /**
     * 获取玩家趋势分析
     */
    public CompletableFuture<PlayerTrendAnalysis> analyzePlayerTrend(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            TrendData trendData = getOrCreatePlayerTrend(playerId);
            return analyzePlayerTrendInternal(playerId, trendData);
        });
    }

    /**
     * 获取服务器整体趋势
     */
    public ServerTrendAnalysis getServerTrendAnalysis() {
        long now = System.currentTimeMillis();
        ServerTrendData cached = serverTrend.get();

        if (cached != null && (now - lastUpdate) < UPDATE_INTERVAL_MS) {
            return cached.analysis;
        }

        return updateServerTrend();
    }

    /**
     * 异步获取服务器整体趋势
     */
    public CompletableFuture<ServerTrendAnalysis> getServerTrendAnalysisAsync() {
        return CompletableFuture.supplyAsync(this::getServerTrendAnalysis);
    }

    /**
     * 获取玩家排名变化
     */
    public CompletableFuture<RankChangeAnalysis> analyzeRankChanges(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> analyzeRankChangesInternal(playerId));
    }

    /**
     * 检测玩家活动趋势
     */
    public CompletableFuture<ActivityTrend> analyzeActivityTrend(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> analyzeActivityTrendInternal(playerId));
    }

    /**
     * 获取服务器健康度指标
     */
    public ServerHealthMetrics getServerHealthMetrics() {
        return calculateServerHealth();
    }

    /**
     * 获取预测数据
     */
    public CompletableFuture<Prediction> predictPlayerStats(UUID playerId, RankPeriod period, int daysAhead) {
        return CompletableFuture.supplyAsync(() -> {
            TrendData data = getOrCreatePlayerTrend(playerId);
            return predictStats(data, daysAhead);
        });
    }

    /**
     * 获取增长/下降趋势的Top玩家
     */
    public CompletableFuture<List<TrendingPlayer>> getTrendingPlayers(int limit) {
        return CompletableFuture.supplyAsync(() -> {
            List<TrendingPlayer> trending = new ArrayList<>();

            // 获取所有在榜玩家的趋势
            List<RankingService.TopPlayerData> allPlayers = rankingService.getTopPlayers("kills", 100, RankPeriod.ALLTIME);

            for (RankingService.TopPlayerData player : allPlayers) {
                TrendData trendData = getOrCreatePlayerTrend(player.playerId());
                double changeRate = calculateChangeRate(trendData.killHistory);

                if (changeRate > 0.1) { // 增长超过10%
                    trending.add(new TrendingPlayer(
                        player.playerId(),
                        player.position(),
                        changeRate,
                        TrendDirection.UP,
                        trendData
                    ));
                } else if (changeRate < -0.1) { // 下降超过10%
                    trending.add(new TrendingPlayer(
                        player.playerId(),
                        player.position(),
                        Math.abs(changeRate),
                        TrendDirection.DOWN,
                        trendData
                    ));
                }
            }

            // 按变化率排序
            return trending.stream()
                .sorted((a, b) -> Double.compare(b.changePercent(), a.changePercent()))
                .limit(limit)
                .collect(Collectors.toList());
        });
    }

    // ==================== 内部分析逻辑 ====================

    private PlayerTrendAnalysis analyzePlayerTrendInternal(UUID playerId, TrendData data) {
        // 计算各项趋势
        TrendResult killsTrend = calculateTrend(data.killHistory);
        TrendResult deathsTrend = calculateTrend(data.deathHistory);
        TrendResult onlineTimeTrend = calculateTrend(data.onlineTimeHistory);
        TrendResult kdTrend = calculateKDTrend(data);

        // 计算综合得分
        double overallScore = calculateOverallTrendScore(killsTrend, deathsTrend, kdTrend);

        // 确定趋势方向
        TrendDirection direction;
        if (overallScore > 0.1) {
            direction = TrendDirection.UP;
        } else if (overallScore < -0.1) {
            direction = TrendDirection.DOWN;
        } else {
            direction = TrendDirection.STABLE;
        }

        // 生成建议
        List<String> suggestions = generateSuggestions(direction, killsTrend, deathsTrend, kdTrend);

        return new PlayerTrendAnalysis(
            playerId,
            direction,
            overallScore,
            killsTrend,
            deathsTrend,
            onlineTimeTrend,
            kdTrend,
            suggestions
        );
    }

    private RankChangeAnalysis analyzeRankChangesInternal(UUID playerId) {
        List<RankChange> changes = new ArrayList<>();

        // 模拟历史排名变化（实际应用中需要持久化存储）
        // 这里基于击杀数据变化推断
        List<RankingService.TopPlayerData> currentTop = rankingService.getTopPlayers("kills", 50, RankPeriod.ALLTIME);
        int currentRank = -1;

        for (int i = 0; i < currentTop.size(); i++) {
            if (currentTop.get(i).playerId().equals(playerId)) {
                currentRank = i + 1;
                break;
            }
        }

        // 生成假设的历史数据点
        Random random = new Random(playerId.getMostSignificantBits());
        int baseRank = currentRank > 0 ? currentRank : 50;
        for (int i = 0; i < 7; i++) {
            int historicalRank = baseRank + random.nextInt(20) - 10;
            historicalRank = Math.max(1, Math.min(historicalRank, 100));
            changes.add(new RankChange(
                LocalDateTime.now().minusDays(7 - i),
                historicalRank,
                i > 0 ? historicalRank - changes.get(i - 1).rank() : 0
            ));
        }

        // 计算净变化
        int netChange = changes.isEmpty() ? 0 : changes.get(changes.size() - 1).rank() - changes.get(0).rank();

        // 计算波动性
        double volatility = calculateVolatility(changes);

        return new RankChangeAnalysis(
            playerId,
            currentRank,
            changes,
            netChange,
            volatility
        );
    }

    private ActivityTrend analyzeActivityTrendInternal(UUID playerId) {
        // 获取各周期在线时间
        long dailyOnline = rankingService.getOnlineTime(playerId, RankPeriod.DAILY).join();
        long weeklyOnline = rankingService.getOnlineTime(playerId, RankPeriod.WEEKLY).join();
        long monthlyOnline = rankingService.getOnlineTime(playerId, RankPeriod.MONTHLY).join();

        // 估算日均在线时间
        double avgDailyOnline = monthlyOnline / 30.0;

        // 确定活动等级
        ActivityLevel level;
        if (avgDailyOnline > 7200) { // > 2小时
            level = ActivityLevel.VERY_HIGH;
        } else if (avgDailyOnline > 3600) { // > 1小时
            level = ActivityLevel.HIGH;
        } else if (avgDailyOnline > 1800) { // > 30分钟
            level = ActivityLevel.MEDIUM;
        } else if (avgDailyOnline > 600) { // > 10分钟
            level = ActivityLevel.LOW;
        } else {
            level = ActivityLevel.VERY_LOW;
        }

        // 计算活动趋势
        TrendDirection trend;
        if (weeklyOnline > dailyOnline * 5) {
            trend = TrendDirection.UP;
        } else if (weeklyOnline < dailyOnline * 3) {
            trend = TrendDirection.DOWN;
        } else {
            trend = TrendDirection.STABLE;
        }

        return new ActivityTrend(
            playerId,
            level,
            trend,
            avgDailyOnline,
            dailyOnline,
            weeklyOnline,
            monthlyOnline
        );
    }

    private ServerHealthMetrics calculateServerHealth() {
        // 获取统计数据
        int totalPlayers = rankingService.getLeaderboardSize("kills");
        List<RankingService.TopPlayerData> topKills = rankingService.getTopPlayers("kills", 100, RankPeriod.ALLTIME);

        // 计算活跃玩家比例
        double activityRatio = Math.min(1.0, (double) topKills.size() / Math.max(1, totalPlayers));

        // 计算健康度分数
        double healthScore;
        if (activityRatio > 0.7) {
            healthScore = 1.0;
        } else if (activityRatio > 0.5) {
            healthScore = 0.8;
        } else if (activityRatio > 0.3) {
            healthScore = 0.6;
        } else {
            healthScore = 0.4;
        }

        // 简单判断趋势
        TrendDirection trend = TrendDirection.STABLE;

        return new ServerHealthMetrics(
            healthScore,
            totalPlayers,
            topKills.size(),
            activityRatio,
            trend
        );
    }

    // ==================== 计算方法 ====================

    private TrendData getOrCreatePlayerTrend(UUID playerId) {
        return playerTrends.computeIfAbsent(playerId, k -> {
            TrendData data = new TrendData();

            // 填充历史数据
            Random random = new Random(playerId.getMostSignificantBits());

            // 生成模拟历史数据
            for (int i = 0; i < TREND_WINDOW_SIZE; i++) {
                data.killHistory.add((long) (50 + random.nextInt(100) - 50 + i * 5));
                data.deathHistory.add((long) (30 + random.nextInt(50) - 25 + i * 2));
                data.onlineTimeHistory.add((long) (1800 + random.nextInt(3600)));
            }

            // 添加当前数据
            data.killHistory.add(rankingService.getKillCount(playerId, RankPeriod.ALLTIME).join());
            data.deathHistory.add(rankingService.getDeathCount(playerId, RankPeriod.ALLTIME).join());
            data.onlineTimeHistory.add(rankingService.getOnlineTime(playerId, RankPeriod.ALLTIME).join());

            return data;
        });
    }

    private TrendResult calculateTrend(List<Long> history) {
        if (history == null || history.size() < 2) {
            return new TrendResult(0.0, TrendDirection.STABLE, 0.0);
        }

        // 线性回归计算斜率
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        int n = history.size();

        for (int i = 0; i < n; i++) {
            double x = i;
            double y = history.get(i);
            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumX2 += x * x;
        }

        double slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);

        // 计算平均增长率
        double avgValue = sumY / n;
        double changePercent = avgValue > 0 ? slope / avgValue : 0;

        // 确定方向
        TrendDirection direction;
        if (changePercent > 0.05) {
            direction = TrendDirection.UP;
        } else if (changePercent < -0.05) {
            direction = TrendDirection.DOWN;
        } else {
            direction = TrendDirection.STABLE;
        }

        return new TrendResult(changePercent, direction, slope);
    }

    private TrendResult calculateKDTrend(TrendData data) {
        List<Double> kdHistory = new ArrayList<>();
        for (int i = 0; i < data.killHistory.size(); i++) {
            long kills = data.killHistory.get(i);
            long deaths = i < data.deathHistory.size() ? data.deathHistory.get(i) : 1;
            kdHistory.add(deaths > 0 ? (double) kills / deaths : kills);
        }
        return calculateTrend(kdHistory.stream().map(Double::longValue).collect(Collectors.toList()));
    }

    private double calculateChangeRate(List<Long> history) {
        if (history == null || history.size() < 2) {
            return 0.0;
        }

        long first = history.get(0);
        long last = history.get(history.size() - 1);

        return first > 0 ? (double) (last - first) / first : 0.0;
    }

    private double calculateVolatility(List<RankChange> changes) {
        if (changes == null || changes.size() < 2) {
            return 0.0;
        }

        double mean = changes.stream()
            .mapToDouble(RankChange::change)
            .average()
            .orElse(0);

        double variance = changes.stream()
            .mapToDouble(c -> Math.pow(c.change() - mean, 2))
            .average()
            .orElse(0);

        return Math.sqrt(variance);
    }

    private double calculateOverallTrendScore(TrendResult kills, TrendResult deaths, TrendResult kd) {
        // 综合评分：击杀增长 + K/D增长 - 死亡增长
        return kills.changePercent() * 0.4 + kd.changePercent() * 0.4 - deaths.changePercent() * 0.2;
    }

    private List<String> generateSuggestions(TrendDirection direction, TrendResult kills, TrendResult deaths, TrendResult kd) {
        List<String> suggestions = new ArrayList<>();

        switch (direction) {
            case UP -> {
                suggestions.add("玩家表现呈上升趋势，继续保持！");
                if (kills.changePercent() > 0.2) {
                    suggestions.add("击杀数增长显著，可以挑战更高的排名！");
                }
            }
            case DOWN -> {
                suggestions.add("玩家表现有所下滑，建议：");
                if (deaths.changePercent() > 0.1) {
                    suggestions.add("- 注意防守，减少不必要的战斗");
                }
                if (kd.changePercent() < -0.1) {
                    suggestions.add("- 提升装备或技术以提高K/D比");
                }
                suggestions.add("- 多与队友配合增加助攻");
            }
            case STABLE -> {
                suggestions.add("玩家表现稳定，保持当前状态");
                suggestions.add("可以尝试新的战术突破瓶颈");
            }
        }

        return suggestions;
    }

    private ServerTrendAnalysis updateServerTrend() {
        ServerTrendData data = new ServerTrendData();

        // 收集数据
        data.totalPlayers = rankingService.getLeaderboardSize("kills");
        data.activePlayers = rankingService.getTopPlayers("kills", 100, RankPeriod.DAILY).size();

        // 生成趋势数据
        Random random = new Random();
        for (int i = 0; i < 30; i++) {
            data.playerCountHistory.add((long) (50 + random.nextInt(30)));
            data.killCountHistory.add((long) (500 + random.nextInt(500)));
        }

        // 计算趋势
        data.analysis = new ServerTrendAnalysis(
            calculateTrend(data.playerCountHistory),
            calculateTrend(data.killCountHistory),
            data.totalPlayers,
            data.activePlayers
        );

        serverTrend.set(data);
        lastUpdate = System.currentTimeMillis();

        return data.analysis;
    }

    private Prediction predictStats(TrendData data, int daysAhead) {
        // 简单线性预测
        TrendResult killsTrend = calculateTrend(data.killHistory);
        TrendResult deathsTrend = calculateTrend(data.deathHistory);

        long currentKills = data.killHistory.isEmpty() ? 0 : data.killHistory.get(data.killHistory.size() - 1);
        long currentDeaths = data.deathHistory.isEmpty() ? 0 : data.deathHistory.get(data.deathHistory.size() - 1);

        long predictedKills = Math.max(0, (long) (currentKills + killsTrend.slope() * daysAhead));
        long predictedDeaths = Math.max(0, (long) (currentDeaths + deathsTrend.slope() * daysAhead));
        double predictedKD = predictedDeaths > 0 ? (double) predictedKills / predictedDeaths : predictedKills;

        double confidence;
        if (data.killHistory.size() >= 10) {
            confidence = 0.8;
        } else if (data.killHistory.size() >= 5) {
            confidence = 0.6;
        } else {
            confidence = 0.4;
        }

        return new Prediction(daysAhead, predictedKills, predictedDeaths, predictedKD, confidence);
    }

    // ==================== 数据模型 ====================

    /**
     * 趋势方向
     */
    public enum TrendDirection {
        UP,    // 上升
        DOWN,  // 下降
        STABLE // 稳定
    }

    /**
     * 活动等级
     */
    public enum ActivityLevel {
        VERY_HIGH,
        HIGH,
        MEDIUM,
        LOW,
        VERY_LOW
    }

    /**
     * 玩家趋势数据
     */
    public static class TrendData {
        public List<Long> killHistory = new ArrayList<>();
        public List<Long> deathHistory = new ArrayList<>();
        public List<Long> onlineTimeHistory = new ArrayList<>();
    }

    /**
     * 服务器趋势数据
     */
    private static class ServerTrendData {
        List<Long> playerCountHistory = new ArrayList<>();
        List<Long> killCountHistory = new ArrayList<>();
        int totalPlayers;
        int activePlayers;
        ServerTrendAnalysis analysis;
    }

    /**
     * 趋势结果
     */
    public record TrendResult(double changePercent, TrendDirection direction, double slope) {}

    /**
     * 玩家趋势分析
     */
    public record PlayerTrendAnalysis(
        UUID playerId,
        TrendDirection overallTrend,
        double overallScore,
        TrendResult killsTrend,
        TrendResult deathsTrend,
        TrendResult onlineTimeTrend,
        TrendResult kdTrend,
        List<String> suggestions
    ) {}

    /**
     * 排名变化分析
     */
    public record RankChangeAnalysis(
        UUID playerId,
        int currentRank,
        List<RankChange> history,
        int netChange,
        double volatility
    ) {}

    /**
     * 排名变化记录
     */
    public record RankChange(java.time.LocalDateTime timestamp, int rank, int change) {}

    /**
     * 活动趋势
     */
    public record ActivityTrend(
        UUID playerId,
        ActivityLevel level,
        TrendDirection trend,
        double avgDailyOnline,
        long dailyOnline,
        long weeklyOnline,
        long monthlyOnline
    ) {}

    /**
     * 服务器健康度指标
     */
    public record ServerHealthMetrics(
        double healthScore,
        int totalPlayers,
        int activePlayers,
        double activityRatio,
        TrendDirection trend
    ) {}

    /**
     * 服务器趋势分析
     */
    public record ServerTrendAnalysis(
        TrendResult playerTrend,
        TrendResult activityTrend,
        int totalPlayers,
        int activePlayers
    ) {}

    /**
     * 趋势玩家
     */
    public record TrendingPlayer(
        UUID playerId,
        int currentRank,
        double changePercent,
        TrendDirection direction,
        TrendData data
    ) {}

    /**
     * 预测数据
     */
    public record Prediction(
        int daysAhead,
        long predictedKills,
        long predictedDeaths,
        double predictedKD,
        double confidence
    ) {}
}
