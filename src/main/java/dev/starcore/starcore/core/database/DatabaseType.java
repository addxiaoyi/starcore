package dev.starcore.starcore.core.database;

import java.util.Locale;

public enum DatabaseType {
    SQLITE("SQLite"),
    MYSQL("MySQL");

    private final String displayName;

    DatabaseType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public static DatabaseType fromConfig(String value) {
        if (value == null || value.isBlank()) {
            return SQLITE;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "mysql", "mariadb" -> MYSQL;
            default -> SQLITE;
        };
    }
}
