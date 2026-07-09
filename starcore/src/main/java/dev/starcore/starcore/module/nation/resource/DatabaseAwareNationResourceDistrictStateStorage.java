package dev.starcore.starcore.module.nation.resource;

import dev.starcore.starcore.core.database.DatabaseService;
import dev.starcore.starcore.core.persistence.PersistenceService;

import java.util.Objects;
import java.util.Properties;
import java.util.logging.Logger;

final class DatabaseAwareNationResourceDistrictStateStorage implements NationResourceDistrictStateStorage {
    private final DatabaseService databaseService;
    private final NationResourceDistrictStateStorage fileStorage;
    private final NationResourceDistrictStateStorage sqlStorage;
    private volatile boolean sqlPreferred;

    DatabaseAwareNationResourceDistrictStateStorage(String namespace, DatabaseService databaseService, PersistenceService persistenceService, Logger logger) {
        this.databaseService = Objects.requireNonNull(databaseService, "databaseService");
        this.fileStorage = new PersistenceNationResourceDistrictStateStorage(namespace, persistenceService);
        this.sqlStorage = new SqlNationResourceDistrictStateStorage(namespace, databaseService, persistenceService, logger);
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

    private NationResourceDistrictStateStorage activeStorage() {
        if (sqlPreferred || databaseService.isRunning()) {
            sqlPreferred = true;
            return sqlStorage;
        }
        return fileStorage;
    }
}
