package dev.starcore.starcore.core.database;

import dev.starcore.starcore.core.persistence.PersistenceService;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * 增强的数据一致性验证器
 *
 * 功能：
 * - Properties 与 SQL 数据比对
 * - 数据库表结构验证
 * - 迁移完整性检查
 * - 数据完整性约束验证
 * - 生成详细验证报告
 *
 * 支持的模块：
 * - nation, diplomacy, policy, resource
 * - technology, treasury, war, officer
 * - event, resolution, territory
 */
public class EnhancedDataValidator {

    private final Logger logger;
    private final PersistenceService persistenceService;
    private final DataSource dataSource;

    /**
     * 模块配置：模块名 -> (文件名, 表名)
     */
    private static final Map<String, ModuleConfig> MODULES = new LinkedHashMap<>();

    static {
        MODULES.put("nation", new ModuleConfig("nations.properties", "starcore_nation_state"));
        MODULES.put("diplomacy", new ModuleConfig("relations.properties", "starcore_diplomacy_state"));
        MODULES.put("policy", new ModuleConfig("policies.properties", "starcore_policy_state"));
        MODULES.put("resource", new ModuleConfig("resources.properties", "starcore_resource_state"));
        MODULES.put("technology", new ModuleConfig("technologies.properties", "starcore_technology_state"));
        MODULES.put("treasury", new ModuleConfig("treasuries.properties", "starcore_treasury_state"));
        MODULES.put("war", new ModuleConfig("wars.properties", "starcore_war_state"));
        MODULES.put("officer", new ModuleConfig("officers.properties", "starcore_officer_state"));
        MODULES.put("event", new ModuleConfig("events.properties", "starcore_event_state"));
        MODULES.put("resolution", new ModuleConfig("resolutions.properties", "starcore_resolution_state"));
    }

    /**
     * 已知表列表
     */
    private static final Set<String> KNOWN_TABLES = Set.of(
        "starcore_nation_state",
        "starcore_diplomacy_state",
        "starcore_policy_state",
        "starcore_resource_state",
        "starcore_technology_state",
        "starcore_treasury_state",
        "starcore_war_state",
        "starcore_officer_state",
        "starcore_event_state",
        "starcore_resolution_state",
        "starcore_territory_state",
        "starcore_player_balance",
        "starcore_economy_transactions",
        "starcore_map_chunks",
        "starcore_map_markers",
        "starcore_bankruptcy_state",
        "starcore_player_locale",
        "starcore_transfer_config",
        "starcore_territories",
        "starcore_territories_permissions",
        "starcore_territory_members",
        "starcore_subregions",
        "starcore_subregions_permissions",
        "starcore_friend_relations",
        "starcore_friend_requests",
        "starcore_blacklist",
        "starcore_guilds",
        "starcore_guild_members",
        "starcore_guild_invites",
        "starcore_parties",
        "starcore_party_members",
        "starcore_party_invites",
        "starcore_player_status",
        "starcore_schema_history",
        "starcore_metadata",
        "starcore_migration_log",
        "starcore_backup_records"
    );

    public EnhancedDataValidator(
        PersistenceService persistenceService,
        DataSource dataSource,
        Logger logger
    ) {
        this.persistenceService = Objects.requireNonNull(persistenceService, "persistenceService");
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    // ========== 主验证方法 ==========

    /**
     * 执行完整验证
     *
     * @return 完整验证结果
     */
    public ValidationReport validateAll() {
        ValidationReport report = new ValidationReport();
        report.startTime = Instant.now();

        logger.info("========== 开始完整数据验证 ==========");

        // 1. 验证数据库连接
        validateDatabaseConnection(report);

        // 2. 验证表结构
        validateTableStructure(report);

        // 3. 验证模块数据一致性
        validateModuleConsistency(report);

        // 4. 验证迁移历史
        validateMigrationHistory(report);

        // 5. 验证数据完整性约束
        validateDataIntegrity(report);

        report.endTime = Instant.now();
        logger.info("========== 数据验证完成 ==========");

        return report;
    }

    /**
     * 验证单个模块
     */
    public ValidationResult validateModule(String moduleName) {
        ModuleConfig config = MODULES.get(moduleName);
        if (config == null) {
            return ValidationResult.error(moduleName, "未知的模块: " + moduleName);
        }

        logger.info("验证模块: " + moduleName);

        try {
            // 加载 Properties 数据
            Properties propertiesData = persistenceService.loadProperties("starcore", config.fileName);
            logger.info("  - Properties 加载: " + propertiesData.size() + " 个键");

            // 加载 SQL 数据
            Properties sqlData = loadFromSql(config.tableName);
            logger.info("  - SQL 加载: " + sqlData.size() + " 个键");

            // 比对数据
            return compareProperties(moduleName, propertiesData, sqlData);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "验证模块 " + moduleName + " 失败", e);
            return ValidationResult.error(moduleName, e.getMessage());
        }
    }

