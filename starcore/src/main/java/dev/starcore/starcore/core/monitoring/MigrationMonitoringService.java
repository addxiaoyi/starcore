package dev.starcore.starcore.core.monitoring;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 迁移监控服务
 *
 * 负责监控数据库迁移和性能指标：
 * - Flyway 迁移耗时和成功率
 * - Properties 到 SQL 迁移追踪
 * - 数据库降级事件记录
 * - 存储操作性能监控
 *
 * 提供 Prometheus 格式的指标导出
 */
public class MigrationMonitoringService {

    private final Logger logger;

    // Flyway 迁移指标
    private final AtomicLong flywayMigrationCount = new AtomicLong(0);
    private final AtomicLong flywayMigrationDurationMs = new AtomicLong(0);
    private final AtomicLong flywayMigrationFailures = new AtomicLong(0);

    // Properties 迁移指标
    private final AtomicLong propertiesToSqlSuccess = new AtomicLong(0);
    private final AtomicLong propertiesToSqlFailure = new AtomicLong(0);
    private final AtomicLong propertiesToSqlDurationMs = new AtomicLong(0);

    // 数据库降级指标
    private final AtomicLong databaseDegradationEvents = new AtomicLong(0);

    // 模块级别的性能指标
    private final ConcurrentHashMap<String, ModuleMetrics> moduleMetrics = new ConcurrentHashMap<>();

    // 告警阈值配置
    private final AlertThresholds thresholds;

    public MigrationMonitoringService(Logger logger) {
        this(logger, AlertThresholds.defaults());
    }

    public MigrationMonitoringService(Logger logger, AlertThresholds thresholds) {
        this.logger = logger;
        this.thresholds = thresholds;
    }

    /**
     * 记录 Flyway 迁移
     *
     * @param scriptsExecuted 执行的脚本数量
     * @param durationMs 迁移耗时（毫秒）
     * @param success 是否成功
     */
    public void recordFlywayMigration(int scriptsExecuted, long durationMs, boolean success) {
        flywayMigrationCount.incrementAndGet();
        flywayMigrationDurationMs.addAndGet(durationMs);

        if (!success) {
            flywayMigrationFailures.incrementAndGet();
        }

        logger.info(String.format(
            "[监控] Flyway 迁移%s: %d 个脚本, 耗时 %d ms",
            success ? "完成" : "失败",
            scriptsExecuted,
            durationMs
        ));

        // 超过阈值发出告警
        if (durationMs > thresholds.flywayMigrationSlowMs) {
            alertSlowMigration(scriptsExecuted, durationMs);
        }

        if (!success) {
            alertMigrationFailure("Flyway", "迁移执行失败");
        }
    }

    /**
     * 记录 Properties 到 SQL 迁移
     *
     * @param module 模块名称
     * @param success 是否成功
     * @param durationMs 迁移耗时（毫秒）
     * @param recordCount 迁移的记录数
     */
    public void recordPropertiesToSqlMigration(String module, boolean success, long durationMs, int recordCount) {
        if (success) {
            propertiesToSqlSuccess.incrementAndGet();
            propertiesToSqlDurationMs.addAndGet(durationMs);

            logger.info(String.format(
                "[监控] 模块 %s Properties 迁移成功: %d 条记录, 耗时 %d ms",
                module,
                recordCount,
                durationMs
            ));
        } else {
            propertiesToSqlFailure.incrementAndGet();

            logger.warning(String.format(
                "[监控] 模块 %s Properties 迁移失败",
                module
            ));

            alertMigrationFailure(module, "Properties 到 SQL 迁移失败");
        }
    }

    /**
     * 记录数据库降级事件
     *
     * @param module 模块名称
     * @param reason 降级原因
     */
    public void recordDatabaseDegradation(String module, String reason) {
        databaseDegradationEvents.incrementAndGet();

        logger.log(Level.SEVERE, String.format(
            "[监控] 数据库降级到 Properties: 模块=%s, 原因=%s",
            module,
            reason
        ));

        // 立即告警 - 这是严重事件
        alertDatabaseDegradation(module, reason);
    }

