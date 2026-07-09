package dev.starcore.starcore.core.database;

import dev.starcore.starcore.core.config.ConfigurationService;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;

public record DatabaseSettings(
    boolean enabled,
    boolean failFast,
    DatabaseType type,
    Path sqliteFile,
    String mysqlHost,
    int mysqlPort,
    String mysqlDatabase,
    String mysqlUsername,
    String mysqlPassword,
    String mysqlParameters,
    DatabasePoolSettings pool
) {
    private static final String SQLITE_DRIVER = "org.sqlite.JDBC";
    private static final String MYSQL_DRIVER = "com.mysql.cj.jdbc.Driver";

    public static DatabaseSettings from(ConfigurationService configuration, Path dataFolder) {
        DatabaseType type = DatabaseType.fromConfig(configuration.databaseType());
        Path sqliteFile = resolveSqliteFile(dataFolder, configuration.databaseSqliteFile());
        return new DatabaseSettings(
            configuration.databaseEnabled(),
            configuration.databaseFailFast(),
            type,
            sqliteFile,
            configuration.databaseMysqlHost(),
            configuration.databaseMysqlPort(),
            configuration.databaseMysqlDatabase(),
            configuration.databaseMysqlUsername(),
            configuration.databaseMysqlPassword(),
            configuration.databaseMysqlParameters(),
            new DatabasePoolSettings(
                configuration.databasePoolMaximumPoolSize(),
                configuration.databasePoolMinimumIdle(),
                configuration.databasePoolConnectionTimeoutMs(),
                configuration.databasePoolIdleTimeoutMs(),
                configuration.databasePoolMaxLifetimeMs(),
                configuration.databasePoolKeepaliveTimeMs(),
                configuration.databasePoolValidationTimeoutMs(),
                configuration.databasePoolLeakDetectionThresholdMs()
            )
        );
    }

    public static DatabaseSettings disabled() {
        return new DatabaseSettings(
            false,
            false,
            DatabaseType.SQLITE,
            Path.of("starcore.db").toAbsolutePath().normalize(),
            "127.0.0.1",
            3306,
            "starcore",
            "starcore",
            "",
            "",
            new DatabasePoolSettings(1, 0, 30_000L, 600_000L, 1_800_000L, 0L, 5_000L, 0L)
        );
    }

    public String jdbcUrl() {
        return switch (type) {
            case SQLITE -> "jdbc:sqlite:" + sqliteFile.toAbsolutePath().normalize();
            case MYSQL -> mysqlJdbcUrl();
        };
    }

    public String driverClassName() {
        return switch (type) {
            case SQLITE -> SQLITE_DRIVER;
            case MYSQL -> MYSQL_DRIVER;
        };
    }

    public String summary(boolean running) {
        if (!enabled) {
            return "已关闭";
        }
        String status = running ? "运行中" : "未连接";
        return switch (type) {
            case SQLITE -> "%s %s (%s)".formatted(type.displayName(), status, sqliteFile.toAbsolutePath().normalize());
            case MYSQL -> "%s %s (%s:%d/%s)".formatted(type.displayName(), status, mysqlHost, mysqlPort, mysqlDatabase);
        };
    }

    private String mysqlJdbcUrl() {
        String database = mysqlDatabase == null || mysqlDatabase.isBlank() ? "starcore" : mysqlDatabase.trim();
        String host = mysqlHost == null || mysqlHost.isBlank() ? "127.0.0.1" : mysqlHost.trim();
        String parameters = mysqlParameters == null ? "" : mysqlParameters.trim();
        String base = "jdbc:mysql://%s:%d/%s".formatted(host, mysqlPort, database);
        return parameters.isBlank() ? base : base + "?" + parameters;
    }

    private static Path resolveSqliteFile(Path dataFolder, String rawFile) {
        String file = rawFile == null || rawFile.isBlank() ? "starcore.db" : rawFile.trim();
        try {
            Path path = Path.of(file);
            if (path.isAbsolute()) {
                return path.normalize();
            }
            Path base = dataFolder == null ? Path.of(".") : dataFolder;
            return base.resolve(path).toAbsolutePath().normalize();
        } catch (InvalidPathException ignored) {
            Path base = dataFolder == null ? Path.of(".") : dataFolder;
            return base.resolve("starcore.db").toAbsolutePath().normalize();
        }
    }
}
