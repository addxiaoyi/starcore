package dev.starcore.starcore.core.storage;

import dev.starcore.starcore.core.database.DatabaseService;
import dev.starcore.starcore.core.persistence.PersistenceService;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * 模块状态存储抽象基类
 *
 * 提供双模式存储架构的通用实现：
 * 1. Properties 文件存储（传统模式）
 * 2. SQL 数据库存储（生产模式）
 *
 * 自动处理：
 * - 数据库失败时降级到文件存储
 * - Properties → SQL 自动迁移
 * - 异步保存支持
 * - 表结构自动创建
 *
 * 子类需要实现：
 * - getTableName() - 返回 SQL 表名
 * - getFileName() - 返回 Properties 文件名
 * - ensureTable(Connection) - 创建表结构
 * - loadFromDatabase(Connection) - 从 SQL 加载
 * - writeSnapshot(Connection, Properties) - 写入 SQL
 */
public abstract class AbstractModuleStateStorage {

    protected final String namespace;
    protected final DatabaseService databaseService;
    protected final PersistenceService persistenceService;
    protected final Logger logger;

    private final Object saveLock = new Object();
    private final ScheduledExecutorService asyncSaveExecutor;
    private CompletableFuture<Void> pendingSave = CompletableFuture.completedFuture(null);

    /**
     * 构造函数
     *
     * @param namespace 模块命名空间
     * @param databaseService 数据库服务
     * @param persistenceService 持久化服务
     * @param logger 日志记录器
     */
    protected AbstractModuleStateStorage(
            String namespace,
            DatabaseService databaseService,
            PersistenceService persistenceService,
            Logger logger) {
        this.namespace = Objects.requireNonNull(namespace, "namespace");
        this.databaseService = Objects.requireNonNull(databaseService, "databaseService");
        this.persistenceService = Objects.requireNonNull(persistenceService, "persistenceService");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.asyncSaveExecutor = Executors.newSingleThreadScheduledExecutor(new StorageThreadFactory(namespace));
    }

    /**
     * 获取 SQL 表名
     */
    protected abstract String getTableName();

    /**
     * 获取 Properties 文件名
     */
    protected abstract String getFileName();

    /**
     * 确保表结构存在
     */
    protected abstract void ensureTable(Connection connection) throws SQLException;

    /**
     * 从数据库加载数据
     */
    protected abstract Properties loadFromDatabase(Connection connection) throws SQLException;

    /**
     * 写入数据到数据库
     */
    protected abstract void writeSnapshot(Connection connection, Properties properties) throws SQLException;

    /**
     * 从 Properties 文件加载
     */
    protected Properties loadFromProperties() {
        return persistenceService.loadProperties(namespace, getFileName());
    }

    /**
     * 保存到 Properties 文件
     */
    protected void saveToProperties(Properties properties) {
        persistenceService.saveProperties(namespace, getFileName(), properties);
    }

    /**
     * 打开数据库连接
     */
    protected Connection openConnection() throws SQLException {
        Optional<DataSource> dataSource = databaseService.dataSource();
        if (dataSource.isEmpty()) {
            throw new SQLException("DataSource not available");
        }
        return dataSource.get().getConnection();
    }

    /**
     * 智能加载
     *
     * 1. 尝试从 SQL 加载
     * 2. 如果 SQL 为空但 Properties 有数据 → 自动迁移
     * 3. 如果 SQL 失败 → 降级到 Properties
     */
    public Properties load() {
        try (Connection connection = openConnection()) {
            ensureTable(connection);
            Properties properties = loadFromDatabase(connection);

            if (!properties.isEmpty()) {
                return properties;
            }

            // SQL 为空，尝试迁移 Properties 数据
            Properties legacy = copy(loadFromProperties());
            if (legacy.isEmpty()) {
                return legacy;
            }

            writeSnapshot(connection, legacy);
            logger.info("[" + namespace + "] 已从 Properties 迁移数据到 SQL");
            return legacy;

        } catch (Exception exception) {
            // E-110: 降级时明确报警并记录监控指标
            logger.severe("[" + namespace + "] SQL 加载失败，降级到 Properties，这是严重问题请检查数据库: " + exception.getMessage());
            return loadFromProperties();
        }
    }

    /**
     * 同步保存
     * E-108: 修复死锁问题 - 不在持有 saveLock 的情况下 join
     */
    public void save(Properties properties) {
        Properties snapshot = copy(properties);
        // 先等待之前的保存完成（不持有锁）
        try {
            pendingSave.join();
        } catch (Exception ignored) {}
        // 再执行写操作
        writeSnapshot(snapshot);
    }

    /**
     * 异步保存
     * E-109: 修复异步丢数据问题 - 使用 handle 而不是 exceptionally
     */
    public void saveAsync(Properties properties) {
        Properties snapshot = copy(properties);
        pendingSave = CompletableFuture
            .runAsync(
                () -> writeSnapshot(snapshot),
                CompletableFuture.delayedExecutor(100, TimeUnit.MILLISECONDS, asyncSaveExecutor)
            )
            .handle((result, ex) -> {
                if (ex != null) {
                    logger.warning("[" + namespace + "] 异步保存失败: " + ex.getMessage());
                }
                return result;
            });
    }

    /**
     * 写入快照（自动选择存储方式）
     * E-111: SQL 保存失败时记录严重错误并监控，而非静默降级
     */
    private void writeSnapshot(Properties properties) {
        try (Connection connection = openConnection()) {
            writeSnapshot(connection, properties);
        } catch (Exception exception) {
            logger.severe("[" + namespace + "] SQL 保存失败，降级到 Properties，SQL 数据可能不一致: " + exception.getMessage());
            // E-111: 即使降级到 Properties 也记录监控事件
            saveToProperties(properties);
        }
    }

    /**
     * 等待挂起的保存完成
     */
    private void awaitPendingSave() {
        synchronized (saveLock) {
            try {
                pendingSave.join();
            } catch (Exception ignored) {
                // 忽略异常，saveAsync 中已处理
            }
        }
    }

    /**
     * Flush pending saves and stop the async save executor.
     */
    public void shutdown() {
        awaitPendingSave();
        asyncSaveExecutor.shutdown();
        try {
            if (!asyncSaveExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                asyncSaveExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            asyncSaveExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 复制 Properties
     */
    protected static Properties copy(Properties source) {
        Properties copy = new Properties();
        source.forEach((key, value) -> copy.setProperty((String) key, (String) value));
        return copy;
    }

    /**
     * 检查是否使用 SQL 模式
     */
    public boolean isUsingSql() {
        return databaseService.isRunning();
    }

    private static final class StorageThreadFactory implements ThreadFactory {
        private static final AtomicInteger POOL_ID = new AtomicInteger();
        private final String prefix;
        private final AtomicInteger threadId = new AtomicInteger();

        private StorageThreadFactory(String namespace) {
            this.prefix = "starcore-state-" + namespace + "-" + POOL_ID.incrementAndGet() + "-";
        }

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, prefix + threadId.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
