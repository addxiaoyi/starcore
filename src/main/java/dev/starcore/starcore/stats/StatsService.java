package dev.starcore.starcore.stats;

import dev.starcore.starcore.pvp.stats.PvPStatsService;
import dev.starcore.starcore.ranking.RankingService;
import dev.starcore.starcore.stats.analysis.TrendAnalysisService;
import dev.starcore.starcore.stats.dashboard.StatsDashboardService;
import dev.starcore.starcore.stats.export.StatsExportService;
import dev.starcore.starcore.stats.report.PeriodicReportService;
import dev.starcore.starcore.stats.visualization.VisualizationService;
import org.bukkit.plugin.Plugin;

/**
 * 统计服务主入口
 *
 * 整合所有统计子模块：
 * - 周期性报告 (PeriodicReportService)
 * - 数据可视化 (VisualizationService)
 * - 导出功能 (StatsExportService)
 * - 仪表盘 (StatsDashboardService)
 * - 趋势分析 (TrendAnalysisService)
 *
 * D-140: 已知问题：多数 stats 仍写内存 cache，重启清空。
 * 子服务（如 PvPStatsService）已有异步持久化；建议周期性报告中增加 DB batch 保存任务。
 */
public class StatsService {

    private final Plugin plugin;
    private final RankingService rankingService;
    private final PvPStatsService pvpStatsService;

    // 子服务
    private final PeriodicReportService periodicReportService;
    private final VisualizationService visualizationService;
    private final StatsExportService exportService;
    private final StatsDashboardService dashboardService;
    private final TrendAnalysisService trendAnalysisService;

    private volatile boolean initialized = false;

    public StatsService(Plugin plugin, RankingService rankingService, PvPStatsService pvpStatsService) {
        this.plugin = plugin;
        this.rankingService = rankingService;
        this.pvpStatsService = pvpStatsService;

        // 初始化子服务
        this.visualizationService = new VisualizationService();
        this.exportService = new StatsExportService(plugin, rankingService);
        this.dashboardService = new StatsDashboardService(rankingService, pvpStatsService);
        this.trendAnalysisService = new TrendAnalysisService(rankingService);
        this.periodicReportService = new PeriodicReportService(rankingService);
    }

    /**
     * 初始化统计服务
     */
    public void initialize() {
        if (initialized) return;

        // 启动周期性报告
        periodicReportService.start();

        initialized = true;
        plugin.getLogger().info("[Stats] 统计服务初始化完成");
    }

    /**
     * 关闭统计服务
     */
    public void shutdown() {
        if (!initialized) return;

        // 停止周期性报告
        periodicReportService.stop();

        initialized = false;
        plugin.getLogger().info("[Stats] 统计服务已关闭");
    }

    // ==================== Getter 方法 ====================

    public RankingService getRankingService() {
        return rankingService;
    }

    public PvPStatsService getPvPStatsService() {
        return pvpStatsService;
    }

    public PeriodicReportService getPeriodicReportService() {
        return periodicReportService;
    }

    public VisualizationService getVisualizationService() {
        return visualizationService;
    }

    public StatsExportService getExportService() {
        return exportService;
    }

    public StatsDashboardService getDashboardService() {
        return dashboardService;
    }

    public TrendAnalysisService getTrendAnalysisService() {
        return trendAnalysisService;
    }

    /**
     * 检查服务是否已初始化
     */
    public boolean isInitialized() {
        return initialized;
    }
}
