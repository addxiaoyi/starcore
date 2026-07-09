package dev.starcore.starcore.module.nation;

import dev.starcore.starcore.core.database.DatabaseService;
import dev.starcore.starcore.core.persistence.PersistenceService;

import java.util.Objects;
import java.util.Properties;
import java.util.logging.Logger;

final class DatabaseAwareNationStateStorage implements NationStateStorage {
    private final DatabaseService databaseService;
    private final NationStateStorage fileStorage;
    private final NationStateStorage sqlStorage;
    private volatile boolean sqlPreferred;

    DatabaseAwareNationStateStorage(String namespace, DatabaseService databaseService, PersistenceService persistenceService, Logger logger) {
        this.databaseService = Objects.requireNonNull(databaseService, "databaseService");
        this.fileStorage = new PersistenceNationStateStorage(namespace, persistenceService);
        this.sqlStorage = new SqlNationStateStorage(namespace, databaseService, persistenceService, logger);
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

    private NationStateStorage activeStorage() {
        if (sqlPreferred || databaseService.isRunning()) {
            sqlPreferred = true;
            return sqlStorage;
        }
        return fileStorage;
    }
}
