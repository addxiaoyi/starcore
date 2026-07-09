package dev.starcore.starcore.core.database;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

/**
 * 数据库工具类 - 提供 MySQL/SQLite 兼容性支持
 */
public final class DatabaseUtils {

    /**
     * 检测数据库类型
     */
    public static DatabaseType detectDatabaseType(DataSource dataSource) {
        try (Connection conn = dataSource.getConnection()) {
            String productName = conn.getMetaData().getDatabaseProductName();
            if ("SQLite".equalsIgnoreCase(productName)) {
                return DatabaseType.SQLITE;
            }
            return DatabaseType.MYSQL;
        } catch (SQLException e) {
            return DatabaseType.MYSQL; // 默认使用 MySQL 语法
        }
    }

    /**
     * 检测数据库类型 (从 Connection)
     */
    public static DatabaseType detectDatabaseType(Connection connection) {
        try {
            String productName = connection.getMetaData().getDatabaseProductName();
            if ("SQLite".equalsIgnoreCase(productName)) {
                return DatabaseType.SQLITE;
            }
            return DatabaseType.MYSQL;
        } catch (SQLException e) {
            return DatabaseType.MYSQL;
        }
    }

    /**
     * 获取自增主键语法
     */
    public static String getAutoIncrement() {
        return "INTEGER PRIMARY KEY AUTOINCREMENT";
    }

    /**
     * 获取 VARCHAR 类型
     */
    public static String getVarchar(int length) {
        return "TEXT";
    }

    /**
     * 获取 BIGINT 类型
     */
    public static String getBigInt() {
        return "INTEGER";
    }

    /**
     * 获取 DOUBLE 类型
     */
    public static String getDouble() {
        return "REAL";
    }

    /**
     * 获取 BOOLEAN 类型
     */
    public static String getBoolean() {
        return "INTEGER";
    }

    /**
     * 获取 TEXT 类型
     */
    public static String getText() {
        return "TEXT";
    }

    /**
     * 获取 INT 类型
     */
    public static String getInt() {
        return "INTEGER";
    }

    /**
     * 创建索引语法 (SQLite)
     */
    public static String createIndex(String indexName, String tableName, String columnName) {
        return String.format("CREATE INDEX IF NOT EXISTS %s ON %s(%s)", indexName, tableName, columnName);
    }

    /**
     * 创建唯一索引语法 (SQLite)
     */
    public static String createUniqueIndex(String indexName, String tableName, String columnName) {
        return String.format("CREATE UNIQUE INDEX IF NOT EXISTS %s ON %s(%s)", indexName, tableName, columnName);
    }

    /**
     * 获取 ON CONFLICT 语法 (SQLite)
     */
    public static String getOnConflictDoUpdate(String primaryKey) {
        return "ON CONFLICT(" + primaryKey + ") DO UPDATE SET";
    }

    /**
     * 获取 ON CONFLICT DO NOTHING 语法 (SQLite)
     */
    public static String getOnConflictDoNothing() {
        return "ON CONFLICT DO NOTHING";
    }

    /**
     * 检查表是否存在
     */
    public static boolean tableExists(Connection conn, String tableName) throws SQLException {
        DatabaseMetaData meta = conn.getMetaData();
        try (var rs = meta.getTables(null, null, tableName, new String[]{"TABLE"})) {
            return rs.next();
        }
    }

    /**
     * 检查列是否存在
     */
    public static boolean columnExists(Connection conn, String tableName, String columnName) throws SQLException {
        DatabaseMetaData meta = conn.getMetaData();
        try (var rs = meta.getColumns(null, null, tableName, columnName)) {
            return rs.next();
        }
    }

    /**
     * 数据库类型枚举
     */
    public enum DatabaseType {
        MYSQL,
        SQLITE
    }
}