    /**
     * 记录存储操作性能
     *
     * @param module 模块名称
     * @param operation 操作类型 ("load" 或 "save")
     * @param durationMs 操作耗时（毫秒）
     * @param success 是否成功
     */
    public void recordStorageOperation(String module, String operation, long durationMs, boolean success) {
        ModuleMetrics metrics = moduleMetrics.computeIfAbsent(
            module,
            k -> new ModuleMetrics()
        );

        if ("load".equals(operation)) {
            metrics.recordLoad(durationMs, success);
        } else if ("save".equals(operation)) {
            metrics.recordSave(durationMs, success);
        }

        // 性能告警
        if (durationMs > thresholds.storageOperationSlowMs) {
            alertSlowOperation(module, operation, durationMs);
        }

        // 失败告警
        if (!success) {
            logger.warning(String.format(
                "[监控] 存储操作失败: 模块=%s, 操作=%s, 耗时=%d ms",
                module,
                operation,
                durationMs
            ));
        }
    }

    /**
     * 获取监控报告
     */
    public MonitoringReport getReport() {
        return new MonitoringReport(
            flywayMigrationCount.get(),
            flywayMigrationDurationMs.get(),
            flywayMigrationFailures.get(),
            propertiesToSqlSuccess.get(),
            propertiesToSqlFailure.get(),
            propertiesToSqlDurationMs.get(),
            databaseDegradationEvents.get(),
            new HashMap<>(moduleMetrics)
        );
    }

    /**
     * 导出 Prometheus 格式指标
     */
    public String exportPrometheusMetrics() {
        StringBuilder metrics = new StringBuilder();

        // Flyway 迁移指标
        appendMetric(metrics,
            "starcore_flyway_migrations_total",
            "counter",
            "Flyway 迁移总次数",
            flywayMigrationCount.get());

        appendMetric(metrics,
            "starcore_flyway_migrations_duration_ms_total",
            "counter",
            "Flyway 迁移总耗时（毫秒）",
            flywayMigrationDurationMs.get());

        appendMetric(metrics,
            "starcore_flyway_migrations_failures_total",
            "counter",
            "Flyway 迁移失败次数",
            flywayMigrationFailures.get());

        // Properties 迁移指标
        appendMetric(metrics,
            "starcore_properties_migrations_success_total",
            "counter",
            "Properties 迁移成功次数",
            propertiesToSqlSuccess.get());

        appendMetric(metrics,
            "starcore_properties_migrations_failure_total",
            "counter",
            "Properties 迁移失败次数",
            propertiesToSqlFailure.get());

        appendMetric(metrics,
            "starcore_properties_migrations_duration_ms_total",
            "counter",
            "Properties 迁移总耗时（毫秒）",
            propertiesToSqlDurationMs.get());

        // 数据库降级指标
        appendMetric(metrics,
            "starcore_database_degradations_total",
            "counter",
            "数据库降级事件总数",
            databaseDegradationEvents.get());

        // 模块级别指标
        for (Map.Entry<String, ModuleMetrics> entry : moduleMetrics.entrySet()) {
            String module = entry.getKey();
            ModuleMetrics m = entry.getValue();

            appendMetricWithLabel(metrics,
                "starcore_storage_operations_total",
                "counter",
                "存储操作总次数",
                "module", module,
                "operation", "load",
                m.loadCount.get());

            appendMetricWithLabel(metrics,
                "starcore_storage_operations_total",
                "counter",
                "存储操作总次数",
                "module", module,
                "operation", "save",
                m.saveCount.get());

            appendMetricWithLabel(metrics,
                "starcore_storage_operations_duration_ms_total",
                "counter",
                "存储操作总耗时（毫秒）",
                "module", module,
                "operation", "load",
                m.loadDurationMs.get());

            appendMetricWithLabel(metrics,
                "starcore_storage_operations_duration_ms_total",
                "counter",
                "存储操作总耗时（毫秒）",
                "module", module,
                "operation", "save",
                m.saveDurationMs.get());

            appendMetricWithLabel(metrics,
                "starcore_storage_operations_failures_total",
                "counter",
                "存储操作失败次数",
                "module", module,
                "operation", "load",
                m.loadFailures.get());

            appendMetricWithLabel(metrics,
                "starcore_storage_operations_failures_total",
                "counter",
                "存储操作失败次数",
                "module", module,
                "operation", "save",
                m.saveFailures.get());
        }

        return metrics.toString();
    }

    /**
     * 重置所有指标（仅用于测试）
     */
    public void resetMetrics() {
        flywayMigrationCount.set(0);
        flywayMigrationDurationMs.set(0);
        flywayMigrationFailures.set(0);
        propertiesToSqlSuccess.set(0);
        propertiesToSqlFailure.set(0);
        propertiesToSqlDurationMs.set(0);
        databaseDegradationEvents.set(0);
        moduleMetrics.clear();
    }

    // ========== 告警方法 ==========

