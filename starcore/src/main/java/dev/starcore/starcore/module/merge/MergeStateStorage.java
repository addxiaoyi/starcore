package dev.starcore.starcore.module.merge;

import dev.starcore.starcore.core.database.DatabaseService;
import dev.starcore.starcore.core.persistence.PersistenceService;
import org.bukkit.plugin.Plugin;

import java.util.Properties;
import java.util.logging.Logger;

/**
 * 数据库感知的合并公投状态存储
 */
public final class MergeStateStorage {
    private static final String FILE_NAME = "merge-referendums.properties";

    private final String namespace;
    private final DatabaseService databaseService;
    private final PersistenceService persistenceService;
    private final Logger logger;
    private final Properties cache = new Properties();

    public MergeStateStorage(
            String namespace,
            DatabaseService databaseService,
            PersistenceService persistenceService,
            Logger logger
    ) {
        this.namespace = namespace;
        this.databaseService = databaseService;
        this.persistenceService = persistenceService;
        this.logger = logger;
    }

    /**
     * 异步保存状态
     */
    public void saveAsync(Properties properties) {
        if (properties == null) {
            return;
        }
        synchronized (cache) {
            cache.clear();
            cache.putAll(properties);
        }
        persistenceService.savePropertiesAsync(namespace, FILE_NAME, properties);
    }

    /**
     * 同步保存状态
     */
    public void save(Properties properties) {
        if (properties == null) {
            return;
        }
        synchronized (cache) {
            cache.clear();
            cache.putAll(properties);
        }
        persistenceService.saveProperties(namespace, FILE_NAME, properties);
    }

    /**
     * 加载状态
     */
    public Properties load() {
        Properties props = persistenceService.loadProperties(namespace, FILE_NAME);
        if (props == null || props.isEmpty()) {
            // 尝试从数据库加载
            props = loadFromDatabase();
        }
        return props != null ? props : new Properties();
    }

    private Properties loadFromDatabase() {
        // 如果有数据库支持，可以从这里加载
        // 目前返回空 Properties，依赖 PersistenceService
        return new Properties();
    }

    /**
     * 获取缓存的 Properties（用于同步访问）
     */
    public Properties cached() {
        synchronized (cache) {
            return new Properties(cache);
        }
    }
}