    /**
     * 验证表结构
     */
    public void validateTableStructure(ValidationReport report) {
        logger.info("验证表结构...");

        Set<String> existingTables = getExistingTables();
        Set<String> expectedTables = new HashSet<>(KNOWN_TABLES);

        // 找出缺失的表
        Set<String> missingTables = new TreeSet<>(expectedTables);
        missingTables.removeAll(existingTables);

        // 找出多余的表
        Set<String> extraTables = new TreeSet<>(existingTables);
        extraTables.removeAll(expectedTables);

        for (String table : missingTables) {
            report.addIssue(new ValidationIssue(
                IssueType.MISSING_TABLE,
                "表缺失",
                table,
                "表 " + table + " 不存在"
            ));
        }

        for (String table : extraTables) {
            report.addIssue(new ValidationIssue(
                IssueType.EXTRA_TABLE,
                "额外表",
                table,
                "发现未知表: " + table
            ));
        }

        if (missingTables.isEmpty() && extraTables.isEmpty()) {
            logger.info("  ✓ 所有已知表都存在");
        }
    }

    /**
     * 验证迁移历史
     */
    public void validateMigrationHistory(ValidationReport report) {
        logger.info("验证迁移历史...");

        try (Connection conn = dataSource.getConnection()) {
            // 检查 starcore_schema_history 表
            if (!tableExists(conn, "starcore_schema_history")) {
                report.addIssue(new ValidationIssue(
                    IssueType.MISSING_TABLE,
                    "迁移历史",
                    "starcore_schema_history",
                    "迁移历史表不存在"
                ));
                return;
            }

            // 获取已应用的迁移
            Set<String> appliedMigrations = new TreeSet<>();
            String query = "SELECT version FROM starcore_schema_history WHERE success = 1";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query)) {
                while (rs.next()) {
                    appliedMigrations.add(rs.getString("version"));
                }
            }

            logger.info("  - 已应用迁移: " + appliedMigrations);

