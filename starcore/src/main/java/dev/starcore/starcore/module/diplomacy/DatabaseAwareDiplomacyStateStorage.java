package dev.starcore.starcore.module.diplomacy;

import dev.starcore.starcore.core.database.DatabaseService;
import dev.starcore.starcore.core.persistence.PersistenceService;

import java.util.Objects;
import java.util.Properties;
import java.util.logging.Logger;

final class DatabaseAwareDiplomacyStateStorage implements DiplomacyStateStorage {
    private final DatabaseService databaseService;
    private final DiplomacyStateStorage fileStorage;
    private final DiplomacyStateStorage sqlStorage;
    private volatile boolean sqlPreferred;

    DatabaseAwareDiplomacyStateStorage(String namespace, DatabaseService databaseService, PersistenceService persistenceService, Logger logger) {
        this.databaseService = Objects.requireNonNull(databaseService, "databaseService");
        this.fileStorage = new PersistenceDiplomacyStateStorage(namespace, persistenceService);
        this.sqlStorage = new SqlDiplomacyStateStorage(namespace, databaseService, persistenceService, logger);
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

    private DiplomacyStateStorage activeStorage() {
        if (sqlPreferred || databaseService.isRunning()) {
            sqlPreferred = true;
            return sqlStorage;
        }
        return fileStorage;
    }
}
