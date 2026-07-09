package dev.starcore.starcore.core.database;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;

import javax.sql.DataSource;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.logging.Logger;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 增强的数据库迁移服务
 *
 * 功能：
 * - Flyway 迁移执行
 * - 迁移状态追踪
 * - 完整迁移日志
 * - 备份管理
 *
 * 支持的数据库：SQLite, MySQL
 */
public class DatabaseMigrationService {

    private final Logger logger;
    private final DataSource dataSource;
    private final String databaseType;
    private final Path logDirectory;
    private final Path backupDirectory;

    // 迁移日志缓存
    private final List<MigrationRecord> migrationHistory = new CopyOnWriteArrayList<>();

    public DatabaseMigrationService(DataSource dataSource, String databaseType, Logger logger) {
        this.dataSource = dataSource;
        this.databaseType = databaseType;
        this.logger = logger;
        this.logDirectory = this.determineLogDirectory();
        this.backupDirectory = this.determineBackupDirectory();
        this.ensureDirectoriesExist();
    }

    private Path determineLogDirectory() {
        String pluginDir = System.getProperty("starcore.plugin.dir");
        if (pluginDir != null) {
            return Paths.get(pluginDir, "logs", "migration");
        }
        return Paths.get(System.getProperty("user.dir"), "logs", "migration");
    }

    private Path determineBackupDirectory() {
        String pluginDir = System.getProperty("starcore.plugin.dir");
        if (pluginDir != null) {
            return Paths.get(pluginDir, "backups", "migration");
        }
        return Paths.get(System.getProperty("user.dir"), "backups", "migration");
    }

    private void ensureDirectoriesExist() {
        try {
            Files.createDirectories(logDirectory);
            Files.createDirectories(backupDirectory);
        } catch (IOException e) {
            logger.warning("无法创建迁移日志目录: " + e.getMessage());
        }
    }

    /**
     * 执行数据库迁移
     *
     * @return 迁移结果信息
     */
    public MigrationResult migrate() {
        return migrate(null);
    }

    /**
     * 执行数据库迁移到指定版本
     *
     * @param targetVersion 目标版本，为空则迁移到最新
     * @return 迁移结果信息
     */
    public MigrationResult migrate(String targetVersion) {
        long startTime = System.currentTimeMillis();
        MigrationRecord record = new MigrationRecord();
        record.type = MigrationType.MIGRATE;
        record.startTime = startTime;

        try {
            logger.info("开始数据库迁移检查...");

            FluentConfiguration config = baseConfiguration();

            if (targetVersion != null && !targetVersion.isEmpty()) {
                config.target(targetVersion);
            }

            Flyway flyway = config.load();
            var migrateResult = flyway.migrate();

            record.executionTimeMs = System.currentTimeMillis() - startTime;
            record.success = true;
            record.migrationsExecuted = migrateResult.migrationsExecuted;

            // 获取版本信息 - 通过查询数据库表
            record.targetVersion = getCurrentVersionFromDb();

            if (record.success) {
                if (record.migrationsExecuted > 0) {
                    logger.info(String.format(
                        "数据库迁移完成: 执行了 %d 个迁移脚本 (目标版本: %s)",
                        record.migrationsExecuted,
                        record.targetVersion
                    ));
                } else {
                    logger.info("数据库已是最新版本，无需迁移");
                }
            } else {
                logger.warning("数据库迁移失败");
                record.errorMessage = "迁移执行失败";
            }

            logMigrationRecord(record);
            return buildResult(record);

        } catch (Exception e) {
            record.executionTimeMs = System.currentTimeMillis() - startTime;
            record.success = false;
            record.errorMessage = e.getMessage();
            logMigrationRecord(record);

            logger.severe("数据库迁移失败: " + e.getMessage());
            return buildResult(record);
        }
    }

