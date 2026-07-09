package dev.starcore.starcore.core.database;

import dev.starcore.starcore.core.persistence.PersistenceService;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * 数据一致性验证命令行工具
 *
 * 可独立运行的验证工具，无需启动 Minecraft 服务器
 *
 * 使用方式：
 * ```bash
 * java -cp starcore.jar dev.starcore.starcore.core.database.DataConsistencyValidatorCommand \
 *   --data-dir /path/to/plugins/starcore \
 *   --db-url jdbc:mysql://localhost:3306/starcore \
 *   --db-user username \
 *   --db-password password \
 *   --modules nation,diplomacy,policy \
 *   --output report.md
 * ```
 *
 * 参数：
 * - --data-dir: StarCore 数据目录 (默认: ./plugins/starcore)
 * - --db-url: 数据库 JDBC URL (必需)
 * - --db-user: 数据库用户名 (必需)
 * - --db-password: 数据库密码 (必需)
 * - --modules: 要验证的模块，逗号分隔 (默认: 全部)
 * - --output: 输出报告文件路径 (默认: data-consistency-report-<timestamp>.md)
 * - --help: 显示帮助信息
 */
public class DataConsistencyValidatorCommand {

    private static final Logger LOGGER = Logger.getLogger(DataConsistencyValidatorCommand.class.getName());
    private static final DateTimeFormatter TIMESTAMP_FORMATTER =
        DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").withZone(ZoneId.systemDefault());

