package dev.starcore.starcore.module.resource;

import dev.starcore.starcore.core.database.DatabaseService;
import dev.starcore.starcore.core.persistence.PersistenceService;

import java.util.Objects;
import java.util.Properties;
import java.util.logging.Logger;

final class DatabaseAwareResourceStateStorage implements ResourceStateStorage {
    private final DatabaseService databaseService;
    private final ResourceStateStorage fileStorage;
    private final ResourceStateStorage sqlStorage;
    private volatile boolean sqlPreferred;

    DatabaseAwareResourceStateStorage(String namespace, DatabaseService databaseService, PersistenceService persistenceService, Logger logger) {
        this.databaseService = Objects.requireNonNull(databaseService, "databaseService");
        this.fileStorage = new PersistenceResourceStateStorage(namespace, persistenceService);
        this.sqlStorage = new SqlResourceStateStorage(namespace, databaseService, persistenceService, logger);
        this.sqlPreferred = databaseService.isRunning();
    }

    @Override
    public Properties load() {
        return activeStorage().load();
    }

    @Override
    public void save(Properties properties) {
        activeStorage().save(properties);
    }

    @Override
    public void saveAsync(Properties properties) {
        activeStorage().saveAsync(properties);
    }

    private ResourceStateStorage activeStorage() {
        if (sqlPreferred || databaseService.isRunning()) {
            sqlPreferred = true;
            return sqlStorage;
        }
        return fileStorage;
    }
}