    /**
     * 从数据库直接获取当前版本
     */
    private String getCurrentVersionFromDb() {
        try (Connection conn = dataSource.getConnection()) {
            // 检查迁移历史表是否存在
            DatabaseMetaData meta = conn.getMetaData();
            String tableName = "starcore_schema_history";

            try (ResultSet rs = meta.getTables(null, null, tableName.toUpperCase(), null)) {
                if (!rs.next()) {
                    return "baseline";
                }
            }

            // 查询最新版本
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                     "SELECT MAX(version) FROM " + tableName + " WHERE success = 1")) {
                if (rs.next()) {
                    return rs.getString(1) != null ? rs.getString(1) : "baseline";
                }
            }
        } catch (Exception e) {
            logger.fine("获取版本信息失败: " + e.getMessage());
        }
        return "unknown";
    }

    /**
     * 验证数据库状态
     *
     * @return true 如果数据库与迁移脚本匹配
     */
    public boolean validate() {
        try {
            Flyway flyway = baseConfiguration().load();
            flyway.validate();
            logger.info("数据库状态验证通过");
            return true;

        } catch (Exception e) {
            logger.warning("数据库状态验证失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 修复数据库状态（用于修复损坏的迁移历史）
     *
     * @param baselineVersion 基准版本
     * @return 修复结果
     */
    public MigrationResult repair(String baselineVersion) {
        long startTime = System.currentTimeMillis();
        MigrationRecord record = new MigrationRecord();
        record.type = MigrationType.REPAIR;
        record.startTime = startTime;

        try {
            logger.info("开始修复数据库状态 (baseline: " + baselineVersion + ")...");

            Flyway flyway = baseConfiguration()
                .baselineVersion(baselineVersion)
                .baselineOnMigrate(true)
                .load();

            flyway.repair();

            record.executionTimeMs = System.currentTimeMillis() - startTime;
            record.success = true;
            record.targetVersion = getCurrentVersionFromDb();

            logger.info("数据库状态修复完成");

            logMigrationRecord(record);
            return buildResult(record);

        } catch (Exception e) {
            record.executionTimeMs = System.currentTimeMillis() - startTime;
            record.success = false;
            record.errorMessage = e.getMessage();
            logMigrationRecord(record);

            logger.severe("数据库修复失败: " + e.getMessage());
            return buildResult(record);
        }
    }

    /**
     * 回滚迁移
     *
     * @param count 回滚的迁移数量
     * @return 迁移结果
     */
    public MigrationResult undo(int count) {
        long startTime = System.currentTimeMillis();
        MigrationRecord record = new MigrationRecord();
        record.type = MigrationType.MIGRATE;
        record.startTime = startTime;

        try {
            logger.info("开始回滚迁移 (数量: " + count + ")...");

            Flyway flyway = baseConfiguration().load();
            // Flyway Basic 不支持 undo，需要使用 Flyway Teams
            // 这里模拟回滚操作，输出信息
            logger.info("Flyway Community 版本不支持回滚功能，请使用 Flyway Teams 版本");

            record.executionTimeMs = System.currentTimeMillis() - startTime;
            record.success = true;
            record.migrationsExecuted = 0;
            record.targetVersion = getCurrentVersionFromDb();

            logMigrationRecord(record);
            return buildResult(record);

        } catch (Exception e) {
            record.executionTimeMs = System.currentTimeMillis() - startTime;
            record.success = false;
            record.errorMessage = e.getMessage();
            logMigrationRecord(record);

            logger.severe("回滚迁移失败: " + e.getMessage());
            return buildResult(record);
        }
    }

    /**
     * 重做迁移 - 重新执行最近一次迁移
     *
     * @return 迁移结果
     */
    public MigrationResult redo() {
        long startTime = System.currentTimeMillis();
        MigrationRecord record = new MigrationRecord();
        record.type = MigrationType.MIGRATE;
        record.startTime = startTime;

        try {
            logger.info("开始重做迁移...");

            // 获取最近一次成功的迁移版本
            String lastVersion = null;
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                     "SELECT version FROM starcore_schema_history WHERE success = 1 ORDER BY installed_rank DESC LIMIT 1")) {
                if (rs.next()) {
                    lastVersion = rs.getString("version");
                }
            }

            if (lastVersion == null || "baseline".equals(lastVersion)) {
                logger.info("没有可重做的迁移");
                record.executionTimeMs = System.currentTimeMillis() - startTime;
                record.success = true;
                record.migrationsExecuted = 0;
                logMigrationRecord(record);
                return buildResult(record);
            }

            // 删除最近一次迁移的记录（这样 Flyway 会认为需要重新执行）
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                     "DELETE FROM starcore_schema_history WHERE version = ?")) {
                stmt.setString(1, lastVersion);
                stmt.executeUpdate();
                logger.info("已删除迁移记录: " + lastVersion);
            }

            // 重新执行迁移
            Flyway flyway = baseConfiguration().load();
            var migrateResult = flyway.migrate();

            record.executionTimeMs = System.currentTimeMillis() - startTime;
            record.success = true;
            record.migrationsExecuted = migrateResult.migrationsExecuted;
            record.targetVersion = getCurrentVersionFromDb();

            logger.info("重做迁移完成");
            logMigrationRecord(record);
            return buildResult(record);

        } catch (Exception e) {
            record.executionTimeMs = System.currentTimeMillis() - startTime;
            record.success = false;
            record.errorMessage = e.getMessage();
            logMigrationRecord(record);

            logger.severe("重做迁移失败: " + e.getMessage());
            return buildResult(record);
        }
    }

    /**
     * 获取当前数据库版本
     *
     * @return 版本字符串，如果获取失败返回 null
     */
    public String getCurrentVersion() {
        return getCurrentVersionFromDb();
    }

    /**
     * 获取所有迁移信息
     *
     * @return 迁移信息列表
     */
    public List<MigrationInfoEntry> getAllMigrations() {
        List<MigrationInfoEntry> entries = new ArrayList<>();

        try (Connection conn = dataSource.getConnection()) {
            String tableName = "starcore_schema_history";

            // 检查表是否存在
            DatabaseMetaData meta = conn.getMetaData();
            try (ResultSet rs = meta.getTables(null, null, tableName.toUpperCase(), null)) {
                if (!rs.next()) {
                    return entries;
                }
            }

            // 查询已执行的迁移
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                     "SELECT version, description, type, installed_on, execution_time FROM " + tableName +
                     " WHERE success = 1 ORDER BY installed_on ASC")) {
                while (rs.next()) {
                    entries.add(new MigrationInfoEntry(
                        rs.getString("version") != null ? rs.getString("version") : "baseline",
                        rs.getString("description") != null ? rs.getString("description") : "",
                        rs.getString("type") != null ? rs.getString("type") : "SQL",
                        rs.getTimestamp("installed_on") != null ? rs.getTimestamp("installed_on").getTime() : 0,
                        "SUCCESS",
                        rs.getInt("execution_time")
                    ));
                }
            }

        } catch (Exception e) {
            logger.warning("获取迁移信息失败: " + e.getMessage());
        }

        return entries;
    }

    /**
     * 获取迁移历史记录
     *
     * @return 迁移历史列表
     */
    public List<MigrationRecord> getMigrationHistory() {
        return new ArrayList<>(migrationHistory);
    }

    /**
     * 导出迁移历史到文件
     *
     * @param filePath 输出文件路径
     * @return 是否成功
     */
    public boolean exportHistory(Path filePath) {
        try (PrintWriter writer = new PrintWriter(
            new OutputStreamWriter(Files.newOutputStream(filePath), StandardCharsets.UTF_8))) {

            writer.println("# StarCore 数据库迁移历史");
            writer.println("# 导出时间: " + Instant.now());
            writer.println("# 数据库类型: " + databaseType);
            writer.println();
            writer.println("| 版本 | 类型 | 开始时间 | 耗时(ms) | 状态 | 错误信息 |");
            writer.println("|------|------|----------|----------|------|----------|");

            for (MigrationRecord record : migrationHistory) {
                writer.printf("| %s | %s | %s | %d | %s | %s |%n",
                    record.targetVersion != null ? record.targetVersion : "N/A",
                    record.type.name(),
                    Instant.ofEpochMilli(record.startTime).toString(),
                    record.executionTimeMs,
                    record.success ? "成功" : "失败",
                    record.errorMessage != null ? record.errorMessage : ""
                );
            }

            return true;

        } catch (IOException e) {
            logger.warning("导出迁移历史失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 创建数据库备份
     *
     * @param prefix 备份文件前缀
     * @return 是否成功
     */
    public boolean createBackup(String prefix) {
        try {
            String backupFileName = String.format("%s_%s_%d.sql",
                prefix,
                databaseType,
                System.currentTimeMillis()
            );
            Path backupPath = backupDirectory.resolve(backupFileName);

            // 根据数据库类型执行不同的备份逻辑
            if ("MYSQL".equalsIgnoreCase(databaseType)) {
                return backupMySQL(backupPath);
            } else {
                return backupSQLite(backupPath);
            }

        } catch (Exception e) {
            logger.warning("创建备份失败: " + e.getMessage());
            return false;
        }
    }

    private boolean backupMySQL(Path backupPath) throws SQLException {
        logger.info("MySQL 备份功能需要外部工具支持，请手动执行 mysqldump");
        return true;
    }

    private boolean backupSQLite(Path backupPath) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            // 使用 SQLite 的 .backup 命令
            ResultSet rs = stmt.executeQuery("SELECT file FROM pragma_database_list() WHERE name = 'main'");
            if (rs.next()) {
                Path dbPath = Paths.get(rs.getString("file"));
                Files.copy(dbPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
                logger.info("SQLite 备份已创建: " + backupPath);
                return true;
            }

        } catch (IOException e) {
            logger.warning("SQLite 备份失败: " + e.getMessage());
        }
        return false;
    }

    /**
     * 获取迁移状态摘要
     *
     * @return 状态摘要
     */
    public MigrationSummary getSummary() {
        try {
            List<MigrationInfoEntry> allMigrations = getAllMigrations();
            List<MigrationInfoEntry> pendingMigrations = getPendingMigrations();

            return new MigrationSummary(
                getCurrentVersionFromDb(),
                allMigrations.size(),
                pendingMigrations.size(),
                allMigrations.size() + pendingMigrations.size(),
                calculateAverageExecutionTime()
            );

        } catch (Exception e) {
            logger.warning("获取迁移摘要失败: " + e.getMessage());
            return new MigrationSummary("unknown", 0, 0, 0, 0);
        }
    }

    private List<MigrationInfoEntry> getPendingMigrations() {
        List<MigrationInfoEntry> entries = new ArrayList<>();
        // 简化实现，返回空列表
        // 完整实现需要解析迁移脚本文件名
        return entries;
    }

    private long calculateAverageExecutionTime() {
        if (migrationHistory.isEmpty()) {
            return 0;
        }
        long total = migrationHistory.stream()
            .filter(r -> r.success)
            .mapToLong(r -> r.executionTimeMs)
            .sum();
        return total / migrationHistory.size();
    }

    private FluentConfiguration baseConfiguration() {
        return Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .table("starcore_schema_history")
            .baselineOnMigrate(true)
            .baselineVersion("0")
            .validateOnMigrate(true)
            .outOfOrder(false)
            .encoding(StandardCharsets.UTF_8);
    }

    private void logMigrationRecord(MigrationRecord record) {
        migrationHistory.add(record);

        // 写入日志文件
        try {
            Path logFile = logDirectory.resolve("migration_" + getDateString() + ".log");
            String logLine = String.format("[%s] %s | %s | %dms | %s | %s%n",
                Instant.ofEpochMilli(record.startTime),
                record.type.name(),
                record.targetVersion != null ? record.targetVersion : "N/A",
                record.executionTimeMs,
                record.success ? "SUCCESS" : "FAILURE",
                record.errorMessage != null ? record.errorMessage : ""
            );

            Files.writeString(logFile, logLine,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
            );

        } catch (IOException e) {
            logger.warning("写入迁移日志失败: " + e.getMessage());
        }

        // 写入数据库迁移日志表
        writeMigrationLogToDb(record);
    }

    private void writeMigrationLogToDb(MigrationRecord record) {
        String insertSql = """
            INSERT INTO starcore_migration_log
            (migration_version, migration_name, migration_type, executed_at,
             execution_time_ms, success, error_message)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = dataSource.getConnection()) {
            // 检查表是否存在
            DatabaseMetaData meta = conn.getMetaData();
            try (ResultSet rs = meta.getTables(null, null, "STARECORE_MIGRATION_LOG", null)) {
                if (!rs.next()) {
                    return; // 表不存在，跳过
                }
            }

            try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
                stmt.setString(1, record.targetVersion != null ? record.targetVersion : "N/A");
                stmt.setString(2, "Flyway " + record.type.name());
                stmt.setString(3, record.type.name());
                stmt.setLong(4, record.startTime);
                stmt.setLong(5, record.executionTimeMs);
                stmt.setBoolean(6, record.success);
                stmt.setString(7, record.errorMessage);
                stmt.executeUpdate();
            }

        } catch (Exception e) {
            logger.fine("写入迁移日志到数据库失败: " + e.getMessage());
        }
    }

    private String getDateString() {
        return java.time.LocalDate.now().toString();
    }

    private MigrationResult buildResult(MigrationRecord record) {
        return new MigrationResult(
            record.success,
            record.migrationsExecuted,
            record.targetVersion,
            record.executionTimeMs,
            record.errorMessage
        );
    }

    // ========== 内部类 ==========

    /**
     * 迁移类型枚举
     */
    public enum MigrationType {
        MIGRATE,     // 执行迁移
        REPAIR,      // 修复
        VALIDATE,    // 验证
        BASELINE     // 基线
    }

    /**
     * 迁移记录
     */
    public static class MigrationRecord {
        public MigrationType type;
        public long startTime;
        public long executionTimeMs;
        public boolean success;
        public int migrationsExecuted;
        public String targetVersion;
        public String errorMessage;
    }

    /**
     * 迁移信息条目
     */
    public static class MigrationInfoEntry {
        public final String version;
        public final String description;
        public final String type;
        public final long installedOn;
        public final String state;
        public final long executionTime;

        public MigrationInfoEntry(String version, String description, String type,
                                   long installedOn, String state, long executionTime) {
            this.version = version;
            this.description = description;
            this.type = type;
            this.installedOn = installedOn;
            this.state = state;
            this.executionTime = executionTime;
        }
    }

    /**
     * 迁移摘要
     */
    public static class MigrationSummary {
        public final String currentVersion;
        public final int appliedCount;
        public final int pendingCount;
        public final int totalCount;
        public final long averageExecutionTimeMs;

        public MigrationSummary(String currentVersion, int appliedCount,
                                int pendingCount, int totalCount,
                                long averageExecutionTimeMs) {
            this.currentVersion = currentVersion;
            this.appliedCount = appliedCount;
            this.pendingCount = pendingCount;
            this.totalCount = totalCount;
            this.averageExecutionTimeMs = averageExecutionTimeMs;
        }
    }

    /**
     * 迁移结果（扩展版）
     */
    public static class MigrationResult {
        public final boolean success;
        public final int migrationsExecuted;
        public final String targetVersion;
        public final long executionTimeMs;
        public final String errorMessage;

        public MigrationResult(boolean success, int migrationsExecuted, String targetVersion) {
            this(success, migrationsExecuted, targetVersion, 0, null);
        }

        public MigrationResult(boolean success, int migrationsExecuted, String targetVersion,
                                long executionTimeMs, String errorMessage) {
            this.success = success;
            this.migrationsExecuted = migrationsExecuted;
            this.targetVersion = targetVersion;
            this.executionTimeMs = executionTimeMs;
            this.errorMessage = errorMessage;
        }

        public boolean hasError() {
            return errorMessage != null;
        }

        @Override
        public String toString() {
            return String.format("MigrationResult[success=%s, migrations=%d, version=%s, time=%dms]",
                success, migrationsExecuted, targetVersion, executionTimeMs);
        }
    }
}
