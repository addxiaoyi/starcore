package dev.starcore.starcore.module.officer;

import dev.starcore.starcore.core.persistence.PersistenceService;

import java.util.Objects;
import java.util.Properties;

final class PersistenceOfficerStateStorage implements OfficerStateStorage {
    private static final String FILE_NAME = "officers.properties";

    private final String namespace;
    private final PersistenceService persistenceService;

    PersistenceOfficerStateStorage(String namespace, PersistenceService persistenceService) {
        this.namespace = Objects.requireNonNull(namespace, "namespace");
        this.persistenceService = Objects.requireNonNull(persistenceService, "persistenceService");
    }

    @Override
    public Properties load() {
        return persistenceService.loadProperties(namespace, FILE_NAME);
    }

    @Override
    public void save(Properties properties) {
        persistenceService.saveProperties(namespace, FILE_NAME, properties);
    }

    @Override
    public void saveAsync(Properties properties) {
        persistenceService.savePropertiesAsync(namespace, FILE_NAME, properties);
    }
}
