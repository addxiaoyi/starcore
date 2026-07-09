package dev.starcore.starcore.stats.report;

import dev.starcore.starcore.ranking.RankPeriod;
import dev.starcore.starcore.ranking.RankingService;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 周期性报告服务
 *
 * 自动生成并发送服务器统计报告
 * 支持多种报告周期：每小时、每天、每周、每月
 */
public class PeriodicReportService {

    private final RankingService rankingService;
    private final ScheduledExecutorService scheduler;
    private final Map<ReportType, ScheduledFuture<?>> scheduledTasks;
    private final Map<ReportType, ReportCallback> callbacks;
    private final AtomicBoolean running = new AtomicBoolean(false);

    // 报告历史记录
    private final LinkedList<ReportRecord> reportHistory = new LinkedList<>();
    private static final int MAX_HISTORY_SIZE = 100;

    public PeriodicReportService(RankingService rankingService) {
        this.rankingService = rankingService;
        this.scheduler = Executors.newScheduledThreadPool(4);
        this.scheduledTasks = new ConcurrentHashMap<>();
        this.callbacks = new ConcurrentHashMap<>();
    }

    /**
     * 启动所有周期性报告任务
     */
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }

        // 每小时报告
        scheduleReport(ReportType.HOURLY, Duration.ofHours(1), () -> generateHourlyReport());

        // 每天报告 (每24小时)
        scheduleReport(ReportType.DAILY, Duration.ofHours(24), () -> generateDailyReport());

        // 每周报告 (每7天)
        scheduleReport(ReportType.WEEKLY, Duration.ofDays(7), () -> generateWeeklyReport());

        // 每月报告 (每30天)
        scheduleReport(ReportType.MONTHLY, Duration.ofDays(30), () -> generateMonthlyReport());
    }

    /**
     * 停止所有周期性报告任务
     */
    public void stop() {
        running.set(false);
        scheduledTasks.values().forEach(future -> future.cancel(false));
        scheduledTasks.clear();
        scheduler.shutdown();
    }

    /**
     * 注册报告回调
     */
    public void registerCallback(ReportType type, ReportCallback callback) {
        callbacks.put(type, callback);
    }

    /**
     * 生成报告
     */
    public CompletableFuture<Report> generateReport(ReportType type) {
        return CompletableFuture.supplyAsync(() -> createReport(type));
    }

    /**
     * 手动触发指定类型的报告
     */
    public CompletableFuture<Report> triggerReport(ReportType type) {
        return CompletableFuture.supplyAsync(() -> {
            Report report = createReport(type);
            ReportCallback callback = callbacks.get(type);
            if (callback != null) {
                callback.onReportGenerated(report);
            }
            return report;
        });
    }

    /**
     * 获取报告历史
     */
    public List<ReportRecord> getReportHistory(ReportType type) {
        // 与 addToHistory 一致加锁：异步线程改动链表结构时，主线程读方需同步
        synchronized (reportHistory) {
            return reportHistory.stream()
                .filter(r -> r.type() == type)
                .toList();
        }
    }

    /**
     * 获取最近的报告历史
     */
    public List<ReportRecord> getRecentHistory(int limit) {
        synchronized (reportHistory) {
            return reportHistory.stream()
                .limit(limit)
                .toList();
        }
    }

    // ==================== 报告生成 ====================

    private Report createReport(ReportType type) {
        LocalDateTime now = LocalDateTime.now();
        String period = getPeriodDescription(type, now);

        return switch (type) {
            case HOURLY -> generateHourlyReportInternal(period, now);
            case DAILY -> generateDailyReportInternal(period, now);
            case WEEKLY -> generateWeeklyReportInternal(period, now);
            case MONTHLY -> generateMonthlyReportInternal(period, now);
        };
    }

    private Report generateHourlyReport() {
        LocalDateTime now = LocalDateTime.now();
        String period = getPeriodDescription(ReportType.HOURLY, now);
        return generateHourlyReportInternal(period, now);
    }

    private Report generateHourlyReportInternal(String period, LocalDateTime timestamp) {
        Map<String, Object> metrics = new HashMap<>();

        // 获取小时榜数据
        List<RankingService.TopPlayerData> topKills = rankingService.getTopPlayers("kills", 10, RankPeriod.HOURLY);
        metrics.put("topKills", topKills);
        metrics.put("totalPlayers", rankingService.getLeaderboardSize("kills"));

        // 获取在线时间数据
        List<RankingService.TopPlayerData> topPlaytime = rankingService.getTopPlayers("playtime", 5, RankPeriod.HOURLY);
        metrics.put("topPlaytime", topPlaytime);

        String content = buildHourlyReportContent(period, metrics);
        Report report = new Report(ReportType.HOURLY, period, timestamp, content, metrics);
        addToHistory(report);
        return report;
    }

    private Report generateDailyReport() {
        LocalDateTime now = LocalDateTime.now();
        String period = getPeriodDescription(ReportType.DAILY, now);
        return generateDailyReportInternal(period, now);
    }

    private Report generateDailyReportInternal(String period, LocalDateTime timestamp) {
        Map<String, Object> metrics = new HashMap<>();

        // 日榜数据
        List<RankingService.TopPlayerData> topKills = rankingService.getTopPlayers("kills", 10, RankPeriod.DAILY);
        List<RankingService.TopPlayerData> topKDR = rankingService.getTopPlayers("kdratio", 5, RankPeriod.DAILY);
        List<RankingService.TopPlayerData> topPlaytime = rankingService.getTopPlayers("playtime", 5, RankPeriod.DAILY);

        metrics.put("topKills", topKills);
        metrics.put("topKDR", topKDR);
        metrics.put("topPlaytime", topPlaytime);
        metrics.put("totalPlayers", rankingService.getLeaderboardSize("kills"));

        String content = buildDailyReportContent(period, metrics);
        Report report = new Report(ReportType.DAILY, period, timestamp, content, metrics);
        addToHistory(report);
        return report;
    }

    private Report generateWeeklyReport() {
        LocalDateTime now = LocalDateTime.now();
        String period = getPeriodDescription(ReportType.WEEKLY, now);
        return generateWeeklyReportInternal(period, now);
    }

    private Report generateWeeklyReportInternal(String period, LocalDateTime timestamp) {
        Map<String, Object> metrics = new HashMap<>();

        // 周榜数据
        List<RankingService.TopPlayerData> topKills = rankingService.getTopPlayers("kills", 15, RankPeriod.WEEKLY);
        List<RankingService.TopPlayerData> topKDR = rankingService.getTopPlayers("kdratio", 10, RankPeriod.WEEKLY);
        List<RankingService.TopPlayerData> topPlaytime = rankingService.getTopPlayers("playtime", 10, RankPeriod.WEEKLY);

        metrics.put("topKills", topKills);
        metrics.put("topKDR", topKDR);
        metrics.put("topPlaytime", topPlaytime);
        metrics.put("totalPlayers", rankingService.getLeaderboardSize("kills"));

        String content = buildWeeklyReportContent(period, metrics);
        Report report = new Report(ReportType.WEEKLY, period, timestamp, content, metrics);
        addToHistory(report);
        return report;
    }

    private Report generateMonthlyReport() {
        LocalDateTime now = LocalDateTime.now();
        String period = getPeriodDescription(ReportType.MONTHLY, now);
        return generateMonthlyReportInternal(period, now);
    }

    private Report generateMonthlyReportInternal(String period, LocalDateTime timestamp) {
        Map<String, Object> metrics = new HashMap<>();

        // 月榜数据
        List<RankingService.TopPlayerData> topKills = rankingService.getTopPlayers("kills", 20, RankPeriod.MONTHLY);
        List<RankingService.TopPlayerData> topKDR = rankingService.getTopPlayers("kdratio", 15, RankPeriod.MONTHLY);
        List<RankingService.TopPlayerData> topPlaytime = rankingService.getTopPlayers("playtime", 15, RankPeriod.MONTHLY);

        metrics.put("topKills", topKills);
        metrics.put("topKDR", topKDR);
        metrics.put("topPlaytime", topPlaytime);
        metrics.put("totalPlayers", rankingService.getLeaderboardSize("kills"));

        String content = buildMonthlyReportContent(period, metrics);
        Report report = new Report(ReportType.MONTHLY, period, timestamp, content, metrics);
        addToHistory(report);
        return report;
    }

    // ==================== 报告内容构建 ====================

    private String buildHourlyReportContent(String period, Map<String, Object> metrics) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== ").append(period).append(" 统计报告 ===\n\n");

        // 击杀榜
        sb.append("【小时击杀榜 Top5】\n");
        List<?> topKills = (List<?>) metrics.get("topKills");
        buildTopList(sb, topKills, 5);

        // 在线时间
        sb.append("\n【在线时间 Top3】\n");
        List<?> topPlaytime = (List<?>) metrics.get("topPlaytime");
        buildTopList(sb, topPlaytime, 3);

        return sb.toString();
    }

    private String buildDailyReportContent(String period, Map<String, Object> metrics) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== ").append(period).append(" 统计报告 ===\n\n");

        // 击杀榜
        sb.append("【日击杀榜 Top10】\n");
        List<?> topKills = (List<?>) metrics.get("topKills");
        buildTopList(sb, topKills, 10);

        // K/D榜
        sb.append("\n【日K/D榜 Top5】\n");
        List<?> topKDR = (List<?>) metrics.get("topKDR");
        buildTopList(sb, topKDR, 5);

        // 在线时间
        sb.append("\n【在线时间 Top5】\n");
        List<?> topPlaytime = (List<?>) metrics.get("topPlaytime");
        buildTopList(sb, topPlaytime, 5);

        return sb.toString();
    }

    private String buildWeeklyReportContent(String period, Map<String, Object> metrics) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== ").append(period).append(" 统计报告 ===\n\n");

        // 击杀榜
        sb.append("【周击杀榜 Top15】\n");
        List<?> topKills = (List<?>) metrics.get("topKills");
        buildTopList(sb, topKills, 15);

        // K/D榜
        sb.append("\n【周K/D榜 Top10】\n");
        List<?> topKDR = (List<?>) metrics.get("topKDR");
        buildTopList(sb, topKDR, 10);

        // 在线时间
        sb.append("\n【在线时间 Top10】\n");
        List<?> topPlaytime = (List<?>) metrics.get("topPlaytime");
        buildTopList(sb, topPlaytime, 10);

        return sb.toString();
    }

    private String buildMonthlyReportContent(String period, Map<String, Object> metrics) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== ").append(period).append(" 统计报告 ===\n\n");

        // 击杀榜
        sb.append("【月击杀榜 Top20】\n");
        List<?> topKills = (List<?>) metrics.get("topKills");
        buildTopList(sb, topKills, 20);

        // K/D榜
        sb.append("\n【月K/D榜 Top15】\n");
        List<?> topKDR = (List<?>) metrics.get("topKDR");
        buildTopList(sb, topKDR, 15);

        // 在线时间
        sb.append("\n【在线时间 Top15】\n");
        List<?> topPlaytime = (List<?>) metrics.get("topPlaytime");
        buildTopList(sb, topPlaytime, 15);

        return sb.toString();
    }

    private void buildTopList(StringBuilder sb, List<?> list, int limit) {
        if (list == null || list.isEmpty()) {
            sb.append("暂无数据\n");
            return;
        }

        int count = 0;
        for (Object item : list) {
            if (count >= limit) break;
            if (item instanceof RankingService.TopPlayerData data) {
                sb.append(String.format("  %d. %s - %d\n",
                    data.position(),
                    data.playerId().toString().substring(0, 8),
                    data.value()));
            }
            count++;
        }
    }

    // ==================== 辅助方法 ====================

    private void scheduleReport(ReportType type, Duration interval, Runnable task) {
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(() -> {
            if (running.get()) {
                try {
                    Report report = createReport(type);
                    ReportCallback callback = callbacks.get(type);
                    if (callback != null) {
                        callback.onReportGenerated(report);
                    }
                } catch (Exception e) {
                    // 记录错误但不中断其他任务
                }
            }
        }, interval.toMillis(), interval.toMillis(), TimeUnit.MILLISECONDS);

        scheduledTasks.put(type, future);
    }

    private void addToHistory(Report report) {
        synchronized (reportHistory) {
            reportHistory.addFirst(new ReportRecord(
                report.type(),
                report.timestamp(),
                report.period()
            ));

            while (reportHistory.size() > MAX_HISTORY_SIZE) {
                reportHistory.removeLast();
            }
        }
    }

    private String getPeriodDescription(ReportType type, LocalDateTime now) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        return switch (type) {
            case HOURLY -> now.format(formatter) + " 小时报告";
            case DAILY -> now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + " 日报告";
            case WEEKLY -> "第" + ((now.getDayOfYear() - 1) / 7 + 1) + "周报告";
            case MONTHLY -> now.format(DateTimeFormatter.ofPattern("yyyy-MM")) + " 月报告";
        };
    }

    // ==================== 内部类 ====================

    /**
     * 报告类型
     */
    public enum ReportType {
        HOURLY("小时报告"),
        DAILY("日报告"),
        WEEKLY("周报告"),
        MONTHLY("月报告");

        private final String displayName;

        ReportType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * 报告
     */
    public record Report(
        ReportType type,
        String period,
        LocalDateTime timestamp,
        String content,
        Map<String, Object> metrics
    ) {}

    /**
     * 报告记录
     */
    public record ReportRecord(
        ReportType type,
        LocalDateTime timestamp,
        String period
    ) {}

    /**
     * 报告回调接口
     */
    @FunctionalInterface
    public interface ReportCallback {
        void onReportGenerated(Report report);
    }
}