    public static void main(String[] args) {
        setupLogging();

        try {
            CommandLineArgs cmdArgs = parseArguments(args);

            if (cmdArgs.showHelp) {
                printHelp();
                System.exit(0);
            }

            // 验证必需参数
            if (cmdArgs.dbUrl == null || cmdArgs.dbUser == null || cmdArgs.dbPassword == null) {
                LOGGER.severe("错误: 缺少必需的数据库参数");
                printHelp();
                System.exit(1);
            }

            LOGGER.info("========================================");
            LOGGER.info("  数据一致性验证工具");
            LOGGER.info("========================================");
            LOGGER.info("数据目录: " + cmdArgs.dataDir);
            LOGGER.info("数据库: " + cmdArgs.dbUrl);
            LOGGER.info("输出报告: " + cmdArgs.outputPath);
            LOGGER.info("");

            // 执行验证
            runValidation(cmdArgs);

            LOGGER.info("");
            LOGGER.info("========================================");
            LOGGER.info("  验证完成");
            LOGGER.info("========================================");

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "验证失败", e);
            System.exit(1);
        }
    }

    private static void runValidation(CommandLineArgs cmdArgs) throws Exception {
        // 创建 PersistenceService (简化版本，不依赖 Bukkit)
        StandalonePersistenceService persistenceService =
            new StandalonePersistenceService(Paths.get(cmdArgs.dataDir));

        // 创建数据源
        DataSource dataSource = createDataSource(cmdArgs);

        // 创建验证器
        DataConsistencyValidator validator = new DataConsistencyValidator(
            persistenceService,
            dataSource,
            LOGGER
        );

        // 执行验证
        Map<String, DataConsistencyValidator.ValidationResult> results;
        if (cmdArgs.modules.isEmpty()) {
            LOGGER.info("验证所有模块...");
            results = validator.validateAll();
        } else {
            LOGGER.info("验证指定模块: " + cmdArgs.modules);
            results = validator.validateModules(cmdArgs.modules);
        }

        // 生成报告
        Path outputPath = Paths.get(cmdArgs.outputPath);
        validator.generateReport(results, outputPath);

        // 打印摘要
        printSummary(results);
    }

    private static DataSource createDataSource(CommandLineArgs cmdArgs) throws Exception {
        // 使用 HikariCP 创建连接池
        com.zaxxer.hikari.HikariConfig config = new com.zaxxer.hikari.HikariConfig();
        config.setJdbcUrl(cmdArgs.dbUrl);
        config.setUsername(cmdArgs.dbUser);
        config.setPassword(cmdArgs.dbPassword);
        config.setMaximumPoolSize(5);
        config.setConnectionTimeout(30000);

        return new com.zaxxer.hikari.HikariDataSource(config);
    }

    private static void printSummary(Map<String, DataConsistencyValidator.ValidationResult> results) {
        LOGGER.info("");
        LOGGER.info("验证摘要:");
        LOGGER.info("----------------------------------------");

        int passed = 0;
        int failed = 0;
        int errors = 0;

        for (Map.Entry<String, DataConsistencyValidator.ValidationResult> entry : results.entrySet()) {
            String moduleName = entry.getKey();
            DataConsistencyValidator.ValidationResult result = entry.getValue();

            String status;
            if (result.hasError()) {
                status = "错误";
                errors++;
            } else if (result.isConsistent()) {
                status = "通过";
                passed++;
            } else {
                status = "失败";
                failed++;
            }

            LOGGER.info(String.format("  %-12s : %s", moduleName, status));
        }

        LOGGER.info("----------------------------------------");
        LOGGER.info(String.format("通过: %d | 失败: %d | 错误: %d", passed, failed, errors));
    }

    private static CommandLineArgs parseArguments(String[] args) {
        CommandLineArgs cmdArgs = new CommandLineArgs();

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];

            switch (arg) {
                case "--help", "-h" -> cmdArgs.showHelp = true;
                case "--data-dir" -> cmdArgs.dataDir = args[++i];
                case "--db-url" -> cmdArgs.dbUrl = args[++i];
                case "--db-user" -> cmdArgs.dbUser = args[++i];
                case "--db-password" -> cmdArgs.dbPassword = args[++i];
                case "--modules" -> {
                    String modulesStr = args[++i];
                    cmdArgs.modules = Arrays.asList(modulesStr.split(","));
                }
                case "--output" -> cmdArgs.outputPath = args[++i];
                default -> LOGGER.warning("未知参数: " + arg);
            }
        }

        // 设置默认输出路径
        if (cmdArgs.outputPath == null) {
            String timestamp = TIMESTAMP_FORMATTER.format(Instant.now());
            cmdArgs.outputPath = "data-consistency-report-" + timestamp + ".md";
        }

        return cmdArgs;
    }

    private static void printHelp() {
        System.out.println("""
            数据一致性验证工具

            用法:
              java -cp starcore.jar dev.starcore.starcore.core.database.DataConsistencyValidatorCommand [选项]

            选项:
              --data-dir <path>       StarCore 数据目录 (默认: ./plugins/starcore)
              --db-url <url>          数据库 JDBC URL (必需)
              --db-user <username>    数据库用户名 (必需)
              --db-password <pwd>     数据库密码 (必需)
              --modules <list>        要验证的模块，逗号分隔 (默认: 全部)
              --output <file>         输出报告文件路径 (默认: 自动生成)
              --help, -h              显示此帮助信息

            示例:
              # 验证所有模块
              java -cp starcore.jar dev.starcore.starcore.core.database.DataConsistencyValidatorCommand \\
                --data-dir /path/to/plugins/starcore \\
                --db-url jdbc:mysql://localhost:3306/starcore \\
                --db-user root \\
                --db-password password

              # 验证指定模块
              java -cp starcore.jar dev.starcore.starcore.core.database.DataConsistencyValidatorCommand \\
                --data-dir /path/to/plugins/starcore \\
                --db-url jdbc:mysql://localhost:3306/starcore \\
                --db-user root \\
                --db-password password \\
                --modules nation,diplomacy,policy \\
                --output my-report.md

            支持的模块:
              nation, diplomacy, policy, resource, technology, treasury,
              war, officer, event, resolution
            """);
    }

    private static void setupLogging() {
        Logger rootLogger = Logger.getLogger("");
        rootLogger.setLevel(Level.INFO);

        // 移除默认的 handler
        for (var handler : rootLogger.getHandlers()) {
            rootLogger.removeHandler(handler);
        }

        // 添加自定义的 console handler
        ConsoleHandler consoleHandler = new ConsoleHandler();
        consoleHandler.setLevel(Level.INFO);
        consoleHandler.setFormatter(new SimpleFormatter());
        rootLogger.addHandler(consoleHandler);
    }

    /**
     * 命令行参数
     */
    private static class CommandLineArgs {
        String dataDir = "./plugins/starcore";
        String dbUrl;
        String dbUser;
        String dbPassword;
        List<String> modules = Collections.emptyList();
        String outputPath;
        boolean showHelp = false;
    }

    /**
     * 独立的 PersistenceService 实现
     * 不依赖 Bukkit Plugin
     */
    private static class StandalonePersistenceService extends PersistenceService {
        private final Path dataDirectory;

        StandalonePersistenceService(Path dataDirectory) {
            super(null, null);
            this.dataDirectory = dataDirectory;
        }

        @Override
        public Properties loadProperties(String namespace, String fileName) {
            Properties properties = new Properties();
            try {
                Path file = dataDirectory.resolve(namespace).resolve(fileName).normalize();
                if (!java.nio.file.Files.exists(file)) {
                    LOGGER.warning("文件不存在: " + file);
                    return properties;
                }
                try (java.io.InputStream input = java.nio.file.Files.newInputStream(file)) {
                    properties.load(input);
                }
                return properties;
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "无法加载文件: " + namespace + "/" + fileName, e);
                return properties;
            }
        }
    }
}