    private void alertSlowMigration(int scriptsExecuted, long durationMs) {
        logger.log(Level.WARNING, String.format(
            "[告警] Flyway 迁移缓慢: %d 个脚本耗时 %d ms (阈值: %d ms)",
            scriptsExecuted,
            durationMs,
            thresholds.flywayMigrationSlowMs
        ));
    }

    private void alertMigrationFailure(String source, String reason) {
        logger.log(Level.SEVERE, String.format(
            "[告警] 数据库迁移失败: 来源=%s, 原因=%s",
            source,
            reason
        ));
    }

    private void alertDatabaseDegradation(String module, String reason) {
        logger.log(Level.SEVERE, String.format(
            "[告警] 数据库降级事件: 模块=%s, 原因=%s - 系统已降级到 Properties 文件存储",
            module,
            reason
        ));
    }

    private void alertSlowOperation(String module, String operation, long durationMs) {
        logger.log(Level.WARNING, String.format(
            "[告警] 存储操作缓慢: 模块=%s, 操作=%s, 耗时=%d ms (阈值: %d ms)",
            module,
            operation,
            durationMs,
            thresholds.storageOperationSlowMs
        ));
    }

    // ========== 辅助方法 ==========

    private void appendMetric(StringBuilder sb, String name, String type, String help, long value) {
        sb.append("# HELP ").append(name).append(" ").append(help).append("\n");
        sb.append("# TYPE ").append(name).append(" ").append(type).append("\n");
        sb.append(name).append(" ").append(value).append("\n\n");
    }

    private void appendMetricWithLabel(StringBuilder sb, String name, String type, String help,
                                        String label1Key, String label1Value,
                                        String label2Key, String label2Value,
                                        long value) {
        // 只在第一次输出时添加 HELP 和 TYPE
        if (!sb.toString().contains("# TYPE " + name + " ")) {
            sb.append("# HELP ").append(name).append(" ").append(help).append("\n");
            sb.append("# TYPE ").append(name).append(" ").append(type).append("\n");
        }

        sb.append(name)
          .append("{")
          .append(label1Key).append("=\"").append(label1Value).append("\",")
          .append(label2Key).append("=\"").append(label2Value).append("\"")
          .append("} ")
          .append(value)
          .append("\n");
    }

    /**
     * 模块性能指标
     */
    public static class ModuleMetrics {
        private final AtomicLong loadCount = new AtomicLong(0);
        private final AtomicLong loadDurationMs = new AtomicLong(0);
        private final AtomicLong loadFailures = new AtomicLong(0);

        private final AtomicLong saveCount = new AtomicLong(0);
        private final AtomicLong saveDurationMs = new AtomicLong(0);
        private final AtomicLong saveFailures = new AtomicLong(0);

        public void recordLoad(long durationMs, boolean success) {
            loadCount.incrementAndGet();
            loadDurationMs.addAndGet(durationMs);
            if (!success) {
                loadFailures.incrementAndGet();
            }
        }

        public void recordSave(long durationMs, boolean success) {
            saveCount.incrementAndGet();
            saveDurationMs.addAndGet(durationMs);
            if (!success) {
                saveFailures.incrementAndGet();
            }
        }

        public long getLoadCount() { return loadCount.get(); }
        public long getLoadDurationMs() { return loadDurationMs.get(); }
        public long getLoadFailures() { return loadFailures.get(); }
        public long getSaveCount() { return saveCount.get(); }
        public long getSaveDurationMs() { return saveDurationMs.get(); }
        public long getSaveFailures() { return saveFailures.get(); }

        public double getAverageLoadTimeMs() {
            long count = loadCount.get();
            return count > 0 ? (double) loadDurationMs.get() / count : 0.0;
        }

        public double getAverageSaveTimeMs() {
            long count = saveCount.get();
            return count > 0 ? (double) saveDurationMs.get() / count : 0.0;
        }
    }

    /**
     * 告警阈值配置
     */
    public static class AlertThresholds {
        public final long flywayMigrationSlowMs;
        public final long storageOperationSlowMs;

        public AlertThresholds(long flywayMigrationSlowMs, long storageOperationSlowMs) {
            this.flywayMigrationSlowMs = flywayMigrationSlowMs;
            this.storageOperationSlowMs = storageOperationSlowMs;
        }

        public static AlertThresholds defaults() {
            return new AlertThresholds(
                30000,  // Flyway 迁移超过 30 秒告警
                5000    // 存储操作超过 5 秒告警
            );
        }
    }
}
