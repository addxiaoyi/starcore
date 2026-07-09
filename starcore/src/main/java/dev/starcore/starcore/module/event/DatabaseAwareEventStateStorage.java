package dev.starcore.starcore.module.event;

import dev.starcore.starcore.core.database.DatabaseService;
import dev.starcore.starcore.core.persistence.PersistenceService;

import java.util.Objects;
import java.util.Properties;
import java.util.logging.Logger;

final class DatabaseAwareEventStateStorage implements EventStateStorage {
    private final DatabaseService databaseService;
    private final EventStateStorage fileStorage;
    private final EventStateStorage sqlStorage;
    private volatile boolean sqlPreferred;

    DatabaseAwareEventStateStorage(String namespace, DatabaseService databaseService, PersistenceService persistenceService, Logger logger) {
        this.databaseService = Objects.requireNonNull(databaseService, "databaseService");
        this.fileStorage = new PersistenceEventStateStorage(namespace, persistenceService);
        this.sqlStorage = new SqlEventStateStorage(namespace, databaseService, persistenceService, logger);
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

    private EventStateStorage activeStorage() {
        if (sqlPreferred || databaseService.isRunning()) {
            sqlPreferred = true;
            return sqlStorage;
        }
        return fileStorage;
    }
}
