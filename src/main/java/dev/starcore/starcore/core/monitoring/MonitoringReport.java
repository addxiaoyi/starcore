package dev.starcore.starcore.core.monitoring;

import java.util.Collections;
import java.util.Map;

/**
 * 监控报告
 *
 * 包含所有监控指标的快照
 */
public class MonitoringReport {

    private final long flywayMigrationCount;
    private final long flywayMigrationDurationMs;
    private final long flywayMigrationFailures;

    private final long propertiesToSqlSuccess;
    private final long propertiesToSqlFailure;
    private final long propertiesToSqlDurationMs;

    private final long databaseDegradationEvents;

    private final Map<String, MigrationMonitoringService.ModuleMetrics> moduleMetrics;

    public MonitoringReport(
            long flywayMigrationCount,
            long flywayMigrationDurationMs,
            long flywayMigrationFailures,
            long propertiesToSqlSuccess,
            long propertiesToSqlFailure,
            long propertiesToSqlDurationMs,
            long databaseDegradationEvents,
            Map<String, MigrationMonitoringService.ModuleMetrics> moduleMetrics) {
        this.flywayMigrationCount = flywayMigrationCount;
        this.flywayMigrationDurationMs = flywayMigrationDurationMs;
        this.flywayMigrationFailures = flywayMigrationFailures;
        this.propertiesToSqlSuccess = propertiesToSqlSuccess;
        this.propertiesToSqlFailure = propertiesToSqlFailure;
        this.propertiesToSqlDurationMs = propertiesToSqlDurationMs;
        this.databaseDegradationEvents = databaseDegradationEvents;
        this.moduleMetrics = Collections.unmodifiableMap(moduleMetrics);
    }

    // Getters

    public long getFlywayMigrationCount() {
        return flywayMigrationCount;
    }

    public long getFlywayMigrationDurationMs() {
        return flywayMigrationDurationMs;
    }

    public long getFlywayMigrationFailures() {
        return flywayMigrationFailures;
    }

    public long getPropertiesToSqlSuccess() {
        return propertiesToSqlSuccess;
    }

    public long getPropertiesToSqlFailure() {
        return propertiesToSqlFailure;
    }

    public long getPropertiesToSqlDurationMs() {
        return propertiesToSqlDurationMs;
    }

    public long getDatabaseDegradationEvents() {
        return databaseDegradationEvents;
    }

    public Map<String, MigrationMonitoringService.ModuleMetrics> getModuleMetrics() {
        return moduleMetrics;
    }

    /**
     * 计算 Flyway 平均迁移时间
     */
    public double getAverageFlywayMigrationTimeMs() {
        return flywayMigrationCount > 0
            ? (double) flywayMigrationDurationMs / flywayMigrationCount
            : 0.0;
    }

    /**
     * 计算 Properties 迁移成功率
     */
    public double getPropertiesMigrationSuccessRate() {
        long total = propertiesToSqlSuccess + propertiesToSqlFailure;
        return total > 0
            ? (double) propertiesToSqlSuccess / total * 100.0
            : 100.0;
    }

    /**
     * 生成文本格式报告
     */
    public String toText() {
        StringBuilder sb = new StringBuilder();

        sb.append("=== StarCore 监控报告 ===\n\n");

        // Flyway 迁移
        sb.append("【Flyway 迁移】\n");
        sb.append(String.format("  总次数: %d\n", flywayMigrationCount));
        sb.append(String.format("  总耗时: %d ms\n", flywayMigrationDurationMs));
        sb.append(String.format("  平均耗时: %.2f ms\n", getAverageFlywayMigrationTimeMs()));
        sb.append(String.format("  失败次数: %d\n\n", flywayMigrationFailures));

        // Properties 迁移
        sb.append("【Properties 迁移】\n");
        sb.append(String.format("  成功次数: %d\n", propertiesToSqlSuccess));
        sb.append(String.format("  失败次数: %d\n", propertiesToSqlFailure));
        sb.append(String.format("  成功率: %.2f%%\n", getPropertiesMigrationSuccessRate()));
        sb.append(String.format("  总耗时: %d ms\n\n", propertiesToSqlDurationMs));

        // 数据库降级
        sb.append("【数据库降级】\n");
        sb.append(String.format("  降级事件: %d 次\n\n", databaseDegradationEvents));

        // 模块性能
        if (!moduleMetrics.isEmpty()) {
            sb.append("【模块性能】\n");
            for (Map.Entry<String, MigrationMonitoringService.ModuleMetrics> entry : moduleMetrics.entrySet()) {
                String module = entry.getKey();
                MigrationMonitoringService.ModuleMetrics metrics = entry.getValue();

                sb.append(String.format("  模块: %s\n", module));
                sb.append(String.format("    加载: %d 次, 平均 %.2f ms, 失败 %d 次\n",
                    metrics.getLoadCount(),
                    metrics.getAverageLoadTimeMs(),
                    metrics.getLoadFailures()));
                sb.append(String.format("    保存: %d 次, 平均 %.2f ms, 失败 %d 次\n",
                    metrics.getSaveCount(),
                    metrics.getAverageSaveTimeMs(),
                    metrics.getSaveFailures()));
            }
        }

        return sb.toString();
    }

    @Override
    public String toString() {
        return toText();
    }
}