            // 检查是否有失败的迁移
            String failedQuery = "SELECT version, description FROM starcore_schema_history WHERE success = 0";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(failedQuery)) {
                while (rs.next()) {
                    report.addIssue(new ValidationIssue(
                        IssueType.MIGRATION_FAILURE,
                        "迁移失败",
                        rs.getString("version"),
                        "迁移失败: " + rs.getString("description")
                    ));
                }
            }

        } catch (Exception e) {
            report.addIssue(new ValidationIssue(
                IssueType.ERROR,
                "迁移验证",
                "starcore_schema_history",
                e.getMessage()
            ));
        }
    }

    /**
     * 验证数据完整性约束
     */
    public void validateDataIntegrity(ValidationReport report) {
        logger.info("验证数据完整性...");

        try (Connection conn = dataSource.getConnection()) {
            // 检查玩家余额表的外键关系
            validatePlayerBalanceIntegrity(conn, report);

            // 检查领土表的引用完整性
            validateTerritoryIntegrity(conn, report);

            // 检查社交关系表的完整性
            validateSocialIntegrity(conn, report);

        } catch (Exception e) {
            logger.log(Level.WARNING, "数据完整性验证失败", e);
        }
    }

    private void validatePlayerBalanceIntegrity(Connection conn, ValidationReport report) throws SQLException {
        // 检查是否有负余额
        String query = "SELECT player_uuid, balance FROM starcore_player_balance WHERE balance < 0";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            while (rs.next()) {
                report.addIssue(new ValidationIssue(
                    IssueType.INTEGRITY_VIOLATION,
                    "余额异常",
                    rs.getString("player_uuid"),
                    "玩家 " + rs.getString("player_uuid") + " 余额为负: " + rs.getDouble("balance")
                ));
            }
        }
    }

    private void validateTerritoryIntegrity(Connection conn, ValidationReport report) throws SQLException {
        // 检查是否有引用不存在的国家的领土
        String query = """
            SELECT t.territory_id, t.nation_id
            FROM starcore_territories t
            LEFT JOIN starcore_nation_state n ON t.nation_id = SUBSTRING(n.property_key, 8, 36)
            WHERE t.nation_id IS NOT NULL AND n.property_key IS NULL
            """;
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            while (rs.next()) {
                report.addIssue(new ValidationIssue(
                    IssueType.ORPHAN_REFERENCE,
                    "孤儿引用",
                    rs.getString("territory_id"),
                    "领土引用不存在的国家: " + rs.getString("nation_id")
                ));
            }
        }
    }

    private void validateSocialIntegrity(Connection conn, ValidationReport report) throws SQLException {
        // 检查好友关系是否双向存在（可选检查）
        String query = """
            SELECT fr1.player_uuid, fr1.friend_uuid
            FROM starcore_friend_relations fr1
            LEFT JOIN starcore_friend_relations fr2
                ON fr1.player_uuid = fr2.friend_uuid AND fr1.friend_uuid = fr2.player_uuid
            WHERE fr2.player_uuid IS NULL
            LIMIT 100
            """;
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            int count = 0;
            while (rs.next() && count < 10) {
                report.addIssue(new ValidationIssue(
                    IssueType.INTEGRITY_VIOLATION,
                    "单向好友关系",
                    rs.getString("player_uuid"),
                    "玩家 " + rs.getString("player_uuid") + " 关注了 " + rs.getString("friend_uuid") + " 但对方未回关"
                ));
                count++;
            }
        }
    }

    private void validateDatabaseConnection(ValidationReport report) {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            logger.info("  - 数据库产品: " + meta.getDatabaseProductName());
            logger.info("  - 数据库版本: " + meta.getDatabaseProductVersion());
            report.databaseProduct = meta.getDatabaseProductName();
            report.connected = true;
        } catch (Exception e) {
            report.addIssue(new ValidationIssue(
                IssueType.ERROR,
                "数据库连接",
                "connection",
                e.getMessage()
            ));
        }
    }

    private void validateModuleConsistency(ValidationReport report) {
        for (String moduleName : MODULES.keySet()) {
            ValidationResult result = validateModule(moduleName);
            report.addModuleResult(moduleName, result);
        }
    }

    // ========== 辅助方法 ==========

    private Set<String> getExistingTables() {
        Set<String> tables = new TreeSet<>();
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            String schema = null;
            if (meta.storesLowerCaseIdentifiers()) {
                schema = conn.getCatalog();
            }

            try (ResultSet rs = meta.getTables(null, schema, "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    tables.add(rs.getString("TABLE_NAME").toLowerCase());
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "获取表列表失败", e);
        }
        return tables;
    }

    private boolean tableExists(Connection conn, String tableName) throws SQLException {
        DatabaseMetaData meta = conn.getMetaData();
        try (ResultSet rs = meta.getTables(null, null, tableName, new String[]{"TABLE"})) {
            return rs.next();
        }
    }

    private Properties loadFromSql(String tableName) throws SQLException {
        Properties properties = new Properties();
        String query = String.format(
            "SELECT property_key, property_value FROM %s ORDER BY property_key",
            tableName
        );

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query);
             ResultSet resultSet = statement.executeQuery()) {

            while (resultSet.next()) {
                String key = resultSet.getString("property_key");
                String value = resultSet.getString("property_value");
                properties.setProperty(key, value);
            }
        }

        return properties;
    }

    private ValidationResult compareProperties(
        String moduleName,
        Properties propertiesData,
        Properties sqlData
    ) {
        Set<String> propKeys = propertiesData.stringPropertyNames();
        Set<String> sqlKeys = sqlData.stringPropertyNames();

        Set<String> onlyInProperties = new TreeSet<>(propKeys);
        onlyInProperties.removeAll(sqlKeys);

        Set<String> onlyInSql = new TreeSet<>(sqlKeys);
        onlyInSql.removeAll(propKeys);

        Map<String, ValueDifference> valueDifferences = new TreeMap<>();
        for (String key : propKeys) {
            if (sqlKeys.contains(key)) {
                String propValue = propertiesData.getProperty(key);
                String sqlValue = sqlData.getProperty(key);
                if (!Objects.equals(propValue, sqlValue)) {
                    valueDifferences.put(key, new ValueDifference(propValue, sqlValue));
                }
            }
        }

        return new ValidationResult(
            moduleName,
            propKeys.size(),
            sqlKeys.size(),
            onlyInProperties,
            onlyInSql,
            valueDifferences,
            null
        );
    }

    /**
     * 生成验证报告
     */
    public void generateReport(ValidationReport report, Path outputPath) throws IOException {
        StringBuilder sb = new StringBuilder();

        sb.append("# StarCore 数据一致性验证报告\n\n");
        sb.append("**生成时间**: ").append(report.startTime).append("\n");
        sb.append("**完成时间**: ").append(report.endTime).append("\n");
        sb.append("**数据库产品**: ").append(report.databaseProduct).append("\n\n");

        // 统计
        sb.append("## 总览\n\n");
        int totalIssues = report.issues.size();
        int passedModules = (int) report.moduleResults.values().stream()
            .filter(ValidationResult::isConsistent).count();
        int failedModules = report.moduleResults.size() - passedModules;

        sb.append("| 类型 | 数量 |\n");
        sb.append("|------|------|\n");
        sb.append("| 通过模块 | ").append(passedModules).append(" |\n");
        sb.append("| 失败模块 | ").append(failedModules).append(" |\n");
        sb.append("| 发现问题 | ").append(totalIssues).append(" |\n\n");

        // 问题列表
        if (!report.issues.isEmpty()) {
            sb.append("## 发现的问题\n\n");
            for (ValidationIssue issue : report.issues) {
                sb.append("- **").append(issue.type.name()).append("**: ")
                    .append(issue.category).append(" - ")
                    .append(issue.target).append("\n");
                sb.append("  - ").append(issue.description).append("\n\n");
            }
        }

        // 模块详细结果
        sb.append("## 模块验证结果\n\n");
        for (Map.Entry<String, ValidationResult> entry : report.moduleResults.entrySet()) {
            String moduleName = entry.getKey();
            ValidationResult result = entry.getValue();

            sb.append("### ").append(moduleName).append(" 模块\n\n");
            sb.append("- Properties 键: ").append(result.propertiesKeyCount).append("\n");
            sb.append("- SQL 键: ").append(result.sqlKeyCount).append("\n");
            sb.append("- 状态: ").append(result.isConsistent() ? "✅ 通过" : "❌ 失败").append("\n\n");

            if (!result.onlyInProperties.isEmpty()) {
                sb.append("**仅在 Properties 中** (").append(result.onlyInProperties.size()).append("):\n");
                result.onlyInProperties.stream().limit(5)
                    .forEach(k -> sb.append("- `").append(k).append("`\n"));
                if (result.onlyInProperties.size() > 5) {
                    sb.append("- ... 还有 ").append(result.onlyInProperties.size() - 5).append(" 个\n");
                }
                sb.append("\n");
            }

            if (!result.onlyInSql.isEmpty()) {
                sb.append("**仅在 SQL 中** (").append(result.onlyInSql.size()).append("):\n");
                result.onlyInSql.stream().limit(5)
                    .forEach(k -> sb.append("- `").append(k).append("`\n"));
                if (result.onlyInSql.size() > 5) {
                    sb.append("- ... 还有 ").append(result.onlyInSql.size() - 5).append(" 个\n");
                }
                sb.append("\n");
            }

            if (!result.valueDifferences.isEmpty()) {
                sb.append("**值不一致** (").append(result.valueDifferences.size()).append("):\n");
                result.valueDifferences.entrySet().stream().limit(5)
                    .forEach(e -> sb.append("- `").append(e.getKey()).append("`\n"));
                if (result.valueDifferences.size() > 5) {
                    sb.append("- ... 还有 ").append(result.valueDifferences.size() - 5).append(" 个\n");
                }
                sb.append("\n");
            }
        }

        Files.writeString(outputPath, sb.toString());
        logger.info("报告已生成: " + outputPath);
    }

    // ========== 内部类 ==========

    public static class ModuleConfig {
        final String fileName;
        final String tableName;

        ModuleConfig(String fileName, String tableName) {
            this.fileName = fileName;
            this.tableName = tableName;
        }
    }

    public static class ValueDifference {
        public final String propertiesValue;
        public final String sqlValue;

        ValueDifference(String propertiesValue, String sqlValue) {
            this.propertiesValue = propertiesValue;
            this.sqlValue = sqlValue;
        }
    }

    public static class ValidationResult {
        public final String moduleName;
        public final int propertiesKeyCount;
        public final int sqlKeyCount;
        public final Set<String> onlyInProperties;
        public final Set<String> onlyInSql;
        public final Map<String, ValueDifference> valueDifferences;
        public final String errorMessage;

        ValidationResult(
            String moduleName,
            int propertiesKeyCount,
            int sqlKeyCount,
            Set<String> onlyInProperties,
            Set<String> onlyInSql,
            Map<String, ValueDifference> valueDifferences,
            String errorMessage
        ) {
            this.moduleName = moduleName;
            this.propertiesKeyCount = propertiesKeyCount;
            this.sqlKeyCount = sqlKeyCount;
            this.onlyInProperties = onlyInProperties;
            this.onlyInSql = onlyInSql;
            this.valueDifferences = valueDifferences;
            this.errorMessage = errorMessage;
        }

        public boolean isConsistent() {
            return !hasError()
                && onlyInProperties.isEmpty()
                && onlyInSql.isEmpty()
                && valueDifferences.isEmpty();
        }

        public boolean hasError() {
            return errorMessage != null;
        }

        static ValidationResult error(String moduleName, String errorMessage) {
            return new ValidationResult(
                moduleName,
                0,
                0,
                Collections.emptySet(),
                Collections.emptySet(),
                Collections.emptyMap(),
                errorMessage
            );
        }
    }

    public enum IssueType {
        MISSING_TABLE,
        EXTRA_TABLE,
        MIGRATION_FAILURE,
        INTEGRITY_VIOLATION,
        ORPHAN_REFERENCE,
        ERROR
    }

    public static class ValidationIssue {
        public final IssueType type;
        public final String category;
        public final String target;
        public final String description;

        ValidationIssue(IssueType type, String category, String target, String description) {
            this.type = type;
            this.category = category;
            this.target = target;
            this.description = description;
        }
    }

    public static class ValidationReport {
        public Instant startTime;
        public Instant endTime;
        public boolean connected = false;
        public String databaseProduct = "Unknown";
        public final List<ValidationIssue> issues = new ArrayList<>();
        public final Map<String, ValidationResult> moduleResults = new LinkedHashMap<>();

        public void addIssue(ValidationIssue issue) {
            issues.add(issue);
        }

        public void addModuleResult(String moduleName, ValidationResult result) {
            moduleResults.put(moduleName, result);
        }

        public int getIssueCount() {
            return issues.size();
        }

        public int getPassedModuleCount() {
            return (int) moduleResults.values().stream().filter(ValidationResult::isConsistent).count();
        }
    }
}
