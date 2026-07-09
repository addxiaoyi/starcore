package dev.starcore.starcore.core.database;

import dev.starcore.starcore.core.persistence.PersistenceService;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 数据一致性验证工具
 *
 * 用于验证 Properties 文件和 SQL 数据库中的数据完全一致
 *
 * 功能：
 * - 比对所有模块的 Properties 和 SQL 数据
 * - 检测键值对数量差异
 * - 检测值不一致
 * - 生成详细的验证报告
 *
 * 支持的模块：
 * - nation (nations.properties)
 * - diplomacy (relations.properties)
 * - policy (policies.properties)
 * - resource (resources.properties)
 * - technology (technologies.properties)
 * - treasury (treasuries.properties)
 * - war (wars.properties)
 * - officer (officers.properties)
 * - event (events.properties)
 * - resolution (resolutions.properties)
 */
public class DataConsistencyValidator {

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

    public DataConsistencyValidator(
        PersistenceService persistenceService,
        DataSource dataSource,
        Logger logger
    ) {
        this.persistenceService = Objects.requireNonNull(persistenceService, "persistenceService");
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * 验证单个模块的数据一致性
     *
     * @param moduleName 模块名称
     * @return 验证结果
     */
    public ValidationResult validateModule(String moduleName) {
        ModuleConfig config = MODULES.get(moduleName);
        if (config == null) {
            throw new IllegalArgumentException("未知的模块: " + moduleName);
        }

        logger.info("验证模块: " + moduleName);

        try {
            // 1. 加载 Properties 数据
            Properties propertiesData = persistenceService.loadProperties("starcore", config.fileName);
            logger.info("  - Properties 加载: " + propertiesData.size() + " 个键");

            // 2. 加载 SQL 数据
            Properties sqlData = loadFromSql(config.tableName);
            logger.info("  - SQL 加载: " + sqlData.size() + " 个键");

            // 3. 比对数据
            return compareProperties(moduleName, propertiesData, sqlData);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "验证模块 " + moduleName + " 失败", e);
            return ValidationResult.error(moduleName, e.getMessage());
        }
    }

    /**
     * 验证所有模块
     *
     * @return 所有模块的验证结果
     */
    public Map<String, ValidationResult> validateAll() {
        Map<String, ValidationResult> results = new LinkedHashMap<>();

        logger.info("开始验证所有模块...");
        for (String moduleName : MODULES.keySet()) {
            results.put(moduleName, validateModule(moduleName));
        }
        logger.info("验证完成");

        return results;
    }

    /**
     * 验证指定的模块列表
     *
     * @param moduleNames 模块名称列表
     * @return 验证结果
     */
    public Map<String, ValidationResult> validateModules(List<String> moduleNames) {
        Map<String, ValidationResult> results = new LinkedHashMap<>();

        logger.info("开始验证 " + moduleNames.size() + " 个模块...");
        for (String moduleName : moduleNames) {
            if (!MODULES.containsKey(moduleName)) {
                logger.warning("跳过未知模块: " + moduleName);
                continue;
            }
            results.put(moduleName, validateModule(moduleName));
        }
        logger.info("验证完成");

        return results;
    }

    /**
     * 从 SQL 数据库加载数据
     */
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

    /**
     * 比对两个 Properties 对象
     */
    private ValidationResult compareProperties(
        String moduleName,
        Properties propertiesData,
        Properties sqlData
    ) {
        Set<String> propKeys = propertiesData.stringPropertyNames();
        Set<String> sqlKeys = sqlData.stringPropertyNames();

        // 找出仅在 Properties 中存在的键
        Set<String> onlyInProperties = new TreeSet<>(propKeys);
        onlyInProperties.removeAll(sqlKeys);

        // 找出仅在 SQL 中存在的键
        Set<String> onlyInSql = new TreeSet<>(sqlKeys);
        onlyInSql.removeAll(propKeys);

        // 比对相同键的值
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
     *
     * @param results 验证结果
     * @param outputPath 输出文件路径
     */
    public void generateReport(
        Map<String, ValidationResult> results,
        Path outputPath
    ) throws IOException {
        StringBuilder report = new StringBuilder();

        // 报告头部
        report.append("# 数据一致性验证报告\n\n");
        report.append("**生成时间**: ").append(Instant.now()).append("\n\n");
        report.append("---\n\n");

        // 统计信息
        int totalModules = results.size();
        int passedModules = 0;
        int failedModules = 0;
        int errorModules = 0;

        for (ValidationResult result : results.values()) {
            if (result.hasError()) {
                errorModules++;
            } else if (result.isConsistent()) {
                passedModules++;
            } else {
                failedModules++;
            }
        }

        report.append("## 总览\n\n");
        report.append("| 状态 | 数量 |\n");
        report.append("|------|------|\n");
        report.append("| 通过 | ").append(passedModules).append(" |\n");
        report.append("| 失败 | ").append(failedModules).append(" |\n");
        report.append("| 错误 | ").append(errorModules).append(" |\n");
        report.append("| **总计** | **").append(totalModules).append("** |\n\n");

        report.append("---\n\n");

        // 详细结果
        report.append("## 详细结果\n\n");

        for (Map.Entry<String, ValidationResult> entry : results.entrySet()) {
            String moduleName = entry.getKey();
            ValidationResult result = entry.getValue();

            report.append("### ").append(moduleName).append(" 模块\n\n");

            if (result.hasError()) {
                report.append("**状态**: 错误\n\n");
                report.append("**错误信息**: ").append(result.errorMessage).append("\n\n");
                continue;
            }

            if (result.isConsistent()) {
                report.append("**状态**: 通过\n\n");
                report.append("- Properties 键数量: ").append(result.propertiesKeyCount).append("\n");
                report.append("- SQL 键数量: ").append(result.sqlKeyCount).append("\n");
                report.append("- 数据完全一致\n\n");
            } else {
                report.append("**状态**: 失败\n\n");
                report.append("- Properties 键数量: ").append(result.propertiesKeyCount).append("\n");
                report.append("- SQL 键数量: ").append(result.sqlKeyCount).append("\n\n");

                // 仅在 Properties 中存在的键
                if (!result.onlyInProperties.isEmpty()) {
                    report.append("#### 仅在 Properties 中存在的键 (")
                        .append(result.onlyInProperties.size())
                        .append(")\n\n");
                    int count = 0;
                    for (String key : result.onlyInProperties) {
                        report.append("- `").append(key).append("`\n");
                        if (++count >= 50) {
                            report.append("- ... (共 ").append(result.onlyInProperties.size()).append(" 个)\n");
                            break;
                        }
                    }
                    report.append("\n");
                }

                // 仅在 SQL 中存在的键
                if (!result.onlyInSql.isEmpty()) {
                    report.append("#### 仅在 SQL 中存在的键 (")
                        .append(result.onlyInSql.size())
                        .append(")\n\n");
                    int count = 0;
                    for (String key : result.onlyInSql) {
                        report.append("- `").append(key).append("`\n");
                        if (++count >= 50) {
                            report.append("- ... (共 ").append(result.onlyInSql.size()).append(" 个)\n");
                            break;
                        }
                    }
                    report.append("\n");
                }

                // 值不一致的键
                if (!result.valueDifferences.isEmpty()) {
                    report.append("#### 值不一致的键 (")
                        .append(result.valueDifferences.size())
                        .append(")\n\n");
                    int count = 0;
                    for (Map.Entry<String, ValueDifference> diffEntry : result.valueDifferences.entrySet()) {
                        String key = diffEntry.getKey();
                        ValueDifference diff = diffEntry.getValue();
                        report.append("**`").append(key).append("`**\n");
                        report.append("- Properties: `").append(truncate(diff.propertiesValue, 100)).append("`\n");
                        report.append("- SQL: `").append(truncate(diff.sqlValue, 100)).append("`\n\n");
                        if (++count >= 20) {
                            report.append("... (共 ").append(result.valueDifferences.size()).append(" 个差异)\n\n");
                            break;
                        }
                    }
                }
            }

            report.append("---\n\n");
        }

        // 写入文件
        Files.writeString(outputPath, report.toString());
        logger.info("报告已生成: " + outputPath);
    }

    /**
     * 截断长字符串用于显示
     */
    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "null";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }

    /**
     * 获取支持的模块列表
     */
    public static Set<String> getSupportedModules() {
        return MODULES.keySet();
    }

    /**
     * 模块配置
     */
    private static class ModuleConfig {
        final String fileName;
        final String tableName;

        ModuleConfig(String fileName, String tableName) {
            this.fileName = fileName;
            this.tableName = tableName;
        }
    }

    /**
     * 值差异
     */
    public static class ValueDifference {
        public final String propertiesValue;
        public final String sqlValue;

        ValueDifference(String propertiesValue, String sqlValue) {
            this.propertiesValue = propertiesValue;
            this.sqlValue = sqlValue;
        }
    }

    /**
     * 验证结果
     */
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

        /**
         * 是否数据一致
         */
        public boolean isConsistent() {
            return !hasError()
                && onlyInProperties.isEmpty()
                && onlyInSql.isEmpty()
                && valueDifferences.isEmpty();
        }

        /**
         * 是否有错误
         */
        public boolean hasError() {
            return errorMessage != null;
        }

        /**
         * 创建错误结果
         */
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
}